package worldeditor

import domain.Level
import domain.SourceNode
import domain.TargetNode
import network.WorldEditorNodeDTO
import network.WorldEditorNodeKind

/**
 * Save-time validation rules for the world editor.
 *
 * All messages are German user-facing texts shown in the editor's error line.
 */
internal object WorldEditorValidation {

    /**
     * Returns the first validation error for the given editor state, or null when it is saveable.
     *
     * @param worldName Name the world should be saved under.
     * @param level Editor working copy holding the allowed colors and vehicle types.
     * @param records Editor-managed source and sink records.
     * @param editorNodes Server-facing editor node DTOs derived from [records].
     */
    fun validationError(
        worldName: String,
        level: Level,
        records: Collection<EditorNodeRecord>,
        editorNodes: List<WorldEditorNodeDTO>
    ): String? = structuralError(worldName, records) ?: settingsError(level, editorNodes)

    /** Checks name, required endpoints, and placement rules. */
    private fun structuralError(worldName: String, records: Collection<EditorNodeRecord>): String? {
        if (worldName.trim().isEmpty()) return "Der Weltname darf nicht leer sein."
        if (records.none { it.kind == WorldEditorNodeKind.SOURCE }) {
            return "Die Welt braucht mindestens eine Quelle."
        }
        if (records.none { it.kind == WorldEditorNodeKind.TARGET }) {
            return "Die Welt braucht mindestens eine Senke."
        }
        if (records.any { !WorldEditorPlacement.isInsideLevel(it.node.position.x, it.node.position.y) }) {
            return "Quelle/Senke liegt ausserhalb des Spielfelds."
        }
        if (WorldEditorPlacement.spacingViolationExists(records)) {
            return "Quellen/Senken brauchen mindestens 12m Abstand."
        }
        return null
    }

    /** Checks that every node carries known colors and vehicle types. */
    private fun settingsError(level: Level, editorNodes: List<WorldEditorNodeDTO>): String? {
        if (editorNodes.any { it.colorNames.isEmpty() }) {
            return "Jede Quelle/Senke braucht mindestens eine Farbe."
        }
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

    /**
     * Colors accepted at save time: the editor palette plus every color already used by the
     * level, so worlds based on templates with non-palette colors stay saveable.
     */
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

    /**
     * Vehicle types accepted at save time: the editor defaults plus every type already used by
     * the level, so worlds based on templates with custom types stay saveable.
     */
    private fun allowedVehicleTypeNames(level: Level): Set<String> =
        WorldEditorDefaults.vehicleTypes.map { it.name }.toSet() +
            level.vehicleTypes.map { it.name }
}
