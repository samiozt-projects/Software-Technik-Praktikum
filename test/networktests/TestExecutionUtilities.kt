package networktests

import config.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import network.NetworkConnectionState
import network.TrafficSimWebSocketClient
import network.VehicleMessageData
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Utility functions for test execution logic.
 */
object TestExecutionUtilities {

    /**
     * Executes the connection and vehicle sending test.
     */
    suspend fun executeConnectionAndVehicleSendingTest() {
        println("Starting real server test - attempting to connect to server")
        logServerConfiguration()
        
        val restClient = RestTestUtilities.createRestClient()
        if (!RestTestUtilities.testServerConnectivity(restClient)) return
        
        val client = ClientTestUtilities.createTestClient(onVehicleMessage = { println("Vehicle received") })
        var testWorld: RestTestUtilities.TestWorldSetup? = null
        
        try {
            testWorld = RestTestUtilities.setupTestWorldAndGetFieldIds(restClient)
            val field1Id = testWorld.firstFieldId
            val field2Id = testWorld.secondFieldId
            val transitionId = testWorld.transitionId
            assertTrue(
                WebSocketTestUtilities.connectWithTimeout(client, field1Id),
                "Connection should be successful"
            )
            
            val testVehicle = TestUtilities.createTestVehicle()
            TestUtilities.logVehicleProperties(testVehicle)
            assertTrue(
                WebSocketTestUtilities.sendTestVehicle(client, testVehicle, transitionId, field1Id, field2Id),
                "Vehicle should be sent successfully"
            )
            
            delay(2000.milliseconds)
            println("Test completed successfully")
            
        } finally {
            WebSocketTestUtilities.disconnectSafely(client)
            testWorld?.let { RestTestUtilities.deleteTestWorld(restClient, it.worldId) }
        }
    }

    /**
     * Executes the vehicle reception test.
     */
    suspend fun executeVehicleReceptionTest() {
        val receivedVehicles = mutableListOf<VehicleMessageData>()
        val restClient = RestTestUtilities.createRestClient()
        val client = ClientTestUtilities.createTestClient(onVehicleMessage = { receivedVehicles.add(it) })
        var testWorld: RestTestUtilities.TestWorldSetup? = null
        
        try {
            testWorld = RestTestUtilities.setupTestWorldAndGetFieldIds(restClient)
            val field1Id = testWorld.firstFieldId
            WebSocketTestUtilities.connectWithTimeout(client, field1Id)
            delay(5000.milliseconds) // Wait for vehicles
            
            validateReceivedVehicles(receivedVehicles)
            
        } finally {
            WebSocketTestUtilities.disconnectSafely(client)
            testWorld?.let { RestTestUtilities.deleteTestWorld(restClient, it.worldId) }
        }
    }

    /**
     * Executes the error handling test.
     */
    suspend fun executeErrorHandlingTest() {
        var errorReceived: Boolean

        val restClient = RestTestUtilities.createRestClient()
        val client = ClientTestUtilities.createTestClient()
        var testWorld: RestTestUtilities.TestWorldSetup? = null
        
        try {
            testWorld = RestTestUtilities.setupTestWorldAndGetFieldIds(restClient)
            val field1Id = testWorld.firstFieldId
            
            errorReceived = testInvalidConnection(client)
            
            errorReceived = testInvalidVehicleSend(client, field1Id) || errorReceived

            assertTrue(errorReceived, "Errors should have been detected")
            
        } finally {
            WebSocketTestUtilities.disconnectSafely(client)
            testWorld?.let { RestTestUtilities.deleteTestWorld(restClient, it.worldId) }
        }
    }

    /**
     * Validates received vehicles.
     */
    fun validateReceivedVehicles(receivedVehicles: List<VehicleMessageData>) {
        if (receivedVehicles.isNotEmpty()) {
            val firstVehicle = receivedVehicles[0]
            assertNotNull(firstVehicle.vehicleProperties)
            assertNotNull(firstVehicle.vehicleProperties["color"])
            assertNotNull(firstVehicle.vehicleProperties["type"])
            assertNotNull(firstVehicle.vehicleProperties["speedMetersPerSecond"])
        }
        
        println("Received ${receivedVehicles.size} vehicles")
    }

    /**
     * Tests invalid connection.
     */
    suspend fun testInvalidConnection(client: TrafficSimWebSocketClient): Boolean {
        var errorReceived = false
        try {
            withContext(Dispatchers.IO) {
                client.connect("invalid-field-id").get()
            }
            delay(1000.milliseconds)
            if (client.connectionState == NetworkConnectionState.ERROR) errorReceived = true
        } catch (_: Exception) {
            errorReceived = true
        }
        return errorReceived
    }

    /**
     * Tests invalid vehicle send.
     */
    suspend fun testInvalidVehicleSend(client: TrafficSimWebSocketClient, field1Id: String): Boolean {
        var errorReceived = false
        if (client.connectionState == NetworkConnectionState.CONNECTED) {
            try {
                WebSocketTestUtilities.sendTestVehicle(
                    client, 
                    TestUtilities.createTestVehicle(), 
                    "invalid-transition", 
                    field1Id, 
                    "invalid-target", 
                    level = 99
                )
            } catch (_: Exception) {
                errorReceived = true
            }
        }
        return errorReceived
    }

    /**
     * Logs server configuration.
     */
    fun logServerConfiguration() {
        try {
            val config = ConfigManager.loadNetworkConfig()
            println("Connecting to server at: ${config.baseUrl}")
        } catch (_: Exception) {
            println("Using default server configuration")
        }
    }
}
