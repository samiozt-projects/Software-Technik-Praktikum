package domaintests

import domain.Color
import domain.ConnectionNode
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.Vehicle
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies vehicle movement on polyline streets.
 *
 * These tests exercise the domain-only position state: current segment index,
 * distance along that segment and the interpolated render position derived from both.
 */
class VehicleAdvanceTest {

    /**
     * Test that the advance function moves the vehicle along the segment.
     */
    @Test
    fun advance_onSingleSegment_movesAlongSegment() {
        // A one-segment street isolates the simplest case: only distanceAlongSegment changes.
        val vehicle = testVehicle()
        setupVehicleOnStreet(vehicle, straightStreet(100.0))

        val reachedEnd = vehicle.advance(25.0)

        assertFalse(reachedEnd, "Vehicle should not have reached end after 25 px on a 100 px segment")
        assertEquals(0, vehicle.currentSegmentIndex)
        assertEquals(25.0, vehicle.distanceAlongSegment)

        val pos = vehicle.calculatePosition()
        assertNotNull(pos)
        assertEquals(25.0f, pos.x.toFloat())
        assertEquals(0.0f, pos.y.toFloat())
    }

    /**
     * Test that the advance function moves the vehicle to the next segment when it overshoots the current segment.
     */
    @Test
    fun advance_overshootsSegment_movesToNextSegment() {
        // This covers the loop in Vehicle.advance that consumes more than one segment.
        val vehicle = testVehicle()
        setupVehicleOnStreet(vehicle, lShapedStreet(50.0))

        // 75 px > 50 (first segment) → leftover 25 on second segment
        val reachedEnd = vehicle.advance(75.0)

        assertFalse(reachedEnd, "Vehicle should not have reached end after 75 of 100 px total")
        assertEquals(1, vehicle.currentSegmentIndex)
        assertEquals(25.0, vehicle.distanceAlongSegment)

        val pos = vehicle.calculatePosition()
        assertNotNull(pos)
        assertEquals(50.0f, pos.x.toFloat())
        assertEquals(25.0f, pos.y.toFloat())
    }

    /**
     * Test that the advance function consumes multiple segments when the vehicle moves across a curved polyline.
     */
    @Test
    fun advance_acrossCurvedPolyline_consumesMultipleSegments() {
        val vehicle = testVehicle()

        val node0 = ConnectionNode(Point(0, 0), 0)
        val node1 = ConnectionNode(Point(100, 100), 0)
        val street = Street(
            node0,
            node1,
            listOf(Point(0, 0), Point(30, 0), Point(30, 40), Point(100, 40), Point(100, 100))
        )

        vehicle.currentStreet = street
        vehicle.currentSegmentIndex = 0
        vehicle.distanceAlongSegment = 0.0

        val reachedEnd = vehicle.advance(90.0)

        assertFalse(reachedEnd, "vehicle should still be inside the polyline after 90 px")
        assertEquals(2, vehicle.currentSegmentIndex)
        assertEquals(20.0, vehicle.distanceAlongSegment)

        val pos = vehicle.calculatePosition()
        assertNotNull(pos)
        assertEquals(50.0f, pos.x.toFloat())
        assertEquals(40.0f, pos.y.toFloat())
    }

    /**
     * Test that the advance function returns true when the vehicle reaches the end of the street.
     */
    @Test
    fun advance_pastEnd_returnsTrue_andClampsAtLastPoint() {
        // Overshooting the whole path should not leave the vehicle beyond the target point.
        val vehicle = testVehicle()
        setupVehicleOnStreet(vehicle, straightStreet(100.0))

        // 150 px > 100 px total → reached end
        val reachedEnd = vehicle.advance(150.0)

        assertTrue(reachedEnd, "Vehicle should signal that the end was reached")

        val pos = vehicle.calculatePosition()
        assertNotNull(pos)
        assertEquals(100.0f, pos.x.toFloat())
        assertEquals(0.0f, pos.y.toFloat())
    }

    /**
     * Creates a test vehicle for testing purposes.
     * 
     * @return The test vehicle.
     */
    private fun testVehicle(): Vehicle {
        val type = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)
        val color = Color("Rot", Colors.RED)
        val cfg = SourceNodeConfiguration(color, 9999.0, 0.0, type)
        val source = SourceNode(Point(0, 0), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val level = Level("test", 1)
        return Vehicle(color, type, source, cfg, level)
    }

    /**
     * Sets up a vehicle on a street with the given path.
     * 
     * @param vehicle The vehicle to set up.
     * @param path The path points for the street.
     * @return The created street.
     */
    private fun setupVehicleOnStreet(vehicle: Vehicle, path: List<Point>): Street {
        val node0 = ConnectionNode(path.first(), 0)
        val node1 = ConnectionNode(path.last(), 0)
        val street = Street(node0, node1, path)
        
        vehicle.currentStreet = street
        vehicle.currentSegmentIndex = 0
        vehicle.distanceAlongSegment = 0.0
        
        return street
    }

    /**
     * Creates a simple straight street from (0,0) to (length,0).
     * 
     * @param length The length of the street.
     * @return The street path.
     */
    private fun straightStreet(length: Double): List<Point> {
        return listOf(Point(0, 0), Point(length, 0))
    }

    /**
     * Creates an L-shaped street with two segments.
     * 
     * @param segmentLength The length of each segment.
     * @return The street path.
     */
    private fun lShapedStreet(segmentLength: Double): List<Point> {
        return listOf(Point(0, 0), Point(segmentLength, 0), Point(segmentLength, segmentLength))
    }
}
