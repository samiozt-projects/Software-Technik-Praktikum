package domaintests

import domain.Level
import domain.WeatherCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the weather-to-adhesion mapping used by level physics.
 */
class WeatherConditionTest {

    /**
     * Verifies that dry weather keeps the existing standard driving behavior unchanged.
     */
    @Test
    fun effectiveAdhesionCoefficientModifier_dryKeepsBaseModifier() {
        val level = Level("Dry", 1, adhesionCoefficientModifier = 1.0, weatherCondition = WeatherCondition.DRY)

        assertEquals(1.0, level.effectiveAdhesionCoefficientModifier())
    }

    /**
     * Verifies that wet and snowy weather lower the effective adhesion in the expected order.
     */
    @Test
    fun effectiveAdhesionCoefficientModifier_wetAndSnowyReduceAdhesion() {
        val dry = Level("Dry", 1, weatherCondition = WeatherCondition.DRY)
        val wet = Level("Wet", 1, weatherCondition = WeatherCondition.WET)
        val snowy = Level("Snowy", 1, weatherCondition = WeatherCondition.SNOWY)

        assertTrue(wet.effectiveAdhesionCoefficientModifier() < dry.effectiveAdhesionCoefficientModifier())
        assertTrue(snowy.effectiveAdhesionCoefficientModifier() < wet.effectiveAdhesionCoefficientModifier())
    }

    /**
     * Verifies that weather stacks with existing level-specific adhesion tuning.
     */
    @Test
    fun effectiveAdhesionCoefficientModifier_combinesLevelAndWeatherModifiers() {
        val level = Level("Tuned wet", 1, adhesionCoefficientModifier = 0.8, weatherCondition = WeatherCondition.WET)

        assertEquals(0.8 * WeatherCondition.WET.adhesionMultiplier, level.effectiveAdhesionCoefficientModifier())
    }

    /**
     * Verifies that malformed level tuning values cannot produce negative or NaN physics inputs.
     */
    @Test
    fun effectiveAdhesionCoefficientModifier_rejectsInvalidBaseModifiers() {
        val negative = Level("Negative", 1, adhesionCoefficientModifier = -1.0, weatherCondition = WeatherCondition.DRY)
        val nan = Level("NaN", 1, adhesionCoefficientModifier = Double.NaN, weatherCondition = WeatherCondition.DRY)
        val infinite = Level("Infinite", 1, adhesionCoefficientModifier = Double.POSITIVE_INFINITY, weatherCondition = WeatherCondition.DRY)

        assertEquals(0.0, negative.effectiveAdhesionCoefficientModifier())
        assertEquals(0.0, nan.effectiveAdhesionCoefficientModifier())
        assertEquals(0.0, infinite.effectiveAdhesionCoefficientModifier())
    }
}
