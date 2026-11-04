package ui

import korlibs.image.color.Colors
import audio.onClickWithSound
import korlibs.korge.input.onMouseDrag
import korlibs.korge.view.Container
import korlibs.korge.view.Graphics
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.position
import korlibs.math.geom.Point
import korlibs.math.geom.vector.StrokeInfo
import kotlin.math.max
import kotlin.math.min

/**
 * A reusable vertical scrollbar with an optional pair of page-scroll arrows and a draggable thumb.
 *
 * The scrollbar owns the scroll offset of a single vertically scrolling list. On construction it
 * draws the track, arrows, and a draggable thumb into [parent] at ([x], [y]) and reports the
 * current scroll offset through [onScroll] so the caller can move its content view. The offset is
 * `0.0` at the top and grows negative while scrolling down, matching a content view whose `y` is
 * shifted upward. All sizes are in scene pixels.
 *
 * Geometry is fixed for the scrollbar's lifetime: create a new instance when [contentHeight] or
 * [viewportHeight] changes (for example after the list gains or loses rows).
 *
 * @param parent container that receives the scrollbar views.
 * @param x scene x-position of the scrollbar's left edge.
 * @param y scene y-position of the scrollbar's top edge.
 * @param barHeight visual height of the scrollbar track, excluding optional page arrows.
 * @param viewportHeight height of the visible content area the list scrolls within.
 * @param contentHeight total height of the scrollable content.
 * @param initialScrollY scroll offset to start from; clamped into range.
 * @param pageScrollDistance positive distance moved by the optional arrow buttons; null hides them.
 * @param onScroll invoked with the clamped scroll offset on creation and whenever it changes.
 */
class Scrollbar(
    /** container that receives the scrollbar views. */
    parent: Container,
    private val x: Double,
    private val y: Double,
    private val barHeight: Double,
    private val viewportHeight: Double,
    private val contentHeight: Double,
    /** scroll offset to start from; clamped into range. */
    initialScrollY: Double = 0.0,
    private val pageScrollDistance: Double? = null,
    private val onScroll: (scrollY: Double) -> Unit,
) {
    companion object {
        /** Width of the scrollbar track. */
        const val WIDTH = 58.0

        /** Top and bottom inset that keeps the thumb clear of the track ends. */
        const val THUMB_INSET = 12.0

        /** Width of the dark draggable thumb. */
        const val THUMB_WIDTH = 32.0

        /** Minimum height of the dark draggable thumb. */
        const val MIN_THUMB_HEIGHT = 92.0

        /** Border thickness of the track, matching the menu's card rows. */
        const val STROKE_THICKNESS = 1.5

        /** Height of an optional page-scroll arrow button. */
        const val PAGE_BUTTON_HEIGHT = 42.0

        /** Vertical gap separating an optional arrow button from the track. */
        const val PAGE_BUTTON_GAP = 8.0

        /** Vertical distance from an arrow button's center to its triangle tip. */
        private const val PAGE_ARROW_TIP_OFFSET = 11.0

        /** Vertical distance from an arrow button's center to its triangle base. */
        private const val PAGE_ARROW_BASE_OFFSET = 8.0

        /** Horizontal distance from an arrow's center to either base corner. */
        private const val PAGE_ARROW_HALF_WIDTH = 12.0

        /** Scrollbar colors, matching the menu's black-on-white card design. */
        /** Color of the scrollbar border. */
        val BORDER_COLOR = Colors.BLACK
        /** Color of the scrollbar thumb. */
        val THUMB_COLOR = Colors["#6B6B6B"]

        /**
         * Returns the lowest scroll offset that still keeps content in range.
         *
         * @param viewportHeight the height of the viewport.
         * @param contentHeight the height of the content.
         * @return The minimum scroll offset.
         */
        fun minScrollY(viewportHeight: Double, contentHeight: Double): Double {
            val overflow = contentHeight - viewportHeight
            return if (overflow > 0.0) -overflow else 0.0
        }

        /**
         * Returns whether the content overflows the viewport and a scrollbar is needed.
         *
         * @param viewportHeight the height of the viewport.
         * @param contentHeight the height of the content.
         * @return Whether the scrollbar is visible.
         */
        fun isVisible(viewportHeight: Double, contentHeight: Double): Boolean =
            minScrollY(viewportHeight, contentHeight) < 0.0

        /**
         * Clamps a requested offset so the list stays in range and short lists do not move.
         *
         * @param requestedScrollY the requested scroll offset.
         * @param viewportHeight the height of the viewport.
         * @param contentHeight the height of the content.
         * @return The clamped scroll offset.
         */
        fun clampScrollY(requestedScrollY: Double, viewportHeight: Double, contentHeight: Double): Double =
            min(0.0, max(minScrollY(viewportHeight, contentHeight), requestedScrollY))

        /**
         * Returns the vertical travel range available for the thumb between the insets.
         *
         * @param barHeight the height of the scrollbar.
         * @return The track height.
         */
        fun trackHeight(barHeight: Double): Double =
            barHeight - THUMB_INSET * 2.0

        /**
         * Calculates the thumb height, proportional to how much of the content is visible.
         *
         * @param barHeight the height of the scrollbar.
         * @param viewportHeight the height of the viewport.
         * @param contentHeight the height of the content.
         * @return The thumb height.
         */
        fun thumbHeight(barHeight: Double, viewportHeight: Double, contentHeight: Double): Double {
            val track = trackHeight(barHeight)
            if (!isVisible(viewportHeight, contentHeight)) return track
            return max(MIN_THUMB_HEIGHT, track * viewportHeight / contentHeight).coerceAtMost(track)
        }

        /**
         * Calculates the thumb top relative to the scrollbar's own top edge for a scroll offset. 
         *
         * @param scrollY the scroll offset.
         * @param barHeight the height of the scrollbar.
         * @param viewportHeight the height of the viewport.
         * @param contentHeight the height of the content.
         * @return The thumb offset.
        */
        fun thumbOffsetY(scrollY: Double, barHeight: Double, viewportHeight: Double, contentHeight: Double): Double {
            val minScroll = minScrollY(viewportHeight, contentHeight)
            val travel = trackHeight(barHeight) - thumbHeight(barHeight, viewportHeight, contentHeight)
            val progress = if (minScroll == 0.0) {
                0.0
            } else {
                (clampScrollY(scrollY, viewportHeight, contentHeight) / minScroll).coerceIn(0.0, 1.0)
            }
            return THUMB_INSET + travel * progress
        }

        /**
         * Maps a thumb drag into a scroll offset.
         *
         * @param dragStartScrollY scroll offset captured when the thumb drag started.
         * @param thumbDeltaY pixels the thumb has moved since the thumb drag started.
         * @param barHeight the height of the scrollbar.
         * @param viewportHeight the height of the viewport.
         * @param contentHeight the height of the content.
         * @return The scroll offset.
         */
        fun scrollYFromThumbDrag(
            dragStartScrollY: Double,
            thumbDeltaY: Double,
            barHeight: Double,
            viewportHeight: Double,
            contentHeight: Double
        ): Double {
            val travel = trackHeight(barHeight) - thumbHeight(barHeight, viewportHeight, contentHeight)
            if (travel <= 0.0) return clampScrollY(dragStartScrollY, viewportHeight, contentHeight)

            val minScroll = minScrollY(viewportHeight, contentHeight)
            return clampScrollY(dragStartScrollY + thumbDeltaY / travel * minScroll, viewportHeight, contentHeight)
        }
    }

    /** 
     * The current clamped scroll offset (`0.0` at the top, negative while scrolled down). 
     */
    var scrollY: Double = clampScrollY(initialScrollY, viewportHeight, contentHeight)
        private set

    /** Whether the scrollbar is visible. */
    private val visible = isVisible(viewportHeight, contentHeight)

    /** The scrollbar's container. */
    private val container = parent.container().position(x, y)

    /** The scrollbar's thumb. */
    private var thumb: Graphics? = null

    /**
     * Initializes the scrollbar.
     */
    init {
        require(pageScrollDistance == null || pageScrollDistance > 0.0) {
            "pageScrollDistance must be positive when page arrows are enabled."
        }
        if (visible) {
            drawTrack()
            thumb = drawThumb()
            attachThumbDrag()
            drawPageButtons()
            updateThumb()
        }
        onScroll(scrollY)
    }

    /**
     * Returns whether a stage-space point lies inside the visible scrollbar.
     *
     * @param sceneX the stage x-position to check.
     * @param sceneY the stage y-position to check.
     */
    fun contains(sceneX: Double, sceneY: Double): Boolean {
        if (!visible) return false
        val localPoint = container.globalToLocal(Point(sceneX, sceneY))
        val buttonExtension = if (pageScrollDistance == null) 0.0 else PAGE_BUTTON_HEIGHT + PAGE_BUTTON_GAP
        return localPoint.x in 0.0..WIDTH && localPoint.y in -buttonExtension..(barHeight + buttonExtension)
    }

    /** 
     * Scrolls to an absolute offset, clamped into range.
     *
     * @param value the absolute offset to scroll to.
     */
    fun scrollTo(value: Double) {
        val clampedScrollY = clampScrollY(value, viewportHeight, contentHeight)
        if (clampedScrollY == scrollY) return
        scrollY = clampedScrollY
        updateThumb()
        onScroll(scrollY)
    }

    /**
     * Scrolls by a relative delta, clamped into range.
     *
     * @param delta the amount to scroll by.
     */
    fun scrollBy(delta: Double) = scrollTo(scrollY + delta)

    /**
     * Scrolls by the configured page distance.
     *
     * @param direction positive to move down the content, negative to move up; zero has no effect.
     */
    fun scrollByPage(direction: Int) {
        val distance = pageScrollDistance ?: return
        scrollByStep(direction, distance)
    }

    /**
     * Scrolls by a caller-provided fixed distance.
     *
     * @param direction positive to move down the content, negative to move up; zero has no effect.
     * @param distance positive distance moved in scene pixels.
     */
    fun scrollByStep(direction: Int, distance: Double) {
        require(distance > 0.0) { "Scroll step distance must be positive." }
        if (direction == 0) return
        scrollBy(if (direction > 0) -distance else distance)
    }

    /**
     * Updates the thumb position based on the scroll offset.
     */
    private fun updateThumb() {
        thumb?.y = thumbOffsetY(scrollY, barHeight, viewportHeight, contentHeight)
    }

    /**
     * Attaches a mouse drag listener to the thumb.
     */
    private fun attachThumbDrag() {
        val thumbView = thumb ?: return
        var dragStartScrollY = scrollY
        thumbView.onMouseDrag {
            if (it.start) dragStartScrollY = scrollY
            scrollTo(scrollYFromThumbDrag(dragStartScrollY, it.dy, barHeight, viewportHeight, contentHeight))
        }
    }

    /** Draws optional buttons that move the content upward or downward by one configured page. */
    private fun drawPageButtons() {
        if (pageScrollDistance == null) return
        drawPageButton(
            buttonY = -PAGE_BUTTON_HEIGHT - PAGE_BUTTON_GAP,
            pointsUp = true,
            onClick = { scrollByPage(direction = -1) }
        )
        drawPageButton(
            buttonY = barHeight + PAGE_BUTTON_GAP,
            pointsUp = false,
            onClick = { scrollByPage(direction = 1) }
        )
    }

    /** Draws a bordered page-scroll button with an upward or downward triangle. */
    private fun drawPageButton(buttonY: Double, pointsUp: Boolean, onClick: () -> Unit) {
        val centerX = WIDTH / 2.0
        val centerY = PAGE_BUTTON_HEIGHT / 2.0
        val arrowTipY = if (pointsUp) centerY - PAGE_ARROW_TIP_OFFSET else centerY + PAGE_ARROW_TIP_OFFSET
        val arrowBaseY = if (pointsUp) centerY + PAGE_ARROW_BASE_OFFSET else centerY - PAGE_ARROW_BASE_OFFSET
        container.container {
            position(0.0, buttonY)
            graphics {
                fill(Colors.WHITE) { rect(0.0, 0.0, WIDTH, PAGE_BUTTON_HEIGHT) }
                stroke(BORDER_COLOR, StrokeInfo(thickness = STROKE_THICKNESS)) {
                    rect(0.0, 0.0, WIDTH, PAGE_BUTTON_HEIGHT)
                }
                fill(THUMB_COLOR) {
                    moveTo(centerX, arrowTipY)
                    lineTo(centerX - PAGE_ARROW_HALF_WIDTH, arrowBaseY)
                    lineTo(centerX + PAGE_ARROW_HALF_WIDTH, arrowBaseY)
                    close()
                }
            }
            onClickWithSound { onClick() }
        }
    }

    /**
     * Draws the track of the scrollbar.
     */
    private fun drawTrack() {
        container.graphics {
            fill(Colors.WHITE) { rect(0.0, 0.0, WIDTH, barHeight) }
            stroke(BORDER_COLOR, StrokeInfo(thickness = STROKE_THICKNESS)) { rect(0.0, 0.0, WIDTH, barHeight) }
        }
    }

    /**
     * Draws the thumb of the scrollbar.
     *
     * @return The thumb of the scrollbar.
     */
    private fun drawThumb(): Graphics =
        container.graphics {
            fill(THUMB_COLOR) {
                rect(
                    (WIDTH - THUMB_WIDTH) / 2.0,
                    0.0,
                    THUMB_WIDTH,
                    thumbHeight(barHeight, viewportHeight, contentHeight)
                )
            }
        }
}
