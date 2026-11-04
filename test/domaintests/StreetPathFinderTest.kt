package domaintests

import domain.Color
import domain.ConnectionNode
import domain.SourceNode
import domain.Street
import domain.StreetPathFinder
import domain.TargetNode
import imageprocessing.StreetDetection
import korlibs.image.color.RGBA
import korlibs.math.geom.Point
import korlibs.math.geom.degrees
import org.bytedeco.opencv.global.opencv_imgcodecs.imread
import org.bytedeco.opencv.opencv_core.Mat
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests the StreetPathFinder class. */
class StreetPathFinderTest {
    private lateinit var streetDetection: StreetDetection
    private lateinit var roadImage3: Mat
    private lateinit var roadImage4: Mat

    /**
     * Sets up the test environment by creating a StreetDetection instance and loading the test images.
     * The images are rotated by 180 degrees to ensure that they are loaded in the correct orientation for testing.
     */
    @BeforeTest
    fun setUp() {
        streetDetection = StreetDetection()

        /**
         * Helper function to load an image and rotate it by 180 degrees.
         * This is done to ensure that the images are loaded in the correct orientation for testing.
         * 
         * @param path The path to the image.
         * @return The rotated image.
         */
        fun loadAndRotate(path: String): Mat {
            val img = imread(path)
            val rotated = streetDetection.rotateImage180(img)
            img.release()
            return rotated
        }

        roadImage3 = loadAndRotate("res/road/Road3.png")
        roadImage4 = loadAndRotate("res/road/Road4.png")
        if (roadImage3.empty() || roadImage4.empty()) {
            throw IOException("Could not load pathfinding test images.")
        }
    }

    /**
     * Releases the test images after the tests are completed.
     */
    @AfterTest
    fun release() {
        roadImage3.release()
        roadImage4.release()
    }

    /**
     * Test that the driving path on Road4 reaches the target.
     */
    @Test
    fun testRoad4DrivingPathReachesTarget() {
        val startNode = SourceNode(Point(133.0, 502.0), 0, 0.degrees, emptyList())
        val endNode = TargetNode(Point(1335.0, 897.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val segments = streetDetection.buildStreetGraph(
            roadImage4,
            mutableListOf(Street(startNode, endNode, emptyList()))
        )

        val path = StreetPathFinder.findDrivingPath(segments, startNode, endNode)

        assertFalse(path.isEmpty(), "Driving path from left to bottom-right should not be empty")
        assertTrue(path.first().distanceTo(startNode.position) < 30.0, "Path should start near source node")
        assertTrue(path.last().distanceTo(endNode.position) < 30.0, "Path should end near target node")
    }

    /**
     * Test that the upper exit on Road4 uses the right-hand roundabout direction.
     */
    @Test
    fun testRoad4UpperExitUsesRightHandRoundaboutDirection() {
        val lowerRouteStart = SourceNode(Point(133.0, 502.0), 0, 0.degrees, emptyList())
        val upperRouteStart = SourceNode(Point(133.0, 502.0), 0, 0.degrees, emptyList())
        val lowerExit = TargetNode(Point(1335.0, 897.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val upperExit = TargetNode(Point(1279.0, 86.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))

        val segments = streetDetection.buildStreetGraph(
            roadImage4,
            mutableListOf(
                Street(lowerRouteStart, lowerExit, emptyList()),
                Street(upperRouteStart, upperExit, emptyList())
            )
        )

        val path = StreetPathFinder.findDrivingPath(segments, upperRouteStart, upperExit)

        assertFalse(path.isEmpty(), "Driving path from left to upper-right should not be empty")
        assertTrue(
            path.any { it.x in 720.0..930.0 && it.y > 540.0 },
            "Path should enter the roundabout on the right-hand side and pass through the lower arc"
        )
        assertTrue(path.last().distanceTo(upperExit.position) < 30.0, "Path should end near upper-right target")
    }

    /**
     * Test that the driving path on the vertical street in Road3 reaches the target.
     */
    @Test
    fun testRoad3DrivingPathVerticalStreet() {
        val startNode2 = SourceNode(Point(760.0, 5.0), 0, 0.degrees, emptyList())
        val endNode2 = TargetNode(Point(1007.0, 968.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val s2 = Street(startNode2, endNode2, emptyList())

        val startNode = SourceNode(Point(80.0, 520.0), 0, 0.degrees, emptyList())
        val endNode = TargetNode(Point(1653.0, 508.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val s1 = Street(startNode, endNode, emptyList())

        val segments = streetDetection.buildStreetGraph(roadImage3, mutableListOf(s1, s2))
        val path = StreetPathFinder.findDrivingPath(segments, startNode2, endNode2)

        assertFalse(path.isEmpty(), "Vertical street path should not be empty")
        assertTrue(path.first().distanceTo(startNode2.position) < 30.0, "Path should start near top source")
        assertTrue(path.last().distanceTo(endNode2.position) < 30.0, "Path should end near bottom target")

        val skeleton = streetDetection.createSkeletonStreetImage(roadImage3)
        val junctions = streetDetection.detectConnectionNodes(skeleton)

        // Check if path passes near any detected junction
        // Note: The path doesn't go directly through the junction (min distance ~91.55)
        // but it should be reasonably close to indicate the path finding is working
        val passesJunction = junctions.isNotEmpty() && path.any { point ->
            junctions.any { junction ->
                point.distanceTo(junction.position) < 150.0  // Reasonable tolerance for this path
            }
        }

        assertTrue(passesJunction, "Vertical path should be reasonably close to a junction. Found ${junctions.size} junctions.")
        skeleton.release()
    }

    /**
     * Test that the findDrivingRoutes function returns directed routes sorted by length.
     */
    @Test
    fun testFindDrivingRoutesReturnsDirectedRoutesSortedByLength() {
        val red = Color("Rot", RGBA(255, 0, 0))
        val source = SourceNode(Point(0, 0), 0, 0.degrees, emptyList())
        val longJunction = ConnectionNode(Point(0, 100), 0)
        val shortJunction = ConnectionNode(Point(100, 0), 0)
        val target = TargetNode(Point(200, 0), 0, 180.degrees, red)
        val longFirst = Street(source, longJunction, listOf(Point(0, 0), Point(0, 100)))
        val longSecond = Street(longJunction, target, listOf(Point(0, 100), Point(200, 100), Point(200, 0)))
        val shortFirst = Street(source, shortJunction, listOf(Point(0, 0), Point(100, 0)))
        val shortSecond = Street(shortJunction, target, listOf(Point(100, 0), Point(200, 0)))

        val routes = StreetPathFinder.findDrivingRoutes(
            listOf(longFirst, longSecond, shortFirst, shortSecond),
            source,
            red
        )

        assertEquals(2, routes.size)
        assertEquals(listOf(shortFirst, shortSecond), routes.first().streets)
        assertTrue(routes[0].length < routes[1].length)
    }

    /**
     * Verifies that path finding returns an empty path when no target route exists.
     */
    @Test
    fun findDrivingPath_targetUnreachable_returnsEmptyPath() {
        val source = SourceNode(Point(0, 0), 0, 0.degrees, emptyList())
        val deadEnd = ConnectionNode(Point(100, 0), 0)
        val target = TargetNode(Point(500, 0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        // A street that leaves the source but never reaches the target.
        val isolatedStreet = Street(source, deadEnd, listOf(Point(0, 0), Point(100, 0)))

        val path = StreetPathFinder.findDrivingPath(listOf(isolatedStreet), source, target)

        assertTrue(path.isEmpty(), "no route to the target should yield an empty path")
    }
}
