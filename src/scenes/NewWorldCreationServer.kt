package scenes

import config.ConfigManager
import domain.ConnectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import network.CreateWorldDTO
import network.CreateWorldResponse
import network.FieldPropertiesDTO
import network.NetworkApiException
import network.NetworkSerializationException
import network.ServerConnection
import network.TrafficSimRestClient
import persistence.WorldLevelSelection
import java.io.IOException

/**
 * Signals that a freshly created server world delivered no usable field layout, so tile
 * selection would be impossible in that world.
 *
 * @param message user-facing failure summary.
 * @param cause original load failure, if one was available.
 */
internal class WorldFieldsUnavailableException(
    /** User-facing failure summary. */
    message: String,
    /** Original load failure, if one was available. */
    cause: Throwable? = null
) : IOException(message, cause)

/** Server-facing helpers for the new-world creation flow. */
internal object NewWorldCreationServer {
    /** Replaceable network config lookup used by focused scene tests. */
    internal var networkConfigProvider: () -> ConfigManager.NetworkConfig = {
        ConfigManager.loadNetworkConfig()
    }

    /** Replaceable server create call used by focused scene tests. */
    internal var createWorldCall: (ConfigManager.NetworkConfig, CreateWorldDTO) -> CreateWorldResponse =
        { config, request -> TrafficSimRestClient(config).createWorld(request) }

    /** Replaceable field-loading call used by focused scene tests. */
    internal var loadFieldsCall: (ConfigManager.NetworkConfig, String) -> List<FieldPropertiesDTO> =
        { config, worldId -> TrafficSimRestClient(config).getFields(worldId) }

    /** Replaceable world-delete call used by focused scene tests. */
    internal var deleteWorldCall: (ConfigManager.NetworkConfig, String, String) -> Unit =
        { config, worldId, deleteToken ->
            TrafficSimRestClient(config).deleteWorld(worldId, worldDeleteToken = deleteToken)
        }

    /** Restores replaceable server calls after tests. */
    internal fun resetTestHooks() {
        networkConfigProvider = { ConfigManager.loadNetworkConfig() }
        createWorldCall = { config, request -> TrafficSimRestClient(config).createWorld(request) }
        loadFieldsCall = { config, worldId -> TrafficSimRestClient(config).getFields(worldId) }
        deleteWorldCall = { config, worldId, deleteToken ->
            TrafficSimRestClient(config).deleteWorld(worldId, worldDeleteToken = deleteToken)
        }
    }

    /** Builds the server request for a new world with fixed dimensions. */
    fun createWorldRequest(deleteToken: String): CreateWorldDTO = CreateWorldDTO(
        columns = NewWorldCreationScene.DEFAULT_WORLD_COLUMNS,
        rows = NewWorldCreationScene.DEFAULT_WORLD_ROWS,
        fieldHeight = NewWorldCreationScene.DEFAULT_FIELD_HEIGHT,
        fieldWidth = NewWorldCreationScene.DEFAULT_FIELD_WIDTH,
        deleteToken = deleteToken
    )

    /** Maps create-flow failures to concise user-facing messages. */
    fun errorMessageFor(error: Throwable): String {
        return when (error) {
            is NetworkApiException -> "Serverfehler ${error.statusCode}: ${serverBodySummary(error.responseBody)}"
            is NetworkSerializationException -> "Serverantwort konnte nicht gelesen werden"
            is WorldFieldsUnavailableException -> error.message ?: "Welt konnte nicht erstellt werden"
            is IOException -> "Server nicht erreichbar"
            else -> "Welt konnte nicht erstellt werden"
        }
    }

    /** Creates a real server world when possible, otherwise falls back to a temporary local world. */
    suspend fun createServerWorldEntry(
        worldName: String,
        levelSelection: WorldLevelSelection,
        deleteToken: String,
        temporaryWorldIdFactory: () -> String,
        temporaryLocalCreationEnabled: Boolean
    ): WorldSelectionScene.WorldEntry {
        val config = networkConfigProvider()
        if (!config.active || ServerConnection.connectionState != ConnectionState.CONNECTED) {
            return createTemporaryWorldEntry(worldName, levelSelection, deleteToken, temporaryWorldIdFactory)
        }

        val response = try {
            withContext(Dispatchers.IO) {
                createWorldCall(config, createWorldRequest(deleteToken))
            }
        } catch (error: Throwable) {
            if (shouldUseTemporaryWorldFallback(error, temporaryLocalCreationEnabled)) {
                return createTemporaryWorldEntry(worldName, levelSelection, deleteToken, temporaryWorldIdFactory)
            } else {
                throw error
            }
        }

        val fields = loadCreatedWorldFields(config, response.worldId, deleteToken)

        val world = WorldSelectionScene.rememberOpenedWorld(WorldSelectionScene.WorldEntry(
            id = response.worldId,
            number = 0,
            name = worldName,
            source = "Server",
            columns = NewWorldCreationScene.DEFAULT_WORLD_COLUMNS,
            rows = NewWorldCreationScene.DEFAULT_WORLD_ROWS,
            levelSelection = levelSelection,
            deleteToken = deleteToken
        ))
        ServerConnection.registerFieldTiles(world.id, fields)
        return world
    }

    /**
     * Loads the server-generated fields of a freshly created world.
     *
     * When no usable fields can be loaded, the just-created world is deleted again (best effort,
     * using its delete token) so failed creations do not accumulate orphaned worlds on the server.
     */
    private suspend fun loadCreatedWorldFields(
        config: ConfigManager.NetworkConfig,
        worldId: String,
        deleteToken: String
    ): List<FieldPropertiesDTO> {
        val fields = try {
            withContext(Dispatchers.IO) {
                loadFieldsCall(config, worldId)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            deleteCreatedWorldQuietly(config, worldId, deleteToken)
            throw WorldFieldsUnavailableException("Felder der neuen Welt konnten nicht geladen werden", error)
        }
        if (fields.isEmpty()) {
            deleteCreatedWorldQuietly(config, worldId, deleteToken)
            throw WorldFieldsUnavailableException("Server lieferte keine Felder für die neue Welt")
        }
        return fields
    }

    /** Best-effort delete of a just-created world that turned out to be unusable. */
    private suspend fun deleteCreatedWorldQuietly(
        config: ConfigManager.NetworkConfig,
        worldId: String,
        deleteToken: String
    ) {
        try {
            withContext(Dispatchers.IO) {
                deleteWorldCall(config, worldId, deleteToken)
            }
        } catch (cleanupError: Exception) {
            println("Could not delete unusable world $worldId: ${cleanupError.message}")
        }
    }

    /** Maps the selected starting level to the integer template id required by the editor API. */
    fun editorTemplateLevelId(levelSelection: WorldLevelSelection): Int {
        return when (levelSelection) {
            is WorldLevelSelection.PredefinedLevel -> levelSelection.levelId
            else -> 1
        }
    }

    /** Registers a temporary local world used for UI testing without a coupling server. */
    private fun createTemporaryWorldEntry(
        worldName: String,
        levelSelection: WorldLevelSelection,
        deleteToken: String,
        temporaryWorldIdFactory: () -> String
    ): WorldSelectionScene.WorldEntry {
        val world = WorldSelectionScene.rememberOpenedWorld(WorldSelectionScene.WorldEntry(
            id = temporaryWorldIdFactory(),
            number = 0,
            name = worldName,
            source = "Lokal",
            columns = NewWorldCreationScene.DEFAULT_WORLD_COLUMNS,
            rows = NewWorldCreationScene.DEFAULT_WORLD_ROWS,
            levelSelection = levelSelection,
            deleteToken = deleteToken
        ))
        ServerConnection.registerAllFreeTiles(world.id, world.columns, world.rows)
        return world
    }

    /** Returns whether [error] should fall back to local temporary creation. */
    private fun shouldUseTemporaryWorldFallback(error: Throwable, temporaryLocalCreationEnabled: Boolean): Boolean {
        return temporaryLocalCreationEnabled &&
            error is IOException &&
            error !is NetworkApiException &&
            error !is NetworkSerializationException
    }

    /** Returns a short response-body summary for server error messages. */
    private fun serverBodySummary(body: String): String {
        val cleanBody = body.trim().trim('"').ifEmpty { "Keine Details" }
        return cleanBody.take(90)
    }
}
