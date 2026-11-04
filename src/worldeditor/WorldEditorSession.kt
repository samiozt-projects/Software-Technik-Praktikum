package worldeditor

import domain.Color
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.TargetNode
import network.SaveWorldEditorDTO
import network.WorldEditorNodeDTO
import network.WorldEditorNodeKind
import network.WorldEditorStateDTO
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import persistence.LevelSerializer
import kotlin.math.roundToInt

/**
 * Mutable editor state for one world-editing session.
 *
 * The session always works on a cloned [level] so canceling the editor can discard all changes
 * without mutating the previously saved world state. Color and vehicle-type rules live in
 * [WorldEditorNodeSettings]; save-time validation lives in [WorldEditorValidation].
 *
 * @property worldId Server ID of the edited world, or null while creating a new world.
 * @property worldName User-facing world name edited by the session.
 * @property templateLevelId Standard template ID this world is based on.
 * @property isNewWorld Whether saving should create a new server world.
 * @property level Mutable cloned level used as the editor working copy.
 * @property editorRecords Editor-managed sources and sinks keyed by stable editor IDs.
 */
internal class WorldEditorSession internal constructor(
    val worldId: String?,
    var worldName: String,
    val templateLevelId: Int,
    val isNewWorld: Boolean,
    val level: Level,
    private val editorRecords: EditorRecordMap
) {
    /** Currently selected editor node, if any. */
    var selectedNodeId: String? = editorRecords.keys.firstOrNull()
        private set

    /** Last validation or server-facing error message shown by the scene. */
    var errorMessage: String? = null

    /** Normalizes the cloned working copy so editor-managed nodes obey current placement rules. */
    init {
        dedupeSourceConfigurations()
        normalizeExistingEditorNodes()
    }

    /** Current editor nodes as DTOs suitable for server persistence. */
    val editorNodes: List<WorldEditorNodeDTO>
        get() = editorRecords.values.map { it.toDto() }

    /** Selected node DTO, if a node is selected. */
    val selectedNode: WorldEditorNodeDTO?
        get() = selectedNodeId?.let { editorRecords[it]?.toDto() }

    /** Selects an editor node by ID. */
    fun selectNode(id: String?) {
        selectedNodeId = id?.takeIf { it in editorRecords }
    }

    /** Editor IDs of the records whose domain nodes are contained in [nodes]. */
    fun editorIdsOf(nodes: Collection<domain.Node>): Set<String> =
        editorRecords.filterValues { record -> nodes.any { it === record.node } }.keys

    /**
     * Orientation of the selected node in whole display degrees (0..359), or null without
     * selection. Display degrees always mean the visible arrow direction (0 = up), so sink
     * values are converted from the domain orientation (see [setSelectedOrientation]).
     */
    fun selectedOrientationDegrees(): Int? {
        val degrees = when (val node = selectedRecord()?.node) {
            is SourceNode -> node.orientation.degrees.roundToInt()
            is TargetNode -> node.orientation.degrees.roundToInt() + SINK_ARROW_OFFSET_DEGREES
            else -> return null
        }
        return normalizedDegrees(degrees)
    }

    /**
     * Sets the selected node's orientation from display degrees.
     *
     * @param degrees whole degrees clockwise for the visible arrow: 0 = oben, 90 = rechts,
     *   180 = unten, 270 = links. The sink arrow sprite is drawn rotated by 180 degrees
     *   compared to the source arrow (it points in driving direction), so sink values are
     *   shifted by [SINK_ARROW_OFFSET_DEGREES] before they reach the domain node — the entered
     *   number always matches the direction the player sees.
     * @return false with a user-facing [errorMessage] when nothing is selected or [degrees] is
     *   not a whole number between 0 and 359.
     */
    fun setSelectedOrientation(degrees: Int?): Boolean {
        val record = selectedRecord() ?: return failWith("Keine Quelle/Senke ausgewaehlt.")
        if (degrees == null || degrees !in 0 until DEGREES_PER_TURN) {
            return failWith("Ausrichtung muss eine Gradzahl von 0 bis 359 sein.")
        }
        when (val node = record.node) {
            is SourceNode -> node.orientation = Angle.fromDegrees(degrees.toDouble())
            is TargetNode -> node.orientation = Angle.fromDegrees(
                normalizedDegrees(degrees + SINK_ARROW_OFFSET_DEGREES).toDouble()
            )
            else -> return false
        }
        errorMessage = null
        return true
    }

    /** Maps [degrees] into the 0 until 360 range. */
    private fun normalizedDegrees(degrees: Int): Int =
        ((degrees % DEGREES_PER_TURN) + DEGREES_PER_TURN) % DEGREES_PER_TURN

    /**
     * Resolves a color name against the level's colors and the editor palette.
     *
     * Used by the scene to render placed nodes in their assigned colors.
     */
    fun colorByName(name: String): Color? = WorldEditorNodeSettings.knownColorByName(level, name)

    /** Adds a source or sink at [x], [y] and selects it. */
    fun addNode(kind: WorldEditorNodeKind, x: Double, y: Double): WorldEditorNodeDTO? {
        val position = resolvedPlacementPosition(x, y, excludedId = null) ?: run {
            errorMessage = "Kein freier Platz mit ausreichend Abstand gefunden."
            return null
        }

        val id = nextNodeId(kind)
        val defaultVehicle = WorldEditorNodeSettings.defaultVehicleType(level)
        val node = newDomainNode(kind, position, defaultVehicle)

        level.addNode(node)
        editorRecords[id] = EditorNodeRecord(id, kind, node, listOf(defaultVehicle.name))
        selectedNodeId = id
        errorMessage = null
        return editorRecords.getValue(id).toDto()
    }

    /** Moves an existing source or sink, including connected street endpoints. */
    fun moveNode(id: String, x: Double, y: Double): Boolean {
        val record = editorRecords[id] ?: return false
        val position = resolvedPlacementPosition(x, y, excludedId = id) ?: run {
            errorMessage = "Kein freier Platz mit ausreichend Abstand gefunden."
            return false
        }

        record.node.position = position
        syncConnectedStreetEndpoints(record.node)
        errorMessage = null
        return true
    }

    /** Removes an existing source or sink and all connected streets. */
    fun removeNode(id: String): Boolean {
        val record = editorRecords.remove(id) ?: return false
        val removed = level.removeNode(record.node)
        if (selectedNodeId == id) selectedNodeId = editorRecords.keys.firstOrNull()
        errorMessage = null
        return removed
    }

    /**
     * Toggles a color on the selected node: an unassigned color is added, an assigned color is
     * removed again. The last remaining color cannot be removed, so no neutral (colorless) node
     * can exist.
     */
    fun toggleSelectedColor(colorName: String): Boolean {
        val record = selectedRecord() ?: return false
        val color = WorldEditorNodeSettings.knownColorByName(level, colorName)
            ?: return failWith("Unbekannte Farbe: $colorName")
        return toggleAssignedName(
            assignedNames = record.colorNames(),
            name = color.name,
            keepLastMessage = "Mindestens eine Farbe muss ausgewaehlt bleiben.",
            add = { WorldEditorNodeSettings.addColorTo(level, record, color) },
            remove = { WorldEditorNodeSettings.removeColorFrom(record, color) },
            namesMatch = WorldEditorDefaults::colorNamesMatch
        )
    }

    /**
     * Toggles a vehicle type on the selected node: an unassigned type is added, an assigned type
     * is removed again. The last remaining vehicle type cannot be removed.
     */
    fun toggleSelectedVehicleType(vehicleTypeName: String): Boolean {
        val record = selectedRecord() ?: return false
        val vehicleType = WorldEditorNodeSettings.knownVehicleTypeByName(level, vehicleTypeName)
            ?: return failWith("Unbekannter Fahrzeugtyp: $vehicleTypeName")
        return toggleAssignedName(
            assignedNames = record.vehicleTypeNames(),
            name = vehicleType.name,
            keepLastMessage = "Mindestens ein Fahrzeugtyp muss ausgewaehlt bleiben.",
            add = { WorldEditorNodeSettings.addVehicleTypeTo(record, vehicleType) },
            remove = { WorldEditorNodeSettings.removeVehicleTypeFrom(record, vehicleType) }
        )
    }

    /**
     * Builds the server request for saving the current editor state.
     *
     * @return request DTO, or null when validation failed. The failure is stored in [errorMessage].
     */
    fun saveRequestOrNull(): SaveWorldEditorDTO? {
        WorldEditorValidation.validationError(worldName, level, editorRecords.values, editorNodes)?.let {
            errorMessage = it
            return null
        }

        val levelJson = try {
            LevelSerializer.toJsonString(level)
        } catch (e: Exception) {
            errorMessage = "Welt konnte nicht serialisiert werden: ${e.message}"
            return null
        }

        errorMessage = null
        return SaveWorldEditorDTO(
            name = worldName.trim(),
            templateLevelId = templateLevelId,
            levelJson = levelJson,
            editorNodes = editorNodes
        )
    }

    /**
     * Removes duplicate color-and-type configurations from every source in the working copy, so
     * the configuration menu never lists the same combination twice for one source.
     */
    private fun dedupeSourceConfigurations() {
        level.nodes.filterIsInstance<SourceNode>().forEach { source ->
            source.configurations = WorldEditorNodeSettings.dedupedConfigurations(source.configurations)
        }
    }

    /** Builds the domain node for a newly placed editor node. */
    private fun newDomainNode(
        kind: WorldEditorNodeKind,
        position: Point,
        defaultVehicle: domain.VehicleType
    ): domain.Node {
        val defaultColor = WorldEditorDefaults.paletteColors.first()
        return when (kind) {
            WorldEditorNodeKind.SOURCE -> SourceNode(
                position = position,
                elevation = 0,
                orientation = WorldEditorPlacement.defaultOrientation(),
                configurations = listOf(WorldEditorNodeSettings.defaultConfiguration(defaultColor, defaultVehicle))
            )
            WorldEditorNodeKind.TARGET -> TargetNode(
                position = position,
                elevation = 0,
                orientation = WorldEditorPlacement.defaultOrientation(),
                color = defaultColor
            )
        }
    }

    /** Currently selected editor record, if any. */
    private fun selectedRecord(): EditorNodeRecord? = selectedNodeId?.let { editorRecords[it] }

    /** Stores [message] as the current error and reports failure. */
    private fun failWith(message: String): Boolean {
        errorMessage = message
        return false
    }

    /**
     * Applies one toggle step: removes [name] when it is assigned (unless it is the last entry),
     * adds it otherwise.
     */
    private fun toggleAssignedName(
        assignedNames: List<String>,
        name: String,
        keepLastMessage: String,
        add: () -> Unit,
        remove: () -> Unit,
        namesMatch: (String, String) -> Boolean = { left, right -> left.equals(right, ignoreCase = true) }
    ): Boolean {
        val alreadyAssigned = assignedNames.any { namesMatch(it, name) }
        if (alreadyAssigned && assignedNames.size <= 1) {
            return failWith(keepLastMessage)
        }
        if (alreadyAssigned) remove() else add()
        errorMessage = null
        return true
    }

    private fun resolvedPlacementPosition(x: Double, y: Double, excludedId: String?): Point? {
        return WorldEditorPlacement.resolvedPlacementPosition(x, y, excludedId, editorRecords.values)
    }

    private fun normalizeExistingEditorNodes() {
        editorRecords.values.forEach { record ->
            val position = resolvedPlacementPosition(record.node.position.x, record.node.position.y, record.id)
                ?: record.node.position
            if (record.node.position != position) {
                record.node.position = position
                syncConnectedStreetEndpoints(record.node)
            }
        }
        errorMessage = null
    }

    private fun nextNodeId(kind: WorldEditorNodeKind): String {
        val prefix = if (kind == WorldEditorNodeKind.SOURCE) "source" else "target"
        var index = editorRecords.size + 1
        var candidate = "$prefix-$index"
        while (candidate in editorRecords) {
            index++
            candidate = "$prefix-$index"
        }
        return candidate
    }

    private fun syncConnectedStreetEndpoints(node: domain.Node) {
        node.incomingStreets.forEach { street ->
            street.path = street.path.dropLast(1) + node.position
        }
        node.outgoingStreets.forEach { street ->
            street.path = listOf(node.position) + street.path.drop(1)
        }
    }

    companion object {
        /** Minimum allowed center-to-center distance between editor-managed sources and sinks. */
        const val MIN_NODE_DISTANCE_METER = WorldEditorPlacement.MIN_NODE_DISTANCE_METER

        /** Number of whole degrees in one full turn; orientations are kept in 0 until 360. */
        private const val DEGREES_PER_TURN = 360

        /**
         * Rotation offset between the sink arrow sprite and the source arrow sprite: the sink
         * arrival arrow points in driving direction, so its sprite is authored 180 degrees
         * rotated. Editor display degrees are shifted by this offset for sinks in both
         * directions, keeping "0 = oben" true for the visible arrow of sources AND sinks.
         */
        private const val SINK_ARROW_OFFSET_DEGREES = 180

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
        ): List<SourceNodeConfiguration> = WorldEditorNodeSettings.dedupedConfigurations(configurations)

        /** Creates a new-world editor session from a predefined template level. */
        fun fromTemplate(
            templateLevelId: Int,
            templateLevel: Level,
            worldName: String = WorldEditorDefaults.DEFAULT_NEW_WORLD_NAME
        ): WorldEditorSession {
            return WorldEditorSessionFactory.fromTemplate(templateLevelId, templateLevel, worldName)
        }

        /** Creates an edit session from a server-provided world editor state. */
        fun fromServerState(state: WorldEditorStateDTO): WorldEditorSession {
            return WorldEditorSessionFactory.fromServerState(state)
        }

        /** Creates an edit session from an existing level without a server editor-state payload. */
        fun fromLevel(
            worldId: String?,
            worldName: String,
            templateLevelId: Int,
            isNewWorld: Boolean,
            sourceLevel: Level
        ): WorldEditorSession {
            return WorldEditorSessionFactory.fromLevel(worldId, worldName, templateLevelId, isNewWorld, sourceLevel)
        }
    }
}
