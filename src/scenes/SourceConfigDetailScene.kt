package scenes

import domain.Color
import domain.GameState
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.TargetNode
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.image.text.TextAlignment
import korlibs.korge.annotations.KorgeExperimental
import audio.onClickWithSound
import korlibs.korge.scene.Scene
import korlibs.korge.ui.uiTextInput
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.addUpdater
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.position
import korlibs.korge.view.text
import korlibs.korge.view.xy
import korlibs.math.geom.Size2D
import korlibs.math.geom.vector.StrokeInfo
import scenes.LevelScene.Companion.TABLE_PX_W
import ui.Assets.bitcountSingle
import worldeditor.WorldEditorConfigurationContext
import worldeditor.WorldEditorNodeSettings
import ui.ControlButtons
import ui.addVehiclePreview
import ui.vehicleBitmapForGraphicRef

/**
 * Detail and edit view for a single SourceNodeConfiguration on a SourceNode.
 *
 * Displays a vehicle preview card, editable text fields for initial vehicle speed and
 * generation frequency (in seconds), and an interactive colour palette for selecting
 * exactly one target colour.
 *
 * When the player closes the scene via the (X) button, the edited values are committed
 * back to the matching entry in source.configurations.
 *
 * @param source The parent SourceNode.
 * @param label  Human-readable source name shown in the preview card, e.g. "Quelle 1".
 * @param configuration The specific configuration being edited.
 */
class SourceConfigDetailScene(
    private val source: SourceNode,
    private val label: String,
    private val configuration: SourceNodeConfiguration
) : Scene() {

    /** 
     * Enum to represent the target of the validation error.
     */
    private enum class ValidationErrorTarget {
        /** 
         * The validation error is related to the speed. 
         */
        SPEED,
        /** 
         * The validation error is related to the frequency. 
         */
        FREQ
    }

    companion object {
        /** Width of the error message box. */
        private const val ERROR_BOX_W = 500.0

        /** Height of the error message box. */
        private const val ERROR_BOX_H = 60.0

        /** Side length of each colour swatch square in pixels. */
        private const val SWATCH_SIZE = 52.0

        /** Horizontal gap between consecutive swatch squares. */
        private const val SWATCH_GAP = 8.0

        /**
         * Built-in fallback colour palette used when no [TargetNode] colours can be
         * retrieved from [GameState.currentLevel].
         */
        private val FALLBACK_PALETTE = listOf(
            Color("Rot",  RGBA(0xCC, 0x00, 0x00, 0xFF)),
            Color("Blau", RGBA(0x00, 0x00, 0xCC, 0xFF)),
            Color("Gelb", RGBA(0xFF, 0xCC, 0x00, 0xFF)),
        )

        /** 
         * Returns the X position of the vehicle card. 
         * 
         * @return The X position of the vehicle card. 
         */
        fun vehicleCardX(): Double = (TABLE_PX_W - CARD_SIZE) / 2.0
        
        /** 
         * Returns the Y position of a value row based on its index. 
         * 
         * @param index The index of the value row.
         * @return The Y position of the value row.
         */
        fun valueRowY(index: Int): Double = VALUE_START_Y + index * (VALUE_BOX_H + VALUE_ROW_GAP)

    }

    /**
     * Creates an input box with an error overlay and returns both components.
     *
     * @param x The x position of the input box.
     * @param y The y position of the input box.
     * @param initialText The initial text to display in the input box.
     * @return A pair containing the input field and the overlay graphics.
     */
    private fun SContainer.createInputBoxWithOverlay(
        x: Double,
        y: Double,
        initialText: String
    ): Pair<korlibs.korge.ui.UITextInput, korlibs.korge.view.Graphics> {
        var inputField = uiTextInput { }
        var overlay: korlibs.korge.view.Graphics? = null

        container {
            xy(x, y)
            drawBorderedBox(VALUE_BOX_W, VALUE_BOX_H)
            inputField = uiTextInput(initialText, Size2D(VALUE_BOX_W / 4, VALUE_BOX_H - 4)) {
                centerOn(this@container)
                textSize = LABEL_TEXT_SIZE
                font = bitcountSingle
                this.x = VALUE_BOX_W / 2.0 - 48
            }
            overlay = graphics {
                fill(RGBA(255, 50, 50)) {
                    rect(0.0, 0.0, VALUE_BOX_W, VALUE_BOX_H)
                }
            }.apply {
                blendMode = korlibs.korge.view.BlendMode.MULTIPLY
                mouseEnabled = false
                visible = false
            }
        }

        return Pair(inputField, overlay!!)
    }

    /**
     * Main function to create the scene.
     */
    @OptIn(KorgeExperimental::class)
    override suspend fun SContainer.sceneMain() {
        drawWhiteSceneBackground()
        var selectedColor: Color = configuration.color

        /** Palette built from the edited level's [TargetNode] colours; falls back to [FALLBACK_PALETTE]. */
        val paletteColors: List<Color> = (WorldEditorConfigurationContext.levelOrNull() ?: GameState.currentLevel)
            ?.nodes
            ?.filterIsInstance<TargetNode>()
            ?.map { it.color }
            ?.distinctBy { it.name }
            ?.takeIf { it.isNotEmpty() }
            ?: FALLBACK_PALETTE

        val bitmap = vehicleBitmapForGraphicRef(configuration.vehicleType.graphicRef)

        /** Title */
        text("Quelle bearbeiten", textSize = TITLE_TEXT_SIZE, color = Colors.BLACK, font = bitcountSingle) {
            centerXOn(this@sceneMain)
            y = TITLE_Y
        }

        /** Preview card */
        val cardX = vehicleCardX()
        val cardY = CARD_Y

        container {
            xy(cardX, cardY)
            drawBorderedBox(CARD_SIZE, CARD_SIZE)

            text(label, textSize = CARD_LABEL_TEXT_SIZE, color = Colors.BLACK, font = bitcountSingle) {
                centerXOn(this@container)
                y = CARD_SIZE - 54.0
            }
        }

        /** Inner container whose children are rebuilt whenever the colour selection changes. */
        val previewContainer: Container = container {
            position(cardX + CARD_SIZE / 2.0, cardY + 78.0)
        }

        val selectionBorders = mutableListOf<Container>()

        /** 
         * Rebuilds the preview card with the current vehicle type and color. 
         */
        fun rebuildPreview() {
            previewContainer.removeChildren()
            previewContainer.addVehiclePreview(bitmap, selectedColor, CARD_ICON_W, CARD_ICON_H)
        }

        /** 
         * Updates the selection borders to show the currently selected color. 
         */
        fun updateSelectionBorders() {
            selectionBorders.forEachIndexed { idx, border ->
                border.visible = paletteColors[idx].name == selectedColor.name
            }
        }

        rebuildPreview()

        val labels = listOf("Initiale Geschwindigkeit:", "Häufigkeit (in Sekunden):", "Farbe:")
        var inputSpeed = uiTextInput { }
        var inputFreq = uiTextInput { }
        var speedBoxOverlay: korlibs.korge.view.Graphics? = null
        var freqBoxOverlay: korlibs.korge.view.Graphics? = null

        /** Drawing text label for each property */
        labels.forEachIndexed { index, labelText ->
            val rowY = valueRowY(index)
            text(labelText, textSize = LABEL_TEXT_SIZE, color = Colors.BLACK, font = bitcountSingle) {
                position(VALUE_BOX_X - 50, rowY + VALUE_BOX_H / 2.0 - this.height / 2.0)
                alignment = TextAlignment.RIGHT
            }
        }

        /** Input Box for initialSpeed */
        val (speedInput, speedOverlay) = createInputBoxWithOverlay(
            VALUE_BOX_X, valueRowY(0), 
            configuration.initialVehicleSpeed.toString()
        )
        inputSpeed = speedInput
        speedBoxOverlay = speedOverlay

        /** Input Box for frequency */
        val (freqInput, freqOverlay) = createInputBoxWithOverlay(
            VALUE_BOX_X, valueRowY(1),
            configuration.generationSpeed.toString()
        )
        inputFreq = freqInput
        freqBoxOverlay = freqOverlay

        /** Color Swatch Row */
        container {
            xy(VALUE_BOX_X, valueRowY(2))
            drawBorderedBox(VALUE_BOX_W, VALUE_BOX_H)

            val totalSwatchesW = paletteColors.size * SWATCH_SIZE + (paletteColors.size - 1) * SWATCH_GAP
            val startX = (VALUE_BOX_W - totalSwatchesW) / 2.0

            paletteColors.forEachIndexed { idx, color ->
                val swatchX = startX + idx * (SWATCH_SIZE + SWATCH_GAP)
                val swatchY = (VALUE_BOX_H - SWATCH_SIZE) / 2.0

                val selBorder = container {
                    position(swatchX - 3.0, swatchY - 3.0)
                    graphics {
                        fill(Colors.BLACK) {
                            rect(0.0, 0.0, SWATCH_SIZE + 6.0, SWATCH_SIZE + 6.0)
                        }
                    }
                    visible = color.name == selectedColor.name
                }
                selectionBorders.add(selBorder)

                container {
                    position(swatchX, swatchY)
                    graphics {
                        fill(color.value) { rect(0.0, 0.0, SWATCH_SIZE, SWATCH_SIZE) }
                    }
                    onClickWithSound {
                        selectedColor = color
                        updateSelectionBorders()
                        rebuildPreview()
                    }
                }
            }
        }

        /** Error message container */
        val errorMessageContainer = container {
            visible = false
            val textY = valueRowY(2) + VALUE_BOX_H + 50.0
            val startX = (TABLE_PX_W - ERROR_BOX_W) / 2.0
            xy(startX, textY)

            graphics {
                stroke(Colors.RED, StrokeInfo(thickness = STROKE_THICKNESS)) {
                    rect(0.0, 0.0, ERROR_BOX_W, ERROR_BOX_H)
                }
            }
            text("", textSize = CARD_LABEL_TEXT_SIZE, color = Colors.RED, font = bitcountSingle) {
                position(0.0, 0.0)
            }
        }
        val errorMessageText = errorMessageContainer.lastChild as? korlibs.korge.view.Text

        /** 
         * Sets the error message text and positions it in the center of the error message box.
         * 
         * @param message The error message to display.
         */
        fun setErrorMessage(message: String) {
            errorMessageText?.text = message
            errorMessageText?.position(
                (ERROR_BOX_W - errorMessageText.width) / 2.0,
                (ERROR_BOX_H - errorMessageText.height) / 2.0
            )
        }

        var isCurrentlyValid = true
        var currentValidSpeed = configuration.initialVehicleSpeed
        var currentValidFreq = configuration.generationSpeed
        var currentErrorMessage: String? = null
        var currentErrorTarget: ValidationErrorTarget? = null

        /** Validate input values and update UI */
        fun validateInputs() {
            /** Filter text to only allow numbers and dots */
            val speedText = inputSpeed.text
            val filteredSpeed = speedText.filter { it.isDigit() || it == '.' }
            if (speedText != filteredSpeed) {
                inputSpeed.text = filteredSpeed
            }

            val freqText = inputFreq.text
            val filteredFreq = freqText.filter { it.isDigit() || it == '.' }
            if (freqText != filteredFreq) {
                inputFreq.text = filteredFreq
            }

            /** Get new values if valid, else keep existing values */
            val newSpeed = inputSpeed.text.toDoubleOrNull()?.takeIf { it > 0.0 }
                ?: configuration.initialVehicleSpeed
            val newFreq = inputFreq.text.toDoubleOrNull()?.takeIf { it > 0.0 }
                ?: configuration.generationSpeed

            /** Calculate total frequency from all configurations */
            var totalRate = 0.0
            source.configurations.forEach { conf ->
                totalRate += if (conf === configuration) {
                    1.0 / newFreq
                } else {
                    1.0 / conf.generationSpeed
                }
            }

            /** Calculate effective frequency */
            val effectiveFreq = if (totalRate > 0.0) 1.0 / totalRate else Double.MAX_VALUE

            var valid = true
            var errorMessage: String? = null
            var errorTarget: ValidationErrorTarget? = null

            if (newSpeed > configuration.vehicleType.maxSpeed) {
                valid = false
                errorMessage = "Geschwindigkeit zu hoch"
                errorTarget = ValidationErrorTarget.SPEED
            } else {
                for (conf in source.configurations) {
                    val speed = if (conf === configuration) newSpeed else conf.initialVehicleSpeed
                    val distance = (speed * effectiveFreq) - conf.vehicleType.length
                    if (distance < 2.0) {
                        valid = false
                        errorMessage = "Häufigkeit zu hoch"
                        errorTarget = ValidationErrorTarget.FREQ
                        break
                    }
                }
            }

            /** Update UI if validation state changed */
            val validationChanged = valid != isCurrentlyValid || errorMessage != currentErrorMessage || errorTarget != currentErrorTarget
            if (validationChanged) {
                isCurrentlyValid = valid
                currentErrorMessage = errorMessage
                currentErrorTarget = errorTarget
                speedBoxOverlay?.visible = errorTarget == ValidationErrorTarget.SPEED
                freqBoxOverlay?.visible = errorTarget == ValidationErrorTarget.FREQ
                errorMessageContainer.visible = !valid
                setErrorMessage(errorMessage.orEmpty())
            }

            /** Store valid values */
            if (valid) {
                currentValidSpeed = newSpeed
                currentValidFreq = newFreq
            }
        }

        /** Input validation */
        validateInputs()
        addUpdater { validateInputs() }

        /** Close button */
        ControlButtons.drawCloseButton(this).onClickWithSound {
            /** Apply changes only if valid */
            if (isCurrentlyValid) {
                applyChanges(currentValidSpeed, currentValidFreq, selectedColor)
            }
            leaveConfigurationDetail()
        }

        /** Back button */
        ControlButtons.drawBackButton(this).onClickWithSound {
            /** Apply changes only if valid */
            if (isCurrentlyValid) {
                applyChanges(currentValidSpeed, currentValidFreq, selectedColor)
            }
            returnToConfigurationOverview()
        }
    }

    /**
     * Replaces the edited configuration entry in the configurations of the source with updated values.
     *
     * Any accepted configuration change clears runtime simulation progress and invalidates the
     * currently running simulation so the next run starts from an empty road network using the
     * updated source setup.
     *
     * @param newSpeed The new initial vehicle speed.
     * @param newFreq The new generation frequency.
     * @param newColor The new color.
     */
    private fun applyChanges(
        newSpeed: Double,
        newFreq: Double,
        newColor: Color
    ) {
        if (newSpeed <= 0.0 || newFreq <= 0.0) return
        val index = source.configurations.indexOf(configuration)
        if (index < 0) return
        val updated = SourceNodeConfiguration(
            color = newColor,
            generationSpeed = newFreq,
            initialVehicleSpeed = newSpeed,
            vehicleType = configuration.vehicleType,
            vehiclesToSpawn = configuration.vehiclesToSpawn
        )
        source.configurations = source.configurations.toMutableList().apply {
            this[index] = updated
        }
        val editorLevel = WorldEditorConfigurationContext.levelOrNull()
        if (editorLevel != null) {
            /** Recoloring can hit an existing color-and-type pair; the editor keeps combinations unique. */
            source.configurations = WorldEditorNodeSettings.dedupedConfigurations(source.configurations)
            editorLevel.resetSimulationProgress()
        } else {
            GameState.currentLevel?.resetSimulationProgress()
            GameState.activeSimulation = null
        }
    }

    /**
     * Draws a white rectangle with a black border in the current container.
     *
     * @param width Width of the bordered box.
     * @param height Height of the bordered box.
     * @param fillColor Fill color of the box (default: white).
     * @return The created graphics object.
     */
    private fun Container.drawBorderedBox(width: Double, height: Double, fillColor: RGBA = Colors.WHITE): korlibs.korge.view.Graphics {
        return graphics {
            fill(fillColor) {
                rect(0.0, 0.0, width, height)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = STROKE_THICKNESS)) {
                rect(0.0, 0.0, width, height)
            }
        }
    }
}
