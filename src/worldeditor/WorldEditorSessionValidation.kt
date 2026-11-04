package worldeditor

import domain.Level
import domain.SourceNode
import domain.TargetNode
import network.WorldEditorNodeDTO
import network.WorldEditorNodeKind

/** Save-time validation for [WorldEditorSession]. */
internal object WorldEditorSessionValidation {
    /** Returns a user-facing validation error, or null when the current state can be saved. */
    fun validationError(
        worldName: String,
        level: Level,
        records: Collection<EditorNodeRecord>,
        editorNodes: List<WorldEditorNodeDTO>
    ): String? {
        if (worldName.trim().isEmpty()) return "Der Weltname darf nicht leer sein."
        if (records.none { it.kind == WorldEditorNodeKind.SOURCE }) return "Die Welt braucht mindestens eine Quelle."
        if (records.none { it.kind == WorldEditorNodeKind.TARGET }) return "Die Welt braucht mindestens eine Senke."
        if (records.any { !WorldEditorPlacement.isInsideLevel(it.node.position.x, it.node.position.y) }) {
            return "Quelle/Senke liegt ausserhalb des Spielfelds."
        }
        if (WorldEditorPlacement.spacingViolationExists(records)) {
            return "Quellen/Senken brauchen mindestens 12m Abstand."
        }
        if (editorNodes.any { it.colorNames.isEmpty() }) return "Jede Quelle/Senke braucht mindestens eine Farbe."

        val knownColors = allowedColorNames(level)
        editorNodes.flatMap { it.colorNames }.firstOrNull { it !in knownColors }?.let { unknownColor ->
            return "Unbekannte Farbe: $unknownColor"
        }
        if (editorNodes.any { it.vehicleTypeNames.isEmpty() }) {
            return "Jede Quelle/Senke braucht mindestens einen Fahrzeugtyp."
        }

        val knownVehicleTypes = allowedVehicleTypeNames(level)
        editorNodes.flatMap { it.vehicleTypeNames }.firstOrNull { it !in knownVehicleTypes }?.let { unknownType ->
            return "Unbekannter Fahrzeugtyp: $unknownType"
        }
        return null
    }

    /** Returns all color names accepted by save-time validation. */
    private fun allowedColorNames(level: Level): Set<String> {
        val levelColorNames = level.nodes.flatMap { node ->
            when (node) {
                is SourceNode -> node.configurations.map { it.color.name }
                is TargetNode -> listOf(node.color.name)
                else -> emptyList()
            }
        }
        return WorldEditorDefaults.paletteColors.map { it.name }.toSet() + levelColorNames
    }

    /** Returns all vehicle-type names accepted by save-time validation. */
    private fun allowedVehicleTypeNames(level: Level): Set<String> =
        WorldEditorDefaults.vehicleTypes.map { it.name }.toSet() + level.vehicleTypes.map { it.name }
}
