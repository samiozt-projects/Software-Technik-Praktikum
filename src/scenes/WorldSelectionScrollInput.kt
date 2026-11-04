package scenes

import korlibs.korge.input.mouse
import korlibs.korge.input.onMouseDrag
import korlibs.korge.view.SContainer
import korlibs.time.PerformanceCounter
import ui.Scrollbar
import kotlin.time.Duration.Companion.milliseconds

/** Minimum interval in milliseconds that collapses events from one physical mouse-wheel tick. */
private const val WHEEL_PAGE_DEBOUNCE_MILLISECONDS = 80L

/** Minimum interval that collapses duplicate events from one physical mouse-wheel tick. */
private val WHEEL_PAGE_DEBOUNCE = WHEEL_PAGE_DEBOUNCE_MILLISECONDS.milliseconds

/**
 * Attaches drag and wheel handlers to a world-list scrollbar.
 *
 * The scrollbar callback is evaluated for every event, so it remains valid after a list refresh
 * replaces the old scrollbar instance.
 *
 * @param scrollbar callback returning the current scrollbar.
 * @param wheelStepDistance fixed distance moved by a wheel direction event.
 */
internal fun SContainer.attachWorldListScrolling(
    scrollbar: () -> Scrollbar?,
    wheelStepDistance: Double
) {
    var dragStartScrollY = 0.0
    var listDragActive = false
    var lastWheelPageScrollTime = PerformanceCounter.reference - WHEEL_PAGE_DEBOUNCE
    onMouseDrag {
        val bar = scrollbar() ?: return@onMouseDrag
        if (it.start) {
            listDragActive = !bar.contains(it.sx, it.sy)
            dragStartScrollY = bar.scrollY
        }
        if (listDragActive) bar.scrollTo(dragStartScrollY + it.dy)
    }
    mouse {
        scroll {
            val bar = scrollbar() ?: return@scroll
            val now = PerformanceCounter.reference
            if (now - lastWheelPageScrollTime < WHEEL_PAGE_DEBOUNCE) return@scroll

            val direction = it.scrollDeltaYPixels.compareTo(0.0f)
            if (direction == 0) return@scroll
            bar.scrollByStep(direction, wheelStepDistance)
            lastWheelPageScrollTime = now
        }
    }
}
