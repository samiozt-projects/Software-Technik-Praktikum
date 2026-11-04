package networktests

import domain.Color
import domain.Vehicle
import domain.VehicleType
import korlibs.image.color.RGBA
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import network.VehicleWebSocketConverter

/**
 * Utility functions for network tests.
 */
object TestUtilities {

    /**
     * Creates a test source node for vehicle departure testing.
     * @return The created test source node
     */
    fun createTestSourceNode(): domain.SourceNode {
        return domain.SourceNode(
            position = Point(0.0, 0.0),
            elevation = 0,
            orientation = Angle.ZERO,
            configurations = emptyList()
        )
    }

    /**
     * Creates a test source node configuration.
     * @return The created test source node configuration
     */
    fun createTestSourceNodeConfiguration(): domain.SourceNodeConfiguration {
        return domain.SourceNodeConfiguration(
            color = Color("test", RGBA(255, 255, 255, 255)),
            generationSpeed = 1.0,
            initialVehicleSpeed = 50.0,
            vehicleType = VehicleType("test", 100.0, 10.0, 0.8, 2.5),
            vehiclesToSpawn = 10,
            vehiclesSpawned = 0,
            vehiclesArrived = 0
        )
    }

    /**
     * Creates a test level.
     * @return The created test level
     */
    fun createTestLevel(): domain.Level {
        return domain.Level(
            name = "test-level",
            number = 1
        )
    }

    /**
     * Creates a test vehicle with the specified properties.
     * @param color The color of the vehicle
     * @param vehicleTypeName The type name of the vehicle
     * @param speed The speed of the vehicle
     * @return The created test vehicle
     */
    fun createTestVehicle(
        color: RGBA = RGBA(255, 0, 0, 255),
        vehicleTypeName: String = "car",
        speed: Double = 50.0
    ): Vehicle {
        val vehicleType = when (vehicleTypeName.lowercase()) {
            "truck" -> VehicleType("truck", 80.0, 15.0, 0.7, 2.0)
            "motorcycle" -> VehicleType("motorcycle", 120.0, 5.0, 0.8, 3.0)
            else -> VehicleType("car", 100.0, 10.0, 0.8, 2.5)
        }

        return Vehicle(
            color = Color(vehicleTypeName, color),
            type = vehicleType,
            sourceNode = createTestSourceNode(),
            sourceNodeConfiguration = createTestSourceNodeConfiguration(),
            level = createTestLevel()
        ).apply {
            currentSpeed = speed
        }
    }

    /**
     * Logs vehicle properties for debugging.
     * @param vehicle The vehicle whose properties to log
     */
    fun logVehicleProperties(vehicle: Vehicle) {
        val properties = VehicleWebSocketConverter.vehicleToWebSocketProperties(vehicle)
        println("Sending test vehicle with properties: $properties")
    }
}