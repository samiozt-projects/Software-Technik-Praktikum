package domaintests

import domain.Color
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.TargetNode
import domain.Vehicle
import domain.VehicleType
import korlibs.image.color.RGBA
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import simulation.Simulation
import simulation.VehicleTargetHandoverPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests simulation handover behavior for vehicles reaching coupled sinks.
 */
class VehicleTargetHandoverSimulationTest {
    /**
     * Verifies that reaching a coupled sink triggers one handover and removes the vehicle.
     */
    @Test
    fun tick_vehicleAtCoupledTarget_isHandedOverExactlyOnce() {
        val fixture = singleStreetFixture(targetPosition = Point(10.0, 0.0))
        val recorder = RecordingTargetHandover(coupledTarget = fixture.target)
        val simulation = Simulation(fixture.level, targetHandoverPort = recorder)
        val vehicle = fixture.vehicleAtStart(speedMetersPerSecond = 20.0)
        fixture.level.addVehicle(vehicle)

        simulation.tick(1.0)
        simulation.tick(1.0)

        assertEquals(1, recorder.calls.size)
        assertSame(vehicle, recorder.calls.single().vehicle)
        assertSame(fixture.target, recorder.calls.single().targetNode)
        assertTrue(fixture.level.vehicles.isEmpty())
        assertEquals(1, fixture.target.vehiclesArrived)
        assertEquals(1, fixture.sourceConfig.vehiclesArrived)
    }

    /**
     * Verifies that sink position does not affect handover detection.
     */
    @Test
    fun tick_coupledTargetAwayFromEdge_isHandedOver() {
        val fixture = singleStreetFixture(targetPosition = Point(733.0, 417.0))
        val recorder = RecordingTargetHandover(coupledTarget = fixture.target)
        val simulation = Simulation(fixture.level, targetHandoverPort = recorder)
        val vehicle = fixture.vehicleAtStart(speedMetersPerSecond = 1000.0)
        fixture.level.addVehicle(vehicle)

        simulation.tick(1.0)

        assertEquals(1, recorder.calls.size)
        assertSame(fixture.target, recorder.calls.single().targetNode)
    }

    private fun singleStreetFixture(targetPosition: Point): Fixture {
        val red = Color("Rot", RGBA(255, 0, 0, 255))
        val car = VehicleType("CAR", maxSpeed = 100.0, length = 4.0, adhesionCoefficient = 0.8, acceleration = 3.0)
        val sourceConfig = SourceNodeConfiguration(
            color = red,
            generationSpeed = 9999.0,
            initialVehicleSpeed = 0.0,
            vehicleType = car,
            vehiclesToSpawn = 1,
            vehiclesSpawned = 1
        )
        val source = SourceNode(Point(0.0, 0.0), 0, Angle.ZERO, listOf(sourceConfig))
        val target = TargetNode(targetPosition, 0, Angle.ZERO, red)
        val level = Level("handover-simulation", 1)
        val street = Street(source, target, listOf(source.position, target.position))

        level.addNode(source)
        level.addNode(target)
        level.addStreet(street)

        return Fixture(level, source, sourceConfig, target, street, red, car)
    }

    /**
     * Shared level graph for coupled-target simulation tests.
     *
     * @property level level containing one source, one street, and one target.
     * @property source source node where the test vehicle starts.
     * @property sourceConfig source configuration assigned to the test vehicle.
     * @property target target node treated as coupled sink.
     * @property street street connecting [source] and [target].
     * @property color color shared by source, vehicle, and target.
     * @property vehicleType type assigned to the test vehicle.
     */
    private data class Fixture(
        val level: Level,
        val source: SourceNode,
        val sourceConfig: SourceNodeConfiguration,
        val target: TargetNode,
        val street: Street,
        val color: Color,
        val vehicleType: VehicleType
    ) {
        /**
         * Creates a vehicle positioned at the beginning of [street].
         *
         * @param speedMetersPerSecond initial speed in metres per second.
         * @return vehicle ready to be inserted into [level].
         */
        fun vehicleAtStart(speedMetersPerSecond: Double): Vehicle =
            Vehicle(color, vehicleType, source, sourceConfig, level).apply {
                currentStreet = street
                currentSpeed = speedMetersPerSecond
            }
    }

    private class RecordingTargetHandover(
        private val coupledTarget: TargetNode
    ) : VehicleTargetHandoverPort {
        /** Handover calls accepted by this recorder. */
        val calls = mutableListOf<HandoverCall>()

        override fun handoverVehicle(vehicle: Vehicle, targetNode: TargetNode): Boolean {
            if (targetNode !== coupledTarget) return false
            calls += HandoverCall(vehicle, targetNode)
            return true
        }
    }

    /**
     * Captured target-handover call.
     *
     * @property vehicle vehicle passed to the handover port.
     * @property targetNode target passed to the handover port.
     */
    private data class HandoverCall(
        val vehicle: Vehicle,
        val targetNode: TargetNode
    )
}
