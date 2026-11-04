package renderingtests

import domain.Color
import domain.ConnectionNode
import domain.SourceNode
import domain.TargetNode
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import rendering.GameAreaScale
import scenes.LevelScene
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests construction marker positioning for different node types.
 */
class ConstructionMarkerPositionTest {

    /**
     * Verifies that the source marker is positioned at the visible exit of the source arrow.
     */
    @Test
    fun sourceMarkerUsesVisibleExitEndOfSourceArrow() {
        val source = SourceNode(Point(25, 62.5), 0, Angle.fromDegrees(90.0))

        val marker = LevelScene.constructionMarkerPositionForNode(source)

        assertPointNear(
            Point(
                GameAreaScale.virtualMetersToPixels(25.0) + LevelScene.ARROW_L_PX / 2.0,
                GameAreaScale.virtualMetersToPixels(62.5)
            ),
            marker
        )
    }

    /**
     * Verifies that the target marker is positioned at the visible inbound end of the target arrow.
     */
    @Test
    fun targetMarkerUsesVisibleInboundEndOfTargetArrow() {
        val target = TargetNode(Point(200, 62.5), 0, Angle.fromDegrees(270.0), Color("Red", Colors.RED))

        val marker = LevelScene.constructionMarkerPositionForNode(target)

        assertPointNear(
            Point(
                GameAreaScale.virtualMetersToPixels(200.0) - LevelScene.ARROW_L_PX / 2.0,
                GameAreaScale.virtualMetersToPixels(62.5)
            ),
            marker
        )
    }

    /**
     * Verifies that the connection node marker uses the scaled node position.
     */
    @Test
    fun connectionNodeMarkerUsesScaledNodePosition() {
        val connection = ConnectionNode(Point(112.5, 62.5), 0)

        val marker = LevelScene.constructionMarkerPositionForNode(connection)

        assertPointNear(
            Point(
                GameAreaScale.virtualMetersToPixels(connection.position.x),
                GameAreaScale.virtualMetersToPixels(connection.position.y)
            ),
            marker
        )
    }

    /**
     * Helper function to assert that two points are approximately equal.
     * 
     * @param expected The expected point.
     * @param actual The actual point.
     */
    private fun assertPointNear(expected: Point, actual: Point) {
        assertTrue(
            actual.distanceTo(expected) < 0.0001,
            "expected $actual to be within tolerance of $expected"
        )
    }
}
