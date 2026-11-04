package ui

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.Text
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.text
import korlibs.korge.view.xy
import korlibs.math.geom.vector.StrokeInfo
import ui.Assets.bitcountSingle



/**
 * Common UI utility methods for scenes.
 * Contains methods used by multiple scenes.
 */
object SceneUIUtils {
    /** Appended to text shortened by [truncateWithEllipsis]. */
    private const val ELLIPSIS = "…"

    /**
     * Shortens [label]'s text in place, appending [ELLIPSIS], until it renders within [maxWidth].
     *
     * Re-measures [label]'s actual rendered width after each attempt instead of assuming a fixed
     * character width, so this works regardless of font or string content — needed for names whose
     * length is not bounded by validation (e.g. server-provided world IDs, which can be much longer
     * than a fixed-size label was designed for).
     *
     * @param label the already-created text view to shrink; its `text` is mutated in place.
     * @param maxWidth available width in scene pixels.
     */
    fun truncateWithEllipsis(label: Text, maxWidth: Double) {
        if (label.width <= maxWidth) return
        val original = label.text
        if (original.isEmpty()) return

        var lowerBound = 0
        var upperBound = original.length
        var bestFit = ELLIPSIS
        while (lowerBound <= upperBound) {
            val length = (lowerBound + upperBound) / 2
            val candidate = if (length == 0) ELLIPSIS else "${original.take(length)}$ELLIPSIS"
            label.text = candidate
            if (label.width <= maxWidth) {
                bestFit = candidate
                lowerBound = length + 1
            } else {
                upperBound = length - 1
            }
        }
        label.text = bestFit
    }

    /**
     * Draws a bordered text button.
     *
     * @param container The container to draw in.
     * @param xPos Left edge of the button.
     * @param yPos Top edge of the button.
     * @param width Button width.
     * @param height Button height.
     * @param label Button label.
     * @param textSize Font size.
     * @param disabled Whether the button is disabled.
     * @param strokeThickness Stroke thickness.
     * @return The button container.
     */
    fun drawBorderedTextButton(
        container: Container,
        xPos: Double,
        yPos: Double,
        width: Double,
        height: Double,
        label: String,
        textSize: Double,
        disabled: Boolean = false,
        strokeThickness: Double = 2.0,
        fillColor: RGBA = Colors.WHITE
    ): Container {
        return container.container {
            xy(xPos, yPos)
            // alpha = if (disabled) ControlButtons.DISABLED_ALPHA else 1.0
            graphics {
                fill(fillColor) {
                    rect(0.0, 0.0, width, height)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = strokeThickness)) {
                    rect(0.0, 0.0, width, height)
                }
            }
            text(
                label,
                textSize = textSize,
                color = Colors.BLACK,
                font = bitcountSingle
            ) {
                centerOn(this@container)
            }
        }
    }
}
