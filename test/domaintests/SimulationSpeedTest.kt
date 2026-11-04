package domaintests

import domain.SimulationSpeed
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the user-selectable simulation speed contract.
 */
class SimulationSpeedTest {

    /**
     * Verifies that the UI exposes exactly the accepted speed multipliers in order.
     */
    @Test
    fun options_matchAcceptedSpeedMultipliers() {
        assertEquals(listOf(1.0, 2.0, 5.0, 10.0), SimulationSpeed.OPTIONS.map { it.multiplier })
        assertEquals(listOf("1x", "2x", "5x", "10x"), SimulationSpeed.OPTIONS.map { it.label })
    }

    /**
     * Verifies that unsupported raw values never become active simulation speeds.
     */
    @Test
    fun fromMultiplierOrDefault_rejectsUnsupportedValues() {
        assertEquals(SimulationSpeed.X1, SimulationSpeed.fromMultiplierOrDefault(0.0))
        assertEquals(SimulationSpeed.X1, SimulationSpeed.fromMultiplierOrDefault(-2.0))
        assertEquals(SimulationSpeed.X1, SimulationSpeed.fromMultiplierOrDefault(Double.NaN))
        assertEquals(SimulationSpeed.X1, SimulationSpeed.fromMultiplierOrDefault(Double.POSITIVE_INFINITY))
        assertEquals(SimulationSpeed.X1, SimulationSpeed.fromMultiplierOrDefault(3.0))
    }

    /**
     * Verifies that every accepted multiplier maps to its matching enum entry.
     */
    @Test
    fun fromMultiplierOrDefault_acceptsSupportedValues() {
        assertEquals(SimulationSpeed.X1, SimulationSpeed.fromMultiplierOrDefault(1.0))
        assertEquals(SimulationSpeed.X2, SimulationSpeed.fromMultiplierOrDefault(2.0))
        assertEquals(SimulationSpeed.X5, SimulationSpeed.fromMultiplierOrDefault(5.0))
        assertEquals(SimulationSpeed.X10, SimulationSpeed.fromMultiplierOrDefault(10.0))
    }

    /**
     * Verifies that cycling through speed options wraps from the last option to the first.
     */
    @Test
    fun next_wrapsAcrossAllOptions() {
        assertEquals(SimulationSpeed.X2, SimulationSpeed.X1.next())
        assertEquals(SimulationSpeed.X5, SimulationSpeed.X2.next())
        assertEquals(SimulationSpeed.X10, SimulationSpeed.X5.next())
        assertEquals(SimulationSpeed.X1, SimulationSpeed.X10.next())
    }

    /**
     * Verifies that speed multipliers convert real seconds into simulated seconds.
     */
    @Test
    fun scaledDeltaSeconds_appliesMultiplier() {
        assertEquals(0.5, SimulationSpeed.X1.scaledDeltaSeconds(0.5))
        assertEquals(1.0, SimulationSpeed.X2.scaledDeltaSeconds(0.5))
        assertEquals(2.5, SimulationSpeed.X5.scaledDeltaSeconds(0.5))
        assertEquals(5.0, SimulationSpeed.X10.scaledDeltaSeconds(0.5))
    }

    /**
     * Verifies that invalid frame deltas cannot produce invalid simulation time.
     */
    @Test
    fun scaledDeltaSeconds_rejectsInvalidFrameDeltas() {
        assertEquals(0.0, SimulationSpeed.X10.scaledDeltaSeconds(0.0))
        assertEquals(0.0, SimulationSpeed.X10.scaledDeltaSeconds(-1.0))
        assertEquals(0.0, SimulationSpeed.X10.scaledDeltaSeconds(Double.NaN))
        assertEquals(0.0, SimulationSpeed.X10.scaledDeltaSeconds(Double.POSITIVE_INFINITY))
    }
}
