package imagedetectiontests

import imageprocessing.CameraManager
import kotlinx.coroutines.runBlocking
import org.bytedeco.javacpp.indexer.FloatIndexer
import org.bytedeco.javacpp.indexer.IntIndexer
import org.bytedeco.opencv.global.opencv_imgcodecs.imwrite
import org.bytedeco.opencv.global.opencv_objdetect.CORNER_REFINE_SUBPIX
import org.bytedeco.opencv.global.opencv_objdetect.DICT_4X4_50
import org.bytedeco.opencv.global.opencv_objdetect.drawDetectedMarkers
import org.bytedeco.opencv.global.opencv_objdetect.getPredefinedDictionary
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.MatVector
import org.bytedeco.opencv.opencv_core.Scalar
import org.bytedeco.opencv.opencv_objdetect.ArucoDetector
import org.bytedeco.opencv.opencv_objdetect.DetectorParameters
import org.bytedeco.opencv.opencv_objdetect.RefineParameters
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Manual diagnostic for ArUco corner-marker detection and detector-parameter tuning.
 *
 * This is NOT an automated assertion test. It has two entry points:
 *
 * 1. [sweepDetectorParameters]: captures several frames once and runs every preset in
 *    [tuningPresets] against all of them, printing for each preset how many of the four expected
 *    markers were found per frame. Use this to find the most *reliable* parameter set for the
 *    current lighting/markers without re-capturing for each try.
 * 2. [detectMarkersFromCamera]: single capture using [activeTuning]; prints each marker's ID,
 *    corners and center and writes an annotated control image to `debug/aruco_detection.jpg`.
 *
 * Workflow: run the sweep, pick the preset with the most "frames with all 4", copy its values into
 * [activeTuning] (and later into the production detector), then run the single capture to verify
 * visually. When no camera is present (CI) both tests print a notice and return without failing.
 */
class ArucoMarkerDetectionTest {

    /** Dictionary with the fewest cells (4x4), most robust for small markers; 50 IDs are plenty for 4 corners. */
    private val dictionaryId = DICT_4X4_50

    /** The four corner-marker IDs that should be present. Adjust if you use different IDs. */
    private val expectedMarkerIds = setOf(0, 1, 2, 3)

    /** How many frames the sweep captures to measure detection stability over time. */
    private val sweepFrameCount = 5

    /** Annotated control image written for visual verification. */
    private val outputImagePath = Paths.get("debug", "aruco_detection.jpg")

    /**
     * Tunable ArUco detector parameters. Defaults match OpenCV defaults.
     *
     * @property name readable preset label printed in diagnostic output.
     * @property adaptiveThreshWinSizeMin smallest adaptive-threshold window size.
     * @property adaptiveThreshWinSizeMax largest adaptive-threshold window size.
     * @property adaptiveThreshWinSizeStep step between adaptive-threshold window sizes.
     * @property adaptiveThreshConstant constant subtracted during adaptive thresholding.
     * @property minMarkerPerimeterRate minimum marker perimeter relative to image size.
     * @property maxMarkerPerimeterRate maximum marker perimeter relative to image size.
     * @property polygonalApproxAccuracyRate tolerance for approximating marker contours as quads.
     * @property errorCorrectionRate tolerated marker bit-error correction rate.
     * @property cornerRefinement whether subpixel corner refinement is enabled.
     */
    private data class ArucoTuning(
        val name: String,
        val adaptiveThreshWinSizeMin: Int = 3,
        val adaptiveThreshWinSizeMax: Int = 23,
        val adaptiveThreshWinSizeStep: Int = 10,
        val adaptiveThreshConstant: Double = 7.0,
        val minMarkerPerimeterRate: Double = 0.03,
        val maxMarkerPerimeterRate: Double = 4.0,
        val polygonalApproxAccuracyRate: Double = 0.03,
        val errorCorrectionRate: Double = 0.6,
        val cornerRefinement: Boolean = true
    )

    /**
     * Presets compared by the sweep. "default" is the OpenCV baseline; the others target the known
     * problems (small markers, uneven lighting/dark corners, barrel distortion). Add your own freely.
     */
    private val tuningPresets = listOf(
        ArucoTuning("default"),
        ArucoTuning(
            name = "small-markers",
            minMarkerPerimeterRate = 0.01,
            adaptiveThreshWinSizeMin = 3,
            adaptiveThreshWinSizeMax = 15,
            adaptiveThreshWinSizeStep = 4
        ),
        ArucoTuning(
            name = "low-contrast",
            adaptiveThreshConstant = 3.0,
            adaptiveThreshWinSizeMin = 5,
            adaptiveThreshWinSizeMax = 35,
            adaptiveThreshWinSizeStep = 6
        ),
        ArucoTuning(
            name = "distortion-tolerant",
            minMarkerPerimeterRate = 0.01,
            polygonalApproxAccuracyRate = 0.08,
            errorCorrectionRate = 0.8
        ),
        ArucoTuning(
            name = "aggressive",
            minMarkerPerimeterRate = 0.008,
            adaptiveThreshWinSizeMin = 3,
            adaptiveThreshWinSizeMax = 45,
            adaptiveThreshWinSizeStep = 4,
            adaptiveThreshConstant = 3.0,
            polygonalApproxAccuracyRate = 0.08,
            errorCorrectionRate = 0.8
        )
    )

    /** Parameter set used for the single annotated capture. Copy the winning sweep preset here. */
    private val activeTuning = ArucoTuning(
        name = "active",
        minMarkerPerimeterRate = 0.01,
        adaptiveThreshWinSizeMin = 3,
        adaptiveThreshWinSizeMax = 35,
        adaptiveThreshWinSizeStep = 4,
        adaptiveThreshConstant = 4.0,
        polygonalApproxAccuracyRate = 0.06,
        errorCorrectionRate = 0.7
    )

    /**
     * Captures several frames once and reports detection reliability for every preset, so the most
     * stable parameter set for the current setup can be picked from a single capture session.
     */
    @Test
    fun sweepDetectorParameters() {
        val frames = runBlocking {
            try {
                (1..sweepFrameCount).mapNotNull { CameraManager.takePhoto() }
            } finally {
                CameraManager.shutdown()
            }
        }

        if (frames.isEmpty()) {
            println("[ArUco sweep] No camera frames available - skipping (expected on CI without a webcam).")
            return
        }

        try {
            println("[ArUco sweep] Captured ${frames.size} frame(s) at ${frames.first().cols()}x${frames.first().rows()} px.")
            println("[ArUco sweep] Expected marker IDs: $expectedMarkerIds")
            println("[ArUco sweep] Preset comparison (higher 'all 4' is better):")
            for (tuning in tuningPresets) {
                evaluatePreset(tuning, frames)
            }
        } finally {
            frames.forEach { it.release() }
        }
    }

    /** Runs one preset against all captured frames and prints a stability summary. */
    private fun evaluatePreset(tuning: ArucoTuning, frames: List<Mat>) {
        val detector = buildDetector(tuning)
        val perFrameCounts = mutableListOf<Int>()
        val foundEverywhere = expectedMarkerIds.toMutableSet()
        val foundSomewhere = mutableSetOf<Int>()
        var framesWithAll = 0

        try {
            for (frame in frames) {
                val foundIds = detectIds(detector, frame)
                perFrameCounts.add(foundIds.count { it in expectedMarkerIds })
                foundSomewhere.addAll(foundIds)
                foundEverywhere.retainAll(foundIds)
                if (expectedMarkerIds.all { it in foundIds }) framesWithAll++
            }
        } finally {
            detector.close()
        }

        val neverFound = expectedMarkerIds - foundSomewhere
        val unstable = expectedMarkerIds - foundEverywhere - neverFound
        println(
            "  - %-20s all 4 in %d/%d frames | per-frame counts=%s | never found=%s | unstable=%s".format(
                tuning.name,
                framesWithAll,
                frames.size,
                perFrameCounts,
                if (neverFound.isEmpty()) "-" else neverFound.toString(),
                if (unstable.isEmpty()) "-" else unstable.toString()
            )
        )
    }

    /**
     * Single live capture: detects markers with [activeTuning], prints details and saves an
     * annotated control image.
     */
    @Test
    fun detectMarkersFromCamera() {
        val image = runBlocking {
            try {
                CameraManager.takePhoto()
            } finally {
                CameraManager.shutdown()
            }
        }

        if (image == null || image.empty()) {
            println("[ArUco] No camera frame available - skipping (this is expected on CI without a webcam).")
            image?.release()
            return
        }

        try {
            detectAndReport(image)
        } finally {
            image.release()
        }
    }

    /** Runs the ArUco detector on [image], prints results, and saves an annotated image. */
    private fun detectAndReport(image: Mat) {
        val detector = buildDetector(activeTuning)
        val corners = MatVector()
        val ids = Mat()

        try {
            detector.detectMarkers(image, corners, ids)

            val markerCount = ids.rows()
            println("[ArUco] Tuning '${activeTuning.name}' on ${image.cols()}x${image.rows()} px - detected $markerCount marker(s).")

            if (markerCount == 0) {
                println("[ArUco] Nothing detected. Run sweepDetectorParameters() to find a better preset.")
            } else {
                printMarkers(corners, ids)
                // Green borders + IDs drawn onto the frame; explicit Scalar picks the unambiguous overload.
                drawDetectedMarkers(image, corners, ids, Scalar(0.0, 255.0, 0.0, 0.0))
            }

            saveAnnotatedImage(image)
        } finally {
            corners.close()
            ids.release()
            detector.close()
        }
    }

    /** Builds an ArUco detector from [tuning]. */
    private fun buildDetector(tuning: ArucoTuning): ArucoDetector {
        val dictionary = getPredefinedDictionary(dictionaryId)
        val detectorParameters = DetectorParameters().apply {
            adaptiveThreshWinSizeMin(tuning.adaptiveThreshWinSizeMin)
            adaptiveThreshWinSizeMax(tuning.adaptiveThreshWinSizeMax)
            adaptiveThreshWinSizeStep(tuning.adaptiveThreshWinSizeStep)
            adaptiveThreshConstant(tuning.adaptiveThreshConstant)
            minMarkerPerimeterRate(tuning.minMarkerPerimeterRate)
            maxMarkerPerimeterRate(tuning.maxMarkerPerimeterRate)
            polygonalApproxAccuracyRate(tuning.polygonalApproxAccuracyRate)
            errorCorrectionRate(tuning.errorCorrectionRate)
            if (tuning.cornerRefinement) {
                // Subpixel corner refinement: important for small markers so the homography stays accurate.
                cornerRefinementMethod(CORNER_REFINE_SUBPIX)
            }
        }
        return ArucoDetector(dictionary, detectorParameters, RefineParameters())
    }

    /** Detects markers and returns the set of detected IDs. */
    private fun detectIds(detector: ArucoDetector, image: Mat): Set<Int> {
        val corners = MatVector()
        val ids = Mat()
        try {
            detector.detectMarkers(image, corners, ids)
            return readIds(ids)
        } finally {
            corners.close()
            ids.release()
        }
    }

    /** Reads the integer IDs out of an ArUco ids Mat (N x 1, CV_32S). */
    private fun readIds(ids: Mat): Set<Int> {
        if (ids.empty() || ids.rows() == 0) return emptySet()
        val indexer = ids.createIndexer<IntIndexer>()
        val result = (0 until ids.rows()).map { indexer.get(it.toLong(), 0L) }.toSet()
        indexer.release()
        return result
    }

    /** Prints ID, the four corner points and the center for every detected marker. */
    private fun printMarkers(corners: MatVector, ids: Mat) {
        val idIndexer = ids.createIndexer<IntIndexer>()

        for (i in 0 until corners.size()) {
            val id = idIndexer.get(i, 0L)
            val cornerMat = corners.get(i)
            val cornerIndexer = cornerMat.createIndexer<FloatIndexer>()

            // Corner Mat has shape 1 x 4 x 2: 4 points (TL, TR, BR, BL), 2 channels (x, y).
            val xs = DoubleArray(4)
            val ys = DoubleArray(4)
            for (point in 0 until 4) {
                xs[point] = cornerIndexer.get(0L, point.toLong(), 0L).toDouble()
                ys[point] = cornerIndexer.get(0L, point.toLong(), 1L).toDouble()
            }

            val centerX = xs.average()
            val centerY = ys.average()

            println("[ArUco] ID=$id")
            val labels = listOf("TL", "TR", "BR", "BL")
            for (point in 0 until 4) {
                println("          ${labels[point]} = (%.1f, %.1f)".format(xs[point], ys[point]))
            }
            println("          center = (%.1f, %.1f)".format(centerX, centerY))

            cornerIndexer.release()
        }

        idIndexer.release()
    }

    /** Writes the annotated frame so the detection can be checked visually. */
    private fun saveAnnotatedImage(image: Mat) {
        try {
            Files.createDirectories(outputImagePath.parent)
            imwrite(outputImagePath.toString(), image)
            println("[ArUco] Annotated control image written to ${outputImagePath.toAbsolutePath()}")
        } catch (e: Exception) {
            println("[ArUco] Could not write control image: ${e.message}")
        }
    }
}
