package scenes

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.annotations.KorgeExperimental
import korlibs.korge.ui.UITextInput
import korlibs.korge.ui.uiTextInput
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.Text
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.position
import korlibs.korge.view.text
import korlibs.math.geom.Size2D
import korlibs.math.geom.vector.StrokeInfo
import ui.Assets

/** Drawing helpers for [WorldEditScene]. */
internal object WorldEditSceneUi {
    /** Draws a form label vertically centered next to its input row. */
    fun SContainer.drawFormLabel(label: String, inputY: Double) {
        text(label, textSize = WorldModeUi.FORM_LABEL_TEXT_SIZE, color = Colors.BLACK, font = Assets.bitcountSingle) {
            position(WorldEditScene.FORM_LABEL_X, inputY + WorldModeUi.FORM_INPUT_H / 2.0 - height / 2.0)
        }
    }

    /** Draws one bordered text input and returns it so callers can read the edited value. */
    @OptIn(KorgeExperimental::class)
    fun Container.drawInputBox(
        xPos: Double,
        yPos: Double,
        initialText: String,
        editable: Boolean
    ): UITextInput {
        lateinit var input: UITextInput
        container {
            position(xPos, yPos)
            drawBorderedBox(WorldModeUi.FORM_INPUT_W, WorldModeUi.FORM_INPUT_H)
            input = uiTextInput(initialText, Size2D(WorldModeUi.FORM_INPUT_W - 22.0, WorldModeUi.FORM_INPUT_H - 16.0)) {
                position(11.0, 8.0)
                textSize = 32.0
                font = Assets.bitcountSingle
                enabled = editable
            }
            drawBoxBorder(WorldModeUi.FORM_INPUT_W, WorldModeUi.FORM_INPUT_H)
        }
        return input
    }

    /** Draws the initially hidden centered validation/server error text. */
    fun SContainer.drawMessageText(): Text {
        return text("", textSize = 25.0, color = WorldEditScene.ERROR_TEXT_COLOR, font = Assets.bitcountSingle) {
            WorldModeUi.setCenteredMessage(this, null, WorldEditScene.ERROR_TEXT_COLOR, LevelScene.TABLE_PX_W, WorldModeUi.MESSAGE_Y)
        }
    }

    /** Draws a centered text action button in the bottom action row. */
    fun SContainer.drawActionButton(
        xPos: Double,
        label: String,
        fillColor: RGBA = Colors.WHITE
    ): Container {
        return container {
            position(xPos, WorldModeUi.ACTION_BUTTON_Y)
            drawActionButtonContent(label, fillColor)
        }
    }

    /** Draws the visible button background and label inside an already positioned button container. */
    fun Container.drawActionButtonContent(label: String, fillColor: RGBA) {
        drawRoundedBorderedBox(WorldModeUi.ACTION_BUTTON_W, WorldModeUi.ACTION_BUTTON_H, WorldEditScene.ACTION_BUTTON_RADIUS, fillColor)
        text(label, textSize = 30.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
            centerOn(this@drawActionButtonContent)
        }
    }

    /** Draws a white rectangle with a black border. */
    fun Container.drawBorderedBox(width: Double, height: Double, fillColor: RGBA = Colors.WHITE) {
        graphics {
            fill(fillColor) {
                rect(0.0, 0.0, width, height)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = WorldModeUi.STROKE_THICKNESS)) {
                rect(0.0, 0.0, width, height)
            }
        }
    }

    /** Draws a rounded white rectangle with a black border for bottom action buttons. */
    fun Container.drawRoundedBorderedBox(width: Double, height: Double, radius: Double, fillColor: RGBA) {
        graphics {
            fill(fillColor) {
                roundRect(0.0, 0.0, width, height, radius, radius)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = WorldModeUi.STROKE_THICKNESS)) {
                roundRect(0.0, 0.0, width, height, radius, radius)
            }
        }
    }

    /** Draws only the border of a box, used to keep text inputs visibly framed above the UI widget. */
    fun Container.drawBoxBorder(width: Double, height: Double) {
        graphics {
            stroke(Colors.BLACK, StrokeInfo(thickness = WorldModeUi.STROKE_THICKNESS)) {
                rect(0.0, 0.0, width, height)
            }
        }
    }
}
