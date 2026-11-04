package domaintests

import domain.Color
import domain.GameState
import domain.Level
import simulation.Simulation
import domain.SimulationState
import domain.SourceNode
import domain.TargetNode
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the scene-recovery helpers added for issue #184: a scene transition must no longer crash
 * with "no level loaded in GameState", the recovery must leave a consistent global state, the
 * diagnostic message must name the actually affected scene, and overlapping transitions must be
 * blocked.
 */
class GameStateSceneRecoveryTest {

    /**
     * Builds a minimal valid level so a simulation can be constructed for the state-normalization test.
     */
    private fun createTestLevel(): Level {
        val level = Level("Test Level", 1)
        val source = SourceNode(Point(0.0, 0.0), 0, Angle.ZERO)
        val target = TargetNode(Point(10.0, 10.0), 0, Angle.ZERO, Color("TestColor", Colors.RED))
        level.addNode(source)
        level.addNode(target)
        return level
    }

    /**
     * Resets the shared game state to the documented level-less baseline before each test.
     */
    @BeforeTest
    fun resetGameState() {
        GameState.currentLevel = null
        GameState.simulationState = SimulationState.NO_LEVEL
        GameState.activeSimulation = null
        GameState.endSceneTransition()
    }

    /**
     * Leaves the shared game state clean so other scene tests are not affected by leftover state.
     */
    @AfterTest
    fun cleanUpGameState() {
        GameState.currentLevel = null
        GameState.simulationState = SimulationState.NO_LEVEL
        GameState.activeSimulation = null
        GameState.endSceneTransition()
    }

    /**
     * Verifies that the diagnostic message names LevelScene when LevelScene reports the issue (AC3).
     */
    @Test
    fun noLevelLoadedMessage_namesLevelScene() {
        assertEquals(
            "LevelScene: no level loaded in GameState",
            GameState.noLevelLoadedMessage("LevelScene")
        )
    }

    /**
     * Verifies that the same message names TutorialScene when the tutorial reports the issue (AC3).
     */
    @Test
    fun noLevelLoadedMessage_namesTutorialScene() {
        assertEquals(
            "TutorialScene: no level loaded in GameState",
            GameState.noLevelLoadedMessage("TutorialScene")
        )
    }

    /**
     * Verifies that a missing level yields a null recovery signal instead of throwing (AC1).
     */
    @Test
    fun levelForSceneOrRecover_withoutLevelReturnsNullInsteadOfThrowing() {
        GameState.currentLevel = null

        assertNull(GameState.levelForSceneOrRecover("LevelScene"))
    }

    /**
     * Verifies that a missing level normalizes the global state back to a consistent baseline so a
     * stale simulation cannot survive an overlapping or premature scene change (AC2).
     */
    @Test
    fun levelForSceneOrRecover_withoutLevelNormalizesGameState() {
        val level = createTestLevel()
        GameState.currentLevel = null
        GameState.activeSimulation = Simulation(level)
        GameState.simulationState = SimulationState.SIMULATION

        GameState.levelForSceneOrRecover("LevelScene")

        assertNull(GameState.activeSimulation, "Recovery must clear any stale simulation")
        assertEquals(SimulationState.NO_LEVEL, GameState.simulationState, "Recovery must reset to NO_LEVEL")
    }

    /**
     * Verifies that under normal use a loaded level is returned unchanged and the state is untouched (AC4).
     */
    @Test
    fun levelForSceneOrRecover_returnsLoadedLevelAndKeepsState() {
        val level = createTestLevel()
        GameState.currentLevel = level
        GameState.simulationState = SimulationState.CONSTRUCTION

        val result = GameState.levelForSceneOrRecover("LevelScene")

        assertSame(level, result, "A loaded level must be returned as-is")
        assertEquals(SimulationState.CONSTRUCTION, GameState.simulationState, "Normal use must not reset the state")
    }

    /**
     * Verifies that only one scene transition can be claimed at a time and the slot is released (AC2).
     */
    @Test
    fun sceneTransitionGuard_preventsOverlappingTransitions() {
        assertTrue(GameState.tryBeginSceneTransition(), "The first transition must be allowed")
        assertFalse(GameState.tryBeginSceneTransition(), "An overlapping transition must be blocked")

        GameState.endSceneTransition()

        assertTrue(GameState.tryBeginSceneTransition(), "After release a new transition must be allowed")
        GameState.endSceneTransition()
    }
}
