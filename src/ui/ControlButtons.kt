package ui

import domain.NetworkState
import domain.SimulationSpeed
import domain.SimulationState
import domain.WeatherCondition
import korlibs.image.bitmap.Bitmap32
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import audio.onClickWithSound
import korlibs.korge.view.Container
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.image
import korlibs.korge.view.position
import korlibs.korge.view.size
import korlibs.korge.view.text
import korlibs.korge.view.xy
import korlibs.math.geom.Point
import scenes.LevelScene
import korlibs.math.geom.vector.StrokeInfo

/**
 * Describes which action the left top level button currently represents.
 */
enum class LevelActionButtonMode {
    /** The street network is incomplete, so the simulation cannot be started. */
    DISABLED_START,

    /** The street network is complete and the simulation can be started or resumed. */
    START,

    /** The simulation is running and the button pauses it. */
    PAUSE
}

/**
 * Provides shared drawing and state helpers for the projected control buttons.
 */
object ControlButtons {
    /** Pixel size used for all small level and menu controls. */
    const val BUTTON_SIZE = LevelScene.MENU_BUTTON_PX

    /** Top and side offset for controls that sit in a viewport corner. */
    const val CORNER_OFFSET = 20.0

    /** Tint color for active controls. */
    val ACTIVE_COLOR: RGBA = Colors.BLACK

    /** Tint color for disabled controls. */
    val DISABLED_COLOR: RGBA = Colors["#9a9a9a"]

    /** Opacity used to make black source icons visibly disabled on white backgrounds. */
    const val DISABLED_ALPHA = 0.45

    /**
     * Width of the rectangular back button used in menu-like configuration scenes.
     */
    const val BACK_BUTTON_W = 180.0

    /**
     * Left offset of the rectangular back button from the projected viewport edge.
     */
    const val BACK_BUTTON_X = 40.0

    /**
     * Font size for the text label inside the rectangular back button.
     */
    const val BACK_BUTTON_TEXT_SIZE = 32.0

    /**
     * Stroke thickness used by bordered text-based control buttons.
     */
    const val BORDER_STROKE_THICKNESS = 3.0

    /** Size of the challenge-mode marker shown next to the gear button. */
    const val CHALLENGE_INDICATOR_SIZE = BUTTON_SIZE

    /** Gap between the challenge-mode marker and the gear button. */
    const val CHALLENGE_INDICATOR_GAP = 14.0

    /** Size of the weather marker shown left of the challenge-mode marker. */
    const val WEATHER_INDICATOR_SIZE = BUTTON_SIZE

    /** Gap between the weather marker and the challenge-mode marker. */
    const val WEATHER_INDICATOR_GAP = CHALLENGE_INDICATOR_GAP

    /** Yellow used for the challenge-mode lightning marker. */
    val CHALLENGE_INDICATOR_COLOR: RGBA = Colors["#FFD21E"]

    /** View name used by tests and tooling for the active challenge indicator. */
    const val CHALLENGE_INDICATOR_ACTIVE_NAME = "challenge-indicator-active"

    /** View name used by tests and tooling for the inactive challenge indicator. */
    const val CHALLENGE_INDICATOR_INACTIVE_NAME = "challenge-indicator-inactive"

    /** Blassweiße Füllung für runde Overlay-Buttons über detailreichen Hintergründen. */
    val ROUND_BUTTON_BACKGROUND: RGBA = RGBA(255, 255, 255, 180)
    /** Width of the full simulation-speed selector. */
    const val SPEED_SELECTOR_W = 300.0

    /** Height of the simulation-speed selector. */
    const val SPEED_SELECTOR_H = BUTTON_SIZE

    /** Gap between the start/pause action and the speed selector. */
    const val SPEED_SELECTOR_GAP = 20.0

    /** Font size used inside speed selector segments. */
    const val SPEED_SELECTOR_TEXT_SIZE = 28.0

    /** Diameter of each circular speed option. */
    const val SPEED_OPTION_DIAMETER = BUTTON_SIZE

    /** Fill color for the currently active speed circle. */
    val SPEED_SELECTOR_ACTIVE_FILL: RGBA = Colors.WHITE

    /** Fill color for inactive speed circles. */
    val SPEED_SELECTOR_INACTIVE_FILL: RGBA = Colors["#D0D0D0"]

    /** Border color for the currently active speed circle. */
    val SPEED_SELECTOR_ACTIVE_BORDER: RGBA = Colors.BLACK

    /** Border color for inactive speed circles. */
    val SPEED_SELECTOR_INACTIVE_BORDER: RGBA = Colors["#8A8A8A"]

    /**
     * Returns the mode for the left top level action button from domain state.
     */
    fun levelActionMode(networkState: NetworkState, simulationState: SimulationState): LevelActionButtonMode {
        return when {
            simulationState == SimulationState.SIMULATION -> LevelActionButtonMode.PAUSE
            networkState == NetworkState.COMPLETE -> LevelActionButtonMode.START
            else -> LevelActionButtonMode.DISABLED_START
        }
    }

    /**
     * Draws a start or pause button into [parent] at the top left viewport position.
     */
    fun drawLevelActionButton(parent: Container, mode: LevelActionButtonMode): Container {
        return parent.container {
            position(CORNER_OFFSET, CORNER_OFFSET)
            drawRoundButtonBackground(BUTTON_SIZE)
            when (mode) {
                LevelActionButtonMode.DISABLED_START -> drawControlIcon(
                    Assets.startSimulation,
                    DISABLED_COLOR,
                    DISABLED_ALPHA
                )
                LevelActionButtonMode.START -> drawControlIcon(Assets.startSimulation, ACTIVE_COLOR)
                LevelActionButtonMode.PAUSE -> drawControlIcon(Assets.menu, ACTIVE_COLOR)
            }
        }
    }

    /**
     * Draws the selectable simulation-speed control and highlights [selectedSpeed].
     *
     * @param parent Container that receives the selector.
     * @param selectedSpeed currently active simulation speed.
     * @param enabled whether the selector accepts speed changes.
     * @param onSpeedSelected called when the player selects one of the supported options.
     * @return The selector container.
     */
    fun drawSimulationSpeedSelector(
        parent: Container,
        selectedSpeed: SimulationSpeed,
        enabled: Boolean = true,
        onSpeedSelected: (SimulationSpeed) -> Unit
    ): Container {
        return parent.container {
            position(speedSelectorX(), speedSelectorY())
            val segmentWidth = speedSelectorSegmentWidth()
            SimulationSpeed.OPTIONS.forEachIndexed { index, speed ->
                val selected = speed == selectedSpeed
                val segment = container {
                    position(index * segmentWidth, 0.0)
                    graphics {
                        val center = Point(SPEED_OPTION_DIAMETER / 2.0, SPEED_OPTION_DIAMETER / 2.0)
                        val radius = SPEED_OPTION_DIAMETER / 2.0
                        fill(if (selected) SPEED_SELECTOR_ACTIVE_FILL else SPEED_SELECTOR_INACTIVE_FILL) {
                            circle(center, radius)
                        }
                        stroke(
                            if (selected) SPEED_SELECTOR_ACTIVE_BORDER else SPEED_SELECTOR_INACTIVE_BORDER,
                            StrokeInfo(thickness = BORDER_STROKE_THICKNESS)
                        ) {
                            circle(center, radius)
                        }
                    }
                    val textColor = Colors.BLACK
                    text(speed.label, textSize = SPEED_SELECTOR_TEXT_SIZE, color = textColor, font = Assets.bitcountSingle) {
                        centerOn(this@container)
                    }
                    alpha = if (enabled) 1.0 else DISABLED_ALPHA
                }
                if (enabled) {
                    segment.onClickWithSound {
                        onSpeedSelected(speed)
                    }
                }
            }
        }
    }

    /**
     * Draws the gear button that opens the pause menu from the level scene.
     */
    fun drawGearButton(parent: Container): Container {
        return parent.container {
            position(LevelScene.TABLE_PX_W - BUTTON_SIZE - CORNER_OFFSET, CORNER_OFFSET)
            drawRoundButtonBackground(BUTTON_SIZE)
            drawControlIcon(Assets.gear, ACTIVE_COLOR)
        }
    }

    /**
     * Draws the globe indicator button immediately to the left of the gear button.
     *
     * Used by [scenes.WorldScene] to signal that the current level is a server world in multi-table
     * mode. The button is purely informational; it carries no click action by default.
     *
     * @param parent container that receives the button.
     * @return the created button container.
     */
    fun drawGlobeButton(parent: Container): Container {
        return parent.container {
            position(LevelScene.TABLE_PX_W - 2 * BUTTON_SIZE - 2 * CORNER_OFFSET, CORNER_OFFSET)
            drawRoundButtonBackground(BUTTON_SIZE)
            drawControlIcon(Assets.globus, ACTIVE_COLOR)
        }
    }

    /**
     * Draws the challenge-mode marker immediately next to the gear button.
     */
    fun drawChallengeModeIndicator(parent: Container, active: Boolean): Container {
        return parent.container {
            name = challengeIndicatorName(active)
            xy(challengeIndicatorX(), challengeIndicatorY())
            val lightningColor = if (active) CHALLENGE_INDICATOR_COLOR else Colors.WHITE
            drawChallengeIconGraphic(CHALLENGE_INDICATOR_SIZE, ROUND_BUTTON_BACKGROUND, lightningColor)
        }
    }

    /**
     * Draws the graphic representation of the challenge mode icon (a lightning bolt inside a circle).
     *
     * @param size The total size of the icon.
     * @param backgroundColor Color for the inner circle.
     * @param lightningColor Color for the inner lightning bolt.
     */
    fun Container.drawChallengeIconGraphic(size: Double, backgroundColor: RGBA, lightningColor: RGBA) {
        graphics {
            val center = Point(size / 2.0, size / 2.0)
            val radius = size / 2.0 - 2.0
            fill(backgroundColor) {
                circle(center, radius)
            }
            stroke(Colors.BLACK, lineWidth = 3.0) {
                circle(center, radius)
            }
            fill(Colors.BLACK) {
                polygon(
                    Point(size * 0.60, size * 0.12),
                    Point(size * 0.28, size * 0.54),
                    Point(size * 0.48, size * 0.54),
                    Point(size * 0.36, size * 0.88),
                    Point(size * 0.74, size * 0.42),
                    Point(size * 0.53, size * 0.42)
                )
            }
            fill(lightningColor) {
                polygon(
                    Point(size * 0.58, size * 0.18),
                    Point(size * 0.36, size * 0.50),
                    Point(size * 0.56, size * 0.50),
                    Point(size * 0.46, size * 0.76),
                    Point(size * 0.66, size * 0.46),
                    Point(size * 0.47, size * 0.46)
                )
            }
        }
    }

    /**
     * Returns the stable view name for the challenge indicator state.
     */
    fun challengeIndicatorName(active: Boolean): String {
        return if (active) CHALLENGE_INDICATOR_ACTIVE_NAME else CHALLENGE_INDICATOR_INACTIVE_NAME
    }

    /**
     * Draws the weather marker immediately left of the challenge marker.
     *
     * @param parent container that receives the marker.
     * @param weatherCondition weather condition currently selected for the level.
     * @return the created marker container, ready for click handling by the caller.
     */
    fun drawWeatherIndicator(parent: Container, weatherCondition: WeatherCondition): Container {
        return parent.container {
            xy(weatherIndicatorX(), weatherIndicatorY())
            graphics {
                val center = Point(WEATHER_INDICATOR_SIZE / 2.0, WEATHER_INDICATOR_SIZE / 2.0)
                val radius = WEATHER_INDICATOR_SIZE / 2.0 - 2.0
                fill(ROUND_BUTTON_BACKGROUND) {
                    circle(center, radius)
                }
                stroke(Colors.BLACK, lineWidth = 3.0) {
                    circle(center, radius)
                }
            }
            drawWeatherIcon(
                this,
                weatherCondition,
                WEATHER_INDICATOR_SIZE * 0.62,
                Point(WEATHER_INDICATOR_SIZE / 2.0, WEATHER_INDICATOR_SIZE / 2.0)
            )
        }
    }

    /**
     * Draws the close button used by menu scenes to return to their previous screen.
     */
    fun drawCloseButton(parent: Container): Container {
        return parent.container {
            position(LevelScene.TABLE_PX_W - BUTTON_SIZE - CORNER_OFFSET, CORNER_OFFSET)
            drawControlIcon(Assets.close, ACTIVE_COLOR)
        }
    }

    /**
     * Draws the mockup-style rectangular back button in the top-left corner.
     *
     * The returned container only contains the visual button. Callers attach the
     * concrete navigation behavior with [audio.onClickWithSound], so the
     * shared UI component stays independent from scene-specific transitions.
     *
     * @param parent Container that receives the back button.
     * @return The button container, positioned and ready for click handling.
     */
    fun drawBackButton(parent: Container): Container {
        return parent.container {
            position(BACK_BUTTON_X, CORNER_OFFSET)
            graphics {
                fill(Colors.WHITE) {
                    rect(0.0, 0.0, BACK_BUTTON_W, BUTTON_SIZE)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = BORDER_STROKE_THICKNESS)) {
                    rect(0.0, 0.0, BACK_BUTTON_W, BUTTON_SIZE)
                }
            }
            text("zurück", textSize = BACK_BUTTON_TEXT_SIZE, color = Colors.BLACK, font = Assets.bitcountSingle) {
                centerOn(this@container)
            }
        }
    }

    /**
     * Returns the left x-position of the challenge-mode indicator.
     */
    fun challengeIndicatorX(): Double {
        return LevelScene.TABLE_PX_W - BUTTON_SIZE - CORNER_OFFSET - CHALLENGE_INDICATOR_GAP - CHALLENGE_INDICATOR_SIZE
    }

    /**
     * Returns the top y-position of the challenge-mode indicator.
     */
    fun challengeIndicatorY(): Double {
        return CORNER_OFFSET + (BUTTON_SIZE - CHALLENGE_INDICATOR_SIZE) / 2.0
    }

    /**
     * Returns the left x-position of the weather indicator.
     */
    fun weatherIndicatorX(): Double {
        return challengeIndicatorX() - WEATHER_INDICATOR_GAP - WEATHER_INDICATOR_SIZE
    }

    /**
     * Returns the top y-position of the weather indicator.
     */
    fun weatherIndicatorY(): Double {
        return CORNER_OFFSET + (BUTTON_SIZE - WEATHER_INDICATOR_SIZE) / 2.0
    }

    /**
     * Returns the left x-position of the simulation-speed selector.
     */
    fun speedSelectorX(): Double {
        return CORNER_OFFSET + BUTTON_SIZE + SPEED_SELECTOR_GAP
    }

    /**
     * Returns the top y-position of the simulation-speed selector.
     */
    fun speedSelectorY(): Double = CORNER_OFFSET

    /**
     * Returns the fixed width of one selectable speed segment.
     */
    fun speedSelectorSegmentWidth(): Double {
        return SPEED_SELECTOR_W / SimulationSpeed.OPTIONS.size
    }

    /**
     * Draws one image-based control icon into the current button container.
     */
    private fun Container.drawControlIcon(bitmap: Bitmap32, tint: RGBA, opacity: Double = 1.0) {
        image(bitmap) {
            size(BUTTON_SIZE, BUTTON_SIZE)
            colorMul = tint
            alpha = opacity
        }
    }

    /**
     * Returns the bitmap used to visualize [weatherCondition].
     */
    fun weatherIcon(weatherCondition: WeatherCondition): Bitmap32 =
        when (weatherCondition) {
            WeatherCondition.DRY -> Assets.sun
            WeatherCondition.WET -> Assets.cloud
            WeatherCondition.SNOWY -> Assets.snowflake
        }

    /**
     * Draws a centered weather icon from the matching weather bitmap.
     *
     * @param parent container that receives the icon.
     * @param weatherCondition weather condition to visualize.
     * @param iconSize rendered square size of the icon.
     * @param center center point for the icon inside [parent].
     * @return container that groups the icon draw.
     */
    fun drawWeatherIcon(
        parent: Container,
        weatherCondition: WeatherCondition,
        iconSize: Double,
        center: Point
    ): Container {
        return parent.container {
            image(weatherIcon(weatherCondition)) {
                size(iconSize, iconSize)
                position(center.x - iconSize / 2.0, center.y - iconSize / 2.0)
            }
        }
    }

    /**
     * Draws the shared pale round background used below top-level overlay icons.
     */
    private fun Container.drawRoundButtonBackground(size: Double) {
        graphics {
            val center = Point(size / 2.0, size / 2.0)
            val radius = size / 2.0 - 2.0
            fill(ROUND_BUTTON_BACKGROUND) {
                circle(center, radius)
            }
        }
    }

}
