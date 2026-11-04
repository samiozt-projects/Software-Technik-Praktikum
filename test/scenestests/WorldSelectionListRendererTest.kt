package scenestests

import scenes.WorldSelectionListRenderer
import scenes.WorldSelectionScene
import ui.Scrollbar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests list rendering behavior for large world-selection data sets. */
class WorldSelectionListRendererTest {
    /**
     * Covers a realistic high server-world count: the row-view pool remains small and the
     * scrollbar still reaches the precise position at which the final row is fully visible.
     */
    @Test
    fun largeWorldList_usesFixedPoolAndKeepsScrollbarPositionAccurate() {
        val worldCount = 10_000
        val contentHeight = WorldSelectionScene.worldListContentHeight(worldCount)
        val minimumScrollY = Scrollbar.minScrollY(WorldSelectionScene.ROW_VIEWPORT_H, contentHeight)
        val scrollbarHeight = WorldSelectionScene.LIST_H - 124.0

        assertTrue(WorldSelectionListRenderer.pooledRowCount() < 20)
        assertTrue(WorldSelectionListRenderer.pooledRowCount() < worldCount)
        assertEquals(
            minimumScrollY,
            Scrollbar.clampScrollY(minimumScrollY, WorldSelectionScene.ROW_VIEWPORT_H, contentHeight)
        )
        assertEquals(
            Scrollbar.THUMB_INSET + Scrollbar.trackHeight(scrollbarHeight) -
                Scrollbar.thumbHeight(scrollbarHeight, WorldSelectionScene.ROW_VIEWPORT_H, contentHeight),
            Scrollbar.thumbOffsetY(
                minimumScrollY,
                scrollbarHeight,
                WorldSelectionScene.ROW_VIEWPORT_H,
                contentHeight
            )
        )
    }
}
