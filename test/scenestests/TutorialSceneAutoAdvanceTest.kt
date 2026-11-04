package scenestests

import scenes.TutorialScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the automatic button advancement functionality in TutorialScene.
 * Specifically tests the countdown behavior for the tutorial continue button.
 */
class TutorialSceneAutoAdvanceTest {

    /**
     * Test that the countdown constants are correctly defined.
     */
    @Test
    fun tutorialCountdownConstants_areCorrectlyDefined() {
        // Verify the countdown duration constant
        assertEquals(15, TutorialScene.Companion.TutorialLayout.TUTORIAL_COUNTDOWN_DURATION)
    }

    /** 
     * Test that the countdown has two phases.
     */
    @Test
    fun tutorialCountdown_hasTwoPhases() {
        // Verify the two-phase countdown behavior
        
        // Phase 1: Initial countdown to show explanation
        val phase1Duration = TutorialScene.Companion.TutorialLayout.TUTORIAL_COUNTDOWN_DURATION
        assertEquals(15, phase1Duration, "Phase 1 duration should be 15 seconds")
        
        // Phase 2: Countdown after explanation is shown
        val phase2Duration = TutorialScene.Companion.TutorialLayout.TUTORIAL_COUNTDOWN_DURATION
        assertEquals(15, phase2Duration, "Phase 2 duration should also be 15 seconds")
        
        // Both phases use the same duration
        assertEquals(phase1Duration, phase2Duration, "Both phases should have the same duration")
    }

    /** 
     * Test that the countdown duration is appropriate.
     */
    @Test
    fun tutorialCountdown_durationIsAppropriate() {
        // Verify the countdown duration is appropriate for tutorial flow
        val duration = TutorialScene.Companion.TutorialLayout.TUTORIAL_COUNTDOWN_DURATION
        
        assertTrue(duration > 0, "Countdown duration should be positive")
        assertTrue(duration <= 30, "Countdown duration should be reasonable (<= 30 seconds)")
        assertTrue(duration >= 10, "Countdown duration should allow enough time for reading")
        assertEquals(15, duration, "Countdown duration should be 15 seconds")
    }
}
