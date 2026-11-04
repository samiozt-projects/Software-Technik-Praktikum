package domain

import korlibs.math.geom.Point
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Curve-radius-based speed limits for vehicles travelling along polyline streets.
 *
 * For every inner waypoint of a polyline the local curve radius is the
 * circumradius of the triangle spanned by its predecessor, the waypoint itself
 * and its successor:
 *
 * ```
 * r = (a * b * c) / (4 * A)
 * ```
 *
 * with `a`, `b`, `c` being the triangle side lengths and `A` its area. The
 * resulting speed limit at that waypoint is
 *
 * ```
 * V_max_i = min(V_max, sqrt(µ * g * r))
 * ```
 *
 * where `µ` is the effective adhesion (`VehicleType.adhesionCoefficient` times
 * the caller-provided adhesion modifier) and `g` is the standard gravity.
 * Polyline points are expressed in the same virtual metres as level nodes and street paths.
 *
 * Callers should pass [Level.effectiveAdhesionCoefficientModifier] so weather and the level's
 * base adhesion tuning both influence the resulting curve limit.
 */
object CurveSpeed {

    /** Standard gravity in m/s², used in the curve-speed formula. */
    const val GRAVITY_MS2: Double = GameSimulationConfig.GRAVITY_MS2

    /**
     * Local curve radius in metres at the triangle defined by [prev], [curr] and [next].
     *
     * Geometric idea: the polyline corner at [curr] is approximated by the
     * circumscribed circle through its predecessor, itself and its successor.
     * That circle's radius is given by `r = (a·b·c) / (4·A)` with `a`, `b`,
     * `c` the triangle's side lengths and `A` its area.
     *
     * Notes for readers:
     *  * Coordinates arrive in virtual metres. Because every length on top and bottom of the
     *    fraction is in metres, the raw result is already in metres.
     *  * Three collinear points form a degenerate triangle with zero area. The
     *    "circle" through them is a straight line (infinite radius). We return
     *    [Double.POSITIVE_INFINITY] so callers can use the result directly in
     *    `sqrt(µ·g·r)` without a special case.
     * @param prev Previous point in the polyline.
     * @param curr Current point in the polyline.
     * @param next Next point in the polyline.
     * @return Local curve radius in metres.
     */
    fun localCurveRadiusMeters(prev: Point, curr: Point, next: Point): Double {
        // Pull out the x/y components once so the area formula below stays readable.
        val prevX = prev.x
        val prevY = prev.y
        val currX = curr.x
        val currY = curr.y
        val nextX = next.x
        val nextY = next.y

        // Triangle side lengths (a = prev→curr, b = curr→next, c = prev→next, the long one).
        val a = hypot(currX - prevX, currY - prevY)
        val b = hypot(nextX - currX, nextY - currY)
        val c = hypot(nextX - prevX, nextY - prevY)

        // Triangle area via the shoelace cross-product: twice the signed area is the
        // 2D cross of (curr - prev) and (next - prev); abs/2 yields the unsigned area.
        val area = abs((currX - prevX) * (nextY - prevY) - (nextX - prevX) * (currY - prevY)) / 2.0
        if (area == 0.0) return Double.POSITIVE_INFINITY

        return (a * b * c) / (4.0 * area)
    }

    /**
     * Maximum vehicle speed in m/s at an inner polyline waypoint, taking both
     * the vehicle's global `maxSpeed` and the local curve limit into account.
     *
     * The effective adhesion `µ` is the product of the vehicle-type's
     * `adhesionCoefficient` and the adhesion modifier passed by the caller.
     * Callers should pass [Level.effectiveAdhesionCoefficientModifier], so the level-wide base
     * modifier and the current weather condition are both included. With `g = 9.81 m/s²` the curve
     * limit is `sqrt(µ · g · r)`; the global vehicle max acts as an upper cap so on a wide-enough
     * curve we just return `V_max`.
     * 
     * @param prev Previous point in the polyline.
     * @param curr Current point in the polyline.
     * @param next Next point in the polyline.
     * @param vehicleType The type of the vehicle.
     * @param adhesionModifier The adhesion modifier.
     * @return Maximum vehicle speed in m/s at the current waypoint.
     */
    fun maxSpeedAtWaypoint(
        prev: Point,
        curr: Point,
        next: Point,
        vehicleType: VehicleType,
        adhesionModifier: Double
    ): Double {
        // Vehicle types store max speed in km/h for human readability; the
        // simulation works in m/s, so convert before comparing or returning.
        val vMaxMs = vehicleType.maxSpeed * simulation.Simulation.KMH_TO_MS

        val rMeters = localCurveRadiusMeters(prev, curr, next)
        // Straight section ⇒ no curve constraint; the global vehicle max applies.
        if (rMeters.isInfinite()) return vMaxMs

        // A real, intentional turn cannot be tighter than the vehicle taking it. A radius below the
        // vehicle's own length only ever comes from camera-detection noise (e.g. a few stray skeleton
        // pixels), never from an actual street shape, so it is ignored here rather than imposing a
        // near-zero speed limit derived from it.
        if (rMeters < vehicleType.length) return vMaxMs

        val mu = vehicleType.adhesionCoefficient * adhesionModifier
        val curveLimitMs = sqrt(mu * GRAVITY_MS2 * rMeters)
        return minOf(vMaxMs, curveLimitMs)
    }
}
