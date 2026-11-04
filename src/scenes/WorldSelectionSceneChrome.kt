package scenes

import domain.GameState.tutorialActivated
import korlibs.image.color.Colors
import audio.onClickWithSound
import korlibs.korge.view.SContainer
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.graphics
import korlibs.korge.view.text
import kotlinx.coroutines.launch
import ui.Assets
import ui.ControlButtons

/** Draws the static frame and navigation controls for [WorldSelectionScene]. */
internal object WorldSelectionSceneChrome {
    /** Draws a plain white background that fills the entire projected viewport. */
    fun drawBackgroundFrame(parent: SContainer) {
        parent.graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, LevelScene.TABLE_PX_W, LevelScene.TABLE_PX_H)
            }
        }
    }

    /** Draws the scene title and the world-list column caption. */
    fun drawTitle(parent: SContainer) {
        parent.text(
            WorldSelectionScene.TITLE_TEXT,
            textSize = 78.0,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ) {
            centerXOn(parent)
            y = 62.0
        }
        parent.text(
            WorldSelectionScene.LIST_CAPTION,
            textSize = 30.0,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ) {
            x = WorldSelectionScene.LIST_X
            y = 202.0
        }
    }

    /** Draws the back and close buttons and wires them to the correct target scenes. */
    fun drawNavigation(scene: WorldSelectionScene, parent: SContainer) {
        ControlButtons.drawBackButton(parent).onClickWithSound {
            scene.launch {
                when (WorldSelectionScene.backDestination) {
                    WorldSelectionBackDestination.LEVEL_SCENE ->
                        scene.sceneContainer.changeTo<LevelScene>()
                    WorldSelectionBackDestination.MULTI_TABLE_MENU_SCENE ->
                        scene.sceneContainer.changeTo<MultiTableMenuScene>()
                    WorldSelectionBackDestination.SERVER_MENU ->
                        scene.sceneContainer.changeTo<ServerMenuScene>()
                }
            }
        }
        ControlButtons.drawCloseButton(parent).onClickWithSound {
            scene.launch {
                if (WorldSelectionScene.backDestination == WorldSelectionBackDestination.MULTI_TABLE_MENU_SCENE) {
                    scene.sceneContainer.changeTo<WorldScene>()
                } else if (tutorialActivated) {
                    scene.sceneContainer.changeTo<TutorialScene>()
                } else {
                    scene.sceneContainer.changeTo<LevelScene>()
                }
            }
        }
    }
}
