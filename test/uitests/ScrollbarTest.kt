package uitests

import korlibs.korge.tests.ViewsForTesting
import korlibs.korge.view.container
import korlibs.korge.view.position
import ui.Scrollbar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests input geometry for the shared [Scrollbar]. */
class ScrollbarTest : ViewsForTesting() {

    /**
     * A scrollbar placed in an offset list container must test against stage coordinates. This
     * prevents a thumb drag from also being interpreted as a drag on the world-list surface.
     */
    @Test
    fun contains_usesStageCoordinatesWhenParentIsOffset() = viewsTest {
        val parentX = 150.0
        val parentY = 285.0
        val scrollbarX = 968.0
        val scrollbarY = 62.0
        val scrollbar = container {
            position(parentX, parentY)
        }.let { parent ->
            Scrollbar(
                parent = parent,
                x = scrollbarX,
                y = scrollbarY,
                barHeight = 526.0,
                viewportHeight = 626.0,
                contentHeight = 10_000.0,
                onScroll = {}
            )
        }

        assertTrue(scrollbar.contains(parentX + scrollbarX + 10.0, parentY + scrollbarY + 10.0))
        assertFalse(scrollbar.contains(scrollbarX + 10.0, scrollbarY + 10.0))
    }

    /** Verifies that optional arrow buttons move by their configured page distance. */
    @Test
    fun pageArrowButtons_moveScrollbarByConfiguredPageDistance() = viewsTest {
        val trackY = 50.0
        val trackHeight = 200.0
        val scrollbar = Scrollbar(
            parent = this,
            x = 0.0,
            y = trackY,
            barHeight = trackHeight,
            viewportHeight = 200.0,
            contentHeight = 1_000.0,
            pageScrollDistance = 100.0,
            onScroll = {}
        )

        val arrowCenterX = Scrollbar.WIDTH / 2.0
        val upArrowCenterY = trackY - Scrollbar.PAGE_BUTTON_GAP - Scrollbar.PAGE_BUTTON_HEIGHT / 2.0
        val downArrowCenterY = trackY + trackHeight + Scrollbar.PAGE_BUTTON_GAP + Scrollbar.PAGE_BUTTON_HEIGHT / 2.0

        assertTrue(scrollbar.contains(arrowCenterX, downArrowCenterY))
        mouseMoveAndClickTo(arrowCenterX, downArrowCenterY)
        assertEquals(-100.0, scrollbar.scrollY)

        mouseMoveAndClickTo(arrowCenterX, upArrowCenterY)
        assertEquals(0.0, scrollbar.scrollY)
    }

    /** Wheel navigation can use the same page distance as the on-screen arrow buttons. */
    @Test
    fun scrollByPage_movesInTheRequestedDirection() = viewsTest {
        val scrollbar = Scrollbar(
            parent = this,
            x = 0.0,
            y = 0.0,
            barHeight = 200.0,
            viewportHeight = 200.0,
            contentHeight = 1_000.0,
            pageScrollDistance = 100.0,
            onScroll = {}
        )

        scrollbar.scrollByPage(direction = 1)
        assertEquals(-100.0, scrollbar.scrollY)

        scrollbar.scrollByPage(direction = -1)
        assertEquals(0.0, scrollbar.scrollY)
    }

    /** A shorter caller-provided step can be used without changing the page-arrow distance. */
    @Test
    fun scrollByStep_usesProvidedDistance() = viewsTest {
        val scrollbar = Scrollbar(
            parent = this,
            x = 0.0,
            y = 0.0,
            barHeight = 200.0,
            viewportHeight = 200.0,
            contentHeight = 1_000.0,
            pageScrollDistance = 100.0,
            onScroll = {}
        )

        scrollbar.scrollByStep(direction = 1, distance = 50.0)
        assertEquals(-50.0, scrollbar.scrollY)
    }
}
