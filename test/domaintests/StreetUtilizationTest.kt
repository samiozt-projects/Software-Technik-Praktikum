package domaintests

import domain.Color
import domain.ConnectionNode
import domain.Level
import simulation.Simulation
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.TargetNode
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the per-street traffic utilization (Auslastungswert) that [Simulation] recomputes
 * every tick. A street's utilization is normalized to `[0.0, 1.0]` (0.0 = free flow,
 * 1.0 = fully blocked) and combines a density factor (vehicle count vs. street length) with
 * a speed-deficit factor (average speed vs. the theoretical section maximum).
 *
 * The current simulation has no car-following, so a slow average speed comes from the
 * acceleration ramp or curve braking rather than emergent jams — these tests therefore
 * provoke each factor deterministically instead of relying on congestion forming on its own.
 */
class StreetUtilizationTest {

    private val red = Color("Rot", Colors.RED)

    /** Length of a single car in metres, matching the [VehicleType] used in these tests. */
    private val carLengthMeters = 4.0

    /**
     * Single straight street from `source` to `target`. The vehicle spawns at [initialSpeed]
     * km/h and, with `acceleration = 0`, holds it, so the speed-deficit factor is fully
     * controlled by the gap between [initialSpeed] and [maxSpeed].
     * 
     * @param initialSpeed The initial speed of the vehicle in km/h.
     * @param maxSpeed The maximum speed of the vehicle in km/h.
     * @param generationSpeed The generation speed of the vehicle in km/h.
     * @param lengthMeters The length of the street in metres.
     * @return A level with a single straight street and a single vehicle.
     */
    private fun buildStraightLevel(
        initialSpeed: Double,
        maxSpeed: Double,
        generationSpeed: Double = 9999.0,
        lengthMeters: Double = 100.0
    ): Level {
        val level = Level("UtilizationLevel", 1)
        val auto = VehicleType("Auto", maxSpeed, carLengthMeters, 0.7, 0.0)
        val cfg = SourceNodeConfiguration(red, generationSpeed, initialSpeed, auto)
        val source = SourceNode(Point(0, 0), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val target = TargetNode(Point(lengthMeters, 0), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(Point(0, 0), Point(lengthMeters, 0))))
        level.validate()
        return level
    }

    private fun onlyStreet(level: Level): Street = level.streets.single()

    /**
     * Test that a street with no vehicles reports zero utilization.
     */
    @Test
    fun utilization_streetWithoutVehicles_isZero() {
        /**
         * Two streets in series; after the first tick the single vehicle sits on street one,
         * so street two stays empty and must report free flow.
         */
        val level = Level("TwoStreetLevel", 1)
        val auto = VehicleType("Auto", 80.0, carLengthMeters, 0.7, 0.0)
        val cfg = SourceNodeConfiguration(red, 9999.0, 80.0, auto)
        val source = SourceNode(Point(0, 0), 0, Angle.fromDegrees(0.0), listOf(cfg))
        val connection = ConnectionNode(Point(100, 0), 0)
        val target = TargetNode(Point(200, 0), 0, Angle.fromDegrees(0.0), red)
        level.addNode(source)
        level.addNode(connection)
        level.addNode(target)
        val firstStreet = Street(source, connection, listOf(Point(0, 0), Point(100, 0)))
        val secondStreet = Street(connection, target, listOf(Point(100, 0), Point(200, 0)))
        level.addStreet(firstStreet)
        level.addStreet(secondStreet)
        level.validate()

        val sim = Simulation(level)
        sim.tick(0.0)

        assertEquals(1, level.vehicles.size, "exactly one vehicle should have spawned")
        assertEquals(0.0, secondStreet.utilization, "an empty street must report free flow")
        assertTrue(firstStreet.utilization > 0.0, "the occupied street must report some load")
    }

    /**
     * Test that the utilization reflects only density when the vehicle travels at the section maximum speed.
     */
    @Test
    fun utilization_vehicleAtSectionMaxSpeed_reflectsOnlyDensity() {
        /**
         * Initial == max with no acceleration ⇒ the vehicle holds the theoretical section
         * maximum, so the speed-deficit factor is 0 and only density contributes.
         */
        val level = buildStraightLevel(initialSpeed = 80.0, maxSpeed = 80.0)
        val street = onlyStreet(level)
        val sim = Simulation(level)

        sim.tick(0.0)

        // Street is 100 m long; one 4 m car ⇒ density 0.04, speed deficit 0.
        val expectedDensity = carLengthMeters / 100.0
        val expected = Simulation.UTILIZATION_DENSITY_WEIGHT * expectedDensity
        assertEquals(expected, street.utilization, 1e-9)
    }

    /**
     * Test that a stationary vehicle adds its full speed deficit to the utilization.
     */
    @Test
    fun utilization_stationaryVehicle_addsFullSpeedDeficit() {
        /**
         * Initial 0, max 80, no acceleration ⇒ the vehicle never moves while the section
         * could allow 80 km/h, so the speed-deficit factor is its maximum value of 1.0.
         */
        val level = buildStraightLevel(initialSpeed = 0.0, maxSpeed = 80.0)
        val street = onlyStreet(level)
        val sim = Simulation(level)

        sim.tick(0.0)

        val expectedDensity = carLengthMeters / 100.0
        val expected = Simulation.UTILIZATION_DENSITY_WEIGHT * expectedDensity +
            Simulation.UTILIZATION_SPEED_WEIGHT * 1.0
        assertEquals(expected, street.utilization, 1e-9)
    }

    /**
     * Test that the utilization grows with the number of vehicles on the street when the speed deficit is constant.
     */
    @Test
    fun utilization_growsWithVehicleCount_whenSpeedDeficitIsConstant() {
        /**
         * A long street travelled at a low constant speed keeps every spawned vehicle on it,
         * so the density factor — and therefore the utilization — rises with each vehicle
         * while the speed-deficit factor stays fixed at 0 (initial == max).
         */
        val level = buildStraightLevel(
            initialSpeed = 18.0,
            maxSpeed = 18.0,
            generationSpeed = 1.0,
            lengthMeters = 225.0
        )
        val street = onlyStreet(level)
        val sim = Simulation(level)

        sim.tick(0.0)
        assertEquals(1, level.vehicles.size)
        val loadWithOneVehicle = street.utilization

        /**
         * At 5 m/s (18 km/h) a new vehicle may only spawn once the previous one has cleared the 2 m
         * entry gap (Issue #154), so reaching three spaced vehicles on the street now takes a few
         * more ticks than the former overlap-at-spawn behaviour did.
         */
        repeat(5) { sim.tick(1.0) }
        assertEquals(3, level.vehicles.size, "two further vehicles should have spawned with spacing")
        val loadWithThreeVehicles = street.utilization

        assertTrue(
            loadWithThreeVehicles > loadWithOneVehicle,
            "more vehicles on the same street must raise utilization " +
                "(1 ⇒ $loadWithOneVehicle, 3 ⇒ $loadWithThreeVehicles)"
        )
        // 3 cars × 4 m on a 225 m street ⇒ density 0.0533, speed deficit 0.
        val expectedDensity = 3 * carLengthMeters / 225.0
        assertEquals(Simulation.UTILIZATION_DENSITY_WEIGHT * expectedDensity, loadWithThreeVehicles, 1e-9)
    }

    /**
     * Test that the utilization returns to zero after a vehicle reaches the target.
     */
    @Test
    fun utilization_returnsToZero_afterVehicleReachesTarget() {
        /**
         * One vehicle, no further spawns: once it arrives and is removed, the recompute at
         * the end of the same tick must reset the street it occupied back to free flow.
         */
        val level = buildStraightLevel(initialSpeed = 80.0, maxSpeed = 80.0)
        val street = onlyStreet(level)
        val sim = Simulation(level)

        sim.tick(0.0)
        assertTrue(street.utilization > 0.0, "street is loaded while the vehicle travels it")

        // 100 m / (80/3.6 m/s) ≈ 4.5 s; 10 s is more than enough to arrive.
        sim.tick(10.0)

        assertTrue(level.vehicles.isEmpty(), "the vehicle should have reached its target")
        assertEquals(0.0, street.utilization, "an emptied street must return to free flow")
    }

    /**
     * Test that the utilization is always within the normalized range [0.0, 1.0].
     */
    @Test
    fun utilization_isAlwaysWithinNormalizedRange() {
        /**
         * A heavily fed, slow, short street drives density toward its cap and keeps a
         * positive speed deficit; the value must still never leave [0.0, 1.0].
         */
        val level = buildStraightLevel(
            initialSpeed = 5.0,
            maxSpeed = 80.0,
            generationSpeed = 0.5,
            lengthMeters = 50.0
        )
        val street = onlyStreet(level)
        val sim = Simulation(level)

        sim.tick(0.0)
        repeat(20) { sim.tick(0.5) }

        assertTrue(
            street.utilization in 0.0..1.0,
            "utilization must stay normalized, was ${street.utilization}"
        )
    }
}
