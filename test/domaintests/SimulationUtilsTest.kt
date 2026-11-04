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
 * Tests for the SimulationUtils object to provide code coverage for the delegation methods.
 */
class SimulationUtilsTest {

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
     * Tests that segmentLengthM correctly delegates to StreetUtils.
     */
    @Test
    fun segmentLengthM_delegatesToStreetUtils() {
        val path = listOf(
            scaledPoint(0, 0),
            scaledPoint(100, 0),
            scaledPoint(200, 0)
        )
        
        val result = SimulationUtils.segmentLengthM(path, 0)
        
        // The length should be 12.5 meters (from 0,0 to 100,0 with 8px/m scaling)
        assertEquals(12.5, result, 0.001, "segmentLengthM should return correct segment length")
    }

    /**
     * Tests that streetLengthM correctly delegates to StreetUtils.
     */
    @Test
    fun streetLengthM_delegatesToStreetUtils() {
        val path = listOf(
            scaledPoint(0, 0),
            scaledPoint(100, 0),
            scaledPoint(200, 0)
        )
        
        val result = SimulationUtils.streetLengthM(path)
        
        // The total length should be 25 meters (from 0,0 to 200,0 with 8px/m scaling)
        assertEquals(25.0, result, 0.001, "streetLengthM should return correct total street length")
    }

    /**
     * Tests that progressAlongStreetM correctly delegates to VehicleUtils.
     */
    @Test
    fun progressAlongStreetM_delegatesToVehicleUtils() {
        val level = buildTestLevel()
        val street = level.streets.first()
        
        val vehicle = createTestVehicle(level, street, 50.0)
        
        val result = SimulationUtils.progressAlongStreetM(vehicle)
        
        // The vehicle should have progressed 50 meters along the street
        assertEquals(50.0, result, 0.001, "progressAlongStreetM should return correct vehicle progress")
    }

    /**
     * Tests that remainingDistanceToEndM correctly delegates to VehicleUtils.
     */
    @Test
    fun remainingDistanceToEndM_delegatesToVehicleUtils() {
        val level = buildTestLevel()
        val street = level.streets.first()
        
        val vehicle = createTestVehicle(level, street, 50.0)
        
        val result = SimulationUtils.remainingDistanceToEndM(vehicle)
        
        // The street is 100 meters long, vehicle has progressed 50 meters, so 50 meters remain
        assertEquals(50.0, result, 0.001, "remainingDistanceToEndM should return correct remaining distance")
    }

    /**
     * Tests that stopLineDistanceM correctly delegates to VehicleUtils.
     */
    @Test
    fun stopLineDistanceM_delegatesToVehicleUtils() {
        val level = buildTestLevel()
        val street = level.streets.first()
        
        val vehicle = createTestVehicle(level, street)
        
        val result = SimulationUtils.stopLineDistanceM(vehicle)
        
        // The stop line distance should be based on vehicle length and simulation constants
        val expected = vehicle.type.length / 2.0 + Simulation.STOP_LINE_FRONT_OFFSET_M
        assertEquals(expected, result, 0.001, "stopLineDistanceM should return correct stop line distance")
    }

    /**
     * Tests that mergeClearanceDistanceM correctly delegates to VehicleUtils.
     */
    @Test
    fun mergeClearanceDistanceM_delegatesToVehicleUtils() {
        val level = buildTestLevel()
        
        val vehicle = createTestVehicle(level, level.streets.first())
        
        val result = SimulationUtils.mergeClearanceDistanceM(vehicle)
        
        // The merge clearance distance should be based on vehicle length and simulation constants
        val expected = vehicle.type.length / 2.0 + Simulation.MERGE_CLEARANCE_M
        assertEquals(expected, result, 0.001, "mergeClearanceDistanceM should return correct merge clearance distance")
    }

    /**
     * Tests that leadingVehicleOnSameStreet correctly delegates to RouteUtils.
     */
    @Test
    fun leadingVehicleOnSameStreet_delegatesToRouteUtils() {
        val level = buildTestLevel()
        val street = level.streets.first()
        
        val vehicle1 = createTestVehicle(level, street, 100.0)
        val vehicle2 = createTestVehicle(level, street, 50.0)
        
        level.addVehicle(vehicle1)
        level.addVehicle(vehicle2)
        
        val result = SimulationUtils.leadingVehicleOnSameStreet(vehicle2)
        
        // vehicle1 should be the leading vehicle for vehicle2
        assertNotNull(result, "leadingVehicleOnSameStreet should find a leading vehicle")
        assertEquals(vehicle1, result, "leadingVehicleOnSameStreet should return the correct leading vehicle")
    }

    /**
     * AC: a vehicle on a street under a bridge must never be treated as blocked by a vehicle on the
     * bridge above it, even when both streets occupy the exact same projected path. Leader lookup
     * keys strictly on Street identity, not on projected position, so elevation cannot leak in.
     */
    @Test
    fun leadingVehicleOnSameStreet_ignoresVehicleOnDifferentElevationStreetAtSameProjectedPosition() {
        val level = buildTestLevel()
        val groundStreet = level.streets.first()
        val groundVehicle = createTestVehicle(level, groundStreet, 50.0)
        level.addVehicle(groundVehicle)

        // A bridge occupying the exact same projected path one elevation level up, with its own
        // vehicle further along that path - geometrically "ahead" but on a different Street object.
        val bridgeSource = SourceNode(scaledPoint(0, 0), 1, Angle.fromDegrees(0.0), listOf())
        val bridgeTarget = TargetNode(scaledPoint(800, 0), 1, Angle.fromDegrees(0.0), Color("Rot", Colors.RED))
        level.addNode(bridgeSource)
        level.addNode(bridgeTarget)
        val bridgeStreet = Street(bridgeSource, bridgeTarget, groundStreet.path)
        level.addStreet(bridgeStreet)
        val bridgeVehicle = createTestVehicle(level, bridgeStreet, 100.0)
        level.addVehicle(bridgeVehicle)

        val result = SimulationUtils.leadingVehicleOnSameStreet(groundVehicle)

        assertEquals(
            null,
            result,
            "A vehicle on the bridge above must not be treated as a leader for a vehicle on the ground street below it"
        )
    }

    /**
     * Tests that allConfiguredVehiclesArrived correctly delegates to RouteUtils.
     */
    @Test
    fun allConfiguredVehiclesArrived_delegatesToRouteUtils() {
        val level = buildTestLevel()
        
        // Initially, no vehicles have arrived
        val result1 = SimulationUtils.allConfiguredVehiclesArrived(level)
        assertEquals(false, result1, "allConfiguredVehiclesArrived should return false when no vehicles have arrived")
        
        // Add a source configuration and mark it as arrived
        val sourceNode = level.nodes.filterIsInstance<SourceNode>().first()
        val config = SourceNodeConfiguration(
            Color("Rot", Colors.RED),
            30.0,
            50.0,
            VehicleType("Auto", 80.0, 4.0, 0.7, 3.0),
            vehiclesToSpawn = 1
        )
        config.vehiclesArrived = 1
        sourceNode.configurations = listOf(config)
        
        val result2 = SimulationUtils.allConfiguredVehiclesArrived(level)
        assertEquals(true, result2, "allConfiguredVehiclesArrived should return true when all configured vehicles have arrived")
    }
}
