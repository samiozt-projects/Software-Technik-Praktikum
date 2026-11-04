package audiotests

import audio.SimulationSoundInput
import audio.SimulationSoundPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the pure sound decision logic without touching the audio device.
 */
class SimulationSoundPolicyTest {

    /**
     * Ensures the driving loop stays silent when no vehicle is moving.
     */
    @Test
    fun noMovingVehiclesProducesNoDrivingSound() {
        val decision = SimulationSoundPolicy().evaluate(
            SimulationSoundInput(
                simulationRunning = true,
                movingVehicleCount = 0,
                movingVehicleIntensitySum = 0.0,
                congestionDetected = false
            ),
            elapsedSeconds = 0.1
        )

        assertFalse(decision.playDrivingLoop)
        assertEquals(0.0, decision.drivingVolume)
        assertFalse(decision.playHorn)
    }

    /**
     * Ensures moving vehicles share one capped driving loop.
     */
    @Test
    fun movingVehiclesUseOneBoundedDrivingLoop() {
        val policy = SimulationSoundPolicy()

        val oneVehicle = policy.evaluate(
            SimulationSoundInput(
                simulationRunning = true,
                movingVehicleCount = 1,
                movingVehicleIntensitySum = 1.0,
                congestionDetected = false
            ),
            elapsedSeconds = 0.1
        )
        val manyVehicles = policy.evaluate(
            SimulationSoundInput(
                simulationRunning = true,
                movingVehicleCount = 100,
                movingVehicleIntensitySum = 100.0,
                congestionDetected = false
            ),
            elapsedSeconds = 0.1
        )

        assertTrue(oneVehicle.playDrivingLoop)
        assertEquals(SimulationSoundPolicy.BASE_DRIVING_VOLUME, oneVehicle.drivingVolume)
        assertEquals(SimulationSoundPolicy.MAX_DRIVING_VOLUME, manyVehicles.drivingVolume)
    }

    /**
     * Ensures paused playback mutes the driving loop.
     */
    @Test
    fun pausedSimulationStopsDrivingLoopEvenWithMovingVehicles() {
        val decision = SimulationSoundPolicy().evaluate(
            SimulationSoundInput(
                simulationRunning = false,
                movingVehicleCount = 3,
                movingVehicleIntensitySum = 3.0,
                congestionDetected = false
            ),
            elapsedSeconds = 0.1
        )

        assertFalse(decision.playDrivingLoop)
        assertEquals(0.0, decision.drivingVolume)
        assertFalse(decision.playHorn)
    }

    /**
     * Ensures slower moving vehicles produce a lower driving-loop volume.
     */
    @Test
    fun slowerMovingVehiclesProduceLowerDrivingVolume() {
        val policy = SimulationSoundPolicy()

        val slowVehicle = policy.evaluate(
            SimulationSoundInput(
                simulationRunning = true,
                movingVehicleCount = 1,
                movingVehicleIntensitySum = SimulationSoundPolicy.MIN_MOVING_VEHICLE_INTENSITY,
                congestionDetected = false
            ),
            elapsedSeconds = 0.1
        )
        val fastVehicle = policy.evaluate(
            SimulationSoundInput(
                simulationRunning = true,
                movingVehicleCount = 1,
                movingVehicleIntensitySum = 1.0,
                congestionDetected = false
            ),
            elapsedSeconds = 0.1
        )

        assertTrue(slowVehicle.playDrivingLoop)
        assertTrue(fastVehicle.drivingVolume > slowVehicle.drivingVolume)
    }

    /**
     * Ensures congestion plays one horn when it is first detected.
     */
    @Test
    fun congestionPlaysOneHornOnRisingEdge() {
        val policy = SimulationSoundPolicy()

        val firstCongestedFrame = policy.evaluate(
            SimulationSoundInput(
                simulationRunning = false,
                movingVehicleCount = 0,
                movingVehicleIntensitySum = 0.0,
                congestionDetected = true
            ),
            elapsedSeconds = 0.1
        )
        val continuingCongestion = policy.evaluate(
            SimulationSoundInput(
                simulationRunning = false,
                movingVehicleCount = 0,
                movingVehicleIntensitySum = 0.0,
                congestionDetected = true
            ),
            elapsedSeconds = 1.0
        )

        assertTrue(firstCongestedFrame.playHorn)
        assertFalse(continuingCongestion.playHorn)
    }

    /**
     * Ensures a later separate congestion can trigger another horn.
     */
    @Test
    fun separateCongestionCanHornAgainAfterReset() {
        val policy = SimulationSoundPolicy()

        assertTrue(evaluateCongestionHorn(policy, congestionDetected = true, elapsedSeconds = 0.0))
        policy.evaluate(
            SimulationSoundInput(
                simulationRunning = false,
                movingVehicleCount = 0,
                movingVehicleIntensitySum = 0.0,
                congestionDetected = false
            ),
            elapsedSeconds = 0.0
        )
        assertTrue(evaluateCongestionHorn(policy, congestionDetected = true, elapsedSeconds = 0.0))
    }

    /**
     * Ensures an already congested scene entry does not replay the horn.
     */
    @Test
    fun primedCongestionDoesNotHornOnSceneEntry() {
        val policy = SimulationSoundPolicy()
        policy.primeCongestionState(true)

        val decision = policy.evaluate(
            SimulationSoundInput(
                simulationRunning = false,
                movingVehicleCount = 0,
                movingVehicleIntensitySum = 0.0,
                congestionDetected = true
            ),
            elapsedSeconds = 0.1
        )

        assertFalse(decision.playHorn)
    }

    private fun evaluateCongestionHorn(
        policy: SimulationSoundPolicy,
        congestionDetected: Boolean,
        elapsedSeconds: Double
    ): Boolean {
        return policy.evaluate(
            SimulationSoundInput(
                simulationRunning = false,
                movingVehicleCount = 0,
                movingVehicleIntensitySum = 0.0,
                congestionDetected = congestionDetected
            ),
            elapsedSeconds = elapsedSeconds
        ).playHorn
    }
}
