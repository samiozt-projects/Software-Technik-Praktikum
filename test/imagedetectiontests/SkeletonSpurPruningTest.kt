package imagedetectiontests

import imageprocessing.StreetDetection
import org.bytedeco.opencv.global.opencv_core.CV_8UC1
import org.bytedeco.opencv.global.opencv_imgproc.line
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Point as OpenCVPoint
import org.bytedeco.opencv.opencv_core.Scalar
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests [StreetDetection.pruneShortSkeletonSpurs] against small synthetic 1-pixel skeletons: short
 * branches hanging off a junction (thinning artifacts that flicker phantom junctions between
 * captures) must be removed, while long branches (real streets), the main line, and isolated short
 * fragments (dashed-road pieces later reconnected by gap bridging) must survive.
 */
class SkeletonSpurPruningTest {

    private val streetDetection = StreetDetection()

    private fun blackCanvas(width: Int, height: Int): Mat = Mat(height, width, CV_8UC1, Scalar(0.0, 0.0, 0.0, 0.0))

    private fun draw(mat: Mat, fromX: Int, fromY: Int, toX: Int, toY: Int) {
        line(mat, OpenCVPoint(fromX, fromY), OpenCVPoint(toX, toY), Scalar(255.0, 0.0, 0.0, 0.0), 1, 8, 0)
    }

    private fun pixel(mat: Mat, x: Int, y: Int): Int = mat.ptr(y, x).get().toInt() and 0xFF

    /** Verifies that a short spur off a junction is removed while the main line survives. */
    @Test
    fun prune_shortSpurOffAJunction_isRemovedWhileMainLineSurvives() {
        val skeleton = blackCanvas(300, 100)
        draw(skeleton, 10, 50, 250, 50)
        // 8-pixel spur hanging off the main line at (100, 50) - a typical thinning artifact.
        draw(skeleton, 100, 50, 100, 58)

        streetDetection.pruneShortSkeletonSpurs(skeleton)

        assertEquals(0, pixel(skeleton, 100, 55), "the short spur must be removed")
        assertEquals(0, pixel(skeleton, 100, 58), "the spur's endpoint must be removed")
        assertEquals(255, pixel(skeleton, 100, 50), "the junction pixel itself belongs to the main line and must stay")
        assertEquals(255, pixel(skeleton, 50, 50), "the main line must survive")
        assertEquals(255, pixel(skeleton, 200, 50), "the main line must survive beyond the junction")

        skeleton.release()
    }

    /** Verifies that a branch above the spur threshold is kept as a real street stub. */
    @Test
    fun prune_longBranch_isKeptAsARealStreet() {
        val skeleton = blackCanvas(300, 120)
        draw(skeleton, 10, 50, 250, 50)
        // 40-pixel branch: far above the spur threshold, this is a real street stub.
        draw(skeleton, 150, 50, 150, 90)

        streetDetection.pruneShortSkeletonSpurs(skeleton)

        assertEquals(255, pixel(skeleton, 150, 70), "a long branch must not be pruned")
        assertEquals(255, pixel(skeleton, 150, 90), "the long branch's endpoint must stay")

        skeleton.release()
    }

    /** Verifies that an isolated short fragment (dashed-road piece) is kept for gap bridging. */
    @Test
    fun prune_isolatedShortFragment_isKeptForGapBridging() {
        val skeleton = blackCanvas(300, 100)
        // A short standalone dash with two endpoints and no junction - gap bridging may still
        // reconnect it to a dashed road, so pruning must leave it alone.
        draw(skeleton, 100, 50, 108, 50)

        streetDetection.pruneShortSkeletonSpurs(skeleton)

        assertEquals(255, pixel(skeleton, 104, 50), "an isolated fragment must not be pruned")

        skeleton.release()
    }
}
