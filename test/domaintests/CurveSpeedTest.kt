package domaintests
import domain.CurveSpeed
import simulation.Simulation
import domain.VehicleType
import korlibs.math.geom.Point
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the per-waypoint curve-speed model:
 * `r = abc / (4 A)` and `V_max_i = min(V_max, sqrt(µ · g · r))`.
 *
 * Path coordinates are virtual simulation metres, so the geometric circumradius
 * can be used directly in the physics formula.
 */
class CurveSpeedTest {

    private val carType = VehicleType("Auto", maxSpeed = 80.0, length = 4.0, adhesionCoefficient = 0.7, acceleration = 3.0)

    /**
     * Tests that the local curve radius with collinear points works correctly.
     */
    @Test
    fun localCurveRadius_collinearPoints_isInfinite() {
        val r = CurveSpeed.localCurveRadiusMeters(Point(0, 0), Point(100, 0), Point(200, 0))

        assertTrue(r.isInfinite(), "collinear points must yield an infinite radius (no curvature)")
    }

    /**
     * Tests that the local curve radius works correctly.
     */
    @Test
    fun localCurveRadius_rightAngleTriangle_matchesCircumradius() {
        /**
         * Right-angled triangle with legs a = 40 m, b = 40 m, hypotenuse c = 40√2 m.
         * Circumradius equals hypotenuse / 2 = 20√2 m.
         */
        val r = CurveSpeed.localCurveRadiusMeters(Point(0, 0), Point(40, 0), Point(40, 40))

        val expectedMeters = 20.0 * sqrt(2.0)
        assertEquals(expectedMeters, r, 1e-6)
    }

    /**
     * Tests that the vehicle max in metres per second works correctly.
     */
    @Test
    fun maxSpeedAtWaypoint_straightLine_returnsVehicleMaxInMetresPerSecond() {
        val v = CurveSpeed.maxSpeedAtWaypoint(
            Point(0, 0), Point(100, 0), Point(200, 0),
            carType, adhesionModifier = 1.0
        )

        assertEquals(carType.maxSpeed * Simulation.KMH_TO_MS, v, 1e-9)
    }

    /**
     * Tests that the curve limit below vehicle max works correctly.
     */
    @Test
    fun maxSpeedAtWaypoint_tightCurve_returnsCurveLimitBelowVehicleMax() {
        /**
         * Same right-angled corner used above (r ≈ 28.28 m). With µ = 0.7 and g = 9.81 the
         * curve limit sqrt(µ·g·r) is well below the 80 km/h vehicle max.
         */
        val v = CurveSpeed.maxSpeedAtWaypoint(
            Point(0, 0), Point(40, 0), Point(40, 40),
            carType, adhesionModifier = 1.0
        )

        val rMeters = 20.0 * sqrt(2.0)
        val expected = sqrt(carType.adhesionCoefficient * CurveSpeed.GRAVITY_MS2 * rMeters)
        assertEquals(expected, v, 1e-9)
        assertTrue(v < carType.maxSpeed * Simulation.KMH_TO_MS, "tight curve must clamp the speed below V_max")
    }

    /**
     * A radius tighter than the vehicle's own length cannot come from a real, driveable curve; it is
     * how a camera-detection artifact looks (e.g. the small jump between a SourceNode/TargetNode's
     * configured position and the nearest skeleton pixel actually detected for it - see
     * imageprocessing.StreetDetection.traceSegmentsBetweenJunctions). Such a waypoint must not impose
     * a near-zero speed limit; it should be treated as if it were not a curve at all.
     */
    @Test
    fun maxSpeedAtWaypoint_radiusBelowVehicleLength_ignoresDegenerateCurveAndReturnsVehicleMax() {
        // Tiny zig-zag: legs of 0.5 m each at a sharp angle, well under carType.length (4.0 m).
        val v = CurveSpeed.maxSpeedAtWaypoint(
            Point(0, 0), Point(0.5, 0), Point(0.4, 0.3),
            carType, adhesionModifier = 1.0
        )

        assertEquals(carType.maxSpeed * Simulation.KMH_TO_MS, v, 1e-9, "a sub-vehicle-length radius must be ignored, not clamp speed near zero")
    }

    /**
     * Tests that the level adhesion modifier works correctly.
     */
    @Test
    fun maxSpeedAtWaypoint_appliesLevelAdhesionModifier() {
        val dryLimit = CurveSpeed.maxSpeedAtWaypoint(
            Point(0, 0), Point(40, 0), Point(40, 40),
            carType, adhesionModifier = 1.0
        )
        val slipperyLimit = CurveSpeed.maxSpeedAtWaypoint(
            Point(0, 0), Point(40, 0), Point(40, 40),
            carType, adhesionModifier = 0.25
        )

        // Reducing the caller-provided adhesion modifier must reduce the allowed curve speed.
        assertTrue(slipperyLimit < dryLimit, "lower adhesion modifier must lower the curve limit")
    }
}
