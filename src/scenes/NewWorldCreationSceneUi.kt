package scenes

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.annotations.KorgeExperimental
import audio.onClickWithErrorSound
import audio.onClickWithSound
import korlibs.korge.ui.UITextInput
import korlibs.korge.ui.uiTextInput
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.Text
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.clipContainer
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.position
import korlibs.korge.view.text
import korlibs.math.geom.Size
import korlibs.math.geom.Size2D
import korlibs.math.geom.vector.StrokeInfo
import ui.Assets
import ui.Scrollbar
import ui.attachScrollbarInput

/** Drawing helpers for [NewWorldCreationScene]. */
internal object NewWorldCreationSceneUi {
    /** Handles for form actions whose visuals need to be refreshed after validation changes. */
    data class BottomActionHandles(
        /** Button that submits the create-world form. */
        val createButton: Container
    )

    /** Draws one form label vertically centered next to its input row. */
    fun SContainer.drawFormLabel(label: String, inputY: Double) {
        text(
            label,
            textSize = NewWorldCreationScene.FORM_LABEL_TEXT_SIZE,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ) {
            position(
                NewWorldCreationScene.FORM_LABEL_X,
                inputY + NewWorldCreationScene.INPUT_H / 2.0 - height / 2.0
            )
        }
    }

    /** Draws one bordered editable text input. */
    @OptIn(KorgeExperimental::class)
    fun Container.drawInputBox(
        xPos: Double,
        yPos: Double,
        initialText: String
    ): UITextInput {
        lateinit var input: UITextInput
        container {
            position(xPos, yPos)
            drawBorderedBox(NewWorldCreationScene.INPUT_W, NewWorldCreationScene.INPUT_H)
            input = uiTextInput(
                initialText,
                Size2D(NewWorldCreationScene.INPUT_W - 22.0, NewWorldCreationScene.INPUT_H - 16.0)
            ) {
                position(11.0, 8.0)
                textSize = 32.0
                font = Assets.bitcountSingle
            }
            drawBoxBorder(NewWorldCreationScene.INPUT_W, NewWorldCreationScene.INPUT_H)
        }
        return input
    }

    /** Draws the visible level-selection field and returns its text handle. */
    fun SContainer.drawLevelField(initialLabel: String): Text {
        lateinit var labelText: Text
        container {
            position(NewWorldCreationScene.FORM_INPUT_X, NewWorldCreationScene.LEVEL_INPUT_Y)
            drawBorderedBox(NewWorldCreationScene.INPUT_W, NewWorldCreationScene.INPUT_H)
            labelText = text(initialLabel, textSize = 32.0, color = Colors.BLACK, font = Assets.bitcountSingle)
            positionLevelFieldText(labelText)
        }
        return labelText
    }

    /** Positions the selected level label inside its bordered field. */
    fun positionLevelFieldText(labelText: Text) {
        labelText.position(16.0, NewWorldCreationScene.INPUT_H / 2.0 - labelText.height / 2.0)
    }

    /** Draws the level-option dropdown below the level field. */
    fun SContainer.drawLevelDropdown(
        options: List<NewWorldCreationScene.WorldLevelOption>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ): Container {
        return container {
            position(NewWorldCreationScene.FORM_INPUT_X, NewWorldCreationScene.LEVEL_INPUT_Y + NewWorldCreationScene.INPUT_H + 6.0)
            drawLevelDropdownContent(options, selectedIndex, onSelected)
        }
    }

    private fun Container.drawLevelDropdownContent(
        options: List<NewWorldCreationScene.WorldLevelOption>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val viewportHeight = NewWorldCreationScene.levelDropdownViewportHeight(options.size)
        val contentHeight = NewWorldCreationScene.levelDropdownContentHeight(options.size)
        lateinit var rowsContainer: Container

        clipContainer(Size(NewWorldCreationScene.INPUT_W, viewportHeight)) {
            rowsContainer = container {
                options.forEachIndexed { index, option ->
                    drawLevelOptionRow(index, option, selectedIndex, onSelected)
                }
            }
        }
        drawLevelScrollbar(viewportHeight, contentHeight) { scrollY ->
            rowsContainer.y = scrollY
        }
    }

    private fun Container.drawLevelOptionRow(
        index: Int,
        option: NewWorldCreationScene.WorldLevelOption,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        container {
            position(0.0, index * NewWorldCreationScene.LEVEL_OPTION_H)
            val fill = if (index == selectedIndex) {
                NewWorldCreationScene.CREATE_BUTTON_FILL
            } else {
                NewWorldCreationScene.LEVEL_OPTION_FILL
            }
            drawBorderedBox(NewWorldCreationScene.INPUT_W, NewWorldCreationScene.LEVEL_OPTION_H, fill)
            text(option.label, textSize = 26.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
                position(16.0, NewWorldCreationScene.LEVEL_OPTION_H / 2.0 - height / 2.0)
            }
            onClickWithSound { onSelected(index) }
        }
    }

    private fun Container.drawLevelScrollbar(
        viewportHeight: Double,
        contentHeight: Double,
        onScroll: (Double) -> Unit
    ) {
        val scrollbar = Scrollbar(
            parent = this,
            x = NewWorldCreationScene.INPUT_W + NewWorldCreationScene.LEVEL_DROPDOWN_SCROLLBAR_GAP,
            y = 0.0,
            barHeight = viewportHeight,
            viewportHeight = viewportHeight,
            contentHeight = contentHeight,
            onScroll = onScroll
        )
        attachScrollbarInput(scrollbar, NewWorldCreationScene.LEVEL_DROPDOWN_SCROLL_SPEED)
    }

    /** Draws the initially hidden centered status/server error text. */
    fun SContainer.drawMessageText(): Text {
        return text("", textSize = 25.0, color = NewWorldCreationScene.ERROR_TEXT_COLOR, font = Assets.bitcountSingle) {
            centerXOn(this@drawMessageText)
            y = NewWorldCreationScene.MESSAGE_Y
            visible = false
        }
    }

    /** Draws and wires the bottom action row. */
    fun SContainer.drawBottomActions(
        createFillColor: RGBA,
        onCancel: () -> Unit,
        onCreate: () -> Boolean
    ): BottomActionHandles {
        val rowWidth = NewWorldCreationScene.ACTION_BUTTON_W * 2.0 + NewWorldCreationScene.ACTION_BUTTON_GAP
        val startX = LevelScene.TABLE_PX_W / 2.0 - rowWidth / 2.0

        drawActionButton(startX, NewWorldCreationScene.CANCEL_BUTTON_TEXT).onClickWithSound { onCancel() }
        val createButton = drawActionButton(
            startX + NewWorldCreationScene.ACTION_BUTTON_W + NewWorldCreationScene.ACTION_BUTTON_GAP,
            NewWorldCreationScene.CREATE_BUTTON_TEXT,
            createFillColor
        )
        createButton.onClickWithErrorSound { onCreate() }
        return BottomActionHandles(createButton)
    }

    /** Draws one bottom action button. */
    fun SContainer.drawActionButton(
        xPos: Double,
        label: String,
        fillColor: RGBA = Colors.WHITE
    ): Container {
        return container {
            position(xPos, NewWorldCreationScene.ACTION_BUTTON_Y)
            drawActionButtonContent(label, fillColor)
        }
    }

    /** Draws the visible action-button background and label into an already positioned container. */
    fun Container.drawActionButtonContent(label: String, fillColor: RGBA) {
        drawBorderedBox(NewWorldCreationScene.ACTION_BUTTON_W, NewWorldCreationScene.ACTION_BUTTON_H, fillColor)
        text(label, textSize = 30.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
            centerOn(this@drawActionButtonContent)
        }
    }

    /** Draws a filled rectangle with a black border. */
    fun Container.drawBorderedBox(width: Double, height: Double, fillColor: RGBA = Colors.WHITE) {
        graphics {
            fill(fillColor) {
                rect(0.0, 0.0, width, height)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = NewWorldCreationScene.STROKE_THICKNESS)) {
                rect(0.0, 0.0, width, height)
            }
        }
    }

    /** Draws only the border of a text input box. */
    fun Container.drawBoxBorder(width: Double, height: Double) {
        graphics {
            stroke(Colors.BLACK, StrokeInfo(thickness = NewWorldCreationScene.STROKE_THICKNESS)) {
                rect(0.0, 0.0, width, height)
            }
        }
    }
}
