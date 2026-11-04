package network

import config.ConfigManager
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Data class holding vehicle message data for the onVehicleMessage callback.
 *
 * @property messageType The type of vehicle message (ARRIVAL or SOURCE)
 * @property vehicleProperties The vehicle properties from the WebSocket message
 * @property transitionNodeId The transition node ID (for ARRIVAL messages)
 * @property fromFieldId The source field ID (for ARRIVAL messages)
 * @property toFieldId The target field ID (for ARRIVAL messages)
 * @property level The elevation level
 * @property sourceNodeId The source node ID (for SOURCE messages)
 * @property fieldId The field ID (for SOURCE messages)
 */
data class VehicleMessageData(
    val messageType: VehicleMessageType,
    val vehicleProperties: JsonObject?,
    val transitionNodeId: String? = null,
    val fromFieldId: String? = null,
    val toFieldId: String? = null,
    val level: Int? = null,
    val sourceNodeId: String? = null,
    val fieldId: String? = null
)

/** 
 * Enum describing the type of a vehicle message.
 * @property VEHICLE_ARRIVAL Message describing a vehicle arriving at a transition node.
 * @property VEHICLE_SOURCE Message describing a vehicle being spawned from a source node.
 */
enum class VehicleMessageType {
    VEHICLE_ARRIVAL,
    VEHICLE_SOURCE
}

/** WebSocket client for field registration, live updates and vehicle handover. */
class TrafficSimWebSocketClient(
    private val config: ConfigManager.NetworkConfig = ConfigManager.loadNetworkConfig(),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(DEFAULT_TIMEOUT)
        .sslContext(TrustedCertificates.trustedSslContext())
        .build(),
    private val onMessage: (WebSocketMessage) -> Unit = {},
    private val onVehicleMessage: (VehicleMessageData) -> Unit = {},
    private val onConnectionLost: () -> Unit = {}
) {
    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var connectedFieldId: String? = null

    @Volatile
    private var keepAliveTask: ScheduledFuture<*>? = null

    @Volatile
    private var intentionalCloseInProgress: Boolean = false

    @Volatile
    private var state: NetworkConnectionState = NetworkConnectionState.DISCONNECTED

    /** Wall-clock time of the last inbound frame; used to detect a silently dead connection. */
    @Volatile
    private var lastInboundActivityAt: Long = 0L

    /** Last structured server or local protocol error. */
    @Volatile
    var lastError: TrafficSimServerError? = null
        private set

    private val storedMessages = Collections.synchronizedList(mutableListOf<WebSocketMessage>())
    private val keepAliveExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "traffic-sim-websocket-keepalive").apply { isDaemon = true }
    }

    /** Current connection state for UI and simulation code. */
    val connectionState: NetworkConnectionState
        get() = state

    /** Copy of all messages received during this client session. */
    val receivedMessages: List<WebSocketMessage>
        get() = synchronized(storedMessages) { storedMessages.toList() }

    /** Opens the WebSocket and sends FIELD_CONNECT for the selected field. */
    fun connect(fieldId: String): CompletableFuture<WebSocket> {
        state = NetworkConnectionState.CONNECTING
        lastError = null
        intentionalCloseInProgress = false
        connectedFieldId = fieldId

        return httpClient.newWebSocketBuilder()
            .connectTimeout(DEFAULT_TIMEOUT)
            .buildAsync(config.webSocketUri, FieldWebSocketListener(fieldId))
            .whenComplete { socket, error ->
                if (error != null) {
                    registerLocalError("WebSocket connection failed: ${error.message}")
                } else {
                    webSocket = socket
                }
            }
    }

    /** Sends FIELD_DISCONNECT and closes the WebSocket. */
    fun disconnect(): CompletableFuture<WebSocket>? =
        closeSocket(releaseField = true, reason = "client disconnect")

    /**
     * Closes the WebSocket without sending FIELD_DISCONNECT.
     *
     * Used when the FIELD_CONNECT registration itself failed: the server never accepted this
     * table's field, so sending FIELD_DISCONNECT could release a registration owned by another
     * table (or is rejected as a protocol error).
     */
    fun abortFailedRegistration(): CompletableFuture<WebSocket>? =
        closeSocket(releaseField = false, reason = "field registration failed")

    private fun closeSocket(releaseField: Boolean, reason: String): CompletableFuture<WebSocket>? {
        val socket = webSocket ?: run {
            markDisconnected()
            return null
        }
        intentionalCloseInProgress = true
        if (releaseField) {
            connectedFieldId?.let { sendMessage(WebSocketMessage.fieldDisconnect(it)) }
        }
        stopKeepAlive()
        return socket.sendClose(WebSocket.NORMAL_CLOSURE, reason)
            .whenComplete { _, _ ->
                // A slow close completion must not tear down a connection that was
                // re-established in the meantime (same identity guard as onClose/onError).
                if (webSocket === socket) {
                    markDisconnected()
                }
            }
    }

    /** Sends a vehicle handover message to the server. */
    fun sendVehicleDeparture(
        transitionNodeId: String,
        fromFieldId: String,
        toFieldId: String,
        level: Int,
        vehicleProperties: JsonObject
    ): CompletableFuture<WebSocket> {
        return sendMessage(
            WebSocketMessage.vehicleDeparture(
                transitionNodeId = transitionNodeId,
                fromFieldId = fromFieldId,
                toFieldId = toFieldId,
                level = level,
                vehicleProperties = vehicleProperties
            )
        )
    }

    /**
     * Sends a vehicle handover message to the server using a Vehicle domain object.
     *
     * @param transitionNodeId The ID of the transition node
     * @param fromFieldId The ID of the field the vehicle is leaving from
     * @param toFieldId The ID of the field the vehicle is going to
     * @param level The elevation level of the transition
     * @param vehicle The Vehicle domain object to send
     * @return CompletableFuture that completes when the message is sent
     */
    fun sendVehicleDeparture(
        transitionNodeId: String,
        fromFieldId: String,
        toFieldId: String,
        level: Int,
        vehicle: domain.Vehicle
    ): CompletableFuture<WebSocket> {
        val vehicleProperties = VehicleWebSocketConverter.vehicleToWebSocketProperties(vehicle)
        return sendVehicleDeparture(transitionNodeId, fromFieldId, toFieldId, level, vehicleProperties)
    }

    /** Clears the locally stored incoming message history. */
    fun clearReceivedMessages() {
        storedMessages.clear()
    }

    internal fun handleIncomingText(text: String) {
        val message = try {
            NetworkJson.decodeFromString<WebSocketMessage>(text)
        } catch (e: SerializationException) {
            registerServerError(
                WebSocketMessage(
                    type = WebSocketMessageType.ERROR,
                    errorCode = WebSocketErrorCode.INVALID_MESSAGE_FORMAT.name,
                    message = e.message ?: "Invalid WebSocket message format."
                )
            )
            return
        }

        storedMessages += message
        when (message.type) {
            WebSocketMessageType.ACKNOWLEDGMENT -> handleAcknowledgment(message)
            WebSocketMessageType.ERROR -> registerServerError(message)
            WebSocketMessageType.VEHICLE_ARRIVAL -> handleVehicleArrival(message)
            WebSocketMessageType.VEHICLE_SOURCE -> handleVehicleSource(message)
            WebSocketMessageType.QUELLE_ADDED,
            WebSocketMessageType.TRANSITION_ADDED -> dispatchMessage(message)
            WebSocketMessageType.FIELD_CONNECT,
            WebSocketMessageType.FIELD_DISCONNECT,
            WebSocketMessageType.VEHICLE_DEPARTURE -> dispatchMessage(message)
        }
    }

    internal fun encode(message: WebSocketMessage): String {
        return NetworkJson.encodeToString(message)
    }

    private fun sendMessage(message: WebSocketMessage): CompletableFuture<WebSocket> {
        val socket = webSocket
        if (socket == null) {
            registerLocalError("WebSocket is not connected.")
            return CompletableFuture.failedFuture(IllegalStateException("WebSocket is not connected."))
        }
        return socket.sendText(encode(message), true)
    }

    private fun handleAcknowledgment(message: WebSocketMessage) {
        if (message.success == false) {
            registerServerError(
                message.copy(
                    type = WebSocketMessageType.ERROR,
                    errorCode = message.errorCode ?: WebSocketErrorCode.UNKNOWN.name,
                    message = message.message ?: "Server rejected the request."
                )
            )
            return
        }
        if (state == NetworkConnectionState.CONNECTING) {
            state = NetworkConnectionState.CONNECTED
        }
        dispatchMessage(message)
    }

    private fun dispatchMessage(message: WebSocketMessage) {
        try {
            onMessage(message)
        } catch (e: Exception) {
            registerLocalError("WebSocket message handler failed: ${e.message}")
        }
    }

    private fun handleVehicleArrival(message: WebSocketMessage) {
        try {
            val vehicleData = VehicleMessageData(
                messageType = VehicleMessageType.VEHICLE_ARRIVAL,
                vehicleProperties = message.vehicleProperties,
                transitionNodeId = message.transitionNodeId,
                fromFieldId = message.fromFieldId,
                toFieldId = message.toFieldId,
                level = message.level
            )
            onVehicleMessage(vehicleData)
            dispatchMessage(message)
        } catch (e: Exception) {
            registerLocalError("Failed to handle vehicle arrival: ${e.message}")
        }
    }

    private fun handleVehicleSource(message: WebSocketMessage) {
        try {
            val vehicleData = VehicleMessageData(
                messageType = VehicleMessageType.VEHICLE_SOURCE,
                vehicleProperties = message.vehicleProperties,
                sourceNodeId = message.sourceNodeId,
                fieldId = message.fieldId,
                level = message.level
            )
            onVehicleMessage(vehicleData)
            dispatchMessage(message)
        } catch (e: Exception) {
            registerLocalError("Failed to handle vehicle source: ${e.message}")
        }
    }

    private fun registerServerError(message: WebSocketMessage) {
        val rawCode = message.errorCode
        lastError = TrafficSimServerError(
            code = rawCode?.let { code -> WebSocketErrorCode.entries.firstOrNull { it.name == code } }
                ?: WebSocketErrorCode.UNKNOWN,
            rawCode = rawCode,
            message = message.message ?: "Unknown WebSocket error."
        )
        state = NetworkConnectionState.ERROR
        dispatchMessage(message)
    }

    private fun registerLocalError(message: String) {
        lastError = TrafficSimServerError(
            code = WebSocketErrorCode.UNKNOWN,
            rawCode = null,
            message = message
        )
        state = NetworkConnectionState.ERROR
        stopKeepAlive()
    }

    private fun startKeepAlive() {
        stopKeepAlive()
        markInboundActivity()
        keepAliveTask = keepAliveExecutor.scheduleAtFixedRate(
            { runHeartbeat() },
            KEEPALIVE_SECONDS,
            KEEPALIVE_SECONDS,
            TimeUnit.SECONDS
        )
    }

    /** Records that an inbound frame arrived, i.e. the server is still responding. */
    internal fun markInboundActivity() {
        lastInboundActivityAt = System.currentTimeMillis()
    }

    /**
     * Returns whether the server has been silent for longer than [HEARTBEAT_TIMEOUT_MS].
     *
     * This is how a silently dropped link (e.g. Wi-Fi switched off, where no close frame ever
     * arrives) is detected in bounded time instead of waiting for the OS TCP timeout.
     *
     * @param nowMs current wall-clock time in milliseconds.
     */
    internal fun isSilentPastDeadline(nowMs: Long): Boolean =
        nowMs - lastInboundActivityAt > HEARTBEAT_TIMEOUT_MS

    /**
     * One heartbeat cycle: declares the connection lost when the server has gone silent past the
     * deadline, otherwise sends a ping to probe liveness and keep the link warm.
     */
    private fun runHeartbeat() {
        val socket = webSocket ?: return
        if (isSilentPastDeadline(System.currentTimeMillis())) {
            handleConnectionLoss("No server response within $HEARTBEAT_TIMEOUT_MS ms")
            return
        }
        socket.sendPing(ByteBuffer.allocate(0))
            .whenComplete { _, error ->
                if (error != null) handleConnectionLoss("WebSocket ping failed: ${error.message}")
            }
    }

    /**
     * Handles a detected loss of an established connection: records the error, aborts the dead
     * socket and notifies [onConnectionLost] so the reconnect flow can start.
     *
     * Idempotent — only the first detection per connection notifies, so a heartbeat timeout and a
     * pending ping failure cannot start two reconnects.
     *
     * @param reason human-readable cause stored as the last error.
     */
    internal fun handleConnectionLoss(reason: String) {
        if (state == NetworkConnectionState.ERROR) return
        registerLocalError(reason)
        webSocket?.abort()
        onConnectionLost()
    }

    private fun stopKeepAlive() {
        keepAliveTask?.cancel(false)
        keepAliveTask = null
    }

    private fun markDisconnected() {
        stopKeepAlive()
        webSocket = null
        connectedFieldId = null
        state = NetworkConnectionState.DISCONNECTED
    }

    private inner class FieldWebSocketListener(
        private val fieldId: String
    ) : WebSocket.Listener {
        private val textBuffer = StringBuilder()

        override fun onOpen(webSocket: WebSocket) {
            this@TrafficSimWebSocketClient.webSocket = webSocket
            webSocket.request(1)
            sendMessage(WebSocketMessage.fieldConnect(fieldId))
            startKeepAlive()
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> {
            markInboundActivity()
            textBuffer.append(data)
            if (last) {
                val completeMessage = textBuffer.toString()
                textBuffer.clear()
                handleIncomingText(completeMessage)
            }
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onPing(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*> {
            markInboundActivity()
            webSocket.request(1)
            return webSocket.sendPong(message)
        }

        override fun onPong(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*> {
            markInboundActivity()
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> {
            if (webSocket !== this@TrafficSimWebSocketClient.webSocket) {
                return CompletableFuture.completedFuture(null)
            }
            val intentionalClose = intentionalCloseInProgress
            markDisconnected()
            intentionalCloseInProgress = false
            if (!intentionalClose) {
                onConnectionLost()
            }
            return CompletableFuture.completedFuture(null)
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            if (webSocket !== this@TrafficSimWebSocketClient.webSocket) {
                return
            }
            if (intentionalCloseInProgress) {
                markDisconnected()
                intentionalCloseInProgress = false
                return
            }
            registerLocalError(error.message ?: "WebSocket connection error.")
            onConnectionLost()
        }
    }

    companion object {
        private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(10)

        /** Interval at which a heartbeat ping is sent and the silence deadline is checked. */
        private const val KEEPALIVE_SECONDS = 10L

        /**
         * Maximum time without any inbound frame before the connection is treated as lost.
         *
         * Bounds how long a silently dropped link (Wi-Fi off, cable pulled) stays undetected. With
         * the [KEEPALIVE_SECONDS] check interval the reconnect overlay appears within roughly
         * [HEARTBEAT_TIMEOUT_MS] .. [HEARTBEAT_TIMEOUT_MS] + [KEEPALIVE_SECONDS] seconds, instead of
         * waiting for the OS TCP timeout (which can be minutes).
         */
        internal const val HEARTBEAT_TIMEOUT_MS = 30_000L
    }
}
