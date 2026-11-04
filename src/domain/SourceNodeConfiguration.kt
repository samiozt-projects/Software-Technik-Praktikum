package domain

/**
 * Configuration for a SourceNode specifying what kind of vehicles it should spawn.
 * 
 * @property color The color of the generated vehicles.
 * @property generationSpeed The speed or rate at which vehicles are generated.
 * @property initialVehicleSpeed The speed that vehicles have when they get generated.
 * @property vehiclesToSpawn How many vehicles this configuration should spawn for level completion.
 * @property vehiclesSpawned How many vehicles this configuration has already spawned.
 * @property vehiclesArrived How many spawned vehicles from this configuration have reached a target.
 * @property vehicleType The type of the generated vehicle.
 */
class SourceNodeConfiguration(
    val color: Color,
    val generationSpeed: Double,
    val initialVehicleSpeed: Double,
    var vehicleType: VehicleType,
    val vehiclesToSpawn: Int = GameSimulationConfig.getConfiguredVehiclesToSpawn(),
    var vehiclesSpawned: Int = 0,
    var vehiclesArrived: Int = 0
)
