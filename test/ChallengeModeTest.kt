import domain.ChallengeMode
import domain.ChallengeResult
import domain.Color
import domain.ConnectionNode
import domain.GameState
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import simulation.Simulation
import domain.Street
import domain.TargetNode
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the challenge-mode rules and their edge cases.
 */
class ChallengeModeTest {

    private fun scaledPoint(xPx: Number, yPx: Number): Point =
        Point(xPx.toDouble() / Simulation.PX_PER_METER, yPx.toDouble() / Simulation.PX_PER_METER)

    /**
     * Clears global state touched by challenge tests.
     */
    @AfterTest
    fun tearDown() {
        GameState.currentLevel = null
        ChallengeMode.deactivate()
    }

    /**
     * Verifies that the mode accepts the exact maximum number of crossings.
     */
    @Test
    fun canActivate_acceptsExactConnectionNodeLimit() {
        val level = levelWithConnectionNodes(Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT)

        assertTrue(ChallengeMode.canActivate(level))
    }

    /**
     * Verifies that the mode rejects levels above the crossing limit.
     */
    @Test
    fun canActivate_rejectsMoreThanConnectionNodeLimit() {
        val level = levelWithConnectionNodes(Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT + 1)

        assertFalse(ChallengeMode.canActivate(level))
    }

    /**
     * Verifies that a level can define a stricter crossing limit than the default.
     */
    @Test
    fun canActivate_usesStricterLevelSpecificConnectionNodeLimit() {
        val level = levelWithConnectionNodes(connectionNodeCount = 3, challengeConnectionNodeLimit = 2)

        assertFalse(ChallengeMode.canActivate(level))
    }

    /**
     * Verifies that a level can define a more generous crossing limit than the default.
     */
    @Test
    fun canActivate_usesMoreGenerousLevelSpecificConnectionNodeLimit() {
        val level = levelWithConnectionNodes(connectionNodeCount = 5, challengeConnectionNodeLimit = 6)

        assertTrue(ChallengeMode.canActivate(level))
    }

    /**
     * Verifies that normal validation is unaffected while challenge mode is inactive.
     */
    @Test
    fun validationErrors_returnsEmptyWhenChallengeModeIsInactive() {
        val level = levelWithConnectionNodes(Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT + 1)

        assertTrue(ChallengeMode.validationErrors(level).isEmpty())
    }

    /**
     * Verifies that active challenge mode adds a validation error above the crossing limit.
     */
    @Test
    fun levelValidate_reportsChallengeErrorWhenActiveAndLimitExceeded() {
        val validLevel = levelWithConnectionNodes(Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT)
        assertTrue(ChallengeMode.activate(validLevel))

        val invalidLevel = levelWithConnectionNodes(Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT + 1)
        val errors = invalidLevel.validate()

        assertTrue(errors.any { it.message.contains("Challenge mode allows at most") })
    }

    /**
     * Verifies that activating challenge mode resets edited source and vehicle values.
     */
    @Test
    fun activate_resetsConfigurationToOriginalSnapshot() {
        val level = levelWithConnectionNodes(0)
        GameState.currentLevel = level
        val source = level.nodes.filterIsInstance<SourceNode>().single()
        val original = source.configurations.single()

        original.vehicleType.maxSpeed = 5.0
        original.vehicleType.acceleration = 1.0
        source.configurations = listOf(
            SourceNodeConfiguration(
                color = original.color,
                generationSpeed = 99.0,
                initialVehicleSpeed = 7.0,
                vehicleType = original.vehicleType
            )
        )

        assertTrue(ChallengeMode.activate(level))

        val restored = source.configurations.single()
        assertEquals(30.0, restored.generationSpeed)
        assertEquals(50.0, restored.initialVehicleSpeed)
        assertEquals(80.0, restored.vehicleType.maxSpeed)
        assertEquals(3.0, restored.vehicleType.acceleration)
    }

    /**
     * Verifies that a level with the exact four-crossing limit is not only accepted
     * by the rule check, but can also be completed by the simulation.
     */
    @Test
    fun fourConnectionNodeChallenge_canBeCompletedBySimulation() {
        val level = levelWithConnectionNodes(Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT)
        GameState.currentLevel = level

        assertEquals(4, ChallengeMode.connectionNodeCount(level))
        assertTrue(ChallengeMode.activate(level))

        val simulation = Simulation(level)
        simulation.tick(0.0)
        repeat(20) {
            simulation.tick(1.0)
        }

        assertTrue(simulation.levelCompleted)
    }

    /**
     * Verifies that inactive challenge mode never displays an outcome overlay.
     */
    @Test
    fun resultFor_returnsNullWhenChallengeModeIsInactive() {
        val level = levelWithConnectionNodes(0)

        assertNull(ChallengeMode.resultFor(level, levelCompleted = true, simulationStarted = true))
    }

    /**
     * Verifies that an active but unfinished challenge does not show an end result.
     */
    @Test
    fun resultFor_returnsNullWhileChallengeIsStillRunning() {
        val level = levelWithConnectionNodes(0)
        GameState.currentLevel = level
        assertTrue(ChallengeMode.activate(level))

        assertNull(ChallengeMode.resultFor(level, levelCompleted = false, simulationStarted = true))
    }

    /**
     * Verifies the success result and visible success text after regular completion.
     */
    @Test
    fun resultFor_returnsCompletedWhenLevelIsCompletedWithinRules() {
        val level = levelWithConnectionNodes(0)
        GameState.currentLevel = level
        assertTrue(ChallengeMode.activate(level))

        val result = ChallengeMode.resultFor(level, levelCompleted = true, simulationStarted = true)

        assertEquals(ChallengeResult.COMPLETED, result)
        assertEquals("Challenge geschafft", ChallengeMode.resultText(result))
    }

    /**
     * Verifies that a successfully completed challenge automatically ends challenge mode.
     */
    @Test
    fun resultForAndDeactivateOnCompleted_deactivatesAfterSuccess() {
        val level = levelWithConnectionNodes(0)
        GameState.currentLevel = level
        assertTrue(ChallengeMode.activate(level))

        val result = ChallengeMode.resultForAndDeactivateOnCompleted(
            level,
            levelCompleted = true,
            simulationStarted = true
        )

        assertEquals(ChallengeResult.COMPLETED, result)
        assertFalse(ChallengeMode.active)
    }

    /**
     * Verifies that failed challenges remain active after result evaluation.
     */
    @Test
    fun resultForAndDeactivateOnCompleted_keepsFailedChallengeActive() {
        val validLevel = levelWithConnectionNodes(0)
        GameState.currentLevel = validLevel
        assertTrue(ChallengeMode.activate(validLevel))

        val invalidLevel = levelWithConnectionNodes(Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT + 1)
        val result = ChallengeMode.resultForAndDeactivateOnCompleted(
            invalidLevel,
            levelCompleted = true,
            simulationStarted = true
        )

        assertEquals(ChallengeResult.FAILED, result)
        assertTrue(ChallengeMode.active)
    }

    /**
     * Verifies that violating the crossing rule during construction does not produce an end result.
     */
    @Test
    fun resultFor_returnsNullWhenRulesAreViolatedBeforeSimulationStart() {
        val validLevel = levelWithConnectionNodes(0)
        GameState.currentLevel = validLevel
        assertTrue(ChallengeMode.activate(validLevel))

        val invalidLevel = levelWithConnectionNodes(Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT + 1)

        assertNull(ChallengeMode.resultFor(invalidLevel, levelCompleted = false, simulationStarted = false))
    }

    /**
     * Verifies that violating the crossing rule produces a failed result after simulation start.
     */
    @Test
    fun resultFor_returnsFailedWhenRulesAreViolatedAfterSimulationStart() {
        val validLevel = levelWithConnectionNodes(0)
        GameState.currentLevel = validLevel
        assertTrue(ChallengeMode.activate(validLevel))

        val invalidLevel = levelWithConnectionNodes(Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT + 1)
        val result = ChallengeMode.resultFor(invalidLevel, levelCompleted = false, simulationStarted = true)

        assertEquals(ChallengeResult.FAILED, result)
        assertEquals("Challenge nicht geschafft", ChallengeMode.resultText(result))
    }

    /**
     * Verifies that rule violations take precedence over level completion.
     */
    @Test
    fun resultFor_prefersFailedWhenCompletedLevelViolatesRules() {
        val validLevel = levelWithConnectionNodes(0)
        GameState.currentLevel = validLevel
        assertTrue(ChallengeMode.activate(validLevel))

        val invalidLevel = levelWithConnectionNodes(Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT + 1)

        assertEquals(
            ChallengeResult.FAILED,
            ChallengeMode.resultFor(invalidLevel, levelCompleted = true, simulationStarted = true)
        )
    }

    /**
     * Builds a minimal valid level with [connectionNodeCount] connection nodes.
     */
    private fun levelWithConnectionNodes(
        connectionNodeCount: Int,
        challengeConnectionNodeLimit: Int = Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT
    ): Level {
        val level = Level(
            name = "Challenge Test",
            number = 1,
            challengeConnectionNodeLimit = challengeConnectionNodeLimit
        )
        val red = Color("Rot", Colors.RED)
        val vehicleType = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0, "images/car.png")
        val configuration = SourceNodeConfiguration(red, 30.0, 50.0, vehicleType, vehiclesToSpawn = 1)
        val source = SourceNode(scaledPoint(0, 0), 0, Angle.ZERO, listOf(configuration))
        val target = TargetNode(scaledPoint(1000, 0), 0, Angle.ZERO, red)

        level.addNode(source)
        val connectionNodes = (1..connectionNodeCount).map { index ->
            ConnectionNode(scaledPoint(index * 100, 0), 0).also { level.addNode(it) }
        }
        level.addNode(target)

        val route = listOf(source) + connectionNodes + target
        route.zipWithNext().forEach { (start, end) ->
            level.addStreet(Street(start, end, listOf(start.position, end.position)))
        }
        level.validate()
        return level
    }
}
