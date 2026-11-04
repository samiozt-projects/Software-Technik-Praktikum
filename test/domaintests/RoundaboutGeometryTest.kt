package domaintests

import domain.Color
import domain.ConnectionNode
import domain.Level
import domain.Node
import domain.RoundaboutGeometry
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.TargetNode
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests [RoundaboutGeometry] directly, independent of image detection.
 *
 * A hexagonal junction hub (6 ConnectionNodes forming a roundabout, reported against a real
 * detected level) exposed a bug: assigning each ring segment its direction independently via the
 * general "nearest source" rule can produce a ring that is not a single, consistently drivable
 * circle (e.g. two opposite arcs both pointing "inward"), stranding some source/target
 * combinations even though a real roundabout would let every vehicle get around. RoundaboutGeometry
 * must instead derive every ring segment's direction from the same circle, so the six arcs always
 * form one continuous one-way loop.
 */
class RoundaboutGeometryTest {

    /**
     * The reported level's hexagonal junction hub: 6 ConnectionNodes connected by 6 undirected
     * segments forming one ring.
     * @property n7 Hub junction node 7.
     * @property n8 Hub junction node 8.
     * @property n9 Hub junction node 9.
     * @property n10 Hub junction node 10.
     * @property n11 Hub junction node 11.
     * @property n12 Hub junction node 12.
     */
    private data class HexagonalHub(
        val n7: ConnectionNode,
        val n8: ConnectionNode,
        val n9: ConnectionNode,
        val n10: ConnectionNode,
        val n11: ConnectionNode,
        val n12: ConnectionNode
    ) {
        /** All six hub junctions as a plain list. */
        val nodes: List<ConnectionNode> get() = listOf(n7, n8, n9, n10, n11, n12)

        /** The hub's 6 undirected ring connections: 7-12, 12-8, 8-9, 9-10, 10-11, 11-7, as straight-chord segments. */
        val ringEdges: List<RoundaboutGeometry.RingEdge>
            get() = listOf(n7 to n12, n12 to n8, n8 to n9, n9 to n10, n10 to n11, n11 to n7)
                .map { (a, b) -> RoundaboutGeometry.RingEdge(a, b, listOf(a.position, b.position)) }
    }

    /** Metre-space detect parameters, matching what [domain.StreetPathFinder] passes for level streets. */
    private fun detectInMetres(junctions: List<Node>, ringEdges: List<RoundaboutGeometry.RingEdge>) =
        RoundaboutGeometry.detect(
            junctions,
            ringEdges,
            radiusTolerance = domain.GameSimulationConfig.ROUNDABOUT_RADIUS_TOLERANCE_METERS,
            maxRadius = domain.GameSimulationConfig.MAX_ROUNDABOUT_RADIUS_METERS
        )

    /** Builds the hexagonal hub at the exact positions from the reported level. */
    private fun buildHexagonalHub() = HexagonalHub(
        n7 = ConnectionNode(Point(101.125, 50.375), 0),
        n8 = ConnectionNode(Point(138.875, 45.125), 0),
        n9 = ConnectionNode(Point(139.5, 75.625), 0),
        n10 = ConnectionNode(Point(119.125, 79.5), 0),
        n11 = ConnectionNode(Point(100.375, 74.0), 0),
        n12 = ConnectionNode(Point(117.375, 37.5), 0)
    )

    /** Builds a directed adjacency map for [ring] by orienting each pair via [RoundaboutGeometry]. */
    private fun directedRingAdjacency(ring: List<RoundaboutGeometry.RingEdge>, center: Point): Map<Node, Node> {
        val adjacency = mutableMapOf<Node, Node>()
        for (edge in ring) {
            if (RoundaboutGeometry.isRightHandDirection(edge.nodeA, edge.nodeB, center)) {
                adjacency[edge.nodeA] = edge.nodeB
            } else {
                adjacency[edge.nodeB] = edge.nodeA
            }
        }
        return adjacency
    }

    /** Tests that all six hub junctions are recognized as lying on one detected roundabout circle. */
    @Test
    fun detect_hexagonalHub_findsAllSixJunctionsOnOneCircle() {
        val hub = buildHexagonalHub()

        val roundabout = detectInMetres(hub.nodes, hub.ringEdges)

        assertTrue(roundabout != null, "six junctions arranged in a ring should be detected as a roundabout")
        assertEquals(6, roundabout!!.nodeKeys.size, "all six junctions should lie on the fitted circle")
    }

    /**
     * Orienting each of the six ring segments independently must still yield a single consistent
     * one-way loop: every node gets exactly one ring-outgoing and one ring-incoming edge, so a
     * vehicle entering anywhere on the ring can reach every other point on it.
     */
    @Test
    fun isRightHandDirection_hexagonalHub_producesOneConsistentLoopNotAMixedUpTangle() {
        val hub = buildHexagonalHub()
        val roundabout = detectInMetres(hub.nodes, hub.ringEdges)
        assertTrue(roundabout != null)

        val adjacency = directedRingAdjacency(hub.ringEdges, roundabout!!.center)

        // A consistent single loop has exactly one outgoing edge per node (6 edges, 6 nodes).
        assertEquals(6, adjacency.size, "every junction must have exactly one outgoing ring edge")

        // Walking the ring from any starting node must visit all six nodes and return to the start,
        // proving the direction assignment forms one continuous circle instead of a broken tangle.
        var current: Node = hub.n7
        val visited = mutableListOf(current)
        repeat(6) {
            current = adjacency.getValue(current)
            visited.add(current)
        }
        assertEquals(hub.n7, visited.last(), "walking six ring edges from n7 must return to n7")
        assertEquals(7, visited.size)
        assertEquals(hub.nodes.toSet(), visited.toSet(), "the loop must pass through every junction")
    }

    /**
     * End-to-end reproduction of the reported level: 3 sources and 3 targets connected through the
     * hexagonal roundabout hub. Before the fix, the ring's direction was decided per segment by the
     * general "nearest source" rule, which left 2 of the 4 source/target color combinations
     * (Source 1 / Rot and Source 2 / Tuerkis) with no way through the ring, each showing a
     * construction marker despite a physically complete roundabout. Once every ring segment gets its
     * direction from the same circle (as RoundaboutGeometry now guarantees), the level must validate
     * without any errors.
     */
    @Test
    fun fullReportedLevel_withConsistentRingDirection_hasNoUnreachableConfigurations() {
        val hub = buildHexagonalHub()
        val roundabout = detectInMetres(hub.nodes, hub.ringEdges)
        assertTrue(roundabout != null, "the hub must be recognized as a roundabout")
        val ringDirections = directedRingAdjacency(hub.ringEdges, roundabout!!.center)

        val level = buildReportedLevel(hub, buildReportedLevelActors(), ringDirections)

        val errors = level.validate()
        assertTrue(errors.isEmpty(), "Reported level should validate without errors, but got: ${errors.map { it.message }}")
    }

    /**
     * Any 3 non-collinear points trivially lie on a common circumcircle, so a circle fit alone
     * always "finds" a 3-point roundabout candidate. These four junction positions are exactly what
     * Road5.png's detector output produces: two ordinary, unrelated street crossings (not a
     * roundabout) where 3 of the 4 points happen to be equidistant from an incidental center. Without
     * the ring-connectivity check this used to be misdetected as a roundabout, corrupting a normal
     * one-way segment's direction. Because none of the three are actually wired to each other as a
     * ring (they belong to two separate ordinary crossings instead), detection must reject them.
     */
    @Test
    fun detect_coincidentallyEquidistantButUnconnectedJunctions_isNotMisdetectedAsARoundabout() {
        val crossingA1 = ConnectionNode(Point(898.0, 266.0), 0)
        val crossingA2 = ConnectionNode(Point(1124.0, 418.0), 0)
        val crossingA3 = ConnectionNode(Point(894.0, 508.0), 0)
        val crossingB = ConnectionNode(Point(1230.0, 426.0), 0)

        // Two ordinary crossings: crossingA1/A2/A3 are not mutually connected to each other; each is
        // only connected to unrelated branch nodes elsewhere in the network (not part of this test),
        // so there simply are no direct edges between them here - unlike a real ring.
        val unrelatedEdges = listOf<RoundaboutGeometry.RingEdge>()

        val roundabout = RoundaboutGeometry.detect(
            listOf(crossingA1, crossingA2, crossingA3, crossingB),
            unrelatedEdges
        )

        assertTrue(
            roundabout == null,
            "coincidentally equidistant but unconnected junctions must not be misdetected as a roundabout"
        )
    }

    /**
     * Live-observed false positive (2026-07-13 log): the four corner crossings of a rectangular
     * street block lie exactly on their circumcircle and are wired as a closed ring, so they passed
     * every earlier check and got force-locked to ring direction - corrupting four ordinary one-way
     * streets and cutting sources off from their targets. The circle is far larger than any physical
     * roundabout element ([domain.GameSimulationConfig.MAX_ROUNDABOUT_RADIUS_METERS]), so detection
     * must reject it.
     */
    @Test
    fun detect_largeRectangularBlockOutline_isNotMisdetectedAsARoundabout() {
        // Positions in detector pixels, straight from the live log's forced "ring".
        val cornerA = ConnectionNode(Point(1160.0, 790.0), 0)
        val cornerB = ConnectionNode(Point(1171.0, 324.0), 0)
        val cornerC = ConnectionNode(Point(1559.0, 307.0), 0)
        val cornerD = ConnectionNode(Point(1552.0, 788.0), 0)
        val blockEdges = listOf(cornerA to cornerB, cornerB to cornerC, cornerC to cornerD, cornerD to cornerA)
            .map { (a, b) -> RoundaboutGeometry.RingEdge(a, b, listOf(a.position, b.position)) }

        val roundabout = RoundaboutGeometry.detect(listOf(cornerA, cornerB, cornerC, cornerD), blockEdges)

        assertTrue(roundabout == null, "a street block's rectangular outline must not be misdetected as a roundabout")
    }

    /**
     * A small square block fits under the radius cap, so only the path-conformity check can tell it
     * apart from a real roundabout: its straight edges cut ~29% of the radius inside the circle,
     * while a real roundabout's segments stay near the circle line. Detection must reject it.
     */
    @Test
    fun detect_smallSquareBlockWithStraightEdges_isRejectedByPathConformity() {
        val cornerA = ConnectionNode(Point(800.0, 400.0), 0)
        val cornerB = ConnectionNode(Point(1000.0, 400.0), 0)
        val cornerC = ConnectionNode(Point(1000.0, 600.0), 0)
        val cornerD = ConnectionNode(Point(800.0, 600.0), 0)
        val blockEdges = listOf(cornerA to cornerB, cornerB to cornerC, cornerC to cornerD, cornerD to cornerA)
            .map { (a, b) -> RoundaboutGeometry.RingEdge(a, b, listOf(a.position, b.position)) }

        val roundabout = RoundaboutGeometry.detect(listOf(cornerA, cornerB, cornerC, cornerD), blockEdges)

        assertTrue(roundabout == null, "a small square block's straight edges must fail the path-conformity check")
    }

    /**
     * The 3 sources and 3 targets of the reported level.
     * @property source1 Source configured for Rot and Blau, feeding the hub through n7.
     * @property source2 Source configured for Tuerkis, feeding the hub through n11.
     * @property source3 Source configured for Blau, feeding the hub through n12.
     * @property targetTurquoise Tuerkis target, fed by the hub through n8.
     * @property targetRed Rot target, fed by the hub through n9.
     * @property targetBlue Blau target, fed by the hub through n10.
     */
    private data class ReportedLevelActors(
        val source1: SourceNode,
        val source2: SourceNode,
        val source3: SourceNode,
        val targetTurquoise: TargetNode,
        val targetRed: TargetNode,
        val targetBlue: TargetNode
    )

    /** Builds the reported level's 3 sources and 3 targets with their exact reported configurations. */
    private fun buildReportedLevelActors(): ReportedLevelActors {
        val red = Color("Rot", Colors.RED)
        val blue = Color("Blau", Colors.BLUE)
        val turquoise = Color("Tuerkis", Colors.CYAN)
        val car = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)
        val truck = VehicleType("Lastwagen", 60.0, 8.0, 0.65, 1.8)

        return ReportedLevelActors(
            source1 = SourceNode(
                Point(5.0, 31.25), 0, Angle.Companion.ZERO,
                listOf(SourceNodeConfiguration(red, 1.8, 55.0, car), SourceNodeConfiguration(blue, 3.0, 70.0, car))
            ),
            source2 = SourceNode(Point(5.0, 93.75), 0, Angle.Companion.ZERO, listOf(SourceNodeConfiguration(turquoise, 4.5, 35.0, truck))),
            source3 = SourceNode(Point(112.5, 5.0), 0, Angle.Companion.ZERO, listOf(SourceNodeConfiguration(blue, 5.5, 38.0, truck))),
            targetTurquoise = TargetNode(Point(220.0, 31.5), 0, Angle.Companion.ZERO, turquoise),
            targetRed = TargetNode(Point(220.0, 93.75), 0, Angle.Companion.ZERO, red),
            targetBlue = TargetNode(Point(112.5, 120.0), 0, Angle.Companion.ZERO, blue)
        )
    }

    /** Builds the reported level: [actors] wired to [hub] via [ringDirections]. */
    private fun buildReportedLevel(hub: HexagonalHub, actors: ReportedLevelActors, ringDirections: Map<Node, Node>): Level {
        val level = Level("ReportedRoundaboutLevel", 4)
        val allActors = listOf(actors.source1, actors.source2, actors.source3, actors.targetTurquoise, actors.targetRed, actors.targetBlue)
        for (node in hub.nodes + allActors) {
            level.addNode(node)
        }

        // Boundary streets: source into the ring, ring exit to the matching target.
        level.addStreet(Street(actors.source1, hub.n7, listOf(actors.source1.position, hub.n7.position)))
        level.addStreet(Street(actors.source2, hub.n11, listOf(actors.source2.position, hub.n11.position)))
        level.addStreet(Street(actors.source3, hub.n12, listOf(actors.source3.position, hub.n12.position)))
        level.addStreet(Street(hub.n8, actors.targetTurquoise, listOf(hub.n8.position, actors.targetTurquoise.position)))
        level.addStreet(Street(hub.n9, actors.targetRed, listOf(hub.n9.position, actors.targetRed.position)))
        level.addStreet(Street(hub.n10, actors.targetBlue, listOf(hub.n10.position, actors.targetBlue.position)))

        // Ring streets, each oriented by RoundaboutGeometry rather than by source proximity.
        for ((from, to) in ringDirections) {
            level.addStreet(Street(from, to, listOf(from.position, to.position)))
        }

        return level
    }
}
