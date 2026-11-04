package worldeditor

import domain.Level
import domain.TargetNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import network.CoordinateDTO
import network.CreateTransitionDTO
import network.FieldPropertiesDTO
import network.TrafficSimRestClient
import network.TransitionNode
import network.WorldPropertiesDTO
import rendering.GameAreaScale
import kotlin.math.roundToInt

/**
 * Result of one server transition sync.
 *
 * @property errorMessage user-facing error summary, or null when the sync succeeded.
 */
internal data class TransitionSyncOutcome(
    val errorMessage: String?
)

/**
 * Mirrors editor sink nodes on field borders into coupling-server transitions.
 *
 * The server is the broker for vehicle handover between tables, so every local sink on an inner
 * field edge must have a matching server transition to the neighboring field. The editor level is
 * used as a per-field template: the same edge sink layout is applied to every field of the world.
 */
internal object WorldEditorServerTransitionSync {

    /** Snap distance (in field units) within which a near-edge sink is moved onto the edge. */
    private const val EDGE_SNAP_DISTANCE = 10

    /** Replaceable world lookup used by focused tests. */
    internal var loadWorldCall: (String) -> WorldPropertiesDTO =
        { worldId -> TrafficSimRestClient().getWorld(worldId) }

    /** Replaceable field-list call used by focused tests. */
    internal var loadFieldsCall: (String) -> List<FieldPropertiesDTO> =
        { worldId -> TrafficSimRestClient().getFields(worldId) }

    /** Replaceable transition-delete call used by focused tests. */
    internal var deleteTransitionCall: (String, String, String) -> Unit =
        { worldId, fieldId, nodeId -> TrafficSimRestClient().deleteTransition(worldId, fieldId, nodeId) }

    /** Replaceable transition-create call used by focused tests. */
    internal var createTransitionCall: (String, CreateTransitionDTO) -> Unit =
        { worldId, request -> TrafficSimRestClient().createTransition(worldId, request) }

    /** Restores the replaceable server calls after tests. */
    internal fun resetTestHooks() {
        loadWorldCall = { worldId -> TrafficSimRestClient().getWorld(worldId) }
        loadFieldsCall = { worldId -> TrafficSimRestClient().getFields(worldId) }
        deleteTransitionCall = { worldId, fieldId, nodeId ->
            TrafficSimRestClient().deleteTransition(worldId, fieldId, nodeId)
        }
        createTransitionCall = { worldId, request ->
            TrafficSimRestClient().createTransition(worldId, request)
        }
    }

    /**
     * Replaces the server transitions of [worldId] with the edited edge-sink layout.
     *
     * @return the sync outcome.
     */
    suspend fun syncTransitions(worldId: String, level: Level): TransitionSyncOutcome = withContext(Dispatchers.IO) {
        val failures = mutableListOf<String>()
        try {
            val world = loadWorldCall(worldId)
            val fields = loadFieldsCall(worldId)
            val desiredTransitions = desiredTransitionRequests(level, world, fields)
            deleteExistingTransitions(worldId, fields, failures)
            desiredTransitions.forEach { request ->
                try {
                    createTransitionCall(worldId, request)
                } catch (e: Exception) {
                    failures += "Transition ${request.fromFieldId}->${request.toFieldId} abgelehnt: ${e.message}"
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            failures += e.message ?: "Unbekannter Fehler"
        }

        if (failures.isEmpty()) {
            TransitionSyncOutcome(errorMessage = null)
        } else {
            println("Server transition sync for world $worldId had ${failures.size} failure(s): ${failures.joinToString(" | ")}")
            TransitionSyncOutcome(errorMessage = failures.first().take(120))
        }
    }

    private fun deleteExistingTransitions(
        worldId: String,
        fields: List<FieldPropertiesDTO>,
        failures: MutableList<String>
    ) {
        fields.flatMap { it.fieldTransitions }
            .distinctBy { it.nodeId }
            .forEach { transition ->
                try {
                    deleteTransitionCall(worldId, transition.fromFieldId, transition.nodeId)
                } catch (e: Exception) {
                    failures += "Transition ${transition.nodeId} nicht geloescht: ${e.message}"
                }
            }
    }

    private fun desiredTransitionRequests(
        level: Level,
        world: WorldPropertiesDTO,
        fields: List<FieldPropertiesDTO>
    ): List<CreateTransitionDTO> {
        val fieldsByCoordinate = fields.associateBy { it.coordinate.x to it.coordinate.y }
        val requests = linkedMapOf<TransitionKey, CreateTransitionDTO>()
        val edgeTargets = level.nodes.filterIsInstance<TargetNode>()
            .flatMap { target -> edgeCoordinates(target, world) }

        for (field in fields) {
            for (edgeTarget in edgeTargets) {
                val neighbor = neighborField(field, edgeTarget.edge, world, fieldsByCoordinate) ?: continue
                val coordinate = CoordinateDTO(
                    field.coordinate.x + edgeTarget.coordinate.x,
                    field.coordinate.y + edgeTarget.coordinate.y
                )
                val request = CreateTransitionDTO(
                    fromFieldId = field.fieldId,
                    toFieldId = neighbor.fieldId,
                    coordinate = coordinate,
                    level = edgeTarget.level,
                    biDirectionalWay = false
                )
                requests.putIfAbsent(TransitionKey(request), request)
            }
        }

        return requests.values.toList()
    }

    private fun edgeCoordinates(target: TargetNode, world: WorldPropertiesDTO): List<EdgeTarget> {
        val coordinate = fieldCoordinate(target, world.fieldWidth, world.fieldHeight)
        val edges = mutableListOf<Edge>()
        if (coordinate.x == 0) edges += Edge.LEFT
        if (coordinate.x == world.fieldWidth) edges += Edge.RIGHT
        if (coordinate.y == 0) edges += Edge.BOTTOM
        if (coordinate.y == world.fieldHeight) edges += Edge.TOP
        return edges.map { edge -> EdgeTarget(coordinate, edge, target.elevation) }
    }

    private fun fieldCoordinate(target: TargetNode, fieldWidth: Int, fieldHeight: Int): CoordinateDTO {
        val x = (target.position.x / GameAreaScale.GAME_AREA_W_METER * fieldWidth)
            .roundToInt().coerceIn(0, fieldWidth)
        val y = (target.position.y / GameAreaScale.GAME_AREA_H_METER * fieldHeight)
            .roundToInt().coerceIn(0, fieldHeight)
        return snappedToEdge(x, y, fieldWidth, fieldHeight)
    }

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

    private fun neighborField(
        field: FieldPropertiesDTO,
        edge: Edge,
        world: WorldPropertiesDTO,
        fieldsByCoordinate: Map<Pair<Int, Int>, FieldPropertiesDTO>
    ): FieldPropertiesDTO? {
        val neighborCoordinate = when (edge) {
            Edge.LEFT -> field.coordinate.x - world.fieldWidth to field.coordinate.y
            Edge.RIGHT -> field.coordinate.x + world.fieldWidth to field.coordinate.y
            Edge.BOTTOM -> field.coordinate.x to field.coordinate.y - world.fieldHeight
            Edge.TOP -> field.coordinate.x to field.coordinate.y + world.fieldHeight
        }
        return fieldsByCoordinate[neighborCoordinate]
    }

    private enum class Edge {
        LEFT,
        RIGHT,
        BOTTOM,
        TOP
    }

    private data class EdgeTarget(
        val coordinate: CoordinateDTO,
        val edge: Edge,
        val level: Int
    )

    private data class TransitionKey(
        val fromFieldId: String,
        val toFieldId: String,
        val coordinate: CoordinateDTO,
        val level: Int
    ) {
        constructor(request: CreateTransitionDTO) : this(
            request.fromFieldId,
            request.toFieldId,
            request.coordinate,
            request.level
        )
    }
}
