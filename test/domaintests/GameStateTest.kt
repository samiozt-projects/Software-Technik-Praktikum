package domaintests

import domain.GameState
import domain.Level
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the global [GameState] singleton's level-assignment side effects.
 *
 * [GameState] is a process-wide object, so each test restores it to a clean, level-less baseline
 * afterwards to avoid leaking state into unrelated tests.
 */
class GameStateTest {

    /** Loading a regular level restores automatic street detection for the new construction. */
    @Test
    fun currentLevel_enablesAutomaticStreetDetectionForNewLevel() {
        GameState.automaticStreetDetectionEnabled = false

        GameState.currentLevel = Level("Neues Level", 1)

        assertTrue(GameState.automaticStreetDetectionEnabled)
    }

    /**
     * Restores the global [GameState] singleton after each test.
     */
    @AfterTest
    fun resetGlobalState() {
        GameState.tutorialActivated = false
        GameState.currentLevel = null
    }

    /**
     * Verifies that switching away from the tutorial clears the tutorial flag.
     */
    @Test
    fun currentLevel_loadingNonTutorialLevel_clearsTutorialActivated() {
        GameState.tutorialActivated = true

        GameState.currentLevel = Level("Strecke 1", 1)

        assertFalse(
            GameState.tutorialActivated,
            "loading a non-Tutorial level must clear the tutorial flag"
        )
    }

    /**
     * Verifies that loading the tutorial level preserves the tutorial flag.
     */
    @Test
    fun currentLevel_loadingTutorialLevel_keepsTutorialActivated() {
        GameState.tutorialActivated = true

        GameState.currentLevel = Level("Tutorial", 0)

        assertTrue(
            GameState.tutorialActivated,
            "loading the Tutorial level must keep the tutorial flag"
        )
    }
}
