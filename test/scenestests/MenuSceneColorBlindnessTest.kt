package scenestests

import domain.colorBlindnessModeActivated
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Provides lightweight contract tests for the color-blindness section in the pause menu.
 *
 * The implementation in MenuScene is UI-driven, so these tests focus on the stable
 * values and state transitions that are directly encoded in that section.
 */
class MenuSceneColorBlindnessTest {

    /**
     * Verifies that the global color-blindness mode starts disabled by default.
     */
    @Test
    fun colorBlindnessMode_isDisabledByDefault() {
        assertFalse(colorBlindnessModeActivated)
    }

    /**
     * Verifies that the global color-blindness mode flag can be toggled on and off.
     */
    @Test
    fun colorBlindnessMode_canBeToggled() {
        colorBlindnessModeActivated = false
        colorBlindnessModeActivated = !colorBlindnessModeActivated
        assertTrue(colorBlindnessModeActivated)

        colorBlindnessModeActivated = !colorBlindnessModeActivated
        assertFalse(colorBlindnessModeActivated)
    }
}
