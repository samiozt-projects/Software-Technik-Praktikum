package geometry

import korlibs.math.geom.Point

/**
 * Simplifies a polyline using the Ramer-Douglas-Peucker algorithm.
 *
 * The first and last points are always retained. Intermediate points are kept only when their
 * perpendicular distance from the current start/end chord exceeds [epsilon]. If one point is too
 * far away from that chord, the path is split at that point and both halves are simplified
 * recursively. This keeps visually relevant corners while removing small camera-detection jitter.
 *
 * @param points ordered polyline points in the same coordinate system.
 * @param epsilon maximum allowed distance from the simplified line before a point must be kept.
 * @return a simplified path that preserves the original order of the retained points.
 */
fun simplifyPath(points: List<Point>, epsilon: Double): List<Point> {
    if (points.size < 3 || epsilon <= 0.0) return points

    val start = points.first()
    val end = points.last()
    var maxDistance = 0.0
    var maxIndex = 0

    for (i in 1 until points.lastIndex) {
        val distance = perpendicularDistance(points[i], start, end)
        if (distance > maxDistance) {
            maxDistance = distance
            maxIndex = i
        }
    }

    return if (maxDistance > epsilon) {
        val left = simplifyPath(points.subList(0, maxIndex + 1), epsilon)
        val right = simplifyPath(points.subList(maxIndex, points.size), epsilon)
        left.dropLast(1) + right
    } else {
        listOf(start, end)
    }
}

/**
 * Computes the shortest distance from [point] to the infinite line through [lineStart]/[lineEnd].
 *
 * The projection factor [t] places the perpendicular foot on that line. Degenerate segments are
 * handled as a point-to-point distance so duplicate input points do not divide by zero.
 */
private fun perpendicularDistance(point: Point, lineStart: Point, lineEnd: Point): Double {
    val dx = lineEnd.x - lineStart.x
    val dy = lineEnd.y - lineStart.y
    val lineLengthSq = dx * dx + dy * dy
    if (lineLengthSq == 0.0) return point.distanceTo(lineStart)

    val t = ((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / lineLengthSq
    val projectionX = lineStart.x + dx * t
    val projectionY = lineStart.y + dy * t
    return point.distanceTo(Point(projectionX, projectionY))
}
