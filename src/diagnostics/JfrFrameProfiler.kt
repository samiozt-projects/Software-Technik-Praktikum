package diagnostics

import domain.GameState
import jdk.jfr.Event
import jdk.jfr.Label
import jdk.jfr.Name
import jdk.jfr.StackTrace
import korlibs.korge.view.Stage
import korlibs.korge.view.addUpdater

/**
 * JFR event emitted once per rendered frame while a Java Flight Recorder recording is active.
 *
 * Recording is started manually from a terminal against the running game process:
 * ```
 * jcmd                                          # find the PID of the game JVM
 * jcmd <pid> JFR.start name=fps                 # start recording
 * jcmd <pid> JFR.dump name=fps filename=fps.jfr # dump while still recording
 * jcmd <pid> JFR.stop name=fps                  # stop recording
 * jfr print --events simulation.Frame fps.jfr   # print one line per frame
 * ```
 * Average FPS over the whole recording is the number of `simulation.Frame` events divided by the
 * recording duration (both shown by `jfr summary fps.jfr`).
 *
 * Stack trace capture is disabled because the event fires every frame and the call site is always
 * the same stage updater.
 *
 * @property fps Instantaneous frames per second derived from the frame's real delta time.
 * @property simulationState Name of the current [domain.SimulationState], for filtering frames.
 */
@Name(JfrFrameEvent.NAME)
@Label("Simulation Frame")
@StackTrace(false)
class JfrFrameEvent : Event() {
    @Label("FPS")
    var fps: Double = 0.0

    @Label("Simulation State")
    var simulationState: String = ""

    companion object {
        /** Event type name used with `jfr print --events` and in JMC filters. */
        const val NAME = "simulation.Frame"
    }
}

/**
 * Installs a stage-level updater that emits one [JfrFrameEvent] per rendered frame.
 *
 * Lives on the persistent stage so frames of every scene are captured across scene changes.
 * While no flight recording is running (or the event type is disabled in the recording settings)
 * the updater only performs the [Event.isEnabled] check, so it adds no measurable overhead to
 * normal gameplay.
 */
fun Stage.installJfrFrameLogging() {
    addUpdater { dt ->
        val deltaSeconds = dt.inWholeMicroseconds / 1_000_000.0
        if (deltaSeconds <= 0.0) return@addUpdater

        val event = JfrFrameEvent()
        if (!event.isEnabled) return@addUpdater
        event.fps = 1.0 / deltaSeconds
        event.simulationState = GameState.simulationState.name
        event.commit()
    }
}
