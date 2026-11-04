package networktests

import domain.Color
import domain.FieldReference
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.SourceNodeKind
import domain.Street
import domain.TargetNode
import domain.TransitionAssignment
import domain.TransitionAssignmentRegistry
import domain.Vehicle
import domain.VehicleType
import korlibs.image.color.RGBA
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import network.FixedTransitionHandoverHandler
import network.FixedTransitionHandoverResult
import network.VehicleMessageData
import network.VehicleMessageType
import simulation.Simulation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Tests the server-facing adapter that applies fixed transition assignments to arrivals.
 */
class FixedTransitionHandoverHandlerTest {
    private val localField = FieldReference("world-1-field-0-0", column = 0, row = 0)
    private val neighborField = FieldReference("world-1-field-0-1", column = 1, row = 0)
    private val red = Color("Red", RGBA(255, 0, 0, 255))
    private val car = VehicleType("CAR", maxSpeed = 100.0, length = 4.0, adhesionCoefficient = 0.8, acceleration = 3.0)

    /**
     * Verifies that a server arrival uses the fixed transition assignment and injects the vehicle.
     */
    @Test
    fun handle_acceptsArrivalAndInjectsVehicleAtConfiguredSource() {
        val fixture = createFixture()
        val handler = fixture.handlerFor("transition-right")

        val result = handler.handle(vehicleArrival("transition-right", level = 0))

        assertEquals(FixedTransitionHandoverResult.Accepted, result)
        assertEquals(1, fixture.level.vehicles.size)
        with(fixture.level.vehicles.single()) {
            assertSame(fixture.source, sourceNode)
            assertSame(fixture.sourceConfig, sourceNodeConfiguration)
            assertSame(fixture.street, currentStreet)
            assertEquals(12.5, currentSpeed)
        }
        assertEquals(0, fixture.source.vehiclesGenerated)
        assertEquals(1, fixture.sourceConfig.vehiclesSpawned)
    }

    /**
     * Verifies that server-neutral VEHICLE_SOURCE messages are not used as transition handovers.
     */
    @Test
    fun handle_ignoresServerSourceMessages() {
        val fixture = createFixture()
        val handler = fixture.handlerFor("transition-right")

        val result = handler.handle(
            VehicleMessageData(
                messageType = VehicleMessageType.VEHICLE_SOURCE,
                vehicleProperties = carVehicleProperties(),
                sourceNodeId = "server-neutral-source",
                fieldId = localField.fieldId,
                level = 0
            )
        )

        assertEquals(FixedTransitionHandoverResult.IgnoredNonArrival, result)
        assertEquals(0, fixture.level.vehicles.size)
    }

    /**
     * Verifies that arrivals without a configured transition-to-sink binding are discarded.
     */
    @Test
    fun handle_discardsArrivalWithoutTransitionBinding() {
        val fixture = createFixture()
        val handler = fixture.handlerFor("transition-right")

        val result = handler.handle(vehicleArrival("unknown-transition", level = 0))

        assertEquals(FixedTransitionHandoverResult.Discarded, result)
        assertEquals(0, fixture.level.vehicles.size)
    }

    /**
     * Verifies that the level-0-only rule is enforced for real server arrivals.
     */
    @Test
    fun handle_discardsArrivalOnNonZeroLevel() {
        val fixture = createFixture()
        val handler = fixture.handlerFor("transition-right")

        val result = handler.handle(vehicleArrival("transition-right", level = 1))

        assertEquals(FixedTransitionHandoverResult.Discarded, result)
        assertEquals(0, fixture.level.vehicles.size)
    }

    /**
     * Verifies that malformed vehicle properties are discarded instead of using defaults.
     */
    @Test
    fun handle_discardsMalformedVehicleProperties() {
        val fixture = createFixture()
        val handler = fixture.handlerFor("transition-right")

        val result = handler.handle(
            VehicleMessageData(
                messageType = VehicleMessageType.VEHICLE_ARRIVAL,
                vehicleProperties = buildJsonObject { put("type", "CAR") },
                transitionNodeId = "transition-right",
                fromFieldId = neighborField.fieldId,
                toFieldId = localField.fieldId,
                level = 0
            )
        )

        assertEquals(FixedTransitionHandoverResult.Discarded, result)
        assertEquals(0, fixture.level.vehicles.size)
    }

    /**
     * Verifies that incoming handovers obey the same minimum entry distance as local spawning.
     */
    @Test
    fun handle_discardsArrivalWhenSourceEntryIsBlocked() {
        val fixture = createFixture()
        val handler = fixture.handlerFor("transition-right")
        val street = assertNotNull(fixture.street)
        val blockingVehicle = Vehicle(red, car, fixture.source, fixture.sourceConfig, fixture.level).apply {
            currentStreet = street
            distanceAlongSegment = 0.0
        }
        fixture.level.addVehicle(blockingVehicle)

        val result = handler.handle(vehicleArrival("transition-right", level = 0))

        assertEquals(FixedTransitionHandoverResult.Discarded, result)
        assertEquals(listOf(blockingVehicle), fixture.level.vehicles)
        assertEquals(0, fixture.sourceConfig.vehiclesSpawned)
    }

    /**
     * Verifies that accepted assignments without a usable local route are discarded without crashing.
     */
    @Test
    fun handle_discardsArrivalWithoutValidRouteOnTargetField() {
        val fixture = createFixture(connectRoute = false)
        val handler = fixture.handlerFor("transition-right")

        val result = handler.handle(vehicleArrival("transition-right", level = 0))

        assertEquals(FixedTransitionHandoverResult.Discarded, result)
        assertEquals(0, fixture.level.vehicles.size)
        assertEquals(0, fixture.sourceConfig.vehiclesSpawned)
    }

    /**
     * Verifies that neutral server sources are not used as targets for neighbor-table arrivals.
     */
    @Test
    fun handle_discardsArrivalMappedToNeutralServerSource() {
        val fixture = createFixture(sourceKind = SourceNodeKind.SERVER_NEUTRAL)
        val handler = fixture.handlerFor("transition-right")

        val result = handler.handle(vehicleArrival("transition-right", level = 0))

        assertEquals(FixedTransitionHandoverResult.Discarded, result)
        assertEquals(0, fixture.level.vehicles.size)
        assertEquals(0, fixture.sourceConfig.vehiclesSpawned)
    }

    private fun createFixture(
        sourceKind: SourceNodeKind = SourceNodeKind.LOCAL,
        connectRoute: Boolean = true
    ): Fixture {
        val sourceConfig = SourceNodeConfiguration(
            color = red,
            generationSpeed = 9999.0,
            initialVehicleSpeed = 0.0,
            vehicleType = car
        )
        val source = SourceNode(Point(100.0, 100.0), 0, Angle.ZERO, listOf(sourceConfig), sourceKind)
        val localTarget = TargetNode(Point(500.0, 100.0), 0, Angle.ZERO, red)
        val sink = TargetNode(Point(900.0, 100.0), 0, Angle.ZERO, red)
        val level = Level("handover-test", 1)
        val street = if (connectRoute) Street(source, localTarget, listOf(source.position, localTarget.position)) else null
        level.addNode(source)
        level.addNode(localTarget)
        level.addNode(sink)
        if (street != null) level.addStreet(street)

        val registry = TransitionAssignmentRegistry(
            listOf(
                TransitionAssignment(
                    sink = sink,
                    vehicleType = "CAR",
                    fromField = neighborField,
                    toField = localField,
                    targetSource = source
                )
            )
        )

        return Fixture(level, Simulation(level), source, sourceConfig, sink, street, registry)
    }

    private fun Fixture.handlerFor(transitionNodeId: String): FixedTransitionHandoverHandler =
        FixedTransitionHandoverHandler(
            simulation = simulation,
            registry = registry,
            transitionSinkResolver = { candidateTransitionNodeId ->
                if (candidateTransitionNodeId == transitionNodeId) sink else null
            }
        )

    private fun vehicleArrival(transitionNodeId: String, level: Int): VehicleMessageData =
        VehicleMessageData(
            messageType = VehicleMessageType.VEHICLE_ARRIVAL,
            vehicleProperties = carVehicleProperties(),
            transitionNodeId = transitionNodeId,
            fromFieldId = neighborField.fieldId,
            toFieldId = localField.fieldId,
            level = level
        )

    private fun carVehicleProperties() = buildJsonObject {
        put("color", buildJsonObject {
            put("r", red.value.r)
            put("g", red.value.g)
            put("b", red.value.b)
            put("a", red.value.a)
        })
        put("type", "CAR")
        put("speedMetersPerSecond", 12.5)
    }

    /**
     * Shared object graph for one handover test case.
     *
     * @property level level that contains the local source, sink, and route.
     * @property simulation simulation instance receiving accepted handover vehicles.
     * @property source local source used as the configured handover target.
     * @property sourceConfig vehicle configuration attached to [source].
     * @property sink local sink mapped from the incoming server transition.
     * @property street route segment used by accepted injected vehicles, or null when no route exists.
     * @property registry assignment registry configured for the fixture.
     */
    private data class Fixture(
        val level: Level,
        val simulation: Simulation,
        val source: SourceNode,
        val sourceConfig: SourceNodeConfiguration,
        val sink: TargetNode,
        val street: Street?,
        val registry: TransitionAssignmentRegistry
    )
}
