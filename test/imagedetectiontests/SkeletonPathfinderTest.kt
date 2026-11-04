package imagedetectiontests

import imageprocessing.SkeletonPathfinder
import korlibs.math.geom.Point
import org.bytedeco.opencv.global.opencv_core.CV_8UC1
import org.bytedeco.opencv.global.opencv_imgproc.line
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Point as OpenCVPoint
import org.bytedeco.opencv.opencv_core.Scalar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [SkeletonPathfinder] directly against small synthetic skeleton masks, independent of the full
 * color-detection pipeline. Covers the A* rewrite (still the exact shortest path, not just "a" path)
 * and [SkeletonPathfinder.labelComponents], the pre-check
 * [imageprocessing.StreetDetection.traceSegmentsBetweenJunctions] uses to skip pairs that can never be
 * connected without running a full search.
 */
class SkeletonPathfinderTest {

    private fun blackCanvas(width: Int, height: Int): Mat = Mat(height, width, CV_8UC1, Scalar(0.0, 0.0, 0.0, 0.0))

    private fun drawLine(mat: Mat, from: Point, to: Point) {
        line(mat, OpenCVPoint(from.x.toInt(), from.y.toInt()), OpenCVPoint(to.x.toInt(), to.y.toInt()), Scalar(255.0, 0.0, 0.0, 0.0), 1, 8, 0)
    }

    /** Verifies that a straight skeleton line yields a path spanning its full extent. */
    @Test
    fun findSkeletonPath_straightLine_findsPathOfExpectedLength() {
        val skeleton = blackCanvas(100, 20)
        drawLine(skeleton, Point(5, 10), Point(90, 10))
        val buffer = SkeletonPathfinder.readSkeletonBuffer(skeleton)

        val path = SkeletonPathfinder.findSkeletonPath(buffer, Point(5, 10), Point(90, 10), searchRadius = 5)

        assertTrue(path.size >= 2, "a path must be found along the drawn line")
        assertEquals(5, path.first().x.toInt())
        assertEquals(90, path.last().x.toInt())
    }

    /** Verifies that the path follows an L-shaped skeleton's bend instead of cutting through background. */
    @Test
    fun findSkeletonPath_lShapedSkeleton_followsTheBendRatherThanCuttingThroughBackground() {
        val skeleton = blackCanvas(100, 100)
        drawLine(skeleton, Point(10, 10), Point(10, 80))
        drawLine(skeleton, Point(10, 80), Point(80, 80))
        val buffer = SkeletonPathfinder.readSkeletonBuffer(skeleton)

        val path = SkeletonPathfinder.findSkeletonPath(buffer, Point(10, 10), Point(80, 80), searchRadius = 5)

        assertTrue(path.isNotEmpty(), "a path must be found along the L-shaped skeleton")
        // A path that only followed the skeleton (not a straight diagonal shortcut through the
        // background) must be noticeably longer than the direct Euclidean distance between the ends -
        // a single 90-degree bend has a manhattan/euclidean ratio of sqrt(2) =~ 1.414.
        val directDistance = Point(10, 10).distanceTo(Point(80, 80))
        val pathLength = path.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
        assertTrue(pathLength > directDistance * 1.3, "path should follow the bend, not cut through the background")
    }

    /** Verifies that two disconnected skeleton segments produce no path. */
    @Test
    fun findSkeletonPath_disconnectedSegments_returnsEmptyList() {
        val skeleton = blackCanvas(100, 100)
        drawLine(skeleton, Point(5, 5), Point(20, 5))
        drawLine(skeleton, Point(80, 80), Point(95, 80))
        val buffer = SkeletonPathfinder.readSkeletonBuffer(skeleton)

        val path = SkeletonPathfinder.findSkeletonPath(buffer, Point(5, 5), Point(80, 80), searchRadius = 5)

        assertTrue(path.isEmpty(), "two disconnected segments must not produce a path")
    }

    /** Verifies that all pixels of one connected line share a single component label. */
    @Test
    fun labelComponents_connectedLine_allPixelsShareOneLabel() {
        val skeleton = blackCanvas(100, 20)
        drawLine(skeleton, Point(5, 10), Point(90, 10))
        val buffer = SkeletonPathfinder.readSkeletonBuffer(skeleton)

        val components = SkeletonPathfinder.labelComponents(buffer)
        val start = SkeletonPathfinder.snapToSkeleton(buffer, Point(5, 10), searchRadius = 5)
        val end = SkeletonPathfinder.snapToSkeleton(buffer, Point(90, 10), searchRadius = 5)

        assertNotNull(start)
        assertNotNull(end)
        assertEquals(components.labelAt(start), components.labelAt(end), "one connected line must be a single component")
    }

    /** Verifies that disconnected skeleton segments get distinct component labels. */
    @Test
    fun labelComponents_disconnectedSegments_haveDifferentLabels() {
        val skeleton = blackCanvas(100, 100)
        drawLine(skeleton, Point(5, 5), Point(20, 5))
        drawLine(skeleton, Point(80, 80), Point(95, 80))
        val buffer = SkeletonPathfinder.readSkeletonBuffer(skeleton)

        val components = SkeletonPathfinder.labelComponents(buffer)
        val first = SkeletonPathfinder.snapToSkeleton(buffer, Point(5, 5), searchRadius = 5)
        val second = SkeletonPathfinder.snapToSkeleton(buffer, Point(80, 80), searchRadius = 5)

        assertNotNull(first)
        assertNotNull(second)
        assertTrue(components.labelAt(first) != components.labelAt(second), "disconnected segments must not share a component id")
    }

    /** Verifies that a background pixel carries no component label. */
    @Test
    fun labelComponents_backgroundPixel_hasNoLabel() {
        val skeleton = blackCanvas(50, 50)
        drawLine(skeleton, Point(5, 5), Point(20, 5))
        val buffer = SkeletonPathfinder.readSkeletonBuffer(skeleton)

        val components = SkeletonPathfinder.labelComponents(buffer)

        assertNull(components.labelAt(SkeletonPathfinder.SkeletonPixel(40, 40)), "an untouched background pixel must have no component")
    }

    /** Verifies that one search resolves the paths to several targets on the same line. */
    @Test
    fun findPathsFromSource_multipleTargetsOnOneLine_findsEachInOneSearch() {
        val skeleton = blackCanvas(100, 20)
        drawLine(skeleton, Point(5, 10), Point(90, 10))
        val buffer = SkeletonPathfinder.readSkeletonBuffer(skeleton)

        val paths = SkeletonPathfinder.findPathsFromSource(
            buffer, Point(5, 10), listOf(Point(50, 10), Point(90, 10)), searchRadius = 5
        )

        assertEquals(2, paths.size)
        assertNotNull(paths[0], "the nearer target must be reachable")
        assertNotNull(paths[1], "the farther target must be reachable")
        assertEquals(50, paths[0]!!.last().x.toInt())
        assertEquals(90, paths[1]!!.last().x.toInt())
    }

    /** Verifies that reachable and unreachable targets of one search are reported independently. */
    @Test
    fun findPathsFromSource_mixOfReachableAndUnreachableTargets_reportsEachIndependently() {
        val skeleton = blackCanvas(100, 100)
        drawLine(skeleton, Point(5, 5), Point(50, 5))
        drawLine(skeleton, Point(80, 80), Point(95, 80))
        val buffer = SkeletonPathfinder.readSkeletonBuffer(skeleton)

        val paths = SkeletonPathfinder.findPathsFromSource(
            buffer, Point(5, 5), listOf(Point(50, 5), Point(80, 80)), searchRadius = 5
        )

        assertNotNull(paths[0], "target on the same line must be reachable")
        assertNull(paths[1], "target on a disconnected segment must be reported as unreachable")
    }

    /** Verifies that the multi-target search returns the same path as the single-target wrapper. */
    @Test
    fun findPathsFromSource_matchesFindSkeletonPath_forASingleTarget() {
        val skeleton = blackCanvas(100, 100)
        drawLine(skeleton, Point(10, 10), Point(10, 80))
        drawLine(skeleton, Point(10, 80), Point(80, 80))
        val buffer = SkeletonPathfinder.readSkeletonBuffer(skeleton)

        val viaMultiTarget = SkeletonPathfinder.findPathsFromSource(buffer, Point(10, 10), listOf(Point(80, 80)), searchRadius = 5).first()
        val viaSingleTarget = SkeletonPathfinder.findSkeletonPath(buffer, Point(10, 10), Point(80, 80), searchRadius = 5)

        assertEquals(viaSingleTarget, viaMultiTarget, "both entry points must find the exact same shortest path")
    }
}
