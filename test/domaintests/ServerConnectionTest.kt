package domaintests

import domain.ConnectionState
import network.FieldRegistrationResult
import network.ServerConnection
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [ServerConnection] and its supporting [ConnectionState] / [TableTile] types in
 * isolation from rendering. [ServerConnection] is a process-wide singleton, so every test
 * resets it afterwards to avoid leaking state into unrelated tests, including the KorGE-driven
 * scene interaction tests.
 */
class ServerConnectionTest {

    private companion object {
        const val TEST_WORLD_ID = "world-test"
        const val OTHER_WORLD_ID = "world-other"
    }

    private val originalReconnectDelayMs = ServerConnection.reconnectDelayMs

    /**
     * Uses the local connection path for these unit tests.
     */
    @BeforeTest
    fun useLocalConnectionState() {
        ServerConnection.useTestingConnectionForTesting = true
    }

    /**
     * Resets the process-wide server connection state after each test and restores the reconnect
     * delay, which [ServerConnection.reset] intentionally leaves untouched.
     */
    @AfterTest
    fun resetMock() {
        ServerConnection.reset()
        ServerConnection.reconnectDelayMs = originalReconnectDelayMs
    }

    /**
     * Verifies that the connection starts disconnected with no claimed tile.
     */
    @Test
    fun initialState_isDisconnectedWithNoOwnTile() {
        assertEquals(ConnectionState.DISCONNECTED, ServerConnection.connectionState)
        assertNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Verifies that the server connection test hook marks the connection as connected.
     */
    @Test
    fun connect_setsStateToConnected() {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)

        assertEquals(ConnectionState.CONNECTED, ServerConnection.connectionState)
    }

    /**
     * Verifies that selecting a field does not drop the already established server session state.
     *
     * Runs on the local testing shortcut; the real WebSocket registration path (including a
     * server-side rejection) is covered by [networktests.ServerConnectionFieldRegistrationTest].
     */
    @Test
    fun connectSelectedField_whileConnected_keepsConnectionStateConnected() {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)

        val result = ServerConnection.connectSelectedField("server-field-1").get()

        assertTrue(result is FieldRegistrationResult.Success)
        assertEquals(ConnectionState.CONNECTED, ServerConnection.connectionState)
    }

    /** Verifies that field release keeps the REST-backed server session connected. */
    @Test
    fun disconnectSelectedField_whileConnected_keepsConnectionStateConnected() {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        ServerConnection.connectSelectedField("server-field-1").get()

        val result = ServerConnection.disconnectSelectedField().get()

        assertTrue(result is FieldRegistrationResult.Success)
        assertEquals(ConnectionState.CONNECTED, ServerConnection.connectionState)
    }

    /**
     * Verifies that [ServerConnection.disconnect] resets the connection state and releases
     * the local table's current tile claim, since disconnecting means the table is no longer
     * present in any world.
     */
    @Test
    fun disconnect_resetsConnectionStateAndReleasesOwnTile() {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        ServerConnection.claimTile(TEST_WORLD_ID, firstFreeTileId(TEST_WORLD_ID))

        ServerConnection.disconnect()
        
        // Wait for disconnection to complete
        var retries = 0
        while (retries < 100 && ServerConnection.connectionState != ConnectionState.DISCONNECTED) {
            Thread.sleep(50)
            retries++
        }

        assertEquals(ConnectionState.DISCONNECTED, ServerConnection.connectionState)
        assertNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Verifies that claiming a free tile succeeds and records its id.
     */
    @Test
    fun claimTile_succeedsForAFreeTile() {
        val freeTileId = firstFreeTileId(TEST_WORLD_ID)

        val claimed = ServerConnection.claimTile(TEST_WORLD_ID, freeTileId)

        assertTrue(claimed)
        assertEquals(freeTileId, ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Verifies that claiming a tile already occupied by another table fails.
     */
    @Test
    fun claimTile_failsForAnOccupiedTile() {
        val occupiedTileId = ServerConnection.tilesFor(TEST_WORLD_ID)
            .first { it.occupiedByOtherTable }.id

        val claimed = ServerConnection.claimTile(TEST_WORLD_ID, occupiedTileId)

        assertFalse(claimed)
        assertNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Verifies that claiming an unknown tile id fails without changing the own tile.
     */
    @Test
    fun claimTile_failsForAnUnknownTileId() {
        val claimed = ServerConnection.claimTile(TEST_WORLD_ID, "does-not-exist")

        assertFalse(claimed)
        assertNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Verifies that claiming a second tile replaces the previously claimed one.
     */
    @Test
    fun claimTile_replacesAPreviouslyClaimedTile() {
        val freeTiles = ServerConnection.tilesFor(TEST_WORLD_ID).filter { !it.occupiedByOtherTable }
        ServerConnection.claimTile(TEST_WORLD_ID, freeTiles[0].id)

        ServerConnection.claimTile(TEST_WORLD_ID, freeTiles[1].id)

        assertEquals(freeTiles[1].id, ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Verifies that releasing a claimed tile clears the own tile id.
     */
    @Test
    fun releaseTile_clearsOwnTileId() {
        ServerConnection.claimTile(TEST_WORLD_ID, firstFreeTileId(TEST_WORLD_ID))

        ServerConnection.releaseTile(TEST_WORLD_ID)

        assertNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Verifies that releasing a tile when none is claimed is a harmless no-op.
     */
    @Test
    fun releaseTile_isANoOpWhenNoTileIsClaimed() {
        ServerConnection.releaseTile(TEST_WORLD_ID)

        assertNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Verifies that [ServerConnection.reset] restores the initial disconnected, unclaimed state.
     */
    @Test
    fun reset_restoresInitialState() {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        ServerConnection.claimTile(TEST_WORLD_ID, firstFreeTileId(TEST_WORLD_ID))

        ServerConnection.reset()

        assertEquals(ConnectionState.DISCONNECTED, ServerConnection.connectionState)
        assertNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Verifies that the fixed mock tile layout has at least one free and one occupied tile,
     * since the UI's selection and occupied-coloring behavior both depend on this.
     */
    @Test
    fun tiles_containAtLeastOneFreeAndOneOccupiedTile() {
        val tiles = ServerConnection.tilesFor(TEST_WORLD_ID)
        assertTrue(tiles.any { it.occupiedByOtherTable })
        assertTrue(tiles.any { !it.occupiedByOtherTable })
    }

    /**
     * Verifies that all mock tile ids are unique, since the UI looks tiles up by id.
     */
    @Test
    fun tiles_haveUniqueIds() {
        val ids = ServerConnection.tilesFor(TEST_WORLD_ID).map { it.id }

        assertEquals(ids.toSet().size, ids.size)
    }

    /**
     * Verifies that the local table can only ever hold one tile claim across all worlds: claiming
     * a tile in a new world automatically releases the claim in the previously claimed world.
     */
    @Test
    fun claimTile_releasesClaimInPreviousWorld_whenClaimingInANewWorld() {
        val worldOneTile = firstFreeTileId(TEST_WORLD_ID)
        val worldTwoTile = firstFreeTileId(OTHER_WORLD_ID)

        ServerConnection.claimTile(TEST_WORLD_ID, worldOneTile)
        ServerConnection.claimTile(OTHER_WORLD_ID, worldTwoTile)

        assertNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))
        assertEquals(worldTwoTile, ServerConnection.ownTileIdFor(OTHER_WORLD_ID))
    }

    /**
     * Verifies that claiming a new tile within the same world the local table already has a
     * claim in does not release that claim (only switching to a *different* world should).
     */
    @Test
    fun claimTile_doesNotReleaseItself_whenReplacingWithinTheSameWorld() {
        val freeTiles = ServerConnection.tilesFor(TEST_WORLD_ID).filter { !it.occupiedByOtherTable }
        ServerConnection.claimTile(TEST_WORLD_ID, freeTiles[0].id)

        ServerConnection.claimTile(TEST_WORLD_ID, freeTiles[1].id)

        assertEquals(freeTiles[1].id, ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Verifies that after switching the claim to a new world, claiming a tile in a third world
     * also releases that second world's claim, since the table can only ever hold one claim.
     */
    @Test
    fun claimTile_releasesClaim_acrossMoreThanTwoWorlds() {
        val thirdWorldId = "world-third"
        ServerConnection.claimTile(TEST_WORLD_ID, firstFreeTileId(TEST_WORLD_ID))
        ServerConnection.claimTile(OTHER_WORLD_ID, firstFreeTileId(OTHER_WORLD_ID))

        val thirdWorldTile = firstFreeTileId(thirdWorldId)
        ServerConnection.claimTile(thirdWorldId, thirdWorldTile)

        assertNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))
        assertNull(ServerConnection.ownTileIdFor(OTHER_WORLD_ID))
        assertEquals(thirdWorldTile, ServerConnection.ownTileIdFor(thirdWorldId))
    }

    /**
     * Verifies that releasing the tile in the world the table currently has its claim in allows
     * a subsequent claim in a different world to succeed without being seen as a stale leftover.
     */
    @Test
    fun releaseTile_thenClaimInDifferentWorld_succeedsCleanly() {
        ServerConnection.claimTile(TEST_WORLD_ID, firstFreeTileId(TEST_WORLD_ID))
        ServerConnection.releaseTile(TEST_WORLD_ID)

        val otherWorldTile = firstFreeTileId(OTHER_WORLD_ID)
        val claimed = ServerConnection.claimTile(OTHER_WORLD_ID, otherWorldTile)

        assertTrue(claimed)
        assertNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))
        assertEquals(otherWorldTile, ServerConnection.ownTileIdFor(OTHER_WORLD_ID))
    }

    /**
     * Verifies that losing an established connection switches to the reconnecting state.
     */
    @Test
    fun loseConnection_whileConnected_switchesToReconnecting() {
        ServerConnection.reconnectDelayMs = 5_000
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)

        ServerConnection.loseConnection()

        assertEquals(ConnectionState.RECONNECTING, ServerConnection.connectionState)
        ServerConnection.cancelReconnect()
    }

    /**
     * Verifies that losing a connection keeps the current tile claim, since a successful reconnect
     * should resume play on the same tile rather than force a fresh tile selection.
     */
    @Test
    fun loseConnection_keepsOwnTileClaim() {
        ServerConnection.reconnectDelayMs = 5_000
        val claimedTile = firstFreeTileId(TEST_WORLD_ID)
        ServerConnection.claimTile(TEST_WORLD_ID, claimedTile)
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)

        ServerConnection.loseConnection()

        assertEquals(claimedTile, ServerConnection.ownTileIdFor(TEST_WORLD_ID))
        ServerConnection.cancelReconnect()
    }

    /**
     * Verifies that losing a connection while not connected is a harmless no-op, since there is no
     * established connection to drop.
     */
    @Test
    fun loseConnection_whileDisconnected_isANoOp() {
        ServerConnection.loseConnection()

        assertEquals(ConnectionState.DISCONNECTED, ServerConnection.connectionState)
    }

    /**
     * Verifies that losing a connection notifies the registered connection-lost listener so the UI
     * can bring up the reconnect overlay.
     */
    @Test
    fun loseConnection_notifiesOnConnectionLostListener() {
        ServerConnection.reconnectDelayMs = 5_000
        var notified = false
        ServerConnection.setOnConnectionLost { notified = true }
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)

        ServerConnection.loseConnection()

        assertTrue(notified)
        ServerConnection.cancelReconnect()
    }

    /**
     * Verifies that the automatic reconnect restores a connected state on its own (local mode).
     */
    @Test
    fun loseConnection_automaticReconnect_reachesConnected() {
        ServerConnection.reconnectDelayMs = 0
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)

        ServerConnection.loseConnection()

        awaitConnectionState(ConnectionState.CONNECTED)
    }

    /**
     * Verifies that cancelling the reconnect stops it and returns to a disconnected state.
     */
    @Test
    fun cancelReconnect_returnsToDisconnected() {
        ServerConnection.reconnectDelayMs = 5_000
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        ServerConnection.loseConnection()
        assertEquals(ConnectionState.RECONNECTING, ServerConnection.connectionState)

        ServerConnection.cancelReconnect()

        assertEquals(ConnectionState.DISCONNECTED, ServerConnection.connectionState)
    }

    /**
     * Verifies that [ServerConnection.reset] clears the connection-lost listener so a listener
     * registered by one test cannot leak into the next.
     */
    @Test
    fun reset_clearsOnConnectionLostListener() {
        var notified = false
        ServerConnection.setOnConnectionLost { notified = true }

        ServerConnection.reset()
        ServerConnection.useTestingConnectionForTesting = true
        ServerConnection.reconnectDelayMs = 5_000
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        ServerConnection.loseConnection()

        assertFalse(notified)
        ServerConnection.cancelReconnect()
    }

    /** Waits (blocking) until the connection reaches [expected] or a timeout elapses. */
    private fun awaitConnectionState(expected: ConnectionState, maxRetries: Int = 100) {
        var retries = 0
        while (retries < maxRetries && ServerConnection.connectionState != expected) {
            Thread.sleep(50)
            retries++
        }
        assertEquals(expected, ServerConnection.connectionState)
    }

    /** Returns the id of the first mock tile not occupied by another table in [worldId]. */
    private fun firstFreeTileId(worldId: String): String =
        ServerConnection.tilesFor(worldId).first { !it.occupiedByOtherTable }.id
}
