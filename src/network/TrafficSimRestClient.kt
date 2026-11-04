package network

import config.ConfigManager
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Raised when the coupling server returns a non-success HTTP status.
 *
 * @property statusCode HTTP status returned by the server.
 * @property responseBody Raw response body returned by the server.
 */
class NetworkApiException(
    /** HTTP status returned by the server. */
    val statusCode: Int,
    /** Raw response body returned by the server. */
    val responseBody: String,
    /** URI that produced the error response. */
    requestUri: URI
) : IOException("Server request failed with HTTP $statusCode for $requestUri: $responseBody")

/**
 * Raised when a response body cannot be parsed as the expected DTO.
 */
class NetworkSerializationException(
    /** Description of the failed deserialization. */
    message: String,
    /** Parser exception that caused this error, if available. */
    cause: Throwable? = null
) : IOException(message, cause)

/** REST client for worlds, fields, sources and transitions on the coupling server. */
class TrafficSimRestClient(
    private val config: ConfigManager.NetworkConfig = ConfigManager.loadNetworkConfig(),
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(DEFAULT_TIMEOUT)
        .sslContext(TrustedCertificates.trustedSslContext())
        .build(),
    private val requestTimeout: Duration = DEFAULT_TIMEOUT
) {
    /** Loads all worlds known to the server. */
    fun getWorlds(): List<WorldPropertiesDTO> {
        return get("/worlds")
    }

    /** Loads one world by ID. */
    fun getWorld(worldId: String): WorldPropertiesDTO {
        return get("/worlds/${pathSegment(worldId)}")
    }

    /** Creates a new world and returns the generated world ID. */
    fun createWorld(request: CreateWorldDTO): CreateWorldResponse {
        return post("/worlds", request)
    }

    /** Deletes a world using either the world delete token or an admin token. */
    fun deleteWorld(
        worldId: String,
        worldDeleteToken: String? = null,
        adminDeleteToken: String? = null
    ): String {
        val builder = requestBuilder("/worlds/${pathSegment(worldId)}")
            .DELETE()
        worldDeleteToken?.let { builder.header("X-World-Delete-Token", it) }
        adminDeleteToken?.let { builder.header("X-Admin-Delete-Token", it) }
        return sendServerMessage(builder.build())
    }

    /** Loads the complete editor state for an existing world. */
    fun getWorldEditorState(worldId: String): WorldEditorStateDTO {
        return get("/worlds/${pathSegment(worldId)}/editor-state")
    }

    /** Creates a new server-backed world from an editor state. */
    fun createEditorWorld(request: SaveWorldEditorDTO): WorldEditorStateDTO {
        return post("/worlds/editor-state", request)
    }

    /** Updates the editor state of an existing world without creating a duplicate. */
    fun updateEditorWorld(worldId: String, request: SaveWorldEditorDTO): WorldEditorStateDTO {
        return put("/worlds/${pathSegment(worldId)}/editor-state", request)
    }

    /** Loads all fields belonging to a world. */
    fun getFields(worldId: String): List<FieldPropertiesDTO> {
        return get("/worlds/${pathSegment(worldId)}/fields")
    }

    /** Loads one field by ID. */
    fun getField(worldId: String, fieldId: String): FieldPropertiesDTO {
        return get("/worlds/${pathSegment(worldId)}/fields/${pathSegment(fieldId)}")
    }

    /** Loads all server-side sources on a field. */
    fun getSources(worldId: String, fieldId: String): List<QuelleNode> {
        return get("/worlds/${pathSegment(worldId)}/fields/${pathSegment(fieldId)}/sources")
    }

    /** Creates a server-side source on an outer field edge. */
    fun createSource(worldId: String, fieldId: String, request: CreateQuelleDTO): QuelleNode {
        return post("/worlds/${pathSegment(worldId)}/fields/${pathSegment(fieldId)}/sources", request)
    }

    /** Loads one server-side source by ID. */
    fun getSource(worldId: String, fieldId: String, nodeId: String): QuelleNode {
        return get("/worlds/${pathSegment(worldId)}/fields/${pathSegment(fieldId)}/sources/${pathSegment(nodeId)}")
    }

    /** Deletes one server-side source by ID. */
    fun deleteSource(worldId: String, fieldId: String, nodeId: String): String {
        return delete("/worlds/${pathSegment(worldId)}/fields/${pathSegment(fieldId)}/sources/${pathSegment(nodeId)}")
    }

    /** Loads all transitions connected to a field. */
    fun getTransitions(worldId: String, fieldId: String): List<TransitionNode> {
        return get("/worlds/${pathSegment(worldId)}/fields/${pathSegment(fieldId)}/transitions")
    }

    /** Creates a transition between two neighboring fields. */
    fun createTransition(worldId: String, request: CreateTransitionDTO): TransitionNode {
        return post("/worlds/${pathSegment(worldId)}/fields/transitions", request)
    }

    /** Loads one transition by ID. */
    fun getTransition(worldId: String, fieldId: String, nodeId: String): TransitionNode {
        return get("/worlds/${pathSegment(worldId)}/fields/${pathSegment(fieldId)}/transitions/${pathSegment(nodeId)}")
    }

    /** Deletes one transition by ID. */
    fun deleteTransition(worldId: String, fieldId: String, nodeId: String): String {
        return delete("/worlds/${pathSegment(worldId)}/fields/${pathSegment(fieldId)}/transitions/${pathSegment(nodeId)}")
    }

    /**
     * Sends a GET request and decodes the JSON response.
     *
     * @param path API path relative to the configured base URL.
     * @return Decoded response DTO.
     */
    private inline fun <reified T> get(path: String): T {
        val request = requestBuilder(path).GET().build()
        return sendJson(request)
    }

    /**
     * Sends a POST request with a JSON body and decodes the response.
     *
     * @param path API path relative to the configured base URL.
     * @param body Request DTO serialized as JSON.
     * @return Decoded response DTO.
     */
    private inline fun <reified Request : Any, reified Response> post(path: String, body: Request): Response {
        val requestBody = NetworkJson.encodeToString(body)
        val request = requestBuilder(path)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build()
        return sendJson(request)
    }

    /**
     * Sends a PUT request with a JSON body and decodes the response.
     *
     * @param path API path relative to the configured base URL.
     * @param body Request DTO serialized as JSON.
     * @return Decoded response DTO.
     */
    private inline fun <reified Request : Any, reified Response> put(path: String, body: Request): Response {
        val requestBody = NetworkJson.encodeToString(body)
        val request = requestBuilder(path)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build()
        return sendJson(request)
    }

    private fun delete(path: String): String {
        return sendServerMessage(requestBuilder(path).DELETE().build())
    }

    /**
     * Sends an HTTP request and parses the response body as JSON.
     *
     * @param request Fully built HTTP request.
     * @return Decoded response DTO.
     */
    private inline fun <reified T> sendJson(request: HttpRequest): T {
        val body = sendText(request)
        return try {
            NetworkJson.decodeFromString(body)
        } catch (e: SerializationException) {
            throw NetworkSerializationException(
                "Could not parse response from ${request.uri()} as ${T::class.simpleName}: ${e.message}",
                e
            )
        }
    }

    private fun sendText(request: HttpRequest): String {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        val body = response.body().orEmpty()
        if (response.statusCode() !in 200..299) {
            throw NetworkApiException(response.statusCode(), body, request.uri())
        }
        return body
    }

    private fun sendServerMessage(request: HttpRequest): String {
        val body = sendText(request)
        return try {
            NetworkJson.decodeFromString<String>(body)
        } catch (_: SerializationException) {
            body
        }
    }

    private fun requestBuilder(path: String): HttpRequest.Builder {
        return HttpRequest.newBuilder(uri(path))
            .timeout(requestTimeout)
            .header("Accept", "application/json")
    }

    private fun uri(path: String): URI {
        return URI.create(config.baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
    }

    private fun pathSegment(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
            .replace("+", "%20")
    }

    companion object {
        private val DEFAULT_TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
