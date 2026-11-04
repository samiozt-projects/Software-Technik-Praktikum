package audio

import domain.WeatherCondition

/**
 * Supported vehicle driving loop variants.
 *
 * @property soundPath Resource path of the loop asset.
 * @property gain Mixer gain used to balance the source asset.
 */
internal enum class DrivingSoundType(internal val soundPath: String, internal val gain: Double) {
    /** Passenger car driving loop. */
    CAR("audio/driving_car.wav", 0.90),

    /** Truck driving loop. */
    TRUCK("audio/driving_truck.wav", 0.60),

    /** Motorcycle driving loop. */
    MOTORCYCLE("audio/driving_motorcycle.wav", 0.80)
}

/**
 * Supported ambient weather loops.
 *
 * @property soundPath Resource path of the ambient asset.
 * @property volume Playback volume for this weather condition.
 */
internal enum class WeatherSoundType(internal val soundPath: String, internal val volume: Double) {
    /** Bird ambience for sunny or dry weather. */
    SUNNY("audio/weather_sunny_birds.wav", 0.065),

    /** Rain ambience for wet weather. */
    RAIN("audio/weather_rain.mp3", 0.085),

    /** Wind ambience for snowy winter weather. */
    WINTER("audio/weather_winter_wind.mp3", 0.075);

    companion object {
        /**
         * Maps a level weather condition to its ambient sound loop.
         */
        fun forCondition(weatherCondition: WeatherCondition): WeatherSoundType {
            return when (weatherCondition) {
                WeatherCondition.DRY -> SUNNY
                WeatherCondition.WET -> RAIN
                WeatherCondition.SNOWY -> WINTER
            }
        }
    }
}

/**
 * Holds speed-weighted driving intensity totals for the current frame.
 *
 * @property carIntensity Intensity sum for car sounds.
 * @property truckIntensity Intensity sum for truck sounds.
 * @property motorcycleIntensity Intensity sum for motorcycle sounds.
 * @property movingVehicleCount Number of vehicles contributing to the intensity map.
 */
internal data class MovingVehicleSoundIntensities(
    val carIntensity: Double,
    val truckIntensity: Double,
    val motorcycleIntensity: Double,
    val movingVehicleCount: Int
) {
    /** Combined intensity for all supported vehicle sounds. */
    val totalIntensity: Double
        get() = carIntensity + truckIntensity + motorcycleIntensity

    /** Returns the intensity assigned to [type]. */
    fun intensityFor(type: DrivingSoundType): Double {
        return when (type) {
            DrivingSoundType.CAR -> carIntensity
            DrivingSoundType.TRUCK -> truckIntensity
            DrivingSoundType.MOTORCYCLE -> motorcycleIntensity
        }
    }
}
