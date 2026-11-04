package networktests

import domain.Color
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.TargetNode
import domain.Vehicle
import domain.VehicleType
import korlibs.image.color.RGBA
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import network.CoupledSinkVehicleHandover
import network.VehicleDepartureBinding
import network.VehicleDepartureSender
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests outgoing vehicle handover from coupled sinks to the server message sender.
 */
class CoupledSinkVehicleHandoverTest {
    /**
     * Verifies that a coupled sink sends exactly one server departure message per vehicle.
     */
    @Test
    fun handoverVehicle_coupledSink_sendsExactlyOneVehicleDeparture() {
        val fixture = fixture(targetPosition = Point(733.0, 417.0))
        val sender = RecordingDepartureSender()
        val handover = CoupledSinkVehicleHandover(
            bindingsBySink = mapOf(fixture.target to fixture.binding),
            departureSender = sender
        )

        assertTrue(handover.handoverVehicle(fixture.vehicle, fixture.target))
        assertTrue(handover.handoverVehicle(fixture.vehicle, fixture.target))

        val message = sender.messages.single()
        assertEquals("transition-right", message.transitionNodeId)
        assertEquals("world-1-field-0-0", message.fromFieldId)
        assertEquals("world-1-field-0-1", message.toFieldId)
        assertEquals(0, message.level)
        assertEquals(listOf("color", "type", "speedMetersPerSecond"), message.vehicleProperties.keys.toList())
        assertEquals(listOf("r", "g", "b", "a"), (message.vehicleProperties["color"] as JsonObject).keys.toList())
        assertEquals("CAR", (message.vehicleProperties["type"] as JsonPrimitive).content)
        assertEquals(12.5, (message.vehicleProperties["speedMetersPerSecond"] as JsonPrimitive).content.toDouble())
    }

    /**
     * Verifies that unbound sinks are ignored by the multi-table handover adapter.
     */
    @Test
    fun handoverVehicle_unboundSink_sendsNoMessage() {
        val fixture = fixture(targetPosition = Point(900.0, 100.0))
        val otherTarget = TargetNode(Point(100.0, 100.0), 0, Angle.ZERO, fixture.color)
        val sender = RecordingDepartureSender()
        val handover = CoupledSinkVehicleHandover(
            bindingsBySink = mapOf(otherTarget to fixture.binding),
            departureSender = sender
        )

        assertFalse(handover.handoverVehicle(fixture.vehicle, fixture.target))

        assertTrue(sender.messages.isEmpty())
    }

    private fun fixture(targetPosition: Point): Fixture {
        val color = Color("Rot", RGBA(255, 0, 0, 255))
        val vehicleType = VehicleType("CAR", maxSpeed = 100.0, length = 4.0, adhesionCoefficient = 0.8, acceleration = 3.0)
        val level = Level("handover-network", 1)
        val source = SourceNode(Point(0.0, 0.0), 0, Angle.ZERO)
        val sourceConfig = SourceNodeConfiguration(
            color = color,
            generationSpeed = 1.0,
            initialVehicleSpeed = 0.0,
            vehicleType = vehicleType
        )
        source.configurations = listOf(sourceConfig)
        val target = TargetNode(targetPosition, 0, Angle.ZERO, color)
        val vehicle = Vehicle(color, vehicleType, source, sourceConfig, level).apply {
            currentSpeed = 12.5
        }
        val binding = VehicleDepartureBinding(
            transitionNodeId = "transition-right",
            fromFieldId = "world-1-field-0-0",
            toFieldId = "world-1-field-0-1",
            level = 0
        )

        return Fixture(color, target, vehicle, binding)
    }

    /**
     * Shared domain objects for outgoing coupled-sink handover tests.
     *
     * @property color vehicle color used in the serialized transfer payload.
     * @property target coupled sink reached by the vehicle.
     * @property vehicle vehicle that should be sent to the neighboring field.
     * @property binding server transition binding for [target].
     */
    private data class Fixture(
        val color: Color,
        val target: TargetNode,
        val vehicle: Vehicle,
        val binding: VehicleDepartureBinding
    )

    /**
     * Test sender that records all departure messages instead of using a WebSocket.
     *
     * @property messages messages sent through this sender.
     */
    private class RecordingDepartureSender : VehicleDepartureSender {
        val messages = mutableListOf<DepartureMessage>()

        override fun sendVehicleDeparture(
            transitionNodeId: String,
            fromFieldId: String,
            toFieldId: String,
            level: Int,
            vehicleProperties: JsonObject
        ) {
            messages += DepartureMessage(transitionNodeId, fromFieldId, toFieldId, level, vehicleProperties)
        }
    }

    /**
     * Captured outgoing server departure message.
     *
     * @property transitionNodeId server transition used for the departure.
     * @property fromFieldId local source field ID.
     * @property toFieldId neighboring target field ID.
     * @property level transition elevation level.
     * @property vehicleProperties serialized vehicle payload.
     */
    private data class DepartureMessage(
        val transitionNodeId: String,
        val fromFieldId: String,
        val toFieldId: String,
        val level: Int,
        val vehicleProperties: JsonObject
    )
}
