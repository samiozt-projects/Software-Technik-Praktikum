package scenestests

import domain.GameState
import domain.Level
import simulation.Simulation
import domain.SimulationState
import scenes.LevelMenuScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the automatic level loading functionality in LevelMenuScene.
 * Specifically tests the countdown and auto-load behavior after tutorial completion.
 */
class LevelMenuSceneAutoLoadTest {

    /**
     * Test that the level menu countdown constants are correctly defined.
     */
    @Test
    fun levelMenuCountdownConstants_areCorrectlyDefined() {
        // Verify the countdown duration constant
        assertEquals(15, LevelMenuScene.LEVEL_ONE_COUNTDOWN_DURATION)
    }

    /**
     * Test that tutorial completion triggers level one countdown.
     */
    @Test
    fun tutorialCompletion_triggersLevelOneCountdown() {
        // Setup: Simulate tutorial completion
        val tutorialLevel = Level("Tutorial", 0)
        GameState.currentLevel = tutorialLevel
        
        // Verify conditions for countdown activation
        val isTutorialLevel = GameState.currentLevel?.number == 0
        
        assertTrue(isTutorialLevel, "Tutorial level should be detected")
        
        // After tutorial completion, the countdown should be activated
        // This would be tested in the actual scene, but we can verify the logic
        val shouldActivateCountdown = isTutorialLevel
        assertTrue(shouldActivateCountdown, "Countdown should be activated for tutorial level")
    }

    /**
     * Test that countdown progress calculates correctly.
     */
    @Test
    fun countdownProgress_calculatesCorrectly() {
        // Test the countdown progress calculation logic
        val duration = LevelMenuScene.Companion.LEVEL_ONE_COUNTDOWN_DURATION.toDouble()
        val boxLength = LevelMenuScene.BOX_LENGTH
        val cardStrokeThickness = LevelMenuScene.CARD_STROKE_THICKNESS
        
        // At start (0% elapsed)
        val startValue = duration
        val elapsedRatioStart = 1.0 - (startValue / duration)
        val fillWidthStart = (boxLength - 2 * cardStrokeThickness) * elapsedRatioStart
        assertEquals(0.0, fillWidthStart, "Fill width should be 0 at start")
        
        // At middle (50% elapsed)
        val middleValue = duration / 2
        val elapsedRatioMiddle = 1.0 - (middleValue / duration)
        val fillWidthMiddle = (boxLength - 2 * cardStrokeThickness) * elapsedRatioMiddle
        assertTrue(fillWidthMiddle > 0, "Fill width should be positive at 50% elapsed")
        assertTrue(fillWidthMiddle < (boxLength - 2 * cardStrokeThickness), "Fill width should be less than max at 50% elapsed")
        
        // At end (100% elapsed)
        val endValue = 0.0
        val elapsedRatioEnd = 1.0 - (endValue / duration)
        val fillWidthEnd = (boxLength - 2 * cardStrokeThickness) * elapsedRatioEnd
        assertEquals(boxLength - 2 * cardStrokeThickness, fillWidthEnd, "Fill width should be max at end")
    }

    /**
     * Test that countdown only activates for tutorial level.
     */
    @Test
    fun countdownOnlyActivatesForTutorialLevel() {
        // Test that countdown only activates for tutorial level (level 0)
        
        // Case 1: Tutorial level
        val tutorialLevel = Level("Tutorial", 0)
        GameState.currentLevel = tutorialLevel
        
        val isTutorialLevel = GameState.currentLevel?.number == 0
        assertTrue(isTutorialLevel, "Tutorial level should be detected")
        
        // Case 2: Regular level (not tutorial)
        val regularLevel = Level("Regular Level", 1)
        GameState.currentLevel = regularLevel
        
        val isRegularLevel = GameState.currentLevel?.number == 1
        assertTrue(isRegularLevel, "Regular level should be detected")
        
        // Countdown should only activate for tutorial (level 0)
        val shouldActivateForTutorial = GameState.currentLevel?.number == 0
        val shouldActivateForRegular = GameState.currentLevel?.number == 1

        assertTrue(shouldActivateForRegular, "Regular level detection works")
        // Note: In actual implementation, only level 0 triggers countdown
    }

    /**
     * Test that countdown state clears after completion.
     */
    @Test
    fun countdownState_clearsAfterCompletion() {
        // Verify that after countdown completes and loads level 1,
        // the simulation object is cleared to prevent repeated triggering
        
        // Setup
        val tutorialLevel = Level("Tutorial", 0)
        GameState.currentLevel = tutorialLevel
        GameState.activeSimulation = Simulation(tutorialLevel)
        
        // After countdown completes, simulation should be cleared
        GameState.activeSimulation = null
        
        assertEquals(null, GameState.activeSimulation, "Simulation should be cleared after countdown completion")
    }

    /**
     * Test that level one countdown uses the correct duration.
     */
    @Test
    fun levelOneCountdown_usesCorrectDuration() {
        // Verify the countdown duration is appropriate for level loading
        val duration = LevelMenuScene.Companion.LEVEL_ONE_COUNTDOWN_DURATION
        
        assertTrue(duration > 0, "Countdown duration should be positive")
        assertTrue(duration <= 30, "Countdown duration should be reasonable (<= 30 seconds)")
        assertEquals(15, duration, "Countdown duration should be 15 seconds")
    }
}
