package scenes

import domain.GameState
import domain.Level
import domain.WeatherCondition
import korlibs.image.color.Colors
import audio.onClickWithSound
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.text
import korlibs.korge.view.xy
import korlibs.math.geom.Point
import korlibs.math.geom.vector.StrokeInfo
import kotlinx.coroutines.launch
import rendering.LevelViewRenderer
import ui.Assets
import ui.ControlButtons

/**
 * Lets the player choose the weather condition for the currently loaded level.
 *
 * The scene follows the provided mockup: the level stays visible in the background, while a
 * centered white panel offers three large icon tiles for dry, wet, and snowy weather.
 */
class WeatherConfigurationScene : Scene() {

    companion object {
        /** Heading shown at the top of the weather selection panel. */
        const val TITLE_TEXT = "Wetter auswählen"

        /** Width of the centered weather selection panel. */
        const val PANEL_W = 820.0
        /** Height of the centered weather selection panel. */
        const val PANEL_H = 360.0
        /** Top y-position of the centered weather selection panel. */
        const val PANEL_Y = 294.0

        /** Width and height of each square weather option tile. */
        const val OPTION_SIZE = 220.0
        /** Horizontal gap between neighboring weather option tiles. */
        const val OPTION_GAP = 38.0
        /** Top y-position of the weather option row inside the panel. */
        const val OPTION_Y = 84.0
        /** Rendered size of the weather icon inside each option tile. */
        const val OPTION_ICON_SIZE = 132.0

        /** Text size for the panel heading. */
        const val TITLE_TEXT_SIZE = 42.0

        /** Border thickness for normal option tiles. */
        const val OPTION_BORDER_THICKNESS = 2.0
        /** Border thickness for the currently selected option tile. */
        const val SELECTED_OPTION_BORDER_THICKNESS = 6.0

        /**
         * Returns the left x-position of the centered weather panel.
         */
        fun panelX(): Double = LevelScene.TABLE_PX_W / 2.0 - PANEL_W / 2.0

        /**
         * Returns the x-position for an option tile by index.
         *
         * @param index option index in [WeatherCondition.entries].
         * @return tile x-position inside the panel.
         */
        fun optionX(index: Int): Double {
            require(index in WeatherCondition.entries.indices) { "Weather option index out of bounds: $index" }
            val rowWidth = OPTION_SIZE * WeatherCondition.entries.size + OPTION_GAP * (WeatherCondition.entries.size - 1)
            return PANEL_W / 2.0 - rowWidth / 2.0 + index * (OPTION_SIZE + OPTION_GAP)
        }

        /**
         * Applies a weather selection to [level].
         *
         * @param level current level, or null if no level is loaded.
         * @param weatherCondition selected weather condition.
         * @return true when a level was available and updated.
         */
        fun applyWeatherSelection(level: Level?, weatherCondition: WeatherCondition): Boolean {
            val currentLevel = level ?: return false
            currentLevel.weatherCondition = weatherCondition
            return true
        }

        /**
         * Returns whether weather selection should return to the tutorial-specific scene.
         *
         * @param level current level after weather selection.
         * @return true for the bundled tutorial level.
         */
        fun returnsToTutorialScene(level: Level): Boolean =
            level.number == TutorialScene.Companion.TutorialLayout.LEVEL_NUMBER
    }

    /**
     * Builds the weather configuration scene.
     */
    override suspend fun SContainer.sceneMain() {
        val level = GameState.levelForSceneOrRecover(
            this@WeatherConfigurationScene::class.simpleName ?: "WeatherConfigurationScene"
        ) ?: run {
            recoverToLevelSelection()
            return
        }

        LevelViewRenderer.renderLevel(this)
        drawWeatherPanel(level)
    }

    /**
     * Draws the weather selection panel and all option tiles.
     *
     * @param level level whose current weather is shown and updated.
     */
    private fun SContainer.drawWeatherPanel(level: Level) {
        container {
            xy(panelX(), PANEL_Y)
            drawPanelBackground()
            drawPanelTitle()
            drawWeatherOptions(level)
        }
    }

    /**
     * Draws the panel's white background and black border.
     */
    private fun Container.drawPanelBackground() {
        graphics {
            fill(Colors.WHITE) { rect(0.0, 0.0, PANEL_W, PANEL_H) }
            stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) { rect(0.0, 0.0, PANEL_W, PANEL_H) }
        }
    }

    /**
     * Draws the weather selection title inside the panel.
     */
    private fun Container.drawPanelTitle() {
        text(TITLE_TEXT, textSize = TITLE_TEXT_SIZE, color = Colors.BLACK, font = Assets.bitcountSingle).apply {
            centerXOn(this@drawPanelTitle)
            y = 18.0
        }
    }

    /**
     * Draws all weather options and wires their selection behavior.
     *
     * @param level level whose weather is changed by option clicks.
     */
    private fun Container.drawWeatherOptions(level: Level) {
        WeatherCondition.entries.forEachIndexed { index, weatherCondition ->
            drawWeatherOption(index, weatherCondition, weatherCondition == level.weatherCondition).onClickWithSound {
                applyWeatherSelection(level, weatherCondition)
                launch { returnToCallingScene(level) }
            }
        }
    }

    /**
     * Returns to the scene that opened the weather configuration.
     *
     * @param level level after the weather selection was applied.
     */
    private suspend fun returnToCallingScene(level: Level) {
        if (returnsToTutorialScene(level)) {
            sceneContainer.changeTo<TutorialScene>()
        } else {
            sceneContainer.changeTo<LevelScene>()
        }
    }

    /**
     * Draws one weather option tile.
     *
     * @param index tile index in the weather option row.
     * @param weatherCondition weather condition represented by the tile.
     * @param selected whether the tile represents the currently selected weather.
     * @return the created option container.
     */
    private fun Container.drawWeatherOption(
        index: Int,
        weatherCondition: WeatherCondition,
        selected: Boolean
    ): Container {
        return container {
            xy(optionX(index), OPTION_Y)
            drawWeatherOptionBorder(selected)
            ControlButtons.drawWeatherIcon(
                this,
                weatherCondition,
                OPTION_ICON_SIZE,
                Point(OPTION_SIZE / 2.0, OPTION_SIZE / 2.0)
            )
        }
    }

    /**
     * Draws the square option tile background and selection border.
     *
     * @param selected whether to use the selected border thickness.
     */
    private fun Container.drawWeatherOptionBorder(selected: Boolean) {
        val borderThickness = if (selected) SELECTED_OPTION_BORDER_THICKNESS else OPTION_BORDER_THICKNESS
        graphics {
            fill(Colors.WHITE) { rect(0.0, 0.0, OPTION_SIZE, OPTION_SIZE) }
            stroke(Colors.BLACK, StrokeInfo(thickness = borderThickness)) {
                rect(0.0, 0.0, OPTION_SIZE, OPTION_SIZE)
            }
        }
    }
}
