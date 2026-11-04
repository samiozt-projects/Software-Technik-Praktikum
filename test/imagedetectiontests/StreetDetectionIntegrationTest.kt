package imagedetectiontests

import domain.Color
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.TargetNode
import domain.VehicleType
import imageprocessing.StreetDetection
import imageprocessing.alignPathToEndpoints
import imageprocessing.simplifyPolyline
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import org.bytedeco.opencv.global.opencv_imgcodecs
import org.bytedeco.opencv.opencv_core.Mat
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the image-processing handoff.
 *
 * The OpenCV code now exposes raw geometry for visualization. Domain streets are built
 * elsewhere from known level nodes, so these tests only verify the detected point output
 * and the helper functions that can align/simplify those points when needed.
 */
class StreetDetectionIntegrationTest {

    private lateinit var roadImage: Mat

    /**
     * Loads the test image before each test.
     * The image is rotated by 180 degrees using the rotateImage180 method.
     */
    @BeforeTest
    fun loadRoadImage() {
        // Loading once per test keeps the assertions close to the real resource used in I1.
        val img = opencv_imgcodecs.imread("res/images/Road.png")
        roadImage = StreetDetection().rotateImage180(img)
        img.release()
        if (roadImage.empty()) throw IOException("Could not load res/images/Road.png for integration test")
    }

    /**
     * Releases the test image after the tests are completed.
     */
    @AfterTest
    fun releaseImage() {
        // OpenCV Mats own native memory, so tests release it explicitly after each run.
        roadImage.release()
    }

    /**
     * Builds a level with a source node and a target node for testing purposes.
     *
     * @return A triple containing the level, source node, and target node.
     */
    private fun buildLevelWithSourceAndTarget(): Triple<Level, SourceNode, TargetNode> {
        // Coordinates mirror the endpoints in Road.png so attachDetectedStreet can match them.
        val red = Color("Rot", Colors.RED)
        val auto = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)
        val source = SourceNode(
            Point(58, 460), 0, Angle.fromDegrees(0.0),
            listOf(SourceNodeConfiguration(red, 1.0, 50.0, auto))
        )
        val target = TargetNode(Point(1753, 460), 0, Angle.fromDegrees(0.0), red)
        val level = Level("TestLevel", 0).apply {
            addNode(source)
            addNode(target)
        }
        return Triple(level, source, target)
    }

    /**
     * Test that the detectStreets function returns visualization points without mutating the level.
     */
    @Test
    fun detectStreets_returnsVisualizationPointsWithoutMutatingLevel() {
        val (level, _, _) = buildLevelWithSourceAndTarget()

        val points = StreetDetection().detectStreets(roadImage)

        assertTrue(points.size > 2, "detected street geometry should contain drawable points")
        assertEquals(emptyList(), level.streets, "visual detection should not create domain streets")
    }

    /**
     * Test that the alignPathToEndpoints function snaps endpoints and preserves inner shape.
     */
    @Test
    fun alignPathToEndpoints_forwardPath_snapsEndpointsAndPreservesInnerShape() {
        val source = Point(0, 0)
        val target = Point(100, 0)
        val innerA = Point(25, 20)
        val innerB = Point(75, 20)

        val aligned = alignPathToEndpoints(
            listOf(Point(2, 1), innerA, innerB, Point(98, 1)),
            source,
            target
        )

        assertEquals(listOf(source, innerA, innerB, target), aligned)
    }

    /**
     * Test that the alignPathToEndpoints function flips the path toward the target when the path is reversed.
     */
    @Test
    fun alignPathToEndpoints_reversedPath_flipsPathTowardTarget() {
        val source = Point(0, 0)
        val target = Point(100, 0)
        val innerA = Point(25, 20)
        val innerB = Point(75, 20)

        val aligned = alignPathToEndpoints(
            listOf(Point(98, 1), innerB, innerA, Point(2, 1)),
            source,
            target
        )

        assertEquals(listOf(source, innerA, innerB, target), aligned)
    }

    /**
     * Test that the simplifyPolyline function removes small jitter on mostly straight path.
     */
    @Test
    fun simplifyPolyline_removesSmallJitterOnMostlyStraightPath() {
        val simplified = simplifyPolyline(
            listOf(
                Point(0, 0),
                Point(25, 1),
                Point(50, -1),
                Point(75, 1),
                Point(100, 0)
            ),
            tolerancePx = 5.0
        )

        assertEquals(listOf(Point(0, 0), Point(100, 0)), simplified)
    }

    /**
     * Test that the simplifyPolyline function keeps visible bends and endpoints.
     */
    @Test
    fun simplifyPolyline_keepsVisibleBendsAndEndpoints() {
        val source = Point(0, 0)
        val bend = Point(50, 40)
        val target = Point(100, 0)

        val simplified = simplifyPolyline(
            listOf(source, Point(25, 1), bend, Point(75, 1), target),
            tolerancePx = 10.0
        )

        assertEquals(source, simplified.first())
        assertEquals(target, simplified.last())
        assertTrue(bend in simplified, "visible bends must survive polyline simplification")
    }
}
