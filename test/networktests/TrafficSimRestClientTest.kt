package networktests

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import config.ConfigManager
import korlibs.io.async.suspendTest
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import network.*

/** Tests the REST client against a local in-memory HTTP server. */
class TrafficSimRestClientTest {
    private lateinit var server: HttpServer
    private lateinit var client: TrafficSimRestClient
    private val routes = mutableMapOf<Pair<String, String>, (HttpExchange) -> Unit>()
    private val recordedRequests = mutableListOf<RecordedRequest>()

    /** Starts a local server on a random free port. */
    @BeforeTest
    fun setup() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            recordedRequests += RecordedRequest(
                method = exchange.requestMethod,
                path = exchange.requestURI.path,
                body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8),
                worldDeleteToken = exchange.requestHeaders.getFirst("X-World-Delete-Token")
            )
            routes[exchange.requestMethod to exchange.requestURI.path]
                ?.invoke(exchange)
                ?: exchange.respond(404, "\"not found\"")
        }
        server.start()

        val port = server.address.port
        client = TrafficSimRestClient(
            ConfigManager.NetworkConfig(
                host = "localhost",
                port = port,
                active = true,
                baseUrl = "http://localhost:$port"
            )
        )
    }

    /** Stops the local server. */
    @AfterTest
    fun cleanup() {
        server.stop(0)
        routes.clear()
        recordedRequests.clear()
    }

    /** Verifies that all documented REST endpoints use the expected paths and DTOs. */
    @Test
    fun restClientCallsServerEndpointsAndParsesDtos() {
        registerDocumentedEndpointResponses()

        assertDocumentedEndpointDtos()
        assertRecordedRequestBodies()
    }

    /** Registers representative responses for all REST endpoints used by the client. */
    private fun registerDocumentedEndpointResponses() {
        route("GET", "/worlds", 200, "[$WORLD_JSON]")
        route("GET", "/worlds/world-1", 200, WORLD_JSON)
        route("POST", "/worlds", 201, """{"worldId":"world-created"}""")
        route("DELETE", "/worlds/world-1", 200, """"deleted world"""")
        route("GET", "/worlds/world-1/editor-state", 200, WORLD_EDITOR_STATE_JSON)
        route("POST", "/worlds/editor-state", 201, WORLD_EDITOR_STATE_JSON)
        route("PUT", "/worlds/world-1/editor-state", 200, WORLD_EDITOR_STATE_JSON)
        route("GET", "/worlds/world-1/fields", 200, "[$FIELD_JSON]")
        route("GET", "/worlds/world-1/fields/field-1", 200, FIELD_JSON)
        route("GET", "/worlds/world-1/fields/field-1/sources", 200, "[$SOURCE_JSON]")
        route("POST", "/worlds/world-1/fields/field-1/sources", 201, SOURCE_JSON)
        route("GET", "/worlds/world-1/fields/field-1/sources/source-1", 200, SOURCE_JSON)
        route("DELETE", "/worlds/world-1/fields/field-1/sources/source-1", 200, """"deleted source"""")
        route("GET", "/worlds/world-1/fields/field-1/transitions", 200, "[$TRANSITION_JSON]")
        route("POST", "/worlds/world-1/fields/transitions", 201, TRANSITION_JSON)
        route("GET", "/worlds/world-1/fields/field-1/transitions/transition-1", 200, TRANSITION_JSON)
        route("DELETE", "/worlds/world-1/fields/field-1/transitions/transition-1", 200, """"deleted transition"""")
    }

    /** Verifies that each client method parses the DTO returned by its endpoint. */
    private fun assertDocumentedEndpointDtos() {
        assertEquals("world-1", client.getWorlds().single().worldId)
        assertEquals(2, client.getWorld("world-1").columns)
        assertEquals("world-created", client.createWorld(CreateWorldDTO(2, 2, 100, 100, "secret")).worldId)
        assertEquals("deleted world", client.deleteWorld("world-1", worldDeleteToken = "secret"))
        assertEquals("Editor Welt", client.getWorldEditorState("world-1").name)
        assertEquals(
            WorldEditorNodeKind.SOURCE,
            client.createEditorWorld(SAVE_EDITOR_WORLD_REQUEST).editorNodes.first().kind
        )
        assertEquals("world-1", client.updateEditorWorld("world-1", SAVE_EDITOR_WORLD_REQUEST).worldId)
        assertEquals("field-1", client.getFields("world-1").single().fieldId)
        assertEquals("world-1", client.getField("world-1", "field-1").parentWorldId)
        assertEquals("source-1", client.getSources("world-1", "field-1").single().nodeId)
        assertEquals(
            "source-1",
            client.createSource("world-1", "field-1", CreateQuelleDTO("field-1", CoordinateDTO(0, 50), 0.5)).nodeId
        )
        assertEquals("source-1", client.getSource("world-1", "field-1", "source-1").nodeId)
        assertEquals("deleted source", client.deleteSource("world-1", "field-1", "source-1"))
        assertEquals("transition-1", client.getTransitions("world-1", "field-1").single().nodeId)
        assertEquals(
            "transition-1",
            client.createTransition("world-1", CreateTransitionDTO("field-1", "field-2", CoordinateDTO(100, 50), 0)).nodeId
        )
        assertEquals("transition-1", client.getTransition("world-1", "field-1", "transition-1").nodeId)
        assertEquals("deleted transition", client.deleteTransition("world-1", "field-1", "transition-1"))
    }

    /** Verifies that write requests contain the required payload and headers. */
    private fun assertRecordedRequestBodies() {
        assertTrue(recordedRequests.any { it.method == "POST" && it.path == "/worlds" && it.body.contains("deleteToken") })
        assertTrue(recordedRequests.any { it.method == "POST" && it.path == "/worlds/editor-state" && it.body.contains("editorNodes") })
        assertTrue(recordedRequests.any { it.method == "PUT" && it.path == "/worlds/world-1/editor-state" && it.body.contains("templateLevelId") })
        assertTrue(recordedRequests.any { it.path.endsWith("/sources") && it.body.contains("spawnInterval") })
        assertTrue(recordedRequests.any { it.path.endsWith("/fields/transitions") && it.body.contains("fromFieldId") })
        assertEquals("secret", recordedRequests.first { it.method == "DELETE" && it.path == "/worlds/world-1" }.worldDeleteToken)
    }

    /** Verifies that server error statuses become typed exceptions. */
    @Test
    fun restClientThrowsTypedExceptionForErrorStatus() {
        route("GET", "/worlds/missing", 404, """"World nicht gefunden"""")

        val error = assertFailsWith<NetworkApiException> {
            client.getWorld("missing")
        }

        assertEquals(404, error.statusCode)
        assertTrue(error.responseBody.contains("World"))
    }

    /** Verifies that the world gateway forwards create requests through the REST client. */
    @Test
    fun worldServerGatewayCreatesWorldThroughRestClient() = suspendTest {
        route("POST", "/worlds", 201, """{"worldId":"gateway-world"}""")
        val gateway = TrafficSimWorldServerGateway(
            ConfigManager.NetworkConfig(
                host = "localhost",
                port = server.address.port,
                active = true,
                baseUrl = "http://localhost:${server.address.port}"
            ),
            client
        )

        val response = gateway.createWorld(CreateWorldDTO(3, 3, 100, 100, "gateway-token"))

        assertEquals("gateway-world", response.worldId)
        val request = recordedRequests.single { it.method == "POST" && it.path == "/worlds" }
        assertTrue(request.body.contains("gateway-token"))
        assertTrue(request.body.contains("fieldHeight"))
    }

    /**
     * Registers a route for an HTTP method and path.
     *
     * @param method The HTTP method.
     * @param path The path.
     * @param status The status code to return.
     * @param body The response body to send.
    */
    private fun route(method: String, path: String, status: Int, body: String) {
        routes[method to path] = { exchange -> exchange.respond(status, body) }
    }

    /**
     * Responds to an HTTP request with the given status code and body.
     *
     * @param status The HTTP status code to return.
     * @param body The response body to send.
    */
    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    /**
     * HTTP request recorded by the in-memory test server.
     *
     * @property method HTTP method used by the client.
     * @property path Request path without query parameters.
     * @property body Raw request body sent by the client.
     * @property worldDeleteToken Delete token header used for world deletion.
     */
    private data class RecordedRequest(
        val method: String,
        val path: String,
        val body: String,
        val worldDeleteToken: String?
    )

    companion object {
        private const val WORLD_JSON = """
            {
              "worldId": "world-1",
              "columns": 2,
              "rows": 2,
              "fieldHeight": 100,
              "fieldWidth": 100
            }
        """

        private const val SOURCE_JSON = """
            {
              "fieldId": "field-1",
              "nodeId": "source-1",
              "coordinate": { "x": 0, "y": 50 },
              "spawnInterval": 0.5
            }
        """

        private const val TRANSITION_JSON = """
            {
              "nodeId": "transition-1",
              "fromFieldId": "field-1",
              "toFieldId": "field-2",
              "coordinate": { "x": 100, "y": 50 },
              "biDirectionalWay": false,
              "level": 0
            }
        """

        private const val FIELD_JSON = """
            {
              "fieldId": "field-1",
              "active": true,
              "coordinate": { "x": 0, "y": 0 },
              "parentWorldId": "world-1",
              "fieldQuellen": [$SOURCE_JSON],
              "fieldTransitions": [$TRANSITION_JSON]
            }
        """

        private const val WORLD_EDITOR_STATE_JSON = """
            {
              "worldId": "world-1",
              "name": "Editor Welt",
              "templateLevelId": 2,
              "levelJson": "{\"name\":\"Level 2\"}",
              "updatedAt": "2026-07-03T10:00:00Z",
              "editorNodes": [
                {
                  "id": "source-1",
                  "kind": "SOURCE",
                  "x": 10.0,
                  "y": 20.0,
                  "colorNames": ["Rot", "Blau"],
                  "vehicleTypeNames": ["Auto", "Lastwagen"]
                },
                {
                  "id": "target-1",
                  "kind": "TARGET",
                  "x": 80.0,
                  "y": 20.0,
                  "colorNames": ["Rot"],
                  "vehicleTypeNames": ["Auto"]
                }
              ]
            }
        """

        private val SAVE_EDITOR_WORLD_REQUEST = SaveWorldEditorDTO(
            name = "Editor Welt",
            templateLevelId = 2,
            levelJson = """{"name":"Level 2"}""",
            editorNodes = listOf(
                WorldEditorNodeDTO(
                    id = "source-1",
                    kind = WorldEditorNodeKind.SOURCE,
                    x = 10.0,
                    y = 20.0,
                    colorNames = listOf("Rot", "Blau"),
                    vehicleTypeNames = listOf("Auto", "Lastwagen")
                ),
                WorldEditorNodeDTO(
                    id = "target-1",
                    kind = WorldEditorNodeKind.TARGET,
                    x = 80.0,
                    y = 20.0,
                    colorNames = listOf("Rot"),
                    vehicleTypeNames = listOf("Auto")
                )
            )
        )
    }
}
