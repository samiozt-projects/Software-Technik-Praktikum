package scenestests

import domain.Color
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.TargetNode
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import scenes.StartGateRouteCache
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests [StartGateRouteCache], the cached start gate deciding whether every source configuration
 * has a driving route on the level's current committed street network.
 */
class StartGateRouteCacheTest {

    private val red = Color("Rot", Colors.RED)
    private val car = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)

    /** One level with a single red source configuration and a matching target, without streets. */
    private fun levelWithSourceAndTarget(): Triple<Level, SourceNode, TargetNode> {
        val level = Level("test", 1)
        val config = SourceNodeConfiguration(
            red,
            generationSpeed = 1.0,
            initialVehicleSpeed = 50.0,
            vehicleType = car,
            vehiclesToSpawn = 5
        )
        val source = SourceNode(Point(0.0, 0.0), 0, Angle.fromDegrees(0.0), listOf(config))
        val target = TargetNode(Point(100.0, 0.0), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(target)
        return Triple(level, source, target)
    }

    /**
     * Regression (2026-07-13): the cache used to start with an *empty checked street list*, so a
     * level whose detection was never committed (empty [Level.streets]) compared "unchanged" against
     * the initial empty result and passed the gate - the countdown then started a simulation on a
     * network without a single street, where no vehicle could ever spawn. An unchecked cache must
     * compute, and an empty network must hold the gate for every configuration.
     */
    @Test
    fun emptyStreetNetwork_holdsTheStartGate() {
        val (level, source, _) = levelWithSourceAndTarget()

        val missing = StartGateRouteCache().missingRoutesFor(level)

        assertTrue(
            missing.isNotEmpty(),
            "an uncommitted (empty) street network must hold the start gate"
        )
        assertTrue(
            missing.all { it.first === source },
            "every blocked pair must belong to the level's route-less source"
        )
    }

    /** Once a routable network is committed, the gate must recompute and open. */
    @Test
    fun committedNetworkWithRoutes_opensTheStartGate() {
        val (level, source, target) = levelWithSourceAndTarget()
        val cache = StartGateRouteCache()

        assertTrue(
            cache.missingRoutesFor(level).isNotEmpty(),
            "precondition: the gate holds before any street exists"
        )

        level.addStreet(Street(source, target, listOf(source.position, target.position)))

        assertTrue(
            cache.missingRoutesFor(level).isEmpty(),
            "the gate must recompute for the committed network and open once every configuration has a route"
        )
    }

    /** Removing the only street again (e.g. a re-detection that found nothing) must close the gate. */
    @Test
    fun clearedNetwork_closesTheStartGateAgain() {
        val (level, source, target) = levelWithSourceAndTarget()
        val cache = StartGateRouteCache()
        val street = Street(source, target, listOf(source.position, target.position))
        level.addStreet(street)

        assertTrue(cache.missingRoutesFor(level).isEmpty(), "precondition: the gate is open with a routable network")

        level.removeStreet(street)

        assertTrue(
            cache.missingRoutesFor(level).isNotEmpty(),
            "the gate must close again when the committed network is cleared"
        )
    }
}
