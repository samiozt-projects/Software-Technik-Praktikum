package imagedetectiontests

import imageprocessing.ArucoTableRectifier
import org.bytedeco.javacpp.indexer.UByteIndexer
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.global.opencv_core.bitwise_not
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGR
import org.bytedeco.opencv.global.opencv_imgproc.LINE_8
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.global.opencv_imgproc.rectangle
import org.bytedeco.opencv.global.opencv_objdetect.DICT_4X4_50
import org.bytedeco.opencv.global.opencv_objdetect.generateImageMarker
import org.bytedeco.opencv.global.opencv_objdetect.getPredefinedDictionary
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Point
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.Scalar
import rendering.GameAreaScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [ArucoTableRectifier] using ArUco markers generated in code, so the whole perspective
 * rectification (marker detection, id-to-corner mapping, perspective transform) is verified without
 * a real camera and runs on CI.
 */
class ArucoTableRectifierTest {

    /** Raw-frame size used for the synthetic input. */
    private val frameWidth = 1920
    private val frameHeight = 1080

    /** Side length and corner margin of the generated markers, in pixels. */
    private val markerSize = 160
    private val markerMargin = 80

    /**
     * A white frame with all four corner markers should rectify to the projection surface size.
     */
    @Test
    fun rectifiesToDeskSizeWhenAllMarkersPresent() {
        val image = whiteFrame()
        placeCornerMarkers(image)

        val result = ArucoTableRectifier.rectify(image)

        assertNotNull(result, "Rectification should succeed when all four markers are present")
        assertEquals(GameAreaScale.DESK_W_PX, result.cols(), "Rectified width should match the projection surface")
        assertEquals(GameAreaScale.DESK_H_PX, result.rows(), "Rectified height should match the projection surface")

        result.release()
        image.release()
    }

    /**
     * Without markers there is nothing to rectify, so the caller can fall back to another crop.
     */
    @Test
    fun returnsNullWhenNoMarkersPresent() {
        val image = whiteFrame()

        val result = ArucoTableRectifier.rectify(image)

        assertNull(result, "Rectification should fail (null) when no markers are present")
        image.release()
    }

    /**
     * A missing corner marker must also fail, since a full quadrilateral is required.
     */
    @Test
    fun returnsNullWhenOneMarkerMissing() {
        val image = whiteFrame()
        // Place only three of the four corner markers (omit the bottom-right id).
        placeMarker(image, id = 2, x = markerMargin, y = markerMargin)
        placeMarker(image, id = 1, x = frameWidth - markerMargin - markerSize, y = markerMargin)
        placeMarker(image, id = 3, x = markerMargin, y = frameHeight - markerMargin - markerSize)

        val result = ArucoTableRectifier.rectify(image)

        assertNull(result, "Rectification should fail (null) when a corner marker is missing")
        image.release()
    }

    /**
     * A missing corner marker should be reported by id for UI diagnostics.
     */
    @Test
    fun reportsMissingRequiredMarkerIds() {
        val image = whiteFrame()
        placeMarker(image, id = 2, x = markerMargin, y = markerMargin)
        placeMarker(image, id = 1, x = frameWidth - markerMargin - markerSize, y = markerMargin)
        placeMarker(image, id = 3, x = markerMargin, y = frameHeight - markerMargin - markerSize)

        val missingIds = ArucoTableRectifier.missingRequiredMarkerIds(image)

        assertEquals(setOf(0), missingIds)
        image.release()
    }

    /**
     * An empty image must be handled gracefully.
     */
    @Test
    fun returnsNullForEmptyImage() {
        val empty = Mat()
        assertNull(ArucoTableRectifier.rectify(empty), "Empty input should return null")
        empty.release()
    }

    /**
     * Beamer-projected markers are shown inverted (bright on a black background, see
     * [scenes.ArucoDetectionScene]); `detectTransform(invertedProjectedMarkers = true)` must invert
     * the frame, find the markers inside the four corner crops, map their reference points back
     * into full-frame coordinates, and rectify the FULL projection surface (each marker maps back
     * onto its known inset position instead of onto an output corner).
     *
     * The red probe square painted at the center of the quad spanned by the four marker reference
     * corners must therefore land at the center of the marker quad's known projected positions in
     * the output: a reference point left in crop-local coordinates (a dropped crop offset) or a
     * corner-anchored destination mapping (which would over-zoom onto the inner quad) would both
     * warp the probe elsewhere, so this asserts the full coordinate mapping, not just detection.
     */
    @Test
    fun detectsInvertedProjectedMarkersAndRectifiesTheFullProjectionSurface() {
        val image = blackFrame()
        placeInvertedCornerMarkers(image)

        // The reference corner of each marker is its own top-left, so the quad spans from
        // (margin, margin) to (frame - margin - markerSize) on both axes.
        val probeCenterX = (markerMargin + (frameWidth - markerMargin - markerSize)) / 2
        val probeCenterY = (markerMargin + (frameHeight - markerMargin - markerSize)) / 2
        val probeHalf = 20
        rectangle(
            image,
            Point(probeCenterX - probeHalf, probeCenterY - probeHalf),
            Point(probeCenterX + probeHalf, probeCenterY + probeHalf),
            Scalar(0.0, 0.0, 255.0, 0.0),
            -1, LINE_8, 0
        )

        val transform = ArucoTableRectifier.detectTransform(image, invertedProjectedMarkers = true)
        assertNotNull(transform, "Inverted projected markers should be detected in the corner crops")

        val result = transform.apply(image)
        assertEquals(GameAreaScale.DESK_W_PX, result.cols(), "Rectified width should match the projection surface")
        assertEquals(GameAreaScale.DESK_H_PX, result.rows(), "Rectified height should match the projection surface")

        // The probe (center of the detected marker quad) must map to the center of the quad formed
        // by the markers' known projected positions in the output.
        val margin = ArucoTableRectifier.PROJECTED_MARKER_MARGIN_PX
        val projectedSize = ArucoTableRectifier.PROJECTED_MARKER_SIZE_PX
        val expectedX = ((margin + (GameAreaScale.DESK_W_PX - margin - projectedSize)) / 2).toLong()
        val expectedY = ((margin + (GameAreaScale.DESK_H_PX - margin - projectedSize)) / 2).toLong()

        val indexer = result.createIndexer<UByteIndexer>()
        val blue = indexer.get(expectedY, expectedX, 0)
        val green = indexer.get(expectedY, expectedX, 1)
        val red = indexer.get(expectedY, expectedX, 2)
        indexer.release()

        assertTrue(red > 200, "The probe should map to the projected marker quad's center (red=$red)")
        assertTrue(blue < 50 && green < 50, "The probe pixel should be the pure red probe (blue=$blue, green=$green)")

        result.release()
        transform.release()
        image.release()
    }

    /**
     * Without the inverted-projected-markers flag, an inverted-marker frame must not be detected -
     * the standard path is reserved for the physical black-on-white stickers.
     */
    @Test
    fun standardDetectionIgnoresInvertedMarkers() {
        val image = blackFrame()
        placeInvertedCornerMarkers(image)

        val transform = ArucoTableRectifier.detectTransform(image)

        assertNull(transform, "Inverted markers should not be detected by the standard (sticker) path")
        image.release()
    }

    /** Creates a white BGR frame. */
    private fun whiteFrame(): Mat = Mat(frameHeight, frameWidth, CV_8UC3, Scalar(255.0, 255.0, 255.0, 0.0))

    /** Creates a black BGR frame, the (inverted) background the projected inverted markers sit on. */
    private fun blackFrame(): Mat = Mat(frameHeight, frameWidth, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))

    /** Places the four corner markers in their beamer-projected inverted form (bright on black). */
    private fun placeInvertedCornerMarkers(image: Mat) {
        placeMarker(image, id = 2, x = markerMargin, y = markerMargin, inverted = true)
        placeMarker(image, id = 1, x = frameWidth - markerMargin - markerSize, y = markerMargin, inverted = true)
        placeMarker(image, id = 0, x = frameWidth - markerMargin - markerSize, y = frameHeight - markerMargin - markerSize, inverted = true)
        placeMarker(image, id = 3, x = markerMargin, y = frameHeight - markerMargin - markerSize, inverted = true)
    }

    /**
     * Places the four corner markers using the same id-to-corner mapping as the rectifier
     * (game top-left = id 2, top-right = id 1, bottom-right = id 0, bottom-left = id 3).
     */
    private fun placeCornerMarkers(image: Mat) {
        placeMarker(image, id = 2, x = markerMargin, y = markerMargin)
        placeMarker(image, id = 1, x = frameWidth - markerMargin - markerSize, y = markerMargin)
        placeMarker(image, id = 0, x = frameWidth - markerMargin - markerSize, y = frameHeight - markerMargin - markerSize)
        placeMarker(image, id = 3, x = markerMargin, y = frameHeight - markerMargin - markerSize)
    }

    /** Generates one ArUco marker (optionally inverted, as the beamer projects it) and copies it into [image]. */
    private fun placeMarker(image: Mat, id: Int, x: Int, y: Int, inverted: Boolean = false) {
        val dictionary = getPredefinedDictionary(DICT_4X4_50)
        val markerGray = Mat()
        generateImageMarker(dictionary, id, markerSize, markerGray, 1)
        if (inverted) bitwise_not(markerGray, markerGray)

        val markerBgr = Mat()
        cvtColor(markerGray, markerBgr, COLOR_GRAY2BGR)

        val roi = Mat(image, Rect(x, y, markerSize, markerSize))
        markerBgr.copyTo(roi)

        roi.release()
        markerBgr.release()
        markerGray.release()
    }
}
