package scenes

import domain.FieldReference
import domain.Level
import domain.SourceNode
import domain.SourceNodeKind
import domain.TargetNode
import domain.TransitionAssignment
import domain.TransitionAssignmentRegistry
import korlibs.math.geom.Point
import network.CoupledSinkVehicleHandover
import network.FieldPropertiesDTO
import network.FixedTransitionHandoverHandler
import network.ServerConnection
import network.TransitionNode
import network.VehicleDepartureBinding
import network.VehicleDepartureBindingsBySink
import network.VehicleDepartureSender
import rendering.GameAreaScale
import simulation.NoVehicleTargetHandover
import simulation.Simulation
import simulation.VehicleTargetHandoverPort
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Builds the server handover wiring for a multi-table world scene.
 */
object WorldServerHandover {
    /**
     * Creates a simulation for [level] with outgoing and incoming server transition handovers.
     *
     * Missing world, field, transition, source or sink metadata is treated as "not coupled" so a
     * partially configured server world remains playable locally instead of crashing.
     *
     * @param level local world level opened in [WorldScene].
     * @return simulation configured for server handover when possible.
     */
    fun createSimulation(level: Level): Simulation {
        val context = ServerWorldContext.fromCurrentSelection(level)
            ?: return Simulation(level, NoVehicleTargetHandover)

        val handoverPort = context.createOutgoingHandover()
        val simulation = Simulation(level, handoverPort)
        context.installIncomingHandover(simulation)
        return simulation
    }
}

private data class ServerWorldContext(
    val level: Level,
    val localField: FieldPropertiesDTO,
    val fields: List<FieldPropertiesDTO>,
    val layout: FieldLayout,
    val transitions: List<TransitionNode>
) {
    fun createOutgoingHandover(): VehicleTargetHandoverPort {
        val bindings = outgoingBindings()
        if (bindings.isEmpty()) return NoVehicleTargetHandover

        val sender = VehicleDepartureSender { transitionNodeId, fromFieldId, toFieldId, level, vehicleProperties ->
            ServerConnection.sendVehicleDeparture(transitionNodeId, fromFieldId, toFieldId, level, vehicleProperties)
        }
        return CoupledSinkVehicleHandover(bindings, sender)
    }

    fun installIncomingHandover(simulation: Simulation) {
        val mapping = incomingTransitionSinks()
        val assignments = incomingAssignments(mapping)
        if (mapping.isEmpty() || assignments.isEmpty()) {
            ServerConnection.clearOnVehicleMessage()
            return
        }

        val handler = FixedTransitionHandoverHandler(
            simulation = simulation,
            registry = TransitionAssignmentRegistry(assignments),
            transitionSinkResolver = { transitionId -> mapping[transitionId] }
        )
        ServerConnection.setOnVehicleMessage { message ->
            if (simulation === domain.GameState.activeSimulation) {
                handler.handle(message)
            }
        }
    }

    private fun outgoingBindings(): VehicleDepartureBindingsBySink {
        return buildMap {
            for (transition in transitions) {
                val binding = departureBindingFor(transition) ?: continue
                val sink = nearestTarget(transition.localPoint()) ?: continue
                putIfAbsent(sink, binding)
            }
        }
    }

    private fun incomingTransitionSinks(): Map<String, TargetNode> {
        return buildMap {
            for (transition in transitions) {
                if (!transition.canArriveAt(localField.fieldId)) continue
                val sink = nearestTarget(transition.localPoint()) ?: continue
                putIfAbsent(transition.nodeId, sink)
            }
        }
    }

    private fun incomingAssignments(mapping: Map<String, TargetNode>): List<TransitionAssignment> {
        val localRef = layout.referenceFor(localField) ?: return emptyList()
        val assignmentsByKey = linkedMapOf<Pair<TargetNode, String>, TransitionAssignment>()
        for (transition in transitions) {
            val sink = mapping[transition.nodeId] ?: continue
            val neighborRef = layout.referenceFor(transition.otherFieldId(localField.fieldId) ?: continue) ?: continue
            val source = nearestLocalSource(transition.localPoint()) ?: continue
            for (configuration in source.configurations) {
                val key = sink to configuration.vehicleType.name.trim().uppercase()
                assignmentsByKey.putIfAbsent(
                    key,
                    TransitionAssignment(
                        sink = sink,
                        vehicleType = configuration.vehicleType.name,
                        fromField = neighborRef,
                        toField = localRef,
                        targetSource = source
                    )
                )
            }
        }
        return assignmentsByKey.values.toList()
    }

    private fun departureBindingFor(transition: TransitionNode): VehicleDepartureBinding? {
        val localFieldId = localField.fieldId
        return when {
            transition.fromFieldId == localFieldId -> VehicleDepartureBinding(
                transitionNodeId = transition.nodeId,
                fromFieldId = transition.fromFieldId,
                toFieldId = transition.toFieldId,
                level = transition.level
            )
            transition.biDirectionalWay && transition.toFieldId == localFieldId -> VehicleDepartureBinding(
                transitionNodeId = transition.nodeId,
                fromFieldId = transition.toFieldId,
                toFieldId = transition.fromFieldId,
                level = transition.level
            )
            else -> null
        }
    }

    private fun TransitionNode.localPoint(): Point {
        val relativeX = (coordinate.x - localField.coordinate.x).toDouble().coerceIn(0.0, layout.fieldWidth)
        val relativeY = (coordinate.y - localField.coordinate.y).toDouble().coerceIn(0.0, layout.fieldHeight)
        return Point(
            relativeX / layout.fieldWidth * GameAreaScale.GAME_AREA_W_METER,
            relativeY / layout.fieldHeight * GameAreaScale.GAME_AREA_H_METER
        )
    }

    private fun nearestTarget(point: Point): TargetNode? {
        return level.nodes.filterIsInstance<TargetNode>()
            .minByOrNull { distance(it.position, point) }
    }

    private fun nearestLocalSource(point: Point): SourceNode? {
        return level.nodes.filterIsInstance<SourceNode>()
            .filter { it.kind != SourceNodeKind.SERVER_NEUTRAL }
            .filter { it.configurations.isNotEmpty() }
            .minByOrNull { distance(it.position, point) }
    }

    private fun TransitionNode.canArriveAt(fieldId: String): Boolean {
        return toFieldId == fieldId || (biDirectionalWay && fromFieldId == fieldId)
    }

    private fun TransitionNode.otherFieldId(fieldId: String): String? {
        return when (fieldId) {
            fromFieldId -> toFieldId
            toFieldId -> fromFieldId
            else -> null
        }
    }

    companion object {
        fun fromCurrentSelection(level: Level): ServerWorldContext? {
            val world = WorldSelectionScene.openedWorld ?: return null
            val fieldId = ServerConnection.ownTileIdFor(world.id) ?: return null
            val fields = ServerConnection.fieldsFor(world.id)
            val localField = fields.firstOrNull { it.fieldId == fieldId } ?: return null
            val layout = FieldLayout.from(fields) ?: return null
            val transitions = fields.flatMap { it.fieldTransitions }
                .distinctBy { it.nodeId }
                .filter { it.fromFieldId == fieldId || it.toFieldId == fieldId }
            return ServerWorldContext(level, localField, fields, layout, transitions)
        }
    }
}

private data class FieldLayout(
    val fieldsById: Map<String, FieldPropertiesDTO>,
    val fieldWidth: Double,
    val fieldHeight: Double,
    private val minX: Int,
    private val minY: Int
) {
    fun referenceFor(field: FieldPropertiesDTO): FieldReference? = referenceFor(field.fieldId)

    fun referenceFor(fieldId: String): FieldReference? {
        val field = fieldsById[fieldId] ?: return null
        return FieldReference(
            fieldId = field.fieldId,
            column = ((field.coordinate.x - minX) / fieldWidth).roundToInt(),
            row = ((field.coordinate.y - minY) / fieldHeight).roundToInt()
        )
    }

    companion object {
        fun from(fields: List<FieldPropertiesDTO>): FieldLayout? {
            if (fields.isEmpty()) return null
            val xValues = fields.map { it.coordinate.x }.distinct().sorted()
            val yValues = fields.map { it.coordinate.y }.distinct().sorted()
            val width = positiveStep(xValues) ?: return null
            val height = positiveStep(yValues) ?: return null
            return FieldLayout(
                fieldsById = fields.associateBy { it.fieldId },
                fieldWidth = width.toDouble(),
                fieldHeight = height.toDouble(),
                minX = xValues.first(),
                minY = yValues.first()
            )
        }

        private fun positiveStep(values: List<Int>): Int? {
            return values.zipWithNext { left, right -> right - left }
                .filter { it > 0 }
                .minOrNull()
        }
    }
}

private fun distance(left: Point, right: Point): Double = hypot(left.x - right.x, left.y - right.y)
