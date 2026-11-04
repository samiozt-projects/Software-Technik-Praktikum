package worldeditor

import domain.GameSimulationConfig
import domain.Level
import domain.Node
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.TargetNode
import korlibs.math.geom.Point
import network.WorldEditorNodeDTO
import network.WorldEditorNodeKind
import network.WorldEditorStateDTO
import persistence.LevelSerializer

/** Creates [WorldEditorSession] instances from templates, saved levels or server editor states. */
internal object WorldEditorSessionFactory {
    /** Creates a new-world editor session from a predefined template level. */
    fun fromTemplate(
        templateLevelId: Int,
        templateLevel: Level,
        worldName: String = WorldEditorDefaults.DEFAULT_NEW_WORLD_NAME
    ): WorldEditorSession {
        return fromLevel(
            worldId = null,
            worldName = worldName,
            templateLevelId = templateLevelId,
            isNewWorld = true,
            sourceLevel = templateLevel
        )
    }

    /** Creates an edit session from a server-provided world editor state. */
    fun fromServerState(state: WorldEditorStateDTO): WorldEditorSession {
        val level = LevelSerializer.fromJsonString(state.levelJson, "world-editor:${state.worldId}")
        val records = recordsFromServerState(level, state.editorNodes)
        return WorldEditorSession(
            worldId = state.worldId,
            worldName = state.name,
            templateLevelId = state.templateLevelId,
            isNewWorld = false,
            level = level,
            editorRecords = records.ifEmpty { recordsFromLevel(level) }
        )
    }

    /** Creates an edit session from an existing level without a server editor-state payload. */
    fun fromLevel(
        worldId: String?,
        worldName: String,
        templateLevelId: Int,
        isNewWorld: Boolean,
        sourceLevel: Level
    ): WorldEditorSession {
        val level = LevelSerializer.fromJsonString(LevelSerializer.toJsonString(sourceLevel), "world-editor-template")
        return WorldEditorSession(
            worldId = worldId,
            worldName = worldName,
            templateLevelId = templateLevelId,
            isNewWorld = isNewWorld,
            level = level,
            editorRecords = recordsFromLevel(level)
        )
    }

    private fun recordsFromLevel(level: Level): MutableMap<String, EditorNodeRecord> {
        val records = linkedMapOf<String, EditorNodeRecord>()
        level.nodes.filterIsInstance<SourceNode>().forEachIndexed { index, source ->
            records["source-${index + 1}"] = EditorNodeRecord(
                id = "source-${index + 1}",
                kind = WorldEditorNodeKind.SOURCE,
                node = source,
                initialVehicleTypeNames = source.configurations.map { it.vehicleType.name }.distinct()
                    .ifEmpty { listOf(WorldEditorDefaults.vehicleTypes.first().name) }
            )
        }
        level.nodes.filterIsInstance<TargetNode>().forEachIndexed { index, target ->
            records["target-${index + 1}"] = EditorNodeRecord(
                id = "target-${index + 1}",
                kind = WorldEditorNodeKind.TARGET,
                node = target,
                initialVehicleTypeNames = listOf(WorldEditorDefaults.vehicleTypes.first().name)
            )
        }
        return records
    }

    private fun recordsFromServerState(
        level: Level,
        editorNodes: List<WorldEditorNodeDTO>
    ): MutableMap<String, EditorNodeRecord> {
        val unmatchedSources = level.nodes.filterIsInstance<SourceNode>().toMutableList()
        val unmatchedTargets = level.nodes.filterIsInstance<TargetNode>().toMutableList()
        val records = linkedMapOf<String, EditorNodeRecord>()

        editorNodes.forEach { dto ->
            val node = matchedNodeForDto(dto, unmatchedSources, unmatchedTargets) ?: return@forEach
            val resolvedColors = applyDtoSettings(node, dto)
            records[dto.id] = EditorNodeRecord(
                id = dto.id,
                kind = dto.kind,
                node = node,
                initialVehicleTypeNames = dto.vehicleTypeNames,
                initialSinkColors = if (node is TargetNode) resolvedColors else emptyList()
            )
        }

        return records
    }

    /** Finds and removes the closest still-unmatched domain node for one editor DTO. */
    private fun matchedNodeForDto(
        dto: WorldEditorNodeDTO,
        unmatchedSources: MutableList<SourceNode>,
        unmatchedTargets: MutableList<TargetNode>
    ): Node? {
        return when (dto.kind) {
            WorldEditorNodeKind.SOURCE -> nearestNode(dto, unmatchedSources)?.also { unmatchedSources.remove(it) }
            WorldEditorNodeKind.TARGET -> nearestNode(dto, unmatchedTargets)?.also { unmatchedTargets.remove(it) }
        }
    }

    /**
     * Applies the DTO colors and vehicle type to the matched domain node.
     *
     * The level JSON is the authoritative store for source configurations, so per-configuration
     * tuning (spawn rate, initial speed) done through the source-configuration menu survives a
     * save-and-restore roundtrip. A source is only rebuilt from the DTO color list when the
     * level carries no configurations for it. Sink colors, which the single-color domain
     * [TargetNode] also holds, are resolved from the DTO.
     *
     * @return The resolved color list of the DTO, trimmed by [EditorNodeRecord] for target records.
     */
    private fun applyDtoSettings(node: Node, dto: WorldEditorNodeDTO): List<domain.Color> {
        val colors = dto.colorNames.mapNotNull { resolveColor(node, it) }
        when (node) {
            is SourceNode -> applySourceDtoFallback(node, dto, colors)
            is TargetNode -> colors.firstOrNull()?.let { node.color = it }
        }
        return colors
    }

    /**
     * Rebuilds a source's configurations from the DTO lists. This is only a fallback for
     * degenerate states: the level JSON is the authoritative store for configurations, so a
     * source that already carries configurations is left untouched.
     */
    private fun applySourceDtoFallback(node: SourceNode, dto: WorldEditorNodeDTO, colors: List<domain.Color>) {
        if (node.configurations.isNotEmpty() || colors.isEmpty()) return
        val vehicleTypes = dto.vehicleTypeNames
            .mapNotNull { WorldEditorDefaults.vehicleTypeByName(it) }
            .ifEmpty { listOf(WorldEditorDefaults.vehicleTypes.first()) }
        node.configurations = vehicleTypes.flatMap { vehicleType ->
            colors.map { color -> fallbackConfiguration(color, vehicleType) }
        }
    }

    /** Builds one fallback configuration with editor default spawn settings. */
    private fun fallbackConfiguration(color: domain.Color, vehicleType: domain.VehicleType) =
        SourceNodeConfiguration(
            color = color,
            generationSpeed = WorldEditorDefaults.DEFAULT_GENERATION_SPEED,
            initialVehicleSpeed = vehicleType.maxSpeed,
            vehicleType = vehicleType,
            vehiclesToSpawn = GameSimulationConfig.getConfiguredVehiclesToSpawn()
        )

    /** Resolves a DTO color name against the editor palette, falling back to the node's own colors. */
    private fun resolveColor(node: Node, colorName: String): domain.Color? {
        WorldEditorDefaults.colorByName(colorName)?.let { return it }
        return when (node) {
            is SourceNode -> node.configurations.map { it.color }
            is TargetNode -> listOf(node.color)
            else -> emptyList()
        }.firstOrNull { it.name.equals(colorName, ignoreCase = true) }
    }

    private fun <T : Node> nearestNode(dto: WorldEditorNodeDTO, nodes: List<T>): T? {
        return nodes.minByOrNull { node ->
            node.position.distanceTo(Point(dto.x, dto.y))
        }
    }
}
