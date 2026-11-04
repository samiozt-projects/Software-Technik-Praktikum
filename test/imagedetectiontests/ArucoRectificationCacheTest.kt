package imagedetectiontests

import imageprocessing.ArucoRectificationCache
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGR
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.global.opencv_objdetect.DICT_4X4_50
import org.bytedeco.opencv.global.opencv_objdetect.generateImageMarker
import org.bytedeco.opencv.global.opencv_objdetect.getPredefinedDictionary
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.Scalar
import rendering.GameAreaScale
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals

/**
 * Tests [ArucoRectificationCache] with synthetic ArUco marker images (as in
 * [ArucoTableRectifierTest]) and controlled timestamps, so the revalidation-interval logic is
 * verified deterministically without a real camera or wall-clock timing.
 */
class ArucoRectificationCacheTest {

    private val frameWidth = 1920
    private val frameHeight = 1080
    private val markerSize = 160
    private val markerMargin = 80
    private val revalidationIntervalMs = 1000L

    /** A cached transform is reused for a marker-less frame within the revalidation interval. */
    @Test
    fun rectify_reusesCachedTransformWithinInterval() {
        val cache = ArucoRectificationCache(revalidationIntervalMs)
        val withMarkers = frameWithCornerMarkers()
        val withoutMarkers = whiteFrame()

        val first = cache.rectify(withMarkers, nowMs = 0)
        assertNotNull(first, "First detection with all markers present should succeed")

        val second = cache.rectify(withoutMarkers, nowMs = revalidationIntervalMs - 1)
        assertNotNull(second, "A marker-less frame within the interval should still rectify via the cached transform")
        assertEquals(GameAreaScale.DESK_W_PX, second.cols())
        assertEquals(GameAreaScale.DESK_H_PX, second.rows())

        first.release()
        second.release()
        withMarkers.release()
        withoutMarkers.release()
    }

    /** Once the interval elapses, a frame without markers is no longer rescued by a stale cache from nothing. */
    @Test
    fun rectify_returnsNullWhenIntervalElapsedAndNoTransformWasEverCached() {
        val cache = ArucoRectificationCache(revalidationIntervalMs)
        val withoutMarkers = whiteFrame()

        val result = cache.rectify(withoutMarkers, nowMs = 0)

        assertNull(result, "Without any prior successful detection there is nothing to fall back to")
        withoutMarkers.release()
    }

    /** After the interval elapses, a failed revalidation keeps using the last known-good transform. */
    @Test
    fun rectify_fallsBackToStaleTransformWhenRevalidationFails() {
        val cache = ArucoRectificationCache(revalidationIntervalMs)
        val withMarkers = frameWithCornerMarkers()
        val withoutMarkers = whiteFrame()

        val first = cache.rectify(withMarkers, nowMs = 0)
        assertNotNull(first, "First detection with all markers present should succeed")

        // Interval has elapsed and this frame's markers are covered/missing, but the cache should
        // still return the last known-good transform rather than nothing.
        val second = cache.rectify(withoutMarkers, nowMs = revalidationIntervalMs + 1)

        assertNotNull(second, "A failed revalidation should keep using the last known-good transform")
        assertEquals(GameAreaScale.DESK_W_PX, second.cols())
        assertEquals(GameAreaScale.DESK_H_PX, second.rows())

        first.release()
        second.release()
        withMarkers.release()
        withoutMarkers.release()
    }

    /** After the interval elapses, a successful revalidation replaces the cached transform. */
    @Test
    fun rectify_reDetectsAfterIntervalElapsed() {
        val cache = ArucoRectificationCache(revalidationIntervalMs)
        val withMarkers = frameWithCornerMarkers()

        val first = cache.rectify(withMarkers, nowMs = 0)
        val second = cache.rectify(withMarkers, nowMs = revalidationIntervalMs + 1)

        assertNotNull(first)
        assertNotNull(second, "Re-detection with all markers still present should succeed again")

        first.release()
        second.release()
        withMarkers.release()
    }

    private fun whiteFrame(): Mat = Mat(frameHeight, frameWidth, CV_8UC3, Scalar(255.0, 255.0, 255.0, 0.0))

    /** Creates a white frame with all four corner markers (game top-left = id 2, top-right = id 1, bottom-right = id 0, bottom-left = id 3). */
    private fun frameWithCornerMarkers(): Mat {
        val image = whiteFrame()
        placeMarker(image, id = 2, x = markerMargin, y = markerMargin)
        placeMarker(image, id = 1, x = frameWidth - markerMargin - markerSize, y = markerMargin)
        placeMarker(image, id = 0, x = frameWidth - markerMargin - markerSize, y = frameHeight - markerMargin - markerSize)
        placeMarker(image, id = 3, x = markerMargin, y = frameHeight - markerMargin - markerSize)
        return image
    }

    /** Generates one ArUco marker and copies it into [image] at the given top-left position. */
    private fun placeMarker(image: Mat, id: Int, x: Int, y: Int) {
        val dictionary = getPredefinedDictionary(DICT_4X4_50)
        val markerGray = Mat()
        generateImageMarker(dictionary, id, markerSize, markerGray, 1)

        val markerBgr = Mat()
        cvtColor(markerGray, markerBgr, COLOR_GRAY2BGR)

        val roi = Mat(image, Rect(x, y, markerSize, markerSize))
        markerBgr.copyTo(roi)

        roi.release()
        markerBgr.release()
        markerGray.release()
    }
}
