package scenes

import korlibs.image.color.Colors
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.Text
import korlibs.korge.view.graphics
import korlibs.korge.view.text
import korlibs.math.geom.vector.StrokeInfo
import ui.Assets

/** Draws static editor frames used by [WorldEditorScene]. */
internal object WorldEditorSceneFrames {
    /** Draws the framed editable field area. */
    fun drawFieldFrame(parent: SContainer) {
        parent.graphics {
            fill(Colors["#EEF3F8"]) {
                rect(WorldEditorLayout.FIELD_X, WorldEditorLayout.FIELD_Y, WorldEditorLayout.FIELD_W, WorldEditorLayout.FIELD_H)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) {
                rect(WorldEditorLayout.FIELD_X, WorldEditorLayout.FIELD_Y, WorldEditorLayout.FIELD_W, WorldEditorLayout.FIELD_H)
            }
        }
        parent.text("Spielfeld", textSize = 22.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
            x = WorldEditorLayout.FIELD_X + WorldEditorLayout.FIELD_W / 2.0 - width / 2.0
            y = WorldEditorLayout.FIELD_Y + 24.0
        }
    }

    /** Draws the framed node-properties area. */
    fun drawPropertiesFrame(parent: SContainer) {
        parent.graphics {
            fill(Colors["#F7F7F7"]) {
                rect(WorldEditorLayout.PROPS_X, WorldEditorLayout.PROPS_Y, WorldEditorLayout.PROPS_W, WorldEditorLayout.PROPS_H)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = 1.5)) {
                rect(WorldEditorLayout.PROPS_X, WorldEditorLayout.PROPS_Y, WorldEditorLayout.PROPS_W, WorldEditorLayout.PROPS_H)
            }
        }
        parent.text("Eigenschaften", textSize = 28.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
            x = WorldEditorLayout.PROPS_X + WorldEditorLayout.PROPS_W / 2.0 - width / 2.0
            y = WorldEditorLayout.PROPS_Y + 24.0
        }
    }

    /** Draws the editor error background and returns the text view inside it. */
    fun drawErrorText(parent: SContainer, errorBackground: Container): Text {
        errorBackground.graphics {
            fill(Colors["#FFF4F4"]) {
                rect(WorldEditorLayout.ERROR_X, WorldEditorLayout.ERROR_Y, WorldEditorLayout.ERROR_W, WorldEditorLayout.ERROR_H)
            }
            stroke(Colors["#D44"], StrokeInfo(thickness = 1.0)) {
                rect(WorldEditorLayout.ERROR_X, WorldEditorLayout.ERROR_Y, WorldEditorLayout.ERROR_W, WorldEditorLayout.ERROR_H)
            }
        }
        return parent.text("", textSize = 21.0, color = Colors.RED, font = Assets.bitcountSingle) {
            x = WorldEditorLayout.ERROR_X + 16.0
            y = WorldEditorLayout.ERROR_Y + 12.0
        }
    }
}
