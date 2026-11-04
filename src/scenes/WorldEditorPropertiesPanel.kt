package scenes

import audio.onClickWithResultSound
import audio.onClickWithSound
import korlibs.image.color.Colors
import korlibs.korge.annotations.KorgeExperimental
import korlibs.korge.ui.UITextInput
import korlibs.korge.ui.uiTextInput
import korlibs.korge.view.Container
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.position
import korlibs.korge.view.text
import korlibs.math.geom.Size
import korlibs.math.geom.vector.StrokeInfo
import network.WorldEditorNodeDTO
import network.WorldEditorNodeKind
import ui.Assets
import ui.SceneUIUtils
import worldeditor.WorldEditorDefaults
import worldeditor.WorldEditorSession

/** Draws and wires the selected source/sink property controls in the world editor. */
@OptIn(KorgeExperimental::class)
internal class WorldEditorPropertiesPanel(
    private val session: WorldEditorSession,
    private val redraw: () -> Unit
) {
    /** Rebuilds the complete properties panel content for the current selection. */
    fun draw(container: Container) {
        val selected = session.selectedNode
        drawColorProperties(container, selected)
        drawVehicleProperties(container, selected)
        drawSelectionSummary(container, selected)
        drawRemoveButton(container, selected)
        drawOrientationSection(container, selected)
    }

    private fun drawColorProperties(container: Container, selected: WorldEditorNodeDTO?) {
        container.text(
            "Farben (Klick: an/aus):",
            textSize = 24.0,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ) {
            x = WorldEditorLayout.PROPS_X + 28.0
            y = WorldEditorLayout.PROPS_Y + 92.0
        }
        WorldEditorDefaults.paletteColors.forEachIndexed { index, color ->
            drawColorSwatch(container, selected, index, color)
        }
    }

    private fun drawColorSwatch(
        container: Container,
        selected: WorldEditorNodeDTO?,
        index: Int,
        color: domain.Color
    ) {
        val colorAssigned = selected?.colorNames?.any {
            WorldEditorDefaults.colorNamesMatch(it, color.name)
        } == true
        val swatch = container.container {
            position(WorldEditorLayout.PROPS_X + 28.0 + index * 58.0, WorldEditorLayout.PROPS_Y + 130.0)
        }
        drawSwatchBox(swatch, color, colorAssigned)
        swatch.onClickWithResultSound {
            val changed = session.toggleSelectedColor(color.name)
            redraw()
            changed
        }
    }

    private fun drawSwatchBox(container: Container, color: domain.Color, selected: Boolean) {
        container.graphics {
            fill(color.value) { rect(0.0, 0.0, COLOR_SWATCH_SIZE, COLOR_SWATCH_SIZE) }
            stroke(
                if (selected) Colors["#F2A100"] else Colors.BLACK,
                StrokeInfo(thickness = if (selected) 4.0 else 2.0)
            ) {
                rect(0.0, 0.0, COLOR_SWATCH_SIZE, COLOR_SWATCH_SIZE)
            }
        }
    }

    private fun drawVehicleProperties(container: Container, selected: WorldEditorNodeDTO?) {
        container.text(
            "Fahrzeuge (Klick: an/aus):",
            textSize = 24.0,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ) {
            x = WorldEditorLayout.PROPS_X + 28.0
            y = WorldEditorLayout.PROPS_Y + 200.0
        }
        WorldEditorDefaults.vehicleTypes.forEachIndexed { index, vehicle ->
            val col = index % VEHICLE_BUTTON_COLUMNS
            val row = index / VEHICLE_BUTTON_COLUMNS
            val typeAssigned = selected?.vehicleTypeNames?.any { it.equals(vehicle.name, ignoreCase = true) } == true
            val button = SceneUIUtils.drawBorderedTextButton(
                container,
                WorldEditorLayout.PROPS_X + 28.0 + col * 198.0,
                WorldEditorLayout.PROPS_Y + 238.0 + row * 56.0,
                178.0,
                44.0,
                vehicle.name,
                18.0,
                fillColor = if (typeAssigned) Colors["#CFE3FA"] else Colors.WHITE
            )
            button.onClickWithResultSound {
                val changed = session.toggleSelectedVehicleType(vehicle.name)
                redraw()
                changed
            }
        }
    }

    private fun drawSelectionSummary(container: Container, selected: WorldEditorNodeDTO?) {
        val selectionLabel = selected?.let {
            val kind = if (it.kind == WorldEditorNodeKind.SOURCE) "Quelle" else "Senke"
            "Auswahl: $kind"
        } ?: "Keine Auswahl"
        SceneUIUtils.drawBorderedTextButton(
            container,
            WorldEditorLayout.PROPS_X + 28.0,
            WorldEditorLayout.PROPS_Y + 355.0,
            WorldEditorLayout.PROPS_W - 56.0,
            46.0,
            selectionLabel,
            19.0,
            fillColor = Colors["#FFF0CF"]
        )
        container.text("Gilt fuer die ausgewaehlte\nQuelle/Senke.",
            textSize = 19.0,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ) {
            x = WorldEditorLayout.PROPS_X + 82.0
            y = WorldEditorLayout.PROPS_Y + 430.0
        }
    }

    private fun drawRemoveButton(container: Container, selected: WorldEditorNodeDTO?) {
        val removeButton = SceneUIUtils.drawBorderedTextButton(
            container,
            WorldEditorLayout.PROPS_X + 28.0,
            WorldEditorLayout.PROPS_Y + 462.0,
            WorldEditorLayout.PROPS_W - 56.0,
            40.0,
            "Entfernen",
            18.0,
            disabled = selected == null,
            fillColor = if (selected == null) Colors["#F1F1F1"] else Colors.WHITE
        )
        removeButton.onClickWithSound {
            selected?.let {
                session.removeNode(it.id)
                redraw()
            }
        }
    }

    /**
     * Draws the orientation input below the source-configurations button: a degree field
     * (0-359) that applies every valid keystroke immediately, plus a hint explaining the
     * direction of the degree values. Only visible while a source or sink is selected.
     */
    private fun drawOrientationSection(container: Container, selected: WorldEditorNodeDTO?) {
        if (selected == null) return
        val x = WorldEditorLayout.CONFIG_BUTTON_X
        val labelY = WorldEditorLayout.CONFIG_BUTTON_Y + WorldEditorLayout.CONFIG_BUTTON_H + 18.0
        val inputY = labelY + 36.0

        container.text(
            "Ausrichtung (0-359 Grad):",
            textSize = 22.0,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ) {
            position(x, labelY)
        }

        val input = drawOrientationInput(container, x, inputY)
        // Apply while typing without redrawing the panel, so the field keeps its focus.
        // Incomplete or out-of-range input is ignored until it becomes a valid degree value.
        input.controller.onTextUpdated { controller ->
            val degrees = controller.text.trim().toIntOrNull()
            if (degrees != null && degrees in 0..359) {
                session.setSelectedOrientation(degrees)
            }
        }

        container.text(
            "0 = oben, 90 = rechts,\n180 = unten, 270 = links",
            textSize = 19.0,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ) {
            position(x, inputY + ORIENTATION_INPUT_H + 14.0)
        }
    }

    /** Draws the bordered degree text input prefilled with the selection's current orientation. */
    private fun drawOrientationInput(container: Container, x: Double, y: Double): UITextInput {
        lateinit var input: UITextInput
        container.container {
            position(x, y)
            graphics {
                fill(Colors.WHITE) { rect(0.0, 0.0, ORIENTATION_INPUT_W, ORIENTATION_INPUT_H) }
                stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) {
                    rect(0.0, 0.0, ORIENTATION_INPUT_W, ORIENTATION_INPUT_H)
                }
            }
            input = uiTextInput(
                session.selectedOrientationDegrees()?.toString().orEmpty(),
                Size(ORIENTATION_INPUT_W - 22.0, ORIENTATION_INPUT_H - 16.0)
            ) {
                position(11.0, 8.0)
                textSize = 24.0
                font = Assets.bitcountSingle
            }
        }
        return input
    }

    private companion object {
        const val COLOR_SWATCH_SIZE = 44.0
        const val VEHICLE_BUTTON_COLUMNS = 2
        const val ORIENTATION_INPUT_W = 150.0
        const val ORIENTATION_INPUT_H = 48.0
    }
}
