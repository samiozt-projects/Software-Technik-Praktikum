package imagedetectiontests

import domain.Color
import domain.Level
import domain.Node
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.StreetPathFinder
import domain.TargetNode
import domain.VehicleType
import imageprocessing.CameraManager
import imageprocessing.StreetDetection
import imageprocessing.StreetDetectionIssueKind
import simulation.Simulation
import korlibs.image.color.RGBA
import korlibs.math.geom.degrees
import org.bytedeco.opencv.global.opencv_core.CV_8UC3
import org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGR
import org.bytedeco.opencv.global.opencv_imgproc.boundingRect
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.global.opencv_imgproc.line
import org.bytedeco.opencv.global.opencv_imgproc.rectangle
import org.bytedeco.opencv.global.opencv_imgcodecs.imread
import org.bytedeco.opencv.global.opencv_objdetect.DICT_4X4_50
import org.bytedeco.opencv.global.opencv_objdetect.generateImageMarker
import org.bytedeco.opencv.global.opencv_objdetect.getPredefinedDictionary
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.Scalar
import org.bytedeco.opencv.opencv_core.Point as OpenCVPoint
import persistence.LevelSerializer
import rendering.GameAreaScale
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.BeforeTest
import kotlin.test.Test
import korlibs.math.geom.Point
import java.io.IOException
import kotlin.math.abs

/**
 * StreetDetectionTest is a class that contains unit tests for the StreetDetection class.
 * It tests the detectStreetPolylines, detectStreets, and rotateImage180 methods.
 * 
 * The tests use the imread function from the org.bytedeco.opencv.global.opencv_imgcodecs package to load images.
 * The images are then rotated by 180 degrees using the rotateImage180 method and checked for correctness.
 * The detectStreetPolylines method is tested by checking the number of polylines detected in each image.
 * The detectStreets method is tested by checking the number of visualization points detected in each image.
 * 
 * The tests use the OpenCVPoint class from the org.bytedeco.opencv.opencv_core package to represent points.
 * The Mat class from the org.bytedeco.opencv.opencv_core package is used to represent images.
 * The Scalar class from the org.bytedeco.opencv.opencv_core package is used to represent colors.
 * 
 * The tests are run using the JUnit framework.
 */
class StreetDetectionTest {

    private lateinit var streetDetection: StreetDetection
    private lateinit var emptyImage: Mat
    private lateinit var roadImage: Mat
    private lateinit var roadImage2: Mat
    private lateinit var roadImage3: Mat
    private lateinit var roadImage4: Mat
    private lateinit var roadImage5: Mat
    private val bridgeTestColor = Color("Rot", RGBA(255, 0, 0))
    private val bridgeTestVehicle = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)

    /** Creates a source with a real demand so one-way bridge tests validate source-to-target routing. */
    private fun bridgeTestSource(position: Point): SourceNode = SourceNode(
        position,
        0,
        0.degrees,
        listOf(SourceNodeConfiguration(bridgeTestColor, 5.0, 50.0, bridgeTestVehicle))
    )

    /**
     * Sets up the test environment by initializing the StreetDetection object and loading test images.
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

        roadImage = loadAndRotate("res/road/Road.png")
        roadImage2 = loadAndRotate("res/road/Road2.png")
        roadImage3 = loadAndRotate("res/road/Road3.png")
        roadImage4 = loadAndRotate("res/road/Road4.png")
        roadImage5 = loadAndRotate("res/road/Road5.png")
        if (
            roadImage.empty() ||
            roadImage2.empty() ||
            roadImage3.empty() ||
            roadImage4.empty() ||
            roadImage5.empty()
        ) {
            throw IOException("Could not load test images.")
        }
        emptyImage = Mat() // An empty Mat for testing empty images
    }

    /**
     * Releases the test images after the tests are completed.
     */
    @AfterTest
    fun release() {
        roadImage.release()
        roadImage2.release()
        roadImage3.release()
        roadImage4.release()
        roadImage5.release()
        emptyImage.release()
    }

    /**
     * Test that the detectStreetPolylines function returns an empty list for an empty image.
     */
    @Test
    fun checkPolylinesOfEmptyImage() {
        val polylines = streetDetection.detectStreetPolylines(emptyImage)
        assertTrue(polylines.isEmpty())
    }

    /**
     * Test that processing an empty image does not report an ArUco marker issue.
     */
    @Test
    fun processLevelImage_emptyImageDoesNotReportArucoMarkerIssue() {
        val level = buildSimpleSourceTargetLevel()

        val result = streetDetection.processLevelImage(level, emptyImage)

        assertEquals(emptyList(), result.streets)
        assertEquals(emptyList(), result.issues)
    }

    /**
     * Test that a non-camera test image without red street pixels does not report an ArUco issue.
     */
    @Test
    fun processLevelImage_imageWithoutRedStreetDoesNotReportArucoMarkerIssue() {
        val level = buildSimpleSourceTargetLevel()
        val image = Mat(100, 240, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))

        val result = streetDetection.processLevelImage(level, image)

        assertEquals(emptyList(), result.streets)
        assertEquals(emptyList(), result.issues)

        image.release()
    }

    /**
     * Test that a camera-sized image with one hidden ArUco marker reports only the marker issue.
     */
    @Test
    fun processLevelImage_cameraImageWithMissingArucoMarkerReportsMarkerIssue() {
        val level = buildSimpleSourceTargetLevel()
        val image = cameraSizedFrameWithCornerMarkers(missingBottomRight = true)

        val result = streetDetection.processLevelImage(level, image)

        assertEquals(
            listOf(StreetDetectionIssueKind.MISSING_ARUCO_MARKERS),
            result.issues.map { it.kind }
        )

        image.release()
    }

    /**
     * AC: an illegal direct level-0-to-2 bridge must not silently disappear from detection - the
     * construction phase needs to tell the player why nothing was built there (Lastenheft §3.3:
     * "Verbindungsfehler ... müssen ... angezeigt werden").
     */
    @Test
    fun processLevelImage_directLevelZeroToTwoJumpReportsInvalidElevationTransitionIssue() {
        // processLevelImage snaps a level's source/target positions in *virtual meters*, so they
        // must be converted from the pixel coordinates used to draw the test image below.
        val source = SourceNode(GameAreaScale.translateVirtualPointToInGamePoint(Point(20.0, 50.0)), 0, 0.degrees, emptyList())
        val target = TargetNode(GameAreaScale.translateVirtualPointToInGamePoint(Point(220.0, 50.0)), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val level = Level("DetectionDiagnostics", 0)
        level.addNode(source)
        level.addNode(target)

        val image = Mat(100, 240, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        rectangle(image, OpenCVPoint(95, 34), OpenCVPoint(145, 66), Scalar(0.0, 255.0, 0.0, 0.0), -1, 8, 0)
        line(image, OpenCVPoint(20, 50), OpenCVPoint(220, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)

        val result = streetDetection.processLevelImage(level, image)

        assertTrue(
            result.issues.any { it.kind == StreetDetectionIssueKind.INVALID_ELEVATION_TRANSITION },
            "A rejected 0-to-2 jump must be surfaced as an INVALID_ELEVATION_TRANSITION issue"
        )

        image.release()
    }

    /**
     * Test that the detectStreetPolylines function detects at least one polyline for Road1.
     */
    @Test
    fun checkPolylinesOfNonEmptyImage() {
        val polylines = streetDetection.detectStreetPolylines(roadImage)
        // At least one polyline should be detected in Road.png
        assertFalse(polylines.isEmpty())
        polylines.forEach { it.release() }
    }

    /**
     * Test that the detectStreetPolylines function detects the correct number of polylines for Road1.
     */
    @Test
    fun detectPolylinesOfRoadImage() {
        val polylines = streetDetection.detectStreetPolylines(roadImage)

        assertFalse(polylines.isEmpty(), "Road.png should produce at least one polyline")

        val polylineMat = polylines.maxBy { boundingRect(it).width() }

        // Polyline should have more than 2 points to represent a shape
        assertTrue(polylineMat.rows() > 2)

        val imageWidth = roadImage.cols()
        val imageHeight = roadImage.rows()

        val middleY = imageHeight / 2.0

        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE

        val yCoordinates = mutableListOf<Int>()

        for (i in 0 until polylineMat.rows()) {
            val point = OpenCVPoint(polylineMat.ptr(i))
            val x = point.x()
            val y = point.y()

            minX = minOf(minX, x)
            maxX = maxOf(maxX, x)
            minY = minOf(minY, y)
            maxY = maxOf(maxY, y)
            yCoordinates.add(y)
        }

        // Check horizontal span: should cover a significant portion of the image width
        val horizontalCoverage = (maxX - minX).toDouble() / imageWidth
        assertTrue(horizontalCoverage > 0.5, "Polyline should span more than 50% of the image width (actual: ${horizontalCoverage * 100}%)")
        assertTrue(minX < imageWidth * 0.25, "Polyline should start in the left 25% of the image (actual minX: $minX, expected < ${imageWidth * 0.25})")
        assertTrue(maxX > imageWidth * 0.70, "Polyline should end in the right 30% of the image (actual maxX: $maxX, expected > ${imageWidth * 0.70})")


        // Check vertical position: should be roughly in the middle
        val verticalCenter = (minY + maxY) / 2.0
        val verticalTolerance = imageHeight * 0.15 // Allow 15% deviation from center
        assertTrue(verticalCenter > middleY - verticalTolerance && verticalCenter < middleY + verticalTolerance, "Polyline vertical center ($verticalCenter) should be near image middle ($middleY) with tolerance $verticalTolerance")

        // Check for approximate straightness (low variance in Y coordinates)
        val avgY = yCoordinates.average()
        val varianceY = yCoordinates.map { (it - avgY) * (it - avgY) }.average()
        val stdDevY = kotlin.math.sqrt(varianceY)
        val straightnessTolerance = imageHeight * 0.05 // Allow 5% deviation in Y for straightness
        assertTrue(stdDevY < straightnessTolerance, "Polyline should be relatively straight horizontally (stdDevY: $stdDevY, tolerance: $straightnessTolerance)")

        polylines.forEach { it.release() }
    }

    /**
     * Test that the detectStreets function detects at least one visualization point for Road1.
     */
    @Test
    fun testDetectStreets() {
        val points = streetDetection.detectStreets(roadImage)

        assertFalse(points.isEmpty(), "Should detect at least one visualization point")
        assertTrue(points.size > 2, "Detected visualization path should have more than 2 points")
    }

    /**
     * Test that the createSkeletonStreetImage function creates a skeleton with the correct aspect ratio.
     */
    @Test
    fun testSkeletonCropKeepsTableAspectRatio() {
        val skeleton = streetDetection.createSkeletonStreetImage(roadImage)

        // cropTable now normalizes every detection back to the fixed desk frame so the skeleton stays
        // in the same coordinate system the rest of the pipeline assumes (no more variable-size crop).
        assertEquals(
            GameAreaScale.DESK_W_PX,
            skeleton.cols(),
            "Skeleton must be normalized to the desk frame width"
        )
        assertEquals(
            GameAreaScale.DESK_H_PX,
            skeleton.rows(),
            "Skeleton must be normalized to the desk frame height"
        )

        val aspectRatio = skeleton.cols().toDouble() / skeleton.rows().toDouble()
        assertTrue(
            abs(aspectRatio - 9.0 / 5.0) < 0.01,
            "Normalized table image should keep 9:5 aspect ratio, got $aspectRatio"
        )

        skeleton.release()
    }

    /**
     * Tests the rotateImage180 method to ensure that it correctly rotates the input image by 180 degrees.
     * This is done by rotating the image twice and checking sample pixels to ensure that we get back to the original image.
     * The roadImage is already rotated 180 degrees, so we expect the double rotated image to be the same as the original.
     */
    @Test
    fun testRotateImage180() {
        val rotated = streetDetection.rotateImage180(roadImage)
        
        assertEquals(roadImage.cols(), rotated.cols(), "Rotated image should have same width")
        assertEquals(roadImage.rows(), rotated.rows(), "Rotated image should have same height")
        assertFalse(rotated.empty(), "Rotated image should not be empty")
        
        // Rotate again and check if we get back to original pixel values
        val rotatedTwice = streetDetection.rotateImage180(rotated)
        
        // Check some sample pixels to verify the double rotation brings us back to original
        val samplePoints = listOf(
            OpenCVPoint(10, 10),
            OpenCVPoint(roadImage.cols() / 2, roadImage.rows() / 2),
            OpenCVPoint(roadImage.cols() - 10, roadImage.rows() - 10)
        )
        
        for (point in samplePoints) {
            // For color images (CV_8UC3), we need to access each channel separately
            val originalB = roadImage.ptr(point.y(), point.x()).get(0).toInt() and 0xFF
            val originalG = roadImage.ptr(point.y(), point.x()).get(1).toInt() and 0xFF
            val originalR = roadImage.ptr(point.y(), point.x()).get(2).toInt() and 0xFF

            val rotatedB = rotatedTwice.ptr(point.y(), point.x()).get(0).toInt() and 0xFF
            val rotatedG = rotatedTwice.ptr(point.y(), point.x()).get(1).toInt() and 0xFF
            val rotatedR = rotatedTwice.ptr(point.y(), point.x()).get(2).toInt() and 0xFF
            
            // Check if pixels are equal
            assertEquals(originalB, rotatedB, "Blue channel at (${point.x()}, ${point.y()}) should match after double rotation")
            assertEquals(originalG, rotatedG, "Green channel at (${point.x()}, ${point.y()}) should match after double rotation")
            assertEquals(originalR, rotatedR, "Red channel at (${point.x()}, ${point.y()}) should match after double rotation")
        }
        
        rotated.release()
        rotatedTwice.release()
    }

    /**
     * Test that the detectConnectionNodes function returns an empty list for an empty skeleton.
     */
    @Test
    fun testDetectConnectionNodesWithEmptySkeleton() {
        val emptySkeleton = Mat()
        val nodes = streetDetection.detectConnectionNodes(emptySkeleton)
        assertTrue(nodes.isEmpty(), "Should return empty list for empty skeleton")
        emptySkeleton.release()
    }

    /**
     * Test that the detectConnectionNodes function detects the correct number of connection nodes for Road2.
     */
    @Test
    fun testDetectConnectionNodesWithRoadImageWithoutAnyJunctions() {
        // Create skeleton from road image
        val skeleton = streetDetection.createSkeletonStreetImage(roadImage2)
        assertFalse(skeleton.empty(), "Skeleton should not be empty for the image")

        // Detect connection nodes
        val nodes = streetDetection.detectConnectionNodes(skeleton)

        // Should detect no connection nodes (junctions/branches)
        assertEquals(0, nodes.size)

        skeleton.release()
    }

    /**
     * Test that the detectConnectionNodes function detects the correct number of connection nodes for Road3.
     */
    @Test
    fun testDetectConnectionNodesWithRoadImageWithJunction() {
        // Create skeleton from road image
        val skeleton = streetDetection.createSkeletonStreetImage(roadImage3)
        assertFalse(skeleton.empty(), "Skeleton should not be empty for the image")

        // Detect connection nodes
        val nodes = streetDetection.detectConnectionNodes(skeleton)

        // Should detect one connection nodes (junctions/branches)
        assertEquals(1, nodes.size)

        skeleton.release()
    }

    /**
     * Test that the detectConnectionNodes function detects the correct number of connection nodes for Road4.
     */
    @Test
    fun testDetectConnectionNodesWithRoadImageWithRoundabout() {
        // Create skeleton from road image
        val skeleton = streetDetection.createSkeletonStreetImage(roadImage4)
        assertFalse(skeleton.empty(), "Skeleton should not be empty for the image")

        // Detect connection nodes
        val nodes = streetDetection.detectConnectionNodes(skeleton)

        // Should detect 3 connection nodes (junctions/branches)
        assertEquals(3, nodes.size)

        skeleton.release()
    }

    /**
     * Test that the detectConnectionNodes function detects the correct number of connection nodes for Road.
     */
    @Test
    fun testDetectConnectionNodesRoad() {
        val skeleton = streetDetection.createSkeletonStreetImage(roadImage)
        val conNodes = streetDetection.detectConnectionNodes(skeleton)
        assertEquals(0, conNodes.size)
    }

    /**
     * Test that the detectConnectionNodes function detects the correct number of connection nodes for Road2.
     */
    @Test
    fun testDetectConnectionNodesRoad2() {
        val skeleton = streetDetection.createSkeletonStreetImage(roadImage2)
        val conNodes = streetDetection.detectConnectionNodes(skeleton)
        assertEquals(0, conNodes.size)
    }
    
    /**
     * Test that the detectConnectionNodes function detects the correct number of connection nodes for Road3.
     */
    @Test
    fun testDetectConnectionNodesRoad3() {
        val skeleton = streetDetection.createSkeletonStreetImage(roadImage3)
        val conNodes = streetDetection.detectConnectionNodes(skeleton)
        assertEquals(1, conNodes.size)
    }

    /**
     * Test that the detectConnectionNodes function detects the correct number of connection nodes for Road4.
     */
    @Test
    fun testDetectConnectionNodesRoad4() {
        val skeleton = streetDetection.createSkeletonStreetImage(roadImage4)
        val conNodes = streetDetection.detectConnectionNodes(skeleton)
        assertEquals(3, conNodes.size)
    }

    /**
     * Test that the detectConnectionNodes function initializes the elevation of all nodes to 0.
     */
    @Test
    fun testDetectConnectionNodesElevationInitialization() {
        val skeleton = streetDetection.createSkeletonStreetImage(roadImage)
        val nodes = streetDetection.detectConnectionNodes(skeleton)

        // Verify all nodes have elevation initialized to 0
        for (node in nodes) {
            assertEquals(0, node.elevation, "All connection nodes should have elevation initialized to 0")
        }

        skeleton.release()
    }

    /**
     * Test that the buildStreetGraph function counts segments correctly for Road4.
     */
    @Test
    fun testRoad4BuildStreetGraphSegmentCount(){
        val startNode = SourceNode(Point(133.0, 502.0), 0, 0.degrees, emptyList())
        val endNode = TargetNode(Point(1335.0, 897.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))

        val startNode2 = SourceNode(Point(133.0, 502.0), 0, 0.degrees, emptyList())
        val endNode2 = TargetNode(Point(1279.0, 86.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))

        val s1 = Street(startNode, endNode, emptyList())
        val s2 = Street(startNode2, endNode2, emptyList())

        val segments = streetDetection.buildStreetGraph(roadImage4, mutableListOf(s1, s2))

        assertEquals(6, uniquePhysicalSegmentCount(segments))
    }

    /** Builds one fresh Road4 source/target request pair (roundabout with two exits). */
    private fun road4RequestStreets(): MutableList<Street> {
        val startNode = SourceNode(Point(133.0, 502.0), 0, 0.degrees, emptyList())
        val endNode = TargetNode(Point(1335.0, 897.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val startNode2 = SourceNode(Point(133.0, 502.0), 0, 0.degrees, emptyList())
        val endNode2 = TargetNode(Point(1279.0, 86.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        return mutableListOf(Street(startNode, endNode, emptyList()), Street(startNode2, endNode2, emptyList()))
    }

    /**
     * AC: streets are one-way - no physical segment may ever end up represented by a Street in both
     * directions. Road4's roundabout used to be exactly the shape (junction-to-junction segments,
     * touching no source or target directly) that produced both directions before this fix.
     */
    @Test
    fun testBuildStreetGraphNeverProducesBothDirectionsForAPhysicalSegment() {
        val segments = streetDetection.buildStreetGraph(roadImage4, road4RequestStreets())

        assertEquals(
            uniquePhysicalSegmentCount(segments),
            segments.size,
            "Every physical segment must produce exactly one directed Street, never both directions"
        )
    }

    /**
     * AC: direction assignment must be reproducible - re-detecting the same physical street (e.g. a
     * repeated camera capture) must always yield the same direction.
     */
    @Test
    fun testBuildStreetGraphDirectionsAreReproducibleAcrossRepeatedDetections() {
        val first = streetDetection.buildStreetGraph(roadImage4, road4RequestStreets())
        val second = streetDetection.buildStreetGraph(roadImage4, road4RequestStreets())

        /** Directed-edge fingerprint of [streets], independent of Node object instances. */
        fun directionSignature(streets: List<Street>) = streets
            .map { "${positionKey(it.startNode.position)}->${positionKey(it.endNode.position)}" }
            .toSet()

        assertEquals(
            directionSignature(first),
            directionSignature(second),
            "Re-running detection on the same physical image must assign the same street directions"
        )
    }

    /**
     * Regression (2026-07-13: no vehicles spawned although the preview showed a correct network,
     * fixed only by reloading the level): preview streets are created on every capture and were
     * bound to the level's own source/target node instances - and [domain.Street]'s constructor
     * registers each street on its endpoint nodes, which preview streets never leave again. The
     * polluted adjacency graph made [domain.LevelValidator] report a COMPLETE network before any
     * detection was committed, so the auto-start countdown launched a simulation on an empty
     * street network where no vehicle could ever spawn. Previews must leave the level's nodes
     * completely untouched.
     */
    @Test
    fun testToPreviewStreetsLeavesLevelNodeAdjacencyUntouched() {
        val level = LevelSerializer.fromJsonString(
            Files.readString(Path.of("res/level/level4.json")),
            "res/level/level4.json"
        )

        val pending = streetDetection.detectStreetGraph(level, roadImage5)
        repeat(3) {
            assertTrue(
                streetDetection.toPreviewStreets(pending).isNotEmpty(),
                "Road5 should produce preview streets for level4"
            )
        }

        for (node in level.nodes) {
            assertTrue(
                node.outgoingStreets.isEmpty() && node.incomingStreets.isEmpty(),
                "preview streets must never attach to the level's node at ${node.position} " +
                    "(outgoing=${node.outgoingStreets.size}, incoming=${node.incomingStreets.size})"
            )
        }
    }

    /**
     * Source rule (issue reported 2026-07-13: streets leaving a source were sometimes oriented
     * *toward* it): across the road fixtures, no detected street may ever point into a source node
     * or out of a target node. Guards the proximity-based endpoint locking plus the final
     * enforcement pass in [imageprocessing.StreetDetection]'s direction resolution - a junction
     * detected within the node-merge radius of a source used to get plain rule-chain directions
     * before the commit bound exactly those streets to the source.
     */
    @Test
    fun testDetectedStreetsAlwaysLeaveSourcesAndEnterTargets() {
        /** Fails when any street of [streets] drives into a source or out of a target. */
        fun assertSourceTargetOrientation(streets: List<Street>, fixtureName: String) {
            for (street in streets) {
                assertFalse(
                    street.endNode is SourceNode,
                    "$fixtureName: street ${positionKey(street.startNode.position)} -> " +
                        "${positionKey(street.endNode.position)} must not point into a source"
                )
                assertFalse(
                    street.startNode is TargetNode,
                    "$fixtureName: street ${positionKey(street.startNode.position)} -> " +
                        "${positionKey(street.endNode.position)} must not point out of a target"
                )
            }
        }

        val road3Requests = mutableListOf(
            Street(
                SourceNode(Point(80.0, 520.0), 0, 0.degrees, emptyList()),
                TargetNode(Point(1653.0, 508.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0))),
                emptyList()
            ),
            Street(
                SourceNode(Point(760.0, 5.0), 0, 0.degrees, emptyList()),
                TargetNode(Point(1007.0, 968.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0))),
                emptyList()
            )
        )
        assertSourceTargetOrientation(streetDetection.buildStreetGraph(roadImage3, road3Requests), "Road3")
        assertSourceTargetOrientation(streetDetection.buildStreetGraph(roadImage4, road4RequestStreets()), "Road4")

        // Committed level: after toLevelNode has merged near-source junctions into the level's own
        // source/target nodes, the same rule must hold on the level's street network.
        val level = LevelSerializer.fromJsonString(
            Files.readString(Path.of("res/level/level4.json")),
            "res/level/level4.json"
        )
        streetDetection.processLevelImage(level, roadImage5)
        assertSourceTargetOrientation(level.streets, "Road5/level4 (committed)")
    }

    /**
     * Streets are strictly one-way, and Road5/level4 is the network where a purely per-segment
     * direction heuristic used to cut the Tuerkis configuration off: the physical (undirected)
     * network contains a Tuerkis path, but orienting each segment by "nearest source" alone turned
     * one of its connecting segments the wrong way. Since the demand-flow pass
     * ([domain.StreetDirectionResolver.orientByDemandFlows]) orients undecided segments along an
     * actual route for every (source, color) demand, every configuration of this level must be
     * reachable - this test guards that guarantee against regressing to per-segment-only resolution.
     */
    @Test
    fun testRoad5Level4EveryConfigurationReachableThroughDemandFlowDirections() {
        val level = LevelSerializer.fromJsonString(
            Files.readString(Path.of("res/level/level4.json")),
            "res/level/level4.json"
        )

        val result = streetDetection.processLevelImage(level, roadImage5)
        assertTrue(result.streets.isNotEmpty(), "Road5 should produce streets for level4")
        assertTrue(level.streets.isNotEmpty(), "processLevelImage should add streets to level4")

        assertSourceConfigurationsMatchExpectedReachability(level, expectedUnreachableColors = emptySet())
    }

    /** Asserts every source configuration's reachability matches [expectedUnreachableColors]. */
    private fun assertSourceConfigurationsMatchExpectedReachability(level: Level, expectedUnreachableColors: Set<String>) {
        val sources = level.nodes.filterIsInstance<SourceNode>()
        val targets = level.nodes.filterIsInstance<TargetNode>()

        for (source in sources) {
            for (configuration in source.configurations) {
                val matchingTargets = targets.filter { it.color.name == configuration.color.name }
                assertTrue(matchingTargets.isNotEmpty(), "Level4 should contain a ${configuration.color.name} target")

                val reachable = matchingTargets.any { target -> hasDirectedPath(source, target) }
                if (configuration.color.name in expectedUnreachableColors) {
                    assertFalse(
                        reachable,
                        "${configuration.color.name} was expected to stay unreachable under strict one-way streets"
                    )
                } else {
                    assertTrue(
                        reachable,
                        "Source ${source.position} should still reach a ${configuration.color.name} target"
                    )
                }
            }
        }
    }

    /**
     * Test that the buildStreetGraph function counts segments correctly for Road3.
     */
    @Test
    fun testRoad3BuildStreetGraphSegmentCount(){
        val startNode = SourceNode(Point(80.0, 520.0), 0, 0.degrees, emptyList())
        val endNode = TargetNode(Point(1653.0, 508.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))

        val startNode2 = SourceNode(Point(760.0, 5.0), 0, 0.degrees, emptyList())
        val endNode2 = TargetNode(Point(1007.0, 968.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))

        val s1 = Street(startNode, endNode, emptyList())
        val s2 = Street(startNode2, endNode2, emptyList())

        val segments = streetDetection.buildStreetGraph(roadImage3, mutableListOf(s1, s2))

        assertEquals(4, uniquePhysicalSegmentCount(segments))
    }

    /**
     * Test that all segments in the street graph have non-empty paths.
     */
    @Test
    fun testAllSegmentsHaveNonEmptyPaths() {
        val startNode = SourceNode(Point(133.0, 502.0), 0, 0.degrees, emptyList())
        val endNode = TargetNode(Point(1335.0, 897.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val segments = streetDetection.buildStreetGraph(
            roadImage4, mutableListOf(Street(startNode, endNode, emptyList()))
        )

        for (segment in segments) {
            assertTrue(
                segment.path.size >= 2,
                "Every segment should have at least 2 path points: " +
                    "${segment.startNode.position} → ${segment.endNode.position} has ${segment.path.size}"
            )
        }
    }

    /**
     * Test that the buildStreetGraphWithElevations function splits the blue bridge into level one sections.
     */
    @Test
    fun testBuildStreetGraphWithElevationsSplitsBlueBridgeIntoLevelOneSections() {
        val image = Mat(100, 240, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        rectangle(image, OpenCVPoint(95, 34), OpenCVPoint(145, 66), Scalar(255.0, 0.0, 0.0, 0.0), -1, 8, 0)
        line(image, OpenCVPoint(20, 50), OpenCVPoint(220, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)

        val source = bridgeTestSource(Point(20.0, 50.0))
        val target = TargetNode(Point(220.0, 50.0), 0, 180.degrees, bridgeTestColor)
        val result = streetDetection.buildStreetGraphWithElevations(
            image,
            mutableListOf(Street(source, target, emptyList()))
        )

        assertTrue(result.invalidElevationTransitions.isEmpty(), "Blue level-1 bridges should be valid from level 0")
        assertTrue(result.streets.any { it.startNode.elevation == 0 && it.endNode.elevation == 1 })
        assertTrue(result.streets.any { it.startNode.elevation == 1 && it.endNode.elevation == 1 })
        assertTrue(result.streets.any { it.startNode.elevation == 1 && it.endNode.elevation == 0 })

        image.release()
    }

    /**
     * AC: physical bridge pieces carry no red lane marking of their own (only blue, or blue+green
     * for level 2), so the red skeleton has a genuine gap the width of the bridge. This must be
     * reconnected (via [StreetDetection.buildStreetGraphWithElevations]'s bridge-color-aware gap
     * bridging) so the network stays connected across a real bridge, matching the physical model
     * documented in res/3dprinting/README_3D_printing.md.
     */
    @Test
    fun testBuildStreetGraphWithElevationsReconnectsBlueBridgeAcrossAGapWithNoRedLaneMarking() {
        val image = Mat(100, 260, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        // Two red road stubs with a genuine gap between them - only the blue bridge rail color
        // fills the gap, exactly as on the physical table.
        line(image, OpenCVPoint(20, 50), OpenCVPoint(90, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)
        line(image, OpenCVPoint(150, 50), OpenCVPoint(240, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)
        rectangle(image, OpenCVPoint(85, 34), OpenCVPoint(155, 66), Scalar(255.0, 0.0, 0.0, 0.0), -1, 8, 0)

        val source = bridgeTestSource(Point(20.0, 50.0))
        val target = TargetNode(Point(240.0, 50.0), 0, 180.degrees, bridgeTestColor)
        val result = streetDetection.buildStreetGraphWithElevations(
            image,
            mutableListOf(Street(source, target, emptyList()))
        )

        assertTrue(
            result.invalidElevationTransitions.isEmpty(),
            "A single-level bridge without a red lane marking must still be a valid ramp"
        )
        assertTrue(
            result.streets.any { it.startNode.elevation == 0 && it.endNode.elevation == 1 },
            "Missing the ground-to-bridge ramp across the un-marked gap"
        )
        assertTrue(
            result.streets.any { it.startNode.elevation == 1 && it.endNode.elevation == 0 },
            "Missing the bridge-to-ground ramp across the un-marked gap"
        )
        assertTrue(
            isReachableViaStreets(result.streets, source.position, target.position),
            "The network must stay connected across the un-marked bridge gap"
        )

        image.release()
    }

    /**
     * AC: level-1 and level-2 bridges are correctly distinguished, and a level-2 deck is only ever
     * reached via a one-level-at-a-time ramp (0 -> 1 -> 2 -> 1 -> 0), even though - matching the
     * physical model - none of the bridge tiles carry a red lane marking, so the entire bridge span
     * is one un-marked gap that must be reconnected and then correctly split by sampled color.
     */
    @Test
    fun testBuildStreetGraphWithElevationsReconnectsLevelTwoBridgeAcrossAGapViaLevelOneRamps() {
        val image = Mat(100, 300, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        // Two red road stubs with a wide gap, filled by an un-marked bridge: blue ramp approach,
        // blue+green level-2 deck at its core, blue ramp departure - no red anywhere in between.
        line(image, OpenCVPoint(20, 50), OpenCVPoint(80, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)
        line(image, OpenCVPoint(220, 50), OpenCVPoint(280, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)
        rectangle(image, OpenCVPoint(75, 34), OpenCVPoint(225, 66), Scalar(255.0, 0.0, 0.0, 0.0), -1, 8, 0)
        rectangle(image, OpenCVPoint(115, 34), OpenCVPoint(185, 66), Scalar(0.0, 255.0, 0.0, 0.0), -1, 8, 0)

        val source = bridgeTestSource(Point(20.0, 50.0))
        val target = TargetNode(Point(280.0, 50.0), 0, 180.degrees, bridgeTestColor)
        val result = streetDetection.buildStreetGraphWithElevations(
            image,
            mutableListOf(Street(source, target, emptyList()))
        )

        assertTrue(
            result.invalidElevationTransitions.isEmpty(),
            "A ramp that climbs one level at a time (0->1->2->1->0) must never be reported as invalid"
        )
        assertTrue(result.streets.any { it.startNode.elevation == 0 && it.endNode.elevation == 1 }, "Missing the ground-to-ramp transition")
        assertTrue(result.streets.any { it.startNode.elevation == 1 && it.endNode.elevation == 2 }, "Missing the ramp-to-deck transition")
        assertTrue(result.streets.any { it.startNode.elevation == 2 && it.endNode.elevation == 1 }, "Missing the deck-to-ramp transition")
        assertTrue(result.streets.any { it.startNode.elevation == 1 && it.endNode.elevation == 0 }, "Missing the ramp-to-ground transition")
        assertTrue(
            result.streets.none { abs(it.startNode.elevation - it.endNode.elevation) > 1 },
            "No street may skip a level even when the physical bridge itself reaches level 2"
        )
        assertTrue(
            isReachableViaStreets(result.streets, source.position, target.position),
            "The network must stay connected across the un-marked level-2 bridge gap"
        )

        image.release()
    }

    /**
     * AC: level-1 and level-2 bridges are correctly distinguished when the street pieces are laid
     * on top of the bridge, so the red lane marking runs continuously across the whole span (the
     * layout seen on the physical table: white street pieces with their red stripe rest on the
     * bridge arch, blue base visible at both ends, green deck in the middle). The sampled colors
     * must still produce a one-level-at-a-time 0 -> 1 -> 2 -> 1 -> 0 sequence, never a direct 0 -> 2
     * jump, and the network must stay connected.
     */
    @Test
    fun testBuildStreetGraphWithElevationsSplitsLevelTwoBridgeUnderAContinuousRedLaneMarking() {
        val image = Mat(100, 300, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        // Bridge footprint: blue base with the green level-2 deck in the middle, red line on top.
        rectangle(image, OpenCVPoint(75, 34), OpenCVPoint(225, 66), Scalar(255.0, 0.0, 0.0, 0.0), -1, 8, 0)
        rectangle(image, OpenCVPoint(115, 34), OpenCVPoint(185, 66), Scalar(0.0, 255.0, 0.0, 0.0), -1, 8, 0)
        line(image, OpenCVPoint(20, 50), OpenCVPoint(280, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)

        val source = bridgeTestSource(Point(20.0, 50.0))
        val target = TargetNode(Point(280.0, 50.0), 0, 180.degrees, bridgeTestColor)
        val result = streetDetection.buildStreetGraphWithElevations(
            image,
            mutableListOf(Street(source, target, emptyList()))
        )

        assertTrue(
            result.invalidElevationTransitions.isEmpty(),
            "A level-2 bridge under a continuous red marking must climb one level at a time, not report a jump"
        )
        assertTrue(result.streets.any { it.startNode.elevation == 0 && it.endNode.elevation == 1 }, "Missing the ground-to-ramp transition")
        assertTrue(result.streets.any { it.startNode.elevation == 1 && it.endNode.elevation == 2 }, "Missing the ramp-to-deck transition")
        assertTrue(result.streets.any { it.startNode.elevation == 2 && it.endNode.elevation == 1 }, "Missing the deck-to-ramp transition")
        assertTrue(result.streets.any { it.startNode.elevation == 1 && it.endNode.elevation == 0 }, "Missing the ramp-to-ground transition")
        assertTrue(
            result.streets.none { abs(it.startNode.elevation - it.endNode.elevation) > 1 },
            "No street may skip a level even though the red marking is continuous across the bridge"
        )
        assertTrue(
            isReachableViaStreets(result.streets, source.position, target.position),
            "The network must stay connected across the marked level-2 bridge"
        )

        image.release()
    }

    /**
     * AC: ramps to bridges are recognized as transitions between adjacent levels even when the
     * blue base of a level-2 bridge is only a short strip at each end (as seen from the camera,
     * the green deck of the real bridge reaches almost to the bridge ends). The green mask lying
     * within sampling range of the ramp zone must not override the blue ramp directly beneath
     * the lane, or the whole bridge is rejected as a direct 0 -> 2 jump.
     */
    @Test
    fun testBuildStreetGraphWithElevationsPrefersTheNearerRampColorOverAFartherDeckColor() {
        val image = Mat(100, 300, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        // Narrow blue-only strips (15px) at both bridge ends, green deck for the rest of the span.
        rectangle(image, OpenCVPoint(75, 34), OpenCVPoint(225, 66), Scalar(255.0, 0.0, 0.0, 0.0), -1, 8, 0)
        rectangle(image, OpenCVPoint(90, 34), OpenCVPoint(210, 66), Scalar(0.0, 255.0, 0.0, 0.0), -1, 8, 0)
        line(image, OpenCVPoint(20, 50), OpenCVPoint(280, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)

        val source = bridgeTestSource(Point(20.0, 50.0))
        val target = TargetNode(Point(280.0, 50.0), 0, 180.degrees, bridgeTestColor)
        val result = streetDetection.buildStreetGraphWithElevations(
            image,
            mutableListOf(Street(source, target, emptyList()))
        )

        assertTrue(
            result.invalidElevationTransitions.isEmpty(),
            "Short blue ramp strips must still be classified as level 1, not skipped as a 0-to-2 jump"
        )
        assertTrue(
            result.streets.none { abs(it.startNode.elevation - it.endNode.elevation) > 1 },
            "No street may skip a level when the bridge has short but present ramp strips"
        )
        assertTrue(
            isReachableViaStreets(result.streets, source.position, target.position),
            "The network must stay connected across the level-2 bridge with short ramps"
        )

        image.release()
    }

    /**
     * AC: ramps are recognized as transitions between adjacent levels even when the blue ramp
     * color is only visible beside the lane (offset side rails, as on the real table) and the
     * green deck reaches the bridge ends under the lane itself. The sampled sequence then jumps
     * 0 -> 2 directly, but blue is provably present at the transition, so the ramp level must be
     * inserted instead of rejecting the bridge.
     */
    @Test
    fun testBuildStreetGraphWithElevationsInsertsRampLevelWhenBlueIsOnlyBesideTheLane() {
        val image = Mat(100, 300, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        // Green deck directly under the lane for the whole span; blue only as offset blobs above
        // the lane at both bridge ends, too far to ever be the nearest color at a lane point.
        rectangle(image, OpenCVPoint(75, 40), OpenCVPoint(225, 60), Scalar(0.0, 255.0, 0.0, 0.0), -1, 8, 0)
        rectangle(image, OpenCVPoint(70, 25), OpenCVPoint(95, 35), Scalar(255.0, 0.0, 0.0, 0.0), -1, 8, 0)
        rectangle(image, OpenCVPoint(205, 25), OpenCVPoint(230, 35), Scalar(255.0, 0.0, 0.0, 0.0), -1, 8, 0)
        line(image, OpenCVPoint(20, 50), OpenCVPoint(280, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)

        val source = bridgeTestSource(Point(20.0, 50.0))
        val target = TargetNode(Point(280.0, 50.0), 0, 180.degrees, bridgeTestColor)
        val result = streetDetection.buildStreetGraphWithElevations(
            image,
            mutableListOf(Street(source, target, emptyList()))
        )

        assertTrue(
            result.invalidElevationTransitions.isEmpty(),
            "A 0-to-2 jump with blue ramp color present at the transition must be repaired, not rejected"
        )
        assertTrue(
            result.streets.none { abs(it.startNode.elevation - it.endNode.elevation) > 1 },
            "The repaired bridge must climb one level at a time"
        )
        assertTrue(
            isReachableViaStreets(result.streets, source.position, target.position),
            "The network must stay connected across the repaired level-2 bridge"
        )

        image.release()
    }

    /**
     * AC: a bridge that crosses over an unrelated level-0 junction (e.g. an X-crossing, as in
     * Road5.png) must not contaminate that junction's own streets with elevation, and the ground
     * streets must not be spliced into the bridge merely because it passes nearby.
     */
    @Test
    fun testBuildStreetGraphWithElevationsDoesNotConnectGroundStreetsToACrossingBridge() {
        val image = Mat(220, 280, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))

        // Ground-level X crossing, fully on elevation 0, unrelated to the bridge below.
        line(image, OpenCVPoint(20, 110), OpenCVPoint(260, 110), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)
        line(image, OpenCVPoint(140, 10), OpenCVPoint(140, 210), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)

        // A separate bridge crossing diagonally close to the ground crossing's center, with a
        // genuine gap (no red) between its two red approach stubs - only the blue rail fills it,
        // mirroring how Road5.png overlays a bridge diagonal on top of an unrelated X-crossing.
        line(image, OpenCVPoint(70, 190), OpenCVPoint(100, 160), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)
        line(image, OpenCVPoint(180, 60), OpenCVPoint(210, 30), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)
        rectangle(image, OpenCVPoint(95, 85), OpenCVPoint(185, 135), Scalar(255.0, 0.0, 0.0, 0.0), -1, 8, 0)

        val groundHorizontalSource = SourceNode(Point(20.0, 110.0), 0, 0.degrees, emptyList())
        val groundHorizontalTarget = TargetNode(Point(260.0, 110.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val groundVerticalSource = SourceNode(Point(140.0, 10.0), 0, 0.degrees, emptyList())
        val groundVerticalTarget = TargetNode(Point(140.0, 210.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val bridgeSource = SourceNode(Point(70.0, 190.0), 0, 0.degrees, emptyList())
        val bridgeTarget = TargetNode(Point(210.0, 30.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))

        val result = streetDetection.buildStreetGraphWithElevations(
            image,
            mutableListOf(
                Street(groundHorizontalSource, groundHorizontalTarget, emptyList()),
                Street(groundVerticalSource, groundVerticalTarget, emptyList()),
                Street(bridgeSource, bridgeTarget, emptyList())
            )
        )

        /** True when [street] starts or ends at one of the requested ground X-crossing endpoints. */
        fun touchesRequestedGroundEndpoint(street: Street): Boolean {
            val groundPositions = setOf(
                groundHorizontalSource.position,
                groundHorizontalTarget.position,
                groundVerticalSource.position,
                groundVerticalTarget.position
            )
            return street.startNode.position in groundPositions || street.endNode.position in groundPositions
        }

        val groundStreets = result.streets.filter(::touchesRequestedGroundEndpoint)
        assertTrue(groundStreets.isNotEmpty(), "Sanity: the ground X-crossing must still produce streets")
        assertTrue(
            groundStreets.all { it.startNode.elevation == 0 && it.endNode.elevation == 0 },
            "Ground streets touching the crossing must stay on elevation 0, not inherit the crossing bridge's elevation"
        )
        assertTrue(
            result.streets.any { maxOf(it.startNode.elevation, it.endNode.elevation) > 0 },
            "Sanity: the crossing bridge itself must still be detected as elevated"
        )

        image.release()
    }

    /**
     * AC: robustness - a plain, uncolored gap between two road stubs (an incomplete network, not a
     * bridge) must not be silently auto-connected. Without this guard, the larger bridge-gap search
     * radius could turn every unfinished construction-phase network into a falsely "complete" one.
     */
    @Test
    fun testBuildStreetGraphWithElevationsDoesNotBridgeAPlainGapWithoutBridgeColorCoverage() {
        val image = Mat(100, 340, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        // The gap is wider than the plain dashed-road gap-bridging distance (120px) so this test
        // exercises the color-gated elevated-bridge bridging specifically, not the unrelated
        // short-gap dash bridging.
        line(image, OpenCVPoint(20, 50), OpenCVPoint(90, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)
        line(image, OpenCVPoint(270, 50), OpenCVPoint(320, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)

        val source = SourceNode(Point(20.0, 50.0), 0, 0.degrees, emptyList())
        val target = TargetNode(Point(320.0, 50.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val result = streetDetection.buildStreetGraphWithElevations(
            image,
            mutableListOf(Street(source, target, emptyList()))
        )

        assertFalse(
            isReachableViaStreets(result.streets, source.position, target.position),
            "An uncolored gap is an incomplete network, not a bridge, and must stay disconnected"
        )

        image.release()
    }

    /** Position-based BFS reachability check over a detached street list, independent of node identity. */
    private fun isReachableViaStreets(streets: List<Street>, from: Point, to: Point): Boolean {
        if (from.distanceTo(to) < 1.0) return true
        val visited = mutableSetOf(positionKey(from))
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (street in streets) {
                val (from2, to2) = street.startNode.position to street.endNode.position
                val next = when {
                    from2.distanceTo(current) < 1.0 -> to2
                    to2.distanceTo(current) < 1.0 -> from2
                    else -> null
                } ?: continue
                if (visited.add(positionKey(next))) queue.add(next)
            }
        }
        return visited.contains(positionKey(to))
    }

    /**
     * Test that the buildStreetGraphWithElevations function simplifies saved street paths.
     */
    @Test
    fun testBuildStreetGraphSimplifiesSavedStreetPaths() {
        val image = Mat(100, 240, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        line(image, OpenCVPoint(20, 50), OpenCVPoint(220, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)

        val source = SourceNode(Point(20.0, 50.0), 0, 0.degrees, emptyList())
        val target = TargetNode(Point(220.0, 50.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val result = streetDetection.buildStreetGraphWithElevations(
            image,
            mutableListOf(Street(source, target, emptyList()))
        )

        assertEquals(1, result.streets.size)
        assertTrue(
            result.streets.single().path.size <= 3,
            "Straight street paths should be simplified before saving"
        )

        image.release()
    }

    /**
     * Test that the buildStreetGraphWithElevations function reports direct level zero to two transitions
     * without illegal streets.
     */
    @Test
    fun testBuildStreetGraphWithElevationsReportsDirectLevelZeroToTwoTransitionWithoutIllegalStreets() {
        val image = Mat(100, 240, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        rectangle(image, OpenCVPoint(95, 34), OpenCVPoint(145, 66), Scalar(0.0, 255.0, 0.0, 0.0), -1, 8, 0)
        line(image, OpenCVPoint(20, 50), OpenCVPoint(220, 50), Scalar(0.0, 0.0, 255.0, 0.0), 5, 8, 0)

        val source = SourceNode(Point(20.0, 50.0), 0, 0.degrees, emptyList())
        val target = TargetNode(Point(220.0, 50.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val result = streetDetection.buildStreetGraphWithElevations(
            image,
            mutableListOf(Street(source, target, emptyList()))
        )

        assertFalse(result.invalidElevationTransitions.isEmpty(), "Direct level 0 to level 2 transitions may be reported")
        assertTrue(
            result.issues.isNotEmpty() &&
                result.issues.all { it.kind == StreetDetectionIssueKind.INVALID_ELEVATION_TRANSITION },
            "rejected level jumps are surfaced as invalid-elevation-transition issues so the " +
                "construction phase shows why the bridge produced no street (issues: ${result.issues})"
        )
        assertTrue(
            result.streets.none { abs(it.startNode.elevation - it.endNode.elevation) > 1 },
            "Reported 0-to-2 transitions should not materialize illegal Street objects"
        )

        image.release()
    }

    /**
     * AC: "Streets are detected completely and not shifted" + "generated level matches the table image".
     *
     * A bright table is placed off-center inside a dark frame so cropTable must actually crop and then
     * rescale back to the desk frame. The drawn road keeps its relative position, proving the crop
     * offset is compensated (no shift) and the skeleton is normalized to the fixed desk frame.
     */
    @Test
    fun testCropTableNormalizesOffsetTableToDeskFrameWithoutShiftingStreet() {
        // Dark frame with a bright, off-center "table" rectangle and a horizontal red road on it.
        val image = Mat(GameAreaScale.DESK_H_PX, GameAreaScale.DESK_W_PX, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        rectangle(image, OpenCVPoint(100, 50), OpenCVPoint(1700, 950), Scalar(255.0, 255.0, 255.0, 0.0), -1, 8, 0)
        line(image, OpenCVPoint(300, 500), OpenCVPoint(1500, 500), Scalar(0.0, 0.0, 255.0, 0.0), 10, 8, 0)

        val skeleton = streetDetection.createSkeletonStreetImage(image)

        assertEquals(GameAreaScale.DESK_W_PX, skeleton.cols(), "Skeleton must be normalized to desk frame width")
        assertEquals(GameAreaScale.DESK_H_PX, skeleton.rows(), "Skeleton must be normalized to desk frame height")

        // The table (100,50)-(1700,950) rescaled to the desk frame maps the road ends (300,500)/(1500,500)
        // to roughly x≈225 and x≈1575 at y≈500. Tolerances absorb thinning, rounding and the 180° flip.
        val (left, right) = foregroundExtremes(skeleton)
        assertTrue(abs(left.x - 225.0) < 50.0, "Left road end should land near x≈225 (no shift), got ${left.x}")
        assertTrue(abs(right.x - 1575.0) < 50.0, "Right road end should land near x≈1575 (no shift), got ${right.x}")
        assertTrue(abs(left.y - 500.0) < 50.0, "Left road end should stay near y≈500, got ${left.y}")
        assertTrue(abs(right.y - 500.0) < 50.0, "Right road end should stay near y≈500, got ${right.y}")

        // AC: "no additional street points or connections are created" – a straight road has no junctions.
        assertEquals(0, streetDetection.detectConnectionNodes(skeleton).size, "Straight road must not create junctions")

        skeleton.release()
        image.release()
    }

    /**
     * AC: "detected streets can be saved/loaded" + "streets, sources, sinks and connections are taken
     * consistently into a level". The detected network must survive a serialize → deserialize round-trip.
     */
    @Test
    fun testProcessLevelImageDetectedNetworkSurvivesSaveLoad() {
        val level = LevelSerializer.fromJsonString(
            Files.readString(Path.of("res/level/level4.json")),
            "res/level/level4.json"
        )

        val result = streetDetection.processLevelImage(level, roadImage5)
        assertTrue(result.streets.isNotEmpty(), "Detection should add streets before the round-trip")

        val streetCount = level.streets.size
        val sourceCount = level.nodes.filterIsInstance<SourceNode>().size
        val targetCount = level.nodes.filterIsInstance<TargetNode>().size

        val reloaded = LevelSerializer.fromJsonString(LevelSerializer.toJsonString(level), "roundtrip")

        assertEquals(streetCount, reloaded.streets.size, "Detected streets must survive save/load")
        assertEquals(sourceCount, reloaded.nodes.filterIsInstance<SourceNode>().size, "Sources must survive save/load")
        assertEquals(targetCount, reloaded.nodes.filterIsInstance<TargetNode>().size, "Sinks must survive save/load")
        assertTrue(reloaded.streets.all { it.path.size >= 2 }, "Reloaded streets must keep their polylines")
    }

    /**
     * AC: "after successful detection the simulation can be started with the generated level".
     * Streets are strictly one-way now, so Road5/level4 no longer validates completely cleanly (see
     * testRoad5Level4ReachableColorsWorkAndUnreachableColorReportsOneWayHint) — any remaining
     * validation error must still be one of the already-understood, clearly-explained one-way
     * limitations rather than an unexplained failure. The simulation must still start and spawn
     * traffic on the streets that do have a valid direction.
     */
    @Test
    fun testProcessLevelImageProducesValidStartableSimulation() {
        val level = LevelSerializer.fromJsonString(
            Files.readString(Path.of("res/level/level4.json")),
            "res/level/level4.json"
        )

        streetDetection.processLevelImage(level, roadImage5)

        val errors = level.validate()
        val knownOneWayLimitations = errors.filter {
            it.message.contains("one-way") || it.message.contains("no outgoing streets")
        }
        assertEquals(
            errors.size,
            knownOneWayLimitations.size,
            "Detected level should only report already-understood one-way limitations, but got: ${errors.map { it.message }}"
        )

        val simulation = Simulation(level)
        simulation.runPlayback(60.0)
        assertTrue(
            simulation.statistics().generatedVehicles > 0,
            "Simulation should start and spawn vehicles on the detected level"
        )
    }

    /**
     * AC: "generated level matches the table image" / "streets not shifted". A camera-resolution frame
     * is perspective-warped from the play-area quad onto the desk frame, so a road drawn along the
     * quad's horizontal centerline must span the full desk width at mid-height.
     */
    @Test
    fun testCameraFrameIsPerspectiveWarpedOntoDeskFrame() {
        val image = Mat(CameraManager.CAMERA_HEIGHT, CameraManager.CAMERA_WIDTH, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        // Red road across the full raw frame at mid-height. Whatever section falls inside the play-area
        // quad must, after the perspective warp, span the full desk width (independent of exact corners).
        line(image, OpenCVPoint(0, 540), OpenCVPoint(CameraManager.CAMERA_WIDTH, 540), Scalar(0.0, 0.0, 255.0, 0.0), 14, 8, 0)

        val skeleton = streetDetection.createSkeletonStreetImage(image)

        assertEquals(GameAreaScale.DESK_W_PX, skeleton.cols(), "Warped skeleton must use the desk frame width")
        assertEquals(GameAreaScale.DESK_H_PX, skeleton.rows(), "Warped skeleton must use the desk frame height")

        val (left, right) = foregroundExtremes(skeleton)
        assertTrue(left.x < 150.0, "Warped road should start near the left desk edge, got ${left.x}")
        assertTrue(right.x > GameAreaScale.DESK_W_PX - 150.0, "Warped road should reach the right desk edge, got ${right.x}")
        assertTrue(left.y in 250.0..750.0, "Warped mid-height road should stay around the vertical middle, got ${left.y}")
        assertTrue(right.y in 250.0..750.0, "Warped mid-height road should stay around the vertical middle, got ${right.y}")

        skeleton.release()
        image.release()
    }

    /**
     * AC: repeated detection must stay consistent. Running processLevelImage twice on the same level
     * must not accumulate streets or connection nodes from earlier captures.
     */
    @Test
    fun testProcessLevelImageDoesNotAccumulateAcrossRepeatedCaptures() {
        val level = LevelSerializer.fromJsonString(
            Files.readString(Path.of("res/level/level4.json")),
            "res/level/level4.json"
        )

        streetDetection.processLevelImage(level, roadImage5)
        val streetsAfterFirst = level.streets.size
        val nodesAfterFirst = level.nodes.size
        assertTrue(streetsAfterFirst > 0, "Sanity: first detection should produce streets")

        streetDetection.processLevelImage(level, roadImage5)

        assertEquals(streetsAfterFirst, level.streets.size, "Repeated detection must not accumulate streets")
        assertEquals(nodesAfterFirst, level.nodes.size, "Repeated detection must not accumulate connection nodes")
    }

    /**
     * AC: "streets are detected completely". A dashed road whose gap is too wide for the morphological
     * close must still be reconnected by skeleton-gap bridging so a single street is produced.
     */
    @Test
    fun testDashedRoadWithGapWiderThanCloseKernelStillProducesStreet() {
        val image = Mat(200, 600, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        // Two collinear red segments with an ~80px gap (wider than the 60px close kernel can bridge).
        line(image, OpenCVPoint(50, 100), OpenCVPoint(250, 100), Scalar(0.0, 0.0, 255.0, 0.0), 6, 8, 0)
        line(image, OpenCVPoint(330, 100), OpenCVPoint(550, 100), Scalar(0.0, 0.0, 255.0, 0.0), 6, 8, 0)

        val source = SourceNode(Point(50.0, 100.0), 0, 0.degrees, emptyList())
        val target = TargetNode(Point(550.0, 100.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val result = streetDetection.buildStreetGraphWithElevations(
            image,
            mutableListOf(Street(source, target, emptyList()))
        )

        assertEquals(1, result.streets.size, "Bridged dashed road should yield exactly one street")
        assertTrue(result.streets.single().path.size >= 2, "Bridged street must have a usable path")

        image.release()
    }

    /**
     * AC: "streets are detected completely". The far side of a gap is often a corner/junction, not a
     * loose endpoint (e.g. where a road meets a branch). Bridging must still reconnect the endpoint to
     * that skeleton so a reachable path exists.
     */
    @Test
    fun testGapToJunctionIsBridgedSoTargetStaysReachable() {
        val image = Mat(260, 600, CV_8UC3, Scalar(0.0, 0.0, 0.0, 0.0))
        // Left segment ends as a loose endpoint at x≈250; right segment's left end at x≈330 is a T:
        // it continues right to the target AND branches down, so that end is a junction, not an endpoint.
        line(image, OpenCVPoint(50, 100), OpenCVPoint(250, 100), Scalar(0.0, 0.0, 255.0, 0.0), 6, 8, 0)
        line(image, OpenCVPoint(330, 100), OpenCVPoint(550, 100), Scalar(0.0, 0.0, 255.0, 0.0), 6, 8, 0)
        line(image, OpenCVPoint(330, 100), OpenCVPoint(330, 230), Scalar(0.0, 0.0, 255.0, 0.0), 6, 8, 0)

        val source = SourceNode(Point(50.0, 100.0), 0, 0.degrees, emptyList())
        val target = TargetNode(Point(550.0, 100.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val segments = streetDetection.buildStreetGraph(
            image,
            mutableListOf(Street(source, target, emptyList()))
        )

        val path = StreetPathFinder.findDrivingPath(segments, source, target)
        assertTrue(path.isNotEmpty(), "Source must reach the target across the bridged gap-to-junction")

        image.release()
    }

    /** Returns the leftmost and rightmost foreground pixels of a binary skeleton. */
    private fun foregroundExtremes(skeleton: Mat): Pair<Point, Point> {
        val rows = skeleton.rows()
        val cols = skeleton.cols()
        val pixels = ByteArray(rows * cols)
        skeleton.data().get(pixels)

        var left = Point(cols.toDouble(), 0.0)
        var right = Point(-1.0, 0.0)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (pixels[y * cols + x].toInt() and 0xFF == 0) continue
                if (x < left.x) left = Point(x.toDouble(), y.toDouble())
                if (x > right.x) right = Point(x.toDouble(), y.toDouble())
            }
        }
        return left to right
    }

    /**
     * Helper function to count the number of unique physical segments.
     *
     * @param streets The list of streets to count the segments from.
     * @return The number of unique physical segments.
     */
    private fun uniquePhysicalSegmentCount(streets: List<Street>): Int {
        return streets
            .map {
                val start = positionKey(it.startNode.position)
                val end = positionKey(it.endNode.position)
                if (start <= end) "$start|$end" else "$end|$start"
            }
            .toSet()
            .size
    }

    /** Builds a minimal level with one source and one target for image-processing handoff tests. */
    private fun buildSimpleSourceTargetLevel(): Level {
        val source = SourceNode(Point(20.0, 50.0), 0, 0.degrees, emptyList())
        val target = TargetNode(Point(220.0, 50.0), 0, 180.degrees, Color("Rot", RGBA(255, 0, 0)))
        val level = Level("DetectionDiagnostics", 0)
        level.addNode(source)
        level.addNode(target)
        return level
    }

    /** Creates a camera-sized frame with ArUco corner markers matching the rectifier mapping. */
    private fun cameraSizedFrameWithCornerMarkers(missingBottomRight: Boolean): Mat {
        val frameWidth = 1920
        val frameHeight = 1080
        val markerSize = 160
        val markerMargin = 80
        val image = Mat(frameHeight, frameWidth, CV_8UC3, Scalar(255.0, 255.0, 255.0, 0.0))

        placeArucoMarker(image, id = 2, x = markerMargin, y = markerMargin)
        placeArucoMarker(image, id = 1, x = frameWidth - markerMargin - markerSize, y = markerMargin)
        if (!missingBottomRight) {
            placeArucoMarker(
                image,
                id = 0,
                x = frameWidth - markerMargin - markerSize,
                y = frameHeight - markerMargin - markerSize
            )
        }
        placeArucoMarker(image, id = 3, x = markerMargin, y = frameHeight - markerMargin - markerSize)

        return image
    }

    /** Generates one ArUco marker and copies it into [image] at the given top-left position. */
    private fun placeArucoMarker(image: Mat, id: Int, x: Int, y: Int) {
        val dictionary = getPredefinedDictionary(DICT_4X4_50)
        val markerGray = Mat()
        generateImageMarker(dictionary, id, 160, markerGray, 1)

        val markerBgr = Mat()
        cvtColor(markerGray, markerBgr, COLOR_GRAY2BGR)

        val roi = Mat(image, Rect(x, y, 160, 160))
        markerBgr.copyTo(roi)

        roi.release()
        markerBgr.release()
        markerGray.release()
    }

    /** 
     * Helper function to check if there is a directed path from start to target.
     * 
     * @param start The starting node.
     * @param target The target node.
     * @return True if there is a directed path from start to target, false otherwise.
     */
    private fun hasDirectedPath(start: Node, target: Node): Boolean {
        val visited = mutableSetOf<Node>()
        val queue = ArrayDeque<Node>()
        queue.add(start)
        visited.add(start)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current === target) return true

            for (street in current.outgoingStreets) {
                if (street.endNode !in visited) {
                    visited.add(street.endNode)
                    queue.add(street.endNode)
                }
            }
        }

        return false
    }

    /**
     * Helper function to create a unique key for a point.
     * 
     * @param point The point to create a key for.
     * @return The unique key for the point.
     */
    private fun positionKey(point: Point): String = "${point.x.toInt()}:${point.y.toInt()}"
}
