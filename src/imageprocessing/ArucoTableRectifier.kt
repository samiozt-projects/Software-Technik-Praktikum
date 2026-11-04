package imageprocessing

import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.javacpp.indexer.IntIndexer
import org.bytedeco.opencv.global.opencv_core.CV_32F
import org.bytedeco.opencv.global.opencv_core.bitwise_not
import org.bytedeco.opencv.global.opencv_imgproc.INTER_LINEAR
import org.bytedeco.opencv.global.opencv_imgproc.getPerspectiveTransform
import org.bytedeco.opencv.global.opencv_imgproc.warpPerspective
import org.bytedeco.opencv.global.opencv_objdetect.CORNER_REFINE_SUBPIX
import org.bytedeco.opencv.global.opencv_objdetect.DICT_4X4_50
import org.bytedeco.opencv.global.opencv_objdetect.getPredefinedDictionary
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.MatVector
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.Size
import org.bytedeco.opencv.opencv_objdetect.ArucoDetector
import org.bytedeco.opencv.opencv_objdetect.DetectorParameters
import org.bytedeco.opencv.opencv_objdetect.RefineParameters
import rendering.GameAreaScale

/**
 * Rectifies the camera frame to a fixed top-down rectangle using four ArUco corner markers.
 *
 * The four markers sit at the corners of the working area. Their centers are mapped onto the
 * corners of a [GameAreaScale.DESK_W_PX] x [GameAreaScale.DESK_H_PX] output image with a perspective
 * transform, which removes the camera tilt (perspective) and produces a consistent crop independent
 * of camera placement. This replaces the contour-based table crop.
 *
 * Note: a perspective transform is linear and pins the four marker corners exactly; it does NOT
 * remove radial lens (fisheye) distortion. Residual barrel distortion stays as a small bow in the
 * middle of the edges. Add a lens-undistortion step before rectification if that residual is too
 * large for the use case.
 */
object ArucoTableRectifier {

    /** Marker dictionary; 4x4 has the fewest cells and is the most robust for small markers. */
    private const val DICTIONARY_ID = DICT_4X4_50

    // Marker IDs mapped to the GAME corners of the output (top-left origin), NOT the raw camera
    // corners. The camera is mounted rotated 180° relative to the projection, so in the raw frame
    // the markers appear: id=0 top-left, id=3 top-right, id=1 bottom-left, id=2 bottom-right.
    // Mapping by id, the homography re-orients to game space, so the raw rotation needs no extra
    // step. If the rectified debug image comes out upside down, swap these four values.
    private const val MARKER_TOP_LEFT = 2
    private const val MARKER_TOP_RIGHT = 1
    private const val MARKER_BOTTOM_RIGHT = 0
    private const val MARKER_BOTTOM_LEFT = 3

    /** Required ArUco marker ids for the four table corners. */
    private val REQUIRED_MARKER_IDS = setOf(
        MARKER_TOP_LEFT,
        MARKER_TOP_RIGHT,
        MARKER_BOTTOM_RIGHT,
        MARKER_BOTTOM_LEFT
    )

    /** Output width in pixels; matches the projection surface so detected pixels map to game space. */
    private const val OUTPUT_WIDTH = GameAreaScale.DESK_W_PX

    /** Output height in pixels; matches the projection surface. */
    private const val OUTPUT_HEIGHT = GameAreaScale.DESK_H_PX

    // Detector parameters tuned for small / low-contrast / lens-distorted corner markers.
    // These mirror the "active" preset from ArucoMarkerDetectionTest; tune there, then copy here.
    private const val ADAPTIVE_THRESH_WIN_SIZE_MIN = 3
    private const val ADAPTIVE_THRESH_WIN_SIZE_MAX = 35
    private const val ADAPTIVE_THRESH_WIN_SIZE_STEP = 4
    private const val ADAPTIVE_THRESH_CONSTANT = 4.0
    private const val MIN_MARKER_PERIMETER_RATE = 0.01
    private const val POLYGONAL_APPROX_ACCURACY_RATE = 0.06
    private const val ERROR_CORRECTION_RATE = 0.7

    /**
     * Fraction of the frame's width/height covered by each of the four corner crops used for
     * projected-marker detection. Each crop is anchored at one frame corner, so adjacent crops
     * overlap by 20% of the frame - a marker sitting near the middle of an edge (off-center
     * projection) is still fully contained in at least one crop instead of being sliced by a hard
     * quadrant boundary and detected in none.
     */
    private const val CORNER_CROP_FRACTION = 0.6

    /**
     * Side length of one beamer-projected corner marker, in projection pixels
     * ([OUTPUT_WIDTH] x [OUTPUT_HEIGHT] space): 6 marker cells of 25 px each. Shared with
     * [scenes.ArucoDetectionScene], which draws the markers at exactly this size - the geometry must
     * match, because [detectTransform]'s projected-marker path maps each detected marker back onto
     * this known position instead of onto an output corner.
     */
    const val PROJECTED_MARKER_SIZE_PX = 150.0

    /**
     * Distance from each projection edge to the nearest edge of its corner marker, in projection
     * pixels. Shared with [scenes.ArucoDetectionScene] - see [PROJECTED_MARKER_SIZE_PX].
     */
    const val PROJECTED_MARKER_MARGIN_PX = 60.0

    /**
     * Which marker corner anchors the crop. OpenCV orders the four corners 0..3 as top-left,
     * top-right, bottom-right, bottom-left in the marker's own orientation, and draws corner 0 as
     * the red dot in drawDetectedMarkers. Using corner 0 anchors the rectified rectangle exactly on
     * those red dots (as seen in aruco_detection.jpg).
     */
    private const val MARKER_REFERENCE_CORNER = 0

    /**
     * A 2D point in image pixels.
     *
     * @property x horizontal image coordinate.
     * @property y vertical image coordinate.
     */
    private data class Pt(val x: Float, val y: Float)

    /**
     * A computed rectification transform, valid for as long as the camera and the four corner
     * markers do not move. Lets a caller detect markers once and reapply the same transform to
     * later frames instead of re-running marker detection (the expensive part) every time.
     */
    class Transform internal constructor(private val matrix: Mat) {
        /** Applies this transform to [image], producing a fresh rectified [OUTPUT_WIDTH]x[OUTPUT_HEIGHT] output. */
        fun apply(image: Mat): Mat {
            val output = Mat()
            warpPerspective(image, output, matrix, Size(OUTPUT_WIDTH, OUTPUT_HEIGHT), INTER_LINEAR, 0, null)
            return output
        }

        /** Releases the native transform matrix. */
        fun release() = matrix.release()
    }

    /**
     * Detects the four corner markers in [image] and computes the perspective [Transform], or null
     * when the image is empty or any of the four corner markers is missing.
     *
     * @param invertedProjectedMarkers Set to true when the markers are beamer-projected in inverted
     * form (bright border on a black background, see [scenes.ArucoDetectionScene]): projecting
     * "black" only yields ambient gray on the white table, so inverted markers have far better
     * contrast. Detection then inverts the camera frame first (so the inverted projection reads as
     * a standard black-border marker) and searches each of the four corner crops of the frame
     * separately instead of the full frame, which keeps the adaptive thresholding local to the
     * projection corner a marker actually sits in and away from clutter around the table.
     *
     * The destination mapping differs too: physical stickers are placed so their reference corners
     * mark the working area's corners, so they map onto the output corners. Projected markers sit
     * INSIDE the projection (inset by [PROJECTED_MARKER_MARGIN_PX]) - mapping those onto the output
     * corners would crop away the projection's outer band and stretch only the inner quad to full
     * size. Instead each detected reference corner is mapped back onto the marker's known projected
     * position, so the rectified output covers the entire projection surface.
     */
    fun detectTransform(image: Mat, invertedProjectedMarkers: Boolean = false): Transform? {
        if (image.empty()) return null

        val pointsById = if (invertedProjectedMarkers) {
            detectInvertedMarkersInCornerCrops(image)
        } else {
            detectMarkerReferencePoints(image)
        }
        val topLeft = pointsById[MARKER_TOP_LEFT] ?: return null
        val topRight = pointsById[MARKER_TOP_RIGHT] ?: return null
        val bottomRight = pointsById[MARKER_BOTTOM_RIGHT] ?: return null
        val bottomLeft = pointsById[MARKER_BOTTOM_LEFT] ?: return null

        val sourcePoints = pointsMat(listOf(topLeft, topRight, bottomRight, bottomLeft))
        val destinationPoints = pointsMat(
            if (invertedProjectedMarkers) projectedMarkerDestinations() else outputCornerDestinations()
        )

        val matrix = getPerspectiveTransform(sourcePoints, destinationPoints)
        sourcePoints.release()
        destinationPoints.release()
        return Transform(matrix)
    }

    /** Destination points for physical stickers: their reference corners mark the output corners. */
    private fun outputCornerDestinations(): List<Pt> = listOf(
        Pt(0f, 0f),
        Pt(OUTPUT_WIDTH.toFloat(), 0f),
        Pt(OUTPUT_WIDTH.toFloat(), OUTPUT_HEIGHT.toFloat()),
        Pt(0f, OUTPUT_HEIGHT.toFloat())
    )

    /**
     * Destination points for beamer-projected markers: each marker's reference corner (its own
     * top-left, [MARKER_REFERENCE_CORNER]) maps back onto the projected position
     * [scenes.ArucoDetectionScene] draws it at, so the perspective transform extrapolates out to the
     * full projection surface instead of cropping to the inner marker quad.
     */
    private fun projectedMarkerDestinations(): List<Pt> {
        val nearEdge = PROJECTED_MARKER_MARGIN_PX.toFloat()
        val farEdgeX = (OUTPUT_WIDTH - PROJECTED_MARKER_MARGIN_PX - PROJECTED_MARKER_SIZE_PX).toFloat()
        val farEdgeY = (OUTPUT_HEIGHT - PROJECTED_MARKER_MARGIN_PX - PROJECTED_MARKER_SIZE_PX).toFloat()
        return listOf(
            Pt(nearEdge, nearEdge),
            Pt(farEdgeX, nearEdge),
            Pt(farEdgeX, farEdgeY),
            Pt(nearEdge, farEdgeY)
        )
    }

    /**
     * Rectifies [image] to a top-down [OUTPUT_WIDTH] x [OUTPUT_HEIGHT] view using the four corner
     * markers. One-shot convenience over [detectTransform] + [Transform.apply] for callers that do
     * not need to reuse the transform across frames.
     *
     * @param image The raw camera frame (BGR).
     * @return The rectified image, or null when the image is empty or any of the four corner markers
     *         is missing (the caller can then fall back to another crop strategy).
     */
    fun rectify(image: Mat): Mat? {
        val transform = detectTransform(image) ?: return null
        val output = transform.apply(image)
        transform.release()
        return output
    }

    /** Returns the required ArUco ids that are not visible in [image]. */
    fun missingRequiredMarkerIds(image: Mat): Set<Int> {
        if (image.empty()) return REQUIRED_MARKER_IDS

        val detectedIds = detectMarkerReferencePoints(image).keys
        return REQUIRED_MARKER_IDS - detectedIds
    }

    /** Detects all markers and returns a map of marker id to its reference-corner point in image pixels. */
    private fun detectMarkerReferencePoints(image: Mat): Map<Int, Pt> {
        val detector = buildDetector()
        val corners = MatVector()
        val ids = Mat()
        val referencePoints = mutableMapOf<Int, Pt>()

        try {
            detector.detectMarkers(image, corners, ids)
            if (ids.empty() || ids.rows() == 0) return emptyMap()

            val idIndexer = ids.createIndexer<IntIndexer>()
            for (i in 0 until corners.size()) {
                val id = idIndexer.get(i, 0L)
                referencePoints[id] = markerReferenceCorner(corners.get(i))
            }
            idIndexer.release()
        } finally {
            corners.close()
            ids.release()
            detector.close()
        }

        return referencePoints
    }

    /**
     * Detects beamer-projected inverted markers: inverts the whole frame once (so the projected
     * bright-on-black markers read as standard black-border markers), then runs detection on each of
     * the four corner crops (see [CORNER_CROP_FRACTION]) and maps every found reference point back
     * into full-frame pixel coordinates.
     *
     * Results are merged by marker id across the crops; where overlapping crops both find the same
     * marker, the first hit wins (the reference point is the same physical corner either way).
     */
    private fun detectInvertedMarkersInCornerCrops(image: Mat): Map<Int, Pt> {
        val inverted = Mat()
        bitwise_not(image, inverted)
        try {
            val cropWidth = (inverted.cols() * CORNER_CROP_FRACTION).toInt()
            val cropHeight = (inverted.rows() * CORNER_CROP_FRACTION).toInt()
            if (cropWidth < 1 || cropHeight < 1) return emptyMap()

            val cropOrigins = listOf(
                0 to 0,
                inverted.cols() - cropWidth to 0,
                inverted.cols() - cropWidth to inverted.rows() - cropHeight,
                0 to inverted.rows() - cropHeight
            )

            val referencePoints = mutableMapOf<Int, Pt>()
            for ((originX, originY) in cropOrigins) {
                val crop = Mat(inverted, Rect(originX, originY, cropWidth, cropHeight))
                val pointsInCrop = detectMarkerReferencePoints(crop)
                crop.release()
                for ((id, point) in pointsInCrop) {
                    if (id !in referencePoints) {
                        referencePoints[id] = Pt(point.x + originX, point.y + originY)
                    }
                }
            }
            return referencePoints
        } finally {
            inverted.release()
        }
    }

    /** Reads the [MARKER_REFERENCE_CORNER] corner (the red dot) of one marker (corner Mat shape 1 x 4 x 2). */
    private fun markerReferenceCorner(cornerMat: Mat): Pt {
        val cornerIndexer = cornerMat.createIndexer<FloatIndexer>()
        val x = cornerIndexer.get(0L, MARKER_REFERENCE_CORNER.toLong(), 0L)
        val y = cornerIndexer.get(0L, MARKER_REFERENCE_CORNER.toLong(), 1L)
        cornerIndexer.release()
        return Pt(x, y)
    }

    /** Builds a 4x2 CV_32F matrix of points for [getPerspectiveTransform]. */
    private fun pointsMat(points: List<Pt>): Mat {
        val mat = Mat(points.size, 2, CV_32F)
        val indexer = mat.createIndexer<FloatIndexer>()
        points.forEachIndexed { row, point ->
            indexer.put(row.toLong(), 0L, point.x)
            indexer.put(row.toLong(), 1L, point.y)
        }
        indexer.release()
        return mat
    }

    /** Builds an ArUco detector with parameters tuned for the corner markers. */
    private fun buildDetector(): ArucoDetector {
        val dictionary = getPredefinedDictionary(DICTIONARY_ID)
        val parameters = DetectorParameters().apply {
            adaptiveThreshWinSizeMin(ADAPTIVE_THRESH_WIN_SIZE_MIN)
            adaptiveThreshWinSizeMax(ADAPTIVE_THRESH_WIN_SIZE_MAX)
            adaptiveThreshWinSizeStep(ADAPTIVE_THRESH_WIN_SIZE_STEP)
            adaptiveThreshConstant(ADAPTIVE_THRESH_CONSTANT)
            minMarkerPerimeterRate(MIN_MARKER_PERIMETER_RATE)
            polygonalApproxAccuracyRate(POLYGONAL_APPROX_ACCURACY_RATE)
            errorCorrectionRate(ERROR_CORRECTION_RATE)
            cornerRefinementMethod(CORNER_REFINE_SUBPIX)
        }
        return ArucoDetector(dictionary, parameters, RefineParameters())
    }
}
