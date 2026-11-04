package networktests

import network.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Utility functions for WebSocket-related network tests.
 */
object WebSocketTestUtilities {

    /**
     * Timeout for WebSocket connection
     */
    const val CONNECTION_TIMEOUT_SECONDS = 10L

    /**
     * Timeout for WebSocket operations
     */
    const val OPERATION_TIMEOUT_SECONDS = 15L

    /**
     * Connects to the server with a timeout.
     * @param client The WebSocket client to use
     * @param fieldId The field ID to connect to
     * @return True if connection was successful, false otherwise
     */
    suspend fun connectWithTimeout(client: TrafficSimWebSocketClient, fieldId: String): Boolean {
        return try {
            withTimeout(CONNECTION_TIMEOUT_SECONDS.seconds) {
                client.connect(fieldId).get()
                
                var waitCount = 0
                while (client.connectionState != NetworkConnectionState.CONNECTED && 
                       client.connectionState != NetworkConnectionState.ERROR && 
                       waitCount < 20) {
                    delay(100)
                    waitCount++
                }
                
                client.connectionState == NetworkConnectionState.CONNECTED
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Disconnects from the server safely.
     * @param client The WebSocket client to use
     */
    suspend fun disconnectSafely(client: TrafficSimWebSocketClient) {
        try {
            if (client.connectionState != NetworkConnectionState.DISCONNECTED) {
                client.disconnect()
                delay(200)
            }
        } catch (_: Exception) {
            // Ignore disconnection errors
        }
    }

    /**
     * Sends a test vehicle to the server.
     * @param client The WebSocket client to use
     * @param vehicle The vehicle to send
     * @param transitionId The ID of the transition node
     * @param fromField The ID of the field the vehicle is leaving from
     * @param toField The ID of the field the vehicle is going to
     * @param level The elevation level of the transition
     * @return True if the vehicle was sent successfully, false otherwise
     */
    suspend fun sendTestVehicle(
        client: TrafficSimWebSocketClient,
        vehicle: domain.Vehicle,
        transitionId: String,
        fromField: String,
        toField: String,
        level: Int = 1
    ): Boolean {
        return try {
            withTimeout(OPERATION_TIMEOUT_SECONDS.seconds) {
                client.sendVehicleDeparture(transitionId, fromField, toField, level, vehicle).get()
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}