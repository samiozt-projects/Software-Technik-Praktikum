package imageprocessing

import config.ConfigManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.bytedeco.opencv.global.opencv_core.mean
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Rect
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties
import kotlin.math.abs

/**
 * Measured statistics of a sampled calibration patch.
 * @property brightness Mean grayscale brightness (0-255) of the sampled region.
 * @property blueMean Mean blue channel value (0-255) of the sampled region.
 * @property redMean Mean red channel value (0-255) of the sampled region.
 */
data class PatchMeasurement(val brightness: Double, val blueMean: Double, val redMean: Double)

/** Fraction of the frame's width/height sampled, centered - large enough to reliably land inside a full-screen reference area regardless of exact camera/table alignment. */
private const val CALIBRATION_PATCH_FRACTION = 0.5

/**
 * Samples a centered region of [image] (raw BGR camera frame) and measures its brightness and
 * color balance.
 *
 * Deliberately samples the raw frame directly rather than the ArUco-rectified table: calibration
 * needs to work even before/without a successful marker detection, and a generously-sized central
 * region of the raw frame reliably lands inside a full-screen projected reference area regardless of
 * exact camera/table alignment.
 *
 * @return The measurement, or null when [image] is empty or too small to sample.
 */
fun measureCalibrationPatch(image: Mat): PatchMeasurement? {
    if (image.empty() || image.cols() < 4 || image.rows() < 4) return null

    val width = (image.cols() * CALIBRATION_PATCH_FRACTION).toInt().coerceAtLeast(1)
    val height = (image.rows() * CALIBRATION_PATCH_FRACTION).toInt().coerceAtLeast(1)
    val x = (image.cols() - width) / 2
    val y = (image.rows() - height) / 2
    val patch = Mat(image, Rect(x, y, width, height))

    val gray = Mat()
    cvtColor(patch, gray, COLOR_BGR2GRAY)
    val brightness = mean(gray).get(0)
    val channelMeans = mean(patch)

    val measurement = PatchMeasurement(
        brightness = brightness,
        blueMean = channelMeans.blue(),
        redMean = channelMeans.red()
    )

    gray.release()
    patch.release()
    return measurement
}

/**
 * Tries each of [candidates] via [measure] and returns the one whose measured value is closest to
 * [target].
 *
 * A plain sweep rather than an adaptive search: a camera property's response direction and shape
 * (e.g. does a larger exposure value mean brighter or darker) is backend/device specific and not
 * known in advance, so a bounded sweep over a configured range is the most robust choice here - it
 * cannot diverge or overshoot the way a gradient-following search could on an unknown response curve.
 *
 * @param candidates Values to try, in the order they are applied/measured.
 * @param target The value [measure] should ideally return.
 * @param measure Applies one candidate and returns its measured result, or null if measurement failed.
 * @return The best candidate, or null when every candidate's measurement failed.
 */
suspend fun <T> sweepForClosestMatch(candidates: List<T>, target: Double, measure: suspend (T) -> Double?): T? {
    var best: T? = null
    var bestError = Double.POSITIVE_INFINITY

    for (candidate in candidates) {
        val measured = measure(candidate) ?: continue
        val error = abs(measured - target)
        if (error < bestError) {
            bestError = error
            best = candidate
        }
    }

    return best
}

/**
 * Runs the camera auto-calibration sweep once: finds the exposure value that best matches
 * [ConfigManager.CameraAutoCalibrationConfig.targetBrightness], then the white-balance color
 * temperature that best neutralizes the blue/red channel balance.
 *
 * The caller is expected to already be showing a full-screen neutral/white reference (e.g. via the
 * beamer) before calling this, since [measureCalibrationPatch] samples the live camera frame
 * directly. A failure at any point (disabled, invalid configuration, no camera available) leaves the
 * camera on whatever settings it already had and never blocks the caller - calibration is a
 * best-effort improvement, not a requirement to proceed.
 *
 * The whole sweep is additionally bounded by [ConfigManager.CameraAutoCalibrationConfig.maxDurationMs]:
 * if that elapses (e.g. an unexpectedly slow or stuck camera driver), calibration is abandoned and
 * the camera is left on whatever settings it already had, rather than risking an unbounded startup
 * delay.
 *
 * @return true when at least the exposure sweep found a usable frame and was applied.
 */
suspend fun runCameraAutoCalibration(
    config: ConfigManager.CameraAutoCalibrationConfig = ConfigManager.loadCameraAutoCalibrationConfig()
): Boolean {
    if (!config.enabled) {
        println("[Calibration] Auto-calibration disabled via config - skipping.")
        return false
    }
    if (config.exposureStep <= 0 || config.exposureMin > config.exposureMax) {
        println("[Calibration] Invalid exposure sweep configuration - skipping.")
        return false
    }
    if (config.whiteBalanceStep <= 0 || config.whiteBalanceMin > config.whiteBalanceMax) {
        println("[Calibration] Invalid white-balance sweep configuration - skipping.")
        return false
    }

    val completed = withTimeoutOrNull(config.maxDurationMs) { runSweeps(config) }
    if (completed == null) {
        println("[Calibration] Timed out after ${config.maxDurationMs}ms - keeping the camera's current settings.")
        return false
    }
    return completed
}

/** Runs the exposure sweep, then the white-balance sweep, applying each winning candidate as it is found. */
private suspend fun runSweeps(config: ConfigManager.CameraAutoCalibrationConfig): Boolean {
    // The sweep applies each candidate via CameraManager.setExposure/setWhiteBalanceTemperature,
    // which are silent no-ops while the camera is still closed. Without initializing first, the very
    // first candidate's setExposure would be lost (the camera only opens inside the subsequent
    // takePhoto), so that candidate would be measured with the camera's startup settings - and the
    // measurement wrongly credited to it, potentially skewing the whole sweep's winner.
    if (!CameraManager.initialize()) {
        println("[Calibration] No camera available - skipping auto-calibration.")
        return false
    }

    val exposureCandidates = (config.exposureMin..config.exposureMax step config.exposureStep).toList()
    val bestExposure = sweepForClosestMatch(exposureCandidates, config.targetBrightness) { candidate ->
        CameraManager.setExposure(candidate)
        delay(config.settleDelayMs)
        val frame = CameraManager.takePhoto() ?: return@sweepForClosestMatch null
        val measurement = measureCalibrationPatch(frame)
        frame.release()
        measurement?.brightness
    }

    if (bestExposure == null) {
        println("[Calibration] No camera frame available - skipping auto-calibration.")
        return false
    }
    CameraManager.setExposure(bestExposure)
    println("[Calibration] Exposure set to $bestExposure (target brightness ${config.targetBrightness}).")

    val whiteBalanceCandidates = (config.whiteBalanceMin..config.whiteBalanceMax step config.whiteBalanceStep).toList()
    val bestWhiteBalance = sweepForClosestMatch(whiteBalanceCandidates, target = 0.0) { candidate ->
        CameraManager.setWhiteBalanceTemperature(candidate)
        delay(config.settleDelayMs)
        val frame = CameraManager.takePhoto() ?: return@sweepForClosestMatch null
        val measurement = measureCalibrationPatch(frame)
        frame.release()
        measurement?.let { abs(it.blueMean - it.redMean) }
    }

    if (bestWhiteBalance == null) {
        println("[Calibration] White-balance sweep failed - keeping the configured white balance.")
        return true
    }
    CameraManager.setWhiteBalanceTemperature(bestWhiteBalance)
    println("[Calibration] White balance set to ${bestWhiteBalance}K.")
    logCalibrationDriftAndPersist(bestExposure, bestWhiteBalance)
    return true
}

/** Where the last successful calibration result is remembered across app restarts (see [logCalibrationDriftAndPersist]). */
private val CALIBRATION_RESULT_CACHE_PATH: Path = Paths.get("debug", "camera-calibration-last-result.properties")

/**
 * Diagnostic only: compares this run's white balance against the last persisted result and logs the
 * delta, then persists the new result for the next comparison. Every sweep still runs in full and
 * every applied value is exactly what the sweep found - this never changes calibration *behavior*,
 * only makes cross-session drift visible: the HSV street/bridge color thresholds
 * (config/app-config.properties) were tuned against captures from one specific calibration, and a
 * fresh sweep landing on a different white balance next session can shift detected colors relative
 * to those thresholds without any code or config change being at fault. Exposure is not compared -
 * it directly targets a measured brightness and is expected to legitimately vary with real room
 * lighting, unlike white balance which the HSV tuning assumes is roughly stable.
 *
 * Persistence failures (e.g. read-only debug directory) are logged and otherwise ignored - this is a
 * diagnostic aid, never a reason to fail calibration.
 */
private fun logCalibrationDriftAndPersist(exposure: Int, whiteBalanceKelvin: Int) {
    val previous = readLastCalibrationResult()
    if (previous != null) {
        val delta = whiteBalanceKelvin - previous.second
        println(
            "[Calibration] White balance ${whiteBalanceKelvin}K (previous recorded run: ${previous.second}K, " +
                "delta ${if (delta >= 0) "+" else ""}${delta}K). The HSV street/bridge thresholds in " +
                "app-config.properties were tuned against one specific calibration - a large delta here is a " +
                "plausible explanation if detected street/bridge colors look different from a previous session."
        )
    } else {
        println("[Calibration] No previous calibration result on record yet - this run's result ($whiteBalanceKelvin K) becomes the baseline.")
    }

    try {
        Files.createDirectories(CALIBRATION_RESULT_CACHE_PATH.parent)
        val properties = Properties()
        properties.setProperty("exposure", exposure.toString())
        properties.setProperty("white_balance_kelvin", whiteBalanceKelvin.toString())
        Files.newOutputStream(CALIBRATION_RESULT_CACHE_PATH).use {
            properties.store(it, "Last successful camera auto-calibration result - see logCalibrationDriftAndPersist.")
        }
    } catch (e: Exception) {
        println("[Calibration] Could not persist this run's calibration result for future comparison: ${e.message}")
    }
}

/** Reads the last persisted calibration result as (exposure, whiteBalanceKelvin), or null when none exists yet or it cannot be parsed. */
private fun readLastCalibrationResult(): Pair<Int, Int>? {
    if (!Files.exists(CALIBRATION_RESULT_CACHE_PATH)) return null
    return try {
        val properties = Properties()
        Files.newInputStream(CALIBRATION_RESULT_CACHE_PATH).use { properties.load(it) }
        val exposure = properties.getProperty("exposure")?.toIntOrNull() ?: return null
        val whiteBalance = properties.getProperty("white_balance_kelvin")?.toIntOrNull() ?: return null
        exposure to whiteBalance
    } catch (e: Exception) {
        null
    }
}

/** Delay between marker-detection retry attempts, in ms. */
private const val ARUCO_SEED_RETRY_DELAY_MS = 300L

/**
 * Captures a frame and detects the four corner markers in it, storing the result in
 * [PermanentArucoTransform] for the rest of the application's runtime.
 *
 * Meant to be called once, at the end of calibration: after [runCameraAutoCalibration] has tuned
 * exposure/white balance, a well-exposed frame detects the markers more reliably than an
 * uncalibrated one. The caller is expected to already be projecting the four corner markers in
 * their inverted form (e.g. via [scenes.ArucoDetectionScene]) - if they are only ever shown during
 * calibration and never again afterward, this is the only chance to ever detect them.
 *
 * @param maxAttempts Number of capture-and-detect attempts before giving up.
 * @return true when detection succeeded and the transform was established.
 */
suspend fun seedPermanentArucoTransform(maxAttempts: Int = 3): Boolean {
    repeat(maxAttempts) { attempt ->
        val frame = CameraManager.takePhoto()
        if (frame != null) {
            val seeded = PermanentArucoTransform.seed(frame)
            frame.release()
            if (seeded) {
                println("[Calibration] Projected corner markers detected and locked in for this session.")
                return true
            }
        }
        if (attempt < maxAttempts - 1) delay(ARUCO_SEED_RETRY_DELAY_MS)
    }
    println(
        "[Calibration] Could not detect the projected corner markers after $maxAttempts attempts - " +
            "falling back to physical-marker detection during gameplay."
    )
    return false
}
