package config

import imageprocessing.CameraManager
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/**
 * Comprehensive test suite for ConfigManager.
 * Tests all aspects of configuration loading and error handling.
 */
class ConfigManagerTest {

    private val backupConfigFile = "config/app-config.properties.backup"
    private val testConfigFile = "test-config.properties"

    /**
     * Setup method called before each test.
     * Backs up the original configuration file to ensure test isolation.
     */
    @BeforeTest
    fun setup() {
        /* Backup the original config file */
        val originalConfig = File("config/app-config.properties")
        if (originalConfig.exists()) {
            Files.copy(originalConfig.toPath(), File(backupConfigFile).toPath())
        }
    }

    /** 
     * Cleanup method called after each test.
     * Restores the original configuration file and cleans up test files.
     */
    @AfterTest
    fun cleanup() {
        /* Restore the original config file */
        val backupFile = File(backupConfigFile)
        if (backupFile.exists()) {
            Files.copy(backupFile.toPath(), File("config/app-config.properties").toPath(), 
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            if (!backupFile.delete()) {
                System.err.println("Failed to delete backup config file: $backupConfigFile")
            }
        }
        
        /* Clean up test config file if it exists */
        if (!File(testConfigFile).delete()) {
            System.err.println("Failed to delete test config file: $testConfigFile")
        }
    }

    /**
     * Tests loading configuration from a valid configuration file.
     */
    @Test
    fun testLoadConfigWithValidConfiguration() {
        /* Create a test config file with valid values */
        val testConfigContent = """
            camera.model.name=Test Camera Model
            camera.default.index=2
            camera.width=1280
            camera.height=720
            imageprocessing.pre_crop.x=100
            imageprocessing.pre_crop.y=50
            imageprocessing.pre_crop.width=1024
            imageprocessing.pre_crop.height=768
        """.trimIndent()
        
        File(testConfigFile).writeText(testConfigContent)
        
        /* Temporarily use our test config */
        setupTestConfig()
        
        try {
            val config = ConfigManager.loadConfig()
            
            /* Verify camera configuration */
            assertEquals("Test Camera Model", config.camera.modelName)
            assertEquals(2, config.camera.defaultIndex)
            assertEquals(1280, config.camera.width)
            assertEquals(720, config.camera.height)
            
            /* Verify image processing configuration */
            assertEquals(100, config.imageProcessing.preCropX)
            assertEquals(50, config.imageProcessing.preCropY)
            assertEquals(1024, config.imageProcessing.preCropWidth)
            assertEquals(768, config.imageProcessing.preCropHeight)
            
        } finally {
            restoreOriginalConfig()
        }
    }

    /**
     * Tests that default values are used when configuration file is missing.
     */
    @Test
    fun testLoadConfigWithMissingFileUsesDefaults() {
        /* Temporarily move the config file to simulate missing file */
        Files.move(
            File("config/app-config.properties").toPath(),
            File("config/app-config.properties.temp").toPath()
        )
        
        try {
            val config = ConfigManager.loadConfig()
            
            /* Verify defaults are used for camera configuration */
            assertEquals("1080P FHD Camera", config.camera.modelName)
            assertEquals(0, config.camera.defaultIndex)
            assertEquals(1920, config.camera.width)
            assertEquals(1080, config.camera.height)
            
            /* Verify defaults are used for image processing configuration */
            assertEquals(90, config.imageProcessing.preCropX)
            assertEquals(23, config.imageProcessing.preCropY)
            assertEquals(1582, config.imageProcessing.preCropWidth)
            assertEquals(1004, config.imageProcessing.preCropHeight)
            
        } finally {
            /* Restore config file */
            Files.move(
                File("config/app-config.properties.temp").toPath(),
                File("config/app-config.properties").toPath()
            )
        }
    }

    /**
     * Tests loading configuration with partial values (some missing).
     */
    @Test
    fun testLoadConfigWithPartialConfiguration() {
        /* Create a test config with only some values */
        val testConfigContent = """
            camera.model.name=Partial Config Camera
            imageprocessing.pre_crop.x=200
        """.trimIndent()
        
        File(testConfigFile).writeText(testConfigContent)
        
        /* Temporarily use our test config */
        setupTestConfig()
        
        try {
            val config = ConfigManager.loadConfig()
            
            /* Verify configured values are used */
            assertEquals("Partial Config Camera", config.camera.modelName)
            assertEquals(200, config.imageProcessing.preCropX)
            
            /* Verify defaults for missing values */
            assertEquals(0, config.camera.defaultIndex) // default
            assertEquals(1920, config.camera.width) // default
            assertEquals(23, config.imageProcessing.preCropY) // default
            
        } finally {
            restoreOriginalConfig()
        }
    }

    /**
     * Tests loading configuration with invalid values (should use defaults).
     */
    @Test
    fun testLoadConfigWithInvalidValuesUsesDefaults() {
        /* Create a test config with invalid values */
        val testConfigContent = """
            camera.model.name=Valid Camera Name
            camera.default.index=invalid
            camera.width=not_a_number
            camera.height=999999
            imageprocessing.pre_crop.x=abc
            imageprocessing.pre_crop.y=50
        """.trimIndent()
        
        File(testConfigFile).writeText(testConfigContent)
        
        /* Temporarily use our test config */
        setupTestConfig()
        
        try {
            val config = ConfigManager.loadConfig()
            
            /* Verify valid string value is used */
            assertEquals("Valid Camera Name", config.camera.modelName)
            
            /* Verify valid numeric value is used */
            assertEquals(50, config.imageProcessing.preCropY)
            
            /* Verify defaults for invalid values */
            assertEquals(0, config.camera.defaultIndex) // default because "invalid" is not a number
            assertEquals(1920, config.camera.width) // default because "not_a_number" is invalid
            assertEquals(90, config.imageProcessing.preCropX) // default because "abc" is invalid
            
            /* Verify valid large number is accepted */
            assertEquals(999999, config.camera.height)
            
        } finally {
            restoreOriginalConfig()
        }
    }

    /**
     * Tests loading configuration from a malformed properties file.
     */
    @Test
    fun testLoadConfigWithMalformedFile() {
        /* Create a malformed config file */
        val testConfigContent = """
            camera.model.name=Valid Camera
            this line is not valid properties format
            camera.width=1280
            =missing key
            imageprocessing.pre_crop.x another invalid line
        """.trimIndent()
        
        File(testConfigFile).writeText(testConfigContent)
        
        /* Temporarily use our test config */
        setupTestConfig()
        
        try {
            /* This should not throw an exception, but use defaults for invalid parts */
            val config = ConfigManager.loadConfig()
            
            /* Verify valid values are still loaded */
            assertEquals("Valid Camera", config.camera.modelName)
            assertEquals(1280, config.camera.width)
            
            /* Other values should use defaults */
            assertEquals(0, config.camera.defaultIndex)
            assertEquals(1080, config.camera.height)
            
        } finally {
            restoreOriginalConfig()
        }
    }

    /**
     * Tests backward compatibility methods.
     */
    @Test
    fun testBackwardCompatibilityMethods() {
        val cameraConfig = ConfigManager.loadCameraConfig()
        val imageProcessingConfig = ConfigManager.loadImageProcessingConfig()
        
        assertNotNull(cameraConfig)
        assertNotNull(imageProcessingConfig)
        
        /* Verify they match the full config */
        val fullConfig = ConfigManager.loadConfig()
        assertEquals(fullConfig.camera.modelName, cameraConfig.modelName)
        assertEquals(fullConfig.camera.defaultIndex, cameraConfig.defaultIndex)
        assertEquals(fullConfig.camera.width, cameraConfig.width)
        assertEquals(fullConfig.camera.height, cameraConfig.height)
        
        assertEquals(fullConfig.imageProcessing.preCropX, imageProcessingConfig.preCropX)
        assertEquals(fullConfig.imageProcessing.preCropY, imageProcessingConfig.preCropY)
        assertEquals(fullConfig.imageProcessing.preCropWidth, imageProcessingConfig.preCropWidth)
        assertEquals(fullConfig.imageProcessing.preCropHeight, imageProcessingConfig.preCropHeight)
    }

    /**
     * Tests configuration consistency across multiple loads.
     */
    @Test
    fun testConfigurationConsistency() {
        /* Load configuration multiple times */
        val config1 = ConfigManager.loadConfig()
        val config2 = ConfigManager.loadConfig()
        val config3 = ConfigManager.loadConfig()
        
        /* Verify all configurations are identical */
        assertEquals(config1.camera.modelName, config2.camera.modelName)
        assertEquals(config1.camera.defaultIndex, config2.camera.defaultIndex)
        assertEquals(config1.camera.width, config2.camera.width)
        assertEquals(config1.camera.height, config2.camera.height)
        
        assertEquals(config1.imageProcessing.preCropX, config2.imageProcessing.preCropX)
        assertEquals(config1.imageProcessing.preCropY, config2.imageProcessing.preCropY)
        assertEquals(config1.imageProcessing.preCropWidth, config2.imageProcessing.preCropWidth)
        assertEquals(config1.imageProcessing.preCropHeight, config2.imageProcessing.preCropHeight)
        
        /* Verify config3 matches as well */
        assertEquals(config1.camera.modelName, config3.camera.modelName)
        assertEquals(config1.imageProcessing.preCropX, config3.imageProcessing.preCropX)
    }

    /**
     * Tests loading simulation configuration.
     */
    @Test
    fun testLoadSimulationConfiguration() {
        /* Create a test config with simulation values */
        val testConfigContent = """
            simulation.speed=2.0
            simulation.vehicles_to_spawn=15
            simulation.challenge.node_limit=8
        """.trimIndent()
        
        File(testConfigFile).writeText(testConfigContent)
        
        /* Temporarily use our test config */
        setupTestConfig()
        
        try {
            val config = ConfigManager.loadConfig()
            
            /* Verify simulation configuration */
            assertEquals(2.0, config.simulation.speed)
            assertEquals(15, config.simulation.vehiclesToSpawn)
            assertEquals(8, config.simulation.challengeNodeLimit)
            
        } finally {
            /* Restore original configuration */
            restoreOriginalConfig()
        }
    }

    /**
     * Tests that configuration values are within reasonable bounds.
     */
    @Test
    fun testConfigurationValuesAreReasonable() {
        val config = ConfigManager.loadConfig()
        
        /* Camera configuration sanity checks */
        assertTrue(config.camera.modelName.isNotEmpty(), "Camera model name should not be empty")
        assertTrue(config.camera.width > 0, "Camera width should be positive")
        assertTrue(config.camera.height > 0, "Camera height should be positive")
        assertTrue(config.camera.defaultIndex >= 0, "Camera default index should be non-negative")
        
        /* Image processing configuration sanity checks */
        assertTrue(config.imageProcessing.preCropX >= 0, "Pre-crop X should be non-negative")
        assertTrue(config.imageProcessing.preCropY >= 0, "Pre-crop Y should be non-negative")
        assertTrue(config.imageProcessing.preCropWidth > 0, "Pre-crop width should be positive")
        assertTrue(config.imageProcessing.preCropHeight > 0, "Pre-crop height should be positive")
        
        /* Reasonable size limits */
        assertTrue(config.camera.width < 10000, "Camera width seems unreasonably large")
        assertTrue(config.camera.height < 10000, "Camera height seems unreasonably large")
        assertTrue(config.imageProcessing.preCropWidth < 5000, "Pre-crop width seems unreasonably large")
        assertTrue(config.imageProcessing.preCropHeight < 5000, "Pre-crop height seems unreasonably large")
    }

    /**
     * Tests integration with CameraManager.
     */
    @Test
    fun testIntegrationWithCameraManager() {
        val config = ConfigManager.loadCameraConfig()
        
        /* Verify CameraManager uses the configured values */
        assertEquals(config.modelName, CameraManager.CAMERA_MODEL_NAME)
        assertEquals(config.defaultIndex, CameraManager.CAMERA_DEFAULT_INDEX)
        assertEquals(config.width, CameraManager.CAMERA_WIDTH)
        assertEquals(config.height, CameraManager.CAMERA_HEIGHT)
    }



    /**
     * Tests that empty configuration file uses defaults.
     */
    @Test
    fun testEmptyConfigurationFileUsesDefaults() {
        /* Create an empty config file */
        File(testConfigFile).writeText("")
        
        /* Temporarily use our test config */
        setupTestConfig()
        
        try {
            val config = ConfigManager.loadConfig()
            
            /* All values should be defaults */
            assertEquals("1080P FHD Camera", config.camera.modelName)
            assertEquals(0, config.camera.defaultIndex)
            assertEquals(1920, config.camera.width)
            assertEquals(1080, config.camera.height)
            assertEquals(90, config.imageProcessing.preCropX)
            assertEquals(23, config.imageProcessing.preCropY)
            assertEquals(1582, config.imageProcessing.preCropWidth)
            assertEquals(1004, config.imageProcessing.preCropHeight)
            
        } finally {
            restoreOriginalConfig()
        }
    }

    /**
     * Tests that configuration with only whitespace uses defaults.
     */
    @Test
    fun testWhitespaceOnlyConfigurationUsesDefaults() {
        /* Create a config file with only whitespace */
        File(testConfigFile).writeText("   \n\n   \t\n   ")
        
        /* Temporarily use our test config */
        setupTestConfig()
        
        try {
            val config = ConfigManager.loadConfig()
            
            /* All values should be defaults */
            assertEquals("1080P FHD Camera", config.camera.modelName)
            assertEquals(0, config.camera.defaultIndex)
            assertEquals(90, config.imageProcessing.preCropX)
            
        } finally {
            restoreOriginalConfig()
        }
    }

    /**
     * Moves the original config to a temporary file and replaces it with the test config.
     */
    private fun setupTestConfig() {
        Files.move(
            File("config/app-config.properties").toPath(),
            File("config/app-config.properties.temp").toPath()
        )
        Files.move(
            File(testConfigFile).toPath(),
            File("config/app-config.properties").toPath()
        )
    }

    /**
     * Moves the temporary config file back to its original location and restores the original config.
     */
    private fun restoreOriginalConfig() {
        Files.move(
            File("config/app-config.properties").toPath(),
            File(testConfigFile).toPath()
        )
        Files.move(
            File("config/app-config.properties.temp").toPath(),
            File("config/app-config.properties").toPath()
        )
    }
}
