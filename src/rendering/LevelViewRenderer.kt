package rendering

import domain.GameState
import domain.SourceNode
import domain.TargetNode
import domain.WeatherCondition
import korlibs.image.bitmap.Bitmap32
import korlibs.korge.view.SContainer
import korlibs.korge.view.anchor
import korlibs.korge.view.container
import korlibs.korge.view.image
import korlibs.korge.view.position
import korlibs.korge.view.size
import korlibs.korge.view.solidRect
import korlibs.math.geom.degrees
import scenes.LevelScene
import ui.Assets
import kotlin.math.max

/**
 * Renderer for the level view, responsible for rendering the background, nodes, and vehicles.
*/
object LevelViewRenderer {

    /**
     * Returns the background image that visually represents [weatherCondition].
     */
    fun backgroundForWeather(weatherCondition: WeatherCondition): Bitmap32 =
        when (weatherCondition) {
            WeatherCondition.DRY -> Assets.sunnyLandscape
            WeatherCondition.WET -> Assets.wetLandscape
            WeatherCondition.SNOWY -> Assets.snowyLandscape
        }

    /**
     * Calculates cover-fit dimensions that fill the level viewport without changing aspect ratio.
     */
    fun coverSize(
        imageWidth: Double,
        imageHeight: Double,
        viewportWidth: Double = LevelScene.TABLE_PX_W,
        viewportHeight: Double = LevelScene.TABLE_PX_H
    ): Pair<Double, Double> {
        require(imageWidth > 0.0) { "Background image width must be positive." }
        require(imageHeight > 0.0) { "Background image height must be positive." }
        val scale = max(viewportWidth / imageWidth, viewportHeight / imageHeight)
        return imageWidth * scale to imageHeight * scale
    }

    /**
     * Renders the weather-dependent background as the lowest level visual.
     *
     * Call this before creating street, construction, and vehicle layers so the landscape cannot
     * cover gameplay elements.
     *
     * @param container scene container that receives the background image.
     * @param weatherCondition weather condition whose landscape should be shown.
     */
    fun renderBackground(container: SContainer, weatherCondition: WeatherCondition) {
        val background = backgroundForWeather(weatherCondition)
        val (backgroundWidth, backgroundHeight) = coverSize(background.width.toDouble(), background.height.toDouble())
        container.image(background) {
            size(backgroundWidth, backgroundHeight)
            position(
                (LevelScene.TABLE_PX_W - backgroundWidth) / 2.0,
                (LevelScene.TABLE_PX_H - backgroundHeight) / 2.0
            )
        }
    }

    /**
     * Renders all static level nodes above the street network.
     *
     * @param container scene container that receives the node sprites.
     */
    fun renderLevelNodes(container: SContainer) {
        val level = GameState.currentLevel
        if (level == null) {
            println(GameState.noLevelLoadedMessage("LevelViewRenderer"))
            return
        }

        /** Render each node using preloaded Assets */
        for (node in level.nodes) {
            when (node) {
                is SourceNode -> container.renderSourceNode(node)
                is TargetNode -> container.renderTargetNode(node)
                /** ConnectionNodes are not shown during the construction phase */
            }
        }
    }

    /**
     * Renders the weather-dependent level background and all static level nodes.
     */
    fun renderLevel(container: SContainer) {
        val level = GameState.currentLevel
        if (level == null) {
            println(GameState.noLevelLoadedMessage("LevelViewRenderer"))
            return
        }

        renderBackground(container, level.weatherCondition)
        renderLevelNodes(container)
    }

    /**
     * -----------------------------------------------------------------------
     * Node renderers
     * -----------------------------------------------------------------------
     */

    /**
     * Renders a [SourceNode] as a garage sprite with a directional exit arrow.
     * Both are centred on the node position and rotated to match
     * [SourceNode.orientation] (0° = facing up / +x axis).
     */
    private fun SContainer.renderSourceNode(node: SourceNode) {
        /** Translating in-game coordinates into virtual coordinates of screen size */
        val ax = GameAreaScale.virtualMetersToPixels(node.position.x)
        val ay = GameAreaScale.virtualMetersToPixels(node.position.y)

        /**
         * Builds the colored overlay that sits inside the source arrow.
         *
         * The stack is rotated together with the arrow so the color preview
         * keeps the same orientation as the rendered sprite.
         */
        val colorIndicator = container {
            position(ax, ay)
            rotation = node.orientation + 90.degrees
        }
        val stripeColors = sourceIndicatorColors(node)
        val stripeHeight = (LevelScene.LANE_W_PX - 4) / stripeColors.size
        /**
         * Add one equally sized stripe per emitted color so every color is
         * visible exactly once inside the source indicator.
         */
        for ((index, color) in stripeColors.withIndex()) {
            colorIndicator.solidRect(LevelScene.ARROW_L_PX - 4, stripeHeight, color.value) {
                position(
                    -LevelScene.ARROW_L_PX / 2 + 2,
                    -LevelScene.LANE_W_PX / 2 + 2 + index * stripeHeight,
                )
            }
        }

        /** Base source arrow sprite shown underneath the color overlay. */
        image(Assets.arrowStart) {
            size(LevelScene.LANE_W_PX,LevelScene.ARROW_L_PX)
            anchor(0.5, 0.5)
            position(ax, ay)
            rotation = node.orientation
        }
    }

    /**
     * Colors shown as stripes inside a source indicator: every configured color exactly once
     * (in first-appearance order), regardless of how many vehicle types share that color —
     * a source with three vehicle types per color must not repeat its colors three times.
     */
    internal fun sourceIndicatorColors(node: SourceNode): List<domain.Color> =
        node.configurations.map { it.color }.distinctBy { it.name.lowercase() }

    /**
     * Renders a [TargetNode] as a checkered-flag sprite with an arrival arrow.
     *
     * The arrival arrow is placed on the inbound side (orientation + 180°) and
     * rotated so it points toward the flag, showing players where to connect
     * the last street segment.
     */
    private fun SContainer.renderTargetNode(node: TargetNode) {
        /** Translating in-game coordinates into virtual coordinates of screen size */
        val ax = GameAreaScale.virtualMetersToPixels(node.position.x)
        val ay = GameAreaScale.virtualMetersToPixels(node.position.y)

        /**
         * Target nodes accept exactly one color, so a single centered rectangle
         * is enough to show the expected incoming vehicle color.
         */
        solidRect(LevelScene.ARROW_L_PX - 4, LevelScene.LANE_W_PX - 4 ,node.color.value) {
            anchor(0.5, 0.5)
            position(ax, ay)
            rotation = node.orientation + 90.degrees
        }

        /** Target arrow sprite drawn above the colored target indicator. */
        image(Assets.arrowEnd) {
            size(LevelScene.LANE_W_PX, LevelScene.ARROW_L_PX)
            anchor(0.5, 0.5)
            position(ax, ay)
            rotation = node.orientation
        }
    }
}
