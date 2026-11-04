package networktests

import config.ConfigManager
import network.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Helper function to create a test source node.
 * 
 * @return A test source node.
*/
private fun createTestSourceNode(): domain.SourceNode {
    return domain.SourceNode(
        position = korlibs.math.geom.Point(0.0, 0.0),
        elevation = 0,
        orientation = korlibs.math.geom.Angle.ZERO,
        configurations = emptyList()
    )
}

/**
 * Helper function to create a test source node configuration.
 * 
 * @return A test source node configuration.
*/
private fun createTestSourceNodeConfiguration(): domain.SourceNodeConfiguration {
    return domain.SourceNodeConfiguration(
        color = domain.Color("test", korlibs.image.color.RGBA(255, 255, 255, 255)),
        generationSpeed = 1.0,
        initialVehicleSpeed = 50.0,
        vehicleType = domain.VehicleType("test", 100.0, 10.0, 0.8, 2.5),
        vehiclesToSpawn = 10,
        vehiclesSpawned = 0,
        vehiclesArrived = 0
    )
}

/**
 * Helper function to create a test level.
 * 
 * @return A test level.
*/
private fun createTestLevel(): domain.Level {
    return domain.Level(
        name = "test-level",
        number = 1
    )
}

/** Tests WebSocket message handling without depending on an external server. */
class TrafficSimWebSocketClientTest {
    private val client = TrafficSimWebSocketClient(
        ConfigManager.NetworkConfig(
            host = "localhost",
            port = 8080,
            active = true,
            baseUrl = "http://localhost:8080"
        )
    )

    /** Verifies that FIELD_CONNECT is encoded in the server format. */
    @Test
    fun encodesFieldConnectMessage() {
        val encoded = client.encode(WebSocketMessage.fieldConnect("field-1"))

        assertTrue(encoded.contains("FIELD_CONNECT"))
        assertTrue(encoded.contains("field-1"))
        assertEquals(
            WebSocketMessageType.FIELD_CONNECT,
            NetworkJson.decodeFromString<WebSocketMessage>(encoded).type
        )
    }

    /** Verifies that vehicle handover keeps arbitrary JSON vehicle properties. */
    @Test
    fun encodesVehicleDepartureWithVehicleProperties() {
        val encoded = client.encode(
            WebSocketMessage.vehicleDeparture(
                transitionNodeId = "transition-1",
                fromFieldId = "field-1",
                toFieldId = "field-2",
                level = 1,
                vehicleProperties = buildJsonObject {
                    put("color", buildJsonObject {
                        put("r", 255)
                        put("g", 0)
                        put("b", 0)
                        put("a", 255)
                    })
                    put("type", "CAR")
                }
            )
        )

        val decoded = NetworkJson.decodeFromString<WebSocketMessage>(encoded)

        assertEquals(WebSocketMessageType.VEHICLE_DEPARTURE, decoded.type)
        assertEquals("transition-1", decoded.transitionNodeId)
        assertEquals(1, decoded.level)
        val colorElement = decoded.vehicleProperties?.get("color")
        assertNotNull(colorElement)
        assertTrue(colorElement is JsonObject)
        val rElement = colorElement["r"]
        assertNotNull(rElement)
        assertTrue(rElement is JsonPrimitive)
        assertEquals(255, (rElement).content.toInt())
        assertEquals("CAR", decoded.vehicleProperties["type"]?.toString()?.trim('"'))
    }

    /** Verifies that vehicle handover works with Vehicle domain objects. */
    @Test
    fun encodesVehicleDepartureWithVehicleDomainObject() {
        val vehicle = domain.Vehicle(
            color = domain.Color("test", korlibs.image.color.RGBA(255, 0, 0, 255)),
            type = domain.VehicleType("car", 100.0, 10.0, 0.8, 2.5),
            sourceNode = createTestSourceNode(),
            sourceNodeConfiguration = createTestSourceNodeConfiguration(),
            level = createTestLevel()
        ).apply { currentSpeed = 50.0 }
        
        val properties = VehicleWebSocketConverter.vehicleToWebSocketProperties(vehicle)
        
        with(properties["color"] as JsonObject) {
            assertEquals(255, (this["r"] as JsonPrimitive).content.toInt())
            assertEquals(0, (this["g"] as JsonPrimitive).content.toInt())
            assertEquals(0, (this["b"] as JsonPrimitive).content.toInt())
            assertEquals(255, (this["a"] as JsonPrimitive).content.toInt())
        }
        
        assertEquals("CAR", (properties["type"] as JsonPrimitive).content)
        assertEquals(50.0, (properties["speedMetersPerSecond"] as JsonPrimitive).content.toDouble())
    }

    /** Verifies that incoming live messages are stored for later simulation integration. */
    @Test
    fun storesIncomingVehicleArrivalMessages() {
        var vehicleMessageReceived = false
        var vehicleMessageData: VehicleMessageData? = null
        
        val clientWithCallback = TrafficSimWebSocketClient(
            ConfigManager.NetworkConfig(
                host = "localhost", port = 8080, active = true, baseUrl = "http://localhost:8080"),
            onVehicleMessage = { data ->
                vehicleMessageReceived = true
                vehicleMessageData = data
            }
        )
        
        clientWithCallback.handleIncomingText(
            """
            {
              "type": "VEHICLE_ARRIVAL",
              "transitionNodeId": "transition-1",
              "fromFieldId": "field-1",
              "toFieldId": "field-2",
              "level": 0,
              "vehicleProperties": { "color": "blue" },
              "message": "Vehicle arrived"
            }
            """.trimIndent()
        )

        val message = clientWithCallback.receivedMessages.single()

        assertEquals(WebSocketMessageType.VEHICLE_ARRIVAL, message.type)
        assertEquals("transition-1", message.transitionNodeId)
        assertEquals("blue", message.vehicleProperties?.get("color")?.toString()?.trim('"'))
        
        // Verify the vehicle message callback was called
        assertEquals(true, vehicleMessageReceived)
        assertNotNull(vehicleMessageData)
        assertEquals(VehicleMessageType.VEHICLE_ARRIVAL, vehicleMessageData.messageType)
        assertEquals("transition-1", vehicleMessageData.transitionNodeId)
        assertEquals("field-1", vehicleMessageData.fromFieldId)
        assertEquals("field-2", vehicleMessageData.toFieldId)
        assertEquals(0, vehicleMessageData.level)
    }
    
    /** Verifies that incoming vehicle source messages trigger the vehicle callback. */
    @Test
    fun handlesIncomingVehicleSourceMessages() {
        var receivedData: VehicleMessageData? = null
        
        val client = TrafficSimWebSocketClient(
            ConfigManager.NetworkConfig("localhost", 8080, true, "http://localhost:8080"),
            onVehicleMessage = { receivedData = it }
        )
        
        val sourceMessage = """
            {
              "type": "VEHICLE_SOURCE",
              "fieldId": "field-1",
              "sourceNodeId": "source-1",
              "level": 1,
              "vehicleProperties": { "color": {"r": 0, "g": 255, "b": 0, "a": 255}, "type": "TRUCK" }
            }
        """.trimIndent()
        
        client.handleIncomingText(sourceMessage)
        
        assertNotNull(receivedData)
        verifyVehicleSourceMessage(receivedData!!)
    }
    
    /**
     * Helper function to verify that a vehicle source message is valid.
     */
    private fun verifyVehicleSourceMessage(data: VehicleMessageData) {
        assertEquals(VehicleMessageType.VEHICLE_SOURCE, data.messageType)
        assertEquals("source-1", data.sourceNodeId)
        assertEquals("field-1", data.fieldId)
        assertEquals(1, data.level)
        
        val color = data.vehicleProperties?.get("color") as JsonObject
        assertEquals(0, (color["r"] as JsonPrimitive).content.toInt())
        assertEquals(255, (color["g"] as JsonPrimitive).content.toInt())
        assertEquals("TRUCK", (data.vehicleProperties["type"] as JsonPrimitive).content)
    }

    /** Verifies that documented server errors update the connection state safely. */
    @Test
    fun handlesFieldRegistrationError() {
        client.handleIncomingText(
            """
            {
              "type": "ERROR",
              "errorCode": "FIELD_REGISTRATION_FAILED",
              "message": "Field is already active"
            }
            """.trimIndent()
        )

        val error = assertNotNull(client.lastError)

        assertEquals(NetworkConnectionState.ERROR, client.connectionState)
        assertEquals(WebSocketErrorCode.FIELD_REGISTRATION_FAILED, error.code)
        assertEquals("FIELD_REGISTRATION_FAILED", error.rawCode)
    }

    /** Verifies that malformed WebSocket payloads do not crash the client. */
    @Test
    fun invalidMessageFormatIsHandledWithoutCrash() {
        client.handleIncomingText("""{"type":"UNKNOWN_MESSAGE"}""")

        val error = assertNotNull(client.lastError)

        assertEquals(NetworkConnectionState.ERROR, client.connectionState)
        assertEquals(WebSocketErrorCode.INVALID_MESSAGE_FORMAT, error.code)
    }

    /**
     * Verifies that a detected connection loss records the error and notifies the connection-lost
     * listener exactly once, so a heartbeat timeout and a stray ping failure cannot start two
     * reconnects (issue #210).
     */
    @Test
    fun handleConnectionLoss_notifiesListenerOnceAndSetsError() {
        var lostCount = 0
        val client = TrafficSimWebSocketClient(
            ConfigManager.NetworkConfig(
                host = "localhost",
                port = 8080,
                active = true,
                baseUrl = "http://localhost:8080"
            ),
            onConnectionLost = { lostCount++ }
        )

        client.handleConnectionLoss("simulated drop")
        client.handleConnectionLoss("second detection")

        assertEquals(1, lostCount)
        assertEquals(NetworkConnectionState.ERROR, client.connectionState)
    }

    /**
     * Verifies that the heartbeat only reports the server as silent once the inbound-activity
     * deadline has passed, which is what bounds how long a silently dropped link (e.g. Wi-Fi off)
     * stays undetected.
     */
    @Test
    fun heartbeat_reportsSilenceOnlyPastDeadline() {
        client.markInboundActivity()
        val now = System.currentTimeMillis()

        assertFalse(client.isSilentPastDeadline(now))
        assertTrue(client.isSilentPastDeadline(now + TrafficSimWebSocketClient.HEARTBEAT_TIMEOUT_MS + 1_000))
    }
}
