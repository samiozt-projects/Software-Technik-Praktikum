package imagedetectiontests

import imageprocessing.measureCalibrationPatch
import imageprocessing.sweepForClosestMatch
import kotlinx.coroutines.runBlocking
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Scalar
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [measureCalibrationPatch] and [sweepForClosestMatch], the two pure/OpenCV-only building
 * blocks behind camera auto-calibration. The actual camera-driving orchestration
 * (`runCameraAutoCalibration`) needs a real camera and is not covered here, matching the existing
 * "no camera - skip" pattern for camera-dependent tests in this project.
 */
class CameraCalibrationTest {

    /** A uniformly white frame should measure as bright and channel-balanced. */
    @Test
    fun measureCalibrationPatch_uniformWhiteFrame_isBrightAndBalanced() {
        val image = Mat(200, 300, CV_8UC3, Scalar(255.0, 255.0, 255.0, 0.0))

        val result = measureCalibrationPatch(image)

        assertTrue(result != null, "A valid frame should produce a measurement")
        assertTrue(result.brightness > 250.0, "A white frame should measure near-maximum brightness")
        assertTrue(abs(result.blueMean - result.redMean) < 1.0, "A neutral white frame should have balanced channels")

        image.release()
    }

    /** A frame with a blue color cast should measure a higher blue mean than red mean. */
    @Test
    fun measureCalibrationPatch_blueColorCast_isDetected() {
        val image = Mat(200, 300, CV_8UC3, Scalar(220.0, 150.0, 100.0, 0.0))

        val result = measureCalibrationPatch(image)

        assertTrue(result != null)
        assertTrue(result.blueMean > result.redMean, "A blue-cast frame should measure more blue than red")

        image.release()
    }

    /** An empty image cannot be sampled. */
    @Test
    fun measureCalibrationPatch_emptyImage_returnsNull() {
        val empty = Mat()

        assertNull(measureCalibrationPatch(empty))

        empty.release()
    }

    /** The candidate whose measurement is closest to the target is selected. */
    @Test
    fun sweepForClosestMatch_selectsClosestCandidate() = runBlocking {
        val measurements = mapOf(1 to 100.0, 2 to 150.0, 3 to 220.0)

        val best = sweepForClosestMatch(measurements.keys.toList(), target = 160.0) { candidate ->
            measurements.getValue(candidate)
        }

        assertEquals(2, best, "150.0 is closer to the target 160.0 than 100.0 or 220.0")
    }

    /** Candidates whose measurement fails (null) are skipped, not treated as a match. */
    @Test
    fun sweepForClosestMatch_skipsFailedMeasurements() = runBlocking {
        val measurements = mapOf(1 to null, 2 to 90.0, 3 to null)

        val best = sweepForClosestMatch(measurements.keys.toList(), target = 100.0) { candidate ->
            measurements.getValue(candidate)
        }

        assertEquals(2, best, "The only successfully measured candidate should win even if not perfect")
    }

    /** When every candidate's measurement fails, there is nothing to select. */
    @Test
    fun sweepForClosestMatch_allMeasurementsFail_returnsNull() = runBlocking {
        val best = sweepForClosestMatch(listOf(1, 2, 3), target = 100.0) { null }

        assertNull(best)
    }
}
