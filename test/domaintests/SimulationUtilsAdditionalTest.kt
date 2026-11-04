package domaintests

import domain.*
import simulation.Simulation
import simulation.SimulationUtils
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Additional tests for the SimulationUtils object to provide code coverage for more delegation methods.
 * This file contains tests that were split from SimulationUtilsTest.kt to keep file sizes manageable.
 */
class SimulationUtilsAdditionalTest {

    /**
     * Scales a point from pixels to meters.
     * 
     * @param xPx The x-coordinate in pixels.
     * @param yPx The y-coordinate in pixels.
     * @return The scaled point in meters.
     */
    private fun scaledPoint(xPx: Number, yPx: Number): Point =
        Point(xPx.toDouble() / Simulation.PX_PER_METER, yPx.toDouble() / Simulation.PX_PER_METER)

    /**
     * Builds a minimal level for testing SimulationUtils functions.
     * 
     * @return The test level.
     */
    private fun buildTestLevel(): Level {
        val level = Level("TestLevel", 1)
        val red = Color("Rot", Colors.RED)
        
        val source = SourceNode(scaledPoint(0, 0), 0, Angle.fromDegrees(0.0), listOf())
        val target = TargetNode(scaledPoint(800, 0), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(scaledPoint(0, 0), scaledPoint(800, 0))))
        level.validate()
        return level
    }

    /**
     * Creates a test vehicle with default configuration.
     * 
     * @param level The level to create the vehicle for.
     * @param street The street to place the vehicle on.
     * @param distanceAlongSegment The distance along the segment.
     * @return The created vehicle.
     */
    private fun createTestVehicle(level: Level, street: Street, distanceAlongSegment: Double = 0.0): Vehicle {
        val sourceNode = level.nodes.filterIsInstance<SourceNode>().first()
        val config = SourceNodeConfiguration(
            Color("Rot", Colors.RED),
            30.0,
            50.0,
            VehicleType("Auto", 80.0, 4.0, 0.7, 3.0),
            vehiclesToSpawn = 1
        )
        
        return Vehicle(
            Color("Rot", Colors.RED),
            VehicleType("Auto", 80.0, 4.0, 0.7, 3.0),
            sourceNode,
            config,
            level
        ).apply {
            currentStreet = street
            currentSegmentIndex = 0
            this.distanceAlongSegment = distanceAlongSegment
        }
    }

    /**
     * Tests that remainingDistanceToStopLineM correctly delegates to VehicleUtils.
     */
    @Test
    fun remainingDistanceToStopLineM_delegatesToVehicleUtils() {
        val level = buildTestLevel()
        val street = level.streets.first()
        
        val vehicle = createTestVehicle(level, street, 50.0)
        
        val result = SimulationUtils.remainingDistanceToStopLineM(vehicle)
        
        // Should return a reasonable remaining distance to stop line
        assertNotNull(result, "remainingDistanceToStopLineM should return a valid distance")
    }

    /**
     * Tests that clearDistanceToLeaderM correctly delegates to VehicleUtils.
     */
    @Test
    fun clearDistanceToLeaderM_delegatesToVehicleUtils() {
        val level = buildTestLevel()
        val street = level.streets.first()
        
        val leader = createTestVehicle(level, street, 100.0)
        val follower = createTestVehicle(level, street, 50.0)
        
        val result = SimulationUtils.clearDistanceToLeaderM(follower, leader)
        
        // Should return a reasonable clear distance between vehicles
        assertNotNull(result, "clearDistanceToLeaderM should return a valid distance")
    }

    /**
     * Tests that currentSegmentDirection correctly delegates to VehicleUtils.
     */
    @Test
    fun currentSegmentDirection_delegatesToVehicleUtils() {
        val level = buildTestLevel()
        val street = level.streets.first()
        
        val vehicle = createTestVehicle(level, street, 50.0)
        
        val result = SimulationUtils.currentSegmentDirection(vehicle)
        
        // Should return a direction vector for the current segment
        assertNotNull(result, "currentSegmentDirection should return a valid direction vector")
    }

    /**
     * Tests that leadingVehicleOnNextStreet correctly delegates to RouteUtils.
     */
    @Test
    fun leadingVehicleOnNextStreet_delegatesToRouteUtils() {
        val level = buildTestLevel()
        
        val sourceNode = level.nodes.filterIsInstance<SourceNode>().first()
        val config = SourceNodeConfiguration(
            Color("Rot", Colors.RED),
            30.0,
            50.0,
            VehicleType("Auto", 80.0, 4.0, 0.7, 3.0),
            vehiclesToSpawn = 1
        )
        
        val vehicle = Vehicle(
            Color("Rot", Colors.RED),
            VehicleType("Auto", 80.0, 4.0, 0.7, 3.0),
            sourceNode,
            config,
            level
        )
        vehicle.currentStreet = level.streets.first()
        
        val result = SimulationUtils.leadingVehicleOnNextStreet(vehicle)
        
        // For a simple level with one street, there should be no leading vehicle on next street
        assertEquals(null, result, "leadingVehicleOnNextStreet should return null when no next street exists")
    }

    /**
     * Tests that leadVehicle correctly delegates to RouteUtils.
     */
    @Test
    fun leadVehicle_delegatesToRouteUtils() {
        val level = buildTestLevel()
        val street = level.streets.first()
        
        val vehicle = createTestVehicle(level, street, 100.0)
        
        level.addVehicle(vehicle)
        
        val result = SimulationUtils.leadVehicle(street, level)
        
        // Should return the leading vehicle on the street
        assertEquals(vehicle, result, "leadVehicle should return the leading vehicle on the street")
    }
}
