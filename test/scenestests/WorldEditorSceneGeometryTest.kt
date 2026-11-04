package scenestests

import rendering.GameAreaScale
import scenes.WorldEditorLayout
import scenes.WorldEditorSceneGeometry
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests editor-field coordinate conversion used by drag-and-drop placement. */
class WorldEditorSceneGeometryTest {
    /** Verifies that screen drag coordinates are clamped to the visible editor field. */
    @Test
    fun clampScreenCoordinates_keepsDragPositionInsideField() {
        assertEquals(WorldEditorLayout.FIELD_X, WorldEditorSceneGeometry.clampScreenXToField(-25.0))
        assertEquals(
            WorldEditorLayout.FIELD_X + WorldEditorLayout.FIELD_W,
            WorldEditorSceneGeometry.clampScreenXToField(WorldEditorLayout.FIELD_X + WorldEditorLayout.FIELD_W + 25.0)
        )
        assertEquals(WorldEditorLayout.FIELD_Y, WorldEditorSceneGeometry.clampScreenYToField(-25.0))
        assertEquals(
            WorldEditorLayout.FIELD_Y + WorldEditorLayout.FIELD_H,
            WorldEditorSceneGeometry.clampScreenYToField(WorldEditorLayout.FIELD_Y + WorldEditorLayout.FIELD_H + 25.0)
        )
    }

    /** Verifies that out-of-field drag coordinates become bounded level coordinates. */
    @Test
    fun screenToLevelCoordinates_convertOutOfFieldDragToLevelBounds() {
        assertEquals(0.0, WorldEditorSceneGeometry.screenToLevelX(WorldEditorLayout.FIELD_X - 25.0))
        assertEquals(
            GameAreaScale.GAME_AREA_W_METER,
            WorldEditorSceneGeometry.screenToLevelX(WorldEditorLayout.FIELD_X + WorldEditorLayout.FIELD_W + 25.0)
        )
        assertEquals(0.0, WorldEditorSceneGeometry.screenToLevelY(WorldEditorLayout.FIELD_Y - 25.0))
        assertEquals(
            GameAreaScale.GAME_AREA_H_METER,
            WorldEditorSceneGeometry.screenToLevelY(WorldEditorLayout.FIELD_Y + WorldEditorLayout.FIELD_H + 25.0)
        )
    }
}
