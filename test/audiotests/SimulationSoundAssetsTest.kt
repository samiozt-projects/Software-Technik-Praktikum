package audiotests

import korlibs.audio.sound.readSound
import korlibs.io.file.std.resourcesVfs
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies that the simulation sound assets are valid audio files.
 */
class SimulationSoundAssetsTest {
    private val requiredSoundPaths = listOf(
        "audio/driving_car.wav",
        "audio/driving_truck.wav",
        "audio/driving_motorcycle.wav",
        "audio/traffic_horn.wav",
        "audio/weather_sunny_birds.wav",
        "audio/weather_rain.mp3",
        "audio/weather_winter_wind.mp3",
        "audio/ui_click.wav",
        "audio/ui_error.wav",
        "audio/ui_success_chime.mp3",
        "audio/ui_timer_3s.wav",
        "audio/ui_timer_15s.wav"
    )

    /**
     * Ensures required simulation sound files can be decoded by KorGE.
     */
    @Test
    fun simulationSoundFilesCanBeDecoded() = runBlocking {
        for (path in requiredSoundPaths) {
            val audioData = resourcesVfs[path].readSound().decode()
            assertTrue(audioData.totalSamples > 0, "$path should contain decodable audio samples")
        }
    }
}
