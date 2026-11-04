package scenes

import network.FieldRegistrationResult
import network.ServerConnection
import korlibs.image.color.Colors
import audio.onClickWithSound
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import scenes.LevelScene.Companion.TABLE_PX_H
import scenes.LevelScene.Companion.TABLE_PX_W
import ui.ControlButtons
import ui.WorldPlaceholderSceneUI

/**
 * Placeholder scene for an opened world in multi-table mode.
 *
 * Shows the selected world and lets the local table claim a free table tile for that world.
 * Table-tile occupancy is tracked per world through [ServerConnection], so claiming a tile
 * here never affects any other world's layout. The scene keeps the flow from world selection
 * to tile claiming testable until the real multi-table world runtime exists.
 */
class WorldPlaceholderScene : Scene() {

    private var tileGridContainer: Container? = null
    private var tileRegistrationInProgress = false
    private var tileRegistrationMessage: String? = null

    /** Identifies which scene opened the tile-selection placeholder. */
    enum class BackDestination {
        /** The world-selection list opened this placeholder. */
        WORLD_SELECTION,

        /** The server menu opened this placeholder directly for the current world. */
        SERVER_MENU
    }

    companion object {
        /** Scene that the back button should return to. */
        var backDestination: BackDestination = BackDestination.WORLD_SELECTION

        private var confirmedWorldId: String? = null
        private var confirmedTileId: String? = null

        /**
         * Returns the confirmed tile ID for [worldId], or null if no tile is confirmed
         * or the confirmation belongs to a different world.
         */
        fun confirmedTileIdFor(worldId: String): String? =
            if (confirmedWorldId == worldId) confirmedTileId else null

        /** Marks [tileId] in [worldId] as the confirmed (green) tile. */
        fun confirmTile(worldId: String, tileId: String) {
            confirmedWorldId = worldId
            confirmedTileId = tileId
        }

        /** Clears the confirmed tile, e.g. on "Entfernen" or disconnect. */
        fun clearConfirmedTile() {
            confirmedWorldId = null
            confirmedTileId = null
        }

        /** Clears mutable navigation and tile-selection state for scene tests. */
        internal fun resetLocalStateForTesting() {
            clearConfirmedTile()
            backDestination = BackDestination.WORLD_SELECTION
        }
    }

    /** Root container of this scene, captured in [sceneMain] so private helper methods can draw into it. */
    private lateinit var sceneRoot: SContainer

    /**
     * Builds the placeholder view for the currently opened world.
     */
    override suspend fun SContainer.sceneMain() {
        sceneRoot = this

        graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, TABLE_PX_W, TABLE_PX_H)
            }
        }

        ControlButtons.drawBackButton(this).onClickWithSound {
            launch {
                when (backDestination) {
                    BackDestination.WORLD_SELECTION -> sceneContainer.changeTo<WorldSelectionScene>()
                    BackDestination.SERVER_MENU -> sceneContainer.changeTo<ServerMenuScene>()
                }
            }
        }

        ControlButtons.drawCloseButton(this).onClickWithSound {
            launch {
                sceneContainer.changeTo<LevelScene>()
            }
        }

        val label = WorldSelectionScene.openedWorld?.let {
            WorldSelectionScene.worldLabel(it)
        } ?: "Keine Welt ausgewählt"

        WorldPlaceholderSceneUI.drawTitleBar(this, "Welt geöffnet", label)
        redrawTileGrid()
    }

    /**
     * Rebuilds the table-tile grid for the currently opened world.
     */
    private fun redrawTileGrid() {
        tileGridContainer?.let { it.parent?.removeChild(it) }

        if (WorldSelectionScene.openedWorld != null) {
            tileGridContainer = drawTileGrid(sceneRoot)
        }
    }

    /**
     * Draws the 3-column grid of selectable table tiles for the opened world.
     */
    private fun drawTileGrid(content: SContainer): Container {
        val worldId = WorldSelectionScene.openedWorld?.id ?: return content.container { }

        return content.container {
            WorldPlaceholderSceneUI.drawTileGrid(this, worldId, confirmedTileIdFor(worldId)) { tileId ->
                if (!tileRegistrationInProgress && confirmedTileIdFor(worldId) == null) {
                    tileRegistrationMessage = null
                    WorldPlaceholderSceneLogic.onTileSelected(worldId, tileId, ::redrawTileGrid)
                }
            }
            drawTileSelectionActionButton(worldId)
            WorldPlaceholderSceneUI.drawTileSelectionMessage(this, worldId, tileRegistrationMessage)
        }
    }

    /**
     * Draws the action button for tile selection.
     */
    private fun Container.drawTileSelectionActionButton(worldId: String) {
        val ownTileId = ServerConnection.ownTileIdFor(worldId)
        val hasSelection = ownTileId != null
        val showingConfirmedTile = confirmedTileIdFor(worldId) != null && ownTileId == confirmedTileIdFor(worldId)

        WorldPlaceholderSceneUI.drawTileSelectionActionButton(
            this,
            worldId,
            hasSelection,
            showingConfirmedTile,
            ::onTileSelectionConfirmed,
            { onTileSelectionRemoved(worldId) },
            { launch { WorldPlaceholderSceneLogic.navigateToPlaceholderTargetLevel(sceneContainer) } }
        )
    }

    /**
     * Confirms the currently claimed tile: it turns green and stays green while browsing.
     */
    private fun onTileSelectionConfirmed() {
        val worldId = WorldSelectionScene.openedWorld?.id ?: return
        val tileId = ServerConnection.ownTileIdFor(worldId) ?: return
        runTileFieldOperation(
            fallbackMessage = "Feld konnte nicht registriert werden",
            onSuccess = { confirmTile(worldId, tileId) }
        ) { WorldPlaceholderSceneLogic.onTileSelectionConfirmed(tileId) }
    }

    /**
     * Releases the confirmed tile and resets the selection state.
     */
    private fun onTileSelectionRemoved(worldId: String) {
        runTileFieldOperation(
            fallbackMessage = "Feld konnte nicht freigegeben werden",
            onSuccess = ::clearConfirmedTile
        ) { WorldPlaceholderSceneLogic.onTileSelectionRemoved(worldId) }
    }

    /**
     * Runs one asynchronous field operation (register/release) with the shared in-progress lock,
     * error display, and grid redraw handling.
     */
    private fun runTileFieldOperation(
        fallbackMessage: String,
        onSuccess: () -> Unit,
        operation: suspend () -> FieldRegistrationResult
    ) {
        if (tileRegistrationInProgress) return

        tileRegistrationInProgress = true
        tileRegistrationMessage = null
        redrawTileGrid()
        launch {
            val result = try {
                operation()
            } catch (cancellation: CancellationException) {
                tileRegistrationInProgress = false
                throw cancellation
            } catch (error: Throwable) {
                FieldRegistrationResult.Failure(
                    "$fallbackMessage: ${error.message ?: "Unbekannter Fehler"}"
                )
            }
            tileRegistrationInProgress = false
            when (result) {
                is FieldRegistrationResult.Success -> onSuccess()
                is FieldRegistrationResult.Failure -> tileRegistrationMessage = result.message
            }
            redrawTileGrid()
        }
    }
}
