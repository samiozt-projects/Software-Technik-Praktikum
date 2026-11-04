package network

import config.ConfigManager
import domain.ConnectionState
import domain.TableTile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.milliseconds

private const val WEBSOCKET_REGISTRATION_TIMEOUT_MS = 5000L
private const val WEBSOCKET_REGISTRATION_POLL_MS = 100L

/**
 * Result of trying to register or deregister a selected server field for live WebSocket traffic.
 */
sealed class FieldRegistrationResult {
    /** The field operation completed successfully. */
    object Success : FieldRegistrationResult()

    /**
     * The field operation failed.
     *
     * @property message user-facing failure details.
     */
    data class Failure(val message: String) : FieldRegistrationResult()
}

/**
 * Manages the real server connection using WebSocket.
 */
class ServerConnectionManager {
    
    /** Current connection state */
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set
    
    /** The WebSocket client for field registration and vehicle handover */
    private val webSocketClient: TrafficSimWebSocketClient

    /** REST client used for world and field setup data. */
    private val restClient: TrafficSimRestClient
    
    /** The server address for display purposes */
    val serverAddress: String
    
    /** Coroutine scope for managing connection operations */
    private val connectionScope = CoroutineScope(Dispatchers.Default)
    
    /** Current connection job */
    private var connectionJob: Job? = null

    /** Forces local connection behavior in tests without changing app configuration files. */
    var useTestingConnectionForTesting: Boolean = false
    
    /** Field ID that we're connecting to */
    private var currentFieldId: String? = null
    
    /** Callback for when connection state changes */
    private var onStateChange: (() -> Unit)? = null
    
    /** Callback for when vehicle messages are received */
    private var onVehicleMessage: ((VehicleMessageData) -> Unit)? = null
    
    /** Callback for when WebSocket messages are received */
    private var onWebSocketMessage: ((WebSocketMessage) -> Unit)? = null

    /** Callback invoked once when an established connection is lost unexpectedly. */
    private var onConnectionLost: (() -> Unit)? = null

    /** Job running the automatic reconnect retry loop, if any. */
    private var reconnectJob: Job? = null

    /** Delay between automatic reconnect attempts (and before the first one). Overridable for tests. */
    var reconnectDelayMs: Long = 2000

    /** Per-world tile layouts */
    private val tilesByWorld: MutableMap<String, List<TableTile>> = mutableMapOf()

    /** Per-world server field metadata used by simulation handover mapping. */
    private val fieldsByWorld: MutableMap<String, List<FieldPropertiesDTO>> = mutableMapOf()
    
    /** Per-world identifier of the tile the local table has claimed */
    private val ownTileIdByWorld: MutableMap<String, String?> = mutableMapOf()
    
    /** Identifier of the world the local table currently has a tile claim in */
    private var currentClaimWorldId: String? = null
    
    /** 
     * Constructor of [ServerConnectionManager] 
     * Loads the network configuration from the [ConfigManager] 
     * and initializes the WebSocket client.
     */
    constructor() {
        val config = ConfigManager.loadNetworkConfig()
        // Store the server address for display
        serverAddress = config.baseUrl
        
        // Initialize with empty callbacks, we'll set them up through our own callbacks
        webSocketClient = TrafficSimWebSocketClient(
            config = config,
            onMessage = { message ->
                onWebSocketMessage?.invoke(message)
            },
            onVehicleMessage = { vehicleData ->
                onVehicleMessage?.invoke(vehicleData)
            },
            // An unexpected socket close/error while connected is surfaced as a lost connection.
            // loseConnection() is a no-op unless the state is CONNECTED, so an intentional
            // disconnect (which first moves to DISCONNECTING) does not start a reconnect.
            onConnectionLost = { loseConnection() }
        )
        restClient = TrafficSimRestClient(config)
    }
    
    /**
     * Sets the callback for connection state changes.
     *
     * @param callback The callback to set.
     */
    fun setOnStateChange(callback: () -> Unit) {
        this.onStateChange = callback
    }
    
    /**
     * Sets the callback for WebSocket messages.
     *
     * @param callback The callback to set.
     */
    fun setOnWebSocketMessage(callback: (WebSocketMessage) -> Unit) {
        this.onWebSocketMessage = callback
    }
    
    /**
     * Sets the callback for vehicle messages.
     *
     * @param callback The callback to set.
     */
    fun setOnVehicleMessage(callback: (VehicleMessageData) -> Unit) {
        this.onVehicleMessage = callback
    }

    /**
     * Clears the vehicle-message callback.
     */
    fun clearOnVehicleMessage() {
        this.onVehicleMessage = null
    }

    /**
     * Sets the callback invoked once whenever an established connection is lost through
     * [loseConnection]. The application wires this to switch to the multi-table mode scene where
     * the reconnect overlay is shown.
     *
     * @param callback The callback to set.
     */
    fun setOnConnectionLost(callback: () -> Unit) {
        this.onConnectionLost = callback
    }
    
    /**
     * Opens the REST-backed server session.
     *
     * @param fieldId ignored for compatibility with older placeholder-field callers.
     */
    fun connect(fieldId: String) {
        connect()
    }

    /**
     * Opens a server session for REST-backed multi-table menus.
     *
     * The WebSocket itself is registered only after a world field was selected, because the server
     * requires a real field ID generated from a selected world. This method therefore verifies that
     * the configured server is reachable and then marks the table as connected for world selection.
     */
    fun connect() {
        if (connectionState != ConnectionState.DISCONNECTED) {
            return
        }
        
        val config = ConfigManager.loadNetworkConfig()
        
        connectionState = ConnectionState.CONNECTING
        currentFieldId = null
        onStateChange?.invoke()
        
        if (useTestingConnectionForTesting || !config.active) {
            handleTestingConnection()
            return
        }

        establishServerSession()
    }

    /**
     * Registers the local table's selected server field through the live WebSocket.
     *
     * @param fieldId real server field ID from the selected world.
     */
    fun connectSelectedField(fieldId: String): CompletableFuture<FieldRegistrationResult> {
        val config = ConfigManager.loadNetworkConfig()
        val previousState = connectionState
        currentFieldId = fieldId
        if (previousState != ConnectionState.CONNECTED) {
            connectionState = ConnectionState.CONNECTING
            onStateChange?.invoke()
        }
        if (useTestingConnectionForTesting || !config.active) {
            connectionState = ConnectionState.CONNECTED
            onStateChange?.invoke()
            return CompletableFuture.completedFuture(FieldRegistrationResult.Success)
        }
        return establishWebSocketConnection(fieldId, previousState)
    }

    /**
     * Checks that the configured REST server is reachable before enabling world selection.
     */
    private fun establishServerSession() {
        connectionJob = connectionScope.launch {
            try {
                restClient.getWorlds()
                connectionState = ConnectionState.CONNECTED
                onStateChange?.invoke()
            } catch (e: Exception) {
                println("Server session failed: ${e.message}")
                connectionState = ConnectionState.DISCONNECTED
                onStateChange?.invoke()
            }
        }
    }
    
    /**
     * Handles the connection process when network is disabled (testing mode).
     */
    private fun handleTestingConnection() {
        if (connectionDelayForTesting > 0) {
            connectionJob = connectionScope.launch {
                delay(connectionDelayForTesting.milliseconds)
                if (connectionState == ConnectionState.CONNECTING) {
                    connectionState = ConnectionState.CONNECTED
                    onStateChange?.invoke()
                }
            }
        } else {
            // Connect immediately if no delay is set
            connectionState = ConnectionState.CONNECTED
            onStateChange?.invoke()
        }
    }
    
    /**
     * Establishes the actual WebSocket connection to the server.
     *
     * @param fieldId The field ID to connect to.
     */
    private fun establishWebSocketConnection(
        fieldId: String,
        previousState: ConnectionState
    ): CompletableFuture<FieldRegistrationResult> {
        val result = CompletableFuture<FieldRegistrationResult>()
        connectionJob = connectionScope.launch {
            try {
                releasePreviousFieldRegistration()
                val future: CompletableFuture<*> = webSocketClient.connect(fieldId)

                // Wait for connection to complete
                withContext(Dispatchers.IO) {
                    future.get()
                }

                waitForWebSocketRegistration()

                // Check if connection was successful
                if (webSocketClient.connectionState == NetworkConnectionState.CONNECTED) {
                    connectionState = ConnectionState.CONNECTED
                    onStateChange?.invoke()
                    result.complete(FieldRegistrationResult.Success)
                } else {
                    result.complete(handleFieldRegistrationFailure(previousState))
                    onStateChange?.invoke()
                }
            } catch (e: Exception) {
                println("Connection failed: ${e.message}")
                result.complete(handleFieldRegistrationFailure(previousState, e))
                onStateChange?.invoke()
            }
        }
        return result
    }

    /**
     * Sends FIELD_DISCONNECT for a still-registered previous field before a new field is
     * registered, so switching tiles or worlds never leaves the old field claimed on the server.
     */
    private suspend fun releasePreviousFieldRegistration() {
        if (webSocketClient.connectionState != NetworkConnectionState.CONNECTED) return
        withContext(Dispatchers.IO) {
            webSocketClient.disconnect()?.get()
        }
    }

    /**
     * Handles a failed selected-field registration without losing the surrounding REST-backed
     * server session when the user was already connected before choosing the field.
     */
    private fun handleFieldRegistrationFailure(
        previousState: ConnectionState,
        cause: Throwable? = null
    ): FieldRegistrationResult {
        val serverError = webSocketClient.lastError
        println("Field registration failed: ${serverError?.message ?: cause?.message ?: "unknown error"}")
        webSocketClient.abortFailedRegistration()
        currentFieldId = null
        // A concurrently detected connection loss (RECONNECTING) must not be overwritten here,
        // otherwise the running reconnect loop would be silently disarmed.
        if (connectionState != ConnectionState.RECONNECTING) {
            connectionState = if (previousState == ConnectionState.CONNECTED) {
                ConnectionState.CONNECTED
            } else {
                ConnectionState.DISCONNECTED
            }
        }
        return FieldRegistrationResult.Failure(fieldRegistrationErrorMessage(serverError, cause))
    }

    private fun fieldRegistrationErrorMessage(
        serverError: TrafficSimServerError?,
        cause: Throwable?
    ): String {
        val detail = serverError?.message ?: cause?.message
        return if (detail.isNullOrBlank()) {
            "Feld konnte nicht registriert werden."
        } else {
            "Feld konnte nicht registriert werden: ${detail.take(90)}"
        }
    }

    /**
     * Deregisters the currently selected field without closing the surrounding server session.
     */
    fun disconnectSelectedField(): CompletableFuture<FieldRegistrationResult> {
        val fieldId = currentFieldId ?: return CompletableFuture.completedFuture(FieldRegistrationResult.Success)
        val config = ConfigManager.loadNetworkConfig()
        if (useTestingConnectionForTesting || !config.active) {
            currentFieldId = null
            return CompletableFuture.completedFuture(FieldRegistrationResult.Success)
        }

        val result = CompletableFuture<FieldRegistrationResult>()
        connectionJob = connectionScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    webSocketClient.disconnect()?.get()
                }
                currentFieldId = null
                onStateChange?.invoke()
                result.complete(FieldRegistrationResult.Success)
            } catch (e: Exception) {
                println("Field deregistration failed for $fieldId: ${e.message}")
                result.complete(FieldRegistrationResult.Failure(fieldDeregistrationErrorMessage(e)))
            }
        }
        return result
    }

    private fun fieldDeregistrationErrorMessage(cause: Throwable): String {
        val detail = cause.message
        return if (detail.isNullOrBlank()) {
            "Feld konnte nicht freigegeben werden."
        } else {
            "Feld konnte nicht freigegeben werden: ${detail.take(90)}"
        }
    }

    /**
     * Waits until the server answered the FIELD_CONNECT registration.
     */
    private suspend fun waitForWebSocketRegistration() {
        val timeoutAt = System.currentTimeMillis() + WEBSOCKET_REGISTRATION_TIMEOUT_MS
        while (
            System.currentTimeMillis() < timeoutAt &&
            webSocketClient.connectionState == NetworkConnectionState.CONNECTING
        ) {
            delay(WEBSOCKET_REGISTRATION_POLL_MS.milliseconds)
        }
    }
    
    /**
     * Disconnects from the server.
     */
    fun disconnect() {
        if (connectionState != ConnectionState.CONNECTED) {
            return
        }
        
        val config = ConfigManager.loadNetworkConfig()
        
        if (useTestingConnectionForTesting || !config.active) {
            handleTestingDisconnection()
            return
        }
        
        performWebSocketDisconnection()
    }
    
    /**
     * Handles the disconnection process when network is disabled (testing mode).
     */
    private fun handleTestingDisconnection() {
        connectionState = ConnectionState.DISCONNECTED
        currentFieldId = null
        
        // Release any tile claims
        currentClaimWorldId?.let { ownTileIdByWorld[it] = null }
        currentClaimWorldId = null
        
        onStateChange?.invoke()
    }
    
    /**
     * Performs the actual WebSocket disconnection from the server.
     */
    private fun performWebSocketDisconnection() {
        connectionState = ConnectionState.DISCONNECTING
        onStateChange?.invoke()
        
        connectionJob = connectionScope.launch {
            try {
                webSocketClient.disconnect()
                
                // Wait a bit for disconnect to complete
                delay(500.milliseconds)
                
                connectionState = ConnectionState.DISCONNECTED
                currentFieldId = null
                
                // Release any tile claims
                currentClaimWorldId?.let { ownTileIdByWorld[it] = null }
                currentClaimWorldId = null
                
                onStateChange?.invoke()
            } catch (e: Exception) {
                println("Disconnection failed: ${e.message}")
                connectionState = ConnectionState.DISCONNECTED
                currentFieldId = null
                onStateChange?.invoke()
            }
        }
    }
    
    /**
     * Cancels the current connection attempt.
     */
    fun cancelConnection() {
        connectionJob?.cancel()
        connectionJob = null
        
        if (connectionState == ConnectionState.CONNECTING) {
            connectionState = ConnectionState.DISCONNECTED
            onStateChange?.invoke()
        }
    }
    
    /**
     * Cancels the current disconnection attempt.
     */
    fun cancelDisconnection() {
        connectionJob?.cancel()
        connectionJob = null
        
        if (connectionState == ConnectionState.DISCONNECTING) {
            connectionState = ConnectionState.CONNECTED
            onStateChange?.invoke()
        }
    }
    
    /**
     * Handles an unexpected loss of an established connection (server outage or dropped socket).
     *
     * Only acts while [connectionState] is [ConnectionState.CONNECTED]: it switches to
     * [ConnectionState.RECONNECTING], notifies the state and connection-lost listeners so the UI can
     * bring up the reconnect overlay, and starts the automatic reconnect. The tile claim is kept, so
     * a successful reconnect resumes play on the same tile; only cancelling releases it.
     *
     * Safe to call from the WebSocket callback thread.
     */
    fun loseConnection() {
        if (connectionState != ConnectionState.CONNECTED) {
            return
        }
        connectionState = ConnectionState.RECONNECTING
        onStateChange?.invoke()
        onConnectionLost?.invoke()
        startReconnect()
    }

    /**
     * Runs the automatic reconnect: retries with [reconnectDelayMs] between attempts until the
     * connection is restored ([ConnectionState.CONNECTED]) or the attempt is cancelled through
     * [cancelReconnect]. In local/testing mode every attempt succeeds immediately.
     */
    private fun startReconnect() {
        reconnectJob?.cancel()
        reconnectJob = connectionScope.launch {
            val config = ConfigManager.loadNetworkConfig()
            val localMode = useTestingConnectionForTesting || !config.active
            while (connectionState == ConnectionState.RECONNECTING) {
                delay(reconnectDelayMs.milliseconds)
                if (connectionState != ConnectionState.RECONNECTING) break
                val reconnected = localMode || tryReestablishConnection()
                if (reconnected) {
                    connectionState = ConnectionState.CONNECTED
                    onStateChange?.invoke()
                    break
                }
            }
        }
    }

    /**
     * Attempts to re-establish the live connection once. Re-registers the previously selected field
     * over the WebSocket when one is known, otherwise re-checks REST reachability.
     *
     * @return true when the connection was successfully restored.
     */
    private suspend fun tryReestablishConnection(): Boolean {
        return try {
            currentFieldId?.let { tryReestablishFieldConnection(it) } ?: tryReestablishServerSession()
        } catch (e: Exception) {
            println("Reconnect attempt failed: ${e.message}")
            false
        }
    }

    /** Re-registers the previously selected field over the WebSocket. */
    private suspend fun tryReestablishFieldConnection(fieldId: String): Boolean {
        withContext(Dispatchers.IO) {
            webSocketClient.connect(fieldId).get()
        }
        waitForWebSocketRegistration()
        return webSocketClient.connectionState == NetworkConnectionState.CONNECTED
    }

    /** Checks whether the REST-backed server session can still be reached. */
    private fun tryReestablishServerSession(): Boolean {
        restClient.getWorlds()
        return true
    }

    /**
     * Cancels the automatic reconnect and gives up the dropped connection.
     *
     * Mirrors a full disconnect: releases the tile claim and returns to
     * [ConnectionState.DISCONNECTED].
     */
    fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        if (connectionState == ConnectionState.RECONNECTING) {
            connectionState = ConnectionState.DISCONNECTED
            currentFieldId = null
            currentClaimWorldId?.let { ownTileIdByWorld[it] = null }
            currentClaimWorldId = null
            onStateChange?.invoke()
        }
    }

    /**
     * Returns the tile layout for the specified world ID.
     *
     * @param worldId The world ID to get the tile layout for.
     * @return The tile layout for the specified world ID.
     */
    fun tilesFor(worldId: String): List<TableTile> {
        return tilesByWorld.getOrPut(worldId) { loadFieldTileLayout(worldId) }
    }

    /**
     * Pre-registers an all-free tile layout for a newly created local world.
     *
     * @param worldId stable identifier of the world to initialize.
     */
    fun registerAllFreeTiles(worldId: String, columns: Int = 3, rows: Int = 3) {
        val tileCount = (columns.coerceAtLeast(1) * rows.coerceAtLeast(1)).coerceAtLeast(1)
        tilesByWorld[worldId] = List(tileCount) { index -> TableTile("slot-${index + 1}", occupiedByOtherTable = false) }
    }

    /**
     * Registers a server-provided field layout for a world, preserving real field IDs so later
     * FIELD_CONNECT registration uses values the coupling server actually knows.
     */
    fun registerFieldTiles(worldId: String, fields: List<FieldPropertiesDTO>) {
        fieldsByWorld[worldId] = fields
        tilesByWorld[worldId] = fields.map { field ->
            TableTile(field.fieldId, occupiedByOtherTable = field.active)
        }
    }

    /**
     * Returns cached server field metadata for a world, loading it from REST when necessary.
     *
     * @param worldId server world identifier.
     * @return field metadata including sources and transitions, or an empty list if unavailable.
     */
    fun fieldsFor(worldId: String): List<FieldPropertiesDTO> {
        fieldsByWorld[worldId]?.let { return it }
        val config = ConfigManager.loadNetworkConfig()
        if (useTestingConnectionForTesting || !config.active) {
            return emptyList()
        }
        return try {
            restClient.getFields(worldId).also { fieldsByWorld[worldId] = it }
        } catch (e: Exception) {
            println("Could not load field metadata for world $worldId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Drops cached field metadata while keeping the selectable tile layout and local tile claim.
     *
     * @param worldId server world identifier whose fields should be reloaded on next use.
     */
    fun forgetFieldMetadata(worldId: String) {
        fieldsByWorld.remove(worldId)
    }
    
    /**
     * Returns the tile ID claimed by the local table in the specified world.
     *
     * @param worldId The world ID to get the claimed tile ID for.
     * @return The tile ID claimed by the local table in the specified world.
     */
    fun ownTileIdFor(worldId: String): String? = ownTileIdByWorld[worldId]
    
    /**
     * Claims a tile in the specified world for the local table.
     *
     * @param worldId The world ID to claim a tile in.
     * @param tileId The tile ID to claim.
     * @return True if the tile was claimed successfully, false otherwise.
     */
    fun claimTile(worldId: String, tileId: String): Boolean {
        val tile = tilesFor(worldId).firstOrNull { it.id == tileId } ?: return false
        if (tile.occupiedByOtherTable) return false

        val previousClaimWorldId = currentClaimWorldId
        if (previousClaimWorldId != null && previousClaimWorldId != worldId) {
            ownTileIdByWorld[previousClaimWorldId] = null
        }

        ownTileIdByWorld[worldId] = tileId
        currentClaimWorldId = worldId
        return true
    }
    
    /**
     * Releases the tile claim in the specified world.
     *
     * @param worldId The world ID to release the tile claim in.
     */
    fun releaseTile(worldId: String) {
        ownTileIdByWorld[worldId] = null
        if (currentClaimWorldId == worldId) {
            currentClaimWorldId = null
        }
    }

    /**
     * Drops all locally tracked tile state for a world that no longer exists (e.g. after the
     * world was deleted): the cached tile layout and the local table's tile claim in that world.
     * Tile state of all other worlds stays untouched.
     *
     * @param worldId The ID of the removed world.
     */
    fun forgetWorldTiles(worldId: String) {
        tilesByWorld.remove(worldId)
        fieldsByWorld.remove(worldId)
        ownTileIdByWorld.remove(worldId)
        if (currentClaimWorldId == worldId) {
            currentClaimWorldId = null
        }
    }

    /**
     * Sends a vehicle departure message to the server.
     *
     * @param transitionNodeId The ID of the transition node.
     * @param fromFieldId The ID of the field to depart from.
     * @param toFieldId The ID of the field to depart to.
     * @param level The level of the vehicle.
     * @param vehicleProperties The properties of the vehicle.
     * @return A CompletableFuture representing the asynchronous operation.
     */
    fun sendVehicleDeparture(
        transitionNodeId: String,
        fromFieldId: String,
        toFieldId: String,
        level: Int,
        vehicleProperties: JsonObject
    ): CompletableFuture<*> {
        return webSocketClient.sendVehicleDeparture(transitionNodeId, fromFieldId, toFieldId, level, vehicleProperties)
    }

    /**
     * Resets the connection manager to its initial state.
     */
    fun reset() {
        connectionJob?.cancel()
        connectionJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        onConnectionLost = null
        onVehicleMessage = null
        connectionState = ConnectionState.DISCONNECTED
        currentFieldId = null
        tilesByWorld.clear()
        fieldsByWorld.clear()
        ownTileIdByWorld.clear()
        currentClaimWorldId = null
        useTestingConnectionForTesting = false
    }
    
    /**
     * Sets the connection state directly for testing purposes.
     * This bypasses the normal connection flow and should only be used in tests.
     *
     * @param state The connection state to set.
     */
    fun setConnectionStateForTesting(state: ConnectionState) {
        useTestingConnectionForTesting = true
        connectionState = state
    }
    
    /**
     * Sets the connection delay for testing purposes.
     * This allows tests to control the connection timing.
     */
    var connectionDelayForTesting: Long = 900

    /**
     * Returns a fresh mock tile layout for testing purposes.
     *
     * @return A fresh mock tile layout.
     */
    private fun defaultTileLayout(): List<TableTile> = listOf(
        TableTile("Tisch A", occupiedByOtherTable = true),
        TableTile("Tisch B", occupiedByOtherTable = true),
        TableTile("frei 1", occupiedByOtherTable = false),
        TableTile("Tisch C", occupiedByOtherTable = true),
        TableTile("frei 2", occupiedByOtherTable = false),
        TableTile("frei 3", occupiedByOtherTable = false),
        TableTile("frei 4", occupiedByOtherTable = false),
        TableTile("frei 5", occupiedByOtherTable = false),
        TableTile("frei 6", occupiedByOtherTable = false)
    )

    /**
     * Loads server fields for [worldId] and maps active fields to occupied table tiles.
     *
     * Falls back to the local placeholder layout when the server fields cannot be loaded, so the
     * existing UI remains usable in offline/test scenarios.
     */
    private fun loadFieldTileLayout(worldId: String): List<TableTile> {
        val config = ConfigManager.loadNetworkConfig()
        if (
            useTestingConnectionForTesting ||
            !config.active ||
            worldId == "new-world" ||
            worldId.startsWith("saved-")
        ) {
            return defaultTileLayout()
        }

        return try {
            restClient.getFields(worldId).map { field ->
                TableTile(field.fieldId, occupiedByOtherTable = field.active)
            }.ifEmpty { defaultTileLayout() }
        } catch (e: Exception) {
            println("Could not load fields for world $worldId: ${e.message}")
            defaultTileLayout()
        }
    }
}

/**
 * Singleton instance of the ServerConnectionManager for global access.
 */
object ServerConnection {
    /** Identifier used for the tile representing the local table once selected. */
    const val OWN_TABLE_ID = "Tisch D"
    private val instance = ServerConnectionManager()
    
    /** The server address currently used */
    val serverAddress: String get() = instance.serverAddress
    
    /** The current connection state */
    val connectionState: ConnectionState get() = instance.connectionState
    
    /** 
     * Connects to the server with the specified field ID.
     *
     * @param fieldId The field ID to connect to.
     */
    fun connect(fieldId: String) = instance.connect(fieldId)

    /**
     * Opens a server session without registering a field WebSocket yet.
     */
    fun connect() = instance.connect()

    /**
     * Registers the selected field on the live WebSocket connection.
     *
     * @param fieldId real server field ID.
     */
    fun connectSelectedField(fieldId: String): CompletableFuture<FieldRegistrationResult> =
        instance.connectSelectedField(fieldId)

    /**
     * Deregisters the selected field while keeping the server session active.
     */
    fun disconnectSelectedField(): CompletableFuture<FieldRegistrationResult> =
        instance.disconnectSelectedField()
    
    /** 
     * Disconnects from the server.
     */
    fun disconnect() = instance.disconnect()
    
    /**
     * Cancels the connection attempt.
     */
    fun cancelConnection() = instance.cancelConnection()
    
    /**
     * Cancels the disconnection attempt.
     */
    fun cancelDisconnection() = instance.cancelDisconnection()

    /**
     * Signals that an established connection was lost, starting the automatic reconnect.
     */
    fun loseConnection() = instance.loseConnection()

    /**
     * Cancels the automatic reconnect and returns to a disconnected state.
     */
    fun cancelReconnect() = instance.cancelReconnect()

    /**
     * Sets the callback invoked when an established connection is lost.
     *
     * @param callback The callback to set.
     */
    fun setOnConnectionLost(callback: () -> Unit) = instance.setOnConnectionLost(callback)

    /**
     * Delay between automatic reconnect attempts. Overridable for tests.
     */
    var reconnectDelayMs: Long
        get() = instance.reconnectDelayMs
        set(value) { instance.reconnectDelayMs = value }
    
    /**
     * Returns the tile layout for the specified world ID.
     *
     * @param worldId The world ID to get the tile layout for.
     */
    fun tilesFor(worldId: String) = instance.tilesFor(worldId)

    /**
     * Returns cached server fields including transition metadata for the specified world.
     *
     * @param worldId The world ID to get field metadata for.
     */
    fun fieldsFor(worldId: String) = instance.fieldsFor(worldId)

    /**
     * Drops cached server field metadata without releasing local tile state.
     *
     * @param worldId The world ID whose field metadata should be reloaded on next use.
     */
    fun forgetFieldMetadata(worldId: String) = instance.forgetFieldMetadata(worldId)
    
    /**
     * Returns the tile ID claimed by the local table in the specified world.
     *
     * @param worldId The world ID to get the claimed tile ID for.
     */
    fun ownTileIdFor(worldId: String) = instance.ownTileIdFor(worldId)
    
    /**
     * Claims a tile in the specified world for the local table.
     *
     * @param worldId The world ID to claim a tile in.
     * @param tileId The tile ID to claim.
     */
    fun claimTile(worldId: String, tileId: String) = instance.claimTile(worldId, tileId)
    
    /**
     * Releases the tile claim in the specified world.
     *
     * @param worldId The world ID to release the tile claim in.
     */
    fun releaseTile(worldId: String) = instance.releaseTile(worldId)

    /**
     * Drops all locally tracked tile state for a world that no longer exists.
     *
     * @param worldId The ID of the removed world.
     */
    fun forgetWorldTiles(worldId: String) = instance.forgetWorldTiles(worldId)

    /**
     * Pre-registers an all-free tile layout for a local new-world flow.
     *
     * @param worldId stable identifier of the world to initialize.
     */
    fun registerAllFreeTiles(worldId: String, columns: Int = 3, rows: Int = 3) =
        instance.registerAllFreeTiles(worldId, columns, rows)

    /**
     * Registers server fields as selectable table tiles using their real server IDs.
     */
    fun registerFieldTiles(worldId: String, fields: List<FieldPropertiesDTO>) =
        instance.registerFieldTiles(worldId, fields)

    /**
     * Sends a vehicle departure message through the registered field WebSocket.
     *
     * @param transitionNodeId server transition used by the vehicle.
     * @param fromFieldId local field ID.
     * @param toFieldId neighboring field ID.
     * @param level transition elevation level.
     * @param vehicleProperties serialized vehicle payload.
     */
    fun sendVehicleDeparture(
        transitionNodeId: String,
        fromFieldId: String,
        toFieldId: String,
        level: Int,
        vehicleProperties: JsonObject
    ): CompletableFuture<*> =
        instance.sendVehicleDeparture(transitionNodeId, fromFieldId, toFieldId, level, vehicleProperties)

    /**
     * Sets the callback for server vehicle arrival/source messages.
     *
     * @param callback The callback to set.
     */
    fun setOnVehicleMessage(callback: (VehicleMessageData) -> Unit) = instance.setOnVehicleMessage(callback)

    /**
     * Clears the callback for server vehicle arrival/source messages.
     */
    fun clearOnVehicleMessage() = instance.clearOnVehicleMessage()

    /**
     * Sets the callback for connection state changes.
     * 
     * @param callback The callback to set.
     */
    fun setOnStateChange(callback: () -> Unit) = instance.setOnStateChange(callback)

    /**
     * Resets the connection manager to its initial state.
     */
    fun reset() = instance.reset()
    
    /**
     * Sets the connection state directly for testing purposes.
     * This bypasses the normal connection flow and should only be used in tests.
     *
     * @param state The connection state to set.
     */
    fun setConnectionStateForTesting(state: ConnectionState) = instance.setConnectionStateForTesting(state)

    /**
     * Forces local connection behavior in tests without editing the network configuration file.
     */
    var useTestingConnectionForTesting: Boolean
        get() = instance.useTestingConnectionForTesting
        set(value) { instance.useTestingConnectionForTesting = value }
    
    /**
     * Sets the connection delay for testing purposes.
     * This allows tests to control the connection timing.
     */
    var connectionDelayForTesting: Long
        get() = instance.connectionDelayForTesting
        set(value) { instance.connectionDelayForTesting = value }
}
