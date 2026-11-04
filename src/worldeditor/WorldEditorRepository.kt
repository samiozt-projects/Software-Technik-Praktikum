package worldeditor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import network.SaveWorldEditorDTO
import network.TrafficSimRestClient
import network.WorldEditorStateDTO

/** Gateway interface for loading and saving server-backed world editor states. */
interface WorldEditorGateway {
    /**
     * Loads the editor state for a server-backed world.
     *
     * @param worldId ID of the world whose editor state is requested.
     * @return persisted editor state for the requested world.
     */
    suspend fun getWorldEditorState(worldId: String): WorldEditorStateDTO

    /** Creates a new world from [request]. */
    suspend fun createEditorWorld(request: SaveWorldEditorDTO): WorldEditorStateDTO

    /** Updates an existing world from [request]. */
    suspend fun updateEditorWorld(worldId: String, request: SaveWorldEditorDTO): WorldEditorStateDTO
}

/** REST-backed implementation of [WorldEditorGateway]. */
class RestWorldEditorGateway(
    private val clientFactory: () -> TrafficSimRestClient = { TrafficSimRestClient() }
) : WorldEditorGateway {
    /** Loads the editor state through a REST client on an IO dispatcher. */
    override suspend fun getWorldEditorState(worldId: String): WorldEditorStateDTO = withContext(Dispatchers.IO) {
        clientFactory().getWorldEditorState(worldId)
    }

    /** Creates a new editor-backed world through a REST client on an IO dispatcher. */
    override suspend fun createEditorWorld(request: SaveWorldEditorDTO): WorldEditorStateDTO = withContext(Dispatchers.IO) {
        clientFactory().createEditorWorld(request)
    }

    /** Updates an existing editor-backed world through a REST client on an IO dispatcher. */
    override suspend fun updateEditorWorld(worldId: String, request: SaveWorldEditorDTO): WorldEditorStateDTO = withContext(Dispatchers.IO) {
        clientFactory().updateEditorWorld(worldId, request)
    }
}

/** Mutable repository facade used by scenes and tests. */
object WorldEditorRepository {
    /** Active gateway. Tests may replace this with an in-memory implementation. */
    var gateway: WorldEditorGateway = RestWorldEditorGateway()
}
