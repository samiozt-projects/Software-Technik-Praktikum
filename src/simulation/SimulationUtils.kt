package simulation

import domain.*

/**
 * Utility functions for the simulation.
 * This object serves as a central point for accessing various utility functions
 * from the more specialized utility objects.
 */
object SimulationUtils {
    // Delegate to StreetUtils
    /** 
     * Returns the length of a specific segment of a street path in metres.
     * @param path The street path.
     * @param index The index of the segment.
     * @return The length of the segment in metres.
     */
    fun segmentLengthM(path: List<korlibs.math.geom.Point>, index: Int): Double =
        StreetUtils.segmentLengthM(path, index)

    /**
     * Returns the total length of a street path in metres.
     * @param path The street path.
     * @return The total length of the street path in metres.
     */
    fun streetLengthM(path: List<korlibs.math.geom.Point>): Double =
        StreetUtils.streetLengthM(path)

    // Delegate to VehicleUtils
    /**
     * Returns the progress of a vehicle along its current street in metres.
     * @param vehicle The vehicle to get the progress of.
     * @return The progress of the vehicle along its current street in metres.
     */
    fun progressAlongStreetM(vehicle: Vehicle): Double =
        VehicleUtils.progressAlongStreetM(vehicle)

    /**
     * Returns the remaining distance of a vehicle to the end of its current street in metres.
     * @param vehicle The vehicle to get the remaining distance of.
     * @return The remaining distance of the vehicle to the end of its current street in metres.
     */
    fun remainingDistanceToEndM(vehicle: Vehicle): Double =
        VehicleUtils.remainingDistanceToEndM(vehicle)

    /**
     * Returns the remaining distance of a vehicle to the stop line of its current street in metres.
     * @param vehicle The vehicle to get the remaining distance of.
     * @return The remaining distance of the vehicle to the stop line of its current street in metres.
     */
    fun remainingDistanceToStopLineM(vehicle: Vehicle): Double =
        VehicleUtils.remainingDistanceToStopLineM(vehicle)

    /**
     * Returns the distance of a vehicle to the stop line of its current street in metres.
     * @param vehicle The vehicle to get the distance of.
     * @return The distance of the vehicle to the stop line of its current street in metres.
     */
    fun stopLineDistanceM(vehicle: Vehicle): Double =
        VehicleUtils.stopLineDistanceM(vehicle)

    /**
     * Returns the clearance distance of a vehicle to the merge node of its current street in metres.
     * @param vehicle The vehicle to get the clearance distance of.
     * @return The clearance distance of the vehicle to the merge node of its current street in metres.
     */
    fun mergeClearanceDistanceM(vehicle: Vehicle): Double =
        VehicleUtils.mergeClearanceDistanceM(vehicle)

    /**
     * Returns the clear distance of a vehicle to the leader vehicle in metres.
     * @param follower The follower vehicle.
     * @param lead The leader vehicle.
     * @return The clear distance of the follower vehicle to the leader vehicle in metres.
     */
    fun clearDistanceToLeaderM(follower: Vehicle, lead: Vehicle): Double =
        VehicleUtils.clearDistanceToLeaderM(follower, lead)

    /**
     * Returns the clear distance of a vehicle to the leader vehicle on the cross-node street in metres.
     * @param follower The follower vehicle.
     * @param lead The leader vehicle.
     * @return The clear distance of the follower vehicle to the leader vehicle on the cross-node street in metres.
     */
    fun clearDistanceToCrossNodeLeaderM(follower: Vehicle, lead: Vehicle): Double =
        VehicleUtils.clearDistanceToCrossNodeLeaderM(follower, lead)

    /**
     * Returns the direction of the current segment of a vehicle in metres.
     * @param vehicle The vehicle to get the direction of.
     * @return The direction of the current segment of the vehicle in metres.
     */
    fun currentSegmentDirection(vehicle: Vehicle): korlibs.math.geom.Point? =
        VehicleUtils.currentSegmentDirection(vehicle)

    /**
     * Advances a point toward a target point limited by a required gap and a current limit.
     * @param position The current position.
     * @param direction The direction of the advance.
     * @param target The target position.
     * @param requiredGapM The required gap in metres.
     * @param currentLimitM The current limit in metres.
     * @return The advanced position limited by the required gap and the current limit.
     */
    fun advanceLimitedTowardPoint(
        position: korlibs.math.geom.Point,
        direction: korlibs.math.geom.Point,
        target: korlibs.math.geom.Point,
        requiredGapM: Double,
        currentLimitM: Double
    ): Double =
        VehicleUtils.advanceLimitedTowardPoint(position, direction, target, requiredGapM, currentLimitM)

    // Delegate to RouteUtils
    /**
     * Returns the leading vehicle on the same street as the given vehicle.
     * @param vehicle The vehicle to get the leading vehicle of.
     * @return The leading vehicle on the same street as the given vehicle.
     */
    fun leadingVehicleOnSameStreet(vehicle: Vehicle): Vehicle? =
        RouteUtils.leadingVehicleOnSameStreet(vehicle)

    /**
     * Returns the leading vehicle on the next street after the given vehicle.
     * @param vehicle The vehicle to get the leading vehicle of.
     * @return The leading vehicle on the next street after the given vehicle.
     */
    fun leadingVehicleOnNextStreet(vehicle: Vehicle): Vehicle? =
        RouteUtils.leadingVehicleOnNextStreet(vehicle)

    /**
     * Returns the index of a street in a route.
     * @param route The route.
     * @param street The street.
     * @param expectedIndex The expected index of the street.
     * @return The index of the street in the route.
     */
    fun routeStreetIndex(
        route: StreetPathFinder.DrivingRoute,
        street: Street,
        expectedIndex: Int
    ): Int? =
        RouteUtils.routeStreetIndex(route, street, expectedIndex)

    /**
     * Returns the leading vehicle on the street in the given level.
     * @param street The street.
     * @param level The level.
     * @return The leading vehicle on the street in the level.
     */
    fun leadVehicle(street: Street, level: Level): Vehicle? =
        RouteUtils.leadVehicle(street, level)

    /**
     * Returns true if all configured vehicles have arrived at their destinations in the given level.
     * @param level The level.
     * @return True if all configured vehicles have arrived at their destinations.
     */
    fun allConfiguredVehiclesArrived(level: Level): Boolean =
        RouteUtils.allConfiguredVehiclesArrived(level)

    /**
     * Returns the next street after the given vehicle in the given level.
     * @param vehicle The vehicle to get the next street of.
     * @param street The street.
     * @return The next street after the given vehicle in the given level.
     */
    fun nextStreetAfter(vehicle: Vehicle, street: Street): Street? =
        RouteUtils.nextStreetAfter(vehicle, street)
}
