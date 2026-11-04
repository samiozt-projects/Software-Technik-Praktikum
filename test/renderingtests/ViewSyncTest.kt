package renderingtests

import korlibs.korge.view.Container
import korlibs.korge.view.SolidRect
import korlibs.korge.view.View
import rendering.computeViewDiff
import rendering.syncViews
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests the generic bridge between mutable domain sets and KorGE views.
 *
 * LevelScene uses this helper for streets and vehicles, so the tests cover both
 * the pure diff calculation and the side effects of attaching/removing views.
 */
class ViewSyncTest {

    /**
     * Verifies that computeViewDiff adds new items and removes gone ones.
     */
    @Test
    fun computeViewDiff_addsNewItems_andRemovesGoneOnes() {
        // B stays, C must be created, and A must be removed.
        val tracked = setOf("A", "B")
        val current = setOf("B", "C")

        val diff = computeViewDiff(tracked, current)

        assertEquals(setOf("C"), diff.toAdd)
        assertEquals(setOf("A"), diff.toRemove)
    }

    /**
     * Verifies that identical sets produce an empty diff.
     */
    @Test
    fun computeViewDiff_identicalSets_returnsEmptyDiff() {
        // Equal sets should produce no work for the caller.
        val s = setOf(1, 2, 3)
        val diff = computeViewDiff(s, s)

        assertEquals(emptySet(), diff.toAdd)
        assertEquals(emptySet(), diff.toRemove)
    }

    /**
     * Verifies that an empty tracked set results in adding all current items.
     */
    @Test
    fun computeViewDiff_emptyTracked_addsEverything() {
        // Initial synchronization starts with no views, so every domain item is new.
        val diff = computeViewDiff(emptySet<Int>(), setOf(1, 2))

        assertEquals(setOf(1, 2), diff.toAdd)
        assertEquals(emptySet(), diff.toRemove)
    }

    /**
     * Verifies that syncViews adds a view for each new item and tracks it.
     */
    @Test
    fun syncViews_addsViewForNewItem() {
        // The factory simulates a caller-specific renderer that attaches the view to its layer.
        val views = mutableMapOf<String, View>()
        val parent = Container()
        val createdView = SolidRect.Companion(10, 10)
        val factory: (String) -> View = { _ ->
            parent.addChild(createdView)
            createdView
        }

        syncViews(setOf("A"), views, factory)

        assertTrue("A" in views, "expected new item to be tracked in views map")
        assertSame(createdView, views["A"])
    }

    /**
     * Verifies that syncViews removes the view for a gone item and detaches it from the parent.
     */
    @Test
    fun syncViews_removesViewForGoneItem_andDetachesFromParent() {
        // Removing an item must update both the bookkeeping map and the actual scene graph.
        val views = mutableMapOf<String, View>()
        val parent = Container()
        val viewA = SolidRect.Companion(10, 10).also { parent.addChild(it) }
        views["A"] = viewA

        syncViews(emptySet(), views, factory = { error("factory should not be called") })

        assertTrue("A" !in views, "view for gone item should be untracked")
        assertEquals(null, viewA.parent, "view for gone item should be detached from parent")
    }
}
