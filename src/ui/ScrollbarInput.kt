package ui

import korlibs.korge.input.mouse
import korlibs.korge.input.onMouseDrag
import korlibs.korge.view.Container

/**
 * Attaches list-style drag and wheel input to a vertical [Scrollbar].
 *
 * The scrollbar still owns the actual scroll offset; this helper only wires common user input
 * patterns used by scenes that let players drag a list surface or use the mouse wheel.
 *
 * @param scrollbar scrollbar that controls the content view.
 * @param scrollSpeed multiplier applied to mouse-wheel deltas.
 * @param useLineWheelDelta whether to use line-based wheel deltas instead of pixel deltas.
 */
fun Container.attachScrollbarInput(
    scrollbar: Scrollbar,
    scrollSpeed: Double,
    useLineWheelDelta: Boolean = false
) {
    var dragStartScrollY = 0.0
    var listDragActive = false

    onMouseDrag {
        if (it.start) {
            listDragActive = !scrollbar.contains(it.sx, it.sy)
            dragStartScrollY = scrollbar.scrollY
        }
        if (!listDragActive) return@onMouseDrag
        scrollbar.scrollTo(dragStartScrollY + it.dy)
    }

    mouse {
        scroll {
            val wheelDelta = scrollbarWheelDelta(it.scrollDeltaY, it.scrollDeltaYLines, useLineWheelDelta)
            scrollbar.scrollBy(-wheelDelta * scrollSpeed)
        }
    }
}

/** Selects the wheel delta unit requested by the caller. */
private fun scrollbarWheelDelta(pixelDelta: Float, lineDelta: Float, useLineWheelDelta: Boolean): Float =
    if (useLineWheelDelta) lineDelta else pixelDelta
