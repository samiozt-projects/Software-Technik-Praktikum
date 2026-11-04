package domain

/**
 * Represents a specific type of vehicle with its physical and performance characteristics.
 * 
 * @property name The name or designation of the vehicle type.
 * @property maxSpeed The maximum speed the vehicle can achieve.
 * @property length The physical length of the vehicle.
 * @property adhesionCoefficient The adhesion coefficient, which determines traction.
 * @property acceleration The rate of acceleration of the vehicle.
 * @property graphicRef A reference to the graphic asset used for rendering.
 */
class VehicleType(
    val name: String,
    var maxSpeed: Double,
    val length: Double,
    var adhesionCoefficient: Double,
    var acceleration: Double,
    val graphicRef: String = ""
)
