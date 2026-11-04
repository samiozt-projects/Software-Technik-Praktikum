package scenestests

import scenes.LevelScene
import scenes.ServerMenuScene
import ui.WorldPlaceholderSceneUI.WorldPlaceholderConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for scene layout configurations.
 * Verifies that UI elements are positioned and sized according to design specifications.
 */
class SceneLayoutConfigTest {
    /**
     * Tests that the connect button has the correct size and vertical position.
     * Verifies the width, height, and bottom offset of the connection button.
     */
    @Test
    fun `connect button size and vertical position are updated`() {
        assertEquals(320.0, ServerMenuScene.ServerMenuConfig.CONNECTION_BUTTON_W)
        assertEquals(86.0, ServerMenuScene.ServerMenuConfig.ACTION_BUTTON_H)
        assertEquals(150.0, ServerMenuScene.ServerMenuConfig.CONNECTION_BUTTON_BOTTOM_OFFSET)
    }

    /**
     * Tests that the tile grid is centered horizontally in the world placeholder scene.
     * Verifies the calculated X position matches the expected centered position.
     */
    @Test
    fun `tile grid is centered horizontally in world placeholder scene`() {
        val expectedGridWidth = WorldPlaceholderConfig.TILE_GRID_COLUMNS *
            WorldPlaceholderConfig.TILE_SIZE +
            (WorldPlaceholderConfig.TILE_GRID_COLUMNS - 1) *
            WorldPlaceholderConfig.TILE_GAP
        val expectedX = LevelScene.Companion.TABLE_PX_W / 2.0 - expectedGridWidth / 2.0
        assertEquals(expectedX, WorldPlaceholderConfig.TILE_GRID_X)
    }
}
