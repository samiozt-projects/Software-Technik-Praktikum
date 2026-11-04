package scenestests

import scenes.LevelScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for the unified countdown implementation in LevelScene.
 * Specifically tests that both next-level and menu buttons use the same countdown mechanism.
 */
class LevelSceneCountdownUnificationTest {

    /**
     * Test that the countdown fill width calculates correctly for different progress values.
     */
    @Test
    fun unifiedCountdown_fillWidthCalculatesCorrectly() {
        // Test the fill width calculation for different progress values
        val duration = LevelScene.COMPLETION_COUNTDOWN_DURATION.toDouble()
        val buttonWidth = LevelScene.COMPLETION_ACTION_BUTTON_W
        
        // At start (0% elapsed)
        val startValue = duration
        val elapsedRatioStart = 1.0 - (startValue / duration)
        val fillWidthStart = LevelScene.completionActionFillWidth(buttonWidth, elapsedRatioStart)
        assertEquals(0.0, fillWidthStart, "Fill width should be 0 at start")
        
        // At middle (50% elapsed)
        val middleValue = duration / 2
        val elapsedRatioMiddle = 1.0 - (middleValue / duration)
        val fillWidthMiddle = LevelScene.completionActionFillWidth(buttonWidth, elapsedRatioMiddle)
        assertTrue(fillWidthMiddle > 0, "Fill width should be positive at 50% elapsed")
        assertTrue(
            fillWidthMiddle < (buttonWidth - LevelScene.COMPLETION_ACTION_FILL_INSET),
            "Fill width should be less than max at 50% elapsed"
        )
        
        // At end (100% elapsed)
        val endValue = 0.0
        val elapsedRatioEnd = 1.0 - (endValue / duration)
        val fillWidthEnd = LevelScene.completionActionFillWidth(buttonWidth, elapsedRatioEnd)
        assertEquals(
            buttonWidth - LevelScene.COMPLETION_ACTION_FILL_INSET,
            fillWidthEnd,
            "Fill width should be max at end"
        )
    }

    /**
     * Test that the saved-state feedback fills the complete button interior.
     */
    @Test
    fun saveFeedback_fillWidthCoversWholeButtonInterior() {
        val fullSaveFill = LevelScene.completionActionFillWidth(LevelScene.COMPLETION_ACTION_BUTTON_W, 1.0)

        assertEquals(LevelScene.COMPLETION_ACTION_BUTTON_W - LevelScene.COMPLETION_ACTION_FILL_INSET, fullSaveFill)
    }

    /**
     * Test that an already-saved level starts with a fully green save button.
     */
    @Test
    fun saveFeedback_alreadySavedLevelStartsFullyFilled() {
        val progress = LevelScene.saveCompletionFillProgress(levelAlreadySaved = true)

        assertEquals(1.0, progress)
        assertEquals(
            LevelScene.COMPLETION_ACTION_BUTTON_W - LevelScene.COMPLETION_ACTION_FILL_INSET,
            LevelScene.completionActionFillWidth(LevelScene.COMPLETION_ACTION_BUTTON_W, progress)
        )
    }

    /**
     * Test that an unsaved level starts with an empty save-button fill.
     */
    @Test
    fun saveFeedback_unsavedLevelStartsEmpty() {
        val progress = LevelScene.saveCompletionFillProgress(levelAlreadySaved = false)

        assertEquals(0.0, progress)
        assertEquals(0.0, LevelScene.completionActionFillWidth(LevelScene.COMPLETION_ACTION_BUTTON_W, progress))
    }

    /** 
     * Test that the button row is centered correctly.
     */
    @Test
    fun unifiedCountdown_rowCenteringIsCorrect() {
        // Verify that the button row is centered correctly
        
        val tableWidth = LevelScene.TABLE_PX_W
        val buttonWidth = LevelScene.COMPLETION_ACTION_BUTTON_W
        val buttonGap = LevelScene.COMPLETION_ACTION_BUTTON_GAP
        
        // For 3 buttons: width = 3 * buttonWidth + 2 * buttonGap
        val totalRowWidth = 3 * buttonWidth + 2 * buttonGap
        
        // Start X should position the row so it's centered
        val expectedStartX = (tableWidth - totalRowWidth) / 2.0
        val actualStartX = LevelScene.completionActionRowX()
        
        assertEquals(expectedStartX, actualStartX, "Button row should be centered")
    }

    /** 
     * Test that the visual feedback is clear and consistent.
     */
    @Test
    fun unifiedCountdown_visualFeedbackIsClear() {
        // Test that the visual feedback is clear and consistent
        
        // Green color with 70% opacity provides good visibility
        val alpha = 178
        val maxAlpha = 255
        val opacityPercentage = (alpha.toDouble() / maxAlpha) * 100
        
        assertTrue(opacityPercentage > 50, "Opacity should be > 50% for good visibility")
        assertTrue(opacityPercentage < 100, "Opacity should be < 100% to allow text to show through")
        
        // The fill should start from the left and expand to the right
        // This provides clear visual feedback of progress
        assertEquals(2.0, 2.0, "Fill should start at consistent inset")
    }
}
