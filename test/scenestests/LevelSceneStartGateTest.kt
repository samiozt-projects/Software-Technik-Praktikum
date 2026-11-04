package scenestests

import domain.Color
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
import scenes.LevelScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the simulation start gate ([LevelScene.sourceConfigurationsWithoutRoutes]): the simulation
 * may only start when every (source, configuration) pair has an actual driving route from
 * [domain.StreetPathFinder] - [domain.LevelValidator]'s [NetworkState.COMPLETE] alone is not enough,
 * because the two walk independently built graphs and can disagree on detection artifacts (e.g. a
 * degenerate single-point street path).
 */
class LevelSceneStartGateTest {

    private val red = Color("Rot", Colors.RED)
    private val auto = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)

    private fun sourceWithOneConfig(position: Point): SourceNode =
        SourceNode(position, 0, Angle.fromDegrees(0.0), listOf(SourceNodeConfiguration(red, 1.0, 50.0, auto)))

    /** Verifies that a network with a real route for every configuration passes the start gate. */
    @Test
    fun sourceConfigurationsWithoutRoutes_routableNetwork_returnsEmpty() {
        val level = Level("Gate", 1)
        val source = sourceWithOneConfig(Point(0.0, 50.0))
        val target = TargetNode(Point(100.0, 50.0), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(source.position, target.position)))
        level.validate()

        assertTrue(
            LevelScene.sourceConfigurationsWithoutRoutes(level).isEmpty(),
            "a network with a real route for every configuration must pass the start gate"
        )
    }

    /**
     * The exact live-observed failure: a degenerate street keeps the validator's object graph
     * connected (networkState == COMPLETE), but the router drops it, so nothing could ever spawn.
     * The gate must report the affected configuration instead of letting the simulation start.
     */
    @Test
    fun sourceConfigurationsWithoutRoutes_degenerateStreetWithCompleteNetworkState_reportsConfiguration() {
        val level = Level("GateDivergence", 1)
        val source = sourceWithOneConfig(Point(0.0, 50.0))
        val target = TargetNode(Point(100.0, 50.0), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(target)
        // Single-point path: visible to LevelValidator via node.outgoingStreets, invisible to
        // StreetPathFinder's `path.size >= 2` filter.
        level.addStreet(Street(source, target, listOf(source.position)))
        level.validate()
        assertEquals(NetworkState.COMPLETE, level.networkState, "test precondition: the validator considers this network complete")

        val missing = LevelScene.sourceConfigurationsWithoutRoutes(level)

        assertEquals(1, missing.size, "the start gate must catch the configuration the router cannot serve")
        assertEquals(source, missing.single().first)
    }

    /** Verifies that only the configuration without a route is reported, not the routable one beside it. */
    @Test
    fun sourceConfigurationsWithoutRoutes_onlyRouteLessConfigurationIsReported() {
        val level = Level("GateMixed", 1)
        val blue = Color("Blau", Colors.BLUE)
        val source = SourceNode(
            Point(0.0, 50.0), 0, Angle.fromDegrees(0.0),
            listOf(
                SourceNodeConfiguration(red, 1.0, 50.0, auto),
                SourceNodeConfiguration(blue, 1.0, 50.0, auto)
            )
        )
        val redTarget = TargetNode(Point(100.0, 50.0), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(redTarget)
        // Only the red configuration has a route; blue has no matching target anywhere.
        level.addStreet(Street(source, redTarget, listOf(source.position, redTarget.position)))
        level.validate()

        val missing = LevelScene.sourceConfigurationsWithoutRoutes(level)

        assertEquals(1, missing.size)
        assertEquals("Blau", missing.single().second.color.name)
    }
}
