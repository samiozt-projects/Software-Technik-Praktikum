package networktests

import domain.Color
import domain.Vehicle
import domain.VehicleType
import korlibs.image.color.RGBA
import network.TrafficSimWebSocketClient
import network.VehicleMessageData
import network.VehicleMessageType
import network.VehicleWebSocketConverter
import config.ConfigManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import network.NetworkJson
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import networktests.TestUtilities.createTestLevel
import networktests.TestUtilities.createTestSourceNode
import networktests.TestUtilities.createTestSourceNodeConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for Vehicle WebSocket functionality.
 * These tests simulate real-world usage scenarios.
 */
class VehicleWebSocketIntegrationTest {
    
    /** Test the complete workflow of sending and receiving vehicle data. */
    @Test
    fun testCompleteVehicleWorkflow() {
        val vehicle = Vehicle(
            Color("test", RGBA(255, 0, 0, 255)),
            VehicleType("car", 100.0, 10.0, 0.8, 2.5),
            createTestSourceNode(),
            createTestSourceNodeConfiguration(),
            createTestLevel()
        ).apply { currentSpeed = 60.0 }
        
        val properties = VehicleWebSocketConverter.vehicleToWebSocketProperties(vehicle)
        
        // Verify required fields exist
        assertNotNull(properties["color"])
        assertNotNull(properties["type"])
        assertNotNull(properties["speedMetersPerSecond"])
        
        // Test round-trip conversion
        val convertedVehicle = VehicleWebSocketConverter.webSocketPropertiesToVehicle(
            properties, vehicle.sourceNode, vehicle.sourceNodeConfiguration, vehicle.level
        )
        
        assertNotNull(convertedVehicle)
        assertEquals(vehicle.currentSpeed, convertedVehicle.currentSpeed)
        assertEquals(vehicle.type.name.lowercase(), convertedVehicle.type.name.lowercase())
    }
    
    /** Test handling of vehicle arrival messages. */
    @Test
    fun testVehicleArrivalMessageHandling() {
        var receivedData: VehicleMessageData? = null
        
        val client = TrafficSimWebSocketClient(
            ConfigManager.NetworkConfig("localhost", 8080, true, "http://localhost:8080"),
            onVehicleMessage = { receivedData = it }
        )
        
        val arrivalMessage = """
            {
              "type": "VEHICLE_ARRIVAL",
              "transitionNodeId": "transition-123",
              "fromFieldId": "field-source",
              "toFieldId": "field-target",
              "level": 1,
              "vehicleProperties": {
                "color": {"r": 0, "g": 255, "b": 0, "a": 255},
                "type": "TRUCK",
                "speedMetersPerSecond": 45.5
              }
            }
        """.trimIndent()
        
        client.handleIncomingText(arrivalMessage)
        
        assertNotNull(receivedData)
        with(receivedData) {
            assertEquals(VehicleMessageType.VEHICLE_ARRIVAL, messageType)
            assertEquals("transition-123", transitionNodeId)
            assertEquals("field-source", fromFieldId)
            assertEquals("field-target", toFieldId)
            assertEquals(1, level)
            
            val props = vehicleProperties!!
            assertTrue(props["color"] is JsonObject)
            assertEquals("TRUCK", props["type"]?.toString()?.trim('"'))
        }
    }
    
    /** Test handling of vehicle source messages. */
    @Test
    fun testVehicleSourceMessageHandling() {
        var receivedData: VehicleMessageData? = null
        
        val client = TrafficSimWebSocketClient(
            ConfigManager.NetworkConfig("localhost", 8080, true, "http://localhost:8080"),
            onVehicleMessage = { receivedData = it }
        )
        
        val sourceMessage = """
            {
              "type": "VEHICLE_SOURCE",
              "fieldId": "field-456",
              "sourceNodeId": "source-node-789",
              "level": 0,
              "vehicleProperties": {
                "color": {"r": 255, "g": 0, "b": 255, "a": 255},
                "type": "MOTORCYCLE"
              }
            }
        """.trimIndent()
        
        client.handleIncomingText(sourceMessage)
        
        assertNotNull(receivedData)
        with(receivedData) {
            assertEquals(VehicleMessageType.VEHICLE_SOURCE, messageType)
            assertEquals("source-node-789", sourceNodeId)
            assertEquals("field-456", fieldId)
            assertEquals(0, level)
        }
    }
    
    /** Test sending vehicles with different types. */
    @Test
    fun testDifferentVehicleTypes() {
        val sourceNode = createTestSourceNode()
        val sourceNodeConfig = createTestSourceNodeConfiguration()
        val level = createTestLevel()
        
        /**
         * Tests a specific vehicle type.
         * 
         * @param name The name of the vehicle type.
         * @param rgba The RGBA color of the vehicle.
         * @param speed The speed of the vehicle.
         * @param expectedType The expected type string.
         */
        fun testVehicleType(name: String, rgba: RGBA, speed: Double, expectedType: String) {
            val vehicle = Vehicle(Color(name, rgba), 
                                VehicleType(name, 100.0, 10.0, 0.8, 2.5), 
                                sourceNode, sourceNodeConfig, level).apply { currentSpeed = speed }
            
            val properties = VehicleWebSocketConverter.vehicleToWebSocketProperties(vehicle)
            assertEquals(expectedType, (properties["type"] as JsonPrimitive).content)
        }
        
        testVehicleType("car", RGBA(255, 0, 0, 255), 50.0, "CAR")
        testVehicleType("truck", RGBA(0, 0, 255, 255), 40.0, "TRUCK")
        testVehicleType("motorcycle", RGBA(0, 255, 0, 255), 70.0, "MOTORCYCLE")
    }
    
    /** Test error handling in vehicle conversion. */
    @Test
    fun testVehicleConversionErrorHandling() {
        val sourceNode = createTestSourceNode()
        val sourceNodeConfig = createTestSourceNodeConfiguration()
        val level = createTestLevel()
        
        // Test with null properties
        val nullResult = VehicleWebSocketConverter.webSocketPropertiesToVehicle(
            null, sourceNode, sourceNodeConfig, level
        )
        assertEquals(null, nullResult)
        
        // Test with empty properties
        val emptyProperties = buildJsonObject { }
        val emptyResult = VehicleWebSocketConverter.webSocketPropertiesToVehicle(
            emptyProperties, sourceNode, sourceNodeConfig, level
        )
        assertNotNull(emptyResult)
        // Should have default values
        assertEquals("car", emptyResult.type.name.lowercase())
        assertEquals(0.0, emptyResult.currentSpeed)
        
        // Test with invalid property types
        val invalidProperties = buildJsonObject {
            put("color", "not_a_number")
            put("type", 12345)
            put("speedMetersPerSecond", "not_a_number")
        }
        
        val invalidResult = VehicleWebSocketConverter.webSocketPropertiesToVehicle(
            invalidProperties, sourceNode, sourceNodeConfig, level
        )
        assertNotNull(invalidResult)
        // Should have default values due to parsing failures
        assertEquals("car", invalidResult.type.name.lowercase())
        assertEquals(0.0, invalidResult.currentSpeed)
    }
    
    /** Test color conversion between RGBA and ARGB. */
    @Test
    fun testColorConversion() {
        /**
         * Tests color conversion.
         * 
         * @param rgba The RGBA color to test.
         * @param expectedR The expected red component.
         * @param expectedG The expected green component.
         * @param expectedB The expected blue component.
         * @param expectedA The expected alpha component.
         */
        fun testColor(rgba: RGBA, expectedR: Int, expectedG: Int, expectedB: Int, expectedA: Int) {
            val vehicle = Vehicle(
                Color("test", rgba), 
                VehicleType("car", 100.0, 10.0, 0.8, 2.5), 
                createTestSourceNode(), 
                createTestSourceNodeConfiguration(), 
                createTestLevel()
            )
            
            val properties = VehicleWebSocketConverter.vehicleToWebSocketProperties(vehicle)
            val colorElement = properties["color"] as JsonObject
            
            assertEquals(expectedR, (colorElement["r"] as JsonPrimitive).content.toInt())
            assertEquals(expectedG, (colorElement["g"] as JsonPrimitive).content.toInt())
            assertEquals(expectedB, (colorElement["b"] as JsonPrimitive).content.toInt())
            assertEquals(expectedA, (colorElement["a"] as JsonPrimitive).content.toInt())
        }
        
        // Test different colors
        testColor(RGBA(255, 0, 0, 255), 255, 0, 0, 255) // Red
        testColor(RGBA(0, 255, 0, 255), 0, 255, 0, 255) // Green
        testColor(RGBA(0, 0, 255, 128), 0, 0, 255, 128) // Semi-transparent blue
    }
    
    /**
     * Round-Trip-Unit-Test: Ensures serialization and deserialization work correctly.
     * Interoperability: Verifies a vehicle received from another team retains its properties.
     * Uses a concrete example payload from another team.
     */
    @Test
    fun testRoundTripWithExamplePayloadFromOtherTeam() {
        testTruckRoundTrip()
        testMotorcycleRoundTrip()
        testCarRoundTrip()
        testUnknownVehicleTypeRoundTrip()
    }

    /**
     * Tests a truck round-trip.
     */
    private fun testTruckRoundTrip() {
        // Example payload that might come from another team
        val examplePayloadFromOtherTeam = """
            {
              "color": {"r": 128, "g": 0, "b": 128, "a": 255},
              "type": "TRUCK",
              "speedMetersPerSecond": 22.5
            }
        """.trimIndent()
        
        val properties = NetworkJson.decodeFromString<JsonObject>(examplePayloadFromOtherTeam)
        val vehicle = VehicleWebSocketConverter.webSocketPropertiesToVehicle(
            properties,
            createTestSourceNode(),
            createTestSourceNodeConfiguration(),
            createTestLevel()
        )
        
        assertNotNull(vehicle, "Vehicle should be created from example payload")
        assertEquals(22.5, vehicle.currentSpeed, "Speed should be preserved")
        assertEquals("truck", vehicle.type.name.lowercase(), "Type should be preserved")

        val serializedProperties = VehicleWebSocketConverter.vehicleToWebSocketProperties(vehicle)

        assertEquals("TRUCK", (serializedProperties["type"] as JsonPrimitive).content)
        assertEquals(22.5, (serializedProperties["speedMetersPerSecond"] as JsonPrimitive).content.toDouble())

        val serializedColor = serializedProperties["color"] as JsonObject
        assertEquals(128, (serializedColor["r"] as JsonPrimitive).content.toInt())
        assertEquals(0, (serializedColor["g"] as JsonPrimitive).content.toInt())
        assertEquals(128, (serializedColor["b"] as JsonPrimitive).content.toInt())
        assertEquals(255, (serializedColor["a"] as JsonPrimitive).content.toInt())
    }

    /**
     * Tests a motorcycle round-trip.
     */
    private fun testMotorcycleRoundTrip() {
        val motorcyclePayload = """
            {
              "color": {"r": 0, "g": 200, "b": 200, "a": 255},
              "type": "MOTORCYCLE",
              "speedMetersPerSecond": 35.0
            }
        """.trimIndent()
        
        val motorcycleProperties = NetworkJson.decodeFromString<JsonObject>(motorcyclePayload)
        val motorcycle = VehicleWebSocketConverter.webSocketPropertiesToVehicle(
            motorcycleProperties,
            createTestSourceNode(),
            createTestSourceNodeConfiguration(),
            createTestLevel()
        )
        
        assertNotNull(motorcycle)
        assertEquals(35.0, motorcycle.currentSpeed)
        assertEquals("motorcycle", motorcycle.type.name.lowercase())
    }

    /**
     * Tests a car round-trip.
     */
    private fun testCarRoundTrip() {
        val carPayload = """
            {
              "color": {"r": 255, "g": 0, "b": 0, "a": 255},
              "type": "CAR",
              "speedMetersPerSecond": 25.0
            }
        """.trimIndent()

        val carProperties = NetworkJson.decodeFromString<JsonObject>(carPayload)
        val car = VehicleWebSocketConverter.webSocketPropertiesToVehicle(
            carProperties,
            createTestSourceNode(),
            createTestSourceNodeConfiguration(),
            createTestLevel()
        )

        assertNotNull(car)
        assertEquals(25.0, car.currentSpeed)
        assertEquals("car", car.type.name.lowercase())
    }

    /**
     * Tests an unknown vehicle type round-trip.
     */
    private fun testUnknownVehicleTypeRoundTrip() {
        val unknownPayload = """
            {
              "color": {"r": 0, "g": 0, "b": 255, "a": 255},
              "type": "BUS",
              "speedMetersPerSecond": 15.0
            }
        """.trimIndent()

        val unknownProperties = NetworkJson.decodeFromString<JsonObject>(unknownPayload)
        val unknownVehicle = VehicleWebSocketConverter.webSocketPropertiesToVehicle(
            unknownProperties,
            createTestSourceNode(),
            createTestSourceNodeConfiguration(),
            createTestLevel()
        )

        assertNotNull(unknownVehicle)
        assertEquals(15.0, unknownVehicle.currentSpeed)
        assertEquals("car", unknownVehicle.type.name.lowercase()) // Should default to car
    }
}
