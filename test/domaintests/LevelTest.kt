package domaintests

import domain.ConnectionNode
import domain.Color
import domain.Level
import domain.NetworkState
import domain.SourceNode
import domain.Street
import domain.TargetNode
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioural tests for [Level] street mutation that are not about validation
 * (network validation is covered by [LevelValidationTest]).
 */
class LevelTest {

    /**
     * Verifies that removing a street removes now-dangling connection endpoints.
     */
    @Test
    fun removeStreet_danglingConnectionNodeEndpoints_areRemoved() {
        val level = Level("Cleanup", 1)
        val start = ConnectionNode(Point(100, 100), 0)
        val end = ConnectionNode(Point(300, 100), 0)
        val street = Street(start, end, listOf(Point(100, 100), Point(300, 100)))
        level.addNode(start)
        level.addNode(end)
        level.addStreet(street)

        level.removeStreet(street)

        assertTrue(level.streets.isEmpty(), "street should be removed")
        assertFalse(start in level.nodes, "dangling start ConnectionNode should be auto-removed")
        assertFalse(end in level.nodes, "dangling end ConnectionNode should be auto-removed")
    }

    /**
     * Verifies that removing a street keeps connection nodes that still have streets.
     */
    @Test
    fun removeStreet_connectionNodeWithRemainingStreets_isKept() {
        val level = Level("KeepBusyNode", 2)
        val a = ConnectionNode(Point(100, 100), 0)
        val b = ConnectionNode(Point(300, 100), 0)
        val c = ConnectionNode(Point(500, 100), 0)
        val firstStreet = Street(a, b, listOf(Point(100, 100), Point(300, 100)))
        val secondStreet = Street(b, c, listOf(Point(300, 100), Point(500, 100)))
        level.addNode(a)
        level.addNode(b)
        level.addNode(c)
        level.addStreet(firstStreet)
        level.addStreet(secondStreet)

        level.removeStreet(firstStreet)

        assertFalse(a in level.nodes, "node a became dangling and should be removed")
        assertTrue(b in level.nodes, "node b still has an outgoing street and must be kept")
        assertTrue(c in level.nodes, "node c still has an incoming street and must be kept")
        assertEquals(listOf(secondStreet), level.streets)
    }

    /**
     * Verifies that clearing the constructed network keeps fixed source/target nodes but removes
     * streets and connection nodes left from a previous detection.
     */
    @Test
    fun clearStreetNetwork_removesStreetsAndConnectionNodesButKeepsFixedEndpoints() {
        val red = Color("Rot", Colors.RED)
        val level = Level("ClearNetwork", 3, networkState = NetworkState.COMPLETE)
        val source = SourceNode(Point(100, 100), 0, Angle.ZERO, emptyList())
        val connection = ConnectionNode(Point(300, 100), 0)
        val orphanConnection = ConnectionNode(Point(300, 300), 0)
        val target = TargetNode(Point(500, 100), 0, Angle.ZERO, red)
        val firstStreet = Street(source, connection, listOf(source.position, connection.position))
        val secondStreet = Street(connection, target, listOf(connection.position, target.position))

        listOf(source, connection, orphanConnection, target).forEach(level::addNode)
        listOf(firstStreet, secondStreet).forEach(level::addStreet)

        level.clearStreetNetwork()

        assertTrue(level.streets.isEmpty(), "all streets from the previous network should be removed")
        assertTrue(source in level.nodes, "source node must remain after clearing streets")
        assertTrue(target in level.nodes, "target node must remain after clearing streets")
        assertFalse(connection in level.nodes, "connected construction node should be removed")
        assertFalse(orphanConnection in level.nodes, "orphan construction node should be removed")
        assertTrue(source.outgoingStreets.isEmpty(), "source must no longer reference old streets")
        assertTrue(target.incomingStreets.isEmpty(), "target must no longer reference old streets")
        assertEquals(NetworkState.EMPTY, level.networkState)
    }
}
