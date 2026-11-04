package imageprocessing

import config.ConfigManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import org.bytedeco.javacv.VideoInputFrameGrabber
import org.bytedeco.opencv.global.opencv_core.max
import org.bytedeco.opencv.global.opencv_core.min
import org.bytedeco.opencv.global.opencv_imgcodecs.imwrite
import org.bytedeco.opencv.global.opencv_videoio.*
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_videoio.VideoCapture
import org.bytedeco.opencv.opencv_videoio.VideoWriter
import java.nio.file.*

/**
 * Manages the USB webcam lifecycle and handles asynchronous photo capture operations.
 *
 * Capture uses OpenCV [VideoCapture] (DirectShow on Windows) so the manual image controls
 * white balance, focus and exposure can be fixed. Fixed values are the main lever for capture
 * quality: they remove the projector-light color cast and keep colors stable, which the HSV-based
 * [StreetDetection] relies on. They also need no convergence time, so a cold capture stays fast.
 */
object CameraManager {
    /** Camera configuration loaded from config file or using defaults */
    private val cameraConfig: ConfigManager.CameraConfig by lazy {
        ConfigManager.loadCameraConfig()
    }

    /** Model name of the preferred USB camera */
    val CAMERA_MODEL_NAME: String get() = cameraConfig.modelName

    /** Default index for the camera (if no preferred camera is found) (Default: 0 = Webcam) */
    val CAMERA_DEFAULT_INDEX: Int get() = cameraConfig.defaultIndex

    /** Width of camera resolution */
    val CAMERA_WIDTH: Int get() = cameraConfig.width

    /** Height of camera resolution */
    val CAMERA_HEIGHT: Int get() = cameraConfig.height

    /** Fixed white-balance color temperature in Kelvin, or null to keep auto white balance. */
    private val whiteBalanceTemperature: Int? get() = cameraConfig.whiteBalanceTemperature

    /** Fixed focus value, or null to keep autofocus. */
    private val focus: Int? get() = cameraConfig.focus

    /** Fixed exposure value, or null to keep auto-exposure. */
    private val exposure: Int? get() = cameraConfig.exposure

    /** Number of frames combined per photo via pixel median (>= 3 enables the median). */
    private val medianFrameCount: Int get() = cameraConfig.medianFrameCount

    /**
     * How long the webcam streams during initialization before it is considered ready, in ms.
     *
     * The first frames after the stream opens are not usable yet. Streaming for this short period
     * lets the device deliver frames. Capped so a cold capture stays fast; with fixed manual
     * settings no auto-convergence time is needed, and [initialize] is started early in the
     * background during normal gameplay anyway.
     */
    private const val WARMUP_DURATION_MS = 500L

    /** Pause between warm-up reads in milliseconds. */
    private const val WARMUP_GRAB_INTERVAL_MS = 50L

    /** Number of read attempts per frame in [takePhoto] before giving up (bounded so a capture stays fast). */
    private const val GRAB_RETRY_COUNT = 3

    /** Delay between read attempts in milliseconds. */
    private const val GRAB_RETRY_DELAY_MS = 150L

    /** Smallest possible internal buffer, so a capture returns the freshest frame instead of a stale one. */
    private const val MIN_BUFFER_SIZE = 1.0

    /**
     * Auto-exposure property value that selects manual exposure mode (UVC convention: 0.25 = manual).
     * Only used when a fixed [exposure] is configured.
     */
    private const val MANUAL_EXPOSURE_MODE = 0.25

    /** Minimum number of frames required to compute a pixel median. */
    private const val MEDIAN_FRAME_REQUIREMENT = 3

    /** MJPEG pixel format; requested so 1080p fits through USB bandwidth without dropping to a low resolution. */
    private val MJPG_FOURCC: Int =
        VideoWriter.fourcc('M'.code.toByte(), 'J'.code.toByte(), 'P'.code.toByte(), 'G'.code.toByte())

    /** Mutex to synchronize initialization calls and avoid double capture setups */
    private val initMutex = Mutex()

    /** The active video capture instance */
    private var capture: VideoCapture? = null

    /** Indicates whether the camera has been successfully started */
    private var isInitialized = false

    /** Directory used for the last captured debug image during normal gameplay. */
    private val DEFAULT_DEBUG_DIRECTORY: Path = Paths.get("debug")

    /** File name used for the last captured debug image. */
    private const val DEBUG_IMAGE_FILE_NAME = "last_camera_capture.jpg"

    /**
     * Finds the matching camera index by scanning device descriptions for [CAMERA_MODEL_NAME].
     *
     * Uses the DirectShow device enumeration, which matches the index order used by
     * [VideoCapture] with the DirectShow backend on Windows.
     *
     * @return The index of the matched camera, or [CAMERA_DEFAULT_INDEX] on fallback/error.
     */
    suspend fun getPreferredCameraIndex(): Int = withContext(Dispatchers.IO) {
        try {
            val devices = VideoInputFrameGrabber.getDeviceDescriptions()
            findPreferredCameraIndex(devices)?.let { return@withContext it }
        } catch (e: Exception) {
            println("Could not query video devices: ${e.message}")
        }
        return@withContext CAMERA_DEFAULT_INDEX
    }

    /**
     * Returns the first device index whose description matches [CAMERA_MODEL_NAME].
     *
     * @param devices Device descriptions in capture-index order.
     * @return The matching index, or null when no preferred camera was found.
     */
    private fun findPreferredCameraIndex(devices: Array<String>): Int? {
        var index = 0
        while (index < devices.size) {
            val description = devices[index]
            if (description.contains(CAMERA_MODEL_NAME, ignoreCase = true)) {
                println("Found preferred camera '$CAMERA_MODEL_NAME' at index $index: $description")
                return index
            }
            println("Camera at index $index: $description")
            index++
        }
        return null
    }

    /**
     * Initializes and starts the camera capture on the IO thread.
     * If already initialized, returns true immediately.
     *
     * @return True if the camera is successfully initialized, false otherwise.
     */
    suspend fun initialize(): Boolean = initMutex.withLock {
        withContext(Dispatchers.IO) {
            initializeCapture()
        }
    }

    /**
     * Initializes the capture object on the IO dispatcher.
     *
     * @return True if the camera is available after the call, false otherwise.
     */
    private suspend fun initializeCapture(): Boolean {
        if (isInitialized && capture != null) return true

        return try {
            val newCapture = openVideoCapture() ?: return false
            configureCamera(newCapture)
            warmUp(newCapture)
            capture = newCapture
            isInitialized = true
            true
        } catch (e: Exception) {
            println("Error starting the camera: ${e.message}")
            false
        }
    }

    /**
     * Opens the preferred camera with the platform-specific capture backend.
     *
     * @return The opened capture, or null when the device cannot be opened.
     */
    private suspend fun openVideoCapture(): VideoCapture? {
        val targetIndex = getPreferredCameraIndex()
        val osName = System.getProperty("os.name").lowercase()
        val apiPreference = if (osName.contains("win")) CAP_DSHOW else CAP_ANY
        val newCapture = VideoCapture(targetIndex, apiPreference)

        if (newCapture.isOpened) return newCapture

        println("Error starting the camera: VideoCapture could not open device index $targetIndex.")
        newCapture.release()
        return null
    }

    /**
     * Applies resolution, pixel format and the configured manual image controls.
     *
     * Each control is only fixed when a value is configured; a null value leaves the camera on its
     * automatic mode. The actually applied values are logged so they can be read off and tuned.
     */
    private fun configureCamera(capture: VideoCapture) {
        capture.set(CAP_PROP_FRAME_WIDTH, CAMERA_WIDTH.toDouble())
        capture.set(CAP_PROP_FRAME_HEIGHT, CAMERA_HEIGHT.toDouble())
        capture.set(CAP_PROP_FOURCC, MJPG_FOURCC.toDouble())
        capture.set(CAP_PROP_BUFFERSIZE, MIN_BUFFER_SIZE)

        // Fixed white balance removes the projector-light color cast and keeps HSV colors stable.
        whiteBalanceTemperature?.let { temperature ->
            capture.set(CAP_PROP_AUTO_WB, 0.0)
            capture.set(CAP_PROP_WB_TEMPERATURE, temperature.toDouble())
        }

        // Fixed focus avoids autofocus hunting on the low-texture table surface.
        focus?.let { focusValue ->
            capture.set(CAP_PROP_AUTOFOCUS, 0.0)
            capture.set(CAP_PROP_FOCUS, focusValue.toDouble())
        }

        // Fixed exposure keeps brightness constant. Value semantics are camera/backend specific.
        exposure?.let { exposureValue ->
            capture.set(CAP_PROP_AUTO_EXPOSURE, MANUAL_EXPOSURE_MODE)
            capture.set(CAP_PROP_EXPOSURE, exposureValue.toDouble())
        }

        logActualSettings(capture)
    }

    /** Logs the values the driver actually applied, so focus/exposure/white balance can be tuned. */
    private fun logActualSettings(capture: VideoCapture) {
        println(
            "Camera settings applied: " +
                "size=${capture.get(CAP_PROP_FRAME_WIDTH).toInt()}x${capture.get(CAP_PROP_FRAME_HEIGHT).toInt()}, " +
                "autoWB=${capture.get(CAP_PROP_AUTO_WB)}, wbTemp=${capture.get(CAP_PROP_WB_TEMPERATURE)}, " +
                "autofocus=${capture.get(CAP_PROP_AUTOFOCUS)}, focus=${capture.get(CAP_PROP_FOCUS)}, " +
                "autoExposure=${capture.get(CAP_PROP_AUTO_EXPOSURE)}, exposure=${capture.get(CAP_PROP_EXPOSURE)}"
        )
    }

    /**
     * Applies a manual exposure value to the already-open camera, switching it out of automatic
     * exposure mode if necessary. Used by calibration to try candidate values live, without
     * reopening the device. A no-op if the camera has not been initialized yet.
     */
    fun setExposure(value: Int) {
        val currentCapture = capture ?: return
        currentCapture.set(CAP_PROP_AUTO_EXPOSURE, MANUAL_EXPOSURE_MODE)
        currentCapture.set(CAP_PROP_EXPOSURE, value.toDouble())
    }

    /**
     * Applies a manual white-balance color temperature (Kelvin) to the already-open camera,
     * switching it out of automatic white balance if necessary. Used by calibration to try
     * candidate values live, without reopening the device. A no-op if the camera has not been
     * initialized yet.
     */
    fun setWhiteBalanceTemperature(value: Int) {
        val currentCapture = capture ?: return
        currentCapture.set(CAP_PROP_AUTO_WB, 0.0)
        currentCapture.set(CAP_PROP_WB_TEMPERATURE, value.toDouble())
    }

    /** Streams frames briefly so the device starts delivering usable frames before the first capture. */
    private suspend fun warmUp(capture: VideoCapture) {
        val frame = Mat()
        val warmupDeadlineMs = System.currentTimeMillis() + WARMUP_DURATION_MS
        while (System.currentTimeMillis() < warmupDeadlineMs) {
            try {
                capture.read(frame)
            } catch (e: Exception) {
                // Expected during warm-up; ignore until the stream delivers frames.
            }
            delay(WARMUP_GRAB_INTERVAL_MS)
        }
        frame.release()
    }

    /**
     * Captures a single image from the webcam.
     * Automatically initializes the camera if it was not started yet.
     * Reading is performed on the IO thread pool to avoid blocking the main UI thread.
     *
     * When [medianFrameCount] is at least [MEDIAN_FRAME_REQUIREMENT], several frames are captured and
     * combined with a pixel-wise median to suppress sensor and compression noise.
     *
     * @return The captured [Mat] object, or null if the capture failed.
     */
    suspend fun takePhoto(): Mat? = withContext(Dispatchers.IO) {
        if (!ensureInitialized()) return@withContext null
        val currentCapture = capture ?: return@withContext null

        val frames = captureFrames(currentCapture)
        try {
            selectCapturedFrame(frames)
        } finally {
            frames.forEach { it.release() }
        }
    }

    /**
     * Ensures the camera is initialized before a capture.
     *
     * @return True when a capture can be attempted.
     */
    private suspend fun ensureInitialized(): Boolean {
        return isInitialized || initialize()
    }

    /**
     * Captures the configured number of frames for either single-frame or median capture.
     *
     * @param capture active video capture.
     * @return captured non-empty frames.
     */
    private suspend fun captureFrames(capture: VideoCapture): MutableList<Mat> {
        val useMedian = medianFrameCount >= MEDIAN_FRAME_REQUIREMENT
        val framesToGrab = if (useMedian) MEDIAN_FRAME_REQUIREMENT else 1
        val frames = mutableListOf<Mat>()
        repeat(framesToGrab) {
            grabFrame(capture)?.let { frames.add(it) }
        }
        return frames
    }

    /**
     * Selects the returned image from captured frames.
     *
     * @param frames captured frames owned by the caller.
     * @return A new frame for the caller, or null when no usable frame was captured.
     */
    private fun selectCapturedFrame(frames: List<Mat>): Mat? {
        if (frames.size >= MEDIAN_FRAME_REQUIREMENT) return medianOfThree(frames[0], frames[1], frames[2])
        if (frames.isNotEmpty()) return frames.last().clone()

        println("Error capturing camera frame: no usable frame after $GRAB_RETRY_COUNT attempts.")
        return null
    }

    /** Reads one non-empty frame, retrying a few times in case a single read fails. */
    private suspend fun grabFrame(capture: VideoCapture): Mat? {
        repeat(GRAB_RETRY_COUNT) { attempt ->
            try {
                val frame = Mat()
                if (capture.read(frame) && !frame.empty()) {
                    return frame
                }
                frame.release()
            } catch (e: Exception) {
                println("Camera read attempt ${attempt + 1}/$GRAB_RETRY_COUNT failed: ${e.message}")
            }
            delay(GRAB_RETRY_DELAY_MS)
        }
        return null
    }

    /**
     * Computes the per-pixel median of three frames.
     *
     * For three values the median equals max(min(a,b), min(max(a,b), c)), implemented with
     * element-wise OpenCV min/max so it works per channel without sorting each pixel.
     */
    private fun medianOfThree(a: Mat, b: Mat, c: Mat): Mat {
        val minAB = Mat()
        val maxAB = Mat()
        min(a, b, minAB)
        max(a, b, maxAB)

        val minMaxABC = Mat()
        min(maxAB, c, minMaxABC)

        val median = Mat()
        max(minAB, minMaxABC, median)

        minAB.release()
        maxAB.release()
        minMaxABC.release()
        return median
    }

    /**
     * Saves a captured Mat image to a file for debugging purposes.
     *
     * During normal gameplay this writes to `debug/last_camera_capture.jpg`. Tests may pass an
     * isolated directory so they do not depend on, delete, or lock the live gameplay debug folder.
     *
     * @param mat image data to write.
     * @param debugDirectory directory that receives the debug image.
     */
    fun saveDebugImage(mat: Mat, debugDirectory: Path = DEFAULT_DEBUG_DIRECTORY) {
        try {
            val filePath = debugDirectory.resolve(DEBUG_IMAGE_FILE_NAME)

            /** Ensure the debug directory exists */
            Files.createDirectories(debugDirectory)

            /** Save the image (overwrites existing file) */
            imwrite(filePath.toString(), mat)
        } catch (e: Exception) {
            println("Error saving debug image: ${e.message}")
        }
    }

    /**
     * Stops the capture and releases all native webcam resources.
     */
    suspend fun shutdown() = initMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                capture?.release()
                println("Camera shutdown successfully.")
            } catch (e: Exception) {
                println("Error shutting down camera: ${e.message}")
            } finally {
                capture = null
                isInitialized = false
            }
        }
    }
}
