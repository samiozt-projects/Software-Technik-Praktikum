package scenes

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import audio.onClickWithSound
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.position
import korlibs.korge.view.text
import korlibs.math.geom.vector.StrokeInfo
import ui.Assets

/**
 * Click handlers for the action buttons drawn by [WorldSelectionActionPanel].
 *
 * @property onOpen called with the current worlds and selected index when "Welt öffnen" is clicked.
 * @property onEdit called with the current worlds and selected index when "Welt bearbeiten" is clicked.
 * @property onRefresh called when "Welten aktualisieren" is clicked.
 * @property onNewWorld called when "Neue Welt erstellen" is clicked.
 * @property onDelete called with the current worlds and selected index when "Welt löschen" is clicked.
 */
internal data class WorldSelectionActionCallbacks(
    val onOpen: (worlds: List<WorldSelectionScene.WorldEntry>, selectedIndex: Int?) -> Unit,
    val onEdit: (worlds: List<WorldSelectionScene.WorldEntry>, selectedIndex: Int?) -> Unit,
    val onRefresh: () -> Unit,
    val onNewWorld: () -> Unit,
    val onDelete: (worlds: List<WorldSelectionScene.WorldEntry>, selectedIndex: Int?) -> Unit
)

/**
 * Renders the right-side action button column used by [WorldSelectionScene] ("Welt öffnen",
 * "Welt bearbeiten", "Welten aktualisieren", "Neue Welt erstellen", "Welt löschen").
 *
 * Kept in its own file so [WorldSelectionScene] stays under the project's file-size limit.
 * Navigation itself (opening scenes, re-fetching worlds) stays in [WorldSelectionScene] since it
 * needs the scene's coroutine scope; this object only draws buttons and reports clicks through
 * [WorldSelectionActionCallbacks].
 */
internal object WorldSelectionActionPanel {

    /**
     * Owns and refreshes the right-side action buttons for the current world selection.
     *
     * Nested inside [WorldSelectionActionPanel] (rather than declared as a top-level class) so it
     * can call the object's private button-drawing functions.
     *
     * @property panel container holding the action buttons.
     * @property worlds callback returning the worlds currently shown by the list.
     * @property selectedIndex callback returning the currently selected row index.
     * @property callbacks click handlers invoked by the individual action buttons.
     */
    class Controller(
        private val panel: Container,
        private val worlds: () -> List<WorldSelectionScene.WorldEntry>,
        private val selectedIndex: () -> Int?,
        private val callbacks: WorldSelectionActionCallbacks
    ) {
        /**
         * Rebuilds all action buttons to reflect the current selection state.
         */
        fun refresh() {
            panel.removeChildren()
            panel.drawWorldOpenButton(worlds(), selectedIndex, callbacks)
            panel.drawWorldEditButton(worlds(), selectedIndex, callbacks)
        }
    }

    /**
     * Draws the action button panel and returns its controller.
     *
     * @param parent container that receives the action panel.
     * @param worlds callback returning the worlds currently shown by the list.
     * @param selectedIndex callback returning the currently selected row index.
     * @param callbacks click handlers invoked by the individual action buttons.
     * @return the controller that refreshes the panel when the selection or world list changes.
     */
    fun draw(
        parent: SContainer,
        worlds: () -> List<WorldSelectionScene.WorldEntry>,
        selectedIndex: () -> Int?,
        callbacks: WorldSelectionActionCallbacks
    ): Controller {
        val panel = parent.container {
            position(WorldSelectionScene.ACTION_X, WorldSelectionScene.ACTION_Y)
        }
        return Controller(panel, worlds, selectedIndex, callbacks).also { it.refresh() }
    }

    /**
     * Draws the "Welt öffnen" action button and wires it to [WorldSelectionActionCallbacks.onOpen].
     *
     * @param worlds available worlds passed to the open action.
     * @param selectedIndex callback returning the currently selected row index.
     * @param callbacks click handlers invoked by the individual action buttons.
     */
    private fun Container.drawWorldOpenButton(
        worlds: List<WorldSelectionScene.WorldEntry>,
        selectedIndex: () -> Int?,
        callbacks: WorldSelectionActionCallbacks
    ) {
        val enabled = WorldSelectionScene.worldActionEnabled(worlds, selectedIndex())
        drawActionButton("Welt öffnen", enabled, highlighted = true).onClickWithSound {
            callbacks.onOpen(worlds, selectedIndex())
        }
    }

    /**
     * Draws the "Welt bearbeiten", "Welten aktualisieren", "Neue Welt erstellen", and
     * "Welt löschen" action buttons and wires each of them to [callbacks].
     *
     * @param worlds available worlds passed to the edit and delete actions.
     * @param selectedIndex callback returning the currently selected row index.
     * @param callbacks click handlers invoked by the individual action buttons.
     */
    private fun Container.drawWorldEditButton(
        worlds: List<WorldSelectionScene.WorldEntry>,
        selectedIndex: () -> Int?,
        callbacks: WorldSelectionActionCallbacks
    ) {
        val editEnabled = WorldSelectionScene.worldEditorActionEnabled(worlds, selectedIndex())
        drawActionButton(
            "Welt bearbeiten",
            enabled = editEnabled,
            highlighted = false,
            row = 1
        ).onClickWithSound {
            if (WorldSelectionScene.worldEditorActionEnabled(worlds, selectedIndex())) {
                callbacks.onEdit(worlds, selectedIndex())
            }
        }
        drawActionButton("Welten aktualisieren", enabled = true, highlighted = false, row = 2).onClickWithSound {
            callbacks.onRefresh()
        }
        drawActionButton("Neue Welt erstellen", enabled = true, highlighted = false, row = 3).onClickWithSound {
            callbacks.onNewWorld()
        }
        drawActionButton(
            "Welt löschen",
            enabled = WorldSelectionScene.worldActionEnabled(worlds, selectedIndex()),
            highlighted = false,
            row = 4
        ).onClickWithSound {
            callbacks.onDelete(worlds, selectedIndex())
        }
    }

    /**
     * Returns the fill color for an action button based on its state.
     *
     * @param enabled whether the button is interactive.
     * @param highlighted whether the button represents the primary action.
     * @return fill color for the button background.
     */
    private fun actionButtonFill(enabled: Boolean, highlighted: Boolean): RGBA {
        return when {
            enabled && highlighted -> WorldSelectionScene.SELECTED_FILL
            enabled -> Colors.WHITE
            else -> WorldSelectionScene.DISABLED_FILL
        }
    }

    /**
     * Draws the rounded rectangle background for an action button.
     *
     * @param enabled whether the button is interactive; controls the fill color.
     * @param highlighted whether the button represents the primary action; controls the fill color.
     */
    private fun Container.drawActionButtonBackground(enabled: Boolean, highlighted: Boolean) {
        graphics {
            fill(actionButtonFill(enabled, highlighted)) {
                roundRect(0.0, 0.0, WorldSelectionScene.ACTION_BUTTON_W, WorldSelectionScene.ACTION_BUTTON_H, 9.0, 9.0)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = WorldSelectionScene.STROKE_THICKNESS)) {
                roundRect(0.0, 0.0, WorldSelectionScene.ACTION_BUTTON_W, WorldSelectionScene.ACTION_BUTTON_H, 9.0, 9.0)
            }
        }
    }

    /**
     * Draws a single labeled action button at the given row position.
     *
     * @param label text displayed centered inside the button.
     * @param enabled whether the button is interactive; affects opacity and fill color.
     * @param highlighted whether the button represents the primary action; affects fill color.
     * @param row zero-based row index used to compute the vertical offset.
     * @return the created button container, ready for click handling by the caller.
     */
    private fun Container.drawActionButton(
        label: String,
        enabled: Boolean,
        highlighted: Boolean,
        row: Int = 0
    ): Container {
        return container {
            position(0.0, row * (WorldSelectionScene.ACTION_BUTTON_H + WorldSelectionScene.ACTION_BUTTON_GAP))
            alpha = if (enabled) 1.0 else 0.72
            drawActionButtonBackground(enabled, highlighted)
            text(label, textSize = 28.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
                centerOn(this@container)
            }
        }
    }
}
