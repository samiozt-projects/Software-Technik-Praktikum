import diagnostics.installJfrFrameLogging
import audio.InteractionSoundController
import domain.GameState
import domain.SimulationState
import korlibs.event.Key
import korlibs.image.color.*
import korlibs.inject.Injector
import korlibs.korge.*
import korlibs.korge.input.keys
import korlibs.korge.scene.*
import korlibs.math.geom.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import persistence.LevelSerializer
import scenes.ArucoDetectionScene
import scenes.CameraCalibrationScene
import scenes.ConfigurationScene
import scenes.LevelMenuScene
import scenes.LevelScene
import scenes.MenuScene
import scenes.MultiTableMenuScene
import scenes.NewWorldCreationScene
import scenes.SavedNetworksScene
import scenes.ServerMenuScene
import scenes.SourceConfigDetailScene
import scenes.TutorialScene
import scenes.VehicleConfigurationScene
import scenes.WeatherConfigurationScene
import scenes.WorldPlaceholderScene
import scenes.WorldScene
import scenes.WorldSelectionScene
import scenes.installConnectionLossNavigation

/**
 * Main entry point for the application.
 */
suspend fun main() = Korge(windowSize = Size(1800, 1000), backgroundColor = Colors.BLACK) {
    val sceneContainer = sceneContainer()

    // Toggle borderless fullscreen with F11; pressing it again leaves fullscreen. The handler lives
    // on the persistent stage so it stays active across every scene change. A plain
    // gameWindow.fullscreen toggle would keep the window's title bar on Windows, so the real
    // decoration-dropping toggle is implemented in toggleBorderlessFullscreen().
    keys {
        down(Key.F11) { views.gameWindow.toggleBorderlessFullscreen() }
    }

    registerScenes(views.injector)
    // Bounce to the multi-table mode scene (with the reconnect overlay) whenever an established
    // connection drops, from whatever scene is active at the time (issue #210).
    installConnectionLossNavigation(sceneContainer, CoroutineScope(coroutineContext))
    startTutorial(sceneContainer)
}

/**
 * Registers all scene prototypes with the Korge dependency injector.
 *
 * Every scene that can be navigated to via [korlibs.korge.scene.SceneContainer.changeTo] or
 * [korlibs.korge.scene.SceneContainer.pushTo] must appear here. Missing registrations cause a
 * runtime [korlibs.inject.Injector.NotMappedException] when the scene is first requested.
 *
 * @param injector the application-wide injector provided by the Korge runtime.
 */
private fun registerScenes(injector: Injector) {
    injector.mapPrototype { CameraCalibrationScene() }
    injector.mapPrototype { ArucoDetectionScene() }
    injector.mapPrototype { LevelScene() }
    injector.mapPrototype { LevelMenuScene() }
    injector.mapPrototype { SavedNetworksScene() }
    injector.mapPrototype { MenuScene() }
    injector.mapPrototype { MultiTableMenuScene() }
    injector.mapPrototype { TutorialScene() }
    injector.mapPrototype { ConfigurationScene() }
    injector.mapPrototype { ServerMenuScene() }
    injector.mapPrototype { VehicleConfigurationScene(get()) }
    injector.mapPrototype { SourceConfigDetailScene(get(), get(), get()) }
    injector.mapPrototype { WeatherConfigurationScene() }
    injector.mapPrototype { WorldSelectionScene() }
    injector.mapPrototype { NewWorldCreationScene() }
    injector.mapPrototype { WorldPlaceholderScene() }
    injector.mapPrototype { WorldScene() }
}

/**
 * Loads the tutorial level, initializes global state, preloads assets, and navigates to
 * [CameraCalibrationScene], which waits for the Start button, runs the one-time camera
 * auto-calibration sweep, and continues via [ArucoDetectionScene] (projected inverted corner
 * markers) on to [TutorialScene] itself.
 *
 * @param sceneContainer the root scene container used for the initial scene transition.
 */
private suspend fun startTutorial(sceneContainer: SceneContainer) {
    val level = LevelSerializer.loadFromResources("level/tutorial.json")
    GameState.currentLevel = level
    GameState.automaticStreetDetectionEnabled = level.streets.isEmpty()
    GameState.simulationState = SimulationState.CONSTRUCTION
    ui.Assets.loadAll()
    InteractionSoundController.loadSounds(currentCoroutineContext())
    ui.Assets.preloadVehicleGraphics(level.vehicleTypes)
    GameState.tutorialActivated = true
    sceneContainer.changeTo<CameraCalibrationScene>()
}
