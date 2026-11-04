package scenes

import korlibs.image.color.Colors
import korlibs.korge.view.SContainer
import korlibs.korge.view.solidRect
import scenes.LevelScene.Companion.TABLE_PX_H
import scenes.LevelScene.Companion.TABLE_PX_W

/**
 * Paints the scene interior white.
 *
 * The whole application runs on a black window background so [LevelScene] keeps black borders around
 * the clipped playfield (the area outside the projected table is letterboxed in the window
 * background color). Menu-style scenes, however, draw their content directly on top of that black
 * background and were designed for a white one, so they need their own opaque white fill.
 *
 * Calling this as the first child of such a scene fills the projected table area (the visible scene
 * interior) with white while leaving the black letterbox borders untouched. Scenes that render their
 * own full background — the level view or an explicit frame — do not need this helper.
 */
fun SContainer.drawWhiteSceneBackground() {
    solidRect(TABLE_PX_W, TABLE_PX_H, Colors.WHITE)
}
