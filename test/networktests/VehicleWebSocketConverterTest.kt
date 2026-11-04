package networktests

import domain.Color
import domain.Vehicle
import domain.VehicleType as DomainVehicleType
import network.VehicleWebSocketConverter
import korlibs.image.color.RGBA
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        color = Color("test", RGBA(255, 255, 255, 255)),
        generationSpeed = 1.0,
        initialVehicleSpeed = 50.0,
        vehicleType = DomainVehicleType("test", 100.0, 10.0, 0.8, 2.5),
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

/** Tests for VehicleWebSocketConverter utility functions. */
class VehicleWebSocketConverterTest {
    
    /** Test vehicle to domain properties conversion. */
    @Test
    fun testVehicleToWebSocketProperties() {
        // Create a test vehicle with minimal required parameters
        val color = Color("test", RGBA(255, 0, 0, 255)) // Red
        val vehicleType = DomainVehicleType("car", 100.0, 10.0, 0.8, 2.5)
        
        // Create minimal source node and configuration
        val sourceNode = createTestSourceNode()
        val sourceNodeConfig = createTestSourceNodeConfiguration()
        val level = createTestLevel()
        
        val vehicle = Vehicle(color, vehicleType, sourceNode, sourceNodeConfig, level)
        vehicle.currentSpeed = 50.0
        
        // Convert to WebSocket properties
        val properties = VehicleWebSocketConverter.vehicleToWebSocketProperties(vehicle)
        
        // Verify the properties
        val colorElement = properties["color"]
        assertNotNull(colorElement, "Color should be present")
        assertTrue(colorElement is JsonObject, "Color should be an object")
        assertEquals(255, (colorElement["r"] as JsonPrimitive).content.toInt())
        assertEquals(0, (colorElement["g"] as JsonPrimitive).content.toInt())
        assertEquals(0, (colorElement["b"] as JsonPrimitive).content.toInt())
        assertEquals(255, (colorElement["a"] as JsonPrimitive).content.toInt())
        
        assertEquals("CAR", (properties["type"] as? JsonPrimitive)?.content)
        assertEquals(50.0, (properties["speedMetersPerSecond"] as? JsonPrimitive)?.content?.toDouble())
    }
    
    /** Test WebSocket properties to vehicle conversion with valid data. */
    @Test
    fun testWebSocketPropertiesToVehicleValid() {
        // Create valid vehicle properties (new format)
        val properties = Json.parseToJsonElement("""
            {
                "color": {"r": 0, "g": 0, "b": 255, "a": 255},
                "type": "TRUCK",
                "speedMetersPerSecond": 60.0
            }
        """.trimIndent()).jsonObject
        
        val sourceNode = createTestSourceNode()
        val sourceNodeConfig = createTestSourceNodeConfiguration()
        val level = createTestLevel()
        
        // Convert to vehicle
        val vehicle = VehicleWebSocketConverter.webSocketPropertiesToVehicle(
            properties, sourceNode, sourceNodeConfig, level
        )
        
        // Verify the vehicle
        assertNotNull(vehicle)
        assertEquals(0, vehicle.color.value.r) // Blue channel (from RGB)
        assertEquals(0, vehicle.color.value.g)
        assertEquals(255, vehicle.color.value.b)
        assertEquals(255, vehicle.color.value.a)
        assertEquals("truck", vehicle.type.name.lowercase())
        assertEquals(60.0, vehicle.currentSpeed)
    }
    
    /** Test WebSocket properties to vehicle conversion with missing fields. */
    @Test
    fun testWebSocketPropertiesToVehicleMissingFields() {
        // Create properties with missing fields
        val properties = Json.parseToJsonElement("""
            {
                "type": "MOTORCYCLE"
            }
        """.trimIndent()).jsonObject
        
        val sourceNode = createTestSourceNode()
        val sourceNodeConfig = createTestSourceNodeConfiguration()
        val level = createTestLevel()
        
        // Convert to vehicle
        val vehicle = VehicleWebSocketConverter.webSocketPropertiesToVehicle(
            properties, sourceNode, sourceNodeConfig, level
        )
        
        // Verify the vehicle with defaults
        assertNotNull(vehicle)
        assertEquals(0xFF, vehicle.color.value.r) // Default white
        assertEquals(0xFF, vehicle.color.value.g)
        assertEquals(0xFF, vehicle.color.value.b)
        assertEquals(0xFF, vehicle.color.value.a)
        assertEquals("motorcycle", vehicle.type.name.lowercase())
        assertEquals(0.0, vehicle.currentSpeed) // Default speed
    }
    
    /** Test WebSocket properties to vehicle conversion with null properties. */
    @Test
    fun testWebSocketPropertiesToVehicleNull() {
        val sourceNode = createTestSourceNode()
        val sourceNodeConfig = createTestSourceNodeConfiguration()
        val level = createTestLevel()
        
        // Convert null properties
        val vehicle = VehicleWebSocketConverter.webSocketPropertiesToVehicle(
            null, sourceNode, sourceNodeConfig, level
        )
        
        // Should return null
        assertNull(vehicle)
    }
    
    /** Test WebSocket properties to vehicle conversion with invalid data. */
    @Test
    fun testWebSocketPropertiesToVehicleInvalid() {
        // Create properties with invalid data
        val properties = Json.parseToJsonElement("""
            {
                "color": "invalid",
                "type": 123,
                "speedMetersPerSecond": "not_a_number"
            }
        """.trimIndent()).jsonObject
        
        val sourceNode = createTestSourceNode()
        val sourceNodeConfig = createTestSourceNodeConfiguration()
        val level = createTestLevel()
        
        // Convert to vehicle - should handle gracefully with defaults
        val vehicle = VehicleWebSocketConverter.webSocketPropertiesToVehicle(
            properties, sourceNode, sourceNodeConfig, level
        )
        
        // Should still create a vehicle with defaults
        assertNotNull(vehicle)
        assertEquals(0xFF, vehicle.color.value.r) // Default white
        assertEquals("car", vehicle.type.name.lowercase()) // Default car
        assertEquals(0.0, vehicle.currentSpeed) // Default speed
    }
}
