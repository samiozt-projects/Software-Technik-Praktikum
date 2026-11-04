package domaintests

import domain.Color
import domain.ConnectionNode
import domain.Level
import domain.NetworkState
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.TargetNode
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import korlibs.math.geom.Vector2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the validation of a level.
 */
class LevelValidationTest {

    private val red = Color("Red", Colors.RED)
    private val blue = Color("Blue", Colors.BLUE)
    private val car = VehicleType("Car", 120.0, 4.0, 0.8, 5.0)

    /**
     * Tests that an empty level is invalid.
     */
    @Test
    fun testEmptyLevel() {
        val level = Level("Empty", 0)
        val errors = level.validate()
        assertTrue(
            errors.any { it.message.contains("at least one SourceNode") },
            "Should report missing SourceNode"
        )
        assertTrue(
            errors.any { it.message.contains("at least one TargetNode") },
            "Should report missing TargetNode"
        )
        assertEquals(NetworkState.EMPTY, level.networkState)
    }

    /**
     * Tests that a valid level has no validation errors.
     */
    @Test
    fun testValidLevel() {
        val level = Level("Valid", 1)
        val config = SourceNodeConfiguration(red, 1.0, 50.0, car)
        val source = SourceNode(Point(100, 100), 0, Angle.Companion.ZERO, listOf(config))
        val target = TargetNode(Point(500, 100), 0, Angle.Companion.ZERO, red)

        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(Point(100, 100), Point(500, 100))))

        val errors = level.validate()
        assertTrue(
            errors.isEmpty(),
            "Errors should be empty but found: ${errors.map { it.message }}"
        )
        assertEquals(NetworkState.COMPLETE, level.networkState)
        assertEquals(emptyList(), level.validationErrors)
        assertEquals(emptySet(), level.constructionMarkerPositions)
        assertEquals(emptySet(), level.constructionMarkerNodes)
    }

    /**
     * Tests that a level with a missing path has validation errors.
     */
    @Test
    fun testMissingPath() {
        val level = Level("NoPath", 2)
        val config = SourceNodeConfiguration(red, 1.0, 50.0, car)
        val source = SourceNode(Point(100, 100), 0, Angle.Companion.ZERO, listOf(config))
        val target = TargetNode(Point(500, 100), 0, Angle.Companion.ZERO, red)

        level.addNode(source)
        level.addNode(target)
        // No street added

        val errors = level.validate()
        assertTrue(
            errors.any { it.message.contains("no path to a target") },
            "Should report missing path"
        )
        assertEquals(red, errors.first { it.message.contains("no path") }.color)
        assertEquals(NetworkState.EMPTY, level.networkState)
    }

    /**
     * Tests that a level with a missing path but no streets does not show construction markers.
     */
    @Test
    fun testMissingPathWithoutStreetsDoesNotShowConstructionMarkers() {
        val level = Level("NoPathMarkers", 20)
        val config = SourceNodeConfiguration(red, 1.0, 50.0, car)
        val source = SourceNode(Point(100, 100), 0, Angle.Companion.ZERO, listOf(config))
        val target = TargetNode(Point(500, 100), 0, Angle.Companion.ZERO, red)

        level.addNode(source)
        level.addNode(target)

        val errors = level.validate()
        val pathError = errors.first { it.message.contains("no path") }

        assertEquals(errors, level.validationErrors, "latest validation result should be stored")
        assertTrue(source in pathError.affectedNodes, "missing path should include the source node")
        assertTrue(target in pathError.affectedNodes, "missing path should include the matching target node")
        assertEquals(emptyList(), pathError.affectedStreets)
        assertEquals(
            emptySet(),
            level.constructionMarkerPositions,
            "markers should stay hidden until the player has built at least one street"
        )
        assertEquals(
            emptySet(),
            level.constructionMarkerNodes,
            "marker nodes should stay hidden until the player has built at least one street"
        )
    }

    /**
     * Tests that construction markers move to the nearest reachable dead end when the path is missing.
     */
    @Test
    fun testMissingPathMarkersMoveToReachableDeadEnd() {
        val level = Level("PartialPathMarkers", 21)
        val config = SourceNodeConfiguration(red, 1.0, 50.0, car)
        val source = SourceNode(Point(100, 100), 0, Angle.Companion.ZERO, listOf(config))
        val middle = ConnectionNode(Point(300, 100), 0)
        val target = TargetNode(Point(500, 100), 0, Angle.Companion.ZERO, red)
        val partialStreet = Street(source, middle, listOf(Point(100, 100), Point(300, 100)))

        level.addNode(source)
        level.addNode(middle)
        level.addNode(target)
        level.addStreet(partialStreet)

        val errors = level.validate()
        val pathError = errors.first { it.message.contains("no path") }

        assertTrue(middle in pathError.affectedNodes, "reachable dead end should be affected")
        assertTrue(partialStreet in pathError.affectedStreets, "reachable streets should be affected")
        assertEquals(
            setOf(middle.position, target.position),
            level.constructionMarkerPositions,
            "marker should move from the source to the current reachable dead end"
        )
        assertEquals(
            setOf(middle, target),
            level.constructionMarkerNodes,
            "marker nodes should move from source to the reachable dead end"
        )
    }

    /**
     * Tests a genuine one-way dead end: the street between the dead end and the target was only
     * detected running the wrong way, so the source cannot reach a matching target even though a
     * physical connection exists. The validation message must hint at the one-way cause instead of
     * just reporting "no path found", and the dead end itself must also be flagged.
     */
    @Test
    fun testOneWayDeadEndReportsMissingReturnStreetHint() {
        val level = Level("OneWayDeadEnd", 23)
        val config = SourceNodeConfiguration(blue, 1.0, 50.0, car)
        val source = SourceNode(Point(100, 100), 0, Angle.Companion.ZERO, listOf(config))
        val deadEnd = ConnectionNode(Point(300, 100), 0)
        val target = TargetNode(Point(500, 100), 0, Angle.Companion.ZERO, blue)

        level.addNode(source)
        level.addNode(deadEnd)
        level.addNode(target)
        level.addStreet(Street(source, deadEnd, listOf(source.position, deadEnd.position)))
        // The only detected street between deadEnd and target runs target -> deadEnd: a return
        // street towards the target is missing, so deadEnd has no way out.
        level.addStreet(Street(target, deadEnd, listOf(target.position, deadEnd.position)))

        val errors = level.validate()
        val pathError = errors.first { it.message.contains("no path") && it.color == blue }

        assertTrue(
            pathError.message.contains("one-way", ignoreCase = true),
            "Missing path message should hint that a one-way / return street may be the cause"
        )
        assertTrue(deadEnd in pathError.constructionNodes, "construction marker should point at the dead end")
        assertTrue(
            errors.any { it.message.contains("no outgoing streets") },
            "The one-way dead end itself should also be flagged by the pass-through rule"
        )
    }

    /**
     * Tests that construction markers use the nearest reachable junction instead of a wrong-color target.
     */
    @Test
    fun testMissingPathMarkersUseNearestReachableJunctionInsteadOfWrongColorTarget() {
        val level = Level("TJunctionMarkers", 22)
        val redConfig = SourceNodeConfiguration(red, 1.0, 50.0, car)
        val blueConfig = SourceNodeConfiguration(blue, 1.0, 50.0, car)
        val source = SourceNode(Point(50, 300), 0, Angle.Companion.ZERO, listOf(redConfig, blueConfig))
        val intersection = ConnectionNode(Point(800, 300), 0)
        val redTarget = TargetNode(Point(1550, 300), 0, Angle.Companion.ZERO, red)
        val blueTarget = TargetNode(Point(800, 50), 0, Angle.Companion.fromDegrees(270.0), blue)

        level.addNode(source)
        level.addNode(intersection)
        level.addNode(redTarget)
        level.addNode(blueTarget)
        level.addStreet(Street(source, intersection, listOf(source.position, intersection.position)))
        level.addStreet(Street(intersection, redTarget, listOf(intersection.position, redTarget.position)))

        val errors = level.validate()
        val bluePathError = errors.first { it.message.contains("no path") && it.color == blue }

        assertTrue(intersection in bluePathError.constructionNodes)
        assertTrue(blueTarget in bluePathError.constructionNodes)
        assertTrue(redTarget !in bluePathError.constructionNodes)
        assertEquals(
            setOf(intersection, blueTarget),
            level.constructionMarkerNodes,
            "missing blue branch should be shown at the T-junction and blue target only"
        )
    }

    /**
     * Tests that a level with a wrong-color path has validation errors.
     */
    @Test
    fun testWrongColorPath() {
        val level = Level("WrongColor", 3)
        val config = SourceNodeConfiguration(red, 1.0, 50.0, car)
        val source = SourceNode(Point(100, 100), 0, Angle.Companion.ZERO, listOf(config))
        val target = TargetNode(Point(500, 100), 0, Angle.Companion.ZERO, blue) // Blue instead of Red

        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(Point(100, 100), Point(500, 100))))

        val errors = level.validate()
        assertTrue(
            errors.any { it.message.contains("no path to a target") },
            "Should report no path to matching color"
        )
        assertEquals(NetworkState.INCOMPLETE, level.networkState)
    }

    /**
     * Verifies that a connection node with outgoing streets but no incoming streets is invalid.
     */
    @Test
    fun testConnectionNodeWithoutIncomingStreets() {
        val level = Level("NoIncoming", 7)
        val mid = ConnectionNode(Point(300, 100), 0)
        val target1 = TargetNode(Point(500, 100), 0, Angle.Companion.ZERO, red)
        val target2 = TargetNode(Point(500, 300), 0, Angle.Companion.ZERO, blue)

        level.addNode(mid)
        level.addNode(target1)
        level.addNode(target2)

        // Two outgoing streets from mid, no incoming -> SHOULD FAIL the pass-through rule
        level.addStreet(Street(mid, target1, listOf(Point(300, 100), Point(500, 100))))
        level.addStreet(Street(mid, target2, listOf(Point(300, 100), Point(500, 300))))

        val errors = level.validate()
        assertTrue(
            errors.any {
                it.message.contains("ConnectionNode has 2 connections but no incoming streets")
            },
            "Should report pass-through rule violation for ConnectionNode with no incoming streets"
        )
    }

    /**
     * Tests that the pass-through rule applies only to connection nodes.
     */
    @Test
    fun testPassThroughRuleOnlyForConnectionNodes() {
        val level = Level("PassThrough", 4)
        val config = SourceNodeConfiguration(red, 1.0, 50.0, car)
        val source = SourceNode(Point(100, 100), 0, Angle.Companion.ZERO, listOf(config))
        val mid = ConnectionNode(Point(300, 100), 0)
        val target = TargetNode(Point(500, 100), 0, Angle.Companion.ZERO, red)

        level.addNode(source)
        level.addNode(mid)
        level.addNode(target)

        // Two incoming to mid, no outgoing -> SHOULD FAIL
        val source2 = SourceNode(Point(300, 0), 0, Angle.Companion.ZERO, emptyList())
        level.addNode(source2)

        level.addStreet(Street(source, mid, listOf(Point(100, 100), Point(300, 100))))
        level.addStreet(Street(source2, mid, listOf(Point(300, 0), Point(300, 100))))

        // Two outgoing from source2, no incoming -> SHOULD NOT FAIL because source2 is a SourceNode
        val mid2 = ConnectionNode(Point(400, 0), 0)
        level.addNode(mid2)
        Street(source2, mid2, listOf(Point(300, 0), Point(400, 0)))

        val errors = level.validate()
        assertTrue(
            errors.any {
                it.message.contains("ConnectionNode has 2 connections but no outgoing streets")
            },
            "Should report pass-through rule violation for ConnectionNode"
        )
        assertTrue(
            errors.none {
                it.message.contains("SourceNode") && it.message.contains("connections")
            },
            "SourceNode should be exempt from pass-through rule"
        )
    }

    /**
     * Tests that out-of-bounds nodes are reported.
     */
    @Test
    fun testOutOfBounds() {
        val level = Level("OutOfBounds", 5)
        val source = SourceNode(Point(-10, 100), 0, Angle.Companion.ZERO, emptyList())
        level.addNode(source)

        val errors = level.validate()
        assertTrue(
            errors.any { it.message.contains("outside the game area") },
            "Should report out of bounds"
        )
        assertEquals(Point(-10, 100), errors.first { it.message.contains("outside") }.position)
    }

    /**
     * Visual representation of the following network (there are two one-directional streets between
     * A and B):
     * ```
     *    (100, 100)      (200, 100)      (300, 100)                    (600, 100)
     *      [S1] ----------> [A] <--------> [B] ------------------------> [T1]
     *      (Red)             |             ^                            (Red)
     *                        |             |                             ^
     *      [Cross Connect]   |             |                             |
     *                        V             |       (400, 300)            |
     *      [S2] ----------> [C] ----------> [D] -------------------------|
     *      (Blue)                          |                             |
     *    (100, 500)      (200, 500)        |                             |
     *                                      V                             |
     *                                     [T2] --------------------------
     *                                    (Blue)
     *                                  (600, 500)
     * ```
     */

    /**
     * Tests that the complex network pathfinding works correctly.
     */
    @Test
    fun testComplexNetworkPathfinding() {
        val level = Level("Complex", 6)
        val configRed = SourceNodeConfiguration(red, 1.0, 50.0, car)
        val configBlue = SourceNodeConfiguration(blue, 1.0, 50.0, car)

        val s1 = SourceNode(Point(100, 100), 0, Angle.Companion.ZERO, listOf(configRed))
        val s2 = SourceNode(Point(100, 500), 0, Angle.Companion.ZERO, listOf(configBlue))

        val a = ConnectionNode(Point(200, 100), 0)
        val b = ConnectionNode(Point(300, 100), 0)
        val c = ConnectionNode(Point(200, 500), 0)
        val d = ConnectionNode(Point(400, 300), 0)

        val t1 = TargetNode(Point(600, 100), 0, Angle.Companion.ZERO, red)
        val t2 = TargetNode(Point(600, 500), 0, Angle.Companion.ZERO, blue)

        level.addNode(s1)
        level.addNode(s2)
        level.addNode(a)
        level.addNode(b)
        level.addNode(c)
        level.addNode(d)
        level.addNode(t1)
        level.addNode(t2)

        // S1 -> A -> B -> T1 (Main Red Path)
        level.addStreet(Street(s1, a, emptyList()))
        level.addStreet(Street(a, b, emptyList()))
        level.addStreet(Street(b, t1, emptyList()))

        // S2 -> C -> D -> T2 (Main Blue Path)
        level.addStreet(Street(s2, c, emptyList()))
        level.addStreet(Street(c, d, emptyList()))
        level.addStreet(Street(d, t2, emptyList()))

        // Cross connections

        // A -> C (Red can go to C area)
        level.addStreet(Street(a, c, emptyList()))
        // D -> T1 (Blue area can reach Red target)
        level.addStreet(Street(d, t1, emptyList()))
        // Loop: B -> A (backwards)
        level.addStreet(Street(b, a, emptyList()))

        val errors = level.validate()
        assertTrue(
            errors.isEmpty(),
            "Complex valid network should have no errors but found: ${errors.map { it.message }}"
        )
        assertEquals(NetworkState.COMPLETE, level.networkState)

        // Now break the blue path by removing D -> T2
        val dToT2 = level.streets.first { it.startNode == d && it.endNode == t2 }
        level.removeStreet(dToT2)

        val errors2 = level.validate()
        assertTrue(
            errors2.any {
                it.message.contains("no path to a target") && it.color?.name == "Blue"
            },
            "Should report missing path for Blue config"
        )
        assertEquals(NetworkState.INCOMPLETE, level.networkState)

        // Even if S2 can reach T1 (Red), it's not the right color, so it should still fail for Blue.
        assertTrue(errors2.none { it.color?.name == "Red" }, "Red config should still be valid")
    }
}
