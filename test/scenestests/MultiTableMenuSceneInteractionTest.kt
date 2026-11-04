package scenestests

import domain.ConnectionState
import domain.GameState
import domain.Level
import network.ServerConnection
import domain.SimulationState
import korlibs.io.file.baseName
import korlibs.io.file.std.applicationVfs
import korlibs.korge.scene.SceneContainer
import korlibs.korge.scene.sceneContainer
import korlibs.korge.tests.ViewsForTesting
import korlibs.korge.view.Stage
import korlibs.math.geom.Size
import scenes.ConfigurationScene
import scenes.ConfigurationBackDestination
import scenes.LevelMenuScene
import scenes.LevelSaveResult
import scenes.LevelScene
import scenes.MenuScene
import scenes.MultiTableMenuScene
import scenes.SavedNetworksScene
import scenes.ServerMenuScene
import scenes.WorldSelectionBackDestination
import scenes.WorldSelectionScene
import scenes.WorldScene
import ui.Assets
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Interaction-driven tests for [MultiTableMenuScene]'s button navigation.
 *
 * Verifies each of the four mockup buttons reaches its target scene, and that closing the menu
 * returns to [LevelScene], matching the acceptance criteria for the multi-table pause menu.
 */
class MultiTableMenuSceneInteractionTest : ViewsForTesting(
    frameTime = 50.milliseconds,
    windowSize = Size(1800, 1000)
) {

    /**
     * Registers the scene prototypes this test navigates into through typed `pushTo`/`changeTo`
     * calls, mirroring the registrations [main] performs for the running app. [ViewsForTesting]
     * does not set these up on its own, unlike `sceneContainer().changeTo { X() }`.
     */
    @BeforeTest
    fun registerScenePrototypes() {
        injector.mapPrototype { LevelScene() }
        injector.mapPrototype { LevelMenuScene() }
        injector.mapPrototype { ConfigurationScene() }
        injector.mapPrototype { ServerMenuScene() }
        injector.mapPrototype { WorldSelectionScene() }
        injector.mapPrototype { WorldScene() }
    }

    /** Resets process-wide singletons after each test to prevent state leaking into the next. */
    @AfterTest
    fun resetGlobalState() {
        GameState.currentLevel = null
        GameState.simulationState = SimulationState.NO_LEVEL
        ServerConnection.reset()
        ConfigurationScene.backDestination = scenes.ConfigurationBackDestination.GAMEPLAY_SCENE
        ServerMenuScene.backTarget = ServerMenuScene.BackTarget.MENU_SCENE
        WorldSelectionScene.resetLocalWorldStateForTesting()
    }

    /**
     * Verifies that the save action persists the current world to the multi-table world
     * directory ([SavedNetworksScene.SAVED_WORLD_DIRECTORY]) without opening a confirmation
     * scene. Saving to the world directory (not the normal level directory) is what makes the saved
     * world appear in [WorldSelectionScene] rather than in [SavedNetworksScene].
     *
     * Invokes [MenuScene.saveCurrentLevel] directly (properly awaited) rather than
     * through a simulated click: the save flow reads and parses every existing saved file to detect
     * duplicate saves, which is slow enough that the click handler's `launch { ... }` on an
     * unstructured background dispatcher becomes unpredictably racy against the test's frame clock.
     * The save button's own click wiring is a one-line `launch { ... }` call to this same function,
     * so this still exercises the real save-and-navigate behavior end to end.
     */
    @Test
    fun saveButton_savesCurrentWorldAndStaysInMenu() = viewsTest {
        val levelName = "MultiTableMenuSaveInteractionTest"
        try {
            val container = prepareMultiTableMenuScene(levelName)

            val result = MenuScene.saveCurrentLevel()

            assertIs<LevelSaveResult.Saved>(result)
            assertIs<MultiTableMenuScene>(container.currentScene)
            val savedFiles = savedWorldFilesStartingWith(levelName)
            assertTrue(savedFiles.isNotEmpty(), "the save button must write a new saved-world file in the multi-table world directory")
        } finally {
            // Runs even if an assertion above fails, so a broken test run never leaves a stray
            // file behind in the real save directory used by the running app.
            savedWorldFilesStartingWith(levelName).forEach {
                applicationVfs[SavedNetworksScene.savedWorldPath(it)].delete()
            }
        }
    }

    /**
     * Verifies that the world-selection button leads to [WorldSelectionScene].
     */
    @Test
    fun worldSelectionButton_opensWorldSelectionScene() = viewsTest {
        val container = prepareMultiTableMenuScene("Unrelated Level")

        MultiTableMenuScene.openWorldSelection(container)

        assertIs<WorldSelectionScene>(container.currentScene)
        assertEquals(WorldSelectionBackDestination.MULTI_TABLE_MENU_SCENE, WorldSelectionScene.backDestination)
    }

    /**
     * Verifies that the vehicle-configuration button leads to [ConfigurationScene].
     */
    @Test
    fun configurationButton_opensConfigurationScene() = viewsTest {
        val container = prepareMultiTableMenuScene("Unrelated Level")

        MultiTableMenuScene.openConfiguration(container)

        assertIs<ConfigurationScene>(container.currentScene)
        assertEquals(ConfigurationBackDestination.MULTI_TABLE_MENU_SCENE, ConfigurationScene.backDestination)
    }

    /**
     * Verifies that the globe button still leads to the multi-table-mode scene.
     */
    @Test
    fun serverButton_opensServerMenuScene() = viewsTest {
        val container = prepareMultiTableMenuScene("Unrelated Level")

        MultiTableMenuScene.openServerMenu(container)

        assertIs<ServerMenuScene>(container.currentScene)
        assertEquals(ServerMenuScene.BackTarget.MULTI_TABLE_MENU_SCENE, ServerMenuScene.backTarget)
    }

    /**
     * Verifies that closing the menu returns to [WorldScene], the active multi-table world.
     */
    @Test
    fun closeButton_returnsToWorldScene() = viewsTest {
        val container = prepareMultiTableMenuScene("Unrelated Level")

        MultiTableMenuScene.closeToWorld(container)

        assertIs<WorldScene>(container.currentScene)
    }

    /**
     * Loads assets, assigns a level, sets [ConnectionState.CONNECTED] (matching the precondition
     * for [MultiTableMenuScene] to be shown), and renders [MultiTableMenuScene] directly.
     */
    private suspend fun Stage.prepareMultiTableMenuScene(levelName: String): SceneContainer {
        removeChildren()
        Assets.loadAll()
        GameState.currentLevel = Level(levelName, 1)
        GameState.simulationState = SimulationState.CONSTRUCTION
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        val container = sceneContainer()
        container.changeTo { MultiTableMenuScene() }
        // MultiTableMenuScene draws its buttons inside an unscoped `launch` in sceneMain(); a
        // settling buffer lets that coroutine complete before the test tries to click anything.
        repeat(5) { delayFrame() }
        return container
    }

    /** Returns saved-world file base names (from [SavedNetworksScene.SAVED_WORLD_DIRECTORY]) that start with [levelName]. */
    private suspend fun savedWorldFilesStartingWith(levelName: String): List<String> {
        val saveDirectory = applicationVfs[SavedNetworksScene.SAVED_WORLD_DIRECTORY]
        if (!saveDirectory.exists()) return emptyList()
        return saveDirectory.listSimple().map { it.baseName }.filter { it.startsWith(levelName) }
    }

}
