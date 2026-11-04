package imageprocessing

import korlibs.math.geom.Point
import org.bytedeco.opencv.opencv_core.Mat
import java.util.PriorityQueue
import kotlin.math.sqrt

/**
 * Finds paths on a binary, one-pixel-wide street skeleton.
 *
 * StreetDetection uses this after color segmentation and thinning: known source/target
 * positions are snapped to nearby skeleton pixels, then the shortest pixel path between
 * them is traced along the white skeleton mask.
 */
object SkeletonPathfinder {
    /**
     * Integer coordinate of one white pixel in the skeleton mask.
     * @property x The x-coordinate of the pixel.
     * @property y The y-coordinate of the pixel.
    */
    data class SkeletonPixel(val x: Int, val y: Int)

    /**
     * A skeleton mask's pixels copied into a flat JVM array once, so repeated per-pixel lookups (both
     * within one search and across the many searches [StreetDetection.traceSegmentsBetweenJunctions]
     * runs per detection) read plain array memory instead of crossing the JNI boundary for every
     * single neighbor check - the same reasoning [StreetDetection.detectConnectionNodes] already
     * applies to its own full-image scan.
     *
     * @property cols Width of the copied mask in pixels.
     * @property rows Height of the copied mask in pixels.
     */
    class SkeletonBuffer internal constructor(private val data: ByteArray, val cols: Int, val rows: Int) {
        /** Returns true when the pixel at ([x], [y]) is white skeleton; out-of-bounds reads false. */
        fun isForeground(x: Int, y: Int): Boolean {
            if (x !in 0 until cols || y !in 0 until rows) return false
            return data[y * cols + x].toInt() and 0xFF != 0
        }
    }

    /** Copies [skeleton]'s pixels into a [SkeletonBuffer] for cheap repeated lookups. */
    fun readSkeletonBuffer(skeleton: Mat): SkeletonBuffer {
        val rows = skeleton.rows()
        val cols = skeleton.cols()
        val data = ByteArray(rows * cols)
        if (rows > 0 && cols > 0) skeleton.data().get(data)
        return SkeletonBuffer(data, cols, rows)
    }

    /**
     * Finds the closest white skeleton pixel around [point].
     *
     * Level nodes usually come from JSON/game coordinates and will not land exactly on the
     * 1-pixel skeleton, so graph construction first snaps them into the detected street mask.
     */
    fun snapToSkeleton(skeleton: SkeletonBuffer, point: Point, searchRadius: Int): SkeletonPixel? {
        var best: SkeletonPixel? = null
        var bestDist = Double.MAX_VALUE

        val x0 = (point.x - searchRadius).toInt().coerceAtLeast(0)
        val x1 = (point.x + searchRadius).toInt().coerceAtMost(skeleton.cols - 1)
        val y0 = (point.y - searchRadius).toInt().coerceAtLeast(0)
        val y1 = (point.y + searchRadius).toInt().coerceAtMost(skeleton.rows - 1)

        for (sy in y0..y1) {
            for (sx in x0..x1) {
                if (!skeleton.isForeground(sx, sy)) continue

                val distance = point.distanceTo(Point(sx, sy))
                if (distance < bestDist) {
                    bestDist = distance
                    best = SkeletonPixel(sx, sy)
                }
            }
        }

        return best
    }

    /**
     * Connected-component id for every skeleton pixel of [skeleton] (8-connectivity), computed with
     * one flood-fill pass over the whole mask.
     *
     * [StreetDetection.traceSegmentsBetweenJunctions] tries every pair among its key nodes (sources,
     * targets, and detected junctions). Most pairs on a real table are not on the same physical
     * stretch of road, and without this, ruling that out costs a full [findSkeletonPath] search that
     * can only ever end in "no path" - which needs to exhaust that pair's entire reachable region
     * before giving up. Comparing component ids first is an O(1) array lookup per pair instead, and it
     * is exact rather than a heuristic: two pixels share a component if and only if a skeleton path
     * connects them, so this never rules out a pair a full search would have connected.
     */
    class ComponentLabels internal constructor(private val labels: IntArray, private val cols: Int, private val rows: Int) {
        /** The component id at [pixel], or null when [pixel] is out of bounds or background. */
        fun labelAt(pixel: SkeletonPixel): Int? {
            if (pixel.x !in 0 until cols || pixel.y !in 0 until rows) return null
            val label = labels[pixel.y * cols + pixel.x]
            return if (label < 0) null else label
        }
    }

    /** Computes [ComponentLabels] for [skeleton]. See its class KDoc for why this exists. */
    fun labelComponents(skeleton: SkeletonBuffer): ComponentLabels {
        val cols = skeleton.cols
        val rows = skeleton.rows
        val labels = IntArray(rows * cols) { -1 }

        var nextLabel = 0
        val stack = ArrayDeque<Int>()
        for (start in 0 until rows * cols) {
            val startX = start % cols
            val startY = start / cols
            if (!skeleton.isForeground(startX, startY) || labels[start] >= 0) continue

            val label = nextLabel++
            labels[start] = label
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val index = stack.removeLast()
                val x = index % cols
                val y = index / cols
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (!skeleton.isForeground(nx, ny)) continue
                        val neighborIndex = ny * cols + nx
                        if (labels[neighborIndex] >= 0) continue
                        labels[neighborIndex] = label
                        stack.addLast(neighborIndex)
                    }
                }
            }
        }
        return ComponentLabels(labels, cols, rows)
    }

    /**
     * Runs one Dijkstra search from [from] and returns the shortest skeleton path to each of
     * [targets] (by list index; a target unreachable from [from], or too far from any skeleton pixel
     * to snap to in the first place, gets null).
     *
     * A single search serves every target because Dijkstra already computes the shortest distance
     * from [from] to *every* reachable pixel in one run, not just one chosen goal -
     * [StreetDetection.traceSegmentsBetweenJunctions] calling this once per key node (instead of the
     * keyNodes.size - 1 separate single-target searches that pattern used to require) is what turns
     * its overall cost from quadratic to linear in the number of key nodes. The search still stops
     * early once every target has been settled, rather than exhausting the rest of the reachable
     * skeleton region.
     *
     * Movement uses all 8 neighboring pixels. Straight steps cost 1 and diagonal steps cost sqrt(2),
     * so the resulting polylines follow the skeleton while still approximating Euclidean distance.
     */
    fun findPathsFromSource(skeleton: SkeletonBuffer, from: Point, targets: List<Point>, searchRadius: Int): List<List<Point>?> {
        val cols = skeleton.cols
        val start = snapToSkeleton(skeleton, from, searchRadius) ?: return targets.map { null }
        val startIndex = start.y * cols + start.x

        // Pending targets, keyed by the skeleton pixel index they snap to. Several target list-indices
        // may share one pixel index (e.g. two key nodes close enough to snap to the same skeleton
        // pixel); a pending list handles that without losing any of them.
        val pendingByPixelIndex = HashMap<Int, MutableList<Int>>()
        targets.forEachIndexed { targetListIndex, target ->
            val snapped = snapToSkeleton(skeleton, target, searchRadius) ?: return@forEachIndexed
            pendingByPixelIndex.getOrPut(snapped.y * cols + snapped.x) { mutableListOf() }.add(targetListIndex)
        }
        val resolvedPixelIndexByTarget = arrayOfNulls<Int>(targets.size)
        var remainingTargetPixels = pendingByPixelIndex.size

        val previous = HashMap<Int, Int>()
        val bestCost = HashMap<Int, Double>()
        val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })

        bestCost[startIndex] = 0.0
        queue.add(0.0 to startIndex)

        while (queue.isNotEmpty() && remainingTargetPixels > 0) {
            val (cost, index) = queue.poll()
            // Stale entry from an earlier, since-improved relaxation of this pixel - skip it.
            if (cost > (bestCost[index] ?: Double.MAX_VALUE)) continue

            pendingByPixelIndex.remove(index)?.let { targetListIndices ->
                targetListIndices.forEach { resolvedPixelIndexByTarget[it] = index }
                remainingTargetPixels--
            }

            val x = index % cols
            val y = index / cols
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (!skeleton.isForeground(nx, ny)) continue

                    val neighborIndex = ny * cols + nx
                    val stepCost = sqrt((dx * dx + dy * dy).toDouble())
                    val nextCost = cost + stepCost
                    if (nextCost < (bestCost[neighborIndex] ?: Double.MAX_VALUE)) {
                        bestCost[neighborIndex] = nextCost
                        previous[neighborIndex] = index
                        queue.add(nextCost to neighborIndex)
                    }
                }
            }
        }

        /** Walks the [previous] chain back from [endIndex] and returns the start-to-end polyline. */
        fun reconstructPath(endIndex: Int): List<Point> {
            val path = mutableListOf<Point>()
            var current: Int? = endIndex
            while (current != null) {
                path.add(Point(current % cols, current / cols))
                current = previous[current]
            }
            path.reverse()
            return path
        }

        return targets.indices.map { i -> resolvedPixelIndexByTarget[i]?.let(::reconstructPath) }
    }

    /**
     * Traces the shortest path on [skeleton] between [from] and [to].
     *
     * A thin single-target convenience wrapper around [findPathsFromSource]; callers that need paths
     * to several points from the same [from] should call that directly instead of this once per point,
     * since each call here re-runs its own full search from scratch.
     */
    fun findSkeletonPath(skeleton: SkeletonBuffer, from: Point, to: Point, searchRadius: Int): List<Point> =
        findPathsFromSource(skeleton, from, listOf(to), searchRadius).first() ?: emptyList()
}
