package domaintests

import domain.Color
import domain.ConnectionNode
import domain.CurveSpeed
import domain.GameState
import domain.Level
import domain.NetworkState
import domain.Node
import simulation.Simulation
import domain.SimulationSpeed
import domain.SimulationState
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.TargetNode
import simulation.TrafficFlowRating
import domain.Vehicle
import domain.VehicleType
import domain.WeatherCondition
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests the I1 vehicle simulation rules without involving rendering or KorGE scenes.
 *
 * The assertions focus on units, spawn timing, movement, speed capping, and target
 * arrival bookkeeping because those are the behaviors other layers depend on.
 */
class SimulationTest {

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
     * Scales a distance from pixels to meters.
     *
     * @param px The distance in pixels.
     * @return The scaled distance in meters.
     */
    private fun scaledDistance(px: Double): Double = px / Simulation.PX_PER_METER

    /**
     * Asserts that two independently simulated levels have the same externally visible state.
     *
     * @param expectedLevel The expected level.
     * @param actualLevel The actual level.
     * @param expectedSimulation The expected simulation.
     * @param actualSimulation The actual simulation.
     */
    private fun assertEquivalentPlaybackState(
        expectedLevel: Level,
        actualLevel: Level,
        expectedSimulation: Simulation,
        actualSimulation: Simulation
    ) {
        assertEquals(expectedSimulation.congestionDetected, actualSimulation.congestionDetected)
        assertEquals(expectedSimulation.levelCompleted, actualSimulation.levelCompleted)
        assertEquals(expectedLevel.vehicles.size, actualLevel.vehicles.size)

        expectedLevel.vehicles.zip(actualLevel.vehicles).forEach { (expected, actual) ->
            assertEquals(expectedLevel.streets.indexOf(expected.currentStreet), actualLevel.streets.indexOf(actual.currentStreet))
            assertEquals(expected.currentSegmentIndex, actual.currentSegmentIndex)
            assertEquals(expected.currentRouteStreetIndex, actual.currentRouteStreetIndex)
            assertEquals(expected.selectedRouteIndex, actual.selectedRouteIndex)
            assertEquals(expected.distanceAlongSegment, actual.distanceAlongSegment, 0.0001)
            assertEquals(expected.currentSpeed, actual.currentSpeed, 0.0001)
        }

        val expectedArrivals = expectedLevel.nodes.filterIsInstance<TargetNode>().map { it.vehiclesArrived }
        val actualArrivals = actualLevel.nodes.filterIsInstance<TargetNode>().map { it.vehiclesArrived }
        assertEquals(expectedArrivals, actualArrivals)
    }

    /**
     * Builds a minimal valid level: one source at (0,0), one target at old screen coordinate (800,0)
     * connected by a single straight street with one configuration spawning red Autos.
     *
     * @param initialVehicleSpeed The initial speed of the vehicle.
     * @param maxSpeed The maximum speed of the vehicle.
     * @param acceleration The acceleration of the vehicle.
     * @param generationSpeed The generation speed of the vehicle.
     * @param vehiclesToSpawn The number of vehicles to spawn.
     * @param weatherCondition The weather condition.
     * @return The level.
     */
    private fun buildLevel(
        initialVehicleSpeed: Double = 50.0,
        maxSpeed: Double = 80.0,
        acceleration: Double = 3.0,
        generationSpeed: Double = 30.0,
        vehiclesToSpawn: Int = 1,
        weatherCondition: WeatherCondition = WeatherCondition.DRY
    ): Level {
        // The test level is intentionally straight, so expected distances are easy to verify.
        val level = Level("TestLevel", 1, weatherCondition = weatherCondition)
        val red = Color("Rot", Colors.RED)
        val auto = VehicleType("Auto", maxSpeed, 4.0, 0.7, acceleration)
        val cfg = SourceNodeConfiguration(red, generationSpeed, initialVehicleSpeed, auto, vehiclesToSpawn = vehiclesToSpawn)

        val source = SourceNode(scaledPoint(0, 0), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val target = TargetNode(scaledPoint(800, 0), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(scaledPoint(0, 0), scaledPoint(800, 0))))
        level.validate()
        return level
    }

    /**
     * Simulates a completed one-vehicle straight flow with constant speed and validates the common
     * completion counters used by the traffic-flow rating tests.
     */
    private fun completedStraightFlowStatistics(speedKmh: Double, seconds: Int) =
        Simulation(buildLevel(initialVehicleSpeed = speedKmh, maxSpeed = speedKmh, acceleration = 0.0)).run {
            tick(0.0)
            repeat(seconds) { tick(1.0) }

            statistics().also { statistics ->
                assertEquals(1, statistics.generatedVehicles)
                assertEquals(1, statistics.arrivedVehicles)
                assertEquals(speedKmh, statistics.averageSpeedKmh, 0.0001)
            }
        }

    /**
     * Runs one second on identical straight levels with dry and changed weather.
     *
     * @param initialVehicleSpeed The initial speed of the vehicle.
     * @param maxSpeed The maximum speed of the vehicle.
     * @param acceleration The acceleration of the vehicle.
     * @param changedWeather The changed weather condition.
     * @return the dry vehicle first and the changed-weather vehicle second.
     */
    private fun simulateStraightWeatherComparison(
        initialVehicleSpeed: Double,
        maxSpeed: Double,
        acceleration: Double,
        changedWeather: WeatherCondition
    ): Pair<Vehicle, Vehicle> {
        val levels = listOf(WeatherCondition.DRY, changedWeather).map { weather ->
            buildLevel(
                initialVehicleSpeed = initialVehicleSpeed,
                maxSpeed = maxSpeed,
                acceleration = acceleration,
                weatherCondition = weather
            )
        }
        val simulations = levels.map(::Simulation)

        simulations.forEach { it.tick(0.0) }
        simulations.forEach { it.tick(1.0) }

        return levels[0].vehicles.first() to levels[1].vehicles.first()
    }

    /**
     * Tests that the simulation spawns the first vehicle at the source node at time zero.
     */
    @Test
    fun tick_atTimeZero_spawnsFirstVehicleAtSource() {
        // This guards the "due immediately" spawn behavior used when the simulation starts.
        val level = buildLevel(initialVehicleSpeed = 50.0)
        val sim = Simulation(level)

        sim.tick(0.0)

        assertEquals(1, level.vehicles.size, "first tick should spawn one vehicle")
        val vehicle = level.vehicles.first()

        val source = level.nodes.filterIsInstance<SourceNode>().first()
        assertEquals(source.outgoingStreets.first(), vehicle.currentStreet)
        assertEquals(0, vehicle.currentSegmentIndex)
        assertEquals(0.0, vehicle.distanceAlongSegment)

        // currentSpeed is stored in m/s; configuration is in km/h
        assertEquals(50.0 / 3.6, vehicle.currentSpeed, 0.0001)
    }

    /**
     * Tests that the simulation spawns a vehicle on the shortest directed route and stores route options.
     */
    @Test
    fun tick_atTimeZero_spawnsVehicleOnShortestDirectedRouteAndStoresOptions() {
        val level = buildLevelWithShortAndLongRoutes()
        val sim = Simulation(level)
        val source = level.nodes.filterIsInstance<SourceNode>().single()
        val shortFirstStreet = source.outgoingStreets[1]

        sim.tick(0.0)

        val vehicle = level.vehicles.single()
        assertSame(shortFirstStreet, vehicle.currentStreet)
        assertEquals(2, vehicle.drivingRouteOptions.size)
        assertEquals(2, vehicle.drivingRouteOptions.first().streets.size)
        assertTrue(
            vehicle.drivingRouteOptions[0].length < vehicle.drivingRouteOptions[1].length,
            "Route options should be sorted by total Euclidean street length"
        )
    }

    /**
     * Tests that the simulation uses the longer stored route when the shortest route is congested.
     */
    @Test
    fun tick_atTimeZero_usesLongerStoredRouteWhenShortestRouteIsCongested() {
        val level = buildLevelWithShortAndLongRoutes()
        val source = level.nodes.filterIsInstance<SourceNode>().single()
        val longFirstStreet = source.outgoingStreets[0]
        val shortFirstStreet = source.outgoingStreets[1]
        shortFirstStreet.utilization = Simulation.ROUTE_SWITCH_CONGESTION_THRESHOLD
        val sim = Simulation(level)

        sim.tick(0.0)

        val vehicle = level.vehicles.single()
        assertSame(longFirstStreet, vehicle.currentStreet)
        assertEquals(1, vehicle.selectedRouteIndex)
        assertEquals(2, vehicle.drivingRouteOptions.size)
    }

    /**
     * Tests that the simulation switches to a reachable alternative route when the shortest route is congested.
     */
    @Test
    fun tick_switchesToReachableAlternativeWhenUpcomingShortestStreetIsCongested() {
        val fixture = buildLevelWithSharedFirstRoute()
        val sim = Simulation(fixture.level)

        sim.tick(0.0)

        val vehicle = fixture.level.vehicles.single()
        assertSame(fixture.sharedStreet, vehicle.currentStreet)
        assertEquals(0, vehicle.selectedRouteIndex)

        fixture.shortExitStreet.utilization = Simulation.ROUTE_SWITCH_CONGESTION_THRESHOLD
        sim.tick(0.1)

        assertSame(fixture.sharedStreet, vehicle.currentStreet)
        assertEquals(1, vehicle.selectedRouteIndex)
        assertSame(fixture.detourFirstStreet, vehicle.nextStreetOnSelectedRoute())
    }

    /**
     * Tests that the simulation advances a vehicle by the current speed times delta time.
     */
    @Test
    fun tick_advancesVehicleByCurrentSpeedTimesDt() {
        // initial == max → no acceleration, so we isolate pure movement
        val level = buildLevel(initialVehicleSpeed = 50.0, maxSpeed = 50.0)
        val sim = Simulation(level)

        sim.tick(0.0)   // spawn
        sim.tick(1.0)   // 1 second elapsed

        val vehicle = level.vehicles.first()
        val expectedM = (50.0 / 3.6) * 1.0
        assertEquals(expectedM, vehicle.distanceAlongSegment, 0.0001)
    }

    /**
     * Verifies that statistics start with zero runtime values but know the resource length.
     */
    @Test
    fun statistics_beforeTicks_reportsInitialMetrics() {
        val level = buildLevel()
        val sim = Simulation(level)

        val statistics = sim.statistics()

        assertEquals(0, statistics.generatedVehicles)
        assertEquals(0, statistics.arrivedVehicles)
        assertEquals(0.0, statistics.averageSpeedKmh, 0.0001)
        assertEquals(0.0, statistics.simulationDurationSeconds, 0.0001)
        assertEquals(100.0, statistics.roadNetworkLengthMeters, 0.0001)
        assertEquals(TrafficFlowRating.STABLE, statistics.trafficFlowRating)
    }

    /**
     * Verifies generated vehicles, duration, average speed, and resource length after movement.
     */
    @Test
    fun statistics_afterTick_recordsGeneratedVehiclesDurationAverageSpeedAndResources() {
        val level = buildLevel(initialVehicleSpeed = 36.0, maxSpeed = 36.0, acceleration = 0.0)
        val sim = Simulation(level)

        sim.tick(0.0)
        sim.tick(2.0)

        val statistics = sim.statistics()

        assertEquals(1, statistics.generatedVehicles)
        assertEquals(0, statistics.arrivedVehicles)
        assertEquals(36.0, statistics.averageSpeedKmh, 0.0001)
        assertEquals(2.0, statistics.simulationDurationSeconds, 0.0001)
        assertEquals(100.0, statistics.roadNetworkLengthMeters, 0.0001)
        assertEquals(TrafficFlowRating.STABLE, statistics.trafficFlowRating)
    }

    /**
     * Verifies that arrived vehicles are counted and successful free flow is rated efficient.
     */
    @Test
    fun statistics_afterCompletion_countsArrivalsAndRatesEfficientFlow() {
        val statistics = completedStraightFlowStatistics(speedKmh = 72.0, seconds = 5)

        assertEquals(5.0, statistics.simulationDurationSeconds, 0.0001)
        assertEquals(TrafficFlowRating.EFFICIENT, statistics.trafficFlowRating)
    }

    /**
     * Verifies that low average speed prevents an efficient rating even after all vehicles arrive.
     */
    @Test
    fun statistics_slowCompletedFlowDoesNotRateEfficient() {
        val statistics = completedStraightFlowStatistics(speedKmh = 20.0, seconds = 18)

        assertEquals(TrafficFlowRating.STABLE, statistics.trafficFlowRating)
    }

    /**
     * Verifies that very low average speed is treated as overloaded traffic flow.
     */
    @Test
    fun statistics_verySlowCompletedFlowRatesOverloaded() {
        val statistics = completedStraightFlowStatistics(speedKmh = 10.0, seconds = 37)

        assertEquals(TrafficFlowRating.OVERLOADED, statistics.trafficFlowRating)
    }

    /**
     * Verifies that high street utilization is classified as overloaded.
     */
    @Test
    fun statistics_highUtilizationRatesOverloadedFlow() {
        val level = buildLevel()
        val sim = Simulation(level)

        level.streets.single().utilization = 0.9

        assertEquals(TrafficFlowRating.OVERLOADED, sim.statistics().trafficFlowRating)
    }

    /**
     * Verifies that applying a selected simulation speed changes real-time simulation progress.
     */
    @Test
    fun tick_withScaledDelta_advancesFurtherAtHigherSpeed() {
        val normalLevel = buildLevel(initialVehicleSpeed = 3.6, maxSpeed = 3.6, acceleration = 0.0)
        val fastLevel = buildLevel(initialVehicleSpeed = 3.6, maxSpeed = 3.6, acceleration = 0.0)
        val normalSim = Simulation(normalLevel)
        val fastSim = Simulation(fastLevel)

        normalSim.tick(0.0)
        fastSim.tick(0.0)
        normalSim.tick(SimulationSpeed.X1.scaledDeltaSeconds(1.0))
        fastSim.tick(SimulationSpeed.X5.scaledDeltaSeconds(1.0))

        assertEquals(1.0, normalLevel.vehicles.single().distanceAlongSegment, 0.0001)
        assertEquals(5.0, fastLevel.vehicles.single().distanceAlongSegment, 0.0001)
    }

    /**
     * Verifies that playback speed changes do not change the deterministic simulation sequence.
     */
    @Test
    fun runPlayback_equivalentSimulatedTime_isIndependentOfPlaybackChunking() {
        val normalLevel = buildZipperMergeLevel(vehiclesTop = 3, vehiclesBottom = 3, generationSpeed = 0.5)
        val fastLevel = buildZipperMergeLevel(vehiclesTop = 3, vehiclesBottom = 3, generationSpeed = 0.5)
        val normalSim = Simulation(normalLevel)
        val fastSim = Simulation(fastLevel)

        repeat(600) {
            normalSim.runPlayback(Simulation.FIXED_PLAYBACK_STEP_SECONDS)
        }
        repeat(60) {
            fastSim.runPlayback(Simulation.FIXED_PLAYBACK_STEP_SECONDS * SimulationSpeed.X10.multiplier)
        }

        assertEquivalentPlaybackState(normalLevel, fastLevel, normalSim, fastSim)
    }

    /**
     * Tests that the simulation accelerates a vehicle up to its maximum speed.
     */
    @Test
    fun tick_acceleratesUpToMaxSpeed() {
        // initial 50, max 80, acceleration 3 m/s²: after 1 s ⇒ +3 m/s, still below cap
        val level = buildLevel(initialVehicleSpeed = 50.0, maxSpeed = 80.0, acceleration = 3.0)
        val sim = Simulation(level)

        sim.tick(0.0)
        sim.tick(1.0)

        val vehicle = level.vehicles.first()
        val expectedSpeedMs = 50.0 / 3.6 + 3.0 * 1.0
        assertEquals(expectedSpeedMs, vehicle.currentSpeed, 0.0001)
    }

    /**
     * Verifies that snowy weather reduces the acceleration budget on straight roads, not only in curves.
     */
    @Test
    fun tick_snowyWeatherReducesAccelerationOnStraightStreet() {
        val (dryVehicle, snowyVehicle) = simulateStraightWeatherComparison(
            initialVehicleSpeed = 50.0,
            maxSpeed = 80.0,
            acceleration = 5.0,
            changedWeather = WeatherCondition.SNOWY
        )

        assertTrue(
            snowyVehicle.currentSpeed < dryVehicle.currentSpeed,
            "snowy weather should reduce acceleration even on a straight street"
        )
        assertEquals(
            50.0 / 3.6 + 5.0 * WeatherCondition.SNOWY.adhesionMultiplier,
            snowyVehicle.currentSpeed,
            0.0001
        )
    }

    /**
     * Verifies that wet weather reduces the braking budget on straight roads, not only in curves.
     */
    @Test
    fun tick_wetWeatherReducesBrakingOnStraightStreet() {
        val (dryVehicle, wetVehicle) = simulateStraightWeatherComparison(
            initialVehicleSpeed = 80.0,
            maxSpeed = 50.0,
            acceleration = 3.0,
            changedWeather = WeatherCondition.WET
        )

        assertTrue(
            wetVehicle.currentSpeed > dryVehicle.currentSpeed,
            "wet weather should reduce braking budget, so speed drops less in the same tick"
        )
        assertEquals(
            80.0 / 3.6 - 3.0 * WeatherCondition.WET.adhesionMultiplier,
            wetVehicle.currentSpeed,
            0.0001
        )
    }

    /**
     * Verifies that invalid acceleration or adhesion values collapse to a zero movement budget.
     */
    @Test
    fun effectiveAccelerationBudget_rejectsInvalidValues() {
        assertEquals(0.0, Simulation.effectiveAccelerationBudget(-1.0, 1.0))
        assertEquals(0.0, Simulation.effectiveAccelerationBudget(Double.NaN, 1.0))
        assertEquals(0.0, Simulation.effectiveAccelerationBudget(3.0, Double.POSITIVE_INFINITY))
    }

    /**
     * Tests that the simulation does not accelerate beyond the maximum speed.
     */
    @Test
    fun tick_doesNotAccelerateBeyondMaxSpeed() {
        // initial == max, but acceleration is positive — speed must stay capped.
        val level = buildLevel(initialVehicleSpeed = 80.0, maxSpeed = 80.0, acceleration = 5.0)
        val sim = Simulation(level)

        sim.tick(0.0)
        sim.tick(1.0)
        sim.tick(1.0)

        val vehicle = level.vehicles.first()
        val maxMs = 80.0 / 3.6
        assertEquals(maxMs, vehicle.currentSpeed, 0.0001)
    }

    /**
     * Tests that the simulation spawns a second vehicle after the generation speed seconds have passed.
     */
    @Test
    fun tick_afterGenerationSpeedSeconds_spawnsSecondVehicle() {
        /**
         * A slow constant 1 m/s (3.6 km/h, acceleration 0) leaves the entry clear for the second
         * spawn (Issue #154 no longer lets a vehicle spawn on top of a stationary leader) while the
         * first vehicle stays far from the 100 m target, so we still measure only spawn timing.
         */
        val level = buildLevel(
            generationSpeed = 30.0,
            initialVehicleSpeed = 3.6,
            maxSpeed = 3.6,
            acceleration = 0.0,
            vehiclesToSpawn = 2
        )
        val sim = Simulation(level)

        // First vehicle spawns.
        sim.tick(0.0)
        assertEquals(1, level.vehicles.size)

        //15 s elapsed, no new spawn yet.
        sim.tick(15.0)
        assertEquals(1, level.vehicles.size)

        // 30 s reached → second vehicle spawns onto the now-cleared entry.
        sim.tick(15.0)
        assertEquals(2, level.vehicles.size)
    }

    /**
     * Tests that the simulation does not spawn more vehicles after the configured spawn count is reached.
     */
    @Test
    fun tick_afterConfiguredSpawnCountReached_doesNotSpawnMoreVehicles() {
        /**
         * A slow constant 1 m/s (3.6 km/h, acceleration 0) lets the first vehicle clear the entry so
         * the second can spawn (Issue #154 forbids spawning onto an occupied entry), yet both stay
         * well short of the 100 m target. The cap at vehiclesToSpawn = 2 must still hold afterward.
         */
        val level = buildLevel(
            generationSpeed = 1.0,
            initialVehicleSpeed = 3.6,
            maxSpeed = 3.6,
            acceleration = 0.0,
            vehiclesToSpawn = 2
        )
        val sim = Simulation(level)

        sim.tick(0.0)
        repeat(10) { sim.tick(1.0) }

        assertEquals(2, level.vehicles.size, "spawn count must stop at the configured vehicle amount")
        val config = level.nodes.filterIsInstance<SourceNode>().single().configurations.single()
        assertEquals(2, config.vehiclesSpawned)
    }

    /**
     * Reproduces the LevelValidator/StreetPathFinder graph-representation divergence: LevelValidator
     * walks the object graph via Node.outgoingStreets (no path-length check), while StreetPathFinder
     * rebuilds its own adjacency and drops any street with fewer than two path points. A street with a
     * single-point path is therefore "reachable" to the validator but invisible to the router, so the
     * level reports networkState = COMPLETE while this source can never find a route. Verifies the
     * diagnostic added to VehicleSpawnManager surfaces exactly this case, once, without spawning a
     * vehicle it cannot route.
     */
    @Test
    fun tick_degenerateSinglePointStreet_logsRouteDivergenceDiagnosticAndSpawnsNothing() {
        val level = Level("DivergenceLevel", 1)
        val red = Color("Rot", Colors.RED)
        val auto = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)
        val source = SourceNode(scaledPoint(0, 0), 0, Angle.fromDegrees(0.0), listOf(SourceNodeConfiguration(red, 1.0, 50.0, auto)))
        val target = TargetNode(scaledPoint(500, 0), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(target)
        // A single-point path: Node.outgoingStreets still records this street (LevelValidator sees a
        // reachable target), but StreetPathFinder's `path.size >= 2` filter drops it entirely.
        level.addStreet(Street(source, target, listOf(scaledPoint(0, 0))))
        level.validate()
        assertEquals(NetworkState.COMPLETE, level.networkState, "the object-graph validator must consider this network complete")

        val sim = Simulation(level)
        val originalOut = System.out
        val captured = ByteArrayOutputStream()
        System.setOut(PrintStream(captured))
        try {
            repeat(5) { sim.tick(1.0) }
        } finally {
            System.setOut(originalOut)
        }

        assertTrue(level.vehicles.isEmpty(), "a route-less source must never spawn a vehicle, regardless of networkState")
        val diagnosticLines = captured.toString().lines().count { it.startsWith("[Diagnostic]") }
        assertEquals(1, diagnosticLines, "the divergence must be logged exactly once, not once per tick")
    }

    /**
     * Tests that a vehicle is removed and its counter is incremented after reaching the target.
     */
    @Test
    fun tick_vehicleReachesTarget_isRemovedAndCounterIncremented() {
        // generationSpeed huge, so only one vehicle ever spawns; long enough tick to reach target.
        val level = buildLevel(generationSpeed = 9999.0, initialVehicleSpeed = 80.0, maxSpeed = 80.0)
        val sim = Simulation(level)

        sim.tick(0.0)
        assertEquals(1, level.vehicles.size)

        // The scaled 800 px test street is 100 m; 10 s at 80 km/h is more than enough.
        sim.tick(10.0)

        assertTrue(level.vehicles.isEmpty(), "vehicle should be removed after reaching target")
        val target = level.nodes.filterIsInstance<TargetNode>().first()
        assertEquals(1, target.vehiclesArrived)
    }

    /**
     * Tests that the level completion status is set when the only source configuration reaches the target.
     */
    @Test
    fun tick_singleSourceConfigurationReachesTarget_setsLevelCompleted() {
        val level = buildLevel(generationSpeed = 9999.0, initialVehicleSpeed = 80.0, maxSpeed = 80.0)
        val sim = Simulation(level)

        assertEquals(false, sim.levelCompleted, "before any arrival, level is not completed")

        sim.tick(0.0)
        sim.tick(10.0)

        assertEquals(true, sim.levelCompleted, "after the only source configuration arrives, level is completed")
        assertEquals(true, level.completed, "completion status should be stored on the level")
    }

    /**
     * Tests that the simulation progress is reset, clearing vehicles, counters, and completion status.
     */
    @Test
    fun resetSimulationProgress_clearsVehiclesCountersAndCompletionStatus() {
        val level = buildLevel(
            generationSpeed = 9999.0,
            initialVehicleSpeed = 0.0,
            maxSpeed = 0.0,
            acceleration = 0.0,
            vehiclesToSpawn = 2
        )
        val sim = Simulation(level)

        sim.tick(0.0)
        val source = level.nodes.filterIsInstance<SourceNode>().single()
        val target = level.nodes.filterIsInstance<TargetNode>().single()
        val config = source.configurations.single()
        config.vehiclesArrived = 1
        target.vehiclesArrived = 1
        level.completed = true

        level.resetSimulationProgress()

        assertEquals(false, level.completed)
        assertTrue(level.vehicles.isEmpty(), "active vehicles from the previous run must be removed")
        assertEquals(0, source.vehiclesGenerated)
        assertEquals(0, config.vehiclesSpawned)
        assertEquals(0, config.vehiclesArrived)
        assertEquals(0, target.vehiclesArrived)
    }

    /**
     * Tests that the game state reset current level configuration to original clears runtime progress.
     */
    @Test
    fun gameStateResetCurrentLevelConfigurationToOriginal_clearsRuntimeProgress() {
        val level = buildLevel(
            generationSpeed = 9999.0,
            initialVehicleSpeed = 50.0,
            maxSpeed = 50.0,
            vehiclesToSpawn = 1
        )
        GameState.currentLevel = level
        GameState.activeSimulation = Simulation(level)
        GameState.activeSimulation?.tick(0.0)

        val source = level.nodes.filterIsInstance<SourceNode>().single()
        val config = source.configurations.single()
        val target = level.nodes.filterIsInstance<TargetNode>().single()
        source.vehiclesGenerated = 3
        config.vehiclesSpawned = 2
        config.vehiclesArrived = 1
        target.vehiclesArrived = 1
        level.completed = true

        assertTrue(GameState.resetCurrentLevelConfigurationToOriginal())

        assertEquals(false, level.completed)
        assertTrue(level.vehicles.isEmpty(), "reset must remove active vehicles")
        assertEquals(0, source.vehiclesGenerated)
        assertEquals(0, source.configurations.single().vehiclesSpawned)
        assertEquals(0, source.configurations.single().vehiclesArrived)
        assertEquals(0, target.vehiclesArrived)
        assertEquals(null, GameState.activeSimulation)
        GameState.currentLevel = null
    }

    /**
     * Recovering from a congestion abort must clear only the simulation run (active vehicles and the
     * per-source progress counters) while keeping the player's edited vehicle parameters in place,
     * instead of restoring the level's original configuration snapshot.
     */
    @Test
    fun resetSimulationRunKeepingConfiguration_keepsEditedVehicleParametersAfterCongestion() {
        val level = buildLevel(maxSpeed = 50.0, acceleration = 3.0, vehiclesToSpawn = 1)
        // Loading the level captures the original configuration snapshot (maxSpeed 50, accel 3).
        GameState.currentLevel = level

        // The player edits the vehicle parameters before running the simulation.
        val editedVehicleType = level.vehicleTypes.single()
        editedVehicleType.maxSpeed = 120.0
        editedVehicleType.acceleration = 7.5

        // A run starts and the network ends up congested, leaving runtime progress behind.
        GameState.activeSimulation = Simulation(level)
        GameState.activeSimulation?.tick(0.0)
        level.completed = true

        // Recovering from the congestion overlay must clear the run but keep the edits.
        assertTrue(GameState.resetSimulationRunKeepingConfiguration())

        val afterReset = level.vehicleTypes.single()
        assertEquals(120.0, afterReset.maxSpeed, "edited max speed must survive a congestion reset")
        assertEquals(7.5, afterReset.acceleration, "edited acceleration must survive a congestion reset")
        assertEquals(false, level.completed)
        assertTrue(level.vehicles.isEmpty(), "the finished run's vehicles must be cleared")
        assertEquals(null, GameState.activeSimulation)
        GameState.currentLevel = null
    }

    /** --------------------------------------------------------------------------------------------
     * Congestion detection (Issue #93). Covers Simulation.checkForCongestion / congestionDetected.
     * ---------------------------------------------------------------------------------------------
     */

    /**
     * Tests that a lone vehicle cruising on a straight street at its full configured speed does not report congestion.
     */
    @Test
    fun checkForCongestion_freeFlowingStraightTraffic_reportsNoCongestion() {
        /**
         * A lone vehicle cruising on a straight street at its full configured speed must never be
         * flagged: neither the average-speed criterion nor the merge-wait criterion may fire.
         */
        val level = buildLevel(initialVehicleSpeed = 50.0, maxSpeed = 50.0, vehiclesToSpawn = 1)
        val sim = Simulation(level)

        sim.tick(0.0)   // spawn at 50 km/h
        sim.tick(0.1)   // still cruising on the straight street

        assertEquals(false, sim.congestionDetected, "free-flowing traffic must not be reported as congestion")
    }

    /**
     * Tests that an empty network after a vehicle has reached its target does not report congestion.
     */
    @Test
    fun checkForCongestion_emptyNetworkAfterArrival_reportsNoCongestion() {
        /**
         * Once the only vehicle has reached its target the network is empty again; an empty network
         * can never be congested. This guards the empty-vehicle branch of checkForCongestion.
         */
        val level = buildLevel(generationSpeed = 9999.0, initialVehicleSpeed = 80.0, maxSpeed = 80.0, vehiclesToSpawn = 1)
        val sim = Simulation(level)

        sim.tick(0.0)   // spawn
        sim.tick(10.0)  // drive to the target → vehicle removed
        assertTrue(level.vehicles.isEmpty(), "precondition: the vehicle has reached its target")
        sim.tick(0.1)   // run the detection once more on an empty network

        assertEquals(false, sim.congestionDetected, "an empty network must not be reported as congestion")
    }

    /**
     * Tests that a vehicle permanently stuck behind a blocked merge reports congestion.
     */
    @Test
    fun checkForCongestion_vehiclePermanentlyStuckBehindBlockedMerge_reportsCongestion() {
        /**
         * One approach is permanently held by an immovable blocker, so the yielding vehicle can
         * never cross and is stuck at its stop line forever — a genuine, unrecoverable gridlock that
         * the simulation-abort criterion is meant to catch.
         */
        val level = buildBlockedMergeWithSingleYielderLevel()
        val sim = Simulation(level)

        sim.tick(0.0)
        // Let the yielder settle at the stop line and the merge-wait timer exceed its threshold.
        repeat(150) { sim.tick(0.1) }   // 15 s simulated

        assertEquals(true, sim.congestionDetected, "a vehicle stuck forever behind a blocked merge must be reported as congestion")
    }

    /**
     * Tests that a lone vehicle braking for a moderate curve does not report congestion.
     */
    @Test
    fun checkForCongestion_moderateCurveSlowdown_reportsNoCongestion() {
        /**
         * A lone vehicle braking for a (moderate) curve still moves well above a standstill. A
         * legitimate curve slowdown must not be mistaken for a blocked network at any point.
         */
        val level = buildLevelWithTightCurve()   // curve limit ≈ 102 km/h on a 144 km/h vehicle
        val sim = Simulation(level)

        sim.tick(0.0)
        var congestedDuringRun = false
        repeat(80) {
            sim.tick(0.1)
            if (sim.congestionDetected) congestedDuringRun = true
        }

        assertEquals(false, congestedDuringRun, "a vehicle braking for a curve must not be reported as congestion")
    }

    /**
     * Tests that a single vehicle yielding at a working merge does not report congestion.
     */
    @Test
    fun checkForCongestion_singleVehicleYieldingAtWorkingMerge_reportsNoCongestion() {
        /**
         * Two vehicles meet at a working zipper merge: one crosses, the other yields for a moment and
         * then proceeds. Both reach the target, so this is normal traffic, NOT a blocked network.
         * The simulation must not report congestion just because one vehicle briefly waits its turn.
         */
        val level = buildZipperMergeLevel(vehiclesTop = 1, vehiclesBottom = 1, generationSpeed = 9999.0)
        val sim = Simulation(level)

        sim.tick(0.0)
        var congestedDuringRun = false
        repeat(200) {
            sim.tick(0.1)
            if (sim.congestionDetected) congestedDuringRun = true
        }

        assertTrue(level.vehicles.isEmpty(), "precondition: both vehicles eventually pass the merge and arrive")
        assertEquals(false, congestedDuringRun, "a brief, legitimate yield at a working merge must not be reported as congestion")
    }

    /**
     * Tests that congestion is detected when average speed drops below 5% of initial speed.
     * This test verifies the changed threshold from 0.3 to 0.05.
     */
    @Test
    fun checkForCongestion_averageSpeedBelowFivePercent_reportsCongestion() {
        /**
         * Multiple vehicles are spawned but forced to move very slowly (simulating heavy congestion).
         * The average speed should drop below 5% of the initial configured speed, triggering congestion detection.
         */
        val level = buildLevel(
            initialVehicleSpeed = 50.0,
            maxSpeed = 50.0,
            acceleration = 0.0,
            generationSpeed = 0.1,  // Faster generation to get more vehicles quickly
            vehiclesToSpawn = 5
        )
        val sim = Simulation(level)

        // Spawn all vehicles - use enough time to spawn all 5 vehicles
        sim.tick(0.0)
        repeat(50) { sim.tick(0.1) }  // 5 seconds total, enough for 5 vehicles at 0.1s generation
        
        // Verify we have multiple vehicles
        assertTrue(level.vehicles.size >= 3, "should have at least 3 vehicles for meaningful average speed calculation (actual: ${level.vehicles.size})")

        // Force vehicles to move very slowly (simulating congestion)
        for (vehicle in level.vehicles) {
            vehicle.currentSpeed = 0.5  // Very slow speed in m/s (1.8 km/h)
        }

        // Calculate expected threshold: 5% of initial speed
        val initialSpeedMs = 50.0 / 3.6
        val fivePercentThreshold = 0.05 * initialSpeedMs
        val avgCurrentSpeed = level.vehicles.sumOf { it.currentSpeed } / level.vehicles.size

        assertTrue(avgCurrentSpeed < fivePercentThreshold,
            "Average speed should be below 5% threshold for this test to be valid (avg: $avgCurrentSpeed, threshold: $fivePercentThreshold)")

        // A single slow tick must not abort the run: the low average has to persist first
        // (a lone vehicle waiting one tick at a stop line would otherwise instantly end the run).
        sim.tick(0.1)
        assertEquals(false, sim.congestionDetected,
            "a momentary average-speed dip must not be reported as congestion")

        // Keep the average low beyond the persistence window; the slow speeds are re-pinned each
        // tick so leader-following cannot raise them in between.
        repeat(51) {
            for (vehicle in level.vehicles) {
                vehicle.currentSpeed = 0.5
            }
            sim.tick(0.1)
        }

        assertEquals(true, sim.congestionDetected,
            "congestion should be detected when average speed stays below 5% of initial speed beyond the persistence window")
    }

    /**
     * Tests that congestion is NOT detected when average speed is at or above 5% of initial speed.
     * This test verifies the boundary condition for the changed threshold.
     */
    @Test
    fun checkForCongestion_averageSpeedAtOrAboveTenPercent_reportsNoCongestion() {
        /**
         * Multiple vehicles moving at 11% of initial speed should not trigger congestion detection,
         * as the threshold is set at 10%.
         */
        val level = buildLevel(
            initialVehicleSpeed = 50.0,
            maxSpeed = 50.0,
            acceleration = 0.0,
            generationSpeed = 0.1,  // Faster generation to get more vehicles quickly
            vehiclesToSpawn = 5
        )
        val sim = Simulation(level)

        // Spawn all vehicles - use enough time to spawn all 5 vehicles
        sim.tick(0.0)
        repeat(50) { sim.tick(0.1) }  // 5 seconds total, enough for 5 vehicles at 0.1s generation
        
        // Verify we have multiple vehicles
        assertTrue(level.vehicles.size >= 3, "should have at least 3 vehicles for meaningful average speed calculation (actual: ${level.vehicles.size})")

        // Set vehicles to move at 6% of initial speed (above threshold)
        val initialSpeedMs = 50.0 / 3.6
        val elevenPercentSpeed = 0.11 * initialSpeedMs
        for (vehicle in level.vehicles) {
            vehicle.currentSpeed = elevenPercentSpeed
        }

        // Run congestion check
        sim.tick(0.1)

        // Calculate expected threshold: 5% of initial speed
        val fivePercentThreshold = 0.1 * initialSpeedMs
        val avgCurrentSpeed = level.vehicles.sumOf { it.currentSpeed } / level.vehicles.size
        
        assertTrue(avgCurrentSpeed >= fivePercentThreshold, 
            "Average speed should be at or above 10% threshold for this test to be valid (avg: $avgCurrentSpeed, threshold: $fivePercentThreshold)")
        assertEquals(false, sim.congestionDetected, 
            "congestion should NOT be detected when average speed is at or above 10% of initial speed")
    }

    /**
     * Tests that the level completion flag is set when all configured vehicles have already arrived.
     */
    @Test
    fun init_allConfiguredVehiclesAlreadyArrived_marksLevelCompleted() {
        val level = buildLevel(generationSpeed = 9999.0, initialVehicleSpeed = 80.0, maxSpeed = 80.0)
        val config = level.nodes.filterIsInstance<SourceNode>().single().configurations.single()
        config.vehiclesSpawned = 1
        config.vehiclesArrived = 1

        val sim = Simulation(level)

        assertEquals(true, sim.levelCompleted, "loaded progress counters should restore completion state")
        assertEquals(true, level.completed)
    }

    /**
     * Tests that the level completion flag is not set when only one of multiple source configurations reaches a target.
     */
    @Test
    fun tick_oneOfMultipleSourceConfigurationsReachesTarget_doesNotCompleteLevel() {
        val level = buildLevelWithFastAndSlowConfigurations()
        val sim = Simulation(level)

        sim.tick(0.0)
        sim.tick(10.0)

        val target = level.nodes.filterIsInstance<TargetNode>().single()
        assertEquals(1, target.vehiclesArrived, "only the fast source configuration should have arrived")
        assertEquals(
            false,
            sim.levelCompleted,
            "level should wait until every source configuration has at least one target arrival"
        )
    }

    /**
     * Tests that the level completion flag is set when all source configurations have reached a target.
     */
    @Test
    fun tick_allSourceConfigurationsReachTarget_setsLevelCompleted() {
        val level = buildLevelWithFastAndSlowConfigurations()
        val sim = Simulation(level)

        sim.tick(0.0)
        sim.tick(10.0)
        assertEquals(false, sim.levelCompleted, "one missing source configuration should keep the level open")

        sim.tick(80.0)

        val target = level.nodes.filterIsInstance<TargetNode>().single()
        assertTrue(target.vehiclesArrived >= 2, "both source configurations should have reached the target")
        assertEquals(true, sim.levelCompleted)
    }

    /**
     * Tests that the level completion flag remains true even after more vehicles arrive.
     */
    @Test
    fun tick_levelCompletedRemainsTrue_evenAfterMoreVehiclesArrive() {
        // Once set, the I1 completion flag is monotonic and should not be reset by later ticks.
        val level = buildLevel(
            generationSpeed = 5.0,
            initialVehicleSpeed = 80.0,
            maxSpeed = 80.0,
            vehiclesToSpawn = 2
        )
        val sim = Simulation(level)

        // Simulate enough time for several spawns and arrivals
        sim.tick(0.0)
        repeat(10) { sim.tick(2.0) }   // 20 s total

        assertTrue(sim.levelCompleted)
        val target = level.nodes.filterIsInstance<TargetNode>().first()
        assertTrue(target.vehiclesArrived >= 2, "expected at least 2 arrivals")
    }

    /**
     * Tests that the simulation does nothing when the network is incomplete.
     */
    @Test
    fun tick_doesNothingWhenNetworkIsNotComplete() {
        // Build a level with source + target but no street → INCOMPLETE network
        val level = Level("Incomplete", 1)
        val red = Color("Rot", Colors.RED)
        val auto = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)
        val cfg = SourceNodeConfiguration(red, 30.0, 50.0, auto)
        val source = SourceNode(scaledPoint(0, 0), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val target = TargetNode(scaledPoint(800, 0), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(target)
        level.validate()
        assertTrue(level.networkState != NetworkState.COMPLETE)

        val sim = Simulation(level)
        sim.tick(0.0)
        sim.tick(60.0)

        assertEquals(0, level.vehicles.size, "no vehicles should spawn on an incomplete network")
        assertEquals(false, sim.levelCompleted)
    }

    /**
     * Tests that the simulation instance is retained when the pause menu is opened and resumed.
     */
    @Test
    fun tick_pauseMenuOpenedAndResumed_retainsExactSameSimulationInstance() {
        // 1. GIVEN: A valid initialized level set up within the global GameState
        val level = buildLevel(initialVehicleSpeed = 50.0)
        GameState.currentLevel = level
        GameState.simulationState = SimulationState.CONSTRUCTION
        GameState.activeSimulation = null // Ensure a clean initialization state

        // 2. WHEN: The simulation is started for the first time
        GameState.activeSimulation = Simulation(level)
        GameState.simulationState = SimulationState.SIMULATION

        val firstSimulationInstance = GameState.activeSimulation
        assertNotNull(firstSimulationInstance, "Simulation instance must be created when started.")

        // Run a simulation tick to progress time and register entity updates
        GameState.activeSimulation?.tick(0.5)
        assertEquals(1, level.vehicles.size, "A vehicle should have spawned on the first tick.")

        // 3. WHEN: The user opens the pause menu (e.g., clicking the gear icon)
        GameState.simulationState = SimulationState.PAUSED

        /**
         * The UI tier now safely unloads the old LevelScene object.
         * Later the user returns from the pause menu, prompting the UI to build a brand new LevelScene instance.
         * When clicking 'Start' again to resume, the initialization code triggers once more:
         */
        if (GameState.activeSimulation == null) {
            GameState.activeSimulation = Simulation(level)
        }
        GameState.simulationState = SimulationState.SIMULATION

        // 4. THEN: Verify that the persistent state has been correctly retained
        val secondSimulationInstance = GameState.activeSimulation
        assertNotNull(secondSimulationInstance, "Active simulation must not be null after resuming.")

        // CRITICAL ADHERENCE CHECK: Verify that no duplicate or newly generated Simulation object overrode the original
        assertSame(
            firstSimulationInstance,
            secondSimulationInstance,
            "Error: A new simulation instance was created instead of safely resuming the existing one!"
        )

        // Verify that the global engine state is accurately running again
        assertEquals(SimulationState.SIMULATION, GameState.simulationState)
        assertEquals(1, level.vehicles.size, "The spawned vehicle state must persist across pause steps.")
    }


    /**
     * Tests that the vehicle speed does not drop below the maximum speed on a straight street.
     */
    @Test
    fun tick_onStraightStreet_doesNotImposeCurveLimit() {
        /**
         * A 2-point polyline (no inner waypoints) must let the vehicle hold V_max
         * even after the curve-speed logic is in place.
         */
        val level = buildLevel(initialVehicleSpeed = 80.0, maxSpeed = 80.0, acceleration = 3.0)
        val sim = Simulation(level)
        sim.tick(0.0)
        val vehicle = level.vehicles.first()
        val vMax = 80.0 / 3.6

        repeat(5) { sim.tick(0.1) }

        assertEquals(vMax, vehicle.currentSpeed, 1e-9, "vehicle on a straight street should stay at V_max")
    }

    /**
     * Tests that the vehicle speed drops below the maximum speed before the inner waypoint on a curved polyline.
     */
    @Test
    fun tick_onCurvedPolyline_vehicleSpeedDropsBelowMaxBeforeInnerWaypoint() {
        /**
         * A right-angled inner waypoint forces a curve limit well below V_max; the
         * vehicle must brake during its approach.
         */
        val level = buildLevelWithTightCurve()
        val sim = Simulation(level)
        val vMax = 144.0 / 3.6
        sim.tick(0.0)
        val vehicle = level.vehicles.first()
        assertEquals(vMax, vehicle.currentSpeed, 1e-9, "vehicle starts at V_max")

        // 4.5 s of small ticks brings the vehicle into the braking zone before the corner.
        repeat(45) { sim.tick(0.1) }

        assertTrue(
            vehicle.currentSpeed < vMax - 1.0,
            "vehicle must brake to satisfy the curve limit before the inner waypoint; current = ${vehicle.currentSpeed} m/s"
        )
    }

    /**
     * Verifies that snowy weather lowers the curve speed limit on the same curved road.
     */
    @Test
    fun curveSpeed_snowyWeatherLowersLimitOnSameCurvedRoad() {
        val dryLevel = buildLevelWithTightCurve().apply {
            weatherCondition = WeatherCondition.DRY
        }
        val snowyLevel = buildLevelWithTightCurve().apply {
            weatherCondition = WeatherCondition.SNOWY
        }
        val path = dryLevel.streets.first().path
        val vehicleType = dryLevel.nodes
            .filterIsInstance<SourceNode>()
            .single()
            .configurations
            .single()
            .vehicleType

        val dryLimit = CurveSpeed.maxSpeedAtWaypoint(
            path[0], path[1], path[2],
            vehicleType, dryLevel.effectiveAdhesionCoefficientModifier()
        )
        val snowyLimit = CurveSpeed.maxSpeedAtWaypoint(
            path[0], path[1], path[2],
            vehicleType, snowyLevel.effectiveAdhesionCoefficientModifier()
        )

        assertTrue(
            snowyLimit < dryLimit,
            "snowy weather should lower adhesion and therefore lower the curve speed limit"
        )
    }

    /**
     * Tests that the vehicle re-accelerates after passing the curve waypoint.
     */
    @Test
    fun tick_onCurvedPolyline_vehicleReAcceleratesAfterPassingInnerWaypoint() {
        /**
         * After clearing the inner waypoint the vehicle should pick up speed again
         * on the following straight segment.
         */
        val level = buildLevelWithTightCurve()
        val sim = Simulation(level)
        sim.tick(0.0)
        val vehicle = level.vehicles.first()

        // 6 s puts the vehicle just past the corner without reaching the target yet.
        repeat(60) { sim.tick(0.1) }
        assertTrue(level.vehicles.isNotEmpty(), "vehicle must still be in the simulation at 6 s")
        val speedAfterCurve = vehicle.currentSpeed

        repeat(3) { sim.tick(0.1) }

        assertTrue(
            vehicle.currentSpeed > speedAfterCurve,
            "vehicle should re-accelerate after passing the curve waypoint (before: $speedAfterCurve, after: ${vehicle.currentSpeed})"
        )
    }

    /**
     * Tests that the vehicle brakes on the previous street when the next street starts with a tight curve.
     */
    @Test
    fun tick_brakesAlreadyOnPreviousStreet_whenNextStreetStartsWithTightCurve() {
        /**
         * Two streets joined at a ConnectionNode. The first street is straight; the
         * second one starts with a tight right-angled inner waypoint. The vehicle
         * must brake on the *first* street so it can still satisfy the curve limit
         * on the second street.
         */
        val level = buildTwoStreetLevelWithCurveOnSecondStreet()
        val sim = Simulation(level)
        val vMax = 144.0 / 3.6
        sim.tick(0.0)
        val vehicle = level.vehicles.first()
        val firstStreet = vehicle.currentStreet
        assertEquals(vMax, vehicle.currentSpeed, 1e-9, "vehicle starts at V_max")

        repeat(40) { sim.tick(0.1) }

        assertSame(firstStreet, vehicle.currentStreet, "vehicle must still be on the first street at 4 s")
        assertTrue(
            vehicle.currentSpeed < vMax - 1.0,
            "vehicle must already brake on the first street because the next one starts with a tight curve; current = ${vehicle.currentSpeed} m/s"
        )
    }

    /**
     * Tests that the tick function brakes the vehicle before a sharp connection node between two straight streets.
     */
    @Test
    fun tick_brakesBeforeSharpConnectionNode_betweenTwoStraightStreets() {
        /**
         * Two 2-point streets joined at a 90° connection node. Neither street has
         * any inner waypoint of its own — the curve limit must come from the
         * triangle formed by the last point of street A, the connection node
         * itself, and the first point of street B.
         */
        val level = buildTwoStreetLevelWithSharpConnectionAngle()
        val sim = Simulation(level)
        val vMax = 144.0 / 3.6
        sim.tick(0.0)
        val vehicle = level.vehicles.first()
        val firstStreet = vehicle.currentStreet
        assertEquals(vMax, vehicle.currentSpeed, 1e-9, "vehicle starts at V_max")

        repeat(40) { sim.tick(0.1) }

        assertSame(firstStreet, vehicle.currentStreet, "vehicle must still be on the first street at 4 s")
        assertTrue(
            vehicle.currentSpeed < vMax - 1.0,
            "vehicle must brake for the sharp connection-node angle; current = ${vehicle.currentSpeed} m/s"
        )
    }

    /**
     * Tests that the merge node releases at most one vehicle onto the outgoing street per tick.
     */
    @Test
    fun tick_mergeNode_releasesAtMostOneVehicleOntoOutgoingStreetPerTick() {
        /**
         * Two incoming streets feed one outgoing street at a ConnectionNode. Both
         * sources spawn a single vehicle simultaneously, so without zipper control
         * both would enter the outgoing street in the same tick. The merge must
         * serialize them: at most one new vehicle per tick may join the outgoing.
        */
        val level = buildZipperMergeLevel(vehiclesTop = 1, vehiclesBottom = 1, generationSpeed = 9999.0)
        val sim = Simulation(level)
        val outgoing = level.streets.single { it.startNode is ConnectionNode }

        sim.tick(0.0)
        assertEquals(2, level.vehicles.size, "one vehicle per source should spawn on the first tick")

        val seenOnOutgoing = mutableSetOf<Vehicle>()
        var maxEntriesInOneTick = 0
        repeat(400) {
            sim.tick(0.1)
            val newlyEntered = level.vehicles.filter { it.currentStreet === outgoing && it !in seenOnOutgoing }
            seenOnOutgoing.addAll(newlyEntered)
            if (newlyEntered.size > maxEntriesInOneTick) maxEntriesInOneTick = newlyEntered.size
        }

        assertEquals(1, maxEntriesInOneTick, "a merge must release at most one vehicle onto the outgoing street per tick")
        assertEquals(2, seenOnOutgoing.size, "both vehicles should eventually pass the merge")
    }

    /**
     * Tests that both approaches drain under a two-sided backlog at the merge.
     */
    @Test
    fun tick_mergeNode_bothApproachesDrainUnderTwoSidedBacklog() {
        /**
         * Both approaches keep a backlog of vehicles queued at the merge at the same time.
         * The merge must serialize them and serve both approaches so that every vehicle from
         * each side passes the junction — no approach is starved. Strict per-vehicle alternation
         * is intentionally NOT asserted: the current right-of-way implementation may release
         * several vehicles from one approach before switching, which is acceptable as long as
         * both approaches fully drain. (Single-vehicle-per-tick serialization is covered by
         * tick_mergeNode_releasesAtMostOneVehicleOntoOutgoingStreetPerTick.)
         */
        val level = buildZipperMergeLevel(vehiclesTop = 6, vehiclesBottom = 6, generationSpeed = 0.1)
        val sim = Simulation(level)
        val outgoing = level.streets.single { it.startNode is ConnectionNode }
        val topSource = level.nodes.filterIsInstance<SourceNode>().single { it.position == scaledPoint(0, 0) }
        val bottomSource = level.nodes.filterIsInstance<SourceNode>().single { it.position == scaledPoint(0, 400) }

        val seenOnOutgoing = mutableSetOf<Vehicle>()
        val entryOrder = mutableListOf<SourceNode>()
        sim.tick(0.0)
        repeat(600) {
            sim.tick(0.1)
            for (v in level.vehicles) {
                if (v.currentStreet === outgoing && seenOnOutgoing.add(v)) entryOrder.add(v.sourceNode)
            }
        }

        assertEquals(12, entryOrder.size, "all vehicles from both approaches should pass the merge")
        assertEquals(6, entryOrder.count { it === topSource }, "every vehicle from the top approach should pass the merge")
        assertEquals(6, entryOrder.count { it === bottomSource }, "every vehicle from the bottom approach should pass the merge")
    }

    /**
     * Tests that a busy approach is not starved by an empty approach at the merge.
     */
    @Test
    fun tick_mergeNode_busyApproachIsNotStarvedByAnEmptyApproach() {
        /**
         * One approach has a long backlog, the other sends a single vehicle and then runs dry.
         * No vehicle may wait forever: every vehicle from the busy approach must still reach the
         * target after the quiet approach is empty.
         */
        val level = buildZipperMergeLevel(vehiclesTop = 5, vehiclesBottom = 1, generationSpeed = 0.1)
        val sim = Simulation(level)
        val target = level.nodes.filterIsInstance<TargetNode>().single()

        sim.tick(0.0)
        repeat(600) { sim.tick(0.1) }

        assertEquals(6, target.vehiclesArrived, "every vehicle from both approaches must eventually arrive")
        assertTrue(level.vehicles.isEmpty(), "no vehicle may be left waiting at the merge")
        assertTrue(sim.levelCompleted, "all configured vehicles arrived → level completed")
    }

    /**
     * Tests that a ConnectionNode with a single incoming street acts as a pass-through.
     */
    @Test
    fun tick_singleIncomingConnectionNode_passesThroughWithoutWaiting() {
        /**
         * A ConnectionNode with a single incoming street is a plain pass-through, not a merge:
         * the vehicle must continue onto the next street and reach the target without being held
         * back by the zipper rule (regression guard for the size >= 2 merge trigger).
        */
        val level = buildTwoStreetLevelWithSharpConnectionAngle()
        val sim = Simulation(level)
        val target = level.nodes.filterIsInstance<TargetNode>().single()

        sim.tick(0.0)
        repeat(400) { sim.tick(0.1) }

        assertEquals(1, target.vehiclesArrived, "vehicle should pass the single-incoming connection node and arrive")
        assertTrue(level.vehicles.isEmpty())
    }

    /**
     * Tests that the tick function serves a free approach without braking to a stop.
     */
    @Test
    fun tick_mergeNode_freeApproachPassesWithoutBrakingToAStop() {
        /**
         * A merge ConnectionNode (two incoming streets) where only ONE approach ever carries
         * traffic. With the junction free the vehicle must drive straight through the merge
         * without stopping; the zipper rule may only slow a vehicle for genuine contention.
         */
        val level = buildStraightSingleFedMergeLevel()
        val sim = Simulation(level)
        val target = level.nodes.filterIsInstance<TargetNode>().single()
        val cruiseMs = 40.0 / 3.6

        sim.tick(0.0)
        assertEquals(1, level.vehicles.size, "the fed approach should spawn one vehicle")
        val vehicle = level.vehicles.single()

        var minSpeedOnRoad = Double.MAX_VALUE
        repeat(400) {
            sim.tick(0.1)
            if (vehicle in level.vehicles) minSpeedOnRoad = minOf(minSpeedOnRoad, vehicle.currentSpeed)
        }

        assertEquals(1, target.vehiclesArrived, "the vehicle should pass the free merge and reach the target")
        assertTrue(
            minSpeedOnRoad > cruiseMs - 1.0,
            "a vehicle crossing a free merge must not brake to a stop; min speed was $minSpeedOnRoad m/s"
        )
    }

    /**
     * Tests that the tick function serves the closest approach without idling at the merge node.
     */
    @Test
    fun tick_mergeNode_freeMergeServesClosestApproachWithoutIdling() {
        /**
         * Two approaches are active, but one vehicle is much closer to the free merge. The merge
         * must not reserve itself for the farther round-robin approach while the closer vehicle
         * stops at an otherwise free junction.
         */
        val level = buildUnevenApproachMergeLevel()
        val sim = Simulation(level)
        val merge = level.nodes.filterIsInstance<ConnectionNode>().single { it.outgoingStreets.isNotEmpty() }
        val outgoing = merge.outgoingStreets.single()
        val nearSource = level.nodes
            .filterIsInstance<SourceNode>()
            .single { it.position == scaledPoint(900, 500) }
        val farSource = level.nodes
            .filterIsInstance<SourceNode>()
            .single { it.position == scaledPoint(100, 300) }

        sim.tick(0.0)

        var nearEnteredAt: Int? = null
        var farEnteredAt: Int? = null
        var nearStoppedBeforeEntry = false
        repeat(150) { step ->
            sim.tick(0.1)
            val nearVehicle = level.vehicles.firstOrNull { it.sourceNode === nearSource }
            val farVehicle = level.vehicles.firstOrNull { it.sourceNode === farSource }
            if (nearEnteredAt == null && nearVehicle?.currentStreet === outgoing) nearEnteredAt = step
            if (farEnteredAt == null && farVehicle?.currentStreet === outgoing) farEnteredAt = step
            if (
                nearEnteredAt == null &&
                nearVehicle != null &&
                nearVehicle.currentStreet?.endNode === merge &&
                nearVehicle.currentSpeed < 0.1
            ) {
                nearStoppedBeforeEntry = true
            }
        }

        val nearEntry = nearEnteredAt
        val farEntry = farEnteredAt
        assertTrue(nearEntry != null, "the closer approach should enter the free merge")
        assertTrue(!nearStoppedBeforeEntry, "the closer vehicle must not stop while the merge is free")
        assertTrue(farEntry == null || nearEntry < farEntry, "the closer approach should be served before the farther one")
    }

    /**
     * Bug 1 regression: two incoming streets meet the merge almost parallel (the diagonal-into-
     * straight join from the "Intersection and Turn" level). The per-street 2 m rule cannot see
     * across approaches, so without the 2D merge-proximity clamp the two queues render on top of each
     * other right at the node. Verifies that no two vehicles on different approaches ever visually
     * overlap (centre-to-centre below half of both lengths) and that traffic still fully drains.
     */
    @Test
    fun tick_sharpAngleMerge_vehiclesFromDifferentApproachesNeverOverlap() {
        val level = buildSharpAngleMergeLevel()
        val sim = Simulation(level)
        val incoming = level.nodes.filterIsInstance<ConnectionNode>().single().incomingStreets
        val target = level.nodes.filterIsInstance<TargetNode>().single()

        sim.tick(0.0)

        var minCrossApproachClearanceM = Double.MAX_VALUE
        repeat(600) {
            sim.tick(0.1)
            val onApproaches = level.vehicles.filter { it.currentStreet in incoming }
            minCrossApproachClearanceM = minOf(minCrossApproachClearanceM, minPairwiseClearanceM(onApproaches))
        }

        assertTrue(
            minCrossApproachClearanceM >= -0.05,
            "vehicles on different approaches of a sharp-angle merge must not visually overlap; min clearance was $minCrossApproachClearanceM m"
        )
        assertEquals(
            6,
            target.vehiclesArrived,
            "every vehicle must still pass the sharp-angle merge (no deadlock)"
        )
    }

    /**
     * Bug 2 regression: a straight road split into segments by connection nodes, with a slow leader
     * so faster vehicles queue and "nachrücken" through the intersections. Verifies that the
     * minimum-distance gap carries across the nodes — no two vehicles ever visually overlap — and
     * that every vehicle still reaches the target (no deadlock).
     */
    @Test
    fun tick_throughRoadIntersections_queueDoesNotOverlapWhileAdvancingAcrossNodes() {
        val level = buildThroughRoadWithIntersectionsLevel()
        val sim = Simulation(level)
        val target = level.nodes.filterIsInstance<TargetNode>().single()

        sim.tick(0.0)

        var minClearanceM = Double.MAX_VALUE
        repeat(1500) {
            sim.tick(0.1)
            minClearanceM = minOf(minClearanceM, minPairwiseClearanceM(level.vehicles))
        }

        assertTrue(
            minClearanceM >= -0.05,
            "vehicles nachrücken across intersection nodes must not overlap; min clearance was $minClearanceM m"
        )
        assertEquals(5, target.vehiclesArrived, "every vehicle must still cross all intersections and arrive")
    }

    /**
     * Tests that the tick function holds the yielding vehicle shortly before the merge node.
     */
    @Test
    fun tick_mergeNode_yieldingVehicleHoldsShortlyBeforeNode() {
        /**
         * One approach has to yield while the other occupies the crossing. A yielding vehicle must
         * come to rest with its front at the shared stop-line offset (STOP_LINE_FRONT_OFFSET_M)
         * before the connection node, never on the node itself, so it does not block the junction
         * it is waiting to enter. The simulation tracks the rendered centre and translates that
         * shared front line by half the vehicle length.
        */
        val level = buildBlockedMergeWithSingleYielderLevel()
        val sim = Simulation(level)
        val incoming = level.nodes.filterIsInstance<ConnectionNode>().single().incomingStreets
        val yielder = level.nodes
            .filterIsInstance<SourceNode>()
            .single { it.position == scaledPoint(300, 500) }

        sim.tick(0.0)

        var heldDistanceM = Double.NaN
        var heldFrontDistanceM = Double.NaN
        var expectedDistanceM = Double.NaN
        var heldInfo = ""
        for (step in 0 until 300) {
            sim.tick(0.1)
            val held = level.vehicles
                .firstOrNull { it.sourceNode === yielder && it.currentStreet in incoming && it.currentSpeed < 0.1 }
            if (held != null) {
                heldDistanceM = remainingDistanceToNodeM(held)
                heldFrontDistanceM = frontDistanceToNodeM(held)
                expectedDistanceM = expectedMergeStopLineDistanceM(held)
                heldInfo =
                    "step=$step type=${held.type.name} seg=${held.currentSegmentIndex} off=${held.distanceAlongSegment} speed=${held.currentSpeed}"
                if (heldFrontDistanceM in 0.65..0.75) break
            }
        }

        assertTrue(!heldDistanceM.isNaN(), "expected at least one vehicle to be held at the merge stop line")
        assertTrue(!heldFrontDistanceM.isNaN(), "expected to observe the yielding vehicle's front distance")
        assertTrue(!expectedDistanceM.isNaN(), "expected to observe the yielder's vehicle type")
        assertEquals(
            expectedDistanceM,
            heldDistanceM,
            0.05,
            "a yielding vehicle centre must stop at the length-aware stop line; held at $heldDistanceM m [$heldInfo]"
        )
        assertEquals(
            Simulation.STOP_LINE_FRONT_OFFSET_M,
            heldFrontDistanceM,
            0.05,
            "a yielding vehicle front must stop at the shared stop-line offset before the node; front held at $heldFrontDistanceM m [$heldInfo]"
        )
    }

    /**
     * Tests that the tick function keeps the yielding vehicle at the stop line with coarse ticks.
     */
    @Test
    fun tick_mergeNode_yieldingVehicleWithCoarseTickStaysAtStopLine() {
        val level = buildBlockedMergeWithSingleYielderLevel()
        val sim = Simulation(level)
        val incoming = level.nodes.filterIsInstance<ConnectionNode>().single().incomingStreets
        val yielder = level.nodes
            .filterIsInstance<SourceNode>()
            .single { it.position == scaledPoint(300, 500) }

        sim.tick(0.0)

        var heldDistanceM = Double.NaN
        var heldFrontDistanceM = Double.NaN
        var expectedDistanceM = Double.NaN
        var ticks = 0
        while (ticks < 80) {
            sim.tick(0.5)
            val vehicle = level.vehicles.firstOrNull { it.sourceNode === yielder && it.currentStreet in incoming }
            if (vehicle != null && vehicle.currentSpeed < 0.1) {
                heldDistanceM = remainingDistanceToNodeM(vehicle)
                heldFrontDistanceM = frontDistanceToNodeM(vehicle)
                expectedDistanceM = expectedMergeStopLineDistanceM(vehicle)
                if (heldFrontDistanceM in 0.65..0.75) break
            }
            ticks++
        }

        assertTrue(!heldDistanceM.isNaN(), "expected the yielding vehicle to stop while the crossing is occupied")
        assertTrue(!heldFrontDistanceM.isNaN(), "expected to observe the yielding vehicle's front distance")
        assertTrue(!expectedDistanceM.isNaN(), "expected to observe the yielder's vehicle type")
        assertEquals(
            expectedDistanceM,
            heldDistanceM,
            0.05,
            "even with coarse ticks, a yielding vehicle centre must stop at the length-aware stop line; held at $heldDistanceM m"
        )
        assertEquals(
            Simulation.STOP_LINE_FRONT_OFFSET_M,
            heldFrontDistanceM,
            0.05,
            "even with coarse ticks, a yielding vehicle front must stop at the shared stop-line offset before the node; front held at $heldFrontDistanceM m"
        )
    }

    /**
     * Tests that the tick function brakes the yielding vehicle smoothly before the stop line.
     */
    @Test
    fun tick_mergeNode_yieldingVehicleBrakesSmoothlyBeforeStopLine() {
        /**
         * A merge with one permanently blocked approach and one normal approach, so the second
         * vehicle queues behind the first (stop-line-blocked) vehicle on the merge's shared outgoing
         * street. The follower should never slam the brakes at the last second: instead, it should
         * start braking early enough to coast smoothly to the stop line with the front at
         * STOP_LINE_FRONT_OFFSET_M.
         */
        val level = buildBlockedMergeWithSingleYielderLevel()
        val sim = Simulation(level)
        val incoming = level.nodes.filterIsInstance<ConnectionNode>().single().incomingStreets
        val yielder = level.nodes
            .filterIsInstance<SourceNode>()
            .single { it.position == scaledPoint(300, 500) }

        sim.tick(0.0)

        val maxSpeedMs = yielder.configurations.single().vehicleType.maxSpeed * Simulation.KMH_TO_MS
        var rollingBrakeObserved = false
        repeat(200) {
            sim.tick(0.1)
            val vehicle = level.vehicles.firstOrNull { it.sourceNode === yielder && it.currentStreet in incoming }
            if (
                vehicle != null &&
                frontDistanceToNodeM(vehicle) > Simulation.STOP_LINE_FRONT_OFFSET_M + 1.0 &&
                vehicle.currentSpeed in 0.1..(maxSpeedMs - 0.1)
            ) {
                rollingBrakeObserved = true
            }
        }

        assertTrue(
            rollingBrakeObserved,
            "a yielding vehicle should brake while still rolling before the stop line, not only clamp to 0 at the line"
        )
    }

    /**
     * Tests that the tick function uses the same front stop line offset for all vehicle types.
     */
    @Test
    fun tick_mergeNode_vehicleTypesUseSharedFrontStopLine() {
        val motorcycle = VehicleType("Motorrad", 50.0, 2.0, 0.7, 3.0)
        val car = VehicleType("Auto", 50.0, 4.0, 0.7, 3.0)
        val truck = VehicleType("LKW", 50.0, 6.0, 0.7, 3.0)

        assertEquals(Simulation.STOP_LINE_FRONT_OFFSET_M, stoppedYielderFrontDistanceM(motorcycle), 0.05)
        assertEquals(Simulation.STOP_LINE_FRONT_OFFSET_M, stoppedYielderFrontDistanceM(car), 0.05)
        assertEquals(Simulation.STOP_LINE_FRONT_OFFSET_M, stoppedYielderFrontDistanceM(truck), 0.05)
    }

    /**
     * The yielding approach must still be served (no starvation), but the minimum-distance following
     * gap now carries across the node: the freshly released vehicle queues behind the vehicle that
     * crossed before it instead of being dropped on top of it. Replaces the earlier "released within
     * 0.5 m clearance" expectation, which encoded the very overlap the cross-node following rule fixes.
     */
    @Test
    fun tick_mergeNode_releasesNextApproachWithoutOverlappingPreviousVehicle() {
        val level = buildOccupiedMergeWithSingleYielderLevel()
        val sim = Simulation(level)
        val merge = level.nodes.filterIsInstance<ConnectionNode>().single()
        val outgoing = merge.outgoingStreets.single()
        val yielder = level.nodes
            .filterIsInstance<SourceNode>()
            .single { it.position == scaledPoint(300, 500) }

        sim.tick(0.0)

        var occupantEntered = false
        var yielderEntered = false
        var minClearGapM = Double.MAX_VALUE
        repeat(400) {
            sim.tick(0.1)
            val occupant = level.vehicles.firstOrNull { it.sourceNode !== yielder && it.currentStreet === outgoing }
            val released = level.vehicles.firstOrNull { it.sourceNode === yielder && it.currentStreet === outgoing }
            if (occupant != null) occupantEntered = true
            if (released != null) {
                yielderEntered = true
                /** Both share the outgoing street: the released yielder is the follower, the previous
                 * vehicle the leader. They must never overlap.
                 */
                if (occupant != null) minClearGapM = minOf(minClearGapM, clearBumperGapM(released, occupant))
            }
        }

        assertTrue(occupantEntered, "the first approach should enter the outgoing street")
        assertTrue(yielderEntered, "the yielding approach should eventually enter the outgoing street (no starvation)")
        assertTrue(
            minClearGapM >= -0.05,
            "a released vehicle must not overlap the vehicle that crossed the node before it; min clear gap was $minClearGapM m"
        )
    }

    /**
     * Tests that the tick function restarts the waiting vehicle with the merge start speed.
     */
    @Test
    fun tick_mergeNode_waitingVehicleRestartsWithMergeStartSpeed() {
        /**
         * Merge is permanently blocked by a third (stationary) approach so the lead vehicle must stop
         * at the stop line and the second vehicle must queue behind it. Once the merge clears,
         * the waiting vehicle should restart with the merge-start speed (5–10 km/h).
        */
        val level = buildSlowClearingMergeWithSingleYielderLevel()
        val sim = Simulation(level)
        val merge = level.nodes.filterIsInstance<ConnectionNode>().single()
        val incoming = merge.incomingStreets
        val yielder = level.nodes
            .filterIsInstance<SourceNode>()
            .single { it.position == scaledPoint(300, 500) }

        sim.tick(0.0)

        var stoppedAtLine = false
        var restartSpeedMs: Double? = null
        var step = 0
        while (step < 300 && restartSpeedMs == null) {
            sim.tick(0.1)
            val vehicle = level.vehicles.firstOrNull { it.sourceNode === yielder }
            if (
                vehicle != null &&
                vehicle.currentStreet in incoming &&
                vehicle.currentSpeed < 0.1 &&
                kotlin.math.abs(frontDistanceToNodeM(vehicle) - Simulation.STOP_LINE_FRONT_OFFSET_M) <= 0.05
            ) {
                stoppedAtLine = true
            }
            if (
                stoppedAtLine &&
                vehicle != null &&
                vehicle.currentStreet in incoming &&
                vehicle.currentSpeed >= 0.1
            ) {
                restartSpeedMs = vehicle.currentSpeed
            }
            step++
        }

        assertTrue(stoppedAtLine, "the yielding vehicle should wait at the shared merge stop line first")
        val restart = assertNotNull(restartSpeedMs, "the waiting vehicle should restart once the merge is free")
        assertTrue(
            restart >= Simulation.MERGE_START_SPEED_KMH * Simulation.KMH_TO_MS,
            "a granted vehicle should restart at the configured merge start speed; speed was $restart m/s"
        )
        assertTrue(
            restart <= 10.0 * Simulation.KMH_TO_MS,
            "the merge start speed should stay in the requested 5-10 km/h range; speed was $restart m/s"
        )
    }

    /**
     * Tests that the tick function keeps the minimum distance between followers on the same street.
     */
    @Test
    fun tick_followersOnSameStreet_keepAtLeastMinimumDistance() {
        // Issue #105 AC1: two vehicles share one approach to a merge that is permanently blocked by
        // a third (stationary) approach, so the lead vehicle must stop at the merge stop line and the
        // second vehicle queues behind it on the SAME street. A follower must never come closer than
        // MIN_DISTANCE_M (bumper-to-tail, i.e. follower front to leader rear) to the vehicle ahead.
        val level = buildQueueBehindBlockedMergeLevel()
        val sim = Simulation(level)
        val followerSource = level.nodes
            .filterIsInstance<SourceNode>()
            .single { it.position == scaledPoint(200, 500) }
        val followerStreet = followerSource.outgoingStreets.single()

        sim.tick(0.0)

        var minGapM = Double.MAX_VALUE
        repeat(400) {
            sim.tick(0.1)
            /**
             * Order rear-most first (largest remaining distance to the node), so each consecutive
             * pair is (follower behind, leader ahead) on the same street.
             */
            val onStreet = level.vehicles
                .filter { it.currentStreet === followerStreet }
                .sortedByDescending { remainingDistanceToNodeM(it) }
            for (i in 0 until onStreet.size - 1) {
                val follower = onStreet[i]
                val leader = onStreet[i + 1]
                val gapM = clearBumperGapM(follower, leader)
                minGapM = minOf(minGapM, gapM)
            }
        }

        assertTrue(
            minGapM >= Simulation.MIN_DISTANCE_M - 0.05,
            "followers on the same street must keep at least ${Simulation.MIN_DISTANCE_M} m bumper-to-tail; min gap was $minGapM m"
        )
    }

    /**
     * Tests that the tick function brakes the follower to the leader's speed when inside the minimum distance.
     */
    @Test
    fun tick_followerInsideMinimumDistance_brakesToLeaderSpeed() {
        /**
         * Issue #105 AC2: a fast vehicle catches a slower MOVING leader on the same street. Once it
         * reaches the minimum distance it must brake to (at most) the leader's speed instead of
         * driving through it. A merge serializes the two vehicles onto the shared outgoing street,
         * slow leader first, so the fast vehicle ends up genuinely following it.
         */
        val level = buildFastFollowsSlowOnSharedStreetLevel()
        val sim = Simulation(level)
        val merge = level.nodes.filterIsInstance<ConnectionNode>().single()
        val sharedStreet = merge.outgoingStreets.single()
        val slowSource = level.nodes
            .filterIsInstance<SourceNode>()
            .single { it.position == scaledPoint(750, 500) }
        val fastSource = level.nodes
            .filterIsInstance<SourceNode>()
            .single { it.position == scaledPoint(800, 100) }

        sim.tick(0.0)

        var closeFollowingObserved = false
        var minFollowerSpeed = Double.MAX_VALUE
        var leaderSpeedAtMin = Double.NaN
        repeat(300) {
            sim.tick(0.1)
            val slow = level.vehicles
                .firstOrNull { it.sourceNode === slowSource && it.currentStreet === sharedStreet }
            val fast = level.vehicles
                .firstOrNull { it.sourceNode === fastSource && it.currentStreet === sharedStreet }
            if (slow != null && fast != null) {
                /** Clear bumper-to-tail distance from the fast follower's front to the slow leader's rear.*/
                val clearGapM = clearBumperGapM(fast, slow)
                if (clearGapM <= Simulation.MIN_DISTANCE_M + 0.3) {
                    closeFollowingObserved = true
                    /**
                     * No matter the tick, the follower must never drive into the leader (AC1 in a
                     * moving context).
                     */
                    assertTrue(
                        clearGapM >= Simulation.MIN_DISTANCE_M - 0.05,
                        "the follower must not drive into the leader; clear gap was $clearGapM m"
                    )
                    if (fast.currentSpeed < minFollowerSpeed) {
                        minFollowerSpeed = fast.currentSpeed
                        leaderSpeedAtMin = slow.currentSpeed
                    }
                }
            }
        }

        assertTrue(
            closeFollowingObserved,
            "the fast vehicle should catch the slow leader within the minimum distance on the shared street"
        )
        /**
         * AC2: once inside the minimum distance the follower must brake down to the leader's speed
         * (its 72 km/h ≈ 20 m/s max makes a value near the 5 m/s leader speed unambiguous braking).
        */
        assertTrue(
            minFollowerSpeed <= leaderSpeedAtMin + 1.5,
            "a follower inside the minimum distance must brake to the leader's speed " +
                "(leader $leaderSpeedAtMin m/s, follower braked to $minFollowerSpeed m/s)"
        )
    }

    /**
     * Tests that the tick function keeps the cruising speed when the follower is outside the leading vehicle lookahead.
     */
    @Test
    fun tick_followerOutsideLeadingVehicleLookahead_keepsCruisingSpeed() {
        /**
         * Issue #105 tuning: the smooth car-following look-ahead must stay local. With the original
         * unrestricted braking-distance formula this fast follower would already slow down even
         * though the stopped leader is still well outside the arcade-style reaction horizon.
        */
        val level = buildLeaderLookaheadLevel(
            clearGapM = Simulation.LEADING_VEHICLE_LOOKAHEAD_M + 5.0
        )
        val sim = Simulation(level)
        val follower = level.vehicles.single { it.type.name == "FastCar" }
        val maxSpeedMs = follower.type.maxSpeed * Simulation.KMH_TO_MS

        assertEquals(maxSpeedMs, follower.currentSpeed, 1e-9, "test setup should start at max speed")

        sim.tick(0.1)

        assertEquals(
            maxSpeedMs,
            follower.currentSpeed,
            1e-9,
            "a leader outside the local look-ahead must not trigger early smooth braking"
        )
    }

    /**
     * Tests that the tick function brakes toward the leader when inside the leading vehicle lookahead.
     */
    @Test
    fun tick_followerInsideLeadingVehicleLookahead_brakesTowardLeader() {
        /**
         * Companion check to the bounded look-ahead: once the leader is inside the local horizon,
         * the follower must still use the smooth braking ceiling instead of cruising at max speed
         * until the hard 2 m clamp catches it.
        */
        val level = buildLeaderLookaheadLevel(
            clearGapM = Simulation.LEADING_VEHICLE_LOOKAHEAD_M - 5.0
        )
        val sim = Simulation(level)
        val follower = level.vehicles.single { it.type.name == "FastCar" }
        val maxSpeedMs = follower.type.maxSpeed * Simulation.KMH_TO_MS

        assertEquals(maxSpeedMs, follower.currentSpeed, 1e-9, "test setup should start at max speed")

        sim.tick(0.1)

        assertTrue(
            follower.currentSpeed < maxSpeedMs,
            "a leader inside the local look-ahead must still trigger smooth braking"
        )
    }

    /**
     * Tests that the tick function keeps the visible bumper gap between vehicles of different lengths.
     */
    @Test
    fun tick_followerAtMinimumGapMovesAfterLeaderInsteadOfStuttering() {
        // Regression for ruckiness: if the follower is processed before the leader, it clamps against
        // the leader's previous position and waits one frame. Same-street movement must be front to
        // back so a moving leader opens space before the follower applies the 2 m clamp.
        val level = buildMovingLeaderFollowingLevel()
        val sim = Simulation(level)
        val follower = level.vehicles.single { it.type.name == "FollowerCar" }
        val initialProgressM = follower.distanceAlongSegment

        sim.tick(0.1)

        assertTrue(
            follower.distanceAlongSegment > initialProgressM,
            "a follower at the minimum gap should keep rolling after a moving leader, not stutter in place"
        )
    }

    /**
     * Tests that the tick function keeps the visible bumper gap between vehicles of different lengths.
     */
    @Test
    fun tick_vehicleOnDifferentLengthsKeepVisibleBumperGap() {
        // Vehicle sprites are rendered around their travelled centre point. A truck following a
        // shorter car therefore needs both half-lengths in the bumper-gap formula, not just the
        // leader length. Otherwise, the visible front of the truck could overlap the rear of the car.
        val level = buildMixedLengthFollowingLevel()
        val sim = Simulation(level)
        val follower = level.vehicles.single { it.type.name == "FollowerTruck" }
        val leader = level.vehicles.single { it.type.name == "LeaderCar" }

        sim.tick(1.0)

        val gapM = clearBumperGapM(follower, leader)
        assertTrue(
            gapM >= Simulation.MIN_DISTANCE_M - 0.05,
            "mixed-length vehicles must keep the visible bumper gap; clear gap was $gapM m"
        )
    }

    /**
     * Tests that the tick function does not trigger same-street minimum-distance braking before the connection.
     */
    @Test
    fun tick_vehicleOnNextStreetDoesNotTriggerSameStreetMinimumDistanceBeforeConnection() {
        /**
         * The 2 m minimum distance is same-street following only. A vehicle just after a connection
         * node is not a leader for a vehicle that is still on the incoming street before that node.
        */
        val level = buildNextStreetLeaderLevel()
        val sim = Simulation(level)
        val follower = level.vehicles.single { it.type.name == "IncomingCar" }
        val maxSpeedMs = follower.type.maxSpeed * Simulation.KMH_TO_MS

        sim.tick(0.1)

        assertEquals(
            maxSpeedMs,
            follower.currentSpeed,
            1e-9,
            "a vehicle on the next street must not trigger same-street minimum-distance braking before the connection"
        )
    }

    /**
     * Tests that a new vehicle can only spawn when the immediate entry to its next street is free.
     */
    @Test
    fun tick_spawnOntoOccupiedStreetEntry_isDeferredUntilSpaceIsFree() {
        /**
         * Issue #154 AC1: a new vehicle may only spawn when its street entry has room. With the lead
         * vehicle stationary at the source (initial = max = 0) the entry never frees again, so no
         * further vehicle of the same configuration may spawn on top of it even though the spawn
         * timer keeps coming due. The deferred spawn must not be counted as generated.
         */
        val level = buildLevel(
            generationSpeed = 1.0,
            initialVehicleSpeed = 0.0,
            maxSpeed = 0.0,
            acceleration = 0.0,
            vehiclesToSpawn = 5
        )
        val sim = Simulation(level)

        sim.tick(0.0)
        assertEquals(1, level.vehicles.size, "the first vehicle spawns onto a free entry")

        repeat(20) { sim.tick(1.0) }

        assertEquals(1, level.vehicles.size, "no vehicle may spawn while the street entry is occupied")
        val cfg = level.nodes.filterIsInstance<SourceNode>().single().configurations.single()
        assertEquals(1, cfg.vehiclesSpawned, "the blocked spawn is deferred, not counted")
    }

    /**
     * Tests that two source configurations sharing the same immediate next street each spawn one
     * vehicle without overlapping when both are due on the same tick.
     */
    @Test
    fun tick_twoConfigurationsDueOnSameEntry_spawnWithoutOverlapping() {
        /**
         * Issue #154 AC2 + Desired Behavior: two source configurations on the same source come due on
         * the very first tick and both resolve to the start of the shared outgoing street. Only one
         * may spawn that tick; the other is deferred until the entry clears. No two vehicles ever
         * overlap on that street, and the deferred configuration still gets its vehicle out.
         */
        val level = buildSharedEntryTwoConfigurationsLevel()
        val sim = Simulation(level)
        val street = level.nodes.filterIsInstance<SourceNode>().single().outgoingStreets.single()

        sim.tick(0.0)
        assertEquals(
            1,
            level.vehicles.size,
            "two configurations due on the same entry must not both spawn on the same tick"
        )

        var minGapM = Double.MAX_VALUE
        repeat(200) {
            sim.tick(0.1)
            // Order rear-most first so each consecutive pair is (follower behind, leader ahead).
            val onStreet = level.vehicles
                .filter { it.currentStreet === street }
                .sortedByDescending { remainingDistanceToNodeM(it) }
            for (i in 0 until onStreet.size - 1) {
                minGapM = minOf(minGapM, clearBumperGapM(onStreet[i], onStreet[i + 1]))
            }
        }

        assertTrue(
            minGapM >= Simulation.MIN_DISTANCE_M - 0.05,
            "vehicles must never overlap at spawn; min clear gap was $minGapM m"
        )
        val totalSpawned = level.nodes
            .filterIsInstance<SourceNode>()
            .single()
            .configurations
            .sumOf { it.vehiclesSpawned }
        assertEquals(2, totalSpawned, "the deferred configuration still spawns once the entry frees")
    }

    /**
     * Builds a merge whose two incoming streets arrive almost parallel (a sharp ~11° join), mirroring
     * the diagonal-into-straight junction from the "Intersection and Turn" level. Both approaches keep
     * a backlog so vehicles from each side sit at the node simultaneously, exercising the 2D
     * merge-proximity clamp.
     *
     * Layout (1800×1000 px play area):
     * ```
     *  straight source (200,520) ──────────────────────►┐
     *                                                    ├─ merge (1000,520) ──► target (1700,520)
     *  diagonal source (220,420) ─► (900,500) ──────────┘
     * ```
     *
     * @return The level with the sharp angle merge.
     */
    private fun buildSharpAngleMergeLevel(): Level {
        val level = Level("SharpAngleMerge", 41)
        val red = Color("Rot", Colors.RED)
        val auto = VehicleType("Auto", 40.0, 4.0, 0.7, 3.0)
        val straightCfg = SourceNodeConfiguration(red, 0.1, 40.0, auto, vehiclesToSpawn = 3)
        val diagonalCfg = SourceNodeConfiguration(red, 0.1, 40.0, auto, vehiclesToSpawn = 3)
        val straightSource = SourceNode(scaledPoint(200, 520), 0, Angle.fromDegrees(0.0), listOf(straightCfg))
        val diagonalSource = SourceNode(scaledPoint(220, 420), 0, Angle.fromDegrees(0.0), listOf(diagonalCfg))
        val merge = ConnectionNode(scaledPoint(1000, 520), 0)
        val target = TargetNode(scaledPoint(1700, 520), 0, Angle.fromDegrees(0.0), red)
        level.addNode(straightSource)
        level.addNode(diagonalSource)
        level.addNode(merge)
        level.addNode(target)
        level.addStreet(Street(straightSource, merge, listOf(scaledPoint(200, 520), scaledPoint(1000, 520))))
        level.addStreet(
            Street(diagonalSource, merge, listOf(scaledPoint(220, 420), scaledPoint(900, 500), scaledPoint(1000, 520)))
        )
        level.addStreet(Street(merge, target, listOf(scaledPoint(1000, 520), scaledPoint(1700, 520))))
        level.validate()
        return level
    }

    /**
     * Builds a straight road split into four segments by three connection nodes, fed by one source
     * carrying a slow truck ahead of faster cars. The cars catch the truck and queue, so they keep
     * "nachrücken" across the intersection nodes — the scenario where the cross-node following gap
     * must hold (mirrors a grid through-road).
     *
     * @return The level with the through road with intersections.
     */
    private fun buildThroughRoadWithIntersectionsLevel(): Level {
        val level = Level("ThroughRoadIntersections", 42)
        val red = Color("Rot", Colors.RED)
        val car = VehicleType("Auto", 50.0, 4.0, 0.7, 3.0)
        val truck = VehicleType("Lkw", 12.0, 8.0, 0.7, 1.0)
        val truckCfg = SourceNodeConfiguration(red, 9999.0, 12.0, truck, vehiclesToSpawn = 1)
        val carCfg = SourceNodeConfiguration(red, 0.4, 50.0, car, vehiclesToSpawn = 4)
        val source = SourceNode(scaledPoint(100, 500), 0, Angle.fromDegrees(0.0), listOf(truckCfg, carCfg))
        val n1 = ConnectionNode(scaledPoint(500, 500), 0)
        val n2 = ConnectionNode(scaledPoint(900, 500), 0)
        val n3 = ConnectionNode(scaledPoint(1300, 500), 0)
        val target = TargetNode(scaledPoint(1700, 500), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(n1)
        level.addNode(n2)
        level.addNode(n3)
        level.addNode(target)
        level.addStreet(Street(source, n1, listOf(scaledPoint(100, 500), scaledPoint(500, 500))))
        level.addStreet(Street(n1, n2, listOf(scaledPoint(500, 500), scaledPoint(900, 500))))
        level.addStreet(Street(n2, n3, listOf(scaledPoint(900, 500), scaledPoint(1300, 500))))
        level.addStreet(Street(n3, target, listOf(scaledPoint(1300, 500), scaledPoint(1700, 500))))
        level.validate()
        return level
    }

    /**
     * Wires two approaches into one [merge] connection node with a single outgoing street to
     * [target], using straight streets between the node positions. Centralizes the node/street
     * boilerplate shared by the merge-scenario builders so each only defines its sources, merge and
     * target. [approachA]'s street is registered first, fixing the round-robin right-of-way order.
     *
     * @param level the level to populate (its name and number are set by the caller).
     * @param approachA the first incoming node; its street is registered first.
     * @param approachB the second incoming node.
     * @param merge the shared merge connection node.
     * @param target the target the outgoing street leads to.
     * @return the same [level], after its nodes, streets and validation have been added.
     */
    private fun wireTwoApproachMerge(
        level: Level,
        approachA: Node,
        approachB: Node,
        merge: ConnectionNode,
        target: TargetNode
    ): Level {
        listOf(approachA, approachB, merge, target).forEach(level::addNode)
        level.addStreet(Street(approachA, merge, listOf(approachA.position, merge.position)))
        level.addStreet(Street(approachB, merge, listOf(approachB.position, merge.position)))
        level.addStreet(Street(merge, target, listOf(merge.position, target.position)))
        level.validate()
        return level
    }

    /**
     * Builds a symmetric zipper-merge level: two sources feed two incoming streets into
     * one [ConnectionNode] (the merge) which has a single outgoing street to the target.
     *
     * Layout (1800×1000 px play area), mirror-symmetric across y = 200:
     * ```
     *  source-top (0,0) ────────►┐
     *                            ├─ merge (800,200) ──► target (1200,200)
     *  source-bottom (0,400) ───►┘
     * ```
     * The two incoming streets are congruent mirror images, so two vehicles spawned at the
     * same time travel identically and reach the merge on the same tick — a deterministic
     * contention used to exercise the zipper rule. The top incoming street is added first,
     * fixing the round-robin start order.
     *
     * @param vehiclesTop The number of vehicles to spawn from the top source node.
     * @param vehiclesBottom The number of vehicles to spawn from the bottom source node.
     * @param generationSpeed The generation speed of the vehicles.
     * @return The level with the zipper merge.
     */
    private fun buildZipperMergeLevel(
        vehiclesTop: Int = 3,
        vehiclesBottom: Int = 3,
        generationSpeed: Double = 2.0
    ): Level {
        val level = Level("ZipperMerge", 20)
        val red = Color("Rot", Colors.RED)
        val auto = VehicleType("Auto", 40.0, 4.0, 0.7, 3.0)
        val topCfg = SourceNodeConfiguration(red, generationSpeed, 40.0, auto, vehiclesToSpawn = vehiclesTop)
        val bottomCfg = SourceNodeConfiguration(red, generationSpeed, 40.0, auto, vehiclesToSpawn = vehiclesBottom)
        val sourceTop = SourceNode(scaledPoint(0, 0), 0, Angle.fromDegrees(0.0), listOf(topCfg))
        val sourceBottom = SourceNode(scaledPoint(0, 400), 0, Angle.fromDegrees(0.0), listOf(bottomCfg))
        val merge = ConnectionNode(scaledPoint(800, 200), 0)
        val target = TargetNode(scaledPoint(1200, 200), 0, Angle.fromDegrees(0.0), red)
        return wireTwoApproachMerge(level, sourceTop, sourceBottom, merge, target)
    }

    /**
     * Builds a two-street level where the only curvature is at the connection
     * node between two otherwise straight streets.
     *
     * Layout (1800×1000 px play area):
     * ```
     *  source (50,950) ────────────────► connection (1500,950)
     *                                            │  90°
     *                                            ▼
     *                                      target (1500,50)
     * ```
     * Both streets are 2-point polylines, so no inner waypoint exists within
     * either street. The corner triangle (source, connection, target) has legs
     * 1450 px and 900 px, hypotenuse ≈ 1706.6 px, circumradius ≈ 853.3 px ≈
     * 106.7 m. With µ = 0.7 and g = 9.81 the resulting V_max_i ≈ 27.1 m/s ≈
     * 97.5 km/h is well below the vehicle's 144 km/h max, so the connection
     * node imposes an active speed ceiling that the look-ahead must honor.
     *
     * @return The level with the sharp connection angle.
     */
    private fun buildTwoStreetLevelWithSharpConnectionAngle(): Level {
        val level = Level("ConnectionAngleLevel", 5)
        val red = Color("Rot", Colors.RED)
        val fastAuto = VehicleType("FastAuto", 144.0, 4.0, 0.7, 3.0)
        val cfg = SourceNodeConfiguration(red, 9999.0, 144.0, fastAuto)
        val source = SourceNode(scaledPoint(50, 950), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val connection = ConnectionNode(scaledPoint(1500, 950), 0)
        val target = TargetNode(scaledPoint(1500, 50), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(connection)
        level.addNode(target)
        level.addStreet(Street(source, connection, listOf(scaledPoint(50, 950), scaledPoint(1500, 950))))
        level.addStreet(Street(connection, target, listOf(scaledPoint(1500, 950), scaledPoint(1500, 50))))
        level.validate()
        return level
    }

    /**
     * Builds a two-street level: straight first street, then a second street that
     * starts with a tight right-angled inner waypoint just past the connection node.
     *
     * Layout (1800×1000 px play area):
     * ```
     *  source (50,950) ────────────────► connection (1500,950) ──► (1700,950)
     *                                                                  │  90°
     *                                                                  ▼
     *                                                            target (1700,50)
     * ```
     * The inner waypoint at (1700, 950) is the only one in the system; the legs
     * are 200 px and 900 px, hypotenuse ≈ 921.95 px, so the circumradius is
     * ≈ 460.97 px ≈ 57.6 m and `V_max_i = sqrt(0.7·9.81·57.6) ≈ 19.9 m/s ≈
     * 71.6 km/h`, well below the vehicle's 144 km/h max. With acceleration
     * 3 m/s² the braking distance from V_max to V_max_i is ≈ 200 m ≈ 1606 px —
     * more than the 200 px run on the second street, so braking *must* start
     * on the first street for the cross-street look-ahead test to pass.
     *
     * @return The level with the curve on the second street.
     */
    private fun buildTwoStreetLevelWithCurveOnSecondStreet(): Level {
        val level = Level("CrossStreetCurveLevel", 4)
        val red = Color("Rot", Colors.RED)
        val fastAuto = VehicleType("FastAuto", 144.0, 4.0, 0.7, 3.0)
        val cfg = SourceNodeConfiguration(red, 9999.0, 144.0, fastAuto)
        val source = SourceNode(scaledPoint(50, 950), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val connection = ConnectionNode(scaledPoint(1500, 950), 0)
        val target = TargetNode(scaledPoint(1700, 50), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(connection)
        level.addNode(target)
        // Straight first street: source → connection, 1450 px long, no inner waypoints.
        level.addStreet(Street(source, connection, listOf(scaledPoint(50, 950), scaledPoint(1500, 950))))
        // Second street: 200 px straight run, then a 90° corner at (1700, 950),
        // then 900 px straight to the target. The corner is the only inner waypoint.
        level.addStreet(
            Street(
                connection, target,
                listOf(scaledPoint(1500, 950), scaledPoint(1700, 950), scaledPoint(1700, 50))
            )
        )
        level.validate()
        return level
    }

    /**
     * Builds a level with a right-angled inner waypoint that is tight enough to
     * force the curve-speed limit below the vehicle's global max speed.
     *
     * Layout (1800×1000 px play area):
     * ```
     *  source (50,950) ────────────────────────────────► (1700,950)
     *                                                         │  90°  (inner waypoint)
     *                                                         ▼
     *                                                   target (1700,50)
     * ```
     * Triangle in the corner: legs 1650 px and 900 px, hypotenuse ≈ 1879.49 px,
     * giving a circumradius ≈ 939.7 px ≈ 117.5 m. With µ = 0.7, g = 9.81 the
     * resulting `V_max_i ≈ 28.4 m/s ≈ 102 km/h`, comfortably below the vehicle's
     * 144 km/h max so the curve constraint is active. The 1650 px straight
     * approach is long enough that we can observe both the braking phase (~4 s)
     * and the re-acceleration phase (~6 s) within a single simulation run.
     *
     * @return The level with the tight curve.
     */
    private fun buildLevelWithTightCurve(): Level {
        val level = Level("CurveLevel", 3)
        val red = Color("Rot", Colors.RED)
        val fastAuto = VehicleType("FastAuto", 144.0, 4.0, 0.7, 3.0)
        val cfg = SourceNodeConfiguration(red, 9999.0, 144.0, fastAuto)
        val source = SourceNode(scaledPoint(50, 950), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val target = TargetNode(scaledPoint(1700, 50), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(target)
        // Polyline: source → 90° corner at (1700, 950) → target. The corner is the inner waypoint.
        level.addStreet(
            Street(
                source, target,
                listOf(scaledPoint(50, 950), scaledPoint(1700, 950), scaledPoint(1700, 50))
            )
        )
        level.validate()
        return level
    }

    /**
     * Builds a level with one source node with two different configurations
     *
     * @return The level with the fast and slow configurations.
     */
    private fun buildLevelWithFastAndSlowConfigurations(): Level {
        val level = Level("TwoConfigurations", 2)
        val red = Color("Rot", Colors.RED)
        val fastType = VehicleType("FastAuto", 80.0, 4.0, 0.7, 0.0)
        val slowType = VehicleType("SlowAuto", 5.0, 4.0, 0.7, 0.0)
        val source = SourceNode(
            scaledPoint(0, 0),
            0,
            Angle.fromDegrees(0.0),
            listOf(
                SourceNodeConfiguration(red, 9999.0, 80.0, fastType, vehiclesToSpawn = 1),
                SourceNodeConfiguration(red, 9999.0, 5.0, slowType, vehiclesToSpawn = 1)
            )
        )
        val target = TargetNode(scaledPoint(800, 0), 0, Angle.fromDegrees(0.0), red)

        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(scaledPoint(0, 0), scaledPoint(800, 0))))
        level.validate()
        return level
    }

    /**
     * Builds one source with two same-colour configurations feeding a single straight street to a
     * matching target. Both configurations become due on the first tick and resolve to the same
     * spawn position, so they exercise the no-overlap-at-spawn rule (Issue #154). Vehicles move at a
     * constant speed (acceleration 0, max = initial) so the entry clears predictably.
     */
    private fun buildSharedEntryTwoConfigurationsLevel(): Level {
        val level = Level("SharedSpawnEntry", 40)
        val red = Color("Rot", Colors.RED)
        val speedKmh = 36.0
        val auto = VehicleType("Auto", speedKmh, 4.0, 0.7, 0.0)
        val source = SourceNode(
            scaledPoint(0, 500),
            0,
            Angle.fromDegrees(0.0),
            listOf(
                SourceNodeConfiguration(red, 9999.0, speedKmh, auto, vehiclesToSpawn = 1),
                SourceNodeConfiguration(red, 9999.0, speedKmh, auto, vehiclesToSpawn = 1)
            )
        )
        val target = TargetNode(scaledPoint(1000, 500), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(scaledPoint(0, 500), scaledPoint(1000, 500))))
        level.validate()
        return level
    }

    /**
     * Builds a level with one source and two target nodes, creating a short and a long route.
     * The source has two configurations, one for each route. The short route is 100 m long
     * (source -> shortJunction -> target) and the long route is 200 m long
     * (source -> longJunction -> detour -> target).
     *
     * @return The level with the short and long routes.
     */
    private fun buildLevelWithShortAndLongRoutes(): Level {
        val level = Level("RouteChoice", 6)
        val red = Color("Rot", Colors.RED)
        val auto = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)
        val cfg = SourceNodeConfiguration(red, 9999.0, 0.0, auto)
        val source = SourceNode(Point(0, 0), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val longJunction = ConnectionNode(Point(0, 100), 0)
        val shortJunction = ConnectionNode(Point(100, 0), 0)
        val target = TargetNode(Point(200, 0), 0, Angle.fromDegrees(0.0), red)

        level.addNode(source)
        level.addNode(longJunction)
        level.addNode(shortJunction)
        level.addNode(target)

        level.addStreet(Street(source, longJunction, listOf(Point(0, 0), Point(0, 100))))
        level.addStreet(Street(longJunction, target, listOf(Point(0, 100), Point(200, 100), Point(200, 0))))
        level.addStreet(Street(source, shortJunction, listOf(Point(0, 0), Point(100, 0))))
        level.addStreet(Street(shortJunction, target, listOf(Point(100, 0), Point(200, 0))))
        level.validate()
        return level
    }

    /**
     * Helper class for [buildLevelWithSharedFirstRoute] that bundles the relevant streets for tests
     * that only care about a subset of the level's geography.
     *
     * @property level The level with the shared first route choice.
     * @property sharedStreet The shared street between the two routes.
     * @property shortExitStreet The shorter exit street.
     * @property detourFirstStreet The longer detour street.
     */
    private data class SharedFirstRouteFixture(
        val level: Level,
        val sharedStreet: Street,
        val shortExitStreet: Street,
        val detourFirstStreet: Street
    )

    /**
     * Builds two routes with the same first street so rerouting can happen without teleporting:
     * source -> fork -> target is shortest, source -> fork -> detour -> target is longer.
     *
     * @return The level with the shared first route choice.
     */
    private fun buildLevelWithSharedFirstRoute(): SharedFirstRouteFixture {
        val level = Level("SharedFirstRouteChoice", 7)
        val red = Color("Rot", Colors.RED)
        val auto = VehicleType("Auto", 80.0, 4.0, 0.7, 0.0)
        val cfg = SourceNodeConfiguration(red, 9999.0, 0.0, auto)
        val source = SourceNode(Point(0, 0), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val fork = ConnectionNode(Point(100, 0), 0)
        val detour = ConnectionNode(Point(100, 100), 0)
        val target = TargetNode(Point(200, 0), 0, Angle.fromDegrees(0.0), red)

        level.addNode(source)
        level.addNode(fork)
        level.addNode(detour)
        level.addNode(target)

        val shared = Street(source, fork, listOf(Point(0, 0), Point(100, 0)))
        val shortExit = Street(fork, target, listOf(Point(100, 0), Point(200, 0)))
        val detourFirst = Street(fork, detour, listOf(Point(100, 0), Point(100, 100)))
        val detourSecond = Street(detour, target, listOf(Point(100, 100), Point(200, 100), Point(200, 0)))
        level.addStreet(shared)
        level.addStreet(shortExit)
        level.addStreet(detourFirst)
        level.addStreet(detourSecond)
        level.validate()

        return SharedFirstRouteFixture(level, shared, shortExit, detourFirst)
    }

    /**
     * Builds a merge level whose [ConnectionNode] has two incoming streets but only one of them
     * is ever fed with traffic, so the junction is always free for that approach.
     *
     * Layout (1800×1000 px play area):
     * ```
     *  dead connection (1000,100)
     *                 │
     *                 ▼
     *  source (300,500) ──► merge (1000,500) ──► target (1700,500)
     * ```
     * The fed approach and the outgoing street are collinear (all at y = 500), so the merge
     * imposes no curve limit and the vehicle would cruise at constant speed if it is never made
     * to yield. The second incoming street starts at a plain connection node that has no source,
     * so it never produces a competing vehicle — the merge stays free.
     *
     * @return The level with the straight single fed merge.
     */
    private fun buildStraightSingleFedMergeLevel(): Level {
        val level = Level("StraightSingleFedMerge", 21)
        val red = Color("Rot", Colors.RED)
        val auto = VehicleType("Auto", 40.0, 4.0, 0.7, 3.0)
        val cfg = SourceNodeConfiguration(red, 9999.0, 40.0, auto, vehiclesToSpawn = 1)
        val source = SourceNode(scaledPoint(300, 500), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val deadConnection = ConnectionNode(scaledPoint(1000, 100), 0)
        val merge = ConnectionNode(scaledPoint(1000, 500), 0)
        val target = TargetNode(scaledPoint(1700, 500), 0, Angle.fromDegrees(0.0), red)
        return wireTwoApproachMerge(level, source, deadConnection, merge, target)
    }

    /**
     * Builds a merge where the first registered incoming street is much farther from the merge than
     * the second. This catches free-merge idling: the closer vehicle should get the crossing first
     * even though the round-robin pointer initially starts at the farther approach.
     *
     * @return The level with the uneven approach merge.
     */
    private fun buildUnevenApproachMergeLevel(): Level {
        val level = Level("UnevenApproachMerge", 24)
        val red = Color("Rot", Colors.RED)
        val auto = VehicleType("Auto", 40.0, 4.0, 0.7, 3.0)
        val farCfg = SourceNodeConfiguration(red, 9999.0, 40.0, auto, vehiclesToSpawn = 1)
        val nearCfg = SourceNodeConfiguration(red, 9999.0, 40.0, auto, vehiclesToSpawn = 1)
        val farSource = SourceNode(scaledPoint(100, 300), 0, Angle.fromDegrees(0.0), listOf(farCfg))
        val nearSource = SourceNode(scaledPoint(900, 500), 0, Angle.fromDegrees(0.0), listOf(nearCfg))
        val merge = ConnectionNode(scaledPoint(1000, 500), 0)
        val target = TargetNode(scaledPoint(1600, 500), 0, Angle.fromDegrees(0.0), red)
        return wireTwoApproachMerge(level, farSource, nearSource, merge, target)
    }

    /**
     * Builds a merge where a slow, long vehicle on one approach holds the crossing for several
     * seconds while a single fast vehicle on the other approach must yield. The yielding approach is
     * collinear with the outgoing street (no curve limit), so the only thing that can slow that
     * vehicle is the merge stop line — letting a test observe exactly where a yielder comes to rest.
     *
     * Layout (1800×1000 px play area):
     * ```
     *                          slow truck
     *                          (1000,460)
     *                               │
     *                               ▼
     *  fast car (300,500) ─────► merge (1000,500) ─────► target (1700,500)
     * ```
     * The truck's short approach lets it claim the crossing first; its low speed (4 km/h) keeps it
     * occupying the junction long enough that the fast car (50 km/h) must brake to a full stop at
     * its stop line and wait.
     *
     * @return The level with the occupied merge and single yielder.
     */
    private fun buildOccupiedMergeWithSingleYielderLevel(): Level {
        val level = Level("OccupiedMerge", 22)
        val red = Color("Rot", Colors.RED)
        val truck = VehicleType("Truck", 4.0, 8.0, 0.7, 1.0)
        val car = VehicleType("Car", 50.0, 4.0, 0.7, 3.0)
        val occupantCfg = SourceNodeConfiguration(red, 9999.0, 4.0, truck, vehiclesToSpawn = 1)
        val yielderCfg = SourceNodeConfiguration(red, 9999.0, 50.0, car, vehiclesToSpawn = 1)
        val occupantSource = SourceNode(scaledPoint(1000, 460), 0, Angle.fromDegrees(0.0), listOf(occupantCfg))
        val yielderSource = SourceNode(scaledPoint(300, 500), 0, Angle.fromDegrees(0.0), listOf(yielderCfg))
        val merge = ConnectionNode(scaledPoint(1000, 500), 0)
        val target = TargetNode(scaledPoint(1700, 500), 0, Angle.fromDegrees(0.0), red)
        return wireTwoApproachMerge(level, occupantSource, yielderSource, merge, target)
    }

    /**
     * Builds a merge where one approach claims the crossing but cannot move, forcing the other
     * approach to wait at its stop line. This isolates the stop-line behaviour from the normal
     * quick release path used by moving traffic and uses a long yielding vehicle to verify that
     * the stop-line distance includes vehicle length.
     *
     * @param yielderType The type of vehicle to use for the yielder.
     * @return The level with the blocked merge.
     */
    private fun buildBlockedMergeWithSingleYielderLevel(
        yielderType: VehicleType = VehicleType("LongVehicle", 50.0, 8.0, 0.7, 3.0)
    ): Level {
        val level = Level("BlockedMerge", 23)
        val red = Color("Rot", Colors.RED)
        val blockerType = VehicleType("Blocker", 0.0, 4.0, 0.7, 0.0)
        val blockerCfg = SourceNodeConfiguration(red, 9999.0, 0.0, blockerType, vehiclesToSpawn = 1)
        val yielderCfg = SourceNodeConfiguration(red, 9999.0, 50.0, yielderType, vehiclesToSpawn = 1)
        val blockerSource = SourceNode(scaledPoint(1000, 496), 0, Angle.fromDegrees(0.0), listOf(blockerCfg))
        val yielderSource = SourceNode(scaledPoint(300, 500), 0, Angle.fromDegrees(0.0), listOf(yielderCfg))
        val merge = ConnectionNode(scaledPoint(1000, 500), 0)
        val target = TargetNode(scaledPoint(1700, 500), 0, Angle.fromDegrees(0.0), red)
        return wireTwoApproachMerge(level, blockerSource, yielderSource, merge, target)
    }

    /**
     * Builds a merge where one approach enters first, clears slowly, and then releases a yielder
     * that has already stopped at the shared merge stop line.
     *
     * @return The level with the slow clearing merge and single yielder.
     */
    private fun buildSlowClearingMergeWithSingleYielderLevel(): Level {
        val level = Level("SlowClearingMerge", 25)
        val red = Color("Rot", Colors.RED)
        val blockerType = VehicleType("SlowBlocker", 0.5, 4.0, 0.7, 0.0)
        val yielderType = VehicleType("Car", 50.0, 4.0, 0.7, 3.0)
        val blockerCfg = SourceNodeConfiguration(red, 9999.0, 0.5, blockerType, vehiclesToSpawn = 1)
        val yielderCfg = SourceNodeConfiguration(red, 9999.0, 50.0, yielderType, vehiclesToSpawn = 1)
        val blockerSource = SourceNode(scaledPoint(1000, 496), 0, Angle.fromDegrees(0.0), listOf(blockerCfg))
        val yielderSource = SourceNode(scaledPoint(300, 500), 0, Angle.fromDegrees(0.0), listOf(yielderCfg))
        val merge = ConnectionNode(scaledPoint(1000, 500), 0)
        val target = TargetNode(scaledPoint(1700, 500), 0, Angle.fromDegrees(0.0), red)
        return wireTwoApproachMerge(level, blockerSource, yielderSource, merge, target)
    }

    /**
     * Builds a merge that is permanently blocked by a stationary vehicle on one approach, while a
     * second approach feeds **two** vehicles onto the same street. The lead vehicle has to stop at
     * the merge stop line, so the second vehicle must queue behind it on the same street — exactly
     * the same-street following situation Issue #105 governs.
     *
     * Layout (1800×1000 px play area):
     * ```
     *                                stationary blocker (1000,496)
     *                                            │
     *                                            ▼
     *  follower source (200,500) ──► merge (1000,500) ──► target (1700,500)
     * ```
     * The blocker street is registered first, so the round-robin right-of-way grants the (immovable)
     * blocker the crossing and both follower-approach vehicles keep yielding for the whole run. The
     * follower approach is 800 px (100 m) long — plenty of room for two cars plus the 2 m gap.
     *
     * @return The level with the queue behind the blocked merge.
     */
    private fun buildQueueBehindBlockedMergeLevel(): Level {
        val level = Level("QueueBehindBlockedMerge", 26)
        val red = Color("Rot", Colors.RED)
        val generationSpeed = 1.0
        val blockerType = VehicleType("Blocker", 0.0, 4.0, 0.7, 0.0)
        val followerType = VehicleType("Car", 50.0, 4.0, 0.7, 3.0)
        val blockerCfg = SourceNodeConfiguration(red, 9999.0, 0.0, blockerType, vehiclesToSpawn = 1)
        val followerCfg = SourceNodeConfiguration(red, generationSpeed, 50.0, followerType, vehiclesToSpawn = 2)
        val blockerSource = SourceNode(scaledPoint(1000, 496), 0, Angle.fromDegrees(0.0), listOf(blockerCfg))
        val followerSource = SourceNode(scaledPoint(200, 500), 0, Angle.fromDegrees(0.0), listOf(followerCfg))
        val merge = ConnectionNode(scaledPoint(1000, 500), 0)
        val target = TargetNode(scaledPoint(1700, 500), 0, Angle.fromDegrees(0.0), red)
        return wireTwoApproachMerge(level, blockerSource, followerSource, merge, target)
    }

    /**
     * Builds a level where a fast vehicle ends up following a slower, moving leader on one shared
     * street. Two sources feed a merge; the slow approach is registered first and is much shorter, so
     * the slow leader crosses onto the shared outgoing street first and the fast vehicle (from a long
     * second approach) catches it from behind on the straight shared street.
     *
     * Layout (1800×1000 px play area):
     * ```
     *                       fast source (800,100)
     *                                 │
     *                                 ▼
     *  slow source (750,500) ──► merge (800,500) ──────────► target (1750,500)
     * ```
     * The shared street (800,500)→(1750,500) is a straight 950 px (≈119 m) run, so the only thing
     * that can slow the fast follower there is the leading slow vehicle (no curve limit applies). The
     * 90° turn the fast approach makes at the merge stays above its 72 km/h max, so it does not brake
     * for the corner either.
     *
     * @return The level with the fast following slow vehicles.
     */
    private fun buildFastFollowsSlowOnSharedStreetLevel(): Level {
        val level = Level("FastFollowsSlow", 27)
        val red = Color("Rot", Colors.RED)
        val slowType = VehicleType("SlowCar", 18.0, 4.0, 0.7, 3.0)
        val fastType = VehicleType("FastCar", 72.0, 4.0, 0.7, 4.0)
        val slowCfg = SourceNodeConfiguration(red, 9999.0, 18.0, slowType, vehiclesToSpawn = 1)
        val fastCfg = SourceNodeConfiguration(red, 9999.0, 40.0, fastType, vehiclesToSpawn = 1)
        val slowSource = SourceNode(scaledPoint(750, 500), 0, Angle.fromDegrees(0.0), listOf(slowCfg))
        val fastSource = SourceNode(scaledPoint(800, 100), 0, Angle.fromDegrees(0.0), listOf(fastCfg))
        val merge = ConnectionNode(scaledPoint(800, 500), 0)
        val target = TargetNode(scaledPoint(1750, 500), 0, Angle.fromDegrees(0.0), red)
        // Slow approach passed first → round-robin serves the slow leader onto the shared street first.
        return wireTwoApproachMerge(level, slowSource, fastSource, merge, target)
    }

    /**
     * Builds a straight same-street following scenario with manually placed vehicles. Spawning is
     * disabled so the test can isolate the leader look-ahead without merge or source timing effects.
     *
     * @param clearGapM The desired visible bumper gap in metres.
     * @return The level with the leader lookahead.
     */
    private fun buildLeaderLookaheadLevel(clearGapM: Double): Level {
        val fastType = VehicleType("FastCar", 80.0, 4.0, 0.7, 3.0)
        val leaderType = VehicleType("StoppedCar", 0.0, 4.0, 0.7, 0.0)
        return buildSameStreetFollowingLevel("LeaderLookahead", 28, fastType, leaderType, clearGapM)
    }

    /**
     * Builds a same-street pair with the follower deliberately inserted before the leader. This
     * catches movement-order regressions where the follower clamps against stale leader progress.
     *
     * @return The level with the moving leader and following vehicles.
     */
    private fun buildMovingLeaderFollowingLevel(): Level {
        val followerType = VehicleType("FollowerCar", 36.0, 4.0, 0.7, 3.0)
        val leaderType = VehicleType("LeaderCar", 36.0, 4.0, 0.7, 3.0)
        return buildSameStreetFollowingLevel(
            "MovingLeaderFollowing",
            29,
            followerType,
            leaderType,
            leaderSpeedMs = leaderType.maxSpeed * Simulation.KMH_TO_MS
        )
    }

    /**
     * Builds a same-street pair with different vehicle lengths at the minimum visible bumper gap.
     *
     * @return The level with the mixed length following vehicles.
     */
    private fun buildMixedLengthFollowingLevel(): Level {
        val followerType = VehicleType("FollowerTruck", 36.0, 8.0, 0.7, 3.0)
        val leaderType = VehicleType("LeaderCar", 0.0, 4.0, 0.7, 0.0)
        return buildSameStreetFollowingLevel("MixedLengthFollowing", 30, followerType, leaderType)
    }

    private fun buildSameStreetFollowingLevel(
        name: String,
        index: Int,
        followerType: VehicleType,
        leaderType: VehicleType,
        clearGapM: Double = Simulation.MIN_DISTANCE_M,
        leaderSpeedMs: Double = 0.0
    ): Level {
        val level = Level(name, index)
        val red = Color("Rot", Colors.RED)
        val cfg = SourceNodeConfiguration(red, 9999.0, 0.0, followerType, vehiclesToSpawn = 0)
        val source = SourceNode(scaledPoint(0, 500), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val target = TargetNode(scaledPoint(1000, 500), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(target)
        val street = Street(source, target, listOf(scaledPoint(0, 500), scaledPoint(1000, 500)))
        level.addStreet(street)
        level.validate()

        val followerProgressM = scaledDistance(100.0)
        val leaderProgressM = followerProgressM + clearGapM + (leaderType.length + followerType.length) / 2.0
        val follower = Vehicle(red, followerType, source, cfg, level).apply {
            currentStreet = street
            currentSegmentIndex = 0
            distanceAlongSegment = followerProgressM
            currentSpeed = followerType.maxSpeed * Simulation.KMH_TO_MS
        }
        val leader = Vehicle(red, leaderType, source, cfg, level).apply {
            currentStreet = street
            currentSegmentIndex = 0
            distanceAlongSegment = leaderProgressM
            currentSpeed = leaderSpeedMs
        }
        level.addVehicle(follower)
        level.addVehicle(leader)
        return level
    }

    /**
     * Builds a straight single-incoming connection with one vehicle on the incoming street and one
     * already on the outgoing street. They are topologically adjacent, but not same-street leaders.
     *
     * @return The level with the next street leader.
     */
    private fun buildNextStreetLeaderLevel(): Level {
        val level = Level("NextStreetLeader", 30)
        val red = Color("Rot", Colors.RED)
        val incomingType = VehicleType("IncomingCar", 50.0, 4.0, 0.7, 3.0)
        val nextType = VehicleType("NextStreetCar", 0.0, 4.0, 0.7, 0.0)
        val cfg = SourceNodeConfiguration(red, 9999.0, 0.0, incomingType, vehiclesToSpawn = 0)
        val source = SourceNode(scaledPoint(0, 500), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val connection = ConnectionNode(scaledPoint(800, 500), 0)
        val target = TargetNode(scaledPoint(1000, 500), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(connection)
        level.addNode(target)
        val incoming = Street(source, connection, listOf(scaledPoint(0, 500), scaledPoint(800, 500)))
        val outgoing = Street(connection, target, listOf(scaledPoint(800, 500), scaledPoint(1000, 500)))
        level.addStreet(incoming)
        level.addStreet(outgoing)
        level.validate()

        val follower = Vehicle(red, incomingType, source, cfg, level).apply {
            currentStreet = incoming
            currentSegmentIndex = 0
            distanceAlongSegment = scaledDistance(720.0)
            currentSpeed = incomingType.maxSpeed * Simulation.KMH_TO_MS
        }
        val nextStreetVehicle = Vehicle(red, nextType, source, cfg, level).apply {
            currentStreet = outgoing
            currentSegmentIndex = 0
            distanceAlongSegment = 0.0
            currentSpeed = 0.0
        }
        level.addVehicle(follower)
        level.addVehicle(nextStreetVehicle)
        return level
    }

    /**
     * Remaining distance (metres, 1:250 scale) from a vehicle's centre to the end node of its
     * current street. Mirrors the simulation's own stop-line geometry so tests can assert how
     * far before a node a yielding vehicle comes to rest.
     *
     * @param vehicle The vehicle to calculate the distance for.
     * @return The distance from the centre of the vehicle to the end node of its current street in metres.
     */
    private fun remainingDistanceToNodeM(vehicle: Vehicle): Double {
        val path = vehicle.currentStreet?.path ?: return Double.MAX_VALUE
        if (vehicle.currentSegmentIndex >= path.size - 1) return 0.0
        val first = path[vehicle.currentSegmentIndex]
        val second = path[vehicle.currentSegmentIndex + 1]
        var sumM = kotlin.math.hypot(second.x - first.x, second.y - first.y) - vehicle.distanceAlongSegment
        var i = vehicle.currentSegmentIndex + 1
        while (i < path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            sumM += kotlin.math.hypot(b.x - a.x, b.y - a.y)
            i++
        }
        return sumM
    }

    /**
     * Clear visible bumper-to-tail distance between two vehicles on the same street. The simulation
     * and renderer track the travelled centre position, so both half-lengths are removed from the
     * centre-to-centre distance.
     *
     * @param follower The vehicle in front.
     * @param leader The vehicle behind.
     * @return The distance between the rear bumper of the follower and the front bumper of the leader in metres.
     */
    private fun clearBumperGapM(follower: Vehicle, leader: Vehicle): Double =
        remainingDistanceToNodeM(follower) -
            remainingDistanceToNodeM(leader) -
            (follower.type.length + leader.type.length) / 2.0

    /**
     * Smallest clear distance between any pair of [vehicles]: their rendered centre-to-centre
     * distance minus half of both vehicle lengths. A negative result means two vehicles visually
     * overlap. Shared by the overlap regression tests so each only states which vehicles to compare.
     *
     * @param vehicles the vehicles to compare pairwise.
     * @return the minimum clear distance in metres, or [Double.MAX_VALUE] for fewer than two vehicles.
     */
    private fun minPairwiseClearanceM(vehicles: List<Vehicle>): Double {
        var minClearanceM = Double.MAX_VALUE
        for (i in vehicles.indices) {
            for (j in i + 1 until vehicles.size) {
                val pa = vehicles[i].calculatePosition() ?: continue
                val pb = vehicles[j].calculatePosition() ?: continue
                val centreGapM = kotlin.math.hypot(pa.x - pb.x, pa.y - pb.y)
                val touchingDistanceM = (vehicles[i].type.length + vehicles[j].type.length) / 2.0
                minClearanceM = minOf(minClearanceM, centreGapM - touchingDistanceM)
            }
        }
        return minClearanceM
    }

    /**
     * Remaining distance (metres, 1:250 scale) from a yielding vehicle's visible front bumper to
     * the end node of its current street. The simulation tracks the rendered centre, so the helper
     * subtracts half the vehicle length.
     *
     * @param vehicle The vehicle to calculate the distance for.
     * @return The distance from the front bumper of the vehicle to the end node of its current street.
     */
    private fun frontDistanceToNodeM(vehicle: Vehicle): Double =
        remainingDistanceToNodeM(vehicle) - vehicle.type.length / 2.0

    /**
     * Runs a blocked merge until a yielder of [vehicleType] is stopped and returns the front-bumper
     * distance to the merge node.
     *
     * @param vehicleType The type of vehicle to check for.
     * @return The distance from the front bumper of the stopped yielder to the merge node.
     */
    private fun stoppedYielderFrontDistanceM(vehicleType: VehicleType): Double {
        val level = buildBlockedMergeWithSingleYielderLevel(vehicleType)
        val sim = Simulation(level)
        val incoming = level.nodes.filterIsInstance<ConnectionNode>().single().incomingStreets
        val yielder = level.nodes
            .filterIsInstance<SourceNode>()
            .single { it.position == scaledPoint(300, 500) }

        sim.tick(0.0)

        repeat(300) {
            sim.tick(0.1)
            val vehicle = level.vehicles
                .firstOrNull { it.sourceNode === yielder && it.currentStreet in incoming && it.currentSpeed < 0.1 }
            if (vehicle != null) return frontDistanceToNodeM(vehicle)
        }

        return Double.NaN
    }

    /**
     * Expected centre-to-node distance for a yielding vehicle stopped at a merge: the shared
     * front-bumper stop-line gap translated by half the rendered vehicle length.
     *
     * @param vehicle The vehicle to calculate the distance for.
     * @return The distance from the centre of the vehicle to the stop line in metres.
     */
    private fun expectedMergeStopLineDistanceM(vehicle: Vehicle): Double =
        Simulation.STOP_LINE_FRONT_OFFSET_M + vehicle.type.length / 2.0
}
