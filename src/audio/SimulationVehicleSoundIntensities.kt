package audio

import domain.Level
import domain.Vehicle
import simulation.Simulation

/**
 * Groups moving vehicles by sound type and sums their speed-based intensities.
 */
internal fun levelMovingVehicleIntensitiesBySoundType(level: Level): MovingVehicleSoundIntensities {
    var carIntensity = 0.0
    var truckIntensity = 0.0
    var motorcycleIntensity = 0.0
    var movingVehicleCount = 0
    for (vehicle in level.vehicles) {
        val intensity = drivingIntensityFor(vehicle)
        if (intensity <= 0.0) continue

        val soundType = drivingSoundTypeFor(vehicle) ?: continue
        when (soundType) {
            DrivingSoundType.CAR -> carIntensity += intensity
            DrivingSoundType.TRUCK -> truckIntensity += intensity
            DrivingSoundType.MOTORCYCLE -> motorcycleIntensity += intensity
        }
        movingVehicleCount++
    }
    return MovingVehicleSoundIntensities(
        carIntensity = carIntensity,
        truckIntensity = truckIntensity,
        motorcycleIntensity = motorcycleIntensity,
        movingVehicleCount = movingVehicleCount
    )
}

/**
 * Returns the audible intensity contribution of one moving vehicle.
 */
private fun drivingIntensityFor(vehicle: Vehicle): Double {
    if (vehicle.currentSpeed <= SimulationSoundPolicy.MOVING_SPEED_EPSILON_MS) return 0.0

    val maxSpeedMs = vehicle.type.maxSpeed * Simulation.KMH_TO_MS
    if (!maxSpeedMs.isFinite() || maxSpeedMs <= 0.0) return 1.0

    val speedRatio = (vehicle.currentSpeed / maxSpeedMs).coerceIn(0.0, 1.0)
    return SimulationSoundPolicy.MIN_MOVING_VEHICLE_INTENSITY +
        (1.0 - SimulationSoundPolicy.MIN_MOVING_VEHICLE_INTENSITY) * speedRatio
}

/**
 * Resolves a vehicle to the matching driving sound, if supported.
 */
private fun drivingSoundTypeFor(vehicle: Vehicle): DrivingSoundType? {
    return when (vehicle.type.graphicRef.substringAfterLast("/").lowercase()) {
        "car.png" -> DrivingSoundType.CAR
        "truck.png" -> DrivingSoundType.TRUCK
        "motorcycle.png" -> DrivingSoundType.MOTORCYCLE
        else -> null
    }
}
