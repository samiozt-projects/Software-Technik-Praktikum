package worldeditor

import domain.Color
import domain.GameSimulationConfig
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.VehicleType

/** Builds and normalizes source-node configurations for world-editor sessions. */
internal object WorldEditorSourceConfigurations {
    /** Removes repeated color-and-type pairs, keeping the first configuration. */
    fun deduped(configurations: List<SourceNodeConfiguration>): List<SourceNodeConfiguration> =
        configurations.distinctBy { it.color.name.lowercase() to it.vehicleType.name.lowercase() }

    /** Builds the default source configuration used for newly added sources. */
    fun defaultConfiguration(color: Color, vehicleType: VehicleType) =
        SourceNodeConfiguration(
            color = color,
            generationSpeed = WorldEditorDefaults.DEFAULT_GENERATION_SPEED,
            initialVehicleSpeed = vehicleType.maxSpeed,
            vehicleType = vehicleType,
            vehiclesToSpawn = GameSimulationConfig.getConfiguredVehiclesToSpawn()
        )

    /** All vehicle types currently assigned to [record], resolved to usable instances. */
    fun assignedVehicleTypes(record: EditorNodeRecord, level: Level): List<VehicleType> =
        record.vehicleTypeNames()
            .mapNotNull { WorldEditorSessionPalette.knownVehicleTypeByName(level, it) }
            .ifEmpty { listOf(WorldEditorSessionPalette.defaultVehicleType(level)) }

    /**
     * Builds one source configuration for [color] and [vehicleType], carrying over existing spawn
     * settings where possible.
     */
    fun buildSourceConfiguration(
        node: SourceNode,
        color: Color,
        vehicleType: VehicleType
    ): SourceNodeConfiguration {
        val sameTypeTemplate = node.configurations.firstOrNull {
            it.vehicleType.name.equals(vehicleType.name, ignoreCase = true)
        }
        val template = sameTypeTemplate ?: node.configurations.firstOrNull()
        return SourceNodeConfiguration(
            color = color,
            generationSpeed = template?.generationSpeed ?: WorldEditorDefaults.DEFAULT_GENERATION_SPEED,
            initialVehicleSpeed = minOf(
                sameTypeTemplate?.initialVehicleSpeed ?: vehicleType.maxSpeed,
                vehicleType.maxSpeed
            ),
            vehicleType = vehicleType,
            vehiclesToSpawn = template?.vehiclesToSpawn ?: GameSimulationConfig.getConfiguredVehiclesToSpawn()
        )
    }
}
