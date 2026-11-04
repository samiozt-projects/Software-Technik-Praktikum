import domain.Color
import domain.ConnectionNode
import domain.GameState
import domain.Level
import domain.NetworkState
import domain.SimulationState
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.TargetNode
import domain.Vehicle
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Tests the domain model classes. */
class DomainModelTest {
    
    /**
     * Tests that the vehicle classes works correctly.
     */
    @Test
    fun testVehicleClasses() {
        val type = VehicleType("Auto", 120.0, 4.0, 0.8, 5.0)
        assertEquals("Auto", type.name)

        val color = Color("Rot", Colors.RED)
        assertEquals("Rot", color.name)

        val vehicle = testVehicle(color, type)
        vehicle.averageSpeed = 60.0

        assertEquals(color, vehicle.color)
        assertEquals(type, vehicle.type)
        assertEquals(0.0, vehicle.currentSpeed)
        assertEquals(60.0, vehicle.averageSpeed)
    }

    /**
     * Tests that the street referential integrity works correctly.
     */
    @Test
    fun testStreetReferentialIntegrity() {
        val startNode =
                SourceNode(
                        position = Point(0, 0),
                        elevation = 0,
                        orientation = Angle.fromDegrees(90),
                        configurations = emptyList()
                )
        val endNode =
                TargetNode(
                        position = Point(100, 0),
                        elevation = 0,
                        orientation = Angle.fromDegrees(270),
                        color = Color("Blau", Colors.BLUE)
                )

        val path = listOf(Point(0, 0), Point(50, 0), Point(100, 0))
        val street = Street(startNode, endNode, path)

        // Check referential integrity: street must add itself to the respective node lists
        assertTrue(
                startNode.outgoingStreets.contains(street),
                "Street should be in startNode's outgoingStreets"
        )
        assertTrue(startNode.incomingStreets.isEmpty(), "startNode should have no incoming streets")

        assertTrue(
                endNode.incomingStreets.contains(street),
                "Street should be in endNode's incomingStreets"
        )
        assertTrue(endNode.outgoingStreets.isEmpty(), "endNode should have no outgoing streets")

        assertEquals(startNode, street.startNode)
        assertEquals(endNode, street.endNode)

        // Verify that deleting the street removes it from the nodes
        street.delete()
        assertTrue(
                startNode.outgoingStreets.isEmpty(),
                "startNode should have no outgoing streets after deletion"
        )
        assertTrue(
                endNode.incomingStreets.isEmpty(),
                "endNode should have no incoming streets after deletion"
        )
    }

    /**
     * Tests that the connection node works correctly.
     */
    @Test
    fun testConnectionNode() {
        val connNode = ConnectionNode(Point(50, 50), 1)
        assertEquals(1, connNode.elevation)
        assertTrue(connNode.incomingStreets.isEmpty())
        assertTrue(connNode.outgoingStreets.isEmpty())
    }

    /**
     * Tests that the level and state works correctly.
     */
    @Test
    fun testLevelAndState() {
        val level = Level("TestLevel", 1)
        assertEquals("TestLevel", level.name)
        assertEquals(1, level.number)
        assertEquals(NetworkState.EMPTY, level.networkState)

        level.networkState = NetworkState.COMPLETE
        assertEquals(NetworkState.COMPLETE, level.networkState)

        // Test GameState
        GameState.currentLevel = level
        GameState.simulationState = SimulationState.SIMULATION

        assertEquals(level, GameState.currentLevel)
        assertEquals(SimulationState.SIMULATION, GameState.simulationState)
    }

    /**
     * Tests that the node elevation validation works correctly.
     */
    @Test
    fun testNodeElevationValidation() {
        // Valid elevations are 0, 1, 2
        ConnectionNode(Point(0, 0), 0)
        ConnectionNode(Point(0, 0), 1)
        ConnectionNode(Point(0, 0), 2)

        // Invalid elevations should throw an exception
        kotlin.test.assertFailsWith<IllegalArgumentException> { ConnectionNode(Point(0, 0), 3) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { ConnectionNode(Point(0, 0), -1) }
    }

    /**
     * Tests that the source node configurations and tracking works correctly.
     */
    @Test
    fun testSourceNodeConfigurationsAndTracking() {
        val config =
            domain.SourceNodeConfiguration(
                Color("Green", Colors.GREEN),
                2.5,
                30.0,
                VehicleType("Truck", 90.0, 6.0, 0.7, 3.0)
            )
        val sourceNode = SourceNode(Point(10, 10), 0, Angle.fromDegrees(180), listOf(config))

        assertEquals(1, sourceNode.configurations.size)
        assertEquals(2.5, sourceNode.configurations.first().generationSpeed)
        assertEquals(0, sourceNode.vehiclesGenerated)

        sourceNode.vehiclesGenerated++
        assertEquals(1, sourceNode.vehiclesGenerated)
        assertEquals(180.0, sourceNode.orientation.degrees)
    }

    /**
     * Tests that the target node properties and tracking works correctly.
     */
    @Test
    fun testTargetNodePropertiesAndTracking() {
        val targetNode =
                TargetNode(Point(100, 100), 2, Angle.fromDegrees(0), Color("Yellow", Colors.YELLOW))

        assertEquals("Yellow", targetNode.color.name)
        assertEquals(0.0, targetNode.orientation.degrees)
        assertEquals(0, targetNode.vehiclesArrived)

        targetNode.vehiclesArrived += 5
        assertEquals(5, targetNode.vehiclesArrived)
    }

    /**
     * Tests that the multiple streets on connection node works correctly.
     */
    @Test
    fun testMultipleStreetsOnConnectionNode() {
        val center = ConnectionNode(Point(50, 50), 0)
        val top = SourceNode(Point(50, 0), 0, Angle.fromDegrees(90), emptyList())
        val right = TargetNode(Point(100, 50), 0, Angle.fromDegrees(180), Color("Red", Colors.RED))
        val bottom = SourceNode(Point(50, 100), 0, Angle.fromDegrees(270), emptyList())

        // Two incoming streets to center
        Street(top, center, emptyList())
        Street(bottom, center, emptyList())

        // One outgoing street from center
        Street(center, right, emptyList())

        assertEquals(2, center.incomingStreets.size)
        assertEquals(1, center.outgoingStreets.size)

        assertEquals(1, top.outgoingStreets.size)
        assertEquals(1, bottom.outgoingStreets.size)
        assertEquals(1, right.incomingStreets.size)
    }

    /**
     * Tests that the level referential integrity works correctly.
     */
    @Test
    fun testLevelReferentialIntegrity() {
        val level = Level("IntegrityLevel", 1)
        val sourceNode = SourceNode(Point(0, 0), 0, Angle.fromDegrees(90), emptyList())
        val connNode = ConnectionNode(Point(50, 0), 0)

        level.addNode(sourceNode)
        level.addNode(connNode)

        val street = Street(sourceNode, connNode, listOf(Point(0, 0), Point(50, 0)))
        level.addStreet(street)

        assertEquals(2, level.nodes.size)
        assertEquals(1, level.streets.size)

        // Remove the street
        level.removeStreet(street)

        /**
         * The ConnectionNode should be automatically removed because it has no streets left.
         * The SourceNode should remain even though it has no streets.
         */
        assertEquals(1, level.nodes.size, "Level should only have the SourceNode left")
        assertTrue(level.nodes.contains(sourceNode))
        assertEquals(0, level.streets.size, "Street should be removed from level")
    }

    /**
     * Tests that the level vehicles tracking works correctly.
     */
    @Test
    fun testLevelVehiclesTracking() {
        val level = Level("VehicleLevel", 1)
        val type = VehicleType("Auto", 120.0, 4.0, 0.8, 5.0)
        val color = Color("Rot", Colors.RED)
        val vehicle = testVehicle(color, type)

        level.addVehicle(vehicle)
        assertEquals(1, level.vehicles.size)
        assertTrue(level.vehicles.contains(vehicle))

        level.removeVehicle(vehicle)
        assertEquals(0, level.vehicles.size)
    }

    /**
     * Tests that the street elevation validation works correctly.
     */
    @Test
    fun testStreetElevationValidation() {
        val node0 = ConnectionNode(Point(0, 0), 0)
        val node1 = ConnectionNode(Point(100, 0), 1)
        val node2 = ConnectionNode(Point(200, 0), 2)
        
        // Allowed: difference of 0
        Street(node0, ConnectionNode(Point(10, 10), 0), emptyList())
        
        // Allowed: difference of 1
        Street(node0, node1, emptyList())
        Street(node1, node2, emptyList())
        Street(node1, node0, emptyList())
        
        // Not allowed: difference of 2
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            Street(node0, node2, emptyList())
        }
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            Street(node2, node0, emptyList())
        }
    }

    /**
     * Tests that the vehicle position calculation works correctly.
     */
    @Test
    fun testVehiclePositionCalculation() {
        val type = VehicleType("Auto", 120.0, 4.0, 0.8, 5.0)
        val color = Color("Rot", Colors.RED)
        val vehicle = testVehicle(color, type)
        
        // Null when not on a street
        kotlin.test.assertNull(vehicle.calculatePosition())
        
        // Create a test street
        val node0 = ConnectionNode(Point(0, 0), 0)
        val node1 = ConnectionNode(Point(100, 0), 0)
        val street = Street(node0, node1, listOf(Point(0, 0), Point(100, 0)))
        
        vehicle.currentStreet = street
        vehicle.currentSegmentIndex = 0
        vehicle.distanceAlongSegment = 25.0
        
        val pos = vehicle.calculatePosition()
        kotlin.test.assertNotNull(pos)
        assertEquals(25.0f, pos.x.toFloat())
        assertEquals(0.0f, pos.y.toFloat())
        
        // What if it moves to segment index >= path.size? Should cap at the last point.
        vehicle.currentSegmentIndex = 1
        val posEnd = vehicle.calculatePosition()
        kotlin.test.assertNotNull(posEnd)
        assertEquals(100.0f, posEnd.x.toFloat())
    }

    /**
     * Tests that the vehicle type holds a graphic reference.
     */
    @Test
    fun testVehicleTypeHoldsGraphicRef() {
        // Test Data
        val customType = domain.VehicleType(
            name = "truck",
            maxSpeed = 80.0,
            length = 12.0,
            adhesionCoefficient = 0.6,
            acceleration = 1.5,
            graphicRef = "images/truck.png"
        )
        val defaultType = domain.VehicleType(
            name = "car",
            maxSpeed = 120.0,
            length = 4.0,
            adhesionCoefficient = 0.8,
            acceleration = 2.5,
            // result should be: graphicRef = ""
        )

        assertEquals("images/truck.png", customType.graphicRef, "Custom graphic reference should be stored.")
        assertEquals("", defaultType.graphicRef, "Default graphic reference should be empty string.")
    }

    /**
     * Tests that the system resolves the correct graphic for vehicles in all situations.
     * runBlocking is a Coroutine-Builder, so we can include suspend fun in our tests.
     */
    @Test
    fun testSystemResolvesCorrectGraphicForVehiclesInAllSituations() = runBlocking {
        // Setup Environment
        ui.Assets.loadAll()
        // Test Data
        val truckType = domain.VehicleType(
            name = "Truck",
            maxSpeed = 80.0,
            length = 8.0,
            adhesionCoefficient = 0.6,
            acceleration = 1.2,
            graphicRef = "images/truck.png")
        val ghostType = domain.VehicleType(
            name = "Ghost",
            maxSpeed = 100.0,
            length = 4.0,
            adhesionCoefficient = 0.8,
            acceleration = 2.0,
            graphicRef = "images/non_existent.png")
        val normalType = domain.VehicleType(
            name = "Car",
            maxSpeed = 130.0,
            length = 4.0,
            adhesionCoefficient = 0.8,
            acceleration = 2.5,
            graphicRef = "")

        /** Optional integration step: to test the full caching pipeline,
         * you would trigger the asynchronous preloader here:
         */
        ui.Assets.preloadVehicleGraphics(listOf(truckType))

        /**
         * --- Situation A: The Happy Path ---
         * The specific graphic reference is valid and was requested.
         */
        val resolvedTruckBmp = ui.Assets.getVehicleGraphic(truckType.graphicRef)
        assertNotNull(resolvedTruckBmp, "Should return a valid Bitmap for valid references.")

        /**
         * --- Situation B: The Backward-Compatibility Path ---
         * The graphic reference field is completely empty (e.g., legacy levels or standard vehicles).
         * The system must swiftly bypass the map search and fall back to the default car sprite.
         */
        val resolvedNormalBmp = ui.Assets.getVehicleGraphic(normalType.graphicRef)
        assertEquals(ui.Assets.car, resolvedNormalBmp, "Empty graphicRef must strictly fall back to Assets.car")

        /**
         * --- Situation C: The Robustness / Error Path ---
         * A path is defined, but the file either does not exist or failed to preload.
         * The system must gracefully recover instead of crashing, handing out the default car sprite instead.
         */
        val resolvedGhostBmp = ui.Assets.getVehicleGraphic(ghostType.graphicRef)
        assertEquals(ui.Assets.car, resolvedGhostBmp, "Non-existent or un-preloaded graphics must safely fall back to Assets.car")
    }

    /**
     * Tests that the vehicle graphic caching works correctly.
     * 
     * @param color The color of the vehicle.
     * @param type The type of the vehicle.
     * @return The vehicle.
     */
    private fun testVehicle(color: Color, type: VehicleType): Vehicle {
        val cfg = SourceNodeConfiguration(color, 9999.0, 0.0, type)
        val source = SourceNode(Point(0, 0), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val level = Level("test", 1)
        return Vehicle(color, type, source, cfg, level)
    }
}
