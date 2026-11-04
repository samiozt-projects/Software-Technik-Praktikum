package domaintests

import domain.Color
import domain.FieldReference
import domain.HandoverVehicleProperties
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.SourceNodeKind
import domain.TargetNode
import domain.TransitionAssignment
import domain.TransitionAssignmentRegistry
import domain.TransitionAssignmentResult
import domain.VehicleType
import korlibs.image.color.RGBA
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests fixed multi-table transition assignments from local sinks to neighbor-table sources.
 */
class TransitionAssignmentTest {
    private val localField = FieldReference("world-1-field-0-0", column = 0, row = 0)
    private val rightNeighborField = FieldReference("world-1-field-0-1", column = 1, row = 0)
    private val diagonalField = FieldReference("world-1-field-1-1", column = 1, row = 1)
    private val red = Color("Red", RGBA(255, 0, 0, 255))
    private val blue = Color("Blue", RGBA(0, 0, 255, 255))
    private val car = VehicleType("CAR", 50.0, 4.0, 0.8, 3.0)
    private val truck = VehicleType("TRUCK", 30.0, 8.0, 0.7, 2.0)

    /**
     * Verifies that a fixed assignment is accepted and applied to the configured target source.
     */
    @Test
    fun applyIncomingVehicle_usesConfiguredSourceForSinkAndVehicleType() {
        val sink = sinkAt(900.0, 420.0, red)
        val mappedSource = sourceAt(
            120.0,
            760.0,
            SourceNodeConfiguration(red, generationSpeed = 1.0, initialVehicleSpeed = 50.0, vehicleType = car)
        )
        val registry = registryForCar(sink, mappedSource)

        val result = registry.applyIncomingVehicle(sink, level = 0, vehicle = redCar())

        assertTrue(result is TransitionAssignmentResult.Accepted)
        assertSame(mappedSource, result.source)
        assertSame(mappedSource.configurations.single(), result.configuration)
    }

    /**
     * Verifies deterministic selection when several sources have the same color and vehicle type.
     */
    @Test
    fun applyIncomingVehicle_keepsConfiguredSourceWhenSeveralSourcesMatch() {
        val sink = sinkAt(100.0, 200.0, red)
        sourceAt(
            10.0,
            10.0,
            SourceNodeConfiguration(red, generationSpeed = 1.0, initialVehicleSpeed = 50.0, vehicleType = car)
        )
        val mappedSource = sourceAt(
            1500.0,
            800.0,
            SourceNodeConfiguration(red, generationSpeed = 1.0, initialVehicleSpeed = 50.0, vehicleType = car)
        )
        val registry = registryForCar(sink, mappedSource)

        val result = registry.applyIncomingVehicle(sink, level = 0, vehicle = redCar())

        assertTrue(result is TransitionAssignmentResult.Accepted)
        assertSame(mappedSource, result.source)
    }

    /**
     * Verifies that handovers on bridge levels are discarded.
     */
    @Test
    fun applyIncomingVehicle_discardsVehicleWhenTransitionLevelIsNotZero() {
        val sink = sinkAt(900.0, 420.0, red)
        val mappedSource = sourceAt(
            120.0,
            760.0,
            SourceNodeConfiguration(red, generationSpeed = 1.0, initialVehicleSpeed = 50.0, vehicleType = car)
        )
        val registry = registryForCar(sink, mappedSource)

        val result = registry.applyIncomingVehicle(sink, level = 1, vehicle = redCar())

        assertSame(TransitionAssignmentResult.Discarded, result)
    }

    /**
     * Verifies that vehicles without a fixed assignment are discarded.
     */
    @Test
    fun applyIncomingVehicle_discardsVehicleWithoutAssignment() {
        val configuredSink = sinkAt(900.0, 420.0, red)
        val otherSink = sinkAt(50.0, 50.0, red)
        val mappedSource = sourceAt(
            120.0,
            760.0,
            SourceNodeConfiguration(red, generationSpeed = 1.0, initialVehicleSpeed = 50.0, vehicleType = car)
        )
        val registry = registryForCar(configuredSink, mappedSource)

        val result = registry.applyIncomingVehicle(otherSink, level = 0, vehicle = redCar())

        assertSame(TransitionAssignmentResult.Discarded, result)
    }

    /**
     * Verifies that assignments can only target directly neighboring fields.
     */
    @Test
    fun transitionAssignment_rejectsNonNeighborFields() {
        val sink = sinkAt(900.0, 420.0, red)
        val mappedSource = sourceAt(
            120.0,
            760.0,
            SourceNodeConfiguration(red, generationSpeed = 1.0, initialVehicleSpeed = 50.0, vehicleType = car)
        )

        assertFailsWith<IllegalArgumentException> {
            TransitionAssignment(
                sink = sink,
                vehicleType = "CAR",
                fromField = localField,
                toField = diagonalField,
                targetSource = mappedSource
            )
        }
    }

    /**
     * Verifies that duplicate sink and vehicle-type assignments are rejected as ambiguous.
     */
    @Test
    fun transitionAssignmentRegistry_rejectsDuplicateSinkAndVehicleTypeAssignments() {
        val sink = sinkAt(900.0, 420.0, red)
        val firstSource = sourceAt(
            120.0,
            760.0,
            SourceNodeConfiguration(red, generationSpeed = 1.0, initialVehicleSpeed = 50.0, vehicleType = car)
        )
        val secondSource = sourceAt(
            333.0,
            222.0,
            SourceNodeConfiguration(red, generationSpeed = 1.0, initialVehicleSpeed = 50.0, vehicleType = car)
        )

        assertFailsWith<IllegalArgumentException> {
            TransitionAssignmentRegistry(
                listOf(
                    transitionAssignment(sink, "CAR", firstSource),
                    transitionAssignment(sink, "car", secondSource)
                )
            )
        }
    }

    /**
     * Verifies that neutral server sources are never selected as handover targets.
     */
    @Test
    fun applyIncomingVehicle_discardsVehicleForNeutralServerSource() {
        val sink = sinkAt(900.0, 420.0, red)
        val serverSource = sourceAt(
            120.0,
            760.0,
            SourceNodeConfiguration(red, generationSpeed = 1.0, initialVehicleSpeed = 50.0, vehicleType = car),
            kind = SourceNodeKind.SERVER_NEUTRAL
        )
        val registry = registryForCar(sink, serverSource)

        val result = registry.applyIncomingVehicle(sink, level = 0, vehicle = redCar())

        assertSame(TransitionAssignmentResult.Discarded, result)
    }

    /**
     * Verifies that source and sink positions do not influence assignment lookup.
     */
    @Test
    fun applyIncomingVehicle_usesAssignmentIndependentOfNodePositions() {
        val sinkAwayFromEdge = sinkAt(733.0, 417.0, red)
        val sourceAwayFromEdge = sourceAt(
            333.0,
            222.0,
            SourceNodeConfiguration(red, generationSpeed = 1.0, initialVehicleSpeed = 50.0, vehicleType = car)
        )
        val registry = registryForCar(sinkAwayFromEdge, sourceAwayFromEdge)

        val result = registry.applyIncomingVehicle(sinkAwayFromEdge, level = 0, vehicle = redCar())

        assertTrue(result is TransitionAssignmentResult.Accepted)
        assertSame(sourceAwayFromEdge, result.source)
    }

    /**
     * Verifies that a configured source must still match both vehicle color and vehicle type.
     */
    @Test
    fun applyIncomingVehicle_discardsVehicleWhenConfiguredSourceDoesNotMatchProperties() {
        val sink = sinkAt(900.0, 420.0, red)
        val mappedSource = sourceAt(
            120.0,
            760.0,
            SourceNodeConfiguration(blue, generationSpeed = 1.0, initialVehicleSpeed = 30.0, vehicleType = truck)
        )
        val registry = registryForCar(sink, mappedSource)

        val result = registry.applyIncomingVehicle(sink, level = 0, vehicle = redCar())

        assertSame(TransitionAssignmentResult.Discarded, result)
    }

    private fun registryForCar(
        sink: TargetNode,
        mappedSource: SourceNode
    ): TransitionAssignmentRegistry = TransitionAssignmentRegistry(
        listOf(
            transitionAssignment(sink, "CAR", mappedSource)
        )
    )

    private fun transitionAssignment(
        sink: TargetNode,
        vehicleType: String,
        mappedSource: SourceNode
    ): TransitionAssignment = TransitionAssignment(
        sink = sink,
        vehicleType = vehicleType,
        fromField = localField,
        toField = rightNeighborField,
        targetSource = mappedSource
    )

    private fun redCar(): HandoverVehicleProperties =
        HandoverVehicleProperties(red.value, vehicleType = "CAR", speedMetersPerSecond = 12.5)

    private fun sinkAt(x: Double, y: Double, color: Color): TargetNode =
        TargetNode(Point(x, y), elevation = 0, orientation = Angle.ZERO, color = color)

    private fun sourceAt(
        x: Double,
        y: Double,
        configuration: SourceNodeConfiguration,
        kind: SourceNodeKind = SourceNodeKind.LOCAL
    ): SourceNode =
        SourceNode(Point(x, y), elevation = 0, orientation = Angle.ZERO, listOf(configuration), kind)
}
