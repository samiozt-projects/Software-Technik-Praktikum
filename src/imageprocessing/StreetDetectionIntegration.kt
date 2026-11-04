package imageprocessing

import geometry.simplifyPath
import korlibs.math.geom.Point

/**
 * Aligns and orientates a detected polyline path so that it cleanly originates at the
 * [source] node and terminates at the [target] node.
 *
 * Because the computer vision layout ([StreetDetection]) may extract contours in an arbitrary
 * direction (either from start-to-end or vice-versa), this function evaluates the total
 * distance for both orientations. If the path was detected in reverse, it is automatically
 * flipped using [List.reversed].
 *
 * To prevent visual gaps or jagged connections at the junctions, the raw first and last
 * detected points are discarded and strictly snapped to the exact [source] and [target]
 * domain coordinates, while preserving all intermediate shape points.
 *
 * @param detectedPath The raw list of [Point]s returned by the contour detector.
 * @param source The exact position of the spawning source node.
 * @param target The exact position of the destination target node.
 * @return A cleaned list of points guaranteed to start at [source] and end at [target].
 */
fun alignPathToEndpoints(
    detectedPath: List<Point>,
    source: Point,
    target: Point
): List<Point> {
    // Edge case: If the path is empty or a single dot, we cannot determine orientation.
    if (detectedPath.size < 2) return listOf(source, target)

    // Calculate total error distance assuming the detector tracked from source -> target.
    val forwardDistance =
        detectedPath.first().distanceTo(source) + detectedPath.last().distanceTo(target)

    // Calculate total error distance assuming the detector tracked in reverse: target -> source.
    val reverseDistance =
        detectedPath.first().distanceTo(target) + detectedPath.last().distanceTo(source)

    // Choose the orientation that yields the absolute smallest geometric discrepancy.
    // If reverse tracking happened, flip the array sequence to ensure correct traffic flow.
    val orientedPath =
        if (forwardDistance <= reverseDistance) detectedPath else detectedPath.reversed()

    // Prevent open gaps or layout offsets by dropping the loose, imperfectly-detected
    // outer points. Splice the exact, validated domain-node coordinates onto the
    // head and tail while perfectly preserving the inner polyline curves.
    return listOf(source) + orientedPath.drop(1).dropLast(1) + listOf(target)
}

/**
 * Reduces a polyline while preserving its relevant structural shape.
 *
 * Points that lie within a perpendicular distance smaller than [tolerancePx]
 * relative to their surrounding kept points are discarded. Sharp curves,
 * critical corners, and exact start/end positions are structurally retained.
 *
 * This process prevents erratic vehicle jitter and micro-rotations during
 * rendering because noisy point detections from computer vision skeletons
 * are smoothed into clean, logical road segments.
 *
 * @param path The list of captured 2D image coordinates to compress.
 * @param tolerancePx The maximum allowed deviation in pixels. Higher values yield coarser lines.
 * @return A new, structurally optimized list of [Point] coordinates.
 */
fun simplifyPolyline(
    path: List<Point>,
    tolerancePx: Double
): List<Point> {
    return simplifyPath(path, tolerancePx)
}
