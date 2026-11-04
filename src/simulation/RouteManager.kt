package simulation
import domain.*

/**
 * Manages route-related logic in the simulation.
 *
 * @property simulation The simulation instance.
 */
class RouteManager(private val simulation: Simulation) {
    /**
     * Lets active vehicles use one of their stored longer route options when the currently selected
     * remaining route is congested. The current street is never changed here; a candidate route must
     * contain that street, so the actual turn changes only at the next connection node.
     */
    fun rerouteVehiclesAroundCongestion() {
        for (vehicle in simulation.level.vehicles) {
            val currentStreet = vehicle.currentStreet ?: continue
            val selectedRoute = vehicle.selectedRoute ?: continue
            val selectedStreetIndex = SimulationUtils.routeStreetIndex(selectedRoute, currentStreet, vehicle.currentRouteStreetIndex)
                ?: continue

            vehicle.currentRouteStreetIndex = selectedStreetIndex
            val selectedUpcomingStreets = selectedRoute.streets.drop(selectedStreetIndex + 1)
            val selectedLoad = routeCongestion(selectedUpcomingStreets)
            if (selectedLoad < Simulation.ROUTE_SWITCH_CONGESTION_THRESHOLD) continue

            val alternative = bestReachableRouteAlternative(vehicle, currentStreet, selectedLoad) ?: continue
            vehicle.selectedRouteIndex = alternative.routeIndex
            vehicle.currentRouteStreetIndex = alternative.currentStreetIndex
        }
    }

    /**
     * Finds the best stored route that can still be followed from [currentStreet] and improves on
     * [selectedLoad]. Prefer an uncongested route; otherwise choose the lower-load route with the
     * shortest remaining Euclidean distance.
     * 
     * @param vehicle The vehicle to find the best alternative route for.
     * @param currentStreet The current street of the vehicle.
     * @param selectedLoad The load of the currently selected route.
     * @return The best alternative route, or null if no alternative route is found.
     */
    private fun bestReachableRouteAlternative(
        vehicle: Vehicle,
        currentStreet: Street,
        selectedLoad: Double
    ): RouteSwitchCandidate? {
        val remainingOnCurrentStreet = SimulationUtils.remainingDistanceToEndM(vehicle)
        return vehicle.drivingRouteOptions
            .mapIndexedNotNull { index, route ->
                val currentStreetIndex = SimulationUtils.routeStreetIndex(route, currentStreet, vehicle.currentRouteStreetIndex)
                    ?: return@mapIndexedNotNull null
                val upcomingStreets = route.streets.drop(currentStreetIndex + 1)
                val load = routeCongestion(upcomingStreets)
                if (index == vehicle.selectedRouteIndex) return@mapIndexedNotNull null
                if (load + Simulation.ROUTE_SWITCH_LOAD_EPSILON >= selectedLoad) return@mapIndexedNotNull null

                RouteSwitchCandidate(
                    routeIndex = index,
                    currentStreetIndex = currentStreetIndex,
                    load = load,
                    remainingLength = remainingOnCurrentStreet + upcomingStreets.sumOf { SimulationUtils.streetLengthM(it.path) }
                )
            }
            .minWithOrNull(routeSwitchOrdering())
    }

    /**
     * Data class for storing route switch candidates.
     * @property routeIndex Index of the route.
     * @property currentStreetIndex Index of the current street.
     * @property load Load of the route.
     * @property remainingLength Remaining length of the route.
     */
    private data class RouteSwitchCandidate(
        val routeIndex: Int,
        val currentStreetIndex: Int,
        val load: Double,
        val remainingLength: Double
    )

    /**
     * Sorts route choices by congestion first, then by the remaining Euclidean distance.
     * @return A comparator for sorting route choices.
     */
    private fun routeSwitchOrdering(): Comparator<RouteSwitchCandidate> =
        compareBy<RouteSwitchCandidate> { it.load >= Simulation.ROUTE_SWITCH_CONGESTION_THRESHOLD }
            .thenBy { it.load }
            .thenBy { it.remainingLength }
            .thenBy { it.routeIndex }

    /**
     * Maximum utilization over [streets], or free flow when the route has no remaining street.
     * Calculates the congestion of a route.
     * 
     * @param streets The list of streets in the route.
     * @return The congestion of the route.
     */
    private fun routeCongestion(streets: List<Street>): Double =
        streets.maxOfOrNull { it.utilization } ?: 0.0
}
