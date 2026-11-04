package network

import domain.Color
import domain.HandoverVehicleProperties
import domain.Vehicle
import domain.VehicleType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Utility class for converting between Vehicle domain objects and WebSocket vehicle properties.
 */
object VehicleWebSocketConverter {
    
    /**
     * Extracts the RGBA color from the vehicle properties JsonObject.
     * Handles missing fields, invalid formats, and provides fallback values.
     *
     * @param vehicleProperties The JsonObject containing vehicle properties
     * @return RGBA color extracted from properties or fallback white color
     */
    private fun extractColorFromProperties(vehicleProperties: JsonObject): korlibs.image.color.RGBA {
        val colorElement = vehicleProperties["color"]
        return if (colorElement != null && colorElement is JsonObject) {
            val r = colorElement["r"]?.jsonPrimitive?.content?.toIntOrNull() ?: 255
            val g = colorElement["g"]?.jsonPrimitive?.content?.toIntOrNull() ?: 255
            val b = colorElement["b"]?.jsonPrimitive?.content?.toIntOrNull() ?: 255
            val a = colorElement["a"]?.jsonPrimitive?.content?.toIntOrNull() ?: 255
            korlibs.image.color.RGBA(r, g, b, a)
        } else {
            // Fallback to white if color is missing or in wrong format
            korlibs.image.color.RGBA(255, 255, 255, 255)
        }
    }
    
    /**
     * Converts a Vehicle domain object to a JsonObject containing only the required properties
     * for WebSocket communication: color (as RGBA object), type, and speedMetersPerSecond.
     *
     * @param vehicle The Vehicle domain object to convert
     * @return JsonObject containing the vehicle properties for WebSocket communication
     */
    fun vehicleToWebSocketProperties(vehicle: Vehicle): JsonObject {
        return buildJsonObject {
            // Convert color to RGBA object format
            val rgba = vehicle.color.value
            put("color", buildJsonObject {
                put("r", rgba.r)
                put("g", rgba.g)
                put("b", rgba.b)
                put("a", rgba.a)
            })
            
            // Convert vehicle type to uppercase string (CAR, TRUCK, MOTORCYCLE)
            put("type", vehicle.type.name.uppercase())
            
            // Add current speed as speedMetersPerSecond
            put("speedMetersPerSecond", vehicle.currentSpeed)
        }
    }

    /**
     * Parses the agreed team handover payload strictly.
     *
     * Unlike [webSocketPropertiesToVehicle], this function does not invent defaults: missing or
     * malformed color, type, or speed data means the handover cannot be assigned reproducibly and
     * must be discarded.
     *
     * @param vehicleProperties free-form JSON object from a server `VEHICLE_ARRIVAL` message.
     * @return parsed handover properties, or null when the payload is incomplete or malformed.
     */
    fun webSocketPropertiesToHandoverProperties(vehicleProperties: JsonObject?): HandoverVehicleProperties? {
        if (vehicleProperties == null) return null

        val color: korlibs.image.color.RGBA
        val type: String
        val speed: Double
        try {
            val colorObject = vehicleProperties["color"] as? JsonObject ?: return null
            color = parseStrictRgba(colorObject) ?: return null
            type = vehicleProperties["type"]?.jsonPrimitive?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return null
            speed = vehicleProperties["speedMetersPerSecond"]?.jsonPrimitive?.doubleOrNull
                ?: return null
            if (!speed.isFinite()) return null
        } catch (_: IllegalArgumentException) {
            return null
        }

        return HandoverVehicleProperties(
            color = color,
            vehicleType = type,
            speedMetersPerSecond = speed.coerceAtLeast(0.0)
        )
    }
    
    /**
     * Converts a JsonObject from WebSocket vehicle properties to a Vehicle domain object.
     * Handles missing fields, unknown fields, and wrong JSON format gracefully.
     *
     * @param vehicleProperties The JsonObject containing vehicle properties
     * @param sourceNode The source node for the vehicle
     * @param sourceNodeConfiguration The source node configuration
     * @param level The level for the vehicle
     * @return Vehicle domain object created from the properties, or null if conversion fails
     */
    fun webSocketPropertiesToVehicle(
        vehicleProperties: JsonObject?,
        sourceNode: domain.SourceNode,
        sourceNodeConfiguration: domain.SourceNodeConfiguration,
        level: domain.Level
    ): Vehicle? {
        if (vehicleProperties == null) {
            return null
        }
        
        try {
            // Extract color (RGBA object format)
            val rgbaColor = extractColorFromProperties(vehicleProperties)
            val color = Color("websocket_vehicle", rgbaColor)
            
            // Extract vehicle type (now using "type" field with uppercase values)
            val vehicleTypeString = vehicleProperties["type"]?.jsonPrimitive?.content 
                ?: "CAR" // Default to CAR if missing
            
            val vehicleType = when (vehicleTypeString.uppercase()) {
                "TRUCK" -> VehicleType("truck", 80.0, 8.0, 0.7, 2.0)
                "MOTORCYCLE" -> VehicleType("motorcycle", 120.0, 2.0, 0.8, 3.0)
                "CAR" -> VehicleType("car", 100.0, 4.0, 0.8, 2.5)
                else -> {
                    println("Unknown vehicle type received: $vehicleTypeString - Using CAR as fallback")
                    VehicleType("car", 100.0, 4.0, 0.8, 2.5) // Default to car
                }
            }
            
            // Extract current speed (now using "speedMetersPerSecond" field)
            val currentSpeed = vehicleProperties["speedMetersPerSecond"]?.jsonPrimitive?.content?.toDoubleOrNull() 
                ?: 0.0 // Default to 0 if missing or invalid
            
            // Create the vehicle
            val vehicle = Vehicle(color, vehicleType, sourceNode, sourceNodeConfiguration, level)
            vehicle.currentSpeed = currentSpeed
            
            return vehicle
            
        } catch (e: Exception) {
            println("Error converting WebSocket properties to Vehicle: ${e.message}")
            return null
        }
    }

    /**
     * Parses an RGBA object only when all four channels are present and valid.
     *
     * @param colorObject JSON object with `r`, `g`, `b`, and `a` integer fields.
     * @return parsed [korlibs.image.color.RGBA], or null for malformed payloads.
     */
    private fun parseStrictRgba(colorObject: JsonObject): korlibs.image.color.RGBA? {
        val r = colorObject["r"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return null
        val g = colorObject["g"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return null
        val b = colorObject["b"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return null
        val a = colorObject["a"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return null
        if (listOf(r, g, b, a).any { it !in 0..255 }) return null
        return korlibs.image.color.RGBA(r, g, b, a)
    }
}
