package config

import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.util.Properties

/**
 * Manages loading configuration from properties files.
 * Provides fallback to default values if configuration is missing or invalid.
 */
object ConfigManager {
    /** Path to the main configuration file */
    private const val CONFIG_FILE = "config/app-config.properties"

    /** Default configuration values (fallback if config file is missing) */
    /** Camera settings */
    private const val DEFAULT_CAMERA_MODEL_NAME = "1080P FHD Camera"
    private const val DEFAULT_CAMERA_INDEX = 0
    private const val DEFAULT_CAMERA_WIDTH = 1920
    private const val DEFAULT_CAMERA_HEIGHT = 1080

    /** Manual camera image controls. null means "leave the camera on automatic". */
    private val DEFAULT_CAMERA_WB_TEMPERATURE: Int? = 4500
    private val DEFAULT_CAMERA_FOCUS: Int? = null
    private val DEFAULT_CAMERA_EXPOSURE: Int? = null
    private const val DEFAULT_CAMERA_MEDIAN_FRAME_COUNT = 3

    /** Image processing settings */
    private const val DEFAULT_PRE_CROP_X = 90
    private const val DEFAULT_PRE_CROP_Y = 23
    private const val DEFAULT_PRE_CROP_WIDTH = 1582
    private const val DEFAULT_PRE_CROP_HEIGHT = 1004
    private const val DEFAULT_KERNEL_RED_WIDTH = 60
    private const val DEFAULT_KERNEL_RED_HEIGHT = 60
    private const val DEFAULT_POLYLINE_APPROX_EPSILON = 3.0
    private const val DEFAULT_POLYLINE_MIN_ARC_LENGTH = 100.0
    private const val DEFAULT_TABLE_THRESHOLD = 180.0
    private const val DEFAULT_NODE_CLUSTER_RADIUS = 75
    private const val DEFAULT_NODE_SEARCH_RADIUS = 120
    private const val DEFAULT_NODE_SNAP_RADIUS = 25
    private const val DEFAULT_ARUCO_REVALIDATION_INTERVAL_MS = 15_000L

    /**
     * HSV street/bridge color mask thresholds (defaults match config/app-config.properties).
     * Red: the bluish beamer light shifts the physical red lane marking toward magenta (measured
     * dash hue 157-172 on the rectified captures), and the projected street overlay washes dash
     * saturation down to ~30-45 - hence the wide hue band and the low saturation minimum. See the
     * tuning notes in config/app-config.properties before changing these.
     */
    private const val DEFAULT_HSV_RED_HUE_LOW_MAX = 10
    private const val DEFAULT_HSV_RED_HUE_HIGH_MIN = 146
    private const val DEFAULT_HSV_RED_SATURATION_MIN = 30
    private const val DEFAULT_HSV_RED_VALUE_MIN = 40
    private const val DEFAULT_HSV_BLUE_HUE_MIN = 95
    private const val DEFAULT_HSV_BLUE_HUE_MAX = 135
    private const val DEFAULT_HSV_BLUE_SATURATION_MIN = 100
    private const val DEFAULT_HSV_BLUE_VALUE_MIN = 80
    private const val DEFAULT_HSV_GREEN_HUE_MIN = 35
    private const val DEFAULT_HSV_GREEN_HUE_MAX = 90
    private const val DEFAULT_HSV_GREEN_SATURATION_MIN = 100
    private const val DEFAULT_HSV_GREEN_VALUE_MIN = 80

    /** Camera auto-calibration settings */
    private const val DEFAULT_AUTO_CALIBRATION_ENABLED = true
    private const val DEFAULT_AUTO_CALIBRATION_TARGET_BRIGHTNESS = 170.0
    private const val DEFAULT_AUTO_CALIBRATION_EXPOSURE_MIN = -9
    private const val DEFAULT_AUTO_CALIBRATION_EXPOSURE_MAX = -3
    private const val DEFAULT_AUTO_CALIBRATION_EXPOSURE_STEP = 1
    private const val DEFAULT_AUTO_CALIBRATION_WHITE_BALANCE_MIN = 3000
    private const val DEFAULT_AUTO_CALIBRATION_WHITE_BALANCE_MAX = 6000
    private const val DEFAULT_AUTO_CALIBRATION_WHITE_BALANCE_STEP = 500
    private const val DEFAULT_AUTO_CALIBRATION_SETTLE_DELAY_MS = 300L
    private const val DEFAULT_AUTO_CALIBRATION_MAX_DURATION_MS = 45_000L
    private const val DEFAULT_PROJECT_ARUCO_MARKERS = true

    /** Simulation settings */
    private const val DEFAULT_SIMULATION_SPEED = 1.0
    private const val DEFAULT_VEHICLES_TO_SPAWN = 7
    private const val DEFAULT_CHALLENGE_NODE_LIMIT = 4

    /** Network settings */
    private const val DEFAULT_NETWORK_BASE_URL = "https://traficsim.se.uni-kassel.de"
    private const val DEFAULT_NETWORK_HOST = "traficsim.se.uni-kassel.de"
    private const val DEFAULT_NETWORK_PORT = 443
    private const val DEFAULT_NETWORK_ACTIVE = false

    /** Team secret sent as `deleteToken` when creating a world and as `X-World-Delete-Token` when deleting it. */
    private const val DEFAULT_WORLD_DELETE_TOKEN = "team-d-c21f7881-a385-49c3-99a6-8564a0575e34"

    /**
     * Loads camera configuration from file or uses defaults.
     *
     * @property modelName The name of the camera model.
     * @property defaultIndex The default index of the camera.
     * @property width The width of the camera resolution.
     * @property height The height of the camera resolution.
     * @property whiteBalanceTemperature Fixed white-balance color temperature in Kelvin, or null to keep auto white balance.
     * @property focus Fixed focus value, or null to keep autofocus.
     * @property exposure Fixed exposure value (camera/backend specific), or null to keep auto-exposure.
     * @property medianFrameCount Number of frames combined per photo via pixel median (>= 3 enables the median, otherwise a single frame is used).
     */
    data class CameraConfig(
        val modelName: String,
        val defaultIndex: Int,
        val width: Int,
        val height: Int,
        val whiteBalanceTemperature: Int?,
        val focus: Int?,
        val exposure: Int?,
        val medianFrameCount: Int
    )

    /**
     * Loads camera auto-calibration configuration from file or uses defaults.
     *
     * Placeholder default ranges/target - these are camera/lighting specific and need to be tuned
     * against the real table setup; [enabled] is an easy kill switch if calibration misbehaves.
     *
     * @property enabled Whether automatic startup calibration runs at all.
     * @property targetBrightness Target mean brightness (0-255) of the sampled calibration patch.
     * @property exposureMin Smallest exposure value tried during the sweep.
     * @property exposureMax Largest exposure value tried during the sweep.
     * @property exposureStep Step between tried exposure values.
     * @property whiteBalanceMin Smallest white-balance color temperature (Kelvin) tried during the sweep.
     * @property whiteBalanceMax Largest white-balance color temperature (Kelvin) tried during the sweep.
     * @property whiteBalanceStep Step between tried white-balance color temperatures.
     * @property settleDelayMs Delay after applying a candidate setting before the next capture, so the driver can apply it.
     * @property maxDurationMs Hard ceiling on the whole calibration sweep's total run time. If
     * exceeded, calibration is abandoned and the camera is left on whatever settings it already had -
     * a safety net independent of how long each individual capture actually takes on the real camera.
     * @property projectArucoMarkers Whether [scenes.ArucoDetectionScene] projects the four corner
     * markers (inverted, bright-on-black) after the white-balance correction and locks in a
     * permanent transform ([imageprocessing.PermanentArucoTransform]). Set to false to skip that
     * scene and compare against the older behavior: physical stickers only, re-detected periodically
     * by [imageprocessing.ArucoRectificationCache] for the whole session.
     */
    data class CameraAutoCalibrationConfig(
        val enabled: Boolean,
        val targetBrightness: Double,
        val exposureMin: Int,
        val exposureMax: Int,
        val exposureStep: Int,
        val whiteBalanceMin: Int,
        val whiteBalanceMax: Int,
        val whiteBalanceStep: Int,
        val settleDelayMs: Long,
        val maxDurationMs: Long,
        val projectArucoMarkers: Boolean
    )

    /**
     * Loads image processing configuration from file or uses defaults.
     *
     * @property preCropX The x-coordinate of the pre-crop region.
     * @property preCropY The y-coordinate of the pre-crop region.
     * @property preCropWidth The width of the pre-crop region.
     * @property preCropHeight The height of the pre-crop region.
     * @property kernelRedWidth The width of the kernel used for red street detection.
     * @property kernelRedHeight The height of the kernel used for red street detection.
     * @property polylineApproxEpsilon The epsilon value used for polyline approximation.
     * @property polylineMinArcLength The minimum arc length for a polyline to be considered valid.
     * @property tableThreshold The threshold value used for binarizing the grayscale image to detect the table.
     * @property nodeClusterRadius The radius used for clustering nearby connection nodes.
     * @property nodeSearchRadius The radius used to search for the nearest skeleton pixel when snapping a known level node.
     * @property nodeSnapRadius The distance at which a walked skeleton pixel is considered to have reached a node.
     * @property arucoRevalidationIntervalMs How long a detected ArUco rectification transform is
     * reused before the next capture re-detects the corner markers, instead of re-detecting on every
     * single capture (camera and markers do not move during normal use).
     * @property hsv The HSV street/bridge color mask thresholds used by the color segmentation.
     */
    data class ImageProcessingConfig(
        val preCropX: Int,
        val preCropY: Int,
        val preCropWidth: Int,
        val preCropHeight: Int,
        val kernelRedWidth: Int,
        val kernelRedHeight: Int,
        val polylineApproxEpsilon: Double,
        val polylineMinArcLength: Double,
        val tableThreshold: Double,
        val nodeClusterRadius: Int,
        val nodeSearchRadius: Int,
        val nodeSnapRadius: Int,
        val arucoRevalidationIntervalMs: Long,
        val hsv: HsvStreetColorConfig
    )

    /**
     * HSV mask thresholds for the three street/bridge colors used by [imageprocessing.StreetDetection].
     *
     * Exposed via configuration so the color segmentation can be tuned against the real table's
     * lighting without a rebuild - room light lowering a street's saturation below the minimum is a
     * typical cause of partially disappearing street masks. Red wraps around the 0-180 OpenCV hue
     * scale, so it is masked as two ranges: `[0, redHueLowMax]` and `[redHueHighMin, 180]`, both
     * sharing [redSaturationMin]/[redValueMin].
     *
     * @property redHueLowMax Upper hue bound of red's low range (lower bound is 0).
     * @property redHueHighMin Lower hue bound of red's high range (upper bound is 180).
     * @property redSaturationMin Minimum saturation for red street pixels.
     * @property redValueMin Minimum brightness for red street pixels.
     * @property blueHueMin Lower hue bound for blue (level 1) bridge pixels.
     * @property blueHueMax Upper hue bound for blue bridge pixels.
     * @property blueSaturationMin Minimum saturation for blue bridge pixels.
     * @property blueValueMin Minimum brightness for blue bridge pixels.
     * @property greenHueMin Lower hue bound for green (level 2) bridge pixels.
     * @property greenHueMax Upper hue bound for green bridge pixels.
     * @property greenSaturationMin Minimum saturation for green bridge pixels.
     * @property greenValueMin Minimum brightness for green bridge pixels.
     */
    data class HsvStreetColorConfig(
        val redHueLowMax: Int,
        val redHueHighMin: Int,
        val redSaturationMin: Int,
        val redValueMin: Int,
        val blueHueMin: Int,
        val blueHueMax: Int,
        val blueSaturationMin: Int,
        val blueValueMin: Int,
        val greenHueMin: Int,
        val greenHueMax: Int,
        val greenSaturationMin: Int,
        val greenValueMin: Int
    )

    /**
     * Loads simulation configuration from file or uses defaults.
     *
     * @property speed The speed of the simulation.
     * @property vehiclesToSpawn The number of vehicles to spawn.
     * @property challengeNodeLimit The maximum number of nodes allowed in a challenge.
     */
    data class SimulationConfig(
        val speed: Double,
        val vehiclesToSpawn: Int,
        val challengeNodeLimit: Int
    )

    /**
     * Loads network configuration from file or uses defaults.
     *
     * @property baseUrl The HTTPS base URL of the coupling server.
     * @property host The host name or IP address of the coupling server.
     * @property port The server port used for network communication.
     * @property active Whether table coupling should be enabled.
     * @property worldDeleteToken Team secret sent as `deleteToken` when creating a world on the
     *   coupling server and as `X-World-Delete-Token` when deleting one, so only this team can
     *   delete the worlds it created.
     */
    data class NetworkConfig(
        val host: String,
        val port: Int,
        val active: Boolean,
        val baseUrl: String = buildDefaultNetworkBaseUrl(host, port),
        val worldDeleteToken: String = DEFAULT_WORLD_DELETE_TOKEN
    ) {
        /** REST endpoint base URI without a trailing slash. */
        val restBaseUri: URI get() = URI.create(baseUrl)

        /** WebSocket endpoint used for live field coupling. */
        val webSocketUri: URI
            get() {
                val restUri = restBaseUri
                val wsScheme = when (restUri.scheme?.lowercase()) {
                    "http" -> "ws"
                    else -> "wss"
                }
                return URI(
                    wsScheme,
                    restUri.userInfo,
                    restUri.host,
                    restUri.port,
                    "/ws/fields",
                    null,
                    null
                )
            }
    }

    /**
     * Main configuration container holding all configuration sections.
     *
     * @property camera The camera configuration.
     * @property cameraAutoCalibration The camera auto-calibration configuration.
     * @property imageProcessing The image processing configuration.
     * @property simulation The simulation configuration.
     * @property network The table-coupling network configuration.
     */
    data class Config(
        val camera: CameraConfig,
        val cameraAutoCalibration: CameraAutoCalibrationConfig,
        val imageProcessing: ImageProcessingConfig,
        val simulation: SimulationConfig,
        val network: NetworkConfig
    )

    /**
     * Loads all configuration from the properties file.
     * Returns configuration objects with values from the file or defaults if missing/invalid.
     *
     * @return A Config object containing the loaded configuration.
     */
    fun loadConfig(): Config {
        val properties = Properties()
        val configFile = File(CONFIG_FILE)

        /** Try to load from file if it exists */
        if (configFile.exists()) {
            try {
                FileInputStream(configFile).use { input ->
                    properties.load(input)
                }
            } catch (e: Exception) {
                println("Error loading configuration: ${e.message}")
                println("Using default configuration")
            }
        }

        return Config(
            camera = loadCameraConfig(properties),
            cameraAutoCalibration = loadCameraAutoCalibrationConfig(properties),
            imageProcessing = loadImageProcessingConfig(properties),
            simulation = loadSimulationConfig(properties),
            network = loadNetworkConfig(properties)
        )
    }

    /**
     * Loads camera configuration from properties.
     *
     * @param properties The properties object to load configuration from.
     * @return A CameraConfig object containing the loaded configuration.
     */
    private fun loadCameraConfig(properties: Properties): CameraConfig {
        val modelName = properties.getProperty("camera.model.name", DEFAULT_CAMERA_MODEL_NAME)
        val defaultIndex = properties.getProperty("camera.default.index")?.toIntOrNull() ?: DEFAULT_CAMERA_INDEX
        val width = properties.getProperty("camera.width")?.toIntOrNull() ?: DEFAULT_CAMERA_WIDTH
        val height = properties.getProperty("camera.height")?.toIntOrNull() ?: DEFAULT_CAMERA_HEIGHT
        val whiteBalanceTemperature = parseOptionalInt(properties, "camera.white_balance_temperature", DEFAULT_CAMERA_WB_TEMPERATURE)
        val focus = parseOptionalInt(properties, "camera.focus", DEFAULT_CAMERA_FOCUS)
        val exposure = parseOptionalInt(properties, "camera.exposure", DEFAULT_CAMERA_EXPOSURE)
        val medianFrameCount = properties.getProperty("camera.median_frame_count")?.toIntOrNull() ?: DEFAULT_CAMERA_MEDIAN_FRAME_COUNT

        return CameraConfig(modelName, defaultIndex, width, height, whiteBalanceTemperature, focus, exposure, medianFrameCount)
    }

    /**
     * Parses an optional integer property.
     *
     * An absent key falls back to [default]; an explicit blank value or "auto" means
     * "leave the camera on automatic" and returns null.
     *
     * @param properties The properties object to read from.
     * @param key The property key.
     * @param default Value used when the key is absent.
     * @return The parsed value, null for automatic, or [default] when absent/invalid.
     */
    private fun parseOptionalInt(properties: Properties, key: String, default: Int?): Int? {
        val raw = properties.getProperty(key) ?: return default
        if (raw.isBlank() || raw.equals("auto", ignoreCase = true)) return null
        return raw.toIntOrNull() ?: default
    }

    /**
     * Loads camera auto-calibration configuration from properties.
     *
     * @param properties The properties object to load configuration from.
     * @return A CameraAutoCalibrationConfig object containing the loaded configuration.
     */
    private fun loadCameraAutoCalibrationConfig(properties: Properties): CameraAutoCalibrationConfig {
        val enabled = properties.getProperty("camera.auto_calibration.enabled")?.toBooleanStrictOrNull()
            ?: DEFAULT_AUTO_CALIBRATION_ENABLED
        val targetBrightness = properties.getProperty("camera.auto_calibration.target_brightness")?.toDoubleOrNull()
            ?: DEFAULT_AUTO_CALIBRATION_TARGET_BRIGHTNESS
        val exposureMin = properties.getProperty("camera.auto_calibration.exposure_min")?.toIntOrNull()
            ?: DEFAULT_AUTO_CALIBRATION_EXPOSURE_MIN
        val exposureMax = properties.getProperty("camera.auto_calibration.exposure_max")?.toIntOrNull()
            ?: DEFAULT_AUTO_CALIBRATION_EXPOSURE_MAX
        val exposureStep = properties.getProperty("camera.auto_calibration.exposure_step")?.toIntOrNull()
            ?: DEFAULT_AUTO_CALIBRATION_EXPOSURE_STEP
        val whiteBalanceMin = properties.getProperty("camera.auto_calibration.white_balance_min")?.toIntOrNull()
            ?: DEFAULT_AUTO_CALIBRATION_WHITE_BALANCE_MIN
        val whiteBalanceMax = properties.getProperty("camera.auto_calibration.white_balance_max")?.toIntOrNull()
            ?: DEFAULT_AUTO_CALIBRATION_WHITE_BALANCE_MAX
        val whiteBalanceStep = properties.getProperty("camera.auto_calibration.white_balance_step")?.toIntOrNull()
            ?: DEFAULT_AUTO_CALIBRATION_WHITE_BALANCE_STEP
        val settleDelayMs = properties.getProperty("camera.auto_calibration.settle_delay_ms")?.toLongOrNull()
            ?: DEFAULT_AUTO_CALIBRATION_SETTLE_DELAY_MS
        val maxDurationMs = properties.getProperty("camera.auto_calibration.max_duration_ms")?.toLongOrNull()
            ?: DEFAULT_AUTO_CALIBRATION_MAX_DURATION_MS
        val projectArucoMarkers = properties.getProperty("camera.auto_calibration.project_aruco_markers")?.toBooleanStrictOrNull()
            ?: DEFAULT_PROJECT_ARUCO_MARKERS

        return CameraAutoCalibrationConfig(
            enabled, targetBrightness, exposureMin, exposureMax, exposureStep,
            whiteBalanceMin, whiteBalanceMax, whiteBalanceStep, settleDelayMs, maxDurationMs,
            projectArucoMarkers
        )
    }

    /**
     * Loads image processing configuration from properties.
     *
     * @param properties The properties object to load configuration from.
     * @return An ImageProcessingConfig object containing the loaded configuration.
     */
    private fun loadImageProcessingConfig(properties: Properties): ImageProcessingConfig {
        val preCropX = properties.getProperty("imageprocessing.pre_crop.x")?.toIntOrNull() ?: DEFAULT_PRE_CROP_X
        val preCropY = properties.getProperty("imageprocessing.pre_crop.y")?.toIntOrNull() ?: DEFAULT_PRE_CROP_Y
        val preCropWidth = properties.getProperty("imageprocessing.pre_crop.width")?.toIntOrNull() ?: DEFAULT_PRE_CROP_WIDTH
        val preCropHeight = properties.getProperty("imageprocessing.pre_crop.height")?.toIntOrNull() ?: DEFAULT_PRE_CROP_HEIGHT

        val kernelRedWidth = properties.getProperty("imageprocessing.kernel.red.width")?.toIntOrNull() ?: DEFAULT_KERNEL_RED_WIDTH
        val kernelRedHeight = properties.getProperty("imageprocessing.kernel.red.height")?.toIntOrNull() ?: DEFAULT_KERNEL_RED_HEIGHT
        val polylineApproxEpsilon = properties.getProperty("imageprocessing.polyline.approx_epsilon")?.toDoubleOrNull() ?: DEFAULT_POLYLINE_APPROX_EPSILON
        val polylineMinArcLength = properties.getProperty("imageprocessing.polyline.min_arc_length")?.toDoubleOrNull() ?: DEFAULT_POLYLINE_MIN_ARC_LENGTH
        val tableThreshold = properties.getProperty("imageprocessing.table.threshold")?.toDoubleOrNull() ?: DEFAULT_TABLE_THRESHOLD
        val nodeClusterRadius = properties.getProperty("imageprocessing.node.cluster_radius")?.toIntOrNull() ?: DEFAULT_NODE_CLUSTER_RADIUS
        val nodeSearchRadius = properties.getProperty("imageprocessing.node.search_radius")?.toIntOrNull() ?: DEFAULT_NODE_SEARCH_RADIUS
        val nodeSnapRadius = properties.getProperty("imageprocessing.node.snap_radius")?.toIntOrNull() ?: DEFAULT_NODE_SNAP_RADIUS
        val arucoRevalidationIntervalMs = properties.getProperty("imageprocessing.aruco.revalidation_interval_ms")?.toLongOrNull()
            ?: DEFAULT_ARUCO_REVALIDATION_INTERVAL_MS

        return ImageProcessingConfig(preCropX, preCropY, preCropWidth, preCropHeight, kernelRedWidth, kernelRedHeight,
            polylineApproxEpsilon, polylineMinArcLength, tableThreshold, nodeClusterRadius, nodeSearchRadius, nodeSnapRadius,
            arucoRevalidationIntervalMs, loadHsvStreetColorConfig(properties))
    }

    /**
     * Loads the HSV street/bridge color mask thresholds from properties.
     *
     * @param properties The properties object to load configuration from.
     * @return A HsvStreetColorConfig object containing the loaded configuration.
     */
    private fun loadHsvStreetColorConfig(properties: Properties): HsvStreetColorConfig {
        /** Reads the integer property [key], falling back to [default] when absent or invalid. */
        fun intProperty(key: String, default: Int): Int =
            properties.getProperty(key)?.toIntOrNull() ?: default

        return HsvStreetColorConfig(
            redHueLowMax = intProperty("imageprocessing.hsv.red.hue_low_max", DEFAULT_HSV_RED_HUE_LOW_MAX),
            redHueHighMin = intProperty("imageprocessing.hsv.red.hue_high_min", DEFAULT_HSV_RED_HUE_HIGH_MIN),
            redSaturationMin = intProperty("imageprocessing.hsv.red.saturation_min", DEFAULT_HSV_RED_SATURATION_MIN),
            redValueMin = intProperty("imageprocessing.hsv.red.value_min", DEFAULT_HSV_RED_VALUE_MIN),
            blueHueMin = intProperty("imageprocessing.hsv.blue.hue_min", DEFAULT_HSV_BLUE_HUE_MIN),
            blueHueMax = intProperty("imageprocessing.hsv.blue.hue_max", DEFAULT_HSV_BLUE_HUE_MAX),
            blueSaturationMin = intProperty("imageprocessing.hsv.blue.saturation_min", DEFAULT_HSV_BLUE_SATURATION_MIN),
            blueValueMin = intProperty("imageprocessing.hsv.blue.value_min", DEFAULT_HSV_BLUE_VALUE_MIN),
            greenHueMin = intProperty("imageprocessing.hsv.green.hue_min", DEFAULT_HSV_GREEN_HUE_MIN),
            greenHueMax = intProperty("imageprocessing.hsv.green.hue_max", DEFAULT_HSV_GREEN_HUE_MAX),
            greenSaturationMin = intProperty("imageprocessing.hsv.green.saturation_min", DEFAULT_HSV_GREEN_SATURATION_MIN),
            greenValueMin = intProperty("imageprocessing.hsv.green.value_min", DEFAULT_HSV_GREEN_VALUE_MIN)
        )
    }

    /**
     * Loads camera configuration only (for backward compatibility).
     *
     * @return A CameraConfig object containing the loaded configuration.
     */
    fun loadCameraConfig(): CameraConfig {
        return loadConfig().camera
    }

    /**
     * Loads camera auto-calibration configuration only.
     *
     * @return A CameraAutoCalibrationConfig object containing the loaded configuration.
     */
    fun loadCameraAutoCalibrationConfig(): CameraAutoCalibrationConfig {
        return loadConfig().cameraAutoCalibration
    }

    /**
     * Loads image processing configuration only.
     *
     * @return An ImageProcessingConfig object containing the loaded configuration.
     */
    fun loadImageProcessingConfig(): ImageProcessingConfig {
        return loadConfig().imageProcessing
    }

    /**
     * Loads simulation configuration from properties.
     */
    private fun loadSimulationConfig(properties: Properties): SimulationConfig {
        val speed = properties.getProperty("simulation.speed")?.toDoubleOrNull() ?: DEFAULT_SIMULATION_SPEED
        val vehiclesToSpawn = properties.getProperty("simulation.vehicles_to_spawn")?.toIntOrNull() ?: DEFAULT_VEHICLES_TO_SPAWN
        val challengeNodeLimit = properties.getProperty("simulation.challenge.node_limit")?.toIntOrNull() ?: DEFAULT_CHALLENGE_NODE_LIMIT

        return SimulationConfig(speed, vehiclesToSpawn, challengeNodeLimit)
    }

    /**
     * Loads network configuration from properties.
     */
    private fun loadNetworkConfig(properties: Properties): NetworkConfig {
        val configuredBaseUrl = normalizeNetworkBaseUrl(properties.getProperty("network.base_url"))
        val host = properties.getProperty("network.host")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: configuredBaseUrl?.let { URI.create(it).host }
            ?: DEFAULT_NETWORK_HOST
        val port = properties.getProperty("network.port")?.toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?: configuredBaseUrl?.let { effectivePort(URI.create(it)) }
            ?: DEFAULT_NETWORK_PORT
        val active = when (properties.getProperty("network.active")?.trim()?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> DEFAULT_NETWORK_ACTIVE
        }
        val baseUrl = configuredBaseUrl ?: buildDefaultNetworkBaseUrl(host, port)
        val worldDeleteToken = properties.getProperty("network.world_delete_token")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_WORLD_DELETE_TOKEN

        return NetworkConfig(host, port, active, baseUrl, worldDeleteToken)
    }

    /**
     * Loads simulation configuration only.
     */
    fun loadSimulationConfig(): SimulationConfig {
        return loadConfig().simulation
    }

    /**
     * Loads network configuration only.
     */
    fun loadNetworkConfig(): NetworkConfig {
        return loadConfig().network
    }

    private fun normalizeNetworkBaseUrl(rawValue: String?): String? {
        val trimmed = rawValue?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val uri = URI.create(trimmed)
            if ((uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()) {
                trimmed
            } else {
                null
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun buildDefaultNetworkBaseUrl(host: String, port: Int): String {
        if (host == DEFAULT_NETWORK_HOST && port == DEFAULT_NETWORK_PORT) {
            return DEFAULT_NETWORK_BASE_URL
        }
        val scheme = if (port == 80) "http" else "https"
        val portPart = if ((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) "" else ":$port"
        return "$scheme://$host$portPart"
    }

    private fun effectivePort(uri: URI): Int {
        if (uri.port != -1) return uri.port
        return when (uri.scheme?.lowercase()) {
            "http" -> 80
            else -> 443
        }
    }
}
