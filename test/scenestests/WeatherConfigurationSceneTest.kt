package scenestests

import domain.Level
import domain.WeatherCondition
import scenes.LevelScene
import scenes.TutorialScene
import scenes.WeatherConfigurationScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the stable layout and state contract of the weather configuration scene.
 */
class WeatherConfigurationSceneTest {

    /**
     * Verifies that the weather panel is centered and fits inside the projection.
     */
    @Test
    fun panel_isCenteredInsideViewport() {
        val panelX = WeatherConfigurationScene.panelX()

        assertEquals(LevelScene.TABLE_PX_W / 2.0, panelX + WeatherConfigurationScene.PANEL_W / 2.0)
        assertTrue(panelX > 0.0)
        assertTrue(panelX + WeatherConfigurationScene.PANEL_W < LevelScene.TABLE_PX_W)
        assertTrue(WeatherConfigurationScene.PANEL_Y + WeatherConfigurationScene.PANEL_H < LevelScene.TABLE_PX_H)
    }

    /**
     * Verifies that the three weather options are laid out like the mockup row.
     */
    @Test
    fun optionTiles_areThreeCenteredNonOverlappingSquares() {
        val x0 = WeatherConfigurationScene.optionX(0)
        val x1 = WeatherConfigurationScene.optionX(1)
        val x2 = WeatherConfigurationScene.optionX(2)

        assertEquals(WeatherConfigurationScene.OPTION_SIZE + WeatherConfigurationScene.OPTION_GAP, x1 - x0)
        assertEquals(WeatherConfigurationScene.OPTION_SIZE + WeatherConfigurationScene.OPTION_GAP, x2 - x1)
        assertTrue(x0 > 0.0)
        assertTrue(x2 + WeatherConfigurationScene.OPTION_SIZE < WeatherConfigurationScene.PANEL_W)
    }

    /**
     * Verifies that invalid option indexes are rejected instead of producing broken coordinates.
     */
    @Test
    fun optionX_rejectsIndexesOutsideWeatherOptions() {
        assertFailsWith<IllegalArgumentException> {
            WeatherConfigurationScene.optionX(-1)
        }
        assertFailsWith<IllegalArgumentException> {
            WeatherConfigurationScene.optionX(WeatherCondition.entries.size)
        }
    }

    /**
     * Verifies that selecting weather updates the current level object.
     */
    @Test
    fun applyWeatherSelection_updatesLoadedLevel() {
        val level = Level("Weather", 1, weatherCondition = WeatherCondition.DRY)

        assertTrue(WeatherConfigurationScene.applyWeatherSelection(level, WeatherCondition.WET))

        assertEquals(WeatherCondition.WET, level.weatherCondition)
    }

    /**
     * Verifies that selecting weather is safe when no level is loaded.
     */
    @Test
    fun applyWeatherSelection_returnsFalseWithoutLevel() {
        assertFalse(WeatherConfigurationScene.applyWeatherSelection(null, WeatherCondition.SNOWY))
    }

    /**
     * Verifies that changing weather in the tutorial returns to the tutorial scene.
     */
    @Test
    fun returnsToTutorialScene_isTrueForTutorialLevel() {
        val tutorialLevel = Level("Tutorial", TutorialScene.Companion.TutorialLayout.LEVEL_NUMBER)

        assertTrue(WeatherConfigurationScene.returnsToTutorialScene(tutorialLevel))
    }

    /**
     * Verifies that normal levels return to the regular level scene.
     */
    @Test
    fun returnsToTutorialScene_isFalseForNormalLevels() {
        val level = Level("Level 1", 1)

        assertFalse(WeatherConfigurationScene.returnsToTutorialScene(level))
    }
}
