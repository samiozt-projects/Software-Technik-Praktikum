package simulation
import domain.*

/**
 * Manages merge logic at intersections in the simulation.
 *
 * @property simulation The simulation instance.
 */
class MergeManager(private val simulation: Simulation) {
    /**
     * Right-of-way pre-pass for every merge [ConnectionNode] (two or more incoming streets),
     * run once per tick before acceleration so the merge ceiling in speedCeilingForMerge
     * reflects the current grant (Lastenheft §3.3, Issue #92).
     *
     * Per node:
     *  1. The current mergeOccupant is released once it has physically cleared the node
     *     ([hasClearedMerge]); until then the junction stays busy and every other approach yields.
     *  2. If the junction is free and a previously granted vehicle is still approaching from its
     *     stop line, that short grant is kept. Vehicles that are still far from the stop line do not reserve
     *     the merge and therefore cannot make other traffic stop in the middle of the road.
     */
    fun updateMergeGrants() {
        for (node in simulation.level.nodes.filterIsInstance<ConnectionNode>()) {
            val incoming = node.incomingStreets
            if (incoming.size < 2) continue

            // 1. Release a previous occupant that has driven clear of the node.
            val current = simulation.mergeOccupant[node]
            if (current == null || hasClearedMerge(current, node)) simulation.mergeOccupant.remove(node)

            if (simulation.mergeOccupant.containsKey(node)) {
                simulation.mergeGrant.remove(node)
                continue
            }

            val currentGrant = simulation.mergeGrant[node]
            if (currentGrant != null) {
                if (currentGrant in simulation.level.vehicles && currentGrant.currentStreet in incoming) continue
                simulation.mergeGrant.remove(node)
            }

            val waitingByApproach = HashMap<Int, Vehicle>()
            for (i in incoming.indices) {
                val lead = leadVehicle(incoming[i]) ?: continue
                if (SimulationUtils.remainingDistanceToStopLineM(lead) <= Simulation.MERGE_STOP_LINE_EPSILON_M) {
                    waitingByApproach[i] = lead
                }
            }
            if (waitingByApproach.isEmpty()) continue

            val start = (simulation.mergeRightOfWay[node] ?: 0) % incoming.size
            val servedIndex = incoming.indices
                .map { (start + it) % incoming.size }
                .firstOrNull { waitingByApproach.containsKey(it) }
                ?: continue

            simulation.mergeGrant[node] = waitingByApproach.getValue(servedIndex)
        }
    }

    /**
     * The front-most active vehicle on [street], independent of whether it is already close enough
     * to the merge stop line.
     * 
     * @param street The street to find the lead vehicle on.
     * @return The lead vehicle on the street, or null if there is no lead vehicle.
     */
    private fun leadVehicle(street: Street): Vehicle? {
        var lead: Vehicle? = null
        var leadProgress = Double.NEGATIVE_INFINITY
        for (vehicle in simulation.level.vehicles) {
            if (vehicle.currentStreet !== street) continue
            val progress = SimulationUtils.progressAlongStreetM(vehicle)
            if (progress > leadProgress) {
                lead = vehicle
                leadProgress = progress
            }
        }
        return lead
    }

    /**
     * Whether [occupant] has driven clear of the merge [node] so the next approach may be granted.
     * The crossing is still busy while the occupant is on an incoming street of the node. Once it
     * has entered the outgoing street and its rear bumper has moved beyond the node plus the small
     * clearance margin, the next approach may start.
     * 
     * @param occupant The vehicle to check.
     * @param node The node to check against.
     * @return true if the occupant has cleared the merge, false otherwise.
     */
    private fun hasClearedMerge(occupant: Vehicle, node: ConnectionNode): Boolean {
        if (occupant !in simulation.level.vehicles) return true
        val street = occupant.currentStreet ?: return true
        if (street in node.incomingStreets) return false
        if (street in node.outgoingStreets) {
            return SimulationUtils.progressAlongStreetM(occupant) >= SimulationUtils.mergeClearanceDistanceM(occupant)
        }
        return true
    }
}
