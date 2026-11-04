package networktests

import config.ConfigManager
import network.TrafficSimWebSocketClient
import network.VehicleMessageData
import network.WebSocketMessage

/**
 * Utility functions for creating test clients.
 */
object ClientTestUtilities {

    /**
     * Creates a test client for WebSocket communication.
     * @param onVehicleMessage Callback for handling vehicle messages
     * @param onMessage Callback for handling general messages
     * @return The created test client
     */
    fun createTestClient(
        onVehicleMessage: (VehicleMessageData) -> Unit = {},
        onMessage: (WebSocketMessage) -> Unit = {}
    ): TrafficSimWebSocketClient {
        val config = try {
            val loadedConfig = ConfigManager.loadNetworkConfig()
            println("Using network config: host=${loadedConfig.host}, baseUrl=${loadedConfig.baseUrl}")
            loadedConfig
        } catch (e: Exception) {
            println("Failed to load network config, using default: ${e.message}")
            ConfigManager.NetworkConfig(
                host = "localhost",
                port = 8080,
                active = true,
                baseUrl = "http://localhost:8080"
            )
        }
        
        return TrafficSimWebSocketClient(
            config = config,
            httpClient = RealServerTestHttpClient.create(),
            onVehicleMessage = onVehicleMessage,
            onMessage = onMessage
        )
    }
}
