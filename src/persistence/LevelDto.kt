package persistence

import domain.GameSimulationConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Root DTO for the level JSON file.
 *
 * Why a DTO layer?
 * The domain model contains bidirectional object references (nodes hold lists of
 * streets, streets hold references back to nodes). These cycles cannot be serialized
 * directly. The DTO layer breaks the cycle by replacing object references with
 * string IDs — nodes are stored as a flat list and streets reference them by ID.
 * 
 * Shared lookup tables:
 * [vehicleTypes] and [colors] are collected once at the root level and referenced
 * by name from nodes and configurations. This avoids duplicating the same object
 * in every place it is used and keeps the file size small.
 * 
 * [weatherCondition] stores English JSON values: `dry`, `wet`, or `snowy`.
 * 
 * @property name The name of the level.
 * @property number The number of the level.
 * @property adhesionCoefficientModifier The adhesion coefficient modifier of the level.
 * @property weatherCondition The weather condition of the level.
 * @property challengeConnectionNodeLimit The challenge connection node limit of the level.
 * @property vehicleTypes A list of vehicle types in the level.
 * @property colors A list of colors in the level.
 * @property nodes A list of nodes in the level.
 * @property streets A list of streets in the level.
 */
@Serializable
data class LevelDto(
    val name: String,
    val number: Int,
    val adhesionCoefficientModifier: Double = GameSimulationConfig.DEFAULT_ADHESION_COEFFICIENT_MODIFIER,
    val weatherCondition: String = "dry",
    val challengeConnectionNodeLimit: Int = GameSimulationConfig.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT,
    val vehicleTypes: List<VehicleTypeDto>,
    val colors: List<ColorDto>,
    val nodes: List<NodeDto>,
    val streets: List<StreetDto>
)

/**
 * Vehicle type DTO for level files
 * 
 * @property name The name of the vehicle type.
 * @property maxSpeed The maximum speed of the vehicle type.
 * @property length The length of the vehicle type.
 * @property adhesionCoefficient The adhesion coefficient of the vehicle type.
 * @property acceleration The acceleration of the vehicle type.
 * @property graphicRef The path to the graphic file for this vehicle type.
 *                      Sets the default value to empty string if not provided.
 */
@Serializable
data class VehicleTypeDto(
    val name: String,
    val maxSpeed: Double,
    val length: Double,
    val adhesionCoefficient: Double,
    val acceleration: Double,
    val graphicRef: String = ""
)

/**
 * Colors are shared between SourceNodeConfigurationDto (vehicle tint) and
 * TargetNodeDto (accepted vehicle color), which is why they are defined at
 * the level root rather than inline.
 * 
 * @property name The name of the color.
 * @property argb The ARGB value of the color.
 */
@Serializable
data class ColorDto(
    val name: String,
    /**
     * Packed ARGB color in `0xAARRGGBB` byte order — the human-readable convention used in the
     * level JSON files (e.g. red = `0xFFFF0000`). LevelSerializer converts it to and from korlibs'
     * [domain.Color] (whose RGBA packs red in the lowest byte), so the stored value never depends
     * on korlibs' internal layout.
     */
    val argb: UInt
)

/**
 * Sealed DTO hierarchy for nodes.
 *
 * Why sealed / polymorphic?
 * All three node types share a common set of fields (id, position, elevation) but
 * carry different additional data. Using a sealed class with a @SerialName
 * discriminator lets kotlinx.serialization write and read a single nodes array
 * with mixed types — the "type" field in each JSON object determines which
 * subclass is instantiated on load.
 *
 * Why string IDs?
 * Nodes are serialized as a flat list. Streets need to reference their endpoint
 * nodes without embedding the full node object (which would duplicate data and
 * re-introduce cycles). String IDs act as foreign keys, matching the pattern used
 * in relational databases and most JSON formats.
 * 
 * @property id The unique identifier of the node.
 * @property x The x-coordinate of the node.
 * @property y The y-coordinate of the node.
 * @property elevation The elevation of the node.
 */
@Serializable
sealed class NodeDto {
    abstract val id: String
    abstract val x: Double
    abstract val y: Double
    abstract val elevation: Int
}

/**
 * [configurations] are stored inline because they belong exclusively to this node and
 * are never shared with other nodes.
 * 
 * @property id The unique identifier of the node.
 * @property x The x-coordinate of the node.
 * @property y The y-coordinate of the node.
 * @property elevation The elevation of the node.
 * @property orientationDegrees The orientation of the node in degrees.
 * @property configurations A list of source node configurations.
 */
@Serializable
@SerialName("SourceNode")
data class SourceNodeDto(
    override val id: String,
    override val x: Double,
    override val y: Double,
    override val elevation: Int,
    val orientationDegrees: Double,
    val configurations: List<SourceNodeConfigurationDto> = emptyList()
) : NodeDto()

/**
 * The color is stored as a name reference into [LevelDto.colors] rather than
 * an inline RGBA value so that renaming a color only requires one change in the file.
 * 
 * @property id The unique identifier of the node.
 * @property x The x-coordinate of the node.
 * @property y The y-coordinate of the node.
 * @property elevation The elevation of the node.
 * @property orientationDegrees The orientation of the node in degrees.
 * @property colorName The name of the color.
 */
@Serializable
@SerialName("TargetNode")
data class TargetNodeDto(
    override val id: String,
    override val x: Double,
    override val y: Double,
    override val elevation: Int,
    val orientationDegrees: Double,
    val colorName: String
) : NodeDto()

/**
 * Passive routing node with no spawning or counting behaviour.
 * No extra fields beyond the shared base are needed.
 * 
 * @property id The unique identifier of the node.
 * @property x The x-coordinate of the node.
 * @property y The y-coordinate of the node.
 * @property elevation The elevation of the node.
 */
@Serializable
@SerialName("ConnectionNode")
data class ConnectionNodeDto(
    override val id: String,
    override val x: Double,
    override val y: Double,
    override val elevation: Int
) : NodeDto()

/**
 * Both [colorName] and [vehicleTypeName] are name references that are resolved
 * back to domain objects during loading by looking them up in the maps built
 * from [LevelDto.colors] and [LevelDto.vehicleTypes].
 * 
 * @property colorName The name of the color.
 * @property vehicleTypeName The name of the vehicle type.
 * @property generationSpeed The speed at which vehicles are generated.
 * @property initialVehicleSpeed The initial speed of the vehicles.
 * @property vehiclesToSpawn The number of vehicles to spawn.
 */
@Serializable
data class SourceNodeConfigurationDto(
    val colorName: String,
    val vehicleTypeName: String,
    val generationSpeed: Double,
    val initialVehicleSpeed: Double,
    val vehiclesToSpawn: Int = GameSimulationConfig.getConfiguredVehiclesToSpawn()
)

/**
 * Nodes are referenced by their generated string IDs rather than by embedded
 * objects to avoid duplication and cycles. During loading, LevelSerializer
 * resolves these IDs back to the already-constructed Node objects.
 * [path] must contain at least 2 points; this is validated during loading.
 * 
 * @property startNodeId The ID of the starting node.
 * @property endNodeId The ID of the ending node.
 * @property path A list of points representing the path of the street.
 */
@Serializable
data class StreetDto(
    val startNodeId: String,
    val endNodeId: String,
    val path: List<PointDto>
)

/**
 * Points are used to define the path of a street.
 * 
 * @property x The x-coordinate of the point.
 * @property y The y-coordinate of the point.
 */
@Serializable
data class PointDto(
    val x: Double,
    val y: Double
)
