package worldeditor

import domain.Color
import domain.Level
import domain.SourceNode
import domain.TargetNode
import domain.VehicleType

/** Resolves editor colors and vehicle types against a level plus the default editor palette. */
internal object WorldEditorSessionPalette {
    /** Resolves [name] to a color already used by [level], or to a default editor-palette color. */
    fun knownColorByName(level: Level, name: String): Color? {
        return level.nodes.asSequence()
            .flatMap { node ->
                when (node) {
                    is SourceNode -> node.configurations.asSequence().map { it.color }
                    is TargetNode -> sequenceOf(node.color)
                    else -> emptySequence()
                }
            }
            .firstOrNull { WorldEditorDefaults.colorNamesMatch(it.name, name) }
            ?: WorldEditorDefaults.colorByName(name)
    }

    /** Resolves [name] to a vehicle type from [level], or to a default editor vehicle type. */
    fun knownVehicleTypeByName(level: Level, name: String): VehicleType? =
        level.vehicleTypes.firstOrNull { it.name.equals(name, ignoreCase = true) }?.copyVehicleType()
            ?: WorldEditorDefaults.vehicleTypeByName(name)

    /** Returns the standard vehicle type used for newly created editor nodes. */
    fun defaultVehicleType(level: Level): VehicleType =
        knownVehicleTypeByName(level, "Auto") ?: WorldEditorDefaults.vehicleTypeByName("Auto")
            ?: WorldEditorDefaults.vehicleTypes.first().copyVehicleType()

    /** Returns a detached copy so editor changes cannot mutate shared template types. */
    private fun VehicleType.copyVehicleType() =
        VehicleType(name, maxSpeed, length, adhesionCoefficient, acceleration, graphicRef)
}
