package worldeditor

import domain.Color
import domain.GameSimulationConfig
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.TargetNode
import domain.VehicleType

/**
 * Color and vehicle-type mutation rules for editor-managed sources and sinks.
 *
 * Sources store their colors and vehicle types as one [SourceNodeConfiguration] per
 * color-and-type combination, so the configurations always cover every combination. Sinks store
 * their single selected color and vehicle-type list in [EditorNodeRecord] because the domain
 * [TargetNode] holds only one color and no vehicle types.
 */
internal object WorldEditorNodeSettings {

    /** Resolves a color name against the level's colors, then the editor palette. */
    fun knownColorByName(level: Level, name: String): Color? {
        return level.nodes.asSequence()
            .flatMap { node -> nodeColors(node) }
            .firstOrNull { WorldEditorDefaults.colorNamesMatch(it.name, name) }
            ?: WorldEditorDefaults.colorByName(name)
    }

    /** Resolves a vehicle-type name against the level's types, then the editor defaults. */
    fun knownVehicleTypeByName(level: Level, name: String): VehicleType? =
        level.vehicleTypes.firstOrNull { it.name.equals(name, ignoreCase = true) }?.copyVehicleType()
            ?: WorldEditorDefaults.vehicleTypeByName(name)

    /** Default vehicle type used for newly placed sources and sinks. */
    fun defaultVehicleType(level: Level): VehicleType =
        knownVehicleTypeByName(level, "Auto") ?: WorldEditorDefaults.vehicleTypes.first().copyVehicleType()

    /** Builds the initial configuration of a newly placed source. */
    fun defaultConfiguration(color: Color, vehicleType: VehicleType) = SourceNodeConfiguration(
        color = color,
        generationSpeed = WorldEditorDefaults.DEFAULT_GENERATION_SPEED,
        initialVehicleSpeed = vehicleType.maxSpeed,
        vehicleType = vehicleType,
        vehiclesToSpawn = GameSimulationConfig.getConfiguredVehiclesToSpawn()
    )

    /**
     * Adds [color] to [record]: sources gain one configuration per assigned vehicle type, sinks
     * replace their single selected color.
     */
    fun addColorTo(level: Level, record: EditorNodeRecord, color: Color) {
        when (val node = record.node) {
            is SourceNode -> {
                node.configurations = node.configurations + assignedVehicleTypes(level, record).map {
                    buildSourceConfiguration(node, color, it)
                }
            }
            is TargetNode -> {
                record.sinkColors.clear()
                record.sinkColors.add(color)
                node.color = color
            }
        }
    }

    /** Removes [color] from [record]: sources drop matching configurations, sinks shrink their list. */
    fun removeColorFrom(record: EditorNodeRecord, color: Color) {
        when (val node = record.node) {
            is SourceNode -> {
                node.configurations = node.configurations.filterNot {
                    WorldEditorDefaults.colorNamesMatch(it.color.name, color.name)
                }
            }
            is TargetNode -> {
                record.sinkColors.removeAll {
                    WorldEditorDefaults.colorNamesMatch(it.name, color.name)
                }
                record.sinkColors.firstOrNull()?.let { node.color = it }
            }
        }
    }

    /**
     * Adds [vehicleType] to [record]: sources gain one configuration per assigned color, sinks
     * extend their stored type list.
     */
    fun addVehicleTypeTo(record: EditorNodeRecord, vehicleType: VehicleType) {
        val node = record.node
        if (node is SourceNode) {
            val colors = node.configurations.map { it.color }.distinctBy { it.name }
                .ifEmpty { listOf(WorldEditorDefaults.paletteColors.first()) }
            node.configurations = node.configurations + colors.map {
                buildSourceConfiguration(node, it, vehicleType)
            }
        }
        if (record.storedVehicleTypeNames.none { it.equals(vehicleType.name, ignoreCase = true) }) {
            record.storedVehicleTypeNames.add(vehicleType.name)
        }
    }

    /** Removes [vehicleType] from [record]: sources drop matching configurations. */
    fun removeVehicleTypeFrom(record: EditorNodeRecord, vehicleType: VehicleType) {
        val node = record.node
        if (node is SourceNode) {
            node.configurations = node.configurations.filterNot {
                it.vehicleType.name.equals(vehicleType.name, ignoreCase = true)
            }
        }
        record.storedVehicleTypeNames.removeAll { it.equals(vehicleType.name, ignoreCase = true) }
    }

    /**
     * Removes configurations that repeat an earlier configuration's color and vehicle type
     * (names matched case-insensitively), keeping the first occurrence.
     *
     * The source-configuration menu can recolor a configuration onto an already existing
     * color-and-type combination; the editor applies this helper to keep one configuration
     * per combination.
     */
    fun dedupedConfigurations(
        configurations: List<SourceNodeConfiguration>
    ): List<SourceNodeConfiguration> = configurations.distinctBy {
        it.color.name.lowercase() to it.vehicleType.name.lowercase()
    }

    /** All colors carried by [node], in configuration order. */
    private fun nodeColors(node: domain.Node) = when (node) {
        is SourceNode -> node.configurations.asSequence().map { it.color }
        is TargetNode -> sequenceOf(node.color)
        else -> emptySequence()
    }

    /** All vehicle types currently assigned to [record], resolved to usable instances. */
    private fun assignedVehicleTypes(level: Level, record: EditorNodeRecord): List<VehicleType> =
        record.vehicleTypeNames()
            .mapNotNull { knownVehicleTypeByName(level, it) }
            .ifEmpty { listOf(defaultVehicleType(level)) }

    /**
     * Builds one source configuration for [color] and [vehicleType]. Spawn settings carry over
     * from an existing configuration of the same type; without one, the generation timing comes
     * from the first configuration and the initial speed defaults to the type's maximum, always
     * capped at that maximum.
     */
    private fun buildSourceConfiguration(
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

    /** Copies a level vehicle type so editor changes never mutate the original instance. */
    private fun VehicleType.copyVehicleType() =
        VehicleType(name, maxSpeed, length, adhesionCoefficient, acceleration, graphicRef)
}
