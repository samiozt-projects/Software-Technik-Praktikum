package domain

import korlibs.image.color.RGBA

/**
 * Identifies one field inside a server world.
 *
 * @property fieldId Stable server-side field id.
 * @property column Horizontal grid coordinate inside the world.
 * @property row Vertical grid coordinate inside the world.
 */
data class FieldReference(
    val fieldId: String,
    val column: Int,
    val row: Int
) {
    /**
     * Validates that this reference can be used as a stable server field key.
     */
    init {
        require(fieldId.isNotBlank()) { "fieldId must not be blank." }
    }

    /**
     * Returns whether [other] is directly adjacent in the world grid.
     *
     * Diagonal fields are intentionally rejected because server transitions connect only horizontal
     * or vertical neighbors.
     *
     * @param other field to compare with.
     * @return true for horizontal or vertical neighbors, false otherwise.
     */
    fun isDirectNeighbor(other: FieldReference): Boolean {
        val columnDistance = kotlin.math.abs(column - other.column)
        val rowDistance = kotlin.math.abs(row - other.row)
        return columnDistance + rowDistance == 1
    }
}

/**
 * A fixed handover assignment from one local sink and vehicle type to one source on a neighbor field.
 *
 * @property sink sink where the vehicle leaves the current field.
 * @property vehicleType server vehicle type string, for example `CAR`, `TRUCK`, or `MOTORCYCLE`.
 * @property fromField field that owns [sink].
 * @property toField directly neighboring field that owns [targetSource].
 * @property targetSource source that must receive matching vehicles on the target field.
 */
data class TransitionAssignment(
    val sink: TargetNode,
    val vehicleType: String,
    val fromField: FieldReference,
    val toField: FieldReference,
    val targetSource: SourceNode
) {
    /**
     * Validates the assignment constraints required by server transitions.
     */
    init {
        require(vehicleType.isNotBlank()) { "vehicleType must not be blank." }
        require(fromField.isDirectNeighbor(toField)) {
            "Transition assignments are only allowed between directly neighboring fields."
        }
    }

    /** Normalized key used for deterministic assignment lookup. */
    private val normalizedKey: TransitionAssignmentKey = TransitionAssignmentKey(sink, normalizeVehicleType(vehicleType))

    /**
     * Returns the normalized lookup key for this assignment.
     */
    internal fun lookupKey(): TransitionAssignmentKey = normalizedKey
}

/**
 * Vehicle properties received from or sent to the server during a field handover.
 *
 * @property color RGBA vehicle color.
 * @property vehicleType server vehicle type string, for example `CAR`, `TRUCK`, or `MOTORCYCLE`.
 * @property speedMetersPerSecond current vehicle speed in metres per second.
 */
data class HandoverVehicleProperties(
    val color: RGBA,
    val vehicleType: String,
    val speedMetersPerSecond: Double
)

/**
 * Result of applying a fixed transition assignment.
 */
sealed class TransitionAssignmentResult {
    /**
     * Matching fixed assignment and local source configuration were found.
     *
     * @property source selected local target source.
     * @property configuration matching source configuration for color and vehicle type.
     * @property speedMetersPerSecond speed that should be used for the injected vehicle.
     */
    data class Accepted(
        val source: SourceNode,
        val configuration: SourceNodeConfiguration,
        val speedMetersPerSecond: Double
    ) : TransitionAssignmentResult()

    /** Vehicle must be discarded because no valid fixed handover target applies. */
    data object Discarded : TransitionAssignmentResult()
}

/**
 * Applies fixed `(sink, vehicle type) -> (neighbor field, source)` assignments to arriving vehicles.
 *
 * The registry does not infer targets from positions. Positions can therefore be arbitrary: only
 * the explicit [TargetNode] and [SourceNode] references matter. This keeps multi-table handovers
 * reproducible even when several sources have the same color and vehicle type.
 *
 * @property assignments fixed transition assignments accepted by the system.
 */
class TransitionAssignmentRegistry(assignments: List<TransitionAssignment>) {
    private val assignmentsByKey: Map<TransitionAssignmentKey, TransitionAssignment> = assignments.associateByUniqueKey()

    /**
     * Applies the fixed handover assignment for [sink] and [vehicle].
     *
     * Vehicles are accepted only on level 0. Without a fixed assignment, with a server-neutral
     * target source, or without a matching target source configuration, the vehicle is discarded.
     *
     * @param sink sink where the vehicle entered the local field.
     * @param level transition elevation level from the server.
     * @param vehicle received vehicle properties.
     * @return accepted target source/configuration or [TransitionAssignmentResult.Discarded].
     */
    fun applyIncomingVehicle(
        sink: TargetNode,
        level: Int,
        vehicle: HandoverVehicleProperties
    ): TransitionAssignmentResult {
        if (level != 0) return TransitionAssignmentResult.Discarded

        val assignment = assignmentsByKey[TransitionAssignmentKey(sink, normalizeVehicleType(vehicle.vehicleType))]
            ?: return TransitionAssignmentResult.Discarded
        val targetSource = assignment.targetSource
        if (targetSource.kind == SourceNodeKind.SERVER_NEUTRAL) return TransitionAssignmentResult.Discarded

        val configuration = targetSource.configurations.firstOrNull { configuration ->
            configuration.color.value == vehicle.color &&
                normalizeVehicleType(configuration.vehicleType.name) == normalizeVehicleType(vehicle.vehicleType)
        } ?: return TransitionAssignmentResult.Discarded

        return TransitionAssignmentResult.Accepted(
            source = targetSource,
            configuration = configuration,
            speedMetersPerSecond = vehicle.speedMetersPerSecond
        )
    }
}

/**
 * Builds a lookup map while rejecting duplicate `(sink, vehicle type)` assignments.
 *
 * @return assignments keyed by normalized sink/vehicle-type pair.
 */
private fun List<TransitionAssignment>.associateByUniqueKey(): Map<TransitionAssignmentKey, TransitionAssignment> {
    val result = mutableMapOf<TransitionAssignmentKey, TransitionAssignment>()
    for (assignment in this) {
        val previous = result.put(assignment.lookupKey(), assignment)
        require(previous == null) {
            "Duplicate transition assignment for the same sink and vehicle type."
        }
    }
    return result
}

/**
 * Assignment lookup key.
 *
 * @property sink source-field sink node.
 * @property vehicleType normalized server vehicle type.
 */
internal data class TransitionAssignmentKey(
    val sink: TargetNode,
    val vehicleType: String
)

/**
 * Normalizes vehicle type names across server enum values and local display names.
 *
 * @param value vehicle type label.
 * @return uppercase vehicle type without surrounding whitespace.
 */
private fun normalizeVehicleType(value: String): String = value.trim().uppercase()
