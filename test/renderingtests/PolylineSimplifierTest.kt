package renderingtests

import geometry.simplifyPath
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests polyline simplification against points inside and outside the configured tolerance.
 */
class PolylineSimplifierTest {

    /**
     * Verifies that points close enough to the simplified line are removed.
     */
    @Test
    fun simplifyPath_removesPointsWithinTolerance() {
        val path = listOf(
            Point(0, 0),
            Point(5, 0.2),
            Point(10, 0)
        )

        assertEquals(
            listOf(Point(0, 0), Point(10, 0)),
            simplifyPath(path, epsilon = 1.0)
        )
    }

    /**
     * Verifies that points farther away than the tolerance stay in the simplified path.
     */
    @Test
    fun simplifyPath_keepsPointsOutsideTolerance() {
        val path = listOf(
            Point(0, 0),
            Point(5, 5),
            Point(10, 0)
        )

        assertEquals(path, simplifyPath(path, epsilon = 1.0))
    }
}
