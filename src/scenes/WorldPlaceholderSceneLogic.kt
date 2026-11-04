package scenes

import domain.ConnectionState
import domain.Level
import network.FieldRegistrationResult
import network.ServerConnection
import korlibs.korge.scene.SceneContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import persistence.EditorWorldSaves
import persistence.LevelSerializer
import persistence.WorldTemplate
import persistence.loadLevelForWorld
import ui.Assets
import worldeditor.WorldEditorRepository
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Upper bound for one field registration/release round trip. Generously above the WebSocket
 * connect timeout (10s) plus the registration acknowledgment wait (5s), so a result future that
 * never completes cannot lock the tile selection forever.
 */
private const val FIELD_OPERATION_TIMEOUT_MS = 20_000L

/**
 * Logic methods for WorldPlaceholderScene.
 * Contains all methods for handling user actions and state changes.
 */
internal object WorldPlaceholderSceneLogic {
    /** Replaceable field-registration call used by focused scene tests. */
    internal var connectSelectedFieldCall: (String) -> CompletableFuture<FieldRegistrationResult> =
        { fieldId -> ServerConnection.connectSelectedField(fieldId) }

    /** Replaceable field-deregistration call used by focused scene tests. */
    internal var disconnectSelectedFieldCall: () -> CompletableFuture<FieldRegistrationResult> =
        { ServerConnection.disconnectSelectedField() }

    /** Restores replaceable calls after tests. */
    internal fun resetTestHooks() {
        connectSelectedFieldCall = { fieldId -> ServerConnection.connectSelectedField(fieldId) }
        disconnectSelectedFieldCall = { ServerConnection.disconnectSelectedField() }
    }

    /**
     * Claims a tile for the local table.
     *
     * @param worldId ID of the current world.
     * @param tileId ID of the selected tile.
     * @param onRedraw Callback to update the UI.
     */
    fun onTileSelected(worldId: String, tileId: String, onRedraw: () -> Unit) {
        ServerConnection.claimTile(worldId, tileId)
        onRedraw()
    }

    /**
     * Confirms the tile selection and registers the selected server field for live WebSocket traffic.
     *
     * @param fieldId real server field ID represented by the selected tile.
     * @return success or a user-facing error when the field registration failed.
     */
    suspend fun onTileSelectionConfirmed(fieldId: String): FieldRegistrationResult {
        return awaitFieldOperation { connectSelectedFieldCall(fieldId) }
    }

    /**
     * Removes the tile selection.
     *
     * @param worldId ID of the current world.
     * @return success or a user-facing error when the server field could not be released.
     */
    suspend fun onTileSelectionRemoved(worldId: String): FieldRegistrationResult {
        val result = awaitFieldOperation { disconnectSelectedFieldCall() }
        if (result is FieldRegistrationResult.Success) {
            ServerConnection.releaseTile(worldId)
        }
        return result
    }

    /**
     * Awaits a field operation off the render thread with a hard timeout, so a future that never
     * completes surfaces as a visible error instead of locking the tile selection permanently.
     */
    private suspend fun awaitFieldOperation(
        operation: () -> CompletableFuture<FieldRegistrationResult>
    ): FieldRegistrationResult {
        return withContext(Dispatchers.IO) {
            try {
                operation()[FIELD_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS]
            } catch (timeout: TimeoutException) {
                FieldRegistrationResult.Failure("Der Server hat nicht rechtzeitig geantwortet.")
            }
        }
    }

    /**
     * Navigates to the opened world's level after the tile selection is confirmed.
     *
     * Loads the world's saved editor level (server editor state) so sources and sinks placed in
     * the world editor appear in the running world, and opens it in [WorldScene]. Falls back to
     * the world's local level data or a blank [WorldTemplate] world when no saved state exists.
     *
     * @param sceneContainer the scene container used for navigation.
     */
    suspend fun navigateToPlaceholderTargetLevel(sceneContainer: SceneContainer) {
        val level = loadOpenedWorldLevel(WorldSelectionScene.openedWorld)
        LevelMenuScene.assignLoadedLevel(level, predefinedLevelId = null)
        Assets.preloadVehicleGraphics(level.vehicleTypes)
        sceneContainer.changeTo { WorldScene() }
    }

    /**
     * Loads the playable level for [world]: the saved server editor state when connected, then
     * the locally saved editor state, then the world's saved file or original level selection,
     * falling back to a blank template world.
     */
    private suspend fun loadOpenedWorldLevel(world: WorldSelectionScene.WorldEntry?): Level {
        if (world == null) return WorldTemplate.createBlankWorld()
        loadSavedEditorLevel(world)?.let { return it }
        return try {
            // The locally saved editor state is newer than the world's original template
            // selection, so it must win — otherwise saved edits would silently disappear.
            EditorWorldSaves.loadLevelOrNull(world.id)
                ?: world.savedLevelPath?.let { LevelSerializer.loadFromAppVfs(it) }
                ?: world.levelSelection?.loadLevelForWorld(world.name)
                ?: WorldTemplate.createBlankWorld(world.name)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            println("Could not load local level for world ${world.id}: ${e.message}")
            WorldTemplate.createBlankWorld(world.name)
        }
    }

    /**
     * Loads the world's saved editor level from the server, or null when the table is not
     * connected or the world has no server-side editor state (e.g. temporary local worlds).
     */
    private suspend fun loadSavedEditorLevel(world: WorldSelectionScene.WorldEntry): Level? {
        if (ServerConnection.connectionState != ConnectionState.CONNECTED) return null
        return try {
            val state = WorldEditorRepository.gateway.getWorldEditorState(world.id)
            LevelSerializer.fromJsonString(state.levelJson, "world-editor:${world.id}")
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            println("Could not load saved editor level for world ${world.id}: ${e.message}")
            null
        }
    }
}
