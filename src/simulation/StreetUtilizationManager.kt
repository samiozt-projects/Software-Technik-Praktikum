package simulation
import domain.*

/**
 * Manages street utilization calculations in the simulation.
 *
 * @property simulation The simulation instance.
 */
class StreetUtilizationManager(private val simulation: Simulation) {
    /**
     * Recomputes the normalized utilization of every street from the vehicles currently on
     * it. Runs once per tick *after* movement, so the value always reflects the post-move
     * state of the network. Streets without vehicles are reset to `0.0` (free flow), which
     * also clears streets a vehicle has just left or whose vehicle reached its target.
     */
    fun updateStreetUtilization() {
        val vehiclesByStreet = simulation.level.vehicles.groupBy { it.currentStreet }
        for (street in simulation.level.streets) {
            street.utilization = utilizationOf(street, vehiclesByStreet[street].orEmpty())
        }
    }

    /**
     * Normalized traffic load of a single street, combining two factors the way the
     * acceptance criteria describe (`0.0` = free flow, `1.0` = fully blocked):
     *
     *  * **Density** ([densityFactor]) — share of the street's length occupied by the
     *    vehicles currently on it; captures *how many* vehicles are present.
     *  * **Speed deficit** ([speedDeficitFactor]) — how far the vehicles' average speed
     *    falls short of the theoretical maximum speed achievable on this section.
     *
     * Both contribute additively, weighted by [Simulation.UTILIZATION_DENSITY_WEIGHT] and
     * [Simulation.UTILIZATION_SPEED_WEIGHT] (which sum to 1.0); the result is clamped to `[0.0, 1.0]`
     * for safety. An empty street carries no traffic and is free by definition.
     * 
     * @param street The street to calculate the utilization of.
     * @param vehicles The list of vehicles on the street.
     * @return The utilization of the street.
     */
    private fun utilizationOf(street: Street, vehicles: List<Vehicle>): Double {
        if (vehicles.isEmpty()) return 0.0

        val density = densityFactor(street, vehicles)
        val speedDeficit = speedDeficitFactor(street, vehicles)

        return (Simulation.UTILIZATION_DENSITY_WEIGHT * density + Simulation.UTILIZATION_SPEED_WEIGHT * speedDeficit)
            .coerceIn(0.0, 1.0)
    }

    /**
     * Fraction of the street's physical length occupied by the vehicles on it
     * (`Σ vehicle length / street length`), capped at 1.0. Vehicle lengths and street paths are
     * both stored in virtual metres. A degenerate street with zero length contributes no density.
     * 
     * @param street The street to calculate the density factor of.
     * @param vehicles The list of vehicles on the street.
     * @return The density factor of the street.
     */
    private fun densityFactor(street: Street, vehicles: List<Vehicle>): Double {
        val streetLengthMeters = SimulationUtils.streetLengthM(street.path)
        if (streetLengthMeters <= 0.0) return 0.0
        val occupiedMeters = vehicles.sumOf { it.type.length }
        return (occupiedMeters / streetLengthMeters).coerceIn(0.0, 1.0)
    }

    /**
     * How far the vehicles on the street currently lag behind what is physically possible
     * there: `1 − avgSpeed / avgMax`, where `avgSpeed` is their mean current speed and
     * `avgMax` is the mean of their per-vehicle theoretical section maxima
     * ([theoreticalMaxSpeedOnStreet]). Comparing against the theoretical section maximum
     * (rather than a fixed limit) means a street is *not* counted as congested merely
     * because a tight curve forces a low speed — only because the vehicles are slower than
     * that section allows. Clamped to `[0.0, 1.0]`; returns `0.0` if the section max is
     * non-positive (vehicles that cannot move are not treated as a jam).
     * 
     * @param street The street to calculate the speed deficit factor of.
     * @param vehicles The list of vehicles on the street.
     * @return The speed deficit factor of the street.
     */
    private fun speedDeficitFactor(street: Street, vehicles: List<Vehicle>): Double {
        val avgSpeed = vehicles.sumOf { it.currentSpeed } / vehicles.size
        val avgMaxSpeed = vehicles.sumOf { theoreticalMaxSpeedOnStreet(street, it.type) } / vehicles.size
        if (avgMaxSpeed <= 0.0) return 0.0
        return (1.0 - avgSpeed / avgMaxSpeed).coerceIn(0.0, 1.0)
    }

    /**
     * Theoretical maximum speed (m/s) a vehicle of [vehicleType] could travel on [street]:
     * its type max speed, lowered by the tightest weather-adjusted curve along the street's own polyline.
     * Reuses the same per-waypoint curve model as the acceleration logic
     * ([CurveSpeed.maxSpeedAtWaypoint]); a straight street (no inner waypoints) simply
     * yields the plain type max speed.
     * 
     * @param street The street to calculate the theoretical max speed of.
     * @param vehicleType The type of vehicle to calculate the theoretical max speed for.
     * @return The theoretical max speed of the vehicle on the street.
     */
    private fun theoreticalMaxSpeedOnStreet(street: Street, vehicleType: VehicleType): Double {
        var limit = vehicleType.maxSpeed * Simulation.KMH_TO_MS
        val path = street.path
        val effectiveAdhesionModifier = simulation.level.effectiveAdhesionCoefficientModifier()
        for (k in 1..path.size - 2) {
            val waypointLimit = CurveSpeed.maxSpeedAtWaypoint(
                path[k - 1], path[k], path[k + 1],
                vehicleType, effectiveAdhesionModifier
            )
            if (waypointLimit < limit) limit = waypointLimit
        }
        return limit
    }
}
