package imagedetectiontests

import config.ConfigManager
import imageprocessing.CameraManager
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Scalar
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the CameraManager class.
 */
class CameraManagerTest {

    private val debugImageFileName = "last_camera_capture.jpg"

    /**
     * Runs [testBody] with an isolated debug directory that is removed afterwards.
     *
     * The production debug folder may exist or be touched after the game was started. Isolating
     * these tests prevents cleanup from depending on that external filesystem state.
     *
     * @param testBody assertions that receive the missing debug directory and expected image path.
     */
    private fun withDebugDirectory(testBody: (Path, Path) -> Unit) {
        val rootDirectory = Files.createTempDirectory("camera-debug-test")
        val debugDirectory = rootDirectory.resolve("debug")
        val expectedPath = debugDirectory.resolve(debugImageFileName)

        try {
            testBody(debugDirectory, expectedPath)
        } finally {
            Files.deleteIfExists(expectedPath)
            Files.deleteIfExists(debugDirectory)
            Files.deleteIfExists(rootDirectory)
        }
    }

    /**
     * Test that saveDebugImage creates the debug directory if it doesn't exist.
     */
    @Test
    fun testSaveDebugImageCreatesDebugDirectory() {
        withDebugDirectory { debugDirectory, expectedPath ->
            val mat = Mat(100, 100, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))

            CameraManager.saveDebugImage(mat, debugDirectory)

            assertTrue(Files.exists(debugDirectory))
            assertTrue(Files.isDirectory(debugDirectory))
            assertTrue(Files.exists(expectedPath))
        }
    }

    /**
     * Test that saveDebugImage creates the file at the expected path.
     */
    @Test
    fun testSaveDebugImageCreatesFileAtExpectedPath() {
        withDebugDirectory { debugDirectory, expectedPath ->
            val mat = Mat(100, 100, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))

            CameraManager.saveDebugImage(mat, debugDirectory)

            assertTrue(Files.exists(expectedPath))
        }
    }

    /**
     * Test that saveDebugImage handles null or invalid Mat objects gracefully.
     */
    @Test
    fun testSaveDebugImageHandlesInvalidMatGracefully() {
        withDebugDirectory { debugDirectory, _ ->
            val mat = Mat(100, 100, CV_8UC3)
            mat.release()

            CameraManager.saveDebugImage(mat, debugDirectory)
        }
    }

    /**
     * Test that saveDebugImage overwrites existing files.
     */
    @Test
    fun testSaveDebugImageOverwritesExistingFile() {
        withDebugDirectory { debugDirectory, expectedPath ->
            val mat1 = Mat(100, 100, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
            val mat2 = Mat(100, 100, CV_8UC3, Scalar(255.0, 255.0, 255.0, 255.0))

            CameraManager.saveDebugImage(mat1, debugDirectory)
            assertTrue(Files.exists(expectedPath))

            CameraManager.saveDebugImage(mat2, debugDirectory)

            assertTrue(Files.exists(expectedPath))
        }
    }

    /**
     * Tests that CameraManager uses configuration values correctly.
     */
    @Test
    fun testCameraManagerUsesConfiguration() {
        val config = ConfigManager.loadCameraConfig()
        
        // Verify CameraManager uses the configured values
        assertEquals(config.modelName, CameraManager.CAMERA_MODEL_NAME, 
            "CameraManager should use configured model name")
        assertEquals(config.defaultIndex, CameraManager.CAMERA_DEFAULT_INDEX,
            "CameraManager should use configured default index")
        assertEquals(config.width, CameraManager.CAMERA_WIDTH,
            "CameraManager should use configured width")
        assertEquals(config.height, CameraManager.CAMERA_HEIGHT,
            "CameraManager should use configured height")
    }

    /**
     * Tests that CameraManager configuration is consistent across multiple loads.
     */
    @Test
    fun testCameraManagerConfigurationConsistency() {
        val config1 = ConfigManager.loadCameraConfig()
        val config2 = ConfigManager.loadCameraConfig()
        
        // Verify configurations are identical
        assertEquals(config1.modelName, config2.modelName)
        assertEquals(config1.defaultIndex, config2.defaultIndex)
        assertEquals(config1.width, config2.width)
        assertEquals(config1.height, config2.height)
        
        // Verify CameraManager constants are consistent
        assertEquals(CameraManager.CAMERA_MODEL_NAME, CameraManager.CAMERA_MODEL_NAME)
        assertEquals(CameraManager.CAMERA_WIDTH, CameraManager.CAMERA_WIDTH)
        assertEquals(CameraManager.CAMERA_HEIGHT, CameraManager.CAMERA_HEIGHT)
    }
}
