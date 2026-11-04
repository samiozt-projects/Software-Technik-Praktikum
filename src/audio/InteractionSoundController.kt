package audio

import korlibs.audio.sound.PlaybackParameters
import korlibs.audio.sound.Sound
import korlibs.audio.sound.SoundChannel
import korlibs.audio.sound.readSound
import korlibs.io.file.std.resourcesVfs
import korlibs.korge.input.onClick
import korlibs.korge.view.View
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.CoroutineContext

/**
 * Short one-shot sounds used for direct UI feedback and visible countdowns.
 *
 * The controller is deliberately fail-soft: if audio support or a single file is unavailable, the
 * action still runs and only the matching effect is muted.
 */
object InteractionSoundController {
    private val sounds: MutableMap<InteractionSoundEffect, Sound> = mutableMapOf()
    private val activeTimerChannels: MutableMap<InteractionSoundEffect, SoundChannel> = mutableMapOf()
    private val lastPlayedAtMillis: MutableMap<InteractionSoundEffect, Long> = mutableMapOf()
    private var playbackContext: CoroutineContext? = null

    /** Global switch used by tests or future settings screens to mute interaction sounds. */
    var enabled: Boolean = true

    /**
     * Loads all interaction sound effects and stores the playback context used for later clicks.
     */
    suspend fun loadSounds(playbackContext: CoroutineContext) {
        this.playbackContext = playbackContext
        for (effect in InteractionSoundEffect.entries) {
            loadSoundOrNull(effect.soundPath)?.let { sounds[effect] = it }
        }
    }

    /**
     * Plays a short effect once. Missing or disabled sounds are ignored.
     */
    fun play(effect: InteractionSoundEffect) {
        if (!enabled) return
        if (!effect.canReplayNow()) return
        val context = playbackContext ?: return
        val sound = sounds[effect] ?: return
        try {
            if (effect.isTimer) {
                activeTimerChannels.remove(effect)?.stop()
            }
            val channel = sound.play(context, PlaybackParameters(volume = effect.volume))
            if (effect.isTimer) {
                activeTimerChannels[effect] = channel
            }
            lastPlayedAtMillis[effect] = System.currentTimeMillis()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            sounds.remove(effect)
            println("Warning: Interaction sound '${effect.soundPath}' disabled: ${e.message}")
        }
    }

    /**
     * Stops an active timer effect, if it is still playing.
     */
    fun stop(effect: InteractionSoundEffect) {
        activeTimerChannels.remove(effect)?.stop()
    }

    /**
     * Stops all long-running countdown feedback.
     */
    fun stopTimers() {
        activeTimerChannels.values.forEach { it.stop() }
        activeTimerChannels.clear()
    }

    private fun InteractionSoundEffect.canReplayNow(): Boolean {
        if (replayDelayMillis <= 0L) return true
        val now = System.currentTimeMillis()
        val lastPlayed = lastPlayedAtMillis[this] ?: return true
        return now - lastPlayed >= replayDelayMillis
    }

    /**
     * Loads one interaction sound and returns null when the asset cannot be used.
     *
     * @param path resource path of the sound asset.
     * @return loaded sound, or null when loading failed.
     */
    private suspend fun loadSoundOrNull(path: String): Sound? {
        return try {
            resourcesVfs[path].readSound()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            println("Warning: Failed to load interaction sound '$path': ${e.message}")
            null
        }
    }
}

/**
 * Supported interaction effects with conservative per-asset volumes.
 */
enum class InteractionSoundEffect(
    internal val soundPath: String,
    internal val volume: Double,
    internal val isTimer: Boolean = false,
    internal val replayDelayMillis: Long = 0L
) {
    /** Default button click feedback. */
    CLICK("audio/ui_click.wav", 0.18),

    /** Destructive, blocked, or warning-like button feedback. */
    ERROR("audio/ui_error.wav", 0.16, replayDelayMillis = 220L),

    /** Positive feedback for completed levels and successful save/create actions. */
    SUCCESS("audio/ui_success_chime.mp3", 0.20, replayDelayMillis = 220L),

    /** Full three-second recognition/start countdown effect. */
    TIMER_3S("audio/ui_timer_3s.wav", 0.18, isTimer = true),

    /** Full fifteen-second automatic transition countdown effect. */
    TIMER_15S("audio/ui_timer_15s.wav", 0.15, isTimer = true)
}

/**
 * Adds click handling with the standard interaction sound.
 */
fun View.onClickWithSound(
    effect: InteractionSoundEffect = InteractionSoundEffect.CLICK,
    handler: () -> Unit
) {
    onClick {
        InteractionSoundController.play(effect)
        handler()
    }
}

/**
 * Adds click handling whose sound depends on whether the action succeeded.
 */
fun View.onClickWithResultSound(handler: () -> Boolean) {
    onClick {
        val succeeded = handler()
        InteractionSoundController.play(
            if (succeeded) InteractionSoundEffect.CLICK else InteractionSoundEffect.ERROR
        )
    }
}

/**
 * Adds click handling that only plays an immediate sound for blocked/error results.
 *
 * Successful actions can play their own feedback later, after asynchronous work actually succeeds.
 */
fun View.onClickWithErrorSound(handler: () -> Boolean) {
    onClick {
        val succeeded = handler()
        if (!succeeded) {
            InteractionSoundController.play(InteractionSoundEffect.ERROR)
        }
    }
}

/**
 * Adds click handling without immediate feedback.
 *
 * Use this when the action itself will play success/error feedback after asynchronous work finishes.
 */
fun View.onClickWithoutSound(handler: () -> Unit) {
    onClick {
        handler()
    }
}
