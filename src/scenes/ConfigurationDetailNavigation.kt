package scenes

import domain.GameState.tutorialActivated
import korlibs.korge.scene.Scene
import kotlinx.coroutines.launch
import worldeditor.WorldEditorConfigurationContext

/** Gameplay scene targets used when leaving the configuration flow. */
internal enum class ConfigurationGameplayTarget {
    /** Return to the tutorial scene. */
    TUTORIAL_SCENE,

    /** Return to the active level scene. */
    LEVEL_SCENE
}

/**
 * Resolves the gameplay target for leaving configuration screens without consulting the scene
 * history stack, which may still contain older pause-menu scenes.
 */
internal fun configurationGameplayTarget(tutorialActive: Boolean): ConfigurationGameplayTarget =
    if (tutorialActive) ConfigurationGameplayTarget.TUTORIAL_SCENE else ConfigurationGameplayTarget.LEVEL_SCENE

/**
 * Shared leave and return navigation for the configuration detail scenes
 * ([SourceConfigDetailScene], [VehicleConfigurationScene]).
 *
 * While the world editor owns the configuration menu (see [WorldEditorConfigurationContext]),
 * both actions route back into the editor flow instead of the single-table pause-menu flow.
 */

/**
 * Leaves a configuration detail view completely: to the world editor when it opened the
 * configuration menu, otherwise to the tutorial or level scene.
 */
internal fun Scene.leaveConfigurationDetail() {
    launch {
        val editorSession = WorldEditorConfigurationContext.activeSession
        if (editorSession != null) {
            sceneContainer.changeTo { WorldEditorScene(editorSession) }
        } else {
            changeToConfigurationGameplayTarget(configurationGameplayTarget(tutorialActivated))
        }
    }
}

/**
 * Returns from a configuration detail view to the configuration overview.
 *
 * Uses an explicit scene change instead of the scene history so an older pause-menu scene cannot
 * reappear after users visited multi-table mode earlier in the session.
 */
internal fun Scene.returnToConfigurationOverview() {
    launch {
        sceneContainer.changeTo { ConfigurationScene() }
    }
}

/** Navigates to the resolved gameplay scene. */
internal suspend fun Scene.changeToConfigurationGameplayTarget(target: ConfigurationGameplayTarget) {
    when (target) {
        ConfigurationGameplayTarget.TUTORIAL_SCENE -> sceneContainer.changeTo<TutorialScene>()
        ConfigurationGameplayTarget.LEVEL_SCENE -> sceneContainer.changeTo<LevelScene>()
    }
}
