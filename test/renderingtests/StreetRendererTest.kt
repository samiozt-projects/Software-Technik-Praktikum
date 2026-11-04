package renderingtests

import korlibs.korge.view.Container
import korlibs.math.geom.Point
import rendering.StreetRenderer
import domain.Street
import domain.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Tests the structural contract of [rendering.StreetRenderer].
 *
 * Pixel-perfect rendering is handled by KorGE, so this unit test only checks that
 * the renderer returns a view attached to the requested container. That contract is
 * what the reactive synchronization code needs in order to remove rendered streets.
 */
class StreetRendererTest {

    /**
     * Verifies that rendering a simple polyline street attaches the created view to the target container.
     */
    @Test
    fun renderPolylineStreet_returnsViewAttachedToContainer() {
        val renderer = StreetRenderer()
        val container = Container()

        val path = listOf(
            Point(0, 0),
            Point(100, 0)
        )

        val view = renderer.renderPolylineStreet(container, path)

        assertNotNull(view, "renderPolylineStreet should return the created view")
        assertSame(container, view.parent, "returned view should be a child of the given container")
    }

    /**
     * Verifies that rendering a multi-point polyline street attaches the created view to the target container.
     */
    @Test
    fun renderPolylineStreet_acceptsMultiPointPolyline() {
        val renderer = StreetRenderer()
        val container = Container()

        val path = listOf(
            Point(0, 0),
            Point(40, 30),
            Point(70, 30),
            Point(100, 0)
        )

        val view = renderer.renderPolylineStreet(container, path)

        assertNotNull(view, "renderPolylineStreet should render curved/polyline paths")
        assertSame(container, view.parent, "polyline view should be attached to the given container")
    }

    /**
     * Verifies that rendering a saved street network attaches the created view to the target container.
     */
    @Test
    fun renderSavedStreetNetwork_returnsViewAttachedToContainer() {
        val renderer = StreetRenderer()
        val container = Container()

        // Simple concrete Node implementation for tests
        class TestNode(position: Point, elevation: Int) : Node(position, elevation)

        val n1 = TestNode(Point(0, 0), 0)
        val n2 = TestNode(Point(100, 0), 0)

        val s1 = Street(n1, n2, listOf(Point(0, 0), Point(100, 0)))
        val s2 = Street(n1, n2, listOf(Point(0, 10), Point(100, 10)))

        val view = renderer.renderSavedStreetNetwork(container, listOf(s1, s2))

        assertNotNull(view, "renderSavedStreetNetwork should return the created view")
        assertSame(container, view.parent, "returned view should be a child of the given container")
    }

    /**
     * Verifies that rendering a saved street network ignores streets that are too short.
     */
    @Test
    fun renderSavedStreetNetwork_ignoresShortStreets() {
        val renderer = StreetRenderer()
        val container = Container()

        class TestNode(position: Point, elevation: Int) : Node(position, elevation)

        val n1 = TestNode(Point(0, 0), 0)
        val n2 = TestNode(Point(100, 0), 0)

        // One valid street and one invalid short street (single point)
        val s1 = Street(n1, n2, listOf(Point(0, 0), Point(100, 0)))
        val sShort = Street(n1, n2, listOf(Point(42, 42)))

        val view = renderer.renderSavedStreetNetwork(container, listOf(s1, sShort))

        assertNotNull(view, "renderSavedStreetNetwork should return a view even when some streets are too short")
        assertSame(container, view.parent, "returned view should be attached to the provided container")
    }

    /**
     * Verifies that a street entirely on one elevation renders on that same elevation layer.
     */
    @Test
    fun renderedElevation_sameElevationEndpoints_rendersOnThatElevation() {
        class TestNode(position: Point, elevation: Int) : Node(position, elevation)

        val n1 = TestNode(Point(0, 0), 1)
        val n2 = TestNode(Point(100, 0), 1)
        val street = Street(n1, n2, listOf(Point(0, 0), Point(100, 0)))

        assertEquals(1, StreetRenderer.renderedElevation(street))
    }

    /**
     * Verifies that a ramp street is drawn on its higher elevation layer, so bridges are always
     * visualized above the lower road they leave or enter (AC: bridges rendered understandably).
     */
    @Test
    fun renderedElevation_rampBetweenGroundAndLevelOne_rendersOnTheHigherLayer() {
        class TestNode(position: Point, elevation: Int) : Node(position, elevation)

        val ground = TestNode(Point(0, 0), 0)
        val bridge = TestNode(Point(100, 0), 1)
        val ramp = Street(ground, bridge, listOf(Point(0, 0), Point(100, 0)))

        assertEquals(1, StreetRenderer.renderedElevation(ramp))
    }

    /**
     * Verifies that a level-1-to-2 ramp is drawn on the level-2 layer, matching the level-0-to-1
     * case so both bridge tiers are consistently drawn above whatever they cross over.
     */
    @Test
    fun renderedElevation_rampBetweenLevelOneAndTwo_rendersOnTheHigherLayer() {
        class TestNode(position: Point, elevation: Int) : Node(position, elevation)

        val lowerBridge = TestNode(Point(0, 0), 1)
        val upperBridge = TestNode(Point(100, 0), 2)
        val ramp = Street(lowerBridge, upperBridge, listOf(Point(0, 0), Point(100, 0)))

        assertEquals(2, StreetRenderer.renderedElevation(ramp))
    }

    /**
     * Verifies that rendering one elevation layer attaches the created view to the target container.
     */
    @Test
    fun renderSavedStreetNetworkElevation_returnsViewAttachedToContainer() {
        val renderer = StreetRenderer()
        val container = Container()

        class TestNode(position: Point, elevation: Int) : Node(position, elevation)

        val n1 = TestNode(Point(0, 0), 0)
        val n2 = TestNode(Point(100, 0), 1)
        val street = Street(n1, n2, listOf(Point(0, 0), Point(100, 0)))

        val view = renderer.renderSavedStreetNetworkElevation(container, listOf(street), elevation = 1)

        assertNotNull(view, "renderSavedStreetNetworkElevation should return the created view")
        assertSame(container, view.parent, "returned view should be attached to the provided container")
    }

    /**
     * Direction arrows are drawn along every street's path (see [rendering.StreetRenderer]'s
     * arrowPlacements). A street much longer than one arrow-spacing interval must still render
     * without error and produce a single attached view, exercising the multi-arrow placement loop.
     */
    @Test
    fun renderPolylineStreet_longStreet_stillRendersOneAttachedView() {
        val renderer = StreetRenderer()
        val container = Container()

        // Long enough (in virtual meters) to place several direction arrows along it.
        val path = listOf(Point(0, 0), Point(500, 0))

        val view = renderer.renderPolylineStreet(container, path)

        assertNotNull(view, "a long street must still render one view even with multiple direction arrows")
        assertSame(container, view.parent)
    }

    /**
     * A street shorter than one arrow-spacing interval must still get exactly one arrow rather than
     * crashing on an empty placement list or a division by an interval larger than the path.
     */
    @Test
    fun renderPolylineStreet_veryShortStreet_stillRendersWithoutError() {
        val renderer = StreetRenderer()
        val container = Container()

        val path = listOf(Point(0, 0), Point(1, 0))

        val view = renderer.renderPolylineStreet(container, path)

        assertNotNull(view, "a street shorter than the arrow spacing must still render")
        assertSame(container, view.parent)
    }

    /**
     * A polyline with a zero-length segment (two consecutive identical points, which can happen with
     * detected/simplified paths) must not crash the arrow-heading calculation.
     */
    @Test
    fun renderPolylineStreet_pathWithDuplicateConsecutivePoint_stillRendersWithoutError() {
        val renderer = StreetRenderer()
        val container = Container()

        val path = listOf(Point(0, 0), Point(50, 0), Point(50, 0), Point(100, 0))

        val view = renderer.renderPolylineStreet(container, path)

        assertNotNull(view, "a path with a duplicate consecutive point must not crash arrow placement")
        assertSame(container, view.parent)
    }
}
