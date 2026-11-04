package scenes

import domain.GameState
import korlibs.korge.scene.Scene
import korlibs.korge.scene.SceneContainer

/**
 * Shared fallback used by level-dependent scenes when they cannot continue without a loaded level.
 *
 * Switching to [LevelMenuScene] keeps the game stable because that scene does not need a level, and
 * the player can simply pick again. The navigation is guarded through
 * [GameState.tryBeginSceneTransition] so a recovery cannot overlap an already running scene change
 * (issue #184).
 */
internal suspend fun SceneContainer.recoverToLevelSelection() {
    if (!GameState.tryBeginSceneTransition()) return
    try {
        changeTo<LevelMenuScene>()
    } finally {
        GameState.endSceneTransition()
    }
}

/**
 * Recovers a scene by returning to the level selection through its own [Scene.sceneContainer].
 *
 * @see recoverToLevelSelection
 */
internal suspend fun Scene.recoverToLevelSelection() {
    sceneContainer.recoverToLevelSelection()
}
