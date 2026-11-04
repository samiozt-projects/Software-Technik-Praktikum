package scenestests

import domain.ConnectionState
import domain.GameState
import domain.SimulationState
import domain.TableTile
import korlibs.korge.scene.Scene
import korlibs.korge.scene.SceneContainer
import korlibs.korge.scene.sceneContainer
import korlibs.korge.tests.ViewsForTesting
import korlibs.korge.view.Stage
import korlibs.math.geom.Size
import network.ServerConnection
import scenes.ServerMenuScene
import scenes.WorldPlaceholderScene
import scenes.WorldPlaceholderSceneLogic
import scenes.WorldSelectionScene
import ui.Assets
import ui.ControlButtons
import ui.WorldPlaceholderSceneUI.WorldPlaceholderConfig
import worldeditor.RestWorldEditorGateway
import worldeditor.WorldEditorRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shared scene setup, teardown, and tile-grid interaction helpers for [WorldPlaceholderScene] view
 * tests, split across [WorldPlaceholderSceneInteractionTest] and [WorldPlaceholderSceneEditorLevelTest].
 */
abstract class WorldPlaceholderSceneTestBase : ViewsForTesting(
    frameTime = 50.milliseconds,
    windowSize = Size(1800, 1000)
) {
    protected companion object {
        // Test world ID used for the world placeholder scene tests.
        const val TEST_WORLD_ID = "world-test"
    }

    /** Registers scene prototypes used by typed back-button navigation in the scene under test. */
    @BeforeTest
    fun registerScenePrototypes() {
        injector.mapPrototype { ServerMenuScene() }
        injector.mapPrototype { WorldSelectionScene() }
    }

    /**
     * Resets the global state after each test to ensure isolation.
     * Clears server connection state, world selection, game state, and tutorial flags.
     */
    @AfterTest
    fun resetGlobalState() {
        ServerConnection.reset()
        WorldPlaceholderSceneLogic.resetTestHooks()
        WorldSelectionScene.resetLocalWorldStateForTesting()
        WorldPlaceholderScene.resetLocalStateForTesting()
        WorldEditorRepository.gateway = RestWorldEditorGateway()
        GameState.currentLevel = null
        GameState.simulationState = SimulationState.NO_LEVEL
        GameState.tutorialActivated = false
    }

    /**
     * Prepares the world placeholder scene for testing.
     * Loads assets and sets up the test world entry in the world selection scene.
     */
    protected suspend fun Stage.prepareWorldPlaceholderScene(): SceneContainer {
        Assets.loadAll()
        WorldSelectionScene.openedWorld = WorldSelectionScene.WorldEntry(
            TEST_WORLD_ID,
            1,
            "Testwelt",
            "Server"
        )
        val container = sceneContainer()
        container.changeTo { WorldPlaceholderScene() }
        return container
    }

    /** Selects the first free tile and returns its model entry. */
    protected suspend fun selectFirstFreeTile(): TableTile {
        val tileIndex = firstFreeTileIndex()
        val tile = ServerConnection.tilesFor(TEST_WORLD_ID)[tileIndex]
        clickFreeTile(tileIndex)
        delayFrame()
        return tile
    }

    /** Clicks "Auswählen" for the current pending tile and waits until it is confirmed. */
    protected suspend fun confirmPendingTileSelection() {
        clickTileSelectionActionButton(confirmed = false)
        awaitFrames { WorldPlaceholderScene.confirmedTileIdFor(TEST_WORLD_ID) != null }
    }

    /**
     * Clicks "Auswählen" for the current pending tile when the registration is expected to fail,
     * so there is no confirmed-tile state to wait for.
     */
    protected suspend fun attemptPendingTileSelection() {
        clickTileSelectionActionButton(confirmed = false)
        repeat(4) { delayFrame() }
    }

    /**
     * Clicks a free tile at the specified grid index.
     *
     * @param index The grid index of the tile to click.
     */
    protected suspend fun clickFreeTile(index: Int) {
        val col = index % WorldPlaceholderConfig.TILE_GRID_COLUMNS
        val row = index / WorldPlaceholderConfig.TILE_GRID_COLUMNS
        val x = WorldPlaceholderConfig.TILE_GRID_X +
            col * (WorldPlaceholderConfig.TILE_SIZE + WorldPlaceholderConfig.TILE_GAP) +
            WorldPlaceholderConfig.TILE_SIZE / 2.0
        val y = WorldPlaceholderConfig.TILE_GRID_Y +
            row * (WorldPlaceholderConfig.TILE_SIZE + WorldPlaceholderConfig.TILE_GAP) +
            WorldPlaceholderConfig.TILE_SIZE / 2.0

        clickAt(x, y)
    }

    /** Returns the y-coordinate of the tile grid's bottom edge for the current tile count. */
    private fun gridBottomY(): Double {
        val tileCount = ServerConnection.tilesFor(TEST_WORLD_ID).size
        val rowCount = (tileCount + WorldPlaceholderConfig.TILE_GRID_COLUMNS - 1) /
            WorldPlaceholderConfig.TILE_GRID_COLUMNS
        return WorldPlaceholderConfig.TILE_GRID_Y +
            rowCount * (WorldPlaceholderConfig.TILE_SIZE + WorldPlaceholderConfig.TILE_GAP)
    }

    /** Returns the x-coordinate of the tile grid's horizontal center. */
    private fun gridCenterX(): Double =
        WorldPlaceholderConfig.TILE_GRID_X +
            (WorldPlaceholderConfig.TILE_GRID_COLUMNS *
                (WorldPlaceholderConfig.TILE_SIZE + WorldPlaceholderConfig.TILE_GAP) -
                WorldPlaceholderConfig.TILE_GAP) / 2.0

    /**
     * Clicks the tile-selection action button.
     *
     * @param confirmed when false, clicks the single centered "Auswählen" button; when true, the
     *     row instead shows "Entfernen" and "Weiter" side by side, so this clicks the left
     *     ("Entfernen") button at its actual paired position.
     */
    protected suspend fun clickTileSelectionActionButton(confirmed: Boolean) {
        val y = gridBottomY() + WorldPlaceholderConfig.TILE_GAP + WorldPlaceholderConfig.ACTION_BUTTON_H / 2.0
        val x = if (confirmed) {
            val pairWidth = WorldPlaceholderConfig.TILE_ACTION_BUTTON_W * 2 +
                WorldPlaceholderConfig.ACTION_BUTTON_ROW_GAP
            gridCenterX() - pairWidth / 2.0 + WorldPlaceholderConfig.TILE_ACTION_BUTTON_W / 2.0
        } else {
            gridCenterX()
        }

        clickAt(x, y)
    }

    /** Clicks the right ("Weiter") button of the confirmed tile action row. */
    protected suspend fun clickTileNavigateButton() {
        val pairWidth = WorldPlaceholderConfig.TILE_ACTION_BUTTON_W * 2 + WorldPlaceholderConfig.ACTION_BUTTON_ROW_GAP
        val y = gridBottomY() + WorldPlaceholderConfig.TILE_GAP + WorldPlaceholderConfig.ACTION_BUTTON_H / 2.0
        val x = gridCenterX() + pairWidth / 2.0 - WorldPlaceholderConfig.TILE_ACTION_BUTTON_W / 2.0

        clickAt(x, y)
    }

    /**
     * Clicks at the specified coordinates.
     *
     * @param x The x-coordinate to click at.
     * @param y The y-coordinate to click at.
     */
    protected suspend fun clickAt(x: Double, y: Double) {
        mouseMoveTo(x.toInt(), y.toInt())
        delayFrame()
        mouseClick()
        delayFrame()
    }

    /** Clicks the shared back button at its centered position. */
    protected suspend fun clickBackButton() {
        clickAt(
            ControlButtons.BACK_BUTTON_X + ControlButtons.BACK_BUTTON_W / 2.0,
            ControlButtons.CORNER_OFFSET + ControlButtons.BUTTON_SIZE / 2.0
        )
    }

    /** Polls until [container]'s current scene is of type [T]. */
    protected suspend inline fun <reified T : Scene> waitForCurrentScene(
        container: SceneContainer,
        maxFrames: Int = 80
    ) {
        var frame = 0
        while (container.currentScene !is T && frame < maxFrames) {
            delayFrame()
            frame++
        }
    }

    /** Pumps frames until [condition] holds or a generous frame budget runs out. */
    protected suspend fun awaitFrames(maxFrames: Int = 240, condition: () -> Boolean) {
        var frames = 0
        while (!condition() && frames < maxFrames) {
            delayFrame()
            frames++
        }
    }

    /** Returns the index of the first tile not occupied by another table. */
    protected fun firstFreeTileIndex(): Int =
        ServerConnection.tilesFor(TEST_WORLD_ID).indexOfFirst { !it.occupiedByOtherTable }
}
