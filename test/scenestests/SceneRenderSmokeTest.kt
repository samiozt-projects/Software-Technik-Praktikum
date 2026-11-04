package scenestests

import domain.Color
import domain.GameState
import domain.Level
import domain.SimulationState
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.TargetNode
import domain.VehicleType
import domain.WeatherCondition
import korlibs.image.color.Colors
import korlibs.korge.scene.Scene
import korlibs.korge.scene.sceneContainer
import korlibs.korge.tests.ViewsForTesting
import korlibs.korge.view.Stage
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import korlibs.math.geom.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import scenes.ConfigurationScene
import scenes.LevelMenuScene
import scenes.LevelScene
import scenes.MenuScene
import scenes.MultiTableMenuScene
import scenes.SavedNetworksScene
import scenes.ServerMenuScene
import scenes.SourceConfigDetailScene
import scenes.TutorialScene
import scenes.WeatherConfigurationScene
import scenes.WorldEditorScene
import scenes.WorldPlaceholderScene
import scenes.WorldSelectionScene
import ui.Assets
import worldeditor.WorldEditorConfigurationContext
import worldeditor.WorldEditorSession
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Smoke tests that actually render each KorGE [Scene] through the KorGE view-test harness
 * ([ViewsForTesting]). Unlike the layout-helper tests (e.g. [MenuSceneTest], [TutorialSceneTest]),
 * these drive the full `sceneMain` body, which is otherwise never exercised by the suite.
 *
 * Each test only asserts that the scene builds and renders without throwing; this is deliberate —
 * the goal is coverage of the construction/rendering path and a guard against regressions that make
 * a scene fail to load at all. Behavioural assertions stay in the dedicated per-scene tests.
 *
 * The window size mirrors the production [main] (1800×1000) so layout code runs against the real
 * projection dimensions. [GameState] is a process-wide object, so each test restores it afterwards.
 */
class SceneRenderSmokeTest : ViewsForTesting(windowSize = Size(1800, 1000)) {

    /**
     * Restores global scene state after each render smoke test.
     */
    @AfterTest
    fun resetGlobalState() {
        GameState.currentLevel = null
        GameState.simulationState = SimulationState.NO_LEVEL
        GameState.tutorialActivated = false
        WorldSelectionScene.resetLocalWorldStateForTesting()
    }

    /**
     * Verifies that the main menu scene can be constructed and rendered.
     */
    @Test
    fun menuScene_renders() = viewsTest {
        prepareLoadedLevel()
        renderScene { MenuScene() }
    }

    /**
     * Verifies that the level scene can be constructed and rendered.
     */
    @Test
    fun levelScene_renders() = viewsTest {
        prepareLoadedLevel()
        renderScene { LevelScene() }
    }

    /**
     * Verifies that the multi-table pause menu scene can be constructed and rendered.
     */
    @Test
    fun multiTableMenuScene_renders() = viewsTest {
        prepareLoadedLevel()
        renderScene { MultiTableMenuScene() }
    }

    /**
     * Verifies that the predefined level menu scene can be constructed and rendered.
     */
    @Test
    fun levelMenuScene_renders() = viewsTest {
        prepareAssets()
        renderScene { LevelMenuScene() }
    }

    /**
     * Verifies that the saved-networks scene can be constructed and rendered.
     */
    @Test
    fun savedNetworksScene_renders() = viewsTest {
        prepareAssets()
        renderScene { SavedNetworksScene() }
    }

    /**
     * Verifies that the source configuration overview scene can be constructed and rendered.
     */
    @Test
    fun configurationScene_renders() = viewsTest {
        prepareLoadedLevel()
        renderScene { ConfigurationScene() }
    }

    /**
     * Verifies that the server placeholder scene can be constructed and rendered.
     */
    @Test
    fun serverMenuScene_renders() = viewsTest {
        prepareLoadedLevel()
        renderScene { ServerMenuScene() }
    }

    /**
     * Verifies that the world selection scene can be constructed and rendered.
     */
    @Test
    fun worldSelectionScene_renders() = viewsTest {
        prepareAssets()
        renderScene { WorldSelectionScene() }
    }

    /**
     * Verifies that the world editor scene can be constructed and rendered.
     */
    @Test
    fun worldEditorScene_renders() = viewsTest {
        prepareAssets()
        renderScene { WorldEditorScene(editorSession(sampleLevel())) }
    }

    /**
     * Verifies that the configuration menu renders on the world editor's working level without
     * touching [GameState.currentLevel] (issue #227 follow-up: config menu from the editor).
     */
    @Test
    fun configurationSceneInWorldEditorContext_rendersWithoutGameStateLevel() = viewsTest {
        prepareAssets()
        WorldEditorConfigurationContext.open(editorSession(sampleLevel()))
        try {
            renderScene { ConfigurationScene() }
        } finally {
            WorldEditorConfigurationContext.clear()
        }
    }

    /**
     * Verifies that the configuration menu preloads the vehicle graphics of the displayed level,
     * so a type added in the world editor (e.g. Motorrad) shows its own icon instead of the
     * generic car fallback (issue #227 follow-up).
     */
    @Test
    fun configurationScene_preloadsVehicleGraphicsOfEditedLevel() = viewsTest {
        prepareAssets()
        val motorrad = worldeditor.WorldEditorDefaults.vehicleTypes.first { it.name == "Motorrad" }
        WorldEditorConfigurationContext.open(editorSession(levelWithSingleSource(motorrad)))
        try {
            renderScene { ConfigurationScene() }
            awaitVehicleGraphic(motorrad.graphicRef)
            assertNotSame(Assets.car, Assets.getVehicleGraphic(motorrad.graphicRef))
        } finally {
            WorldEditorConfigurationContext.clear()
        }
    }

    /** Builds a world-editor session around [level] for editor-context rendering tests. */
    private fun editorSession(level: Level): WorldEditorSession = WorldEditorSession.fromLevel(
        worldId = "world-1",
        worldName = level.name,
        templateLevelId = 1,
        isNewWorld = false,
        sourceLevel = level
    )

    /** Builds a level with one source spawning [vehicleType], for graphics-preload tests. */
    private fun levelWithSingleSource(vehicleType: VehicleType): Level {
        val level = Level("Motorrad Welt", 1, weatherCondition = WeatherCondition.DRY)
        level.addNode(
            SourceNode(
                Point(100, 100), 0, Angle.ZERO,
                listOf(SourceNodeConfiguration(Color("Rot", Colors.RED), 1.0, 10.0, vehicleType))
            )
        )
        return level
    }

    /**
     * Polls until [graphicRef] is preloaded or the frame budget is exhausted.
     *
     * sceneMain runs unscoped after changeTo returns and the PNG decode happens on a real I/O
     * thread, while [delayFrame] only advances the virtual frame clock in near-zero real time —
     * so the loop also sleeps a few real milliseconds per iteration.
     */
    private suspend fun awaitVehicleGraphic(graphicRef: String) {
        repeat(MAX_ASSET_WAIT_POLLS) {
            if (Assets.getVehicleGraphic(graphicRef) !== Assets.car) return
            realTimeDelay(ASSET_POLL_SLEEP_MS)
        }
        assertTrue(Assets.getVehicleGraphic(graphicRef) !== Assets.car, "Vehicle graphic '$graphicRef' should preload")
    }

    /** Suspends for [milliseconds] of real wall-clock time without blocking the test thread. */
    private suspend fun realTimeDelay(milliseconds: Long) {
        withContext(Dispatchers.Default) {
            delay(milliseconds)
        }
    }

    /** Awaits KorGE's unscoped [Scene.sceneMain] coroutine before asserting that the scene rendered. */
    private suspend inline fun <reified T : Scene> Stage.renderScene(crossinline factory: () -> T): T {
        val scene = sceneContainer().changeTo { factory() }
        var waits = 0
        while (scene.sceneView.children.isEmpty() && waits < MAX_SCENE_WAIT_YIELDS) {
            waits++
            yield()
        }
        assertTrue(scene.sceneView.children.isNotEmpty(), "${scene::class.simpleName} should render at least one view")
        return scene
    }

    /**
     * Verifies that the world placeholder scene can be constructed and rendered.
     */
    @Test
    fun worldPlaceholderScene_renders() = viewsTest {
        prepareAssets()
        WorldSelectionScene.openedWorld = WorldSelectionScene.PLACEHOLDER_WORLDS.first()
        renderScene { WorldPlaceholderScene() }
    }

    /**
     * Verifies that the weather configuration scene can be constructed and rendered.
     */
    @Test
    fun weatherConfigurationScene_renders() = viewsTest {
        prepareLoadedLevel()
        renderScene { WeatherConfigurationScene() }
    }

    /**
     * Verifies that the tutorial scene can be constructed and rendered.
     */
    @Test
    fun tutorialScene_renders() = viewsTest {
        prepareLoadedLevel()
        GameState.tutorialActivated = true
        renderScene { TutorialScene() }
    }

    /**
     * Verifies that the source-configuration detail scene can be constructed and rendered.
     */
    @Test
    fun sourceConfigDetailScene_renders() = viewsTest {
        prepareLoadedLevel()
        val car = VehicleType("Car", 50.0, 4.0, 0.8, 3.0)
        val red = Color("Rot", Colors.RED)
        val config = SourceNodeConfiguration(red, 1.0, 10.0, car)
        val source = SourceNode(Point(100, 100), 0, Angle.ZERO, listOf(config))
        renderScene { SourceConfigDetailScene(source, "Quelle 1", config) }
    }

    /** Loads shared assets; required before any scene that renders sprites or text. */
    private suspend fun prepareAssets() {
        Assets.loadAll()
    }

    /** Loads assets and installs a fully built level into [GameState] for level-bound scenes. */
    private suspend fun prepareAssets(level: Level) {
        Assets.loadAll()
        GameState.currentLevel = level
        GameState.simulationState = SimulationState.CONSTRUCTION
    }

    /**
     * Loads assets and installs a sample level for scenes that require [GameState.currentLevel].
     */
    private suspend fun prepareLoadedLevel() = prepareAssets(sampleLevel())

    /** Minimal but complete level: one configured source, a matching target and a connecting street. */
    private fun sampleLevel(): Level {
        val car = VehicleType("Car", 50.0, 4.0, 0.8, 3.0)
        val red = Color("Rot", Colors.RED)
        val source = SourceNode(
            Point(100, 100), 0, Angle.ZERO,
            listOf(SourceNodeConfiguration(red, 1.0, 10.0, car))
        )
        val target = TargetNode(Point(500, 100), 0, Angle.ZERO, red)
        val level = Level("Render Sample", 1, weatherCondition = WeatherCondition.DRY)
        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(Point(100, 100), Point(500, 100))))
        return level
    }

    private companion object {
        /** Maximum scheduler yields before a scene-render wait fails explicitly. */
        const val MAX_SCENE_WAIT_YIELDS = 500

        /** Maximum real-time polls before an asynchronous asset preload wait fails explicitly. */
        const val MAX_ASSET_WAIT_POLLS = 300

        /** Real milliseconds slept per asset poll iteration. */
        const val ASSET_POLL_SLEEP_MS = 5L
    }
}
