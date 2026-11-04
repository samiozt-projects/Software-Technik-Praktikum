package imagedetectiontests

import domain.Node
import domain.Street
import imageprocessing.DetectionPreviewBuffer
import imageprocessing.streetEdgeKey
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests [DetectionPreviewBuffer]'s visual smoothing: streets appear immediately, survive exactly one
 * missed capture, and disappear after the second consecutive miss. The buffer is display-only, so
 * these tests only concern the returned render list - confirmation/commit behavior is covered by
 * [StreetNetworkStabilityTracker]'s own usage sites.
 */
class DetectionPreviewBufferTest {

    /**
     * Minimal concrete [Node] for building test streets.
     * @param position The node's position.
     * @param elevation The node's elevation level.
     */
    private class TestNode(position: Point, elevation: Int) : Node(position, elevation)

    /** Builds a street between two well-separated points so each street has a distinct edge key. */
    private fun street(fromX: Double, toX: Double): Street {
        val start = TestNode(Point(fromX, 50.0), 0)
        val end = TestNode(Point(toX, 50.0), 0)
        return Street(start, end, listOf(start.position, end.position))
    }

    private fun keysOf(streets: List<Street>): Set<String> = streets.map(::streetEdgeKey).toSet()

    /** Verifies that a newly detected street is rendered immediately, without a confirmation delay. */
    @Test
    fun update_newStreet_appearsImmediately() {
        val buffer = DetectionPreviewBuffer()
        val detected = street(0.0, 100.0)

        val rendered = buffer.update(listOf(detected))

        assertEquals(setOf(streetEdgeKey(detected)), keysOf(rendered))
    }

    /** Verifies that a street missing from exactly one capture stays in the rendered preview. */
    @Test
    fun update_streetMissingOnce_isStillRendered() {
        val buffer = DetectionPreviewBuffer()
        val flaky = street(0.0, 100.0)
        val stable = street(0.0, 200.0)

        buffer.update(listOf(flaky, stable))
        val rendered = buffer.update(listOf(stable))

        assertTrue(streetEdgeKey(flaky) in keysOf(rendered), "a single missed capture must not remove the street from the preview")
        assertTrue(streetEdgeKey(stable) in keysOf(rendered))
    }

    /** Verifies that a street missing from two consecutive captures is removed from the preview. */
    @Test
    fun update_streetMissingTwiceInARow_isRemoved() {
        val buffer = DetectionPreviewBuffer()
        val flaky = street(0.0, 100.0)
        val stable = street(0.0, 200.0)

        buffer.update(listOf(flaky, stable))
        buffer.update(listOf(stable))
        val rendered = buffer.update(listOf(stable))

        assertEquals(setOf(streetEdgeKey(stable)), keysOf(rendered), "two consecutive misses must remove the street")
    }

    /** Verifies that a reappearing street restarts its missing streak instead of continuing the old one. */
    @Test
    fun update_reappearingStreet_resetsItsMissingStreak() {
        val buffer = DetectionPreviewBuffer()
        val flaky = street(0.0, 100.0)
        val stable = street(0.0, 200.0)

        buffer.update(listOf(flaky, stable))
        buffer.update(listOf(stable))
        buffer.update(listOf(flaky, stable))
        val rendered = buffer.update(listOf(stable))

        assertTrue(
            streetEdgeKey(flaky) in keysOf(rendered),
            "after reappearing, the street's missing streak must restart - one new miss alone must not remove it"
        )
    }

    /** Verifies that a re-detected street renders the freshest instance, not the first-seen geometry. */
    @Test
    fun update_redetectedStreet_rendersTheFreshInstance() {
        val buffer = DetectionPreviewBuffer()
        val firstDetection = street(0.0, 100.0)
        // Same bucketed edge identity, but a fresh instance with slightly wobbled geometry.
        val secondDetection = Street(
            TestNode(Point(0.5, 50.4), 0),
            TestNode(Point(100.4, 49.6), 0),
            listOf(Point(0.5, 50.4), Point(100.4, 49.6))
        )
        assertEquals(streetEdgeKey(firstDetection), streetEdgeKey(secondDetection), "test precondition: same edge identity")

        buffer.update(listOf(firstDetection))
        val rendered = buffer.update(listOf(secondDetection))

        assertEquals(1, rendered.size)
        assertTrue(rendered.single() === secondDetection, "the preview must render the most recently detected instance")
    }
}
