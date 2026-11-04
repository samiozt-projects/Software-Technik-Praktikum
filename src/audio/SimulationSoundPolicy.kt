package audio

/**
 * Inputs needed to decide which simulation sounds should be active.
 *
 * @property simulationRunning Whether the simulation is currently playing.
 * @property movingVehicleCount Number of vehicles that should contribute to the driving loop.
 * @property movingVehicleIntensitySum Speed-weighted amount of moving vehicle sound.
 * @property congestionDetected Whether the simulation reported a terminal congestion.
 */
data class SimulationSoundInput(
    val simulationRunning: Boolean,
    val movingVehicleCount: Int,
    val movingVehicleIntensitySum: Double,
    val congestionDetected: Boolean
)

/**
 * Sound actions for one rendered frame.
 *
 * @property playDrivingLoop Whether the shared driving sound should run.
 * @property drivingVolume Volume for the shared driving sound.
 * @property playHorn Whether a congestion horn should start this frame.
 */
data class SimulationSoundDecision(
    val playDrivingLoop: Boolean,
    val drivingVolume: Double,
    val playHorn: Boolean
)

/**
 * Converts simulation state into bounded sound actions without accessing an audio device.
 */
class SimulationSoundPolicy {
    companion object {
        /** Vehicles below this speed are treated as stationary for driving sound. */
        const val MOVING_SPEED_EPSILON_MS: Double = 0.2

        /** Volume used for the first moving vehicle. */
        const val BASE_DRIVING_VOLUME: Double = 0.055

        /** Additional volume per further moving vehicle. */
        const val DRIVING_VOLUME_PER_EXTRA_VEHICLE: Double = 0.012

        /** Hard cap for the shared driving loop. */
        const val MAX_DRIVING_VOLUME: Double = 0.16

        /** Minimum audible intensity for a vehicle that is moving slowly. */
        const val MIN_MOVING_VEHICLE_INTENSITY: Double = 0.30
    }

    private var previousCongestionDetected = false

    /**
     * Produces sound actions for [input].
     *
     * [elapsedSeconds] is kept in the signature because the controller updates this policy once per
     * frame, but the current horn logic only reacts to congestion state changes.
     */
    @Suppress("UNUSED_PARAMETER")
    fun evaluate(input: SimulationSoundInput, elapsedSeconds: Double): SimulationSoundDecision {
        val movingVehicleCount = input.movingVehicleCount.coerceAtLeast(0)
        val movingVehicleIntensitySum =
            if (movingVehicleCount > 0) input.movingVehicleIntensitySum.coerceAtLeast(0.0) else 0.0
        val playDrivingLoop =
            input.simulationRunning && movingVehicleCount > 0 && movingVehicleIntensitySum > 0.0
        val drivingVolume = if (playDrivingLoop) {
            val requestedVolume =
                BASE_DRIVING_VOLUME * movingVehicleIntensitySum.coerceAtMost(1.0) +
                    (movingVehicleIntensitySum - 1.0).coerceAtLeast(0.0) * DRIVING_VOLUME_PER_EXTRA_VEHICLE
            requestedVolume.coerceAtMost(MAX_DRIVING_VOLUME)
        } else {
            0.0
        }

        val playHorn = input.congestionDetected && !previousCongestionDetected
        previousCongestionDetected = input.congestionDetected

        return SimulationSoundDecision(playDrivingLoop, drivingVolume, playHorn)
    }

    /**
     * Synchronizes the initial congestion state without triggering a horn.
     */
    fun primeCongestionState(congestionDetected: Boolean) {
        previousCongestionDetected = congestionDetected
    }
}
