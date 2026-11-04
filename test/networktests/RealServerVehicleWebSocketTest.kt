package networktests

import korlibs.image.color.RGBA
import network.VehicleWebSocketConverter
import config.ConfigManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import networktests.WebSocketTestUtilities.disconnectSafely
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Tests for Vehicle WebSocket functionality with a real server.
 * These tests demonstrate how to connect to and interact with an actual server.
 * 
 * Note: These tests require a running server instance and proper configuration.
 * To enable these tests, set the system property "test.realServer" to "true"
 * and ensure the server is running at the configured URL.
 */
class RealServerVehicleWebSocketTest {
    
    companion object {
        /**
         * Flag to enable/disable real server tests
         */
        val realServerAvailable: Boolean by lazy {
            System.getProperty("test.realServer")?.let { explicitFlag ->
                return@lazy explicitFlag.toBoolean()
            }
            // Also check if network configuration is active
            try {
                val config = ConfigManager.loadNetworkConfig()
                println("Network configuration loaded: host=${config.host}, active=${config.active}")
                config.active
            } catch (e: Exception) {
                println("Failed to load network config: ${e.message}, defaulting to false")
                false
            }
        }

        /** Initializes the test configuration */
        init {
            println("=== Real Server Test Configuration ===")
            println("realServerAvailable: $realServerAvailable")
            if (!realServerAvailable) {
                println("To enable real server tests ensure network.active=true in your config/app-config.properties")
            } else {
                try {
                    val config = ConfigManager.loadNetworkConfig()
                    println("Tests will connect to: ${config.baseUrl}")
                } catch (e: Exception) {
                    println("Configuration loaded but failed to display: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Test connecting to the real server and sending a vehicle.
     */
    @Test
    fun testRealServerConnectionAndVehicleSending() = runBlocking {
        if (!realServerAvailable) {
            println("Skipping real server test - server not available")
            println("DIAGNOSTIC: Make sure to run with -D test.realServer=true")
            return@runBlocking
        }

        TestExecutionUtilities.executeConnectionAndVehicleSendingTest()
    }

    /**
     * Test receiving vehicle messages from the server.
     */
    @Test
    fun testRealServerVehicleReception() = runBlocking {
        if (!realServerAvailable) {
            println("Skipping real server test - server not available")
            return@runBlocking
        }
        TestExecutionUtilities.executeVehicleReceptionTest()
    }
    
    /**
     * Test error handling with the real server.
     */
    @Test
    fun testRealServerErrorHandling() = runBlocking {
        if (!realServerAvailable) {
            println("Skipping real server test - server not available")
            return@runBlocking
        }
        TestExecutionUtilities.executeErrorHandlingTest()
    }

    /**
     * Test connection recovery with the real server.
     */
    @Test
    fun testRealServerConnectionRecovery() = runBlocking {
        if (!realServerAvailable) {
            println("Skipping real server test - server not available")
            return@runBlocking
        }
        
        val restClient = RestTestUtilities.createRestClient()
        val client = ClientTestUtilities.createTestClient()
        var testWorld: RestTestUtilities.TestWorldSetup? = null
        
        try {
            testWorld = RestTestUtilities.setupTestWorldAndGetFieldIds(restClient)
            val field1Id = testWorld.firstFieldId
            
            val firstConnection = WebSocketTestUtilities.connectWithTimeout(client, field1Id)
            println("First connection: $firstConnection")

            disconnectSafely(client)
            delay(500)
            val secondConnection = WebSocketTestUtilities.connectWithTimeout(client, field1Id)
            println("Second connection: $secondConnection")
            
        } finally {
            disconnectSafely(client)
            testWorld?.let { RestTestUtilities.deleteTestWorld(restClient, it.worldId) }
        }
    }
    
    /**
     * Test vehicle properties format conversion.
     */
    @Test
    fun testVehiclePropertiesFormat() = runBlocking {
        println("Testing vehicle properties format conversion")
        
        val testVehicle = TestUtilities.createTestVehicle(
            color = RGBA(255, 0, 0, 255),
            vehicleTypeName = "car",
            speed = 12.5
        )
        
        val properties = VehicleWebSocketConverter.vehicleToWebSocketProperties(testVehicle)
        println("Converted vehicle properties: $properties")
        
        validateVehiclePropertiesFormat(properties)
        
        println("Vehicle properties format test passed!")
    }
    
    private fun validateVehiclePropertiesFormat(properties: JsonObject) {
        // Verify the format
        assertNotNull(properties["color"])
        assertNotNull(properties["type"])
        assertNotNull(properties["speedMetersPerSecond"])
        
        // Verify color format
        val colorElement = properties["color"]
        assertNotNull(colorElement, "Color should be present")
        assertTrue(colorElement is JsonObject, "Color should be an object")
        assertNotNull(colorElement["r"])
        assertNotNull(colorElement["g"])
        assertNotNull(colorElement["b"])
        assertNotNull(colorElement["a"])
        
        // Verify values
        val typeElement = properties["type"]
        assertNotNull(typeElement, "Type should be present")
        assertTrue(typeElement is JsonPrimitive, "Type should be a primitive")
        assertEquals("CAR", typeElement.content)
        
        val speedElement = properties["speedMetersPerSecond"]
        assertNotNull(speedElement, "Speed should be present")
        assertTrue(speedElement is JsonPrimitive, "Speed should be a primitive")
        assertEquals(12.5, speedElement.content.toDouble())
    }
}
