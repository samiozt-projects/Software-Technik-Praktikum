package domaintests

import domain.ConnectionState
import domain.TableTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Covers the [ConnectionState] enum's labels and default value. */
class ConnectionStateTest {

    /** Verifies that the default state before any connection attempt is disconnected. */
    @Test
    fun default_isDisconnected() {
        assertEquals(ConnectionState.DISCONNECTED, ConnectionState.DEFAULT)
    }

    /** Verifies that every state exposes a non-blank German UI label. */
    @Test
    fun everyState_hasANonBlankLabel() {
        ConnectionState.entries.forEach { state ->
            assertTrue(state.label.isNotBlank(), "label for $state should not be blank")
        }
    }

    /** Verifies that the reconnecting state carries the "connection lost" label shown to players. */
    @Test
    fun reconnecting_labelReadsConnectionLost() {
        assertEquals("Verbindung zum Server verloren", ConnectionState.RECONNECTING.label)
    }
}

/** Covers the [TableTile] data class. */
class TableTileTest {

    /** Verifies that two tiles with the same id and occupancy are equal. */
    @Test
    fun tilesWithSameIdAndOccupancy_areEqual() {
        val first = TableTile("Tisch A", occupiedByOtherTable = true)
        val second = TableTile("Tisch A", occupiedByOtherTable = true)

        assertEquals(first, second)
    }

    /** Verifies that tiles with different occupancy are not equal even with the same id. */
    @Test
    fun tilesWithDifferentOccupancy_areNotEqual() {
        val occupied = TableTile("frei 1", occupiedByOtherTable = true)
        val free = TableTile("frei 1", occupiedByOtherTable = false)

        assertNotEquals(occupied, free)
    }
}
