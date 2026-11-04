package simulation

import korlibs.math.geom.Point
import kotlin.math.hypot

/**
 * Utility functions for street-related calculations.
 */
object StreetUtils {
    /**
     * Length in virtual metres of the polyline segment between `path[index]` and `path[index + 1]`.
     * 
     * @param path The path of the street.
     * @param index The index of the segment to calculate the length of.
     * @return The length of the segment in meters.
     */
    fun segmentLengthM(path: List<Point>, index: Int): Double {
        val p = path[index]
        val q = path[index + 1]
        return hypot(q.x - p.x, q.y - p.y)
    }

    /**
     * Total virtual-metre length of a polyline path (sum of all its segments).
     * 
     * @param path The path of the street.
     * @return The total length of the street in meters.
     */
    fun streetLengthM(path: List<Point>): Double {
        var total = 0.0
        for (i in 0 until path.size - 1) total += segmentLengthM(path, i)
        return total
    }
}
