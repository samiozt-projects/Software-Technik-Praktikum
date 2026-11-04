package config

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the network configuration used for table coupling.
 */
class NetworkConfigManagerTest {
    private val configFile = File("config/app-config.properties")
    private val backupConfigFile = File("config/app-config.properties.network.backup")
    private val tempConfigFile = File("config/app-config.properties.network.temp")
    private val testConfigFile = File("network-test-config.properties")

    /**
     * Backs up the real application config before a test replaces it.
     */
    @BeforeTest
    fun setup() {
        if (configFile.exists()) {
            Files.copy(configFile.toPath(), backupConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Restores the real application config and removes temporary test files.
     */
    @AfterTest
    fun cleanup() {
        if (backupConfigFile.exists()) {
            Files.copy(backupConfigFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            deleteFile(backupConfigFile)
        }
        deleteFile(tempConfigFile)
        deleteFile(testConfigFile)
    }

    /**
     * Verifies that explicitly configured network values are loaded.
     */
    @Test
    fun loadNetworkConfigReadsConfiguredValues() {
        withTestConfig(
            """
            network.host=localhost
            network.port=8080
            network.active=true
            """
        ) {
            val config = ConfigManager.loadNetworkConfig()

            assertEquals("localhost", config.host)
            assertEquals(8080, config.port)
            assertEquals("https://localhost:8080", config.baseUrl)
            assertTrue(config.active)
        }
    }

    /**
     * Verifies that the REST base URL can be configured directly.
     */
    @Test
    fun loadNetworkConfigReadsBaseUrl() {
        withTestConfig(
            """
            network.base_url=http://localhost:9090
            network.active=true
            """
        ) {
            val config = ConfigManager.loadNetworkConfig()

            assertEquals("localhost", config.host)
            assertEquals(9090, config.port)
            assertEquals("http://localhost:9090", config.baseUrl)
            assertEquals("http://localhost:9090", config.restBaseUri.toString())
            assertEquals("ws://localhost:9090/ws/fields", config.webSocketUri.toString())
            assertTrue(config.active)
        }
    }

    /**
     * Verifies that missing network configuration falls back to documented defaults.
     */
    @Test
    fun loadNetworkConfigUsesDefaultsWhenConfigFileIsMissing() {
        moveConfigFileAway()
        try {
            assertDefaultNetworkConfig(ConfigManager.loadNetworkConfig())
        } finally {
            restoreMovedConfigFile()
        }
    }

    /**
     * Verifies that invalid network values do not crash and use defaults.
     */
    @Test
    fun loadNetworkConfigUsesDefaultsForInvalidValues() {
        withTestConfig(
            """
            network.host=
            network.port=70000
            network.base_url=not a url
            network.active=yes
            """
        ) {
            assertDefaultNetworkConfig(ConfigManager.loadNetworkConfig())
        }
    }

    /**
     * Verifies that the full configuration object exposes the network section.
     */
    @Test
    fun loadConfigContainsNetworkSection() {
        withTestConfig("network.host=example.org") {
            val config = ConfigManager.loadConfig().network

            assertEquals("example.org", config.host)
            assertEquals(443, config.port)
            assertEquals("https://example.org", config.baseUrl)
            assertFalse(config.active)
        }
    }

    /**
     * Deletes a temporary test file if it exists.
     *
     * @param file The file to delete.
     */
    private fun deleteFile(file: File) {
        if (file.exists() && !file.delete()) {
            System.err.println("Failed to delete temporary config test file: ${file.path}")
        }
    }

    /**
     * Replaces the application config with test content while the given block runs.
     *
     * @param content The properties content to write into the temporary test config.
     * @param block The assertions to run while the test config is active.
     */
    private fun withTestConfig(content: String, block: () -> Unit) {
        testConfigFile.writeText(content.trimIndent())
        moveConfigFileAway()
        Files.move(testConfigFile.toPath(), configFile.toPath())
        try {
            block()
        } finally {
            Files.move(configFile.toPath(), testConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            restoreMovedConfigFile()
        }
    }

    /**
     * Moves the real application config away so tests can use a controlled config state.
     */
    private fun moveConfigFileAway() {
        if (configFile.exists()) {
            Files.move(configFile.toPath(), tempConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Restores the real application config after it was moved away for a test.
     */
    private fun restoreMovedConfigFile() {
        if (tempConfigFile.exists()) {
            Files.move(tempConfigFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Verifies that the given config matches the documented network defaults.
     *
     * @param config The network config returned by ConfigManager.
     */
    private fun assertDefaultNetworkConfig(config: ConfigManager.NetworkConfig) {
        assertEquals("traficsim.se.uni-kassel.de", config.host)
        assertEquals(443, config.port)
        assertEquals("https://traficsim.se.uni-kassel.de", config.baseUrl)
        assertEquals("https://traficsim.se.uni-kassel.de", config.restBaseUri.toString())
        assertEquals("wss://traficsim.se.uni-kassel.de/ws/fields", config.webSocketUri.toString())
        assertFalse(config.active)
    }
}
