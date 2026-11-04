package persistence

import domain.*
import korlibs.image.color.RGBA
import korlibs.io.file.VfsFile
import korlibs.io.file.std.resourcesVfs
import korlibs.io.file.std.applicationVfs
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
* Saves and loads Level objects as JSON files via the LevelDto hierarchy.
*
* Save pipeline (Domain → JSON):
* 1. All unique VehicleTypes and Colors referenced anywhere in the level are
*    collected and written once to the root-level lookup tables.
* 2. Each node is assigned a stable string ID (e.g. "node-1"). These IDs exist
*    only in the file; the domain model uses object references instead.
* 3. Streets are stored with startNodeId/endNodeId string references instead
*    of embedded node objects — this breaks the bidirectional cycle that would
*    otherwise make direct serialization impossible.
* 4. The resulting LevelDto is encoded to a pretty-printed JSON string and
*    written to disk.
*
* Load pipeline (JSON → Domain):
* 1. The JSON string is parsed into a LevelDto by kotlinx.serialization (structural
*    validation happens here — malformed JSON fails at this step).
* 2. Lookup maps (name → Color, name → VehicleType) are built from the root
*    tables so that node configurations can resolve name references in O(1).
* 3. All nodes are instantiated and indexed in a nodeId → Node map.
* 4. Streets are built by looking up start/end nodes in that map. Missing IDs or
*    paths that are too short are caught here and reported as LevelSerializationException.
*
* All suspend functions are async because KorGE VFS file operations are coroutine-based.
*/
object LevelSerializer {

    /**
    * ignoreUnknownKeys keeps older code able to load files written by newer versions
    */
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
    * -------------------------------------------------------------------------
    * Public API
    * -------------------------------------------------------------------------
    */

    /** Serializes [level] to a JSON file at the given [path].
    * Throws LevelSerializationException if the file cannot be written or
    * the level cannot be converted to JSON.
    */
    suspend fun saveToFile(level: Level, path: String) {
        try {
            val file = applicationVfs[path]
            file.ensureParents()
            file.writeString(toJsonString(level))
        } catch (e: LevelSerializationException) {
            throw e
        } catch (e: Exception) {
            throw LevelSerializationException("Could not write file '$path': ${e.message}", e)
        }
    }

    /**
     * Deserializes a Level from a JSON file bundled inside the app's resources
     * (src/commonMain/resources/). Use this variant at runtime; use loadFromFile
     * only when you need to read from an arbitrary file-system path (e.g. saves).
     * [resourcePath] is relative to the resources root, e.g. "levels/level0.json".
     * Throws LevelSerializationException if the resource is missing, unreadable,
     * malformed, or contains invalid references.
     */
    suspend fun loadFromResources(resourcePath: String): Level {
        val file: VfsFile = resourcesVfs[resourcePath]
        return loadLevelFile(file, resourcePath)
    }

    /**
     * Deserializes a Level from a path relative to the application working directory
     * (i.e. the project root when running via Gradle). Use this for asset files that
     * live inside the project's res/ folder and are not bundled into the classpath.
     * [appPath] is relative to the application root, e.g. "level/level0.json".
     * Throws LevelSerializationException if the file is missing, unreadable,
     * malformed, or contains invalid references.
     */

    suspend fun loadFromAppVfs(appPath: String): Level {
        val file: VfsFile = applicationVfs[appPath]
        return loadLevelFile(file, appPath)
    }

    /**
     * Loads a level file from a given file path
     * 
     * @param file the file to load
     * @param sourcePath the path of the file
     * @return the loaded level
     */
    suspend fun loadLevelFile(file: VfsFile, sourcePath: String): Level {
        if (!file.exists()) {
            throw LevelSerializationException("File not found: '$sourcePath'")
        }

        val jsonString = try {
            file.readString()
        } catch (e: Exception) {
            throw LevelSerializationException(
                "Could not read file '$sourcePath': ${e.message}", e
            )
        }

        return fromJsonString(jsonString, sourcePath)
    }

    /** internal so tests can call these directly without touching the file system */
    internal fun toJsonString(level: Level): String {
        return try {
            json.encodeToString(toDto(level))
        } catch (e: SerializationException) {
            throw LevelSerializationException("Could not serialize level '${level.name}': ${e.message}", e)
        }
    }

    /** [sourcePath] is only used in error messages; pass empty string for in-memory use */
    internal fun fromJsonString(jsonString: String, sourcePath: String = ""): Level {
        val dto = try {
            json.decodeFromString<LevelDto>(jsonString)
        } catch (e: SerializationException) {
            throw LevelSerializationException("Malformed JSON: ${e.message}", e)
        }
        return fromDto(dto, sourcePath)
    }

    /**
     * Creates a structurally independent copy of [level] with a different display [name].
     */
    internal fun copyWithName(level: Level, name: String): Level =
        fromDto(toDto(level).copy(name = name), "renamed level copy")

    /**
     *  -------------------------------------------------------------------------
     * Domain → DTO  (serialization path)
     * -------------------------------------------------------------------------
    */

    private fun toDto(level: Level): LevelDto {
        // Collect all unique VehicleTypes and Colors referenced in the level
        val vehicleTypes = level.nodes
            .filterIsInstance<SourceNode>()
            .flatMap { it.configurations }
            .map { it.vehicleType }
            .distinctBy { it.name }

        val colors = (
            level.nodes.filterIsInstance<SourceNode>()
                .flatMap { it.configurations }.map { it.color } +
                level.nodes.filterIsInstance<TargetNode>().map { it.color }
            ).distinctBy { it.name }

        /** Assign stable IDs to all nodes, including street endpoints not explicitly present in level.nodes. */
        val serializableNodes = (
            level.nodes +
                level.streets.flatMap { listOf(it.startNode, it.endNode) }
            ).distinct()
        val nodeIds = serializableNodes.mapIndexed { i, node -> node to "node-${i + 1}" }.toMap()

        return LevelDto(
            name = level.name,
            number = level.number,
            adhesionCoefficientModifier = level.adhesionCoefficientModifier,
            weatherCondition = level.weatherCondition.jsonValue,
            challengeConnectionNodeLimit = level.challengeConnectionNodeLimit,
            vehicleTypes = vehicleTypes.map { it.toDto() },
            colors = colors.map { it.toDto() },
            nodes = serializableNodes.map { it.toDto(nodeIds) },
            streets = level.streets.map { it.toDto(nodeIds) }
        )
    }

    private fun VehicleType.toDto() = VehicleTypeDto(
        name = name,
        maxSpeed = maxSpeed,
        length = length,
        adhesionCoefficient = adhesionCoefficient,
        acceleration = acceleration,
        graphicRef = graphicRef
    )

    /**
     * Serializes the color into the DTO's packed ARGB integer (`0xAARRGGBB`). The raw
     * [RGBA.value] must NOT be written directly: korlibs packs RGBA with red in the lowest byte
     * (`0xAABBGGRR`), so a raw cast would swap the red and blue channels on the next load
     * (see [ColorDto.toDomain]).
     */
    private fun Color.toDto(): ColorDto {
        val c = value
        val packed = (c.a shl 24) or (c.r shl 16) or (c.g shl 8) or c.b
        return ColorDto(
            name = name,
            argb = packed.toUInt()
        )
    }

    private fun Node.toDto(nodeIds: Map<Node, String>): NodeDto {
        val id = nodeIds[this] ?: error("Node not found in ID map")
        return when (this) {
            is SourceNode -> SourceNodeDto(
                id = id,
                x = position.x,
                y = position.y,
                elevation = elevation,
                orientationDegrees = orientation.degrees,
                configurations = configurations.map { it.toDto() }
            )
            is TargetNode -> TargetNodeDto(
                id = id,
                x = position.x,
                y = position.y,
                elevation = elevation,
                orientationDegrees = orientation.degrees,
                colorName = color.name
            )
            is ConnectionNode -> ConnectionNodeDto(
                id = id,
                x = position.x,
                y = position.y,
                elevation = elevation
            )
            else -> error("Unknown node type: ${this::class.simpleName}")
        }
    }

    private fun SourceNodeConfiguration.toDto() = SourceNodeConfigurationDto(
            colorName = color.name,
            vehicleTypeName = vehicleType.name,
            generationSpeed = generationSpeed,
            initialVehicleSpeed = initialVehicleSpeed,
            vehiclesToSpawn = vehiclesToSpawn
        )

    private fun Street.toDto(nodeIds: Map<Node, String>) = StreetDto(
        startNodeId = nodeIds[startNode] ?: error("startNode not found in ID map"),
        endNodeId = nodeIds[endNode] ?: error("endNode not found in ID map"),
        path = path.map { PointDto(it.x, it.y) }
    )

    /**
    * -------------------------------------------------------------------------
    * DTO → Domain  (deserialization path)
    * -------------------------------------------------------------------------
    */

    private fun fromDto(dto: LevelDto, sourcePath: String): Level {
        // Build lookup maps first so node configurations can resolve name references in O(1)
        val vehicleTypesByName = dto.vehicleTypes.associate { it.name to it.toDomain() }
        val colorsByName = dto.colors.associate { it.name to it.toDomain() }

        // Instantiate all nodes and index them by their file ID so streets can look them up
        val nodesById = mutableMapOf<String, Node>()
        for (nodeDto in dto.nodes) {
            val node = nodeDto.toDomain(colorsByName, vehicleTypesByName, sourcePath)
            nodesById[nodeDto.id] = node
        }

        val level = Level(
            name = dto.name,
            number = dto.number,
            adhesionCoefficientModifier = dto.adhesionCoefficientModifier,
            weatherCondition = dto.weatherCondition.toWeatherCondition(sourcePath),
            challengeConnectionNodeLimit = dto.challengeConnectionNodeLimit
        )
        nodesById.values.forEach { level.addNode(it) }

        /** Resolve street node IDs back to domain objects; validate path and references here */
        for (streetDto in dto.streets) {
            val startNode = nodesById[streetDto.startNodeId]
                ?: throw LevelSerializationException(
                    "Street references unknown startNodeId '${streetDto.startNodeId}' in '$sourcePath'"
                )
            val endNode = nodesById[streetDto.endNodeId]
                ?: throw LevelSerializationException(
                    "Street references unknown endNodeId '${streetDto.endNodeId}' in '$sourcePath'"
                )

            if (streetDto.path.size < 2) {
                throw LevelSerializationException(
                    "Street from '${streetDto.startNodeId}' to '${streetDto.endNodeId}' " +
                        "must have at least 2 path points in '$sourcePath'"
                )
            }

            val street = Street(
                startNode = startNode,
                endNode = endNode,
                path = streetDto.path.map { Point(it.x, it.y) }
            )
            level.addStreet(street)
        }

        return level
    }

    /**
     * @return A new VehicleType instance with the same name as the one in the file.
     * A return is necessary because the return type changes from Any? to VehicleType.
     * So it is guaranteed that the return is a VehicleType Instance.
     * */
    private fun VehicleTypeDto.toDomain(): VehicleType {
        return VehicleType(
            name = name,
            maxSpeed = maxSpeed,
            length = length,
            adhesionCoefficient = adhesionCoefficient,
            acceleration = acceleration,
            graphicRef = graphicRef
        )
    }

    /**
     * Reads the packed ARGB integer (`0xAARRGGBB`) stored in the file and rebuilds the domain
     * color via the channel-wise [RGBA] constructor. Casting the stored value straight into
     * `RGBA(Int)` would misread it, because korlibs expects red in the lowest byte
     * (`0xAABBGGRR`) — that mismatch swapped red and blue (e.g. "Rot" loaded as blue).
     */
    private fun ColorDto.toDomain(): Color {
        val packed = argb.toInt()
        val a = (packed ushr 24) and 0xFF
        val r = (packed ushr 16) and 0xFF
        val g = (packed ushr 8) and 0xFF
        val b = packed and 0xFF
        return Color(
            name = name,
            value = RGBA(r, g, b, a)
        )
    }

    private fun NodeDto.toDomain(
        colorsByName: Map<String, Color>,
        vehicleTypesByName: Map<String, VehicleType>,
        sourcePath: String
    ): Node {
        val position = Point(x, y)
        return when (this) {
            is SourceNodeDto -> SourceNode(
                position = position,
                elevation = elevation,
                orientation = Angle.fromDegrees(orientationDegrees),
                configurations = configurations.map {
                    it.toDomain(colorsByName, vehicleTypesByName, sourcePath)
                }
            )

            is TargetNodeDto -> {
                val color = colorsByName[colorName]
                    ?: throw LevelSerializationException(
                        "TargetNode '$id' references unknown color '$colorName' in '$sourcePath'"
                    )
                TargetNode(
                    position = position,
                    elevation = elevation,
                    orientation = Angle.fromDegrees(orientationDegrees),
                    color = color
                )
            }

            is ConnectionNodeDto -> ConnectionNode(
                position = position,
                elevation = elevation
            )
        }
    }

    /** Name references are resolved via the lookup maps built at the start of fromDto() */
    private fun SourceNodeConfigurationDto.toDomain(
        colorsByName: Map<String, Color>,
        vehicleTypesByName: Map<String, VehicleType>,
        sourcePath: String
    ): SourceNodeConfiguration {
        val color = colorsByName[colorName]
            ?: throw LevelSerializationException(
                "SourceNodeConfiguration references unknown color '$colorName' in '$sourcePath'"
            )
        val vehicleType = vehicleTypesByName[vehicleTypeName]
            ?: throw LevelSerializationException(
                "SourceNodeConfiguration references unknown vehicleType '$vehicleTypeName' in '$sourcePath'"
            )
        return SourceNodeConfiguration(
            color = color,
            vehicleType = vehicleType,
            generationSpeed = generationSpeed,
            initialVehicleSpeed = initialVehicleSpeed,
            vehiclesToSpawn = vehiclesToSpawn
        )
    }

    /**
     * Converts the English JSON weather value into the domain enum.
     *
     * Missing values are handled by [LevelDto.weatherCondition]'s default `dry`; unsupported values
     * are reported as serialization errors so malformed level files fail loudly.
     */
    private fun String.toWeatherCondition(sourcePath: String): WeatherCondition {
        return try {
            WeatherCondition.fromJsonValue(this)
        } catch (e: IllegalArgumentException) {
            throw LevelSerializationException(
                "Invalid weatherCondition in '$sourcePath': ${e.message}",
                e
            )
        }
    }
}

/** Thrown when a level cannot be serialized or deserialized.
 * Wraps lower-level exceptions (IO, JSON syntax) with a meaningful message.
 * @property message The message describing the error.
 * @property cause The cause of the error.
 */
class LevelSerializationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
