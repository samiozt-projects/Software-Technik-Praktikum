package domaintests

import network.CoordinateDTO
import network.FieldPropertiesDTO
import network.ServerConnection
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers tile-layout registration helpers in [ServerConnection]. */
class ServerConnectionTileLayoutTest {

    private companion object {
        const val TEST_WORLD_ID = "world-test"
        const val OTHER_WORLD_ID = "world-other"
        const val NEW_WORLD_ID = "world-new"
    }

    /** Uses the local connection path for these unit tests. */
    @BeforeTest
    fun useLocalConnectionState() {
        ServerConnection.useTestingConnectionForTesting = true
    }

    /** Resets the process-wide server connection state after each test. */
    @AfterTest
    fun resetMock() {
        ServerConnection.reset()
    }

    /** Verifies that all-free registration keeps the default tile-grid size. */
    @Test
    fun registerAllFreeTiles_matchesTileCountOfDefaultLayout() {
        val defaultCount = ServerConnection.tilesFor(TEST_WORLD_ID).size

        ServerConnection.registerAllFreeTiles(NEW_WORLD_ID)

        assertEquals(defaultCount, ServerConnection.tilesFor(NEW_WORLD_ID).size)
    }

    /** Verifies that all-free layouts respect the world's grid size. */
    @Test
    fun registerAllFreeTiles_usesWorldDimensions() {
        ServerConnection.registerAllFreeTiles(NEW_WORLD_ID, columns = 2, rows = 2)

        assertEquals(4, ServerConnection.tilesFor(NEW_WORLD_ID).size)
        assertEquals(
            listOf("slot-1", "slot-2", "slot-3", "slot-4"),
            ServerConnection.tilesFor(NEW_WORLD_ID).map { it.id }
        )
    }

    /** Verifies that server-provided fields keep their real IDs in the tile selection layout. */
    @Test
    fun registerFieldTiles_usesServerFieldIds() {
        ServerConnection.registerFieldTiles(
            NEW_WORLD_ID,
            listOf(
                FieldPropertiesDTO("server-field-1", false, CoordinateDTO(0, 0), NEW_WORLD_ID),
                FieldPropertiesDTO("server-field-2", true, CoordinateDTO(100, 0), NEW_WORLD_ID)
            )
        )

        val tiles = ServerConnection.tilesFor(NEW_WORLD_ID)

        assertEquals(listOf("server-field-1", "server-field-2"), tiles.map { it.id })
        assertFalse(tiles[0].occupiedByOtherTable)
        assertTrue(tiles[1].occupiedByOtherTable)
    }

    /** Verifies that registered all-free tiles are not marked occupied. */
    @Test
    fun registerAllFreeTiles_noTileIsOccupiedByOtherTable() {
        ServerConnection.registerAllFreeTiles(NEW_WORLD_ID)

        assertFalse(ServerConnection.tilesFor(NEW_WORLD_ID).any { it.occupiedByOtherTable })
    }

    /** Verifies that all-free registration overrides the default occupied placeholder layout. */
    @Test
    fun registerAllFreeTiles_overridesDefaultLayoutWithOccupiedTiles() {
        assertTrue(ServerConnection.tilesFor(TEST_WORLD_ID).any { it.occupiedByOtherTable })

        ServerConnection.registerAllFreeTiles(NEW_WORLD_ID)

        assertEquals(0, ServerConnection.tilesFor(NEW_WORLD_ID).count { it.occupiedByOtherTable })
    }

    /** Verifies that registering all-free tiles for one world leaves other worlds unchanged. */
    @Test
    fun registerAllFreeTiles_doesNotAffectOtherWorlds() {
        ServerConnection.registerAllFreeTiles(NEW_WORLD_ID)

        assertTrue(ServerConnection.tilesFor(OTHER_WORLD_ID).any { it.occupiedByOtherTable })
    }

    /** Verifies that registered all-free tiles are claimable. */
    @Test
    fun registerAllFreeTiles_allTilesCanBeClaimed() {
        ServerConnection.registerAllFreeTiles(NEW_WORLD_ID)
        val firstTile = ServerConnection.tilesFor(NEW_WORLD_ID).first()

        assertTrue(ServerConnection.claimTile(NEW_WORLD_ID, firstTile.id))
    }
}
