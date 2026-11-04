package domaintests

import domain.*
import simulation.CongestionDetector
import simulation.Simulation
import korlibs.image.color.Colors
import korlibs.math.geom.Point
import korlibs.math.geom.Angle
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for the CongestionDetector class.
 */
class CongestionDetectorTest {

    /**
     * Scales a point from pixels to meters.
     *
     * @param xPx The x-coordinate in pixels.
     * @param yPx The y-coordinate in pixels.
     * @return The scaled point in meters.
     */
    private fun scaledPoint(xPx: Number, yPx: Number): Point =
        Point(xPx.toDouble() / Simulation.PX_PER_METER, yPx.toDouble() / Simulation.PX_PER_METER)

    /**
     * Creates a simple level with one source and one target for testing.
     */
    private fun createSimpleLevel(): Level {
        val level = Level("test", 1)
        
        val red = Color("Rot", Colors.RED)
        val carType = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)
        val config = SourceNodeConfiguration(
            red,
            generationSpeed = 1.0, // 1 second between spawns
            initialVehicleSpeed = 50.0,
            vehicleType = carType,
            vehiclesToSpawn = 10
        )
        
        val source = SourceNode(scaledPoint(0, 0), 0, Angle.fromDegrees(0.0), listOf(config))
        val target = TargetNode(scaledPoint(100, 0), 0, Angle.fromDegrees(0.0), red)
        
        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(scaledPoint(0, 0), scaledPoint(100, 0))))
        
        return level
    }

    /**
     * Test that congestion is detected when spawn is delayed for more than the generationSpeed.
     */
    @Test
    fun testSpawnDelayCongestionDetection() {
        val level = createSimpleLevel()
        val simulation = Simulation(level)
        val congestionDetector = CongestionDetector(simulation)
        
        // Initially, no congestion should be detected
        congestionDetector.checkForCongestion(0.1)
        assertFalse(simulation.congestionDetected, "No congestion should be detected initially")
        
        // Simulate a spawn delay by manually setting the spawn delay timer
        val source = level.nodes.filterIsInstance<SourceNode>().first()
        val config = source.configurations.first()
        val key = source to config
        
        // Access the spawn manager and set a delay
        val spawnManager = simulation.spawnManager
        
        // Test with delay greater than 2*generationSpeed (2.0) - should trigger congestion
        spawnManager.spawnDelayTimers[key] = 2.1
        congestionDetector.checkForCongestion(0.1)
        assertTrue(simulation.congestionDetected, "Congestion should be detected when spawn is delayed for > 2*generationSpeed (2.1 > 2.0)")
        
        // Test with delay equal to 2*generationSpeed - should not trigger congestion
        spawnManager.spawnDelayTimers[key] = 2.0
        congestionDetector.checkForCongestion(0.1)
        assertFalse(simulation.congestionDetected, "Congestion should not be detected at exactly 2*generationSpeed (2.0 == 2.0)")
        
        // Test with delay less than 2*generationSpeed - should not trigger congestion
        spawnManager.spawnDelayTimers[key] = 1.9
        congestionDetector.checkForCongestion(0.1)
        assertFalse(simulation.congestionDetected, "Congestion should not be detected when delay < 2*generationSpeed (1.9 < 2.0)")
    }

    /**
     * Test that congestion is not detected when no spawn delays exist.
     */
    @Test
    fun testNoSpawnDelayCongestion() {
        val level = createSimpleLevel()
        val simulation = Simulation(level)
        val congestionDetector = CongestionDetector(simulation)
        
        // Run congestion check multiple times
        for (i in 1..10) {
            congestionDetector.checkForCongestion(0.1)
            assertFalse(simulation.congestionDetected, "No congestion should be detected without spawn delays")
        }
    }

    /**
     * Regression (2026-07-13): a source configuration whose spawn fails because it has *no driving
     * route at all* (structurally broken/mis-detected network) must never be reported as congestion.
     * It used to accumulate the spawn-delay timer and flag "Verstopfung" a few seconds after the
     * start countdown - without a single vehicle ever driving - and did so again on every retry,
     * trapping the player in an endless countdown-congestion-retry loop.
     */
    @Test
    fun testSpawnBlockedByMissingRouteIsNotReportedAsCongestion() {
        val level = Level("test", 1)
        val red = Color("Rot", Colors.RED)
        val carType = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)
        val config = SourceNodeConfiguration(
            red,
            generationSpeed = 1.0,
            initialVehicleSpeed = 50.0,
            vehicleType = carType,
            vehiclesToSpawn = 10
        )
        level.addNode(SourceNode(scaledPoint(0, 0), 0, Angle.fromDegrees(0.0), listOf(config)))
        level.addNode(TargetNode(scaledPoint(100, 0), 0, Angle.fromDegrees(0.0), red))
        // Deliberately no street: the source has no driving route, as after a broken detection.

        val simulation = Simulation(level)

        // Far beyond 2 * generationSpeed (the spawn-delay congestion threshold).
        repeat(300) { simulation.tick(1.0 / 60.0) }

        assertFalse(
            simulation.congestionDetected,
            "a structurally route-less source must not be reported as congestion - no traffic ever existed"
        )
    }

    /**
     * Counterpart to the missing-route regression test: a spawn that stays blocked because standing
     * traffic occupies the street entry IS real congestion and must still be detected through the
     * spawn-delay criterion.
     */
    @Test
    fun testSpawnBlockedByStationaryTrafficIsReportedAsCongestion() {
        val level = Level("test", 1)
        val red = Color("Rot", Colors.RED)
        // A vehicle that can never move: zero initial speed and zero acceleration.
        val carType = VehicleType("Auto", 80.0, 4.0, 0.7, 0.0)
        val config = SourceNodeConfiguration(
            red,
            generationSpeed = 0.5,
            initialVehicleSpeed = 0.0,
            vehicleType = carType,
            vehiclesToSpawn = 3
        )
        val source = SourceNode(scaledPoint(0, 0), 0, Angle.fromDegrees(0.0), listOf(config))
        val target = TargetNode(scaledPoint(100, 0), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(scaledPoint(0, 0), scaledPoint(100, 0))))

        val simulation = Simulation(level)

        // First tick spawns the immovable vehicle at the entry; every following due spawn stays
        // blocked, so the delay exceeds 2 * generationSpeed (= 1s) well within 2.5 simulated seconds.
        repeat(150) { simulation.tick(1.0 / 60.0) }

        assertTrue(
            simulation.congestionDetected,
            "a spawn permanently blocked by standing traffic at the entry must be reported as congestion"
        )
    }

    /**
     * Regression (2026-07-13): the average-speed criterion needs persistence. A lone vehicle
     * standing for a single tick (e.g. yielding at a merge stop line right after the start) pulled
     * the network average to zero and instantly ended the run as "congested". Only a standstill
     * lasting beyond [GameSimulationConfig.AVERAGE_SPEED_CONGESTION_MIN_DURATION_S] may fire.
     */
    @Test
    fun testMomentaryStandstillIsNotCongestionButSustainedStandstillIs() {
        val level = createSimpleLevel()
        val simulation = Simulation(level)
        val congestionDetector = CongestionDetector(simulation)

        val source = level.nodes.filterIsInstance<SourceNode>().first()
        val config = source.configurations.first()
        val vehicle = Vehicle(config.color, config.vehicleType, source, config, level).apply {
            currentStreet = level.streets.first()
            currentSpeed = 0.0
        }
        level.addVehicle(vehicle)

        congestionDetector.checkForCongestion(1.0 / 60.0)
        assertFalse(
            simulation.congestionDetected,
            "a single stopped tick must not be reported as congestion"
        )

        // Keep the standstill going beyond the persistence window.
        repeat(310) { congestionDetector.checkForCongestion(1.0 / 60.0) }
        assertTrue(
            simulation.congestionDetected,
            "a standstill lasting beyond the persistence window must be reported as congestion"
        )
    }

    /**
     * Test that the dynamic threshold works with different generation speeds.
     */
    @Test
    fun testDynamicThresholdWithDifferentGenerationSpeeds() {
        // Create a level with a different generation speed (2.0 seconds)
        val level = Level("test", 1)
        
        val red = Color("Rot", Colors.RED)
        val carType = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)
        val config = SourceNodeConfiguration(
            red,
            generationSpeed = 2.0, // 2 seconds between spawns
            initialVehicleSpeed = 50.0,
            vehicleType = carType,
            vehiclesToSpawn = 10
        )
        
        val source = SourceNode(scaledPoint(0, 0), 0, Angle.fromDegrees(0.0), listOf(config))
        val target = TargetNode(scaledPoint(100, 0), 0, Angle.fromDegrees(0.0), red)
        
        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(scaledPoint(0, 0), scaledPoint(100, 0))))
        
        val simulation = Simulation(level)
        val congestionDetector = CongestionDetector(simulation)
        
        val spawnManager = simulation.spawnManager
        val key = source to config
        
        // With generationSpeed = 2.0, delay of 4.1 should trigger congestion (2*generationSpeed = 4.0)
        spawnManager.spawnDelayTimers[key] = 4.1
        congestionDetector.checkForCongestion(0.1)
        assertTrue(simulation.congestionDetected, "Congestion should be detected when delay (4.1) > 2*generationSpeed (4.0)")
        
        // With generationSpeed = 2.0, delay of 4.0 should not trigger congestion
        spawnManager.spawnDelayTimers[key] = 4.0
        congestionDetector.checkForCongestion(0.1)
        assertFalse(simulation.congestionDetected, "Congestion should not be detected when delay (4.0) == 2*generationSpeed (4.0)")
        
        // With generationSpeed = 2.0, delay of 3.9 should not trigger congestion
        spawnManager.spawnDelayTimers[key] = 3.9
        congestionDetector.checkForCongestion(0.1)
        assertFalse(simulation.congestionDetected, "Congestion should not be detected when delay (3.9) < 2*generationSpeed (4.0)")
    }
}