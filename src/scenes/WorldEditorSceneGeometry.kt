package scenes

import rendering.GameAreaScale

/** Converts between editor field screen coordinates and level coordinates. */
internal object WorldEditorSceneGeometry {
    /** Horizontal level coordinate in the center of the editable field. */
    val levelCenterX: Double
        get() = GameAreaScale.GAME_AREA_W_METER / 2.0

    /** Vertical level coordinate in the center of the editable field. */
    val levelCenterY: Double
        get() = GameAreaScale.GAME_AREA_H_METER / 2.0

    /** Converts a level x-coordinate to a screen x-coordinate inside the editor field. */
    fun levelToScreenX(x: Double): Double =
        WorldEditorLayout.FIELD_X + x / GameAreaScale.GAME_AREA_W_METER * WorldEditorLayout.FIELD_W

    /** Converts a level y-coordinate to a screen y-coordinate inside the editor field. */
    fun levelToScreenY(y: Double): Double =
        WorldEditorLayout.FIELD_Y + y / GameAreaScale.GAME_AREA_H_METER * WorldEditorLayout.FIELD_H

    /** Returns the left screen coordinate for a node marker centered at level x-coordinate [x]. */
    fun nodeViewX(x: Double): Double =
        levelToScreenX(x) - WorldEditorLayout.NODE_SIZE / 2.0

    /** Returns the top screen coordinate for a node marker centered at level y-coordinate [y]. */
    fun nodeViewY(y: Double): Double =
        levelToScreenY(y) - WorldEditorLayout.NODE_SIZE / 2.0

    /** Clamps a screen x-coordinate to the visible editor field. */
    fun clampScreenXToField(x: Double): Double =
        x.coerceIn(WorldEditorLayout.FIELD_X, WorldEditorLayout.FIELD_X + WorldEditorLayout.FIELD_W)

    /** Clamps a screen y-coordinate to the visible editor field. */
    fun clampScreenYToField(y: Double): Double =
        y.coerceIn(WorldEditorLayout.FIELD_Y, WorldEditorLayout.FIELD_Y + WorldEditorLayout.FIELD_H)

    /** Converts a screen x-coordinate to a level x-coordinate clamped to the editable field. */
    fun screenToLevelX(x: Double): Double =
        ((clampScreenXToField(x) - WorldEditorLayout.FIELD_X) /
            WorldEditorLayout.FIELD_W * GameAreaScale.GAME_AREA_W_METER)

    /** Converts a screen y-coordinate to a level y-coordinate clamped to the editable field. */
    fun screenToLevelY(y: Double): Double =
        ((clampScreenYToField(y) - WorldEditorLayout.FIELD_Y) /
            WorldEditorLayout.FIELD_H * GameAreaScale.GAME_AREA_H_METER)

    /** Returns whether the screen point lies inside the visible editor field. */
    fun screenPointInField(x: Double, y: Double): Boolean =
        x in WorldEditorLayout.FIELD_X..(WorldEditorLayout.FIELD_X + WorldEditorLayout.FIELD_W) &&
            y in WorldEditorLayout.FIELD_Y..(WorldEditorLayout.FIELD_Y + WorldEditorLayout.FIELD_H)
}
