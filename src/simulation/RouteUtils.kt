package simulation
import domain.*

/**
 * Utility functions for route-related calculations.
 */
object RouteUtils {
    /**
     * The nearest vehicle ahead of [vehicle] on the same street, or null if there is none. "Ahead"
     * means a strictly greater traveled distance along the street, so two vehicles at the exact same
     * position (for example, spawned together) never treat each other as a leader and cannot deadlock.
     * 
     * @param vehicle The vehicle to find the leading vehicle for.
     * @return The leading vehicle, or null if there is none.
     */
    fun leadingVehicleOnSameStreet(vehicle: Vehicle): Vehicle? {
        val street = vehicle.currentStreet ?: return null
        val myProgress = VehicleUtils.progressAlongStreetM(vehicle)
        var lead: Vehicle? = null
        var leadProgress = Double.POSITIVE_INFINITY
        for (other in vehicle.level.vehicles) {
            if (other === vehicle || other.currentStreet !== street) continue
            val otherProgress = VehicleUtils.progressAlongStreetM(other)
            if (otherProgress > myProgress && otherProgress < leadProgress) {
                lead = other
                leadProgress = otherProgress
            }
        }
        return lead
    }

    /**
     * The rear-most vehicle on the street [vehicle] will enter next along its route (the one closest
     * to the shared connection node), or null when the node is not a connection node, the route has
     * no continuation, or the next street is empty. "Rear-most" is the binding leader because it is
     * the first obstacle [vehicle] meets after crossing the node.
     *
     * @param vehicle the vehicle whose cross-node leader is sought.
     * @return the rear-most vehicle on [vehicle]'s next route street, or null when there is none.
     */
    fun leadingVehicleOnNextStreet(vehicle: Vehicle): Vehicle? {
        if (vehicle.currentStreet?.endNode !is ConnectionNode) return null
        val nextStreet = vehicle.nextStreetOnSelectedRoute() ?: return null
        var lead: Vehicle? = null
        var leadProgress = Double.POSITIVE_INFINITY
        for (other in vehicle.level.vehicles) {
            if (other === vehicle || other.currentStreet !== nextStreet) continue
            val progress = VehicleUtils.progressAlongStreetM(other)
            if (progress < leadProgress) {
                lead = other
                leadProgress = progress
            }
        }
        return lead
    }

    /**
     * Finds [street] in [route], preferring [expectedIndex] so repeated streets in cyclic networks
     * keep their current route position stable.
     * 
     * @param route The route to find the street in.
     * @param street The street to find.
     * @param expectedIndex The expected index of the street in the route.
     * @return The index of the street in the route, or null if not found.
     */
    fun routeStreetIndex(
        route: StreetPathFinder.DrivingRoute,
        street: Street,
        expectedIndex: Int
    ): Int? {
        if (route.streets.getOrNull(expectedIndex) === street) return expectedIndex
        val index = route.streets.indexOfFirst { it === street }
        return if (index >= 0) index else null
    }

    /**
     * The front-most active vehicle on [street], independent of whether it is already close enough
     * to the merge stop line.
     * 
     * @param street The street to find the lead vehicle on.
     * @param level The level to search in.
     * @return The lead vehicle on the street, or null if there is no lead vehicle.
     */
    fun leadVehicle(street: Street, level: Level): Vehicle? {
        var lead: Vehicle? = null
        var leadProgress = Double.NEGATIVE_INFINITY
        for (vehicle in level.vehicles) {
            if (vehicle.currentStreet !== street) continue
            val progress = VehicleUtils.progressAlongStreetM(vehicle)
            if (progress > leadProgress) {
                lead = vehicle
                leadProgress = progress
            }
        }
        return lead
    }

    /**
     * Checks whether every source configuration has delivered all configured vehicles.
     * 
     * @param level The level to check.
     * @return true if all configured vehicles have arrived, false otherwise.
     */
    fun allConfiguredVehiclesArrived(level: Level): Boolean {
        val configurations = level.nodes
            .filterIsInstance<SourceNode>()
            .flatMap { source -> source.configurations }
        return configurations.isNotEmpty() && configurations.all { it.vehiclesArrived >= it.vehiclesToSpawn }
    }

    /**
     * Gets the next street in the vehicle's route after the current street.
     * 
     * @param vehicle The vehicle to get the next street for.
     * @param street The current street.
     * @return The next street, or null if the vehicle cannot continue to the next street.
     */
    fun nextStreetAfter(vehicle: Vehicle, street: Street): Street? {
        val route = vehicle.selectedRoute ?: return null
        val streetIndex = routeStreetIndex(route, street, vehicle.currentRouteStreetIndex) ?: return null
        return route.streets.getOrNull(streetIndex + 1)
    }
}
