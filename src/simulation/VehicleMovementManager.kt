package simulation
import domain.*

/**
 * Manages vehicle movement in the simulation.
 *
 * @property simulation The simulation instance.
 */
class VehicleMovementManager(private val simulation: Simulation) {
    private val accelerationManager = VehicleAccelerationManager(simulation)

    /**
     * Applies the physical acceleration model to every active vehicle.
     *
     * @param dt The time delta for this simulation step.
     */
    fun accelerateVehicles(dt: Double) {
        accelerationManager.accelerateVehicles(dt)
    }

    /**
     * Moves all active vehicles along their current street and handles arrivals.
     *
     * The loop iterates over a snapshot because reaching a target removes the vehicle
     * from `level.vehicles`. This avoids mutating the list while it is being traversed.
     *
     * At a **merge** [ConnectionNode] (one with two or more incoming streets) only the
     * vehicle that currently holds the crossing (mergeOccupant, granted by
     * updateMergeGrants) may drive across; every other approaching vehicle has already
     * been held at its stop line by [distanceAllowedByMergeStopLine] and will not reach the node
     * (Lastenheft §3.3, Issue #92). A plain pass-through connection node (a single incoming
     * street) continues immediately via [advanceThroughConnection], unchanged from before.
     *  
     * @param dt The time step duration in seconds.
     */
    fun advanceVehicles(dt: Double) {
        for (vehicle in vehiclesInMovementOrder()) {
            if (vehicle !in simulation.level.vehicles) continue
            processVehicleMovement(vehicle, dt)
        }
    }

    /**
     * Processes the movement of a single vehicle for one simulation step.
     *
     * @param vehicle The vehicle to move
     * @param dt The time delta for this simulation step
     */
    private fun processVehicleMovement(vehicle: Vehicle, dt: Double) {
        val distanceM = vehicle.currentSpeed * dt
        
        // Apply movement constraints and update speed
        applyMovementConstraints(vehicle, distanceM)
        
        // Advance the vehicle along its path
        val reachedEnd = vehicle.advance(getMaxAllowedDistance(vehicle, distanceM))
        
        // Handle end-of-street scenarios
        if (reachedEnd) {
            handleVehicleAtStreetEnd(vehicle)
        }
    }

    /**
     * Applies all movement constraints to a vehicle and updates its speed accordingly.
     *
     * @param vehicle The vehicle to constrain
     * @param distanceM The desired movement distance
     */
    private fun applyMovementConstraints(vehicle: Vehicle, distanceM: Double) {
        // Apply merge stop line constraint
        val mergeAllowedM = distanceAllowedByMergeStopLine(vehicle, distanceM)
        if (mergeAllowedM < distanceM) {
            vehicle.currentSpeed = 0.0
        }
        
        // Apply leading vehicle constraint
        val leadingAllowedM = distanceAllowedByLeadingVehicle(vehicle, mergeAllowedM)
        if (leadingAllowedM < mergeAllowedM) {
            RouteUtils.leadingVehicleOnSameStreet(vehicle)?.let { lead ->
                vehicle.currentSpeed = minOf(vehicle.currentSpeed, lead.currentSpeed)
            }
        }
        
        // Apply merge proximity constraint
        val proximityAllowedM = distanceAllowedByMergeProximity(vehicle, leadingAllowedM)
        if (proximityAllowedM < leadingAllowedM) {
            vehicle.currentSpeed = 0.0
        }
        
        // Apply cross-node leader constraint
        val crossNodeAllowedM = distanceAllowedByCrossNodeLeader(vehicle, proximityAllowedM)
        if (crossNodeAllowedM < proximityAllowedM) {
            RouteUtils.leadingVehicleOnNextStreet(vehicle)?.let { lead ->
                vehicle.currentSpeed = minOf(vehicle.currentSpeed, lead.currentSpeed)
            }
        }
    }

    /**
     * Gets the maximum allowed movement distance considering all constraints.
     *
     * @param vehicle The vehicle
     * @param initialDistance The initial desired distance
     * @return The maximum allowed distance after applying all constraints
     */
    private fun getMaxAllowedDistance(vehicle: Vehicle, initialDistance: Double): Double {
        var allowedDistance = initialDistance
        
        // Apply constraints in order of priority
        allowedDistance = minOf(allowedDistance, distanceAllowedByMergeStopLine(vehicle, initialDistance))
        allowedDistance = minOf(allowedDistance, distanceAllowedByLeadingVehicle(vehicle, allowedDistance))
        allowedDistance = minOf(allowedDistance, distanceAllowedByMergeProximity(vehicle, allowedDistance))
        allowedDistance = minOf(allowedDistance, distanceAllowedByCrossNodeLeader(vehicle, allowedDistance))
        
        return allowedDistance
    }

    /**
     * Handles a vehicle that has reached the end of its current street.
     *
     * @param vehicle The vehicle that reached the street end
     */
    private fun handleVehicleAtStreetEnd(vehicle: Vehicle) {
        when (val endNode = vehicle.currentStreet?.endNode) {
            is TargetNode -> handleVehicleAtTarget(vehicle, endNode)
            is ConnectionNode -> handleVehicleAtConnectionNode(vehicle, endNode)
            else -> {}
        }
    }

    /**
     * Handles a vehicle that has reached a target node.
     *
     * The configured [simulation.VehicleTargetHandoverPort] is notified before the vehicle is
     * removed from the local level, so multi-table mode can forward coupled sink arrivals to the
     * neighboring table. Arrival counters are updated locally afterward for both regular and
     * handed-over targets.
     *
     * @param vehicle The vehicle
     * @param targetNode The target node reached
     */
    private fun handleVehicleAtTarget(vehicle: Vehicle, targetNode: TargetNode) {
        simulation.targetHandoverPort.handoverVehicle(vehicle, targetNode)

        // Arrival bookkeeping belongs to the domain layer, so rendering can stay passive.
        targetNode.vehiclesArrived++
        vehicle.sourceNodeConfiguration.vehiclesArrived++
        simulation.level.removeVehicle(vehicle)
        
        if (RouteUtils.allConfiguredVehiclesArrived(simulation.level)) {
            simulation.levelCompleted = true
            simulation.level.completed = true
        }
    }

    /**
     * Handles a vehicle that has reached a connection node.
     *
     * @param vehicle The vehicle
     * @param connectionNode The connection node reached
     */
    private fun handleVehicleAtConnectionNode(vehicle: Vehicle, connectionNode: ConnectionNode) {
        /**
         * Single-incoming node: plain pass-through. Merge node: only the occupant
         * or the vehicle granted at the stop line may cross; any other vehicle that
         * reached the end while yielding is placed back on its stop line.
         */
        if (connectionNode.incomingStreets.size < 2 || mayEnterMerge(vehicle, connectionNode)) {
            enterMerge(vehicle, connectionNode)
            advanceThroughConnection(vehicle)
        } else {
            moveToMergeStopLine(vehicle)
            vehicle.currentSpeed = 0.0
        }
    }

    /**
     * Movement order for one tick. Vehicles on the same street are advanced front-to-back, so a
     * follower clamps against the leader's updated position instead of the previous-frame position.
     * Vehicles on different streets keep their original list to avoid changing merge priority.
     * 
     * @return The list of vehicles in movement order.
     */
    private fun vehiclesInMovementOrder(): List<Vehicle> {
        val originalIndex = simulation.level.vehicles.withIndex().associate { it.value to it.index }
        return simulation.level.vehicles.toList().sortedWith(Comparator { left, right ->
            val leftStreet = left.currentStreet
            if (leftStreet != null && leftStreet === right.currentStreet) {
                val byProgress = VehicleUtils.progressAlongStreetM(right).compareTo(VehicleUtils.progressAlongStreetM(left))
                if (byProgress != 0) byProgress else originalIndex.getValue(left).compareTo(originalIndex.getValue(right))
            } else {
                originalIndex.getValue(left).compareTo(originalIndex.getValue(right))
            }
        })
    }

    /**
     * Continues [vehicle] onto the next street along its route.
     *
     * The next street is chosen from the precomputed route options stored on the vehicle.
     * If the selected route has no continuation, the vehicle has nowhere to go and is
     * dropped from the simulation.
     * 
     * @param vehicle The vehicle to continue onto the next street.
     */
    private fun advanceThroughConnection(vehicle: Vehicle) {
        val nextStreet = vehicle.nextStreetOnSelectedRoute()
        if (nextStreet != null) {
            vehicle.currentStreet = nextStreet
            vehicle.currentRouteStreetIndex++
            vehicle.currentSegmentIndex = 0
            vehicle.distanceAlongSegment = 0.0
        } else {
            simulation.level.removeVehicle(vehicle)
        }
    }

    /**
     * Caps this tick's movement for a yielding vehicle so it cannot physically advance beyond its
     * fixed stop line. Vehicles do not reserve or brake for a merge far in advance; this movement
     * cap is the point where a yielding vehicle actually stops at the line.
     * 
     * @param vehicle The vehicle to calculate the distance allowed by the merge stop line for.
     * @param requestedDistanceM The distance the vehicle would otherwise move.
     * @return The distance the vehicle is allowed to move.
     */
    private fun distanceAllowedByMergeStopLine(vehicle: Vehicle, requestedDistanceM: Double): Double {
        val node = vehicle.currentStreet?.endNode as? ConnectionNode ?: return requestedDistanceM
        if (!accelerationManager.mustYieldAtMerge(vehicle, node)) return requestedDistanceM

        val remainingToStopLineM = VehicleUtils.remainingDistanceToStopLineM(vehicle)
        if (remainingToStopLineM <= 0.0) {
            moveToMergeStopLine(vehicle)
            return 0.0
        }
        return minOf(requestedDistanceM, remainingToStopLineM)
    }

    /**
     * Places [vehicle] exactly at the merge stop line of its current street. This recovers vehicles
     * that already reached the connection node while yielding and keeps rendering aligned with the
     * shared front-bumper stop line ([Simulation.STOP_LINE_FRONT_OFFSET_M]).
     * 
     * @param vehicle The vehicle to move to the merge stop line.
     */
    private fun moveToMergeStopLine(vehicle: Vehicle) {
        val path = vehicle.currentStreet?.path ?: return
        if (path.size < 2) return

        val targetProgressM = maxOf(0.0, StreetUtils.streetLengthM(path) - VehicleUtils.stopLineDistanceM(vehicle))
        var remaining = targetProgressM
        var segment = 0
        while (segment < path.size - 1) {
            val length = StreetUtils.segmentLengthM(path, segment)
            if (remaining <= length) {
                vehicle.currentSegmentIndex = segment
                vehicle.distanceAlongSegment = remaining
                return
            }
            remaining -= length
            segment++
        }

        vehicle.currentSegmentIndex = path.size - 2
        vehicle.distanceAlongSegment = StreetUtils.segmentLengthM(path, path.size - 2)
    }

    /**
     * Caps this tick's movement so [vehicle]'s front never advances closer than [Simulation.MIN_DISTANCE_M] to
     * the rear of the vehicle ahead of it on the same street (Lastenheft §3.3, Issue #105). This is
     * the hard guarantee that two vehicles never drive into each other: even with a coarse `dt` a
     * follower cannot overshoot into the minimum gap. The leader only ever moves forward within a
     * tick, so clamping against its current position keeps the post-tick gap at least [Simulation.MIN_DISTANCE_M].
     * 
     * @param vehicle The vehicle to calculate the distance allowed by the leading vehicle for.
     * @param requestedDistanceM The distance the vehicle would otherwise move.
     * @return The distance the vehicle is allowed to move.
     */
    private fun distanceAllowedByLeadingVehicle(vehicle: Vehicle, requestedDistanceM: Double): Double {
        val lead = RouteUtils.leadingVehicleOnSameStreet(vehicle) ?: return requestedDistanceM
        val allowedM = VehicleUtils.clearDistanceToLeaderM(vehicle, lead) - Simulation.MIN_DISTANCE_M
        if (allowedM <= 0.0) return 0.0
        return minOf(requestedDistanceM, allowedM)
    }

    /**
     * Caps this tick's movement so [vehicle] keeps a 2D minimum clearance to vehicles converging on
     * the merge node at the end of its current street from a *different* incoming approach.
     *
     * The per-street rules ([distanceAllowedByLeadingVehicle], [distanceAllowedByMergeStopLine]) only
     * ever compare vehicles on the same polyline, so where two approaches meet a merge at a sharp
     * (near-parallel) angle their queues are unaware of each other and render on top of one another
     * near the node. This clamp closes that gap in 2D: a vehicle may not advance so far that its
     * rendered centre comes within half of both lengths plus [Simulation.MERGE_PROXIMITY_CLEARANCE_M] of another
     * approaching vehicle that has right of way over it. 
     *
     * Right of way ([mergeProximityHasRightOfWay]) is a strict total order built on the existing merge
     * state, so exactly one vehicle of every conflicting pair yields and the highest-priority approach
     * is never clamped — which is what keeps the rule deadlock-free. Vehicles already past the node (on
     * an outgoing street) are intentionally ignored here; their spacing stays governed by
     * [Simulation.MERGE_CLEARANCE_M] and the same-street following rule.
     *
     * @param vehicle the approaching vehicle whose movement this tick is being limited.
     * @param requestedDistanceM the distance (metres) the vehicle would otherwise move this tick.
     * @return the permitted distance (metres): never more than [requestedDistanceM] and never so far
     *   that the vehicle would come within the required clearance of a higher-priority vehicle on
     *   another approach.
     */
    private fun distanceAllowedByMergeProximity(vehicle: Vehicle, requestedDistanceM: Double): Double {
        if (requestedDistanceM <= 0.0) return requestedDistanceM
        val node = vehicle.currentStreet?.endNode as? ConnectionNode ?: return requestedDistanceM
        if (node.incomingStreets.size < 2) return requestedDistanceM

        val position = vehicle.calculatePosition() ?: return requestedDistanceM
        val direction = VehicleUtils.currentSegmentDirection(vehicle) ?: return requestedDistanceM

        var allowed = requestedDistanceM
        for (other in simulation.level.vehicles) {
            if (other === vehicle) continue
            val otherStreet = other.currentStreet ?: continue
            // Same-street pairs and vehicles already past the node are handled elsewhere; only other
            // incoming approaches of this merge can overlap unseen by the 1D rules.
            if (otherStreet === vehicle.currentStreet) continue
            if (otherStreet !in node.incomingStreets) continue
            if (!mergeProximityHasRightOfWay(other, vehicle, node)) continue

            val otherPosition = other.calculatePosition() ?: continue
            val requiredGapM = (vehicle.type.length + other.type.length) / 2.0 + Simulation.MERGE_PROXIMITY_CLEARANCE_M
            allowed = minOf(allowed, VehicleUtils.advanceLimitedTowardPoint(position, direction, otherPosition, requiredGapM, allowed))
            if (allowed <= 0.0) return 0.0
        }
        return allowed
    }

    /**
     * Whether [other] has right of way over [vehicle] at the merge [node], i.e. [vehicle] must keep
     * clear of [other]. The order is total and deterministic so the proximity clamp never makes two
     * vehicles yield to each other:
     *  1. the merge occupant outranks the granted vehicle, which outranks every plain approach;
     *  2. on equal rank the vehicle physically closer to its stop line wins;
     *  3. ties are broken by incoming-approach index, then identity, to stay a strict total order.
     *
     * @param other the other vehicle converging on the same merge node.
     * @param vehicle the vehicle whose movement is being limited.
     * @param node the shared merge connection node.
     * @return true when [other] has right of way, so [vehicle] must keep clear of it.
     */
    private fun mergeProximityHasRightOfWay(other: Vehicle, vehicle: Vehicle, node: ConnectionNode): Boolean {
        val rankOther = mergeProximityRank(other, node)
        val rankVehicle = mergeProximityRank(vehicle, node)
        if (rankOther != rankVehicle) return rankOther > rankVehicle

        val distanceOther = VehicleUtils.remainingDistanceToStopLineM(other)
        val distanceVehicle = VehicleUtils.remainingDistanceToStopLineM(vehicle)
        if (distanceOther != distanceVehicle) return distanceOther < distanceVehicle

        val indexOther = node.incomingStreets.indexOf(other.currentStreet)
        val indexVehicle = node.incomingStreets.indexOf(vehicle.currentStreet)
        if (indexOther != indexVehicle) return indexOther < indexVehicle
        return System.identityHashCode(other) > System.identityHashCode(vehicle)
    }

    /**
     * Right-of-way rank of [vehicle] at the merge [node], used as the primary key of the proximity
     * order: the occupant currently crossing outranks the vehicle granted to go next, which outranks
     * every plain approaching vehicle.
     *
     * @param vehicle the vehicle to rank.
     * @param node the merge connection node.
     * @return 2 for the merge occupant, 1 for the granted vehicle, 0 otherwise.
     */
    private fun mergeProximityRank(vehicle: Vehicle, node: ConnectionNode): Int = when {
        simulation.mergeOccupant[node] === vehicle -> 2
        simulation.mergeGrant[node] === vehicle -> 1
        else -> 0
    }

    /**
     * Caps this tick's movement so [vehicle]'s front keeps the [Simulation.MIN_DISTANCE_M] following gap to the
     * rear of the vehicle ahead of it *across the connection node* at the end of its current street —
     * i.e. the rear-most vehicle already on the street it will turn onto next. The per-street
     * [distanceAllowedByLeadingVehicle] clamp cannot see past the node, so without this a queue
     * "nachrücken" through an intersection would place each follower on top of the leader that just
     * crossed (only [Simulation.MERGE_CLEARANCE_M] ahead). This is the same hard no-overlap guarantee as the
     * same-street rule, extended one node ahead along the vehicle's route.
     *
     * @param vehicle the vehicle approaching the connection node.
     * @param requestedDistanceM the distance (metres) it would otherwise move this tick.
     * @return the permitted distance (metres), reduced so the [Simulation.MIN_DISTANCE_M] gap to the leader on
     *   the next route street is preserved across the node.
     */
    private fun distanceAllowedByCrossNodeLeader(vehicle: Vehicle, requestedDistanceM: Double): Double {
        val lead = RouteUtils.leadingVehicleOnNextStreet(vehicle) ?: return requestedDistanceM
        val allowedM = VehicleUtils.clearDistanceToCrossNodeLeaderM(vehicle, lead) - Simulation.MIN_DISTANCE_M
        if (allowedM <= 0.0) return 0.0
        return minOf(requestedDistanceM, allowedM)
    }

    /**
     * Whether [vehicle] may enter [node] right now.
     *
     * @param vehicle The vehicle to check.
     * @param node The node to check against.
     * @return true if the vehicle may enter the merge, false otherwise.
     */
    private fun mayEnterMerge(vehicle: Vehicle, node: ConnectionNode): Boolean {
        if (node.incomingStreets.size < 2) return true
        val occupant = simulation.mergeOccupant[node]
        if (occupant != null && occupant !== vehicle && !hasClearedMerge(occupant, node)) return false
        val grant = simulation.mergeGrant[node] ?: return true
        return grant === vehicle
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
    internal fun hasClearedMerge(occupant: Vehicle, node: ConnectionNode): Boolean {
        if (occupant !in simulation.level.vehicles) return true
        val street = occupant.currentStreet ?: return true
        if (street in node.incomingStreets) return false
        if (street in node.outgoingStreets) {
            return VehicleUtils.progressAlongStreetM(occupant) >= VehicleUtils.mergeClearanceDistanceM(occupant)
        }
        return true
    }

    /**
     * Marks [vehicle] as the physical user of [node] once it reaches the merge. This is the point
     * where the zipper pointer advances; far-away vehicles never advance the pointer by merely
     * existing on an approach.
     *
     * @param vehicle The vehicle to enter the merge.
     * @param node The node to enter the merge at.
     */
    private fun enterMerge(vehicle: Vehicle, node: ConnectionNode) {
        if (node.incomingStreets.size < 2) return
        val approachIndex = node.incomingStreets.indexOf(vehicle.currentStreet)
        simulation.mergeOccupant[node] = vehicle
        simulation.mergeGrant.remove(node)
        if (approachIndex >= 0) {
            simulation.mergeRightOfWay[node] = (approachIndex + 1) % node.incomingStreets.size
        }
    }
}
