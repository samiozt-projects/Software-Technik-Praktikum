package imagedetectiontests

import imageprocessing.PermanentArucoTransform
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.global.opencv_core.bitwise_not
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGR
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.global.opencv_objdetect.DICT_4X4_50
import org.bytedeco.opencv.global.opencv_objdetect.generateImageMarker
import org.bytedeco.opencv.global.opencv_objdetect.getPredefinedDictionary
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.Scalar
import rendering.GameAreaScale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [PermanentArucoTransform], the application-lifetime holder used when the four corner markers
 * are projected once during calibration rather than stuck to the table as physical stickers (so the
 * camera never sees them again afterward - see [ArucoRectificationCacheTest] for the physical-sticker,
 * periodically-revalidated counterpart).
 *
 * The projected markers are shown INVERTED (bright on a black background, see
 * [scenes.ArucoDetectionScene]) because a beamer cannot project black; [PermanentArucoTransform.seed]
 * therefore inverts the frame before detecting. The synthetic seed frames here mirror that: inverted
 * markers placed on a black frame.
 *
 * [PermanentArucoTransform] is a singleton, so every test explicitly [PermanentArucoTransform.clear]s
 * it before and after running to avoid leaking state between tests.
 */
class PermanentArucoTransformTest {

    private val frameWidth = 1920
    private val frameHeight = 1080
    private val markerSize = 160
    private val markerMargin = 80

    /** Clears the singleton's state before each test so no test sees a previously seeded transform. */
    @BeforeTest
    fun clearBefore() {
        PermanentArucoTransform.clear()
    }

    /** Clears the singleton's state after each test so no state leaks into later tests. */
    @AfterTest
    fun clearAfter() {
        PermanentArucoTransform.clear()
    }

    /** Before any successful [PermanentArucoTransform.seed], nothing is established. */
    @Test
    fun rectify_beforeSeeding_returnsNull() {
        val image = whiteFrame()

        assertFalse(PermanentArucoTransform.isEstablished)
        assertNull(PermanentArucoTransform.rectify(image))

        image.release()
    }

    /** A frame with all four markers seeds the transform, which then rectifies later frames without needing markers again. */
    @Test
    fun seed_withAllMarkersPresent_establishesReusableTransform() {
        val markerFrame = blackFrame()
        placeCornerMarkers(markerFrame)

        val seeded = PermanentArucoTransform.seed(markerFrame)

        assertTrue(seeded, "Seeding should succeed when all four markers are present")
        assertTrue(PermanentArucoTransform.isEstablished)

        // A later frame with no markers at all should still rectify using the already-seeded transform.
        val laterFrame = whiteFrame()
        val result = PermanentArucoTransform.rectify(laterFrame)

        assertTrue(result != null, "The seeded transform should apply to later marker-less frames")
        assertEquals(GameAreaScale.DESK_W_PX, result.cols())
        assertEquals(GameAreaScale.DESK_H_PX, result.rows())

        result.release()
        laterFrame.release()
        markerFrame.release()
    }

    /** A frame missing a marker cannot seed the transform. */
    @Test
    fun seed_withMissingMarker_fails() {
        val image = blackFrame()
        placeMarker(image, id = 2, x = markerMargin, y = markerMargin)
        placeMarker(image, id = 1, x = frameWidth - markerMargin - markerSize, y = markerMargin)
        placeMarker(image, id = 3, x = markerMargin, y = frameHeight - markerMargin - markerSize)
        // id 0 (bottom-right) intentionally omitted.

        val seeded = PermanentArucoTransform.seed(image)

        assertFalse(seeded)
        assertFalse(PermanentArucoTransform.isEstablished)

        image.release()
    }

    /** [PermanentArucoTransform.clear] discards an established transform. */
    @Test
    fun clear_discardsEstablishedTransform() {
        val markerFrame = blackFrame()
        placeCornerMarkers(markerFrame)
        PermanentArucoTransform.seed(markerFrame)
        assertTrue(PermanentArucoTransform.isEstablished)

        PermanentArucoTransform.clear()

        assertFalse(PermanentArucoTransform.isEstablished)
        markerFrame.release()
    }

    private fun whiteFrame(): Mat = Mat(frameHeight, frameWidth, CV_8UC3, Scalar(255.0, 255.0, 255.0, 0.0))

    /** A black frame, the (inverted) background the projected inverted markers sit on. */
    private fun blackFrame(): Mat = Mat(frameHeight, frameWidth, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))

    /** Places the four inverted corner markers using the same id-to-corner mapping as [imageprocessing.ArucoTableRectifier]. */
    private fun placeCornerMarkers(image: Mat) {
        placeMarker(image, id = 2, x = markerMargin, y = markerMargin)
        placeMarker(image, id = 1, x = frameWidth - markerMargin - markerSize, y = markerMargin)
        placeMarker(image, id = 0, x = frameWidth - markerMargin - markerSize, y = frameHeight - markerMargin - markerSize)
        placeMarker(image, id = 3, x = markerMargin, y = frameHeight - markerMargin - markerSize)
    }

    /** Generates one ArUco marker, inverts it (as the beamer projects it), and copies it into [image]. */
    private fun placeMarker(image: Mat, id: Int, x: Int, y: Int) {
        val dictionary = getPredefinedDictionary(DICT_4X4_50)
        val markerGray = Mat()
        generateImageMarker(dictionary, id, markerSize, markerGray, 1)
        bitwise_not(markerGray, markerGray)

        val markerBgr = Mat()
        cvtColor(markerGray, markerBgr, COLOR_GRAY2BGR)

        val roi = Mat(image, Rect(x, y, markerSize, markerSize))
        markerBgr.copyTo(roi)

        roi.release()
        markerBgr.release()
        markerGray.release()
    }
}
