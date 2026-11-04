package networktests

import network.*

/**
 * Utility functions for REST-related network tests.
 */
object RestTestUtilities {

    /**
     * Delete token for testing
     */
    const val DELETE_TOKEN = "test-delete-token"

    /**
     * Number of columns in test world
     */
    const val WORLD_COLUMNS = 2

    /**
     * Number of rows in test world
     */
    const val WORLD_ROWS = 2

    /**
     * Width of fields in test world
     */
    const val FIELD_WIDTH = 100

    /**
     * Height of fields in test world
     */
    const val FIELD_HEIGHT = 100

    /**
     * Server world created for one real-server test.
     *
     * @property worldId ID of the temporary test world.
     * @property firstFieldId source field used by WebSocket tests.
     * @property secondFieldId target field used by WebSocket tests.
     * @property transitionId transition between [firstFieldId] and [secondFieldId].
     */
    data class TestWorldSetup(
        val worldId: String,
        val firstFieldId: String,
        val secondFieldId: String,
        val transitionId: String
    )

    /**
     * Creates a REST client for interacting with the TrafficSim server.
     * @return The created REST client
     */
    fun createRestClient(): TrafficSimRestClient {
        return TrafficSimRestClient(httpClient = RealServerTestHttpClient.create())
    }

    /**
     * Sets up a test world with two fields and a transition between them.
     *
     * The world is created with [DELETE_TOKEN]. Call [deleteTestWorld] in a `finally` block after
     * using the returned setup so real-server test data does not remain on the coupling server.
     * If setup fails after the world was created, this method deletes that world before rethrowing.
     *
     * @param restClient The REST client to use
     * @return created world metadata, field IDs, and transition ID
     */
    fun setupTestWorldAndGetFieldIds(restClient: TrafficSimRestClient): TestWorldSetup {
        val worldId = restClient.createWorld(
            CreateWorldDTO(WORLD_COLUMNS, WORLD_ROWS, FIELD_HEIGHT, FIELD_WIDTH, DELETE_TOKEN)
        ).worldId
        println("Created test world: $worldId")

        try {
            val fields = restClient.getFields(worldId)
            require(fields.size >= 2) { "World should have at least 2 fields for testing" }

            return buildTestWorldSetup(restClient, worldId, fields[0].fieldId, fields[1].fieldId)
        } catch (error: Throwable) {
            deleteTestWorld(restClient, worldId)
            throw error
        }
    }

    /**
     * Deletes one temporary test world using the same token that was used when creating it.
     *
     * @param restClient The REST client to use.
     * @param worldId ID of the temporary test world to delete.
     */
    fun deleteTestWorld(restClient: TrafficSimRestClient, worldId: String) {
        restClient.deleteWorld(worldId, worldDeleteToken = DELETE_TOKEN)
        println("Deleted test world: $worldId")
    }

    private fun buildTestWorldSetup(
        restClient: TrafficSimRestClient,
        worldId: String,
        firstFieldId: String,
        secondFieldId: String
    ): TestWorldSetup {
        val transition = restClient.createTransition(
            worldId,
            CreateTransitionDTO(
                fromFieldId = firstFieldId,
                toFieldId = secondFieldId,
                coordinate = CoordinateDTO(FIELD_WIDTH, FIELD_HEIGHT / 2),
                level = 1,
                biDirectionalWay = false
            )
        )
        println("Created transition: ${transition.nodeId} between $firstFieldId and $secondFieldId")
        return TestWorldSetup(worldId, firstFieldId, secondFieldId, transition.nodeId)
    }

    /**
     * Tests server connectivity.
     * @param restClient The REST client to use
     * @return True if server is reachable, false otherwise
     */
    fun testServerConnectivity(restClient: TrafficSimRestClient): Boolean {
        return try {
            println("Testing server connectivity...")
            val worlds = restClient.getWorlds()
            println("Server is reachable. Found ${worlds.size} existing worlds.")
            true
        } catch (e: Exception) {
            println("Server connectivity test failed: ${e.message}")
            if (e is NetworkApiException) {
                println("HTTP Status: ${e.statusCode}, Response: ${e.responseBody}")
            }
            false
        }
    }
}
