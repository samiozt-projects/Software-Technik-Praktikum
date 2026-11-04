package scenes

import korlibs.image.color.Colors
import audio.onClickWithSound
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.clipContainer
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.position
import korlibs.korge.view.text
import korlibs.math.geom.Size
import korlibs.math.geom.vector.StrokeInfo
import ui.Assets
import ui.SceneUIUtils
import ui.Scrollbar
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Group of views needed to keep the visible world list synchronized with scrollbar input.
 *
 * @property scrollbar shared vertical scrollbar that owns the current scroll offset.
 * @property container root view of the drawn list; callers remove this to rebuild the list.
 */
internal data class WorldSelectionListView(val scrollbar: Scrollbar, val container: Container)

/**
 * Renders the scrollable world list used by [WorldSelectionScene].
 *
 * Uses a fixed pool of pre-created row views. On each scroll event only visibility, position,
 * text content, and background color are updated — no views are created or destroyed during
 * scrolling, keeping frame time constant regardless of the total world count.
 */
internal object WorldSelectionListRenderer {

    /** Rows retained before and after the visible range to make scrolling seamless. */
    private const val ROW_POOL_BUFFER = 2

    /** Additional pooled rows reserved for viewport-boundary rounding. */
    private const val ROW_POOL_SPARE_COUNT = 2

    /** Horizontal distance between the scrollbar and the right edge of the list. */
    private const val SCROLLBAR_RIGHT_INSET = 14.0

    /** Vertical position of the scrollbar track within the list. */
    private const val SCROLLBAR_TRACK_Y = 62.0

    /** Number of row views retained for a virtualized world list, independent of world count. */
    internal fun pooledRowCount(): Int {
        val rowStep = WorldSelectionScene.ROW_H + WorldSelectionScene.ROW_GAP
        val pageRows = ceil(WorldSelectionScene.ROW_VIEWPORT_H / rowStep).toInt() + 1
        return pageRows + ROW_POOL_BUFFER * 2 + ROW_POOL_SPARE_COUNT
    }

    /**
     * Draws the list frame, rows, and scrollbar into [parent].
     *
     * @param parent container that receives the complete world list.
     * @param worlds available worlds to render.
     * @param selectedIndex callback returning the currently selected row index.
     * @param onWorldSelected callback invoked when a row is selected.
     * @return handles needed by [WorldSelectionScene] to wire scrolling.
     */
    fun draw(
        parent: SContainer,
        worlds: List<WorldSelectionScene.WorldEntry>,
        selectedIndex: () -> Int?,
        onWorldSelected: (Int) -> Unit
    ): WorldSelectionListView {
        lateinit var scrollbar: Scrollbar
        lateinit var controller: VirtualScrollController
        val listContainer = parent.container {
            position(WorldSelectionScene.LIST_X, WorldSelectionScene.LIST_Y)
            drawWorldListFrame()

            clipContainer(Size(WorldSelectionScene.ROW_W, WorldSelectionScene.ROW_VIEWPORT_H)) {
                position(WorldSelectionScene.ROW_X, WorldSelectionScene.ROW_START_Y)
                val rowsContainer = container {}
                controller = VirtualScrollController(rowsContainer, worlds, selectedIndex, onWorldSelected)
            }

            scrollbar = drawWorldScrollbar(WorldSelectionScene.worldListContentHeight(worlds.size)) { scrollY ->
                controller.updateScrollY(scrollY)
            }
        }
        return WorldSelectionListView(scrollbar, listContainer)
    }

    /**
     * Draws the bordered outer frame for the world list.
     */
    private fun Container.drawWorldListFrame() {
        graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, WorldSelectionScene.LIST_W, WorldSelectionScene.LIST_H)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = WorldSelectionScene.STROKE_THICKNESS)) {
                rect(0.0, 0.0, WorldSelectionScene.LIST_W, WorldSelectionScene.LIST_H)
            }
        }
    }

    /**
     * Draws the shared scrollbar for the world list.
     *
     * @param contentHeight total scrollable height for the full world list.
     * @param onScroll called with the new scroll offset whenever it changes.
     * @return the created scrollbar.
     */
    private fun Container.drawWorldScrollbar(contentHeight: Double, onScroll: (Double) -> Unit): Scrollbar =
        Scrollbar(
            parent = this,
            x = WorldSelectionScene.LIST_W - Scrollbar.WIDTH - SCROLLBAR_RIGHT_INSET,
            y = SCROLLBAR_TRACK_Y,
            barHeight = WorldSelectionScene.ROW_VIEWPORT_H -
                2.0 * (Scrollbar.PAGE_BUTTON_HEIGHT + Scrollbar.PAGE_BUTTON_GAP),
            viewportHeight = WorldSelectionScene.ROW_VIEWPORT_H,
            contentHeight = contentHeight,
            pageScrollDistance = WorldSelectionScene.WORLD_PAGE_SCROLL_DISTANCE,
            onScroll = onScroll
        )

    /**
     * Manages a fixed pool of row views, recycling them as the viewport scrolls instead of
     * creating or destroying views at runtime.
     *
     * @param rowsContainer container that holds all pooled row views; its [y] position is
     *   shifted to implement scrolling.
     * @param worlds full list of worlds to display.
     * @param selectedIndex callback returning the currently selected row index.
     * @param onWorldSelected invoked when the user clicks a row.
     */
    private class VirtualScrollController(
        private val rowsContainer: Container,
        private val worlds: List<WorldSelectionScene.WorldEntry>,
        private val selectedIndex: () -> Int?,
        private val onWorldSelected: (Int) -> Unit
    ) {
        private val rowStep = WorldSelectionScene.ROW_H + WorldSelectionScene.ROW_GAP
        private val pageRows = ceil(WorldSelectionScene.ROW_VIEWPORT_H / rowStep).toInt() + 1
        private val poolSize = WorldSelectionListRenderer.pooledRowCount()

        private val pool: List<PoolRow> = List(poolSize) { PoolRow() }
        private val indexToSlot = mutableMapOf<Int, PoolRow>()
        private var scrollY = 0.0
        private var boundFirstIndex = -1
        private var boundLastIndex = -1

        /**
         * Performs the initial row population at scroll offset 0.
         */
        init {
            syncRows()
        }

        /**
         * Moves the content to [newScrollY] and updates the visible row set.
         *
         * @param newScrollY new scroll offset; 0 is the top, negative values scroll down.
         */
        fun updateScrollY(newScrollY: Double) {
            if (scrollY == newScrollY) return
            scrollY = newScrollY
            rowsContainer.y = newScrollY
            syncRows()
        }

        /**
         * Updates the selection highlight on all currently visible rows without re-creating views.
         */
        fun refreshSelection() {
            indexToSlot.forEach { (index, slot) ->
                slot.setSelected(index == selectedIndex())
            }
        }

        /**
         * Releases slots for rows that left the visible range and binds slots for rows that
         * entered it. Pool slots are recycled between the two steps.
         */
        private fun syncRows() {
            val firstVisible = max(0, floor(-scrollY / rowStep).toInt() - ROW_POOL_BUFFER)
            val lastVisible = min(worlds.size - 1, firstVisible + pageRows + ROW_POOL_BUFFER * 2)
            if (firstVisible == boundFirstIndex && lastVisible == boundLastIndex) return

            val boundRows = indexToSlot.entries.iterator()
            while (boundRows.hasNext()) {
                val (index, slot) = boundRows.next()
                if (index < firstVisible || index > lastVisible) {
                    boundRows.remove()
                    slot.release()
                }
            }

            for (i in firstVisible..lastVisible) {
                if (i !in indexToSlot) {
                    val slot = pool.firstOrNull { it.isFree } ?: return
                    indexToSlot[i] = slot
                    slot.bind(i, selected = i == selectedIndex())
                }
            }
            boundFirstIndex = firstVisible
            boundLastIndex = lastVisible
        }

        /**
         * A single pre-created row view that can be re-bound to any world in the list.
         *
         * The two background graphics ([unselectedBg], [selectedBg]) are drawn once at
         * construction time; selection changes toggle their [visible] flag rather than
         * repainting. The text label is updated via its [text][korlibs.korge.view.Text.text]
         * property on each [bind] call.
         */
        private inner class PoolRow {
            /** Root container for all visual elements of this row. */
            val view = rowsContainer.container {}
            private val unselectedBg = view.graphics {
                fill(Colors.WHITE) {
                    rect(0.0, 0.0, WorldSelectionScene.ROW_W, WorldSelectionScene.ROW_H)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = WorldSelectionScene.STROKE_THICKNESS)) {
                    rect(0.0, 0.0, WorldSelectionScene.ROW_W, WorldSelectionScene.ROW_H)
                }
            }
            private val selectedBg = view.graphics {
                fill(WorldSelectionScene.SELECTED_FILL) {
                    rect(0.0, 0.0, WorldSelectionScene.ROW_W, WorldSelectionScene.ROW_H)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = WorldSelectionScene.STROKE_THICKNESS)) {
                    rect(0.0, 0.0, WorldSelectionScene.ROW_W, WorldSelectionScene.ROW_H)
                }
            }
            private val label = view.text("", textSize = 25.0, color = Colors.BLACK, font = Assets.bitcountSingle)

            private var currentIndex = -1

            /** True when this slot is not bound to any row index. */
            var isFree = true
                private set

            /**
             * Hides the view, sets the initial background state, and registers the click handler.
             */
            init {
                selectedBg.visible = false
                view.visible = false
                view.onClickWithSound {
                    if (currentIndex >= 0) {
                        onWorldSelected(currentIndex)
                        refreshSelection()
                    }
                }
            }

            /**
             * Assigns this slot to [index], updates its position, background, and label.
             *
             * @param index row index into [worlds].
             * @param selected whether this row is currently selected.
             */
            fun bind(index: Int, selected: Boolean) {
                currentIndex = index
                isFree = false
                view.position(0.0, index * rowStep)
                setSelected(selected)
                label.text = WorldSelectionScene.worldLabel(worlds[index])
                SceneUIUtils.truncateWithEllipsis(
                    label,
                    WorldSelectionScene.ROW_W - WorldSelectionScene.ROW_LABEL_PADDING_X * 2.0
                )
                label.x = WorldSelectionScene.ROW_LABEL_PADDING_X
                label.y = (WorldSelectionScene.ROW_H - label.height) / 2.0
                view.visible = true
            }

            /**
             * Returns this slot to the pool and hides its view.
             */
            fun release() {
                currentIndex = -1
                isFree = true
                view.visible = false
            }

            /**
             * Toggles the selection highlight without recreating any views.
             *
             * @param selected whether to show the selection fill.
             */
            fun setSelected(selected: Boolean) {
                unselectedBg.visible = !selected
                selectedBg.visible = selected
            }
        }
    }
}
