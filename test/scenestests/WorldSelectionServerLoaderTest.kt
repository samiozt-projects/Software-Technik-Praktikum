package scenestests

import com.sun.net.httpserver.HttpServer
import config.ConfigManager
import domain.ConnectionState
import network.TrafficSimRestClient
import network.ServerConnection
import persistence.WorldLevelSelection
import scenes.ServerWorldCreateResult
import scenes.ServerWorldDeleteResult
import scenes.ServerWorldsResult
import scenes.WorldSelectionServerLoader
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [WorldSelectionServerLoader]'s fetch and parsing logic against a local in-memory HTTP
 * server, so the world-selection menu's server integration is covered without any real network
 * access (see acceptance criterion "Abruf und Parsing der Welt-Daten sind per Test abgesichert").
 */
class WorldSelectionServerLoaderTest {
    private lateinit var server: HttpServer
    private var responseStatus = 200
    private var responseBody = "[]"

    /** `X-World-Delete-Token` header recorded from the last request, if any was sent. */
    private var recordedWorldDeleteToken: String? = null

    /** Raw request body recorded from the last `POST /worlds` request, if any was sent. */
    private var recordedCreateWorldBody: String? = null

    /** Starts a local server on a random free port that always answers `/worlds`. */
    @BeforeTest
    fun setup() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/worlds") { exchange ->
            recordedWorldDeleteToken = exchange.requestHeaders.getFirst("X-World-Delete-Token")
            if (exchange.requestMethod == "POST") {
                recordedCreateWorldBody = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            }
            val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(responseStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    /** Stops the local server and resets the process-wide connection mock. */
    @AfterTest
    fun cleanup() {
        server.stop(0)
        ServerConnection.reset()
    }

    /** Builds a network config pointing at the local test server. */
    private fun activeConfig(): ConfigManager.NetworkConfig {
        val port = server.address.port
        return ConfigManager.NetworkConfig(
            host = "localhost",
            port = port,
            active = true,
            baseUrl = "http://localhost:$port"
        )
    }

    /** Builds a network config pointing at the local test server with coupling disabled. */
    private fun inactiveConfig(): ConfigManager.NetworkConfig {
        val port = server.address.port
        return ConfigManager.NetworkConfig(
            host = "localhost",
            port = port,
            active = false,
            baseUrl = "http://localhost:$port"
        )
    }

    /** Calls [WorldSelectionServerLoader.loadWorlds] and asserts it fails with [expectedMessage]. */
    private fun assertLoadWorldsFails(config: ConfigManager.NetworkConfig, connected: Boolean, expectedMessage: String) {
        val result = WorldSelectionServerLoader.loadWorlds(config, connected = connected, client = TrafficSimRestClient(config))
        assertEquals(expectedMessage, assertIs<ServerWorldsResult.Failure>(result).message)
    }

    /**
     * Calls [WorldSelectionServerLoader.deleteWorld] for a fixed sample world ID and asserts it
     * fails with [expectedMessage]. The tests using this helper only exercise the guard checks and
     * error handling shared with [assertLoadWorldsFails]/[assertCreateWorldFails], none of which
     * depend on which world is being deleted, so the ID itself is not a meaningful parameter.
     */
    private fun assertDeleteWorldFails(config: ConfigManager.NetworkConfig, connected: Boolean, expectedMessage: String) {
        val result = WorldSelectionServerLoader.deleteWorld("world-a", config, connected = connected, client = TrafficSimRestClient(config))
        assertEquals(expectedMessage, assertIs<ServerWorldDeleteResult.Failure>(result).message)
    }

    /** Calls [WorldSelectionServerLoader.createWorld] and asserts it fails with [expectedMessage]. */
    private fun assertCreateWorldFails(config: ConfigManager.NetworkConfig, connected: Boolean, expectedMessage: String) {
        val result = WorldSelectionServerLoader.createWorld(config, connected = connected, client = TrafficSimRestClient(config))
        assertEquals(expectedMessage, assertIs<ServerWorldCreateResult.Failure>(result).message)
    }

    /**
     * Verifies that a successful response is displayed in reverse server order (the most recently
     * created world, last in the server's oldest-first response, is always row 1 and does not
     * require scrolling through hundreds of older worlds to find), while each world's "Welt N"
     * number still reflects its original oldest-first creation order and so stays stable.
     */
    @Test
    fun loadWorlds_mapsSuccessfulResponseToWorldEntriesNewestFirst() {
        responseBody = """
            [
              {"worldId": "world-a", "columns": 2, "rows": 2, "fieldHeight": 100, "fieldWidth": 100},
              {"worldId": "world-b", "columns": 3, "rows": 3, "fieldHeight": 100, "fieldWidth": 100, "name": "Test1", "templateLevelId": 3}
            ]
        """.trimIndent()
        val config = activeConfig()

        val result = WorldSelectionServerLoader.loadWorlds(config, connected = true, client = TrafficSimRestClient(config))

        val success = assertIs<ServerWorldsResult.Success>(result)
        assertEquals(2, success.worlds.size)
        assertEquals("world-b", success.worlds[0].id)
        assertEquals(2, success.worlds[0].number)
        assertEquals("Test1", success.worlds[0].name)
        assertEquals(WorldLevelSelection.PredefinedLevel(3), success.worlds[0].levelSelection)
        assertEquals("Server", success.worlds[0].source)
        assertEquals("world-a", success.worlds[1].id)
        assertEquals(1, success.worlds[1].number)
        assertEquals("world-a", success.worlds[1].name)
    }

    /** Verifies that an empty server world list produces an empty, non-failing result. */
    @Test
    fun loadWorlds_handlesEmptyWorldList() {
        responseBody = "[]"
        val config = activeConfig()

        val result = WorldSelectionServerLoader.loadWorlds(config, connected = true, client = TrafficSimRestClient(config))

        val success = assertIs<ServerWorldsResult.Success>(result)
        assertTrue(success.worlds.isEmpty())
    }

    /** Verifies that a non-success HTTP status becomes a failure with a user-facing message. */
    @Test
    fun loadWorlds_returnsFailureForErrorStatus() {
        responseStatus = 500
        responseBody = "\"internal error\""

        assertLoadWorldsFails(activeConfig(), connected = true, expectedMessage = WorldSelectionServerLoader.CONNECTION_ERROR_MESSAGE)
    }

    /** Verifies that a malformed JSON body becomes a failure instead of throwing. */
    @Test
    fun loadWorlds_returnsFailureForMalformedResponse() {
        responseStatus = 200
        responseBody = "{ this is not valid json"

        assertLoadWorldsFails(activeConfig(), connected = true, expectedMessage = WorldSelectionServerLoader.CONNECTION_ERROR_MESSAGE)
    }

    /** Verifies that an unreachable server is caught as a failure instead of throwing. */
    @Test
    fun loadWorlds_returnsFailureWhenServerUnreachable() {
        server.stop(0)

        assertLoadWorldsFails(activeConfig(), connected = true, expectedMessage = WorldSelectionServerLoader.CONNECTION_ERROR_MESSAGE)
    }

    /** Verifies that no request is sent and a distinct message is returned when coupling is disabled. */
    @Test
    fun loadWorlds_skipsRequestWhenNetworkInactive() {
        assertLoadWorldsFails(inactiveConfig(), connected = true, expectedMessage = WorldSelectionServerLoader.SERVER_DISABLED_MESSAGE)
    }

    /**
     * Verifies that an active-but-not-connected table gets a distinct message and no request is
     * sent, matching the expectation that the "Verbinden" button (not just the static config) must
     * put the table into a connected state before real worlds are fetched.
     */
    @Test
    fun loadWorlds_skipsRequestWhenNotConnected() {
        responseBody = """[{"worldId": "world-a", "columns": 2, "rows": 2, "fieldHeight": 100, "fieldWidth": 100}]"""

        assertLoadWorldsFails(activeConfig(), connected = false, expectedMessage = WorldSelectionServerLoader.NOT_CONNECTED_MESSAGE)
    }

    /** Verifies that a disabled config wins over a connected table (feature-off overrides connection state). */
    @Test
    fun loadWorlds_disabledConfigTakesPrecedenceOverConnectedState() {
        assertLoadWorldsFails(inactiveConfig(), connected = true, expectedMessage = WorldSelectionServerLoader.SERVER_DISABLED_MESSAGE)
    }

    /**
     * Verifies that the default [WorldSelectionServerLoader.loadWorlds] `connected` parameter
     * follows the live [ServerConnection] state, so pressing "Verbinden" in
     * [scenes.ServerMenuScene] is enough to enable real world fetching without every caller
     * having to pass the flag explicitly.
     */
    @Test
    fun loadWorlds_defaultConnectedFlagFollowsServerConnection() {
        ServerConnection.reset()
        responseBody = """[{"worldId": "world-a", "columns": 2, "rows": 2, "fieldHeight": 100, "fieldWidth": 100}]"""
        val config = activeConfig()

        val whileDisconnected = WorldSelectionServerLoader.loadWorlds(config, client = TrafficSimRestClient(config))
        assertEquals(
            WorldSelectionServerLoader.NOT_CONNECTED_MESSAGE,
            assertIs<ServerWorldsResult.Failure>(whileDisconnected).message
        )

        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        val whileConnected = WorldSelectionServerLoader.loadWorlds(config, client = TrafficSimRestClient(config))
        assertIs<ServerWorldsResult.Success>(whileConnected)
    }

    /** Verifies that a successful delete request is mapped to [ServerWorldDeleteResult.Success]. */
    @Test
    fun deleteWorld_returnsSuccessForSuccessfulResponse() {
        responseBody = "\"deleted world\""
        val config = activeConfig()

        val result = WorldSelectionServerLoader.deleteWorld(
            "world-a", config, connected = true, client = TrafficSimRestClient(config)
        )

        assertIs<ServerWorldDeleteResult.Success>(result)
    }

    /** Verifies that a non-success HTTP status becomes a failure with a user-facing message. */
    @Test
    fun deleteWorld_returnsFailureForErrorStatus() {
        responseStatus = 500
        responseBody = "\"internal error\""

        assertDeleteWorldFails(activeConfig(), connected = true, expectedMessage = WorldSelectionServerLoader.DELETE_ERROR_MESSAGE)
    }

    /** Verifies that an unreachable server is caught as a failure instead of throwing (no crash). */
    @Test
    fun deleteWorld_returnsFailureWhenServerUnreachable() {
        server.stop(0)

        assertDeleteWorldFails(activeConfig(), connected = true, expectedMessage = WorldSelectionServerLoader.DELETE_ERROR_MESSAGE)
    }

    /** Verifies that no request is sent and a distinct message is returned when coupling is disabled. */
    @Test
    fun deleteWorld_skipsRequestWhenNetworkInactive() {
        assertDeleteWorldFails(inactiveConfig(), connected = true, expectedMessage = WorldSelectionServerLoader.SERVER_DISABLED_MESSAGE)
    }

    /** Verifies that an active-but-not-connected table gets a distinct message and no request is sent. */
    @Test
    fun deleteWorld_skipsRequestWhenNotConnected() {
        assertDeleteWorldFails(activeConfig(), connected = false, expectedMessage = WorldSelectionServerLoader.NOT_CONNECTED_MESSAGE)
    }

    /**
     * Verifies that the configured team delete token is sent as `X-World-Delete-Token`, so only
     * worlds created with a matching token (see [createWorld_sendsConfiguredTokenAndFixedGridSize])
     * can actually be deleted through this call.
     */
    @Test
    fun deleteWorld_sendsConfiguredWorldDeleteTokenHeader() {
        responseBody = "\"deleted world\""
        val config = activeConfig().copy(worldDeleteToken = "custom-team-token")

        WorldSelectionServerLoader.deleteWorld("world-a", config, connected = true, client = TrafficSimRestClient(config))

        assertEquals("custom-team-token", recordedWorldDeleteToken)
    }

    /** Verifies that a successful create request is mapped to [ServerWorldCreateResult.Success]. */
    @Test
    fun createWorld_returnsSuccessForSuccessfulResponse() {
        responseStatus = 201
        responseBody = """{"worldId": "world-created"}"""
        val config = activeConfig()

        val result = WorldSelectionServerLoader.createWorld(config, connected = true, client = TrafficSimRestClient(config))

        val success = assertIs<ServerWorldCreateResult.Success>(result)
        assertEquals("world-created", success.worldId)
    }

    /**
     * Verifies that the request body carries the configured team delete token and the fixed
     * minimal grid size, since the local world editor does not yet expose column/row/size inputs.
     */
    @Test
    fun createWorld_sendsConfiguredTokenAndFixedGridSize() {
        responseStatus = 201
        responseBody = """{"worldId": "world-created"}"""
        val config = activeConfig().copy(worldDeleteToken = "custom-team-token")

        WorldSelectionServerLoader.createWorld(config, connected = true, client = TrafficSimRestClient(config))

        val body = requireNotNull(recordedCreateWorldBody)
        assertTrue(body.contains("\"columns\":2"))
        assertTrue(body.contains("\"rows\":2"))
        assertTrue(body.contains("\"deleteToken\":\"custom-team-token\""))
    }

    /** Verifies that a non-success HTTP status becomes a failure with a user-facing message. */
    @Test
    fun createWorld_returnsFailureForErrorStatus() {
        responseStatus = 400
        responseBody = "\"Columns must be between 2 and 10, got: 1\""

        assertCreateWorldFails(activeConfig(), connected = true, expectedMessage = WorldSelectionServerLoader.CREATE_ERROR_MESSAGE)
    }

    /** Verifies that an unreachable server is caught as a failure instead of throwing (no crash). */
    @Test
    fun createWorld_returnsFailureWhenServerUnreachable() {
        server.stop(0)

        assertCreateWorldFails(activeConfig(), connected = true, expectedMessage = WorldSelectionServerLoader.CREATE_ERROR_MESSAGE)
    }

    /** Verifies that no request is sent and a distinct message is returned when coupling is disabled. */
    @Test
    fun createWorld_skipsRequestWhenNetworkInactive() {
        assertCreateWorldFails(inactiveConfig(), connected = true, expectedMessage = WorldSelectionServerLoader.SERVER_DISABLED_MESSAGE)
        assertNull(recordedCreateWorldBody)
    }

    /** Verifies that an active-but-not-connected table gets a distinct message and no request is sent. */
    @Test
    fun createWorld_skipsRequestWhenNotConnected() {
        assertCreateWorldFails(activeConfig(), connected = false, expectedMessage = WorldSelectionServerLoader.NOT_CONNECTED_MESSAGE)
        assertNull(recordedCreateWorldBody)
    }
}
