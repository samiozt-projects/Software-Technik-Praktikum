package worldeditor

import domain.Level
import domain.SourceNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import network.CoordinateDTO
import network.CreateQuelleDTO
import network.FieldPropertiesDTO
import network.QuelleNode
import network.TrafficSimRestClient
import network.WorldPropertiesDTO
import rendering.GameAreaScale
import kotlin.math.roundToInt

/**
 * Result of one server source sync.
 *
 * @property errorMessage user-facing error summary, or null when the sync succeeded.
 * @property rejectedSources level sources whose transfer the server rejected.
 */
internal data class SourceSyncOutcome(
    val errorMessage: String?,
    val rejectedSources: List<SourceNode>
)

/**
 * Pushes the editor's source layout to the coupling server after a world save.
 *
 * The server offers no editor-state endpoints, but it does model per-field sources
 * (`POST`/`DELETE /worlds/{w}/fields/{f}/sources`) with three hard rules (verified against the
 * live server): the source coordinate is WORLD-GLOBAL (field origin plus position inside the
 * field), it must sit on an outer edge of the whole world (inner field borders are rejected
 * with HTTP 400), and the spawn interval must be at least [MIN_SPAWN_INTERVAL_SECONDS].
 *
 * The sync therefore assigns each edge source only to the fields whose matching edge is an
 * outer world edge, and translates its coordinate into the field's world-global position.
 * Sources placed away from the level border (and sinks, which the server does not model at
 * all) stay local-only in the saved level JSON.
 */
internal object WorldEditorServerSourceSync {

    /** Snap distance (in field units) within which a near-edge source is moved onto the edge. */
    private const val EDGE_SNAP_DISTANCE = 10

    /** Smallest spawn interval the server accepts ("must be at least 0.1 seconds"). */
    private const val MIN_SPAWN_INTERVAL_SECONDS = 0.1

    /** Outer world edges a source or field can lie on. */
    private enum class WorldEdge {
        /** Left edge of the complete server world grid. */
        LEFT,

        /** Right edge of the complete server world grid. */
        RIGHT,

        /** Top edge of the complete server world grid. */
        TOP,

        /** Bottom edge of the complete server world grid. */
        BOTTOM
    }

    /**
     * One level source mapped into the server's field grid.
     *
     * @property node editor source represented by this server request.
     * @property coordinate source coordinate inside one server field.
     * @property spawnInterval server spawn interval in seconds.
     * @property edges outer world edges touched by [coordinate].
     */
    private data class SourceRequest(
        val node: SourceNode,
        val coordinate: CoordinateDTO,
        val spawnInterval: Double,
        val edges: Set<WorldEdge>
    )

    /** Replaceable world lookup used by focused tests. */
    internal var loadWorldCall: (String) -> WorldPropertiesDTO =
        { worldId -> TrafficSimRestClient().getWorld(worldId) }

    /** Replaceable field-list call used by focused tests. */
    internal var loadFieldsCall: (String) -> List<FieldPropertiesDTO> =
        { worldId -> TrafficSimRestClient().getFields(worldId) }

    /** Replaceable source-list call used by focused tests. */
    internal var loadSourcesCall: (String, String) -> List<QuelleNode> =
        { worldId, fieldId -> TrafficSimRestClient().getSources(worldId, fieldId) }

    /** Replaceable source-create call used by focused tests. */
    internal var createSourceCall: (String, String, CreateQuelleDTO) -> Unit =
        { worldId, fieldId, request -> TrafficSimRestClient().createSource(worldId, fieldId, request) }

    /** Replaceable source-delete call used by focused tests. */
    internal var deleteSourceCall: (String, String, String) -> Unit =
        { worldId, fieldId, nodeId -> TrafficSimRestClient().deleteSource(worldId, fieldId, nodeId) }

    /** Restores the replaceable server calls after tests. */
    internal fun resetTestHooks() {
        loadWorldCall = { worldId -> TrafficSimRestClient().getWorld(worldId) }
        loadFieldsCall = { worldId -> TrafficSimRestClient().getFields(worldId) }
        loadSourcesCall = { worldId, fieldId -> TrafficSimRestClient().getSources(worldId, fieldId) }
        createSourceCall = { worldId, fieldId, request ->
            TrafficSimRestClient().createSource(worldId, fieldId, request)
        }
        deleteSourceCall = { worldId, fieldId, nodeId ->
            TrafficSimRestClient().deleteSource(worldId, fieldId, nodeId)
        }
    }

    /**
     * Replaces the server sources of [worldId] with the edge sources of [level].
     *
     * @return the sync outcome, including the level sources the server rejected.
     */
    suspend fun syncSources(worldId: String, level: Level): SourceSyncOutcome = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        val rejectedSources = mutableListOf<SourceNode>()
        try {
            val world = loadWorldCall(worldId)
            val requests = edgeSourceRequests(level, world)
            loadFieldsCall(worldId).forEach { field ->
                replaceFieldSources(worldId, field, world, requests, failures, rejectedSources)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            failures += e.message ?: "Unbekannter Fehler"
        }

        if (failures.isEmpty()) {
            SourceSyncOutcome(errorMessage = null, rejectedSources = emptyList())
        } else {
            println("Server source sync for world $worldId had ${failures.size} failure(s): ${failures.joinToString(" | ")}")
            SourceSyncOutcome(
                errorMessage = failures.first().take(120),
                rejectedSources = rejectedSources.distinct()
            )
        }
    }

    /**
     * Deletes the field's existing sources and creates the edited edge sources whose world edge
     * belongs to this field, collecting failures and rejected level sources.
     */
    private fun replaceFieldSources(
        worldId: String,
        field: FieldPropertiesDTO,
        world: WorldPropertiesDTO,
        requests: List<SourceRequest>,
        failures: MutableList<String>,
        rejectedSources: MutableList<SourceNode>
    ) {
        try {
            loadSourcesCall(worldId, field.fieldId).forEach { existing ->
                try {
                    deleteSourceCall(worldId, field.fieldId, existing.nodeId)
                } catch (e: Exception) {
                    failures += "Quelle ${existing.nodeId} nicht geloescht: ${e.message}"
                }
            }
        } catch (e: Exception) {
            failures += "Quellen von Feld ${field.fieldId} nicht geladen: ${e.message}"
        }

        val fieldEdges = outerEdgesOf(field, world)
        requests.filter { it.edges.intersect(fieldEdges).isNotEmpty() }.forEach { request ->
            // The server expects world-global coordinates: field origin plus in-field position.
            val worldCoordinate = CoordinateDTO(
                field.coordinate.x + request.coordinate.x,
                field.coordinate.y + request.coordinate.y
            )
            try {
                createSourceCall(
                    worldId,
                    field.fieldId,
                    CreateQuelleDTO(field.fieldId, worldCoordinate, request.spawnInterval)
                )
            } catch (e: Exception) {
                failures += "Quelle bei (${worldCoordinate.x},${worldCoordinate.y}) abgelehnt: ${e.message}"
                rejectedSources += request.node
            }
        }
    }

    /**
     * Maps every level border source into a create request in server field coordinates.
     *
     * Sources placed away from the level border cannot be represented on the server (it only
     * models outer-edge spawners) and are skipped; the saved level JSON still contains them.
     */
    private fun edgeSourceRequests(level: Level, world: WorldPropertiesDTO): List<SourceRequest> {
        val requests = mutableListOf<SourceRequest>()
        level.nodes.filterIsInstance<SourceNode>().forEach { source ->
            val coordinate = fieldCoordinate(source, world.fieldWidth, world.fieldHeight)
            val edges = edgesOf(coordinate, world.fieldWidth, world.fieldHeight)
            if (edges.isEmpty()) {
                println(
                    "Server source sync: source at (${source.position.x}, ${source.position.y}) " +
                        "is not on the level border and stays local-only."
                )
            } else {
                requests += SourceRequest(source, coordinate, combinedSpawnInterval(source), edges)
            }
        }
        return requests
    }

    /**
     * Scales a level position (in [GameAreaScale] meters) into the server's field grid, clamped
     * to the field bounds. Near-edge sources are snapped onto the exact edge, because the server
     * only accepts spawners exactly on edges.
     */
    private fun fieldCoordinate(source: SourceNode, fieldWidth: Int, fieldHeight: Int): CoordinateDTO {
        val x = (source.position.x / GameAreaScale.GAME_AREA_W_METER * fieldWidth)
            .roundToInt().coerceIn(0, fieldWidth)
        val y = (source.position.y / GameAreaScale.GAME_AREA_H_METER * fieldHeight)
            .roundToInt().coerceIn(0, fieldHeight)
        return snappedToEdge(x, y, fieldWidth, fieldHeight)
    }

    /** Moves a coordinate onto the nearest field edge when it lies within [EDGE_SNAP_DISTANCE]. */
    private fun snappedToEdge(x: Int, y: Int, fieldWidth: Int, fieldHeight: Int): CoordinateDTO {
        val edgeDistances = listOf(x, fieldWidth - x, y, fieldHeight - y)
        val nearest = edgeDistances.min()
        if (nearest == 0 || nearest > EDGE_SNAP_DISTANCE) return CoordinateDTO(x, y)
        return when (edgeDistances.indexOf(nearest)) {
            0 -> CoordinateDTO(0, y)
            1 -> CoordinateDTO(fieldWidth, y)
            2 -> CoordinateDTO(x, 0)
            else -> CoordinateDTO(x, fieldHeight)
        }
    }

    /** Edges of the field grid that [coordinate] lies on; empty when it is not on any edge. */
    private fun edgesOf(coordinate: CoordinateDTO, fieldWidth: Int, fieldHeight: Int): Set<WorldEdge> {
        val edges = mutableSetOf<WorldEdge>()
        if (coordinate.x == 0) edges += WorldEdge.LEFT
        if (coordinate.x == fieldWidth) edges += WorldEdge.RIGHT
        if (coordinate.y == 0) edges += WorldEdge.TOP
        if (coordinate.y == fieldHeight) edges += WorldEdge.BOTTOM
        return edges
    }

    /**
     * Outer world edges of [field]: only edges that face the outside of the whole world grid,
     * because the server rejects spawners on inner field borders.
     */
    private fun outerEdgesOf(field: FieldPropertiesDTO, world: WorldPropertiesDTO): Set<WorldEdge> {
        val edges = mutableSetOf<WorldEdge>()
        if (field.coordinate.x == 0) edges += WorldEdge.LEFT
        if (field.coordinate.x + world.fieldWidth == world.columns * world.fieldWidth) edges += WorldEdge.RIGHT
        if (field.coordinate.y == 0) edges += WorldEdge.TOP
        if (field.coordinate.y + world.fieldHeight == world.rows * world.fieldHeight) edges += WorldEdge.BOTTOM
        return edges
    }

    /**
     * Combines all configuration spawn rates of [source] into one server spawn interval: the
     * server models one interval per source, so the intervals of all configurations are merged
     * into their combined rate, clamped to the server's minimum interval.
     */
    private fun combinedSpawnInterval(source: SourceNode): Double {
        val combinedRate = source.configurations
            .filter { it.generationSpeed > 0.0 }
            .sumOf { 1.0 / it.generationSpeed }
        val interval = if (combinedRate > 0.0) 1.0 / combinedRate else WorldEditorDefaults.DEFAULT_GENERATION_SPEED
        return interval.coerceAtLeast(MIN_SPAWN_INTERVAL_SECONDS)
    }
}
