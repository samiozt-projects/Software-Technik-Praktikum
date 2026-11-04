package domaintests

import domain.*
import simulation.Simulation
import simulation.VehicleMovementManager
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the VehicleMovementManager class, focusing on vehicle movement logic
 * including merge handling and the hasClearedMerge function.
 */
class VehicleMovementManagerTest {

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
     * Builds a minimal level with a merge scenario for testing hasClearedMerge.
     * 
     * @return A pair containing the level and the merge connection node.
     */
    private fun buildMergeTestLevel(): Pair<Level, ConnectionNode> {
        val level = Level("MergeTestLevel", 1)
        val red = Color("Rot", Colors.RED)
        
        // Create source nodes
        val source1 = SourceNode(scaledPoint(0, 0), 0, Angle.fromDegrees(0.0), listOf())
        val source2 = SourceNode(scaledPoint(0, 200), 0, Angle.fromDegrees(0.0), listOf())
        
        // Create merge node
        val mergeNode = ConnectionNode(scaledPoint(400, 100), 0)
        
        // Create target node
        val target = TargetNode(scaledPoint(800, 100), 0, Angle.fromDegrees(0.0), red)
        
        // Add nodes to level
        level.addNode(source1)
        level.addNode(source2)
        level.addNode(mergeNode)
        level.addNode(target)
        
        // Create streets
        val street1 = Street(source1, mergeNode, listOf(scaledPoint(0, 0), scaledPoint(400, 100)))
        val street2 = Street(source2, mergeNode, listOf(scaledPoint(0, 200), scaledPoint(400, 100)))
        val outgoingStreet = Street(mergeNode, target, listOf(scaledPoint(400, 100), scaledPoint(800, 100)))
        
        level.addStreet(street1)
        level.addStreet(street2)
        level.addStreet(outgoingStreet)
        
        level.validate()
        return Pair(level, mergeNode)
    }

    /**
     * Creates a test vehicle for merge testing.
     * 
     * @param level The level to create the vehicle for.
     * @param street The street to place the vehicle on.
     * @return The created vehicle.
     */
    private fun createTestVehicle(level: Level, street: Street): Vehicle {
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
        }
    }

    /**
     * Tests that hasClearedMerge returns true when the occupant vehicle has been removed from simulation.
     */
    @Test
    fun hasClearedMerge_occupantRemovedFromSimulation_returnsTrue() {
        val (level, mergeNode) = buildMergeTestLevel()
        val sim = Simulation(level)
        val movementManager = VehicleMovementManager(sim)
        
        // Create a vehicle and add it to the simulation
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
        
        // Add vehicle to level and set as merge occupant
        level.addVehicle(vehicle)
        sim.mergeOccupant[mergeNode] = vehicle
        
        // Remove the vehicle from simulation
        level.removeVehicle(vehicle)
        
        // Test that hasClearedMerge returns true
        val result = movementManager.hasClearedMerge(vehicle, mergeNode)
        assertTrue(result, "hasClearedMerge should return true when vehicle is removed from simulation")
    }

    /**
     * Tests that hasClearedMerge returns true when the occupant has no current street.
     */
    @Test
    fun hasClearedMerge_occupantHasNoCurrentStreet_returnsTrue() {
        val (level, mergeNode) = buildMergeTestLevel()
        val sim = Simulation(level)
        val movementManager = VehicleMovementManager(sim)
        
        // Create a vehicle
        val vehicle = createTestVehicle(level, level.streets.first())
        
        // Add vehicle to level and set as merge occupant
        level.addVehicle(vehicle)
        sim.mergeOccupant[mergeNode] = vehicle
        
        // Set vehicle's current street to null
        vehicle.currentStreet = null
        
        // Test that hasClearedMerge returns true
        val result = movementManager.hasClearedMerge(vehicle, mergeNode)
        assertTrue(result, "hasClearedMerge should return true when vehicle has no current street")
    }

    /**
     * Tests that hasClearedMerge returns false when the occupant is still on an incoming street.
     */
    @Test
    fun hasClearedMerge_occupantOnIncomingStreet_returnsFalse() {
        val (level, mergeNode) = buildMergeTestLevel()
        val sim = Simulation(level)
        val movementManager = VehicleMovementManager(sim)
        
        // Create a vehicle on an incoming street
        val incomingStreet = mergeNode.incomingStreets.first()
        val vehicle = createTestVehicle(level, incomingStreet)
        
        // Add vehicle to level and set as merge occupant
        level.addVehicle(vehicle)
        sim.mergeOccupant[mergeNode] = vehicle
        
        // Test that hasClearedMerge returns false
        val result = movementManager.hasClearedMerge(vehicle, mergeNode)
        assertFalse(result, "hasClearedMerge should return false when vehicle is still on incoming street")
    }

    /**
     * Tests that hasClearedMerge returns true when the occupant is on an outgoing street and has cleared the merge.
     */
    @Test
    fun hasClearedMerge_occupantOnOutgoingStreetAndCleared_returnsTrue() {
        val (level, mergeNode) = buildMergeTestLevel()
        val sim = Simulation(level)
        val movementManager = VehicleMovementManager(sim)
        
        // Create a vehicle on an outgoing street
        val outgoingStreet = mergeNode.outgoingStreets.first()
        val vehicle = createTestVehicle(level, outgoingStreet)
        
        // Add vehicle to level and set as merge occupant
        level.addVehicle(vehicle)
        sim.mergeOccupant[mergeNode] = vehicle
        
        // Move vehicle far enough along the outgoing street to clear the merge
        vehicle.currentSegmentIndex = 1
        vehicle.distanceAlongSegment = 10.0 // More than enough to clear the merge
        
        // Test that hasClearedMerge returns true
        val result = movementManager.hasClearedMerge(vehicle, mergeNode)
        assertTrue(result, "hasClearedMerge should return true when vehicle has cleared the merge on outgoing street")
    }

    /**
     * Tests that hasClearedMerge returns false when the occupant is on an outgoing street but hasn't cleared the merge yet.
     */
    @Test
    fun hasClearedMerge_occupantOnOutgoingStreetButNotCleared_returnsFalse() {
        val (level, mergeNode) = buildMergeTestLevel()
        val sim = Simulation(level)
        val movementManager = VehicleMovementManager(sim)
        
        // Create a vehicle on an outgoing street
        val outgoingStreet = mergeNode.outgoingStreets.first()
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
        vehicle.currentStreet = outgoingStreet
        
        // Add vehicle to level and set as merge occupant
        level.addVehicle(vehicle)
        sim.mergeOccupant[mergeNode] = vehicle
        
        // Position vehicle at the start of the outgoing street (not cleared yet)
        vehicle.currentSegmentIndex = 0
        vehicle.distanceAlongSegment = 0.0
        
        // Test that hasClearedMerge returns false
        val result = movementManager.hasClearedMerge(vehicle, mergeNode)
        assertFalse(result, "hasClearedMerge should return false when vehicle hasn't cleared the merge yet")
    }

    /**
     * Tests that hasClearedMerge returns true when the vehicle is on a street that's neither incoming nor outgoing.
     */
    @Test
    fun hasClearedMerge_occupantOnUnrelatedStreet_returnsTrue() {
        val (level, mergeNode) = buildMergeTestLevel()
        val sim = Simulation(level)
        val movementManager = VehicleMovementManager(sim)
        
        // Create an additional street that's not connected to the merge node
        val unrelatedSource = SourceNode(scaledPoint(1000, 1000), 0, Angle.fromDegrees(0.0), listOf())
        val unrelatedTarget = TargetNode(scaledPoint(1200, 1000), 0, Angle.fromDegrees(0.0), Color("Rot", Colors.RED))
        level.addNode(unrelatedSource)
        level.addNode(unrelatedTarget)
        val unrelatedStreet = Street(unrelatedSource, unrelatedTarget, listOf(scaledPoint(1000, 1000), scaledPoint(1200, 1000)))
        level.addStreet(unrelatedStreet)
        
        // Create a vehicle on the unrelated street
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
            unrelatedSource,
            config,
            level
        )
        vehicle.currentStreet = unrelatedStreet
        
        // Add vehicle to level and set as merge occupant (this shouldn't happen in real scenario but tests the logic)
        level.addVehicle(vehicle)
        sim.mergeOccupant[mergeNode] = vehicle
        
        // Test that hasClearedMerge returns true
        val result = movementManager.hasClearedMerge(vehicle, mergeNode)
        assertTrue(result, "hasClearedMerge should return true when vehicle is on unrelated street")
    }
}
