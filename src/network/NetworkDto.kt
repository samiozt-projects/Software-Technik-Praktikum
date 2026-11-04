package network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Shared JSON settings for the coupling-server API. */
internal val NetworkJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

/**
 * Pixel coordinate used by fields, sources and transitions.
 *
 * @property x Horizontal coordinate in pixels.
 * @property y Vertical coordinate in pixels.
 */
@Serializable
data class CoordinateDTO(
    val x: Int,
    val y: Int
)

/**
 * World metadata returned by the server.
 *
 * @property worldId Unique server ID of the world.
 * @property columns Number of field columns in the world.
 * @property rows Number of field rows in the world.
 * @property fieldHeight Height of one field in server units.
 * @property fieldWidth Width of one field in server units.
 * @property name Optional user-facing world name.
 * @property templateLevelId Optional standard template ID used by this world.
 * @property updatedAt Optional server timestamp for sorting or freshness display.
 */
@Serializable
data class WorldPropertiesDTO(
    val worldId: String,
    val columns: Int,
    val rows: Int,
    val fieldHeight: Int,
    val fieldWidth: Int,
    val name: String? = null,
    val templateLevelId: Int? = null,
    val updatedAt: String? = null
)

/**
 * Request body for creating a world on the server.
 *
 * @property columns Number of field columns to create.
 * @property rows Number of field rows to create.
 * @property fieldHeight Height of each generated field.
 * @property fieldWidth Width of each generated field.
 * @property deleteToken Token required to delete the world later.
 */
@Serializable
data class CreateWorldDTO(
    val columns: Int,
    val rows: Int,
    val fieldHeight: Int,
    val fieldWidth: Int,
    val deleteToken: String
)

/**
 * Response body returned after creating a world.
 *
 * @property worldId Unique server ID of the created world.
 */
@Serializable
data class CreateWorldResponse(
    val worldId: String
)

/** Kind of editor-managed node stored inside a world editor state. */
@Serializable
enum class WorldEditorNodeKind {
    /** Source node controlled by the editor, not by neutral server-side sources. */
    SOURCE,

    /** Target/sink node controlled by the editor. */
    TARGET
}

/**
 * Editor-managed source or sink position and settings.
 *
 * @property id Stable editor-local identifier.
 * @property kind Whether the node is a source or target/sink.
 * @property x X coordinate in level-space pixels.
 * @property y Y coordinate in level-space pixels.
 * @property colorNames Names of all configured colors, in selection order. Sources carry one
 * configuration per color and vehicle type; sinks accept vehicles of any listed color.
 * @property vehicleTypeNames Names of all configured vehicle types, in selection order.
 */
@Serializable
data class WorldEditorNodeDTO(
    val id: String,
    val kind: WorldEditorNodeKind,
    val x: Double,
    val y: Double,
    val colorNames: List<String>,
    val vehicleTypeNames: List<String>
)

/**
 * Complete world editor state loaded from or written to the server.
 *
 * @property worldId Server identifier of the edited world.
 * @property name User-facing world name.
 * @property templateLevelId Standard level template this editor state is based on.
 * @property levelJson Serialized level including editor-managed sources and sinks.
 * @property editorNodes Editor-specific metadata for movable sources and sinks.
 * @property updatedAt Optional server timestamp used by the world list.
 */
@Serializable
data class WorldEditorStateDTO(
    val worldId: String,
    val name: String,
    val templateLevelId: Int,
    val levelJson: String,
    val editorNodes: List<WorldEditorNodeDTO>,
    val updatedAt: String? = null
)

/**
 * Request body for creating or updating a server-backed world editor state.
 *
 * @property name User-facing world name.
 * @property templateLevelId Standard level template this editor state is based on.
 * @property levelJson Serialized level including editor-managed sources and sinks.
 * @property editorNodes Editor-specific metadata for movable sources and sinks.
 */
@Serializable
data class SaveWorldEditorDTO(
    val name: String,
    val templateLevelId: Int,
    val levelJson: String,
    val editorNodes: List<WorldEditorNodeDTO>
)

/**
 * Server-side source node assigned to one field.
 *
 * @property fieldId ID of the field containing this source.
 * @property nodeId Unique server ID of the source node.
 * @property coordinate Source position in field/world coordinates.
 * @property spawnInterval Seconds between server-side vehicle spawns.
 */
@Serializable
data class QuelleNode(
    val fieldId: String,
    val nodeId: String,
    val coordinate: CoordinateDTO,
    val spawnInterval: Double
)

/**
 * Request body for creating a source on a field edge.
 *
 * @property fieldId ID of the field that should receive the source.
 * @property coordinate Source position on an outer field edge.
 * @property spawnInterval Seconds between generated vehicles.
 */
@Serializable
data class CreateQuelleDTO(
    val fieldId: String,
    val coordinate: CoordinateDTO,
    val spawnInterval: Double
)

/**
 * Transition between two neighboring fields.
 *
 * @property nodeId Unique server ID of the transition node.
 * @property fromFieldId Source field of the transition.
 * @property toFieldId Target field of the transition.
 * @property coordinate Position on the shared field edge.
 * @property biDirectionalWay Whether the transition can be used in both directions.
 * @property level Elevation level of the transition.
 */
@Serializable
data class TransitionNode(
    val nodeId: String,
    val fromFieldId: String,
    val toFieldId: String,
    val coordinate: CoordinateDTO,
    val biDirectionalWay: Boolean,
    val level: Int
)

/**
 * Request body for creating a transition between fields.
 *
 * @property fromFieldId Source field of the transition.
 * @property toFieldId Target field of the transition.
 * @property coordinate Position on the shared field edge.
 * @property level Elevation level of the transition.
 * @property biDirectionalWay Whether the transition can be used in both directions.
 */
@Serializable
data class CreateTransitionDTO(
    val fromFieldId: String,
    val toFieldId: String,
    val coordinate: CoordinateDTO,
    val level: Int,
    val biDirectionalWay: Boolean = false
)

/**
 * Field metadata including its server-side sources and transitions.
 *
 * @property fieldId Unique server ID of the field.
 * @property active Whether a client is currently connected to this field.
 * @property coordinate Bottom-left field coordinate in the world.
 * @property parentWorldId ID of the world containing this field.
 * @property fieldQuellen Server-side sources assigned to the field.
 * @property fieldTransitions Transitions connected to the field.
 */
@Serializable
data class FieldPropertiesDTO(
    val fieldId: String,
    val active: Boolean,
    val coordinate: CoordinateDTO,
    val parentWorldId: String,
    val fieldQuellen: List<QuelleNode> = emptyList(),
    val fieldTransitions: List<TransitionNode> = emptyList()
)

/** WebSocket message kinds defined by the coupling server. */
@Serializable
enum class WebSocketMessageType {
    /** Registers a field connection after opening the socket. */
    FIELD_CONNECT,

    /** Deregisters the connected field before closing the socket. */
    FIELD_DISCONNECT,

    /** Reports that a new server-side source is available. */
    QUELLE_ADDED,

    /** Reports that a new transition is available. */
    TRANSITION_ADDED,

    /** Sends a vehicle from this field to a neighboring field. */
    VEHICLE_DEPARTURE,

    /** Delivers a vehicle arriving from a neighboring field. */
    VEHICLE_ARRIVAL,

    /** Delivers a vehicle generated by a server-side source. */
    VEHICLE_SOURCE,

    /** Confirms that a client message was accepted. */
    ACKNOWLEDGMENT,

    /** Reports a server-side or protocol error. */
    ERROR
}

/** Known server-side WebSocket error codes. */
enum class WebSocketErrorCode {
    /** The server could not parse the incoming message. */
    INVALID_MESSAGE_FORMAT,

    /** The requested field could not be registered. */
    FIELD_REGISTRATION_FAILED,

    /** Vehicle handover failed on the server. */
    HANDOVER_FAILED,

    /** The message type is not known by the server. */
    UNKNOWN_MESSAGE_TYPE,

    /** Local fallback for unknown or missing server error codes. */
    UNKNOWN
}

/**
 * Unified WebSocket payload used for field registration and vehicle handover.
 *
 * @property type Message kind that determines the relevant fields.
 * @property fieldId Field affected by registration or source messages.
 * @property transitionNodeId Transition used for a vehicle handover.
 * @property fromFieldId Field where a vehicle leaves.
 * @property toFieldId Field where a vehicle arrives.
 * @property level Elevation level of the transition or vehicle.
 * @property vehicleProperties Free-form JSON vehicle payload.
 * @property sourceNodeId Server-side source that generated a vehicle.
 * @property biDirectionalWay Whether a transition can be used in both directions.
 * @property success Acknowledgment result from the server.
 * @property errorCode Server error code for error messages.
 * @property message Human-readable server message.
 */
@Serializable
data class WebSocketMessage(
    val type: WebSocketMessageType,
    val fieldId: String? = null,
    val transitionNodeId: String? = null,
    val fromFieldId: String? = null,
    val toFieldId: String? = null,
    val level: Int? = null,
    val vehicleProperties: JsonObject? = null,
    val sourceNodeId: String? = null,
    val biDirectionalWay: Boolean? = null,
    val success: Boolean? = null,
    val errorCode: String? = null,
    val message: String? = null
) {
    companion object {
        /** Message sent immediately after opening the WebSocket. */
        fun fieldConnect(fieldId: String) = WebSocketMessage(
            type = WebSocketMessageType.FIELD_CONNECT,
            fieldId = fieldId
        )

        /** Message sent before intentionally closing the WebSocket. */
        fun fieldDisconnect(fieldId: String) = WebSocketMessage(
            type = WebSocketMessageType.FIELD_DISCONNECT,
            fieldId = fieldId
        )

        /** Message sent when a vehicle leaves this field through a transition. */
        fun vehicleDeparture(
            transitionNodeId: String,
            fromFieldId: String,
            toFieldId: String,
            level: Int,
            vehicleProperties: JsonObject
        ) = WebSocketMessage(
            type = WebSocketMessageType.VEHICLE_DEPARTURE,
            transitionNodeId = transitionNodeId,
            fromFieldId = fromFieldId,
            toFieldId = toFieldId,
            level = level,
            vehicleProperties = vehicleProperties
        )
    }
}

/** Current state of the WebSocket connection. */
enum class NetworkConnectionState {
    /** No active socket connection exists. */
    DISCONNECTED,

    /** A socket is opening or waiting for registration acknowledgment. */
    CONNECTING,

    /** The selected field is registered and ready for traffic. */
    CONNECTED,

    /** The connection or protocol handling failed. */
    ERROR
}

/**
 * Last structured WebSocket error received from the server or local parser.
 *
 * @property code Normalized error code used by the client.
 * @property rawCode Original server error code, if present.
 * @property message Human-readable error message.
 */
data class TrafficSimServerError(
    val code: WebSocketErrorCode,
    val rawCode: String?,
    val message: String
)
