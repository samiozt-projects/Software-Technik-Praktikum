package simulation
import domain.*

import korlibs.math.geom.Point
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Utility functions for vehicle-related calculations.
 */
object VehicleUtils {
    /**
     * Distance in virtual metres [vehicle]'s front has traveled from the start of its current street: every
     * fully completed segment plus the offset within the current one. Returns 0 with no street.
     * 
     * @param vehicle The vehicle to calculate the progress along the street for.
     * @return The progress along the street in virtual metres.
     */
    fun progressAlongStreetM(vehicle: Vehicle): Double {
        val path = vehicle.currentStreet?.path ?: return 0.0
        var sum = 0.0
        var i = 0
        while (i < vehicle.currentSegmentIndex && i < path.size - 1) {
            sum += StreetUtils.segmentLengthM(path, i)
            i++
        }
        return sum + vehicle.distanceAlongSegment
    }

    /**
     * Remaining distance in virtual metres from [vehicle]'s front to the end node of its current street: the
     * not-yet-traveled part of the current segment plus every later segment. Returns 0 when the
     * vehicle has no street or is clamped at the final path point.
     * 
     * @param vehicle The vehicle to calculate the remaining distance to the end for.
     * @return The remaining distance to the end in virtual metres.
     */
    fun remainingDistanceToEndM(vehicle: Vehicle): Double {
        val path = vehicle.currentStreet?.path ?: return 0.0
        if (vehicle.currentSegmentIndex >= path.size - 1) return 0.0
        var sum = StreetUtils.segmentLengthM(path, vehicle.currentSegmentIndex) - vehicle.distanceAlongSegment
        var i = vehicle.currentSegmentIndex + 1
        while (i < path.size - 1) {
            sum += StreetUtils.segmentLengthM(path, i)
            i++
        }
        return sum
    }

    /**
     * Remaining front-to-stop-line distance for [vehicle]. Negative values mean the vehicle front
     * has already passed the stop line and should be clamped back if it is yielding. The stop line
     * is a shared front-bumper offset translated to the rendered center position by
     * [stopLineDistanceM].
     * 
     * @param vehicle The vehicle to calculate the remaining distance to the stop line for.
     * @return The remaining distance to the stop line in virtual metres.
     */
    fun remainingDistanceToStopLineM(vehicle: Vehicle): Double =
        remainingDistanceToEndM(vehicle) - stopLineDistanceM(vehicle)

    /**
     * Center-to-node distance at which a yielding vehicle should stop. Vehicle positions are
     * rendered around their center, so the shared front-bumper stop line is translated by half the
     * current vehicle length.
     * 
     * @param vehicle The vehicle to calculate the stop line distance for.
     * @return The stop line distance in virtual metres.
     */
    fun stopLineDistanceM(vehicle: Vehicle): Double =
        Simulation.STOP_LINE_FRONT_OFFSET_M + vehicle.type.length / 2.0

    /**
     * Center progress on the outgoing street needed before the previous occupant's rear bumper has
     * cleared the merge node by [Simulation.MERGE_CLEARANCE_M].
     * 
     * @param vehicle The vehicle to calculate the merge clearance distance for.
     * @return The merge clearance distance in virtual metres.
     */
    fun mergeClearanceDistanceM(vehicle: Vehicle): Double =
        vehicle.type.length / 2.0 + Simulation.MERGE_CLEARANCE_M

    /**
     * Clear bumper-to-tail distance (metres) from [follower]'s front to [lead]'s rear on their shared
     * street. Vehicle sprites are rendered around their traveled center position, so the visible
     * gap is the center-to-center distance minus half of both vehicle lengths. Negative when the two
     * vehicles overlap.
     * 
     * @param follower The follower vehicle.
     * @param lead The leader vehicle.
     * @return The clear bumper-to-tail distance in metres.
     */
    fun clearDistanceToLeaderM(follower: Vehicle, lead: Vehicle): Double {
        val occupiedBetweenCentersM = (lead.type.length + follower.type.length) / 2.0
        return progressAlongStreetM(lead) - progressAlongStreetM(follower) - occupiedBetweenCentersM
    }

    /**
     * Clear bumper-to-tail distance (metres) from [follower]'s front to [lead]'s rear when [lead] is
     * on [follower]'s next route street: the remaining distance to the node plus [lead]'s progress
     * past it, minus half of both vehicle lengths. Negative when the two would overlap.
     *
     * @param follower the approaching vehicle, still before the node.
     * @param lead the leader already on [follower]'s next route street.
     * @return the clear bumper-to-tail distance (metres) across the node; negative when they overlap.
     */
    fun clearDistanceToCrossNodeLeaderM(follower: Vehicle, lead: Vehicle): Double {
        val occupiedBetweenCentersM = (lead.type.length + follower.type.length) / 2.0
        return remainingDistanceToEndM(follower) + progressAlongStreetM(lead) - occupiedBetweenCentersM
    }

    /**
     * Unit heading vector of [vehicle] along the polyline segment it is currently on. Used by the 2D
     * proximity clamp to project distances onto the direction of travel.
     *
     * @param vehicle the vehicle whose heading is needed.
     * @return the normalized direction of the current segment, or null when the vehicle is clamped at
     *   the end of its path (no remaining segment) or the segment has zero length.
     */
    fun currentSegmentDirection(vehicle: Vehicle): Point? {
        val path = vehicle.currentStreet?.path ?: return null
        val index = vehicle.currentSegmentIndex
        if (index >= path.size - 1) return null
        val p1 = path[index]
        val p2 = path[index + 1]
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val length = hypot(dx, dy)
        if (length == 0.0) return null
        return Point(dx / length, dy / length)
    }

    /**
     * Largest forward distance (≤ [currentLimitM]) the point [position] may travel along the unit
     * vector [direction] before its 2D distance to [target] would drop below [requiredGapM]. Exact on
     * a straight segment; the per-tick step is far shorter than a segment, so curvature error is
     * negligible.
     *
     * @param position the vehicle's current 2D centre.
     * @param direction the unit travel direction along the current segment.
     * @param target the 2D centre of the vehicle to stay clear of.
     * @param requiredGapM the minimum allowed centre-to-centre distance to [target].
     * @param currentLimitM the distance already permitted by the earlier clamps this tick.
     * @return the permitted distance (metres), reduced so the post-move distance to [target] stays at
     *   least [requiredGapM]; returns 0 when the vehicle is already inside the circle and would move
     *   deeper into it.
     */
    fun advanceLimitedTowardPoint(
        position: Point,
        direction: Point,
        target: Point,
        requiredGapM: Double,
        currentLimitM: Double
    ): Double {
        val wx = position.x - target.x
        val wy = position.y - target.y
        val distanceSq = wx * wx + wy * wy
        val requiredSq = requiredGapM * requiredGapM
        // Projection of the (target → position) offset onto the travel direction (direction is unit).
        val projection = wx * direction.x + wy * direction.y
        if (distanceSq >= requiredSq) {
            // Outside the clearance circle: the ray enters it at the smaller root of
            // d² + 2·projection·d + (distanceSq − requiredSq) = 0, if it enters at all.
            val discriminant = projection * projection - (distanceSq - requiredSq)
            if (discriminant <= 0.0) return currentLimitM
            val distanceToCircle = -projection - sqrt(discriminant)
            if (distanceToCircle <= 0.0) return currentLimitM
            return minOf(currentLimitM, distanceToCircle)
        }
        // Already inside the clearance circle: only motion that increases the distance is allowed.
        return if (projection > 0.0) currentLimitM else 0.0
    }
}
