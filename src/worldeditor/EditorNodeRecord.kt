package worldeditor

import domain.Color
import domain.Node
import domain.SourceNode
import domain.TargetNode
import network.WorldEditorNodeDTO
import network.WorldEditorNodeKind

/** Mutable lookup table for editor-managed source and sink records by stable editor ID. */
internal typealias EditorRecordMap = MutableMap<String, EditorNodeRecord>

/**
 * Internal bridge between an editor node DTO and the mutable level node it controls.
 *
 * Source colors and vehicle types live directly in the domain node as one
 * [domain.SourceNodeConfiguration] per color-and-type combination. Sink colors and vehicle types
 * live in [sinkColors] and [storedVehicleTypeNames] because the domain [TargetNode] stores neither
 * editor metadata nor vehicle types; the domain node is kept in sync with the single sink color.
 *
 * @property id Stable editor-local identifier.
 * @property kind Whether the record controls a source or a sink.
 * @property node Mutable domain node inside the edited level.
 */
internal class EditorNodeRecord(
    val id: String,
    val kind: WorldEditorNodeKind,
    val node: Node,
    initialVehicleTypeNames: List<String>,
    initialSinkColors: List<Color> = emptyList()
) {
    /**
     * The selected color of a sink. Empty for source records, whose colors are read from the
     * domain configurations instead.
     */
    val sinkColors: MutableList<Color> = when {
        node !is TargetNode -> mutableListOf()
        initialSinkColors.isNotEmpty() -> mutableListOf(initialSinkColors.first())
        else -> mutableListOf(node.color)
    }

    /**
     * All vehicle types of a sink, in selection order; for sources this only serves as a
     * fallback while the domain node has no configurations.
     */
    val storedVehicleTypeNames: MutableList<String> = initialVehicleTypeNames.distinct().toMutableList()

    /** Names of all colors currently assigned to this record's node, in selection order. */
    fun colorNames(): List<String> = when (node) {
        is SourceNode -> node.configurations.map { it.color.name }.distinct()
        is TargetNode -> sinkColors.map { it.name }
        else -> emptyList()
    }

    /** Names of all vehicle types currently assigned to this record's node, in selection order. */
    fun vehicleTypeNames(): List<String> = when (node) {
        is SourceNode -> node.configurations.map { it.vehicleType.name }.distinct()
            .ifEmpty { storedVehicleTypeNames.toList() }
        else -> storedVehicleTypeNames.toList()
    }

    /** Converts this internal record into the server-facing editor node DTO. */
    fun toDto(): WorldEditorNodeDTO = WorldEditorNodeDTO(
        id = id,
        kind = kind,
        x = node.position.x,
        y = node.position.y,
        colorNames = colorNames(),
        vehicleTypeNames = vehicleTypeNames()
    )
}
