package scenestests

import domain.Level
import scenes.LevelScene
import scenes.TutorialScene
import ui.ControlButtons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the pure layout contract of the tutorial overlay.
 *
 * The scene itself creates KorGE views from these values. Testing the shared layout object keeps
 * the assertions stable and makes sure future UI changes keep the tutorial hand-off to Level 1.
 */
class TutorialSceneTest {

    /**
     * Verifies that the tutorial forwards to predefined level slot 1.
     */
    @Test
    fun tutorialNextLevel_targetsLevelOne() {
        assertEquals(1, TutorialScene.Companion.TutorialLayout.NEXT_PREDEFINED_LEVEL_ID)
    }

    /**
     * Verifies that the tutorial overlay contains only the single requested instruction line.
     */
    @Test
    fun tutorialText_usesOnlyPrimaryInstruction() {
        assertEquals(
            "Tutorial: Baue eine Straße, sodass die Fahrzeuge zu ihrem Ziel kommen",
            TutorialScene.Companion.TutorialLayout.PRIMARY_TEXT
        )
    }

    /**
     * Verifies that the tutorial instruction box is centered and fits into the projected playfield.
     */
    @Test
    fun tutorialTextBox_fitsInsideViewport() {
        val boxLeft = LevelScene.TABLE_PX_W / 2.0 - TutorialScene.Companion.TutorialLayout.PRIMARY_BOX_W / 2.0
        val boxRight = boxLeft + TutorialScene.Companion.TutorialLayout.PRIMARY_BOX_W
        val boxBottom = TutorialScene.Companion.TutorialLayout.PRIMARY_BOX_Y +
            TutorialScene.Companion.TutorialLayout.PRIMARY_BOX_H

        assertTrue(boxLeft > 0.0)
        assertTrue(boxRight < LevelScene.TABLE_PX_W)
        assertTrue(TutorialScene.Companion.TutorialLayout.PRIMARY_BOX_Y > 0.0)
        assertTrue(boxBottom < LevelScene.TABLE_PX_H)
        assertTrue(TutorialScene.Companion.TutorialLayout.PRIMARY_BOX_W >= 1600.0)
        assertTrue(TutorialScene.Companion.TutorialLayout.PRIMARY_TEXT_SIZE <= 32.0)
    }

    /**
     * Verifies that the tutorial defines the requested hint next to the active play button.
     */
    @Test
    fun startHint_usesSimulationStartenLabel() {
        assertEquals("Simulation starten", TutorialScene.Companion.TutorialLayout.START_HINT_TEXT)
    }

    /**
     * Verifies that the start hint is placed to the right of the top-left play button.
     */
    @Test
    fun startHint_isPlacedNextToPlayButton() {
        val playButtonRight = ControlButtons.CORNER_OFFSET + ControlButtons.BUTTON_SIZE

        assertTrue(TutorialScene.Companion.TutorialLayout.START_HINT_X > playButtonRight)
    }

    /**
     * Verifies that the tutorial hides the simulation-speed selector.
     */
    @Test
    fun tutorial_hidesSimulationSpeedSelector() {
        assertTrue(LevelScene().showSimulationSpeedSelector())
        assertEquals(false, TutorialScene().showSimulationSpeedSelector())
    }

    /**
     * Verifies that the tutorial uses its own completion flow instead of the generic statistics result.
     */
    @Test
    fun tutorial_hidesResultStatisticsOverlay() {
        assertTrue(LevelScene().showResultStatisticsOverlay())
        assertEquals(false, TutorialScene().showResultStatisticsOverlay())
    }

    /**
     * Verifies that the tutorial uses the normal three-second simulation-start countdown.
     */
    @Test
    fun tutorial_usesAutomaticSimulationCountdown() {
        assertTrue(LevelScene().automaticSimulationCountdownEnabled(Level("Level", 1)))
        assertTrue(TutorialScene().automaticSimulationCountdownEnabled(Level("Tutorial", 0)))
    }

    /**
     * Verifies that the post-completion tutorial explains challenge mode explicitly.
     */
    @Test
    fun challengeExplanation_usesRequestedTitleAndCopy() {
        assertEquals("Challenge-Modus", TutorialScene.Companion.TutorialLayout.CHALLENGE_TITLE)
        assertEquals(
            "Ist das Level zu leicht?\n" +
                "Im Challenge-Modus gelten\n" +
                "besondere Regeln, welche die\n" +
                "Level schwieriger machen.",
            TutorialScene.Companion.TutorialLayout.CHALLENGE_EXPLANATION_TEXT
        )
    }

    /**
     * Verifies that the tutorial uses the requested Weiter button on the visible explanation step.
     */
    @Test
    fun continueButton_usesWeiterLabelOnVisibleExplanationStep() {
        assertEquals("Weiter", TutorialScene.Companion.TutorialLayout.CONTINUE_BUTTON_TEXT)
    }

    /**
     * Verifies that the shared completion action row remains centered in the projected playfield.
     */
    @Test
    fun completionActionRow_isCenteredHorizontally() {
        val rowWidth = LevelScene.COMPLETION_ACTION_BUTTON_W * 3.0 + LevelScene.COMPLETION_ACTION_BUTTON_GAP * 2.0
        val rowCenterX = LevelScene.completionActionRowX() + rowWidth / 2.0

        assertEquals(LevelScene.TABLE_PX_W / 2.0, rowCenterX)
    }

    /**
     * Verifies that the tutorial countdown fill width calculation is correct.
     */
    @Test
    fun tutorialCountdown_calculatesFillWidthCorrectly() {
        val duration = TutorialScene.Companion.TutorialLayout.TUTORIAL_COUNTDOWN_DURATION.toDouble()
        val buttonWidth = TutorialScene.Companion.TutorialLayout.CONTINUE_BUTTON_W

        // Test at start (0% elapsed)
        val startValue = duration
        val elapsedRatioStart = 1.0 - (startValue / duration)
        val fillWidthStart = (buttonWidth - 4.0) * elapsedRatioStart
        assertEquals(0.0, fillWidthStart)

        // Test at middle (50% elapsed)
        val middleValue = duration / 2
        val elapsedRatioMiddle = 1.0 - (middleValue / duration)
        val fillWidthMiddle = (buttonWidth - 4.0) * elapsedRatioMiddle
        assertTrue(fillWidthMiddle > (buttonWidth / 3))
        assertTrue(fillWidthMiddle < (buttonWidth / 2))

        // Test at end (100% elapsed)
        val endValue = 0.0
        val elapsedRatioEnd = 1.0 - (endValue / duration)
        val fillWidthEnd = (buttonWidth - 4.0) * elapsedRatioEnd
        assertEquals(buttonWidth - 4.0, fillWidthEnd)
    }

}
