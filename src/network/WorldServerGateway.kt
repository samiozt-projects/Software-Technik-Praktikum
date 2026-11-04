package network

import config.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Testable boundary for server-world operations used by the UI.
 */
interface WorldServerGateway {
    /** Whether real server access is enabled by configuration. */
    val active: Boolean

    /** Loads all worlds currently known by the server. */
    suspend fun getWorlds(): List<WorldPropertiesDTO>

    /** Creates a new world and returns the server response. */
    suspend fun createWorld(request: CreateWorldDTO): CreateWorldResponse
}

/**
 * Production gateway backed by [TrafficSimRestClient].
 */
class TrafficSimWorldServerGateway(
    private val config: ConfigManager.NetworkConfig = ConfigManager.loadNetworkConfig(),
    private val client: TrafficSimRestClient = TrafficSimRestClient(config)
) : WorldServerGateway {
    override val active: Boolean = config.active

    /** Loads server worlds only when network access is enabled. */
    override suspend fun getWorlds(): List<WorldPropertiesDTO> {
        if (!active) return emptyList()
        return withContext(Dispatchers.IO) { client.getWorlds() }
    }

    /** Sends a create-world request to the coupling server. */
    override suspend fun createWorld(request: CreateWorldDTO): CreateWorldResponse {
        if (!active) throw IOException("Network access is not active.")
        return withContext(Dispatchers.IO) { client.createWorld(request) }
    }
}
