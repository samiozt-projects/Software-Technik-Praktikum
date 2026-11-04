package networktests

import config.ConfigManager
import network.TrafficSimRestClient
import scenes.ServerWorldCreateResult
import scenes.ServerWorldDeleteResult
import scenes.WorldSelectionServerLoader
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Verifies that [TrafficSimRestClient]'s default (production) HTTP client can reach the real
 * coupling server over TLS, without [RealServerTestHttpClient]'s test-only pinned certificate.
 *
 * Regression test for the coupling server's HARICA-issued certificate chain, whose root is not
 * part of the default JDK trust store on most machines (see [network.TrustedCertificates]):
 * without a fix, this fails with `SSLHandshakeException: PKIX path building failed` even though
 * [RealServerTestHttpClient]-backed tests pass, because the production client used by the running
 * game (e.g. the world selection menu) does not go through the test-only pinned certificate.
 */
class ProductionClientRealServerTest {

    /**
     * Verifies that fetching worlds through the production client (default constructor, so it
     * uses [network.TrustedCertificates] like the running game does) succeeds over TLS.
     */
    @Test
    fun defaultRestClient_reachesRealServerOverTls() {
        if (!RealServerVehicleWebSocketTest.realServerAvailable) {
            println("Skipping - real server not available")
            return
        }
        val worlds = TrafficSimRestClient().getWorlds()
        println("Fetched ${worlds.size} worlds from the real coupling server via the production client.")
    }

    /**
     * End-to-end regression test for the team delete-token flow: creates a world on the real
     * server through [WorldSelectionServerLoader.createWorld] (tagged with
     * [ConfigManager.NetworkConfig.worldDeleteToken]), then deletes that same world through
     * [WorldSelectionServerLoader.deleteWorld]. Confirms the token configured in
     * `config/app-config.properties` actually lets this team create and later remove its own
     * worlds, not just that the connection succeeds.
     */
    @Test
    fun createThenDeleteWorld_roundTripsAgainstRealServerWithConfiguredToken() {
        if (!RealServerVehicleWebSocketTest.realServerAvailable) {
            println("Skipping - real server not available")
            return
        }

        var worldId: String? = null
        try {
            val createResult = WorldSelectionServerLoader.createWorld(connected = true)
            worldId = assertIs<ServerWorldCreateResult.Success>(createResult).worldId
            println("Created world $worldId on the real coupling server with the configured team token.")
        } finally {
            worldId?.let { createdWorldId ->
                val deleteResult = WorldSelectionServerLoader.deleteWorld(createdWorldId, connected = true)
                assertIs<ServerWorldDeleteResult.Success>(deleteResult)
                println("Deleted world $createdWorldId on the real coupling server with the configured team token.")
            }
        }
    }
}
