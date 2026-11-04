package scenes

import domain.VehicleType
import korlibs.image.bitmap.Bitmap32
import korlibs.image.color.Colors
import korlibs.image.text.TextAlignment
import korlibs.korge.annotations.KorgeExperimental
import audio.onClickWithSound
import korlibs.korge.scene.Scene
import korlibs.korge.ui.uiTextInput
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.anchor
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.image
import korlibs.korge.view.position
import korlibs.korge.view.text
import korlibs.korge.view.xy
import korlibs.math.geom.Size2D
import korlibs.math.geom.vector.StrokeInfo
import scenes.LevelScene.Companion.TABLE_PX_W
import ui.Assets
import ui.Assets.bitcountSingle
import ui.ControlButtons

/**
 * Displays the vehicle-type detail menu shown from the configuration screen.
 *
 * @property vehicleType The vehicle type whose editable values are shown in this scene.
 */
class VehicleConfigurationScene(var vehicleType: VehicleType) : Scene() {

    /**
     * Stores fixed layout values for the projected vehicle-configuration menu.
     */
    companion object {
        /**
         * Describes one editable property row in the vehicle configuration form.
         *
         * @property label The visible label shown left of the input field.
         * @property value The current numeric value shown in the input field.
         */
        data class VehiclePropertyRow(val label: String, val value: Double)

        /**
         * Returns the centered x-position of the vehicle preview card.
         *
         * @return The card's left x-position.
         */
        fun vehicleCardX(): Double = (TABLE_PX_W - CARD_SIZE) / 2.0

        /**
         * Returns the y-position for a property row.
         *
         * @param index Zero-based row index.
         * @return The row's top y-position.
         */
        fun valueRowY(index: Int): Double = VALUE_START_Y + index * (VALUE_BOX_H + VALUE_ROW_GAP)

        /**
         * Builds the editable property rows for a vehicle type.
         *
         * @param vehicleType The vehicle type whose values should be displayed.
         * @return The ordered list of labels and values used by the scene.
         */
        fun propertyRows(vehicleType: VehicleType): List<VehiclePropertyRow> {
            return listOf(
                VehiclePropertyRow("Maximale Geschwindigkeit:", vehicleType.maxSpeed),
                VehiclePropertyRow("Beschleunigung:", vehicleType.acceleration),
                VehiclePropertyRow("Haftungskoeffizient:", vehicleType.adhesionCoefficient)
            )
        }

        /**
         * Applies text input values to a vehicle type if every value is numeric.
         *
         * @param vehicleType The vehicle type to update.
         * @param maxSpeedText Text entered for maximum speed.
         * @param accelerationText Text entered for acceleration.
         * @param adhesionCoefficientText Text entered for the adhesion coefficient.
         * @return True when all values were parsed and applied, otherwise false.
         */
        fun applyInputValues(
            vehicleType: VehicleType,
            maxSpeedText: String,
            accelerationText: String,
            adhesionCoefficientText: String
        ): Boolean {
            val maxSpeed = maxSpeedText.toDoubleOrNull() ?: return false
            val acceleration = accelerationText.toDoubleOrNull() ?: return false
            val adhesionCoefficient = adhesionCoefficientText.toDoubleOrNull() ?: return false

            vehicleType.maxSpeed = maxSpeed
            vehicleType.acceleration = acceleration
            vehicleType.adhesionCoefficient = adhesionCoefficient
            return true
        }
    }

    /**
     * Builds the complete vehicle-configuration screen and wires the close button.
     *
     * The close button reads the current input values, writes them back to [vehicleType],
     * and then returns to [ConfigurationScene].
     */
    @OptIn(KorgeExperimental::class)
    override suspend fun SContainer.sceneMain() {
        drawWhiteSceneBackground()
        text("Fahrzeugtyp bearbeiten", textSize = TITLE_TEXT_SIZE, color = Colors.BLACK, font = bitcountSingle) {
            centerXOn(this@sceneMain)
            y = TITLE_Y
        }

        drawVehicleCard(vehicleType)

        val rows = propertyRows(vehicleType)

        val listPos = mutableListOf<Double>()
        var inputMaxSpeed = uiTextInput {  }
        var inputAcceleration = uiTextInput {  }
        var inputAdhesionCoefficient = uiTextInput {  }

        /** Drawing text label for each property */
        rows.forEachIndexed { index, row ->
            val rowY = valueRowY(index)
            text(row.label, textSize = LABEL_TEXT_SIZE, color = Colors.BLACK, font = bitcountSingle) {
                position(VALUE_BOX_X - 50, rowY + VALUE_BOX_H / 2.0 - this.height / 2.0)
                alignment = TextAlignment.RIGHT
            }
            listPos.add(rowY)
        }

        /** Input Box for maxSpeed*/
        container {
            xy(VALUE_BOX_X, listPos[0])
            drawBorderedBox(VALUE_BOX_W, VALUE_BOX_H)
             inputMaxSpeed = uiTextInput(vehicleType.maxSpeed.toString(), Size2D(VALUE_BOX_W / 4, VALUE_BOX_H - 4)) {
                centerOn(this@container)
                textSize = LABEL_TEXT_SIZE
                font = bitcountSingle
                this.x = VALUE_BOX_W / 2.0 - 48
            }
        }

        /** Input Box for acceleration*/
        container {
            xy(VALUE_BOX_X, listPos[1])
            drawBorderedBox(VALUE_BOX_W, VALUE_BOX_H)
            inputAcceleration = uiTextInput(vehicleType.acceleration.toString(), Size2D(VALUE_BOX_W / 4, VALUE_BOX_H - 4)) {
                centerOn(this@container)
                textSize = LABEL_TEXT_SIZE
                font = bitcountSingle
                this.x = VALUE_BOX_W / 2.0 - 48
            }
        }

        /** Input Box for adhesionCoefficient*/
        container {
            xy(VALUE_BOX_X, listPos[2])
            drawBorderedBox(VALUE_BOX_W, VALUE_BOX_H)
            inputAdhesionCoefficient = uiTextInput(vehicleType.adhesionCoefficient.toString(), Size2D(VALUE_BOX_W / 4, VALUE_BOX_H - 4)) {
                centerOn(this@container)
                textSize = LABEL_TEXT_SIZE
                font = bitcountSingle
                this.x = VALUE_BOX_W / 2.0 - 48
            }
        }

        /** Close Button */
        ControlButtons.drawCloseButton(this).onClickWithSound {
            applyInputValues(
                vehicleType,
                inputMaxSpeed.text,
                inputAcceleration.text,
                inputAdhesionCoefficient.text
            )
            leaveConfigurationDetail()
        }

        /** Back Button */
        ControlButtons.drawBackButton(this).onClickWithSound {
            applyInputValues(
                vehicleType,
                inputMaxSpeed.text,
                inputAcceleration.text,
                inputAdhesionCoefficient.text
            )
            returnToConfigurationOverview()
        }
    }

    /**
     * Draws the centered vehicle preview card.
     *
     * @param vehicleType The vehicle type whose icon and name should be displayed.
     */
    private fun SContainer.drawVehicleCard(vehicleType: VehicleType) {
        container {
            xy(vehicleCardX(), CARD_Y)
            drawBorderedBox(CARD_SIZE, CARD_SIZE)

            val bitmap = vehicleBitmap(vehicleType)
            image(bitmap) {
                val scaleW = CARD_ICON_W / bitmap.width.toDouble()
                val scaleH = CARD_ICON_H / bitmap.height.toDouble()
                scale = kotlin.math.min(scaleW, scaleH)
                anchor(0.5, 0.5)
                position(CARD_SIZE / 2.0, 78.0)
            }

            text(vehicleType.name, textSize = CARD_LABEL_TEXT_SIZE, color = Colors.BLACK, font = bitcountSingle) {
                centerXOn(this@container)
                y = CARD_SIZE - 54.0
            }
        }
    }

    /**
     * Draws a white rectangle with a black border in the current container.
     *
     * @param width Width of the bordered box.
     * @param height Height of the bordered box.
     */
    private fun Container.drawBorderedBox(width: Double, height: Double) {
        graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, width, height)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = STROKE_THICKNESS)) {
                rect(0.0, 0.0, width, height)
            }
        }
    }

    /**
     * Selects the bitmap used for the given vehicle type.
     *
     * Known built-in names use their shared assets. Unknown names fall back to
     * the configured graphic reference.
     *
     * @param vehicleType The vehicle type to render.
     * @return The bitmap used for the vehicle preview.
     */
    private fun vehicleBitmap(vehicleType: VehicleType): Bitmap32 {
        return when (vehicleType.name.lowercase()) {
            "auto", "car" -> Assets.car
            "lkw", "truck" -> Assets.truck
            "motorrad", "motorcycle" -> Assets.motorcycle
            else -> Assets.getVehicleGraphic(vehicleType.graphicRef)
        }
    }
}
