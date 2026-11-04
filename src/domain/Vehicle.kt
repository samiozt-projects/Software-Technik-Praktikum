package domain

import korlibs.math.geom.Point
import kotlin.math.hypot

/**
 * Represents a vehicle in the simulation.
 *
 * @property color The color of the vehicle.
 * @property type The type of the vehicle, defining its physical properties.
 * @property sourceNode The node from which the vehicle originates.
 * @property sourceNodeConfiguration The configuration of the source node.
 * @property level The level the vehicle is in.
 * @property averageSpeed The average speed of the vehicle.
 * @property currentSpeed The current speed of the vehicle.
 * @property currentStreet The street the vehicle is currently on.
 * @property currentSegmentIndex The index of the current path segment the vehicle is on.
 * @property distanceAlongSegment The distance the vehicle has traveled along the current segment.
 */
class Vehicle(
    var color: Color,
    val type: VehicleType,
    val sourceNode: SourceNode,
    val sourceNodeConfiguration: SourceNodeConfiguration,
    val level: Level
) {
    var averageSpeed: Double = 0.0
    var currentSpeed: Double = 0.0

    // Tracking properties
    var currentStreet: Street? = null
    var currentSegmentIndex: Int = 0
    var distanceAlongSegment: Double = 0.0

    /** Stored directed route options to matching targets, ordered from shortest to longer paths. */
    var drivingRouteOptions: List<StreetPathFinder.DrivingRoute> = emptyList()

    /** Index of the currently selected route inside [drivingRouteOptions]. */
    var selectedRouteIndex: Int = 0

    /** Index of [currentStreet] inside the selected route. */
    var currentRouteStreetIndex: Int = 0

    /** The currently selected route. */
    val selectedRoute: StreetPathFinder.DrivingRoute?
        get() = drivingRouteOptions.getOrNull(selectedRouteIndex)

    /**
     * Next street on the selected route, or null once the vehicle has no route continuation.
     * @return Next street on the selected route, or null once the vehicle has no route continuation.
    */
    fun nextStreetOnSelectedRoute(): Street? {
        val route = selectedRoute ?: return null
        val nextIndex = currentRouteStreetIndex + 1
        return route.streets.getOrNull(nextIndex)
    }

    /**
     * Moves the vehicle forward along its current street by [distance] virtual metres.
     *
     * Streets are stored as polylines. The vehicle therefore has to remember both the
     * segment it is currently on and the distance already travelled on that segment.
     * The method consumes the requested movement step segment by segment and returns
     * true once the final point of the street path has been reached.
     *
     * @param distance The distance to move the vehicle in virtual metres.
     * @return true if the vehicle reached or overshot the end of its current street.
     */
    fun advance(distance: Double): Boolean {
        // A vehicle without an assigned street cannot move yet, for example before spawning.
        val street = currentStreet ?: return false
        val path = street.path
        // `remaining` is the part of this movement step that still has to be applied.
        var remaining = distance

        while (remaining > 0.0 && currentSegmentIndex < path.size - 1) {
            val p1 = path[currentSegmentIndex]
            val p2 = path[currentSegmentIndex + 1]
            // Length of the current polyline segment in virtual metres, independent of its angle.
            val segmentLength = hypot(
                p2.x - p1.x,
                p2.y - p1.y
            )
            // Only the not-yet-travelled part of the current segment can be used here.
            val available = segmentLength - distanceAlongSegment
            if (remaining < available) {
                // The movement ends inside the current segment, so only the offset changes.
                distanceAlongSegment += remaining
                remaining = 0.0
            } else {
                // Consume the rest of the segment and continue with the next one.
                remaining -= available
                currentSegmentIndex++
                distanceAlongSegment = 0.0
            }
        }

        // Reached end of path → clamp at last point and signal completion.
        if (currentSegmentIndex >= path.size - 1) {
            currentSegmentIndex = path.size - 1
            distanceAlongSegment = 0.0
            return true
        }
        return false
    }

    /**
     * Calculates the exact 2D virtual-metre coordinate of the vehicle for rendering purposes.
     * Returns null if the vehicle is not currently on a street (e.g. waiting in an intersection).
     * @return The exact 2D virtual-metre coordinate of the vehicle for rendering purposes.
     */
    fun calculatePosition(): Point? {
        // Rendering cannot place the vehicle until the domain assigned it to a street.
        val street = currentStreet ?: return null
        val path = street.path
        
        // A drawable position requires at least one segment; a finished path uses its final point.
        if (path.size < 2) return null
        if (currentSegmentIndex >= path.size - 1) return path.last()
        
        val p1 = path[currentSegmentIndex]
        val p2 = path[currentSegmentIndex + 1]
        
        // Direction vector of the current segment in virtual metres.
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        
        val segmentLength = hypot(dx, dy)
        
        // Degenerate segments can occur in malformed paths; falling back to p1 avoids division by zero.
        if (segmentLength == 0.0) return p1
        
        // Linear interpolation factor from p1 (0.0) to p2 (1.0).
        val ratio = distanceAlongSegment / segmentLength
        
        return Point(
            p1.x + dx * ratio,
            p1.y + dy * ratio
        )
    }
}
