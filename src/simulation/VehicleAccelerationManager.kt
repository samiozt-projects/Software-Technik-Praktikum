package simulation
import domain.*

import korlibs.math.geom.Point
import kotlin.math.sqrt

/**
 * Manages vehicle acceleration in the simulation.
 *
 * @property simulation The simulation instance.
 */
class VehicleAccelerationManager(private val simulation: Simulation) {
    /**
     * Applies the physical acceleration model to every active vehicle.
     *
     * Vehicle types store their maximum speed in km/h because this is the value shown
     * to users and used in the level configuration. The simulation internally works in
     * m/s, so the maximum is converted before it is compared to the accelerated speed.
     *
     * Each inner polyline waypoint imposes a local speed limit
     * `V_max_i = min(V_max, sqrt(µ·g·r))`. The look-ahead in
     * [speedCeilingForCurves] folds those per-waypoint limits into a single
     * *ceiling* on the speed the vehicle may have **right now** so that it can
     * still decelerate to each upcoming `V_max_i` in time. A second ceiling from
     * [speedCeilingForMerge] keeps a yielding vehicle stopped once it is at its stop line
     * (Issue #92); the actual approach movement is clamped by distanceAllowedByMergeStopLine,
     * so a vehicle does not slow down far before the node. Two outcomes:
     *
     *  * `currentSpeed > ceiling` → the vehicle is too fast for what's ahead and
     *    decelerates by the effective acceleration budget times `dt`. `maxOf(ceiling, …)` prevents the
     *    discrete tick from undershooting the ceiling.
     *  * `currentSpeed ≤ ceiling` → the vehicle speeds up by
     *    the effective acceleration budget times `dt`, capped at the ceiling so it never overshoots.
     *
     * The acceleration and braking budget is scaled by the level's effective adhesion modifier, so
     * wet or snowy weather reduces both speeding up and slowing down. Edge case: if the resulting
     * budget is `0`, the speed-change term vanishes and both branches keep `currentSpeed` unchanged,
     * which is physically correct — a vehicle without traction budget can neither speed up nor brake.
     *  
     * @param dt The time delta for this simulation step.
     */
    fun accelerateVehicles(dt: Double) {
        val effectiveAdhesionModifier = simulation.level.effectiveAdhesionCoefficientModifier()
        for (vehicle in simulation.level.vehicles) {
            /** Convert the type limit once per frame so the cap uses the same unit as currentSpeed. */
            val maxSpeedMs = vehicle.type.maxSpeed * Simulation.KMH_TO_MS
            val acceleration = Simulation.effectiveAccelerationBudget(vehicle.type.acceleration, effectiveAdhesionModifier)

            /**
             * Effective ceiling = vehicle max, possibly lowered by an upcoming curve
             * ([speedCeilingForCurves]) or by a merge node the vehicle must yield to
             * ([speedCeilingForMerge]) or by a leading vehicle. The strictest ceiling wins.
             */
            val curveCeilingMs = speedCeilingForCurves(vehicle, maxSpeedMs, acceleration)
            val mergeCeilingMs = speedCeilingForMerge(vehicle, maxSpeedMs, acceleration)
            val leadingCeilingMs = speedCeilingForLeadingVehicle(vehicle, maxSpeedMs, acceleration)
            val ceilingMs = minOf(curveCeilingMs, mergeCeilingMs, leadingCeilingMs)
            val brakingAcceleration =
                if (leadingCeilingMs < maxSpeedMs && leadingCeilingMs <= curveCeilingMs && leadingCeilingMs <= mergeCeilingMs) {
                    acceleration * Simulation.LEADING_VEHICLE_BRAKE_MULTIPLIER
                } else {
                    acceleration
                }

            val mergeFloorMs = mergeStartSpeedFloor(vehicle, ceilingMs)
            val speedBeforeAcceleration = maxOf(vehicle.currentSpeed, mergeFloorMs)

            vehicle.currentSpeed = if (speedBeforeAcceleration > ceilingMs) {
                /** Above the ceiling → brake by the active deceleration budget without undershooting it. */
                maxOf(ceilingMs, speedBeforeAcceleration - brakingAcceleration * dt)
            } else {
                /** Below the ceiling → speed up to it without overshooting it. */
                val accelerationStep = calculateAccelerationStep(mergeFloorMs, vehicle.currentSpeed, acceleration, dt)
                minOf(ceilingMs, speedBeforeAcceleration + accelerationStep)
            }
        }
    }

    /**
     * Calculates the acceleration step for a vehicle based on merge floor conditions.
     *
     * @param mergeFloorMs The minimum speed floor for merge situations
     * @param currentSpeed The vehicle's current speed
     * @param acceleration The vehicle's acceleration capability
     * @param dt The time delta for this simulation step
     * @return The acceleration step to apply, or 0.0 if merge floor conditions prevent acceleration
     */
    private fun calculateAccelerationStep(
        mergeFloorMs: Double,
        currentSpeed: Double,
        acceleration: Double,
        dt: Double
    ): Double {
        return if (mergeFloorMs > currentSpeed) {
            0.0
        } else {
            acceleration * dt
        }
    }

    /**
     * Returns a small start-speed floor for vehicles that have actually stopped at a merge and are
     * allowed to enter now. Spawn speeds remain fully configuration-driven; this only affects the
     * restart from a right-of-way stop line, and it is still capped by the effective speed ceiling.
     * 
     * @param vehicle The vehicle to calculate the merge start speed for.
     * @param ceilingMs The current speed ceiling in m/s.
     * @return The merge start speed floor in m/s.
     */
    private fun mergeStartSpeedFloor(vehicle: Vehicle, ceilingMs: Double): Double {
        if (vehicle.currentSpeed > Simulation.STOPPED_SPEED_EPSILON_MS || ceilingMs <= 0.0) return 0.0
        val node = vehicle.currentStreet?.endNode as? ConnectionNode ?: return 0.0
        if (node.incomingStreets.size < 2) return 0.0
        if (mustYieldAtMerge(vehicle, node)) return 0.0
        return minOf(ceilingMs, Simulation.MERGE_START_SPEED_KMH * Simulation.KMH_TO_MS)
    }

    /**
     * Returns the highest speed (m/s) the vehicle may travel at this tick, so it can
     * still satisfy the curve limit at every upcoming curvature point along its
     * planned route. Two kinds of curvature points contribute:
     *
     *  * **Inner polyline waypoints** — `path[1] … path[size-2]` of each street;
     *    the triangle uses (prev, curr, next) from the street's own polyline.
     *  * **Connection nodes between streets** — the triangle uses the last point
     *    of the current street, the node itself, and the first point of the next
     *    street along the deterministic route.
     *
     * Source and target endpoints carry no curve limit — the global vehicle max applies.
     *
     * Kinematic idea behind the per-waypoint *bound*
     * ------------------------------------------------
     * The classical motion equation `v_end² = v_now² − 2·a·d` (constant effective deceleration `a`,
     * distance `d`) solved for `v_now` says: from speed `v_now` we can brake down to
     * exactly `v_end` over distance `d`. Plugging `v_end = V_max_i` gives the largest
     * speed we may currently have so the vehicle is still at most `V_max_i` once it
     * reaches the waypoint:
     *
     * ```
     * bound(i) = sqrt(V_max_i² + 2·a·d_i)
     * ```
     *
     * Taking `min` over every upcoming curvature point i (and the global `V_max`) 
     * yields the effective speed ceiling for the current tick.
     *
     * Route traversal
     * ---------------
     * The scan follows the vehicle's selected shortest route across connection nodes,
     * so a tight curve at the *start* of the next street can already trigger braking
     * on the current one. Two natural stop conditions bound the work per tick:
     *
     *  * **Distance cap** `V_max² / (2·a)`. Beyond that distance even the harshest
     *    possible limit `V_max_i = 0` yields `bound ≥ V_max`, so no later curvature
     *    point can lower the ceiling further. Acts as an early exit; not an
     *    arbitrary horizon.
     *  * **Cycle protection.** Each visited street is recorded; revisiting one
     *    aborts the scan so cyclic topologies (roundabouts, loops) stay safe.
     *
     * For `acceleration == 0` after level and weather scaling, the cap collapses to 0 and no
     * curvature point is probed — a vehicle that cannot brake has nothing to compute here.
     *  
     * @param vehicle The vehicle to calculate the speed ceiling for.
     * @param maxSpeedMs The maximum speed in m/s.
     * @param acceleration The acceleration in m/s².
     * @return The speed ceiling in m/s.
     */
    private fun speedCeilingForCurves(vehicle: Vehicle, maxSpeedMs: Double, acceleration: Double): Double {
        val startStreet = vehicle.currentStreet ?: return maxSpeedMs

        // A vehicle clamped at the very end of its street has no remaining inner waypoint on this
        // street to brake for here (and indexing one would run past the polyline). Any curve on the
        // next street is handled once the vehicle drives onto it.
        if (vehicle.currentSegmentIndex >= startStreet.path.size - 1) return maxSpeedMs

        var ceiling = maxSpeedMs
        val effectiveAdhesionModifier = simulation.level.effectiveAdhesionCoefficientModifier()
        val maxRelevantDistanceM = calculateMaxRelevantDistance(maxSpeedMs, acceleration)

        // Walking state along the route
        var street: Street = startStreet
        var startSegmentIndex = vehicle.currentSegmentIndex
        var initialOffsetM = vehicle.distanceAlongSegment
        var distanceToWaypointM = 0.0
        val visited = mutableSetOf<Street>()

        while (true) {
            // Cycle guard: stop if we'd re-enter a street we've already scanned this tick.
            if (!visited.add(street)) return ceiling
            val path = street.path
            // Defensive: a well-formed Street has ≥ 2 points; if not, nothing to scan here.
            if (path.size < 2) return ceiling

            // Advance the running distance to the first waypoint we still have to probe
            distanceToWaypointM += StreetUtils.segmentLengthM(path, startSegmentIndex) - initialOffsetM

            // Scan inner waypoints of this street
            ceiling = scanStreetWaypoints(
                street, startSegmentIndex, distanceToWaypointM,
                maxRelevantDistanceM, ceiling, vehicle, effectiveAdhesionModifier, acceleration
            )

            // Distance to street-end already exceeds what could ever matter → stop.
            if (distanceToWaypointM > maxRelevantDistanceM) return ceiling

            // Try to continue into the next street along the deterministic route.
            val nextStreetResult =
                getNextStreet(vehicle, street, ceiling, distanceToWaypointM, effectiveAdhesionModifier, acceleration)
                    ?: return ceiling

            // Update ceiling and reset segment-local state for the new street
            ceiling = nextStreetResult.second
            street = nextStreetResult.first
            startSegmentIndex = 0
            initialOffsetM = 0.0
        }
    }

    /**
     * Calculates the maximum distance beyond which curves cannot affect the speed ceiling.
     * 
     * @param maxSpeedMs The maximum speed in m/s.
     * @param acceleration The acceleration in m/s².
     * @return The maximum distance in meters.
     */
    private fun calculateMaxRelevantDistance(maxSpeedMs: Double, acceleration: Double): Double {
        return if (acceleration > 0.0) (maxSpeedMs * maxSpeedMs) / (2.0 * acceleration) else 0.0
    }

    /**
     * Scans all inner waypoints of a street and updates the speed ceiling.
     *
     * @param street The street to scan.
     * @param startSegmentIndex The starting segment index for scanning.
     * @param distanceToWaypointM The initial distance to the first waypoint.
     * @param maxRelevantDistanceM The maximum distance beyond which curves cannot affect the speed ceiling.
     * @param ceiling The current speed ceiling.
     * @param vehicle The vehicle to calculate the speed ceiling for.
     * @param effectiveAdhesionModifier The effective adhesion modifier.
     * @param acceleration The acceleration in m/s².
     * @return The updated speed ceiling in m/s.
     */
    private fun scanStreetWaypoints(
        street: Street,
        startSegmentIndex: Int,
        distanceToWaypointM: Double,
        maxRelevantDistanceM: Double,
        ceiling: Double,
        vehicle: Vehicle,
        effectiveAdhesionModifier: Double,
        acceleration: Double
    ): Double {
        val path = street.path
        var currentCeiling = ceiling
        var currentDistance = distanceToWaypointM

        // Inner waypoints of this polyline are path[1] … path[size-2]
        var k = startSegmentIndex + 1
        while (k <= path.size - 2) {
            // Early exit: anything further out cannot tighten the ceiling anymore.
            if (currentDistance > maxRelevantDistanceM) return currentCeiling

            val vMaxIMs = CurveSpeed.maxSpeedAtWaypoint(
                path[k - 1], path[k], path[k + 1],
                vehicle.type, effectiveAdhesionModifier
            )
            // bound(i) = sqrt(V_max_i² + 2·a·d_i) — see the function-level KDoc.
            val bound = sqrt(
                vMaxIMs * vMaxIMs + 2.0 * acceleration * currentDistance
            )
            if (bound < currentCeiling) currentCeiling = bound

            // Move on to the next inner waypoint
            currentDistance += StreetUtils.segmentLengthM(path, k)
            k++
        }

        return currentCeiling
    }

    /**
     * Handles the transition to the next street and checks connection node curves.
     *
     * @param vehicle The vehicle to calculate the speed ceiling for.
     * @param street The current street.
     * @param ceiling The current speed ceiling.
     * @param distanceToWaypointM The distance to the next waypoint.
     * @param effectiveAdhesionModifier The effective adhesion modifier.
     * @param acceleration The acceleration in m/s².
     * @return A pair containing the next street and the updated ceiling, or null if the vehicle cannot continue.
     */
    private fun getNextStreet(
        vehicle: Vehicle,
        street: Street,
        ceiling: Double,
        distanceToWaypointM: Double,
        effectiveAdhesionModifier: Double,
        acceleration: Double
    ): Pair<Street, Double>? {
        // Try to continue into the next street along the deterministic route.
        // Stops the scan at TargetNodes (route ends) or dead-end ConnectionNodes.
        val endNode = street.endNode
        if (endNode !is ConnectionNode) return null
        val nextStreet = RouteUtils.nextStreetAfter(vehicle, street) ?: return null

        // Check the connection node curve
        val path = street.path
        val nextPath = nextStreet.path
        var updatedCeiling = ceiling
        if (nextPath.size >= 2) {
            val vMaxConnectionMs = CurveSpeed.maxSpeedAtWaypoint(
                path[path.size - 2], path[path.size - 1], nextPath[1],
                vehicle.type, effectiveAdhesionModifier
            )
            val bound = sqrt(
                vMaxConnectionMs * vMaxConnectionMs +
                        2.0 * acceleration * distanceToWaypointM
            )
            if (bound < updatedCeiling) updatedCeiling = bound
        }

        return Pair(nextStreet, updatedCeiling)
    }

    /**
     * Returns the highest speed (m/s) [vehicle] may travel at this tick with respect to the merge node at the
     * end of its current street (Lastenheft §3.3, Issue #92).
     *
     *  * Not a merge node (fewer than two incoming streets) → no constraint.
     *  * The vehicle holds the crossing (mergeOccupant) → no constraint; it drives across.
     *  * Otherwise the vehicle keeps yielding to the same stop line, but its approach speed is
     *    smoothly bounded by the remaining braking distance. The movement clamp in
     *    [distanceAllowedByMergeStopLine] remains the hard guarantee that it cannot cross the line.
     *  
     * @param vehicle The vehicle to calculate the speed ceiling for.
     * @param maxSpeedMs The maximum speed of the vehicle.
     * @param acceleration The acceleration of the vehicle.
     * @return The speed ceiling for the vehicle.
     */
    private fun speedCeilingForMerge(
        vehicle: Vehicle,
        maxSpeedMs: Double,
        acceleration: Double
    ): Double {
        val node = vehicle.currentStreet?.endNode as? ConnectionNode ?: return maxSpeedMs
        if (!mustYieldAtMerge(vehicle, node)) return maxSpeedMs

        val remainingToStopLineM = VehicleUtils.remainingDistanceToStopLineM(vehicle)
        if (remainingToStopLineM <= Simulation.MERGE_STOP_LINE_EPSILON_M) return 0.0
        if (acceleration <= 0.0) return maxSpeedMs

        return minOf(maxSpeedMs, sqrt(2.0 * acceleration * remainingToStopLineM))
    }

    /**
     * Returns the highest speed (m/s) [vehicle] may travel at this tick so it keeps the [Simulation.MIN_DISTANCE_M] minimum
     * distance to the vehicle ahead of it on the same street (Lastenheft §3.3, Issue #105). Folded
     * into the effective speed ceiling in [accelerateVehicles] alongside the curve and merge limits.
     *
     *  * No leader ahead on the same street, or the leader is still outside
     *    [Simulation.LEADING_VEHICLE_LOOKAHEAD_M] → no smooth-following constraint.
     *  * Already at or inside the 2 m minimum (remaining distance before the minimum ≤ 0) → brake to
     *    the leader's speed, so the follower matches the vehicle ahead instead of driving into it
     *    (AC2).
     *  * Otherwise the same kinematic braking bound as the curve look-ahead applies:
     *    `sqrt(v_lead² + 2·a_follow·d)` with `d` the clear distance still available before the
     *    2 m gap is reached. `a_follow` is the vehicle's acceleration scaled by
     *    [Simulation.LEADING_VEHICLE_BRAKE_MULTIPLIER], so the local look-ahead does not devolve into normal
     *    hard-clamp motion. The hard movement clamp in [distanceAllowedByLeadingVehicle] remains the
     *    final no-overlap guarantee.
     *  
     * @param vehicle The vehicle to calculate the speed ceiling for.
     * @param maxSpeedMs The maximum speed of the vehicle.
     * @param acceleration The acceleration of the vehicle.
     * @return The speed ceiling for the vehicle.
     */
    private fun speedCeilingForLeadingVehicle(vehicle: Vehicle, maxSpeedMs: Double, acceleration: Double): Double {
        val lead = RouteUtils.leadingVehicleOnSameStreet(vehicle) ?: return maxSpeedMs
        val clearGapM = VehicleUtils.clearDistanceToLeaderM(vehicle, lead)
        val distanceBeforeMinimumM = clearGapM - Simulation.MIN_DISTANCE_M
        if (distanceBeforeMinimumM <= 0.0) return minOf(maxSpeedMs, lead.currentSpeed)
        if (clearGapM > Simulation.LEADING_VEHICLE_LOOKAHEAD_M) return maxSpeedMs
        val brakingAcceleration = acceleration * Simulation.LEADING_VEHICLE_BRAKE_MULTIPLIER
        val bound = sqrt(lead.currentSpeed * lead.currentSpeed + 2.0 * brakingAcceleration * distanceBeforeMinimumM)
        return minOf(maxSpeedMs, bound)
    }

    /**
     * Whether [vehicle] must yield before [node].
     *
     * @param vehicle The vehicle to check.
     * @param node The node to check against.
     * @return true if the vehicle must yield, false otherwise.
     */
    fun mustYieldAtMerge(vehicle: Vehicle, node: ConnectionNode): Boolean {
        if (node.incomingStreets.size < 2) return false
        val occupant = simulation.mergeOccupant[node]
        if (occupant != null && !hasClearedMerge(occupant, node)) return true

        val grant = simulation.mergeGrant[node]
        return grant != null && grant !== vehicle
    }

    /**
     * Whether [occupant] has driven clear of the merge [node] so the next approach may be granted.
     * The crossing is still busy while the occupant is on an incoming street of the node. Once it
     * has entered the outgoing street and its rear bumper has moved beyond the node plus the small
     * clearance margin, the next approach may start.
     * 
     * @param occupant The vehicle to check.
     * @param node The node to check against.
     * @return true if the occupant has cleared the merge, false otherwise.
     */
    private fun hasClearedMerge(occupant: Vehicle, node: ConnectionNode): Boolean {
        if (occupant !in simulation.level.vehicles) return true
        val street = occupant.currentStreet ?: return true
        if (street in node.incomingStreets) return false
        if (street in node.outgoingStreets) {
            return VehicleUtils.progressAlongStreetM(occupant) >= VehicleUtils.mergeClearanceDistanceM(occupant)
        }
        return true
    }
}
