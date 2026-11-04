package renderingtests

import domain.Color
import domain.ConnectionNode
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.StreetPathFinder
import domain.TargetNode
import domain.Vehicle
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import scenes.LevelScene
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests the visual layer selection for vehicles driving onto and off elevated streets.
 */
class VehicleVisualElevationTest {
    private val red = Color("Rot", Colors.RED)
    private val vehicleType = VehicleType("LKW", 80.0, 6.0, 0.7, 3.0, "images/truck.png")
    private val level = Level("test", 1)

    /**
     * Verifies that a vehicle switches to the elevated layer as soon as its front reaches a ramp.
     */
    @Test
    fun vehicleVisualElevation_usesHigherLayerWhenFrontReachesRamp() {
        val route = elevationRoute(
            sourceElevation = 0,
            connectionPosition = Point(100, 0),
            connectionElevation = 0,
            targetPosition = Point(120, 0),
            targetElevation = 1
        )
        val vehicle = routeVehicle(route).apply {
            currentStreet = route.firstStreet
            currentRouteStreetIndex = 0
            currentSegmentIndex = 0
            distanceAlongSegment = 98.0
        }

        assertEquals(1, LevelScene.vehicleVisualElevation(vehicle))
    }

    /**
     * Verifies that a vehicle stays elevated until its rear has fully left the ramp.
     */
    @Test
    fun vehicleVisualElevation_keepsHigherLayerUntilRearLeavesRamp() {
        val route = elevationRoute(
            sourceElevation = 1,
            connectionPosition = Point(20, 0),
            connectionElevation = 0,
            targetPosition = Point(120, 0),
            targetElevation = 0
        )
        val vehicle = routeVehicle(route).apply {
            currentStreet = route.secondStreet
            currentRouteStreetIndex = 1
            currentSegmentIndex = 0
            distanceAlongSegment = 2.0
        }

        assertEquals(1, LevelScene.vehicleVisualElevation(vehicle))
    }

    /**
     * Creates a two-street route whose middle node can model the start or end of a ramp.
     */
    private fun elevationRoute(
        sourceElevation: Int,
        connectionPosition: Point,
        connectionElevation: Int,
        targetPosition: Point,
        targetElevation: Int
    ): ElevationRoute {
        val source = SourceNode(Point(0, 0), sourceElevation, Angle.ZERO)
        val connection = ConnectionNode(connectionPosition, connectionElevation)
        val target = TargetNode(targetPosition, targetElevation, Angle.ZERO, red)
        return ElevationRoute(
            source,
            Street(source, connection, listOf(source.position, connection.position)),
            Street(connection, target, listOf(connection.position, target.position))
        )
    }

    /**
     * Creates a vehicle configured to follow the given route.
     */
    private fun routeVehicle(route: ElevationRoute): Vehicle {
        val cfg = SourceNodeConfiguration(red, 9999.0, 0.0, vehicleType)
        return Vehicle(red, vehicleType, route.source, cfg, level).apply {
            drivingRouteOptions = listOf(
                StreetPathFinder.DrivingRoute(
                    target = route.secondStreet.endNode as TargetNode,
                    streets = listOf(route.firstStreet, route.secondStreet),
                    length = 0.0
                )
            )
            selectedRouteIndex = 0
        }
    }

    /**
     * Holds the shared nodes and streets for an elevation route test case.
     *
     * @property source source node where the tested vehicle starts.
     * @property firstStreet first route segment used before or on the ramp transition.
     * @property secondStreet second route segment used after the ramp transition.
     */
    private data class ElevationRoute(
        val source: SourceNode,
        val firstStreet: Street,
        val secondStreet: Street
    )
}
