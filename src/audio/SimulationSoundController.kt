package audio

import domain.Level
import domain.SimulationState
import domain.WeatherCondition
import simulation.Simulation
import korlibs.audio.sound.PlaybackParameters
import korlibs.audio.sound.Sound
import korlibs.audio.sound.SoundChannel
import korlibs.audio.sound.infinitePlaybackTimes
import korlibs.audio.sound.readSound
import korlibs.io.file.std.resourcesVfs
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.CoroutineContext

/**
 * Owns the KorGE sound channels used by the level scene.
 *
 * The controller uses one driving channel per available vehicle sound type, one ambient weather
 * channel, and at most one horn channel. Channels are bound to the scene coroutine context, so
 * changing scenes stops playback.
 */
class SimulationSoundController private constructor(
    private val playbackContext: CoroutineContext,
    private val policy: SimulationSoundPolicy = SimulationSoundPolicy()
) {
    companion object {
        private const val HORN_SOUND_PATH = "audio/traffic_horn.wav"
        private const val HORN_VOLUME = 0.18

        /** Creates a controller without blocking scene construction on audio decoding. */
        fun create(playbackContext: CoroutineContext): SimulationSoundController =
            SimulationSoundController(playbackContext)

        /**
         * Loads one sound asset, returning null when audio support is unavailable.
         */
        private suspend fun loadSoundOrNull(path: String, warnOnFailure: Boolean = true): Sound? {
            return try {
                resourcesVfs[path].readSound()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (warnOnFailure) {
                    println("Warning: Failed to load simulation sound '$path': ${e.message}")
                }
                null
            }
        }
    }

    private val drivingSounds: MutableMap<DrivingSoundType, Sound> = mutableMapOf()
    private val weatherSounds: MutableMap<WeatherSoundType, Sound> = mutableMapOf()
    private var hornSound: Sound? = null
    private val drivingChannels: MutableMap<DrivingSoundType, SoundChannel> = mutableMapOf()
    private var weatherChannel: SoundChannel? = null
    private var activeWeatherSoundType: WeatherSoundType? = null
    private var hornChannel: SoundChannel? = null

    /**
     * Loads all effects asynchronously after the level scene is already visible.
     *
     * Missing or unsupported files disable only the corresponding effect.
     */
    suspend fun loadSounds() {
        for (type in DrivingSoundType.values()) {
            loadSoundOrNull(type.soundPath)?.let { drivingSounds[type] = it }
        }
        hornSound = loadSoundOrNull(HORN_SOUND_PATH)
        loadWeatherSounds()
    }

    /**
     * Prevents an already completed congestion event from producing another horn after scene entry.
     */
    fun primeCongestionState(congestionDetected: Boolean) {
        policy.primeCongestionState(congestionDetected)
    }

    /**
     * Updates sound playback from the current simulation state.
     */
    fun update(
        level: Level,
        simulation: Simulation?,
        simulationState: SimulationState,
        elapsedSeconds: Double
    ) {
        val simulationRunning = simulationState == SimulationState.SIMULATION
        if (simulationRunning) {
            updateWeatherLoop(level.weatherCondition)
        } else {
            stopWeatherLoop()
        }

        val movingIntensities = levelMovingVehicleIntensitiesBySoundType(level)
        val input = SimulationSoundInput(
            simulationRunning = simulationRunning,
            movingVehicleCount = movingIntensities.movingVehicleCount,
            movingVehicleIntensitySum = movingIntensities.totalIntensity,
            congestionDetected = simulation?.congestionDetected == true
        )
        apply(policy.evaluate(input, elapsedSeconds), movingIntensities)
    }

    /**
     * Stops all active effects immediately.
     */
    fun stopAll() {
        drivingChannels.values.forEach { it.stop() }
        weatherChannel?.stop()
        hornChannel?.stop()
        drivingChannels.clear()
        weatherChannel = null
        activeWeatherSoundType = null
        hornChannel = null
    }

    /**
     * Loads ambient loops for all supported weather conditions.
     */
    private suspend fun loadWeatherSounds() {
        for (type in WeatherSoundType.values()) {
            loadSoundOrNull(type.soundPath)?.let { weatherSounds[type] = it }
        }
    }

    private fun apply(
        decision: SimulationSoundDecision,
        movingVehicleIntensities: MovingVehicleSoundIntensities
    ) {
        updateDrivingLoops(decision, movingVehicleIntensities)
        if (decision.playHorn) {
            playHorn()
        }
    }

    /**
     * Keeps exactly one weather ambience loop active for the level's current weather.
     */
    private fun updateWeatherLoop(weatherCondition: WeatherCondition) {
        val soundType = WeatherSoundType.forCondition(weatherCondition)
        if (activeWeatherSoundType != soundType) {
            stopWeatherLoop()
            activeWeatherSoundType = soundType
        }

        val activeChannel = weatherChannel
        if (activeChannel == null || !activeChannel.state.playingOrPaused) {
            val sound = weatherSounds[soundType] ?: return
            val channel = tryPlaySound(
                sound = sound,
                parameters = PlaybackParameters(
                    times = infinitePlaybackTimes,
                    volume = soundType.volume
                ),
                onUnavailable = {
                    weatherSounds.remove(soundType)
                    stopWeatherLoop()
                }
            ) ?: return
            weatherChannel = channel
        } else {
            activeChannel.volume = soundType.volume
        }
    }

    /**
     * Stops the currently active weather ambience loop.
     */
    private fun stopWeatherLoop() {
        weatherChannel?.stop()
        weatherChannel = null
        activeWeatherSoundType = null
    }

    private fun updateDrivingLoops(
        decision: SimulationSoundDecision,
        movingVehicleIntensities: MovingVehicleSoundIntensities
    ) {
        if (!decision.playDrivingLoop) {
            stopDrivingLoops()
            return
        }

        val requestedVolumes = requestedDrivingVolumesByType(movingVehicleIntensities)
        val masterScale = masterVolumeScale(requestedVolumes.values.sum())
        for (type in DrivingSoundType.values()) {
            val requestedVolume = requestedVolumes[type] ?: 0.0
            if (requestedVolume <= 0.0) {
                drivingChannels.remove(type)?.stop()
                continue
            }

            val volume = requestedVolume * masterScale
            updateDrivingLoop(type, volume)
        }
    }

    private fun updateDrivingLoop(type: DrivingSoundType, volume: Double) {
        val activeChannel = drivingChannels[type]
        if (activeChannel == null || !activeChannel.state.playingOrPaused) {
            val sound = drivingSounds[type] ?: return
            val channel = tryPlaySound(
                sound = sound,
                parameters = PlaybackParameters(
                    times = infinitePlaybackTimes,
                    volume = volume
                ),
                onUnavailable = {
                    drivingSounds.remove(type)
                    drivingChannels.remove(type)?.stop()
                }
            ) ?: return
            drivingChannels[type] = channel
        } else {
            activeChannel.volume = volume
        }
    }

    /**
     * Calculates requested per-type loop volumes before applying the global cap.
     */
    private fun requestedDrivingVolumesByType(
        intensities: MovingVehicleSoundIntensities
    ): Map<DrivingSoundType, Double> {
        val requestedVolumes = mutableMapOf<DrivingSoundType, Double>()
        for (type in DrivingSoundType.values()) {
            val intensity = intensities.intensityFor(type)
            if (type !in drivingSounds || intensity <= 0.0) continue

            val rawVolume =
                SimulationSoundPolicy.BASE_DRIVING_VOLUME * intensity.coerceAtMost(1.0) +
                    (intensity - 1.0).coerceAtLeast(0.0) *
                    SimulationSoundPolicy.DRIVING_VOLUME_PER_EXTRA_VEHICLE
            requestedVolumes[type] =
                (rawVolume * type.gain).coerceAtMost(SimulationSoundPolicy.MAX_DRIVING_VOLUME)
        }
        return requestedVolumes
    }

    /**
     * Scales the mixed driving loops so their combined volume stays below the cap.
     */
    private fun masterVolumeScale(totalRequestedVolume: Double): Double {
        if (totalRequestedVolume <= SimulationSoundPolicy.MAX_DRIVING_VOLUME) return 1.0
        return SimulationSoundPolicy.MAX_DRIVING_VOLUME / totalRequestedVolume
    }

    private fun stopDrivingLoops() {
        drivingChannels.values.forEach { it.stop() }
        drivingChannels.clear()
    }

    private fun playHorn() {
        if (hornChannel?.state?.playingOrPaused == true) return
        val sound = hornSound ?: return
        hornChannel = tryPlaySound(
            sound = sound,
            parameters = PlaybackParameters(volume = HORN_VOLUME),
            onUnavailable = { hornSound = null }
        )
    }

    private fun tryPlaySound(
        sound: Sound,
        parameters: PlaybackParameters,
        onUnavailable: () -> Unit
    ): SoundChannel? {
        return try {
            sound.play(playbackContext, parameters)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            onUnavailable()
            println("Warning: Simulation sound playback disabled: ${e.message}")
            null
        }
    }

}
