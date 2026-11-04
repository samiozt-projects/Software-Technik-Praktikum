package scenestests

import domain.NetworkState
import domain.SimulationSpeed
import domain.SimulationState
import scenes.LevelScene
import ui.ControlButtons
import ui.LevelActionButtonMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the state mapping behind the level action button.
 */
class ControlButtonsTest {

    /**
     * Verifies that incomplete networks expose a disabled start button.
     */
    @Test
    fun levelActionMode_incompleteNetwork_returnsDisabledStart() {
        assertEquals(
            LevelActionButtonMode.DISABLED_START,
            ControlButtons.levelActionMode(NetworkState.INCOMPLETE, SimulationState.CONSTRUCTION)
        )
    }

    /**
     * Verifies that complete networks expose an active start button before running.
     */
    @Test
    fun levelActionMode_completeNetworkBeforeSimulation_returnsStart() {
        assertEquals(
            LevelActionButtonMode.START,
            ControlButtons.levelActionMode(NetworkState.COMPLETE, SimulationState.CONSTRUCTION)
        )
    }

    /**
     * Verifies that running simulations expose a pause button.
     */
    @Test
    fun levelActionMode_runningSimulation_returnsPause() {
        assertEquals(
            LevelActionButtonMode.PAUSE,
            ControlButtons.levelActionMode(NetworkState.COMPLETE, SimulationState.SIMULATION)
        )
    }

    /**
     * Verifies that a paused complete simulation can be resumed via the start button.
     */
    @Test
    fun levelActionMode_pausedCompleteNetwork_returnsStart() {
        assertEquals(
            LevelActionButtonMode.START,
            ControlButtons.levelActionMode(NetworkState.COMPLETE, SimulationState.PAUSED)
        )
    }

    /**
     * Verifies that the challenge marker fits next to the gear button.
     */
    @Test
    fun challengeIndicator_sitsInsideViewportNextToGearButton() {
        val indicatorX = ControlButtons.challengeIndicatorX()
        val indicatorY = ControlButtons.challengeIndicatorY()
        val indicatorRight = indicatorX + ControlButtons.CHALLENGE_INDICATOR_SIZE
        val gearX = LevelScene.TABLE_PX_W - ControlButtons.BUTTON_SIZE - ControlButtons.CORNER_OFFSET

        assertTrue(indicatorX > 0.0)
        assertTrue(indicatorY > 0.0)
        assertEquals(ControlButtons.CHALLENGE_INDICATOR_GAP, gearX - indicatorRight)
    }

    /**
     * Verifies that the weather marker fits immediately left of the challenge marker.
     */
    @Test
    fun weatherIndicator_sitsInsideViewportLeftOfChallengeIndicator() {
        val weatherX = ControlButtons.weatherIndicatorX()
        val weatherY = ControlButtons.weatherIndicatorY()
        val weatherRight = weatherX + ControlButtons.WEATHER_INDICATOR_SIZE
        val challengeX = ControlButtons.challengeIndicatorX()

        assertTrue(weatherX > 0.0)
        assertTrue(weatherY > 0.0)
        assertEquals(ControlButtons.WEATHER_INDICATOR_GAP, challengeX - weatherRight)
    }

    /**
     * Verifies that the challenge marker uses the same footprint as the gear button.
     */
    @Test
    fun challengeIndicator_matchesGearButtonSize() {
        assertEquals(ControlButtons.BUTTON_SIZE, ControlButtons.CHALLENGE_INDICATOR_SIZE)
    }

    /**
     * Verifies that the weather marker uses the same footprint as the other round top controls.
     */
    @Test
    fun weatherIndicator_matchesGearButtonSize() {
        assertEquals(ControlButtons.BUTTON_SIZE, ControlButtons.WEATHER_INDICATOR_SIZE)
    }

    /**
     * Verifies that the shared back button keeps the mockup-style top-left layout.
     */
    @Test
    fun backButtonLayout_isInsideTopLeftViewport() {
        val buttonRight = ControlButtons.BACK_BUTTON_X + ControlButtons.BACK_BUTTON_W
        val buttonBottom = ControlButtons.CORNER_OFFSET + ControlButtons.BUTTON_SIZE

        assertEquals(40.0, ControlButtons.BACK_BUTTON_X)
        assertTrue(ControlButtons.BACK_BUTTON_W > ControlButtons.BUTTON_SIZE)
        assertTrue(ControlButtons.BACK_BUTTON_TEXT_SIZE < ControlButtons.BUTTON_SIZE)
        assertTrue(buttonRight < LevelScene.TABLE_PX_W)
        assertTrue(buttonBottom < LevelScene.TABLE_PX_H)
    }

    /**
     * Verifies that the speed selector sits next to the start button and stays inside the viewport.
     */
    @Test
    fun speedSelectorLayout_sitsNextToStartButtonInsideViewport() {
        val selectorX = ControlButtons.speedSelectorX()
        val selectorY = ControlButtons.speedSelectorY()
        val selectorRight = selectorX + ControlButtons.SPEED_SELECTOR_W
        val selectorBottom = selectorY + ControlButtons.SPEED_SELECTOR_H

        assertEquals(
            ControlButtons.CORNER_OFFSET + ControlButtons.BUTTON_SIZE + ControlButtons.SPEED_SELECTOR_GAP,
            selectorX
        )
        assertEquals(ControlButtons.CORNER_OFFSET, selectorY)
        assertTrue(selectorRight < ControlButtons.challengeIndicatorX())
        assertTrue(selectorBottom < LevelScene.TABLE_PX_H)
    }

    /**
     * Verifies that every supported speed gets one equal-width selector segment.
     */
    @Test
    fun speedSelectorSegmentWidth_matchesSupportedOptions() {
        val expectedWidth = ControlButtons.SPEED_SELECTOR_W / SimulationSpeed.OPTIONS.size

        assertEquals(expectedWidth, ControlButtons.speedSelectorSegmentWidth())
        assertTrue(ControlButtons.speedSelectorSegmentWidth() > 0.0)
    }
}
