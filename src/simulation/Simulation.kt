package simulation
import domain.*

import rendering.GameAreaScale

/**
 * Drives the real-time vehicle simulation on a given [Level].
 *
 * Units
 * -----
 * * `Vehicle.currentSpeed` is stored in metres per second (m/s).
 * * Source configurations and vehicle types use km/h externally; conversion to
 *   m/s happens at spawn time and when capping at maxSpeed.
 * * Street paths and `Vehicle.distanceAlongSegment` are stored in virtual metres. Rendering converts
 *   those metres to screen pixels via [GameAreaScale.virtualMetersToPixels].
 *
 * The class is intentionally pure domain logic — no rendering, no KorGE.
 * 
 * @property level The level to simulate.
 * @property targetHandoverPort optional multi-table handover hook for vehicles reaching targets.
 */
class Simulation(
    val level: Level,
    val targetHandoverPort: VehicleTargetHandoverPort = NoVehicleTargetHandover
) {

    companion object {
        /** Conversion factor km/h → m/s. */
        const val KMH_TO_MS: Double = GameSimulationConfig.KMH_TO_MS

        /** Number of Pixels per Meter */
        const val PX_PER_METER: Double = GameSimulationConfig.PX_PER_METER

        /**
         * Weight of the *density* factor (how many vehicles occupy the street) in the
         * combined street utilization. Together with [UTILIZATION_SPEED_WEIGHT] it sums
         * to 1.0 so the weighted result stays normalized to `[0.0, 1.0]`.
         */
        const val UTILIZATION_DENSITY_WEIGHT: Double = GameSimulationConfig.UTILIZATION_DENSITY_WEIGHT

        /**
         * Weight of the *speed-deficit* factor (how far the vehicles fall short of the
         * theoretical section speed) in the combined street utilization.
         */
        const val UTILIZATION_SPEED_WEIGHT: Double = GameSimulationConfig.UTILIZATION_SPEED_WEIGHT

        /**
         * Minimum clear distance (metres) a vehicle keeps to the rear of the vehicle ahead of it on
         * the **same** street (Lastenheft §3.3, Issue #105). Measured bumper-to-tail between the
         * vehicles' visible front/rear edges, i.e., the center-to-center gap is reduced by half of
         * both [VehicleType.length] values. A follower never advances closer than this and brakes to
         * the leader's speed once it reaches the gap.
         */
        const val MIN_DISTANCE_M: Double = GameSimulationConfig.MIN_DISTANCE_M

        /**
         * Bumper-to-tail distance (metres) within which a follower starts smoothly matching the
         * vehicle ahead. This keeps the simulation arcade-like: the hard [MIN_DISTANCE_M] movement
         * clamp still prevents collisions, but vehicles farther away do not brake just because their
         * full physical stopping distance would be long.
         */
        const val LEADING_VEHICLE_LOOKAHEAD_M: Double = GameSimulationConfig.LEADING_VEHICLE_LOOKAHEAD_M

        /**
         * Arcade braking multiplier for same-street following. The 2 m safety rule should not force
         * an 80 m physical stopping-distance look-ahead, so followers brake more decisively once a
         * leader is inside [LEADING_VEHICLE_LOOKAHEAD_M].
         */
        const val LEADING_VEHICLE_BRAKE_MULTIPLIER: Double = GameSimulationConfig.LEADING_VEHICLE_BRAKE_MULTIPLIER

        /**
         * Additional rear-bumper clearance (metres) beyond a merge node after which the conflict
         * point is considered clear for the next vehicle.
         */
        const val MERGE_CLEARANCE_M: Double = GameSimulationConfig.MERGE_CLEARANCE_M

        /**
         * Lateral safety buffer (metres) added on top of both vehicles' half-lengths for the 2D
         * merge-proximity clamp (distanceAllowedByMergeProximity). Two streets that meet a merge
         * node at a sharp (near-parallel) angle place their queues physically on top of each other
         * near the node, which the per-street 1D following rule cannot see. Keeping the centre-to-
         * centre distance at half of both lengths plus this buffer removes the visible overlap while
         * staying small enough that a normal right-angle merge still rests exactly at its stop line.
         */
        const val MERGE_PROXIMITY_CLEARANCE_M: Double = 0.5

        /**
         * Tolerance (metres) for treating a lead vehicle as waiting at the merge stop line.
         */
        const val MERGE_STOP_LINE_EPSILON_M: Double = GameSimulationConfig.MERGE_STOP_LINE_EPSILON_M

        /**
         * Shared stop-line distance (metres) from the front bumper to the connection node.
         */
        const val STOP_LINE_FRONT_OFFSET_M: Double = GameSimulationConfig.STOP_LINE_FRONT_OFFSET_M

        /**
         * Immediate restart speed (km/h) for a vehicle that has waited at a merge stop line and
         * receives the right of way. This keeps junctions responsive while staying in the requested
         * 5-10 km/h start range.
         */
        const val MERGE_START_SPEED_KMH: Double = GameSimulationConfig.MERGE_START_SPEED_KMH

        /**
         * Speed below which a vehicle is treated as stopped for merge restart purposes.
         */
        const val STOPPED_SPEED_EPSILON_MS: Double = GameSimulationConfig.STOPPED_SPEED_EPSILON_MS

        /**
         * Route load from which a street is treated as congested enough to justify using
         * a longer stored route option. A stationary vehicle on a normal street already
         * reaches roughly this value through the speed-deficit factor.
         */
        const val ROUTE_SWITCH_CONGESTION_THRESHOLD: Double = GameSimulationConfig.ROUTE_SWITCH_CONGESTION_THRESHOLD

        /**
         * Small margin that avoids route flapping when two options have a nearly identical load.
         */
        const val ROUTE_SWITCH_LOAD_EPSILON: Double = GameSimulationConfig.ROUTE_SWITCH_LOAD_EPSILON

        /**
         * Fixed simulation step used by real-time playback.
         *
         * Speed controls run several of these deterministic steps instead of passing one large
         * `dt` into [tick]. That keeps spawn timers, merge waits, braking, and congestion checks
         * independent of the selected playback speed.
         */
        const val FIXED_PLAYBACK_STEP_SECONDS: Double = GameSimulationConfig.FIXED_PLAYBACK_STEP_SECONDS

        /**
         * Floating-point tolerance for draining the fixed-step accumulator.
         */
        private const val FIXED_STEP_EPSILON: Double = GameSimulationConfig.FIXED_STEP_EPSILON

        /** Time to wait before restarting a vehicle at a merge stop line */
        const val WAIT_TIMEOUT_S = GameSimulationConfig.WAIT_TIMEOUT_S

        /**
         * Applies level and weather adhesion to a vehicle's acceleration and braking budget.
         *
         * Dry weather keeps the configured value because its modifier is `1.0`; wet and snowy
         * weather reduce how quickly vehicles can speed up or slow down. Non-finite or negative
         * inputs are treated as `0.0` so malformed level or vehicle data cannot produce invalid
         * speeds.
         *
         * @param vehicleAcceleration configured vehicle acceleration in m/s².
         * @param effectiveAdhesionModifier level modifier after applying weather.
         * @return non-negative acceleration budget in m/s².
         */
        fun effectiveAccelerationBudget(
            vehicleAcceleration: Double,
            effectiveAdhesionModifier: Double
        ): Double {
            val safeAcceleration = if (vehicleAcceleration.isFinite()) {
                vehicleAcceleration.coerceAtLeast(0.0)
            } else {
                0.0
            }
            val safeModifier = if (effectiveAdhesionModifier.isFinite()) {
                effectiveAdhesionModifier.coerceAtLeast(0.0)
            } else {
                0.0
            }
            return safeAcceleration * safeModifier
        }
    }

    /**
     * Round-robin right-of-way pointer for each merge [ConnectionNode]: the index (into the
     * node's `incomingStreets`) of the approach that should be served next. Drives the zipper
     * alternation in updateMergeGrants (Lastenheft §3.3, Issue #92).
     */
    val mergeRightOfWay: MutableMap<ConnectionNode, Int> = mutableMapOf()

    /**
     * Vehicle that currently holds (is crossing) each merge [ConnectionNode], or no entry when the
     * junction is free. Only the occupant may drive across; every other approaching vehicle yields
     * at its stop line. The occupant is released once it has cleared the node (hasClearedMerge),
     * which lets the next approach claim the crossing — see updateMergeGrants (Issue #92).
     */
    val mergeOccupant: MutableMap<ConnectionNode, Vehicle> = mutableMapOf()

    /**
     * Vehicle that has been selected to leave a stop line and enter a free merge next. Unlike
     * [mergeOccupant], this is only assigned to vehicles already waiting at the stop line; far-away
     * vehicles never reserve a merge.
     */
    val mergeGrant: MutableMap<ConnectionNode, Vehicle> = mutableMapOf()

    /**
     * Unprocessed simulated seconds retained between real-time playback frames.
     */
    private var fixedStepAccumulatorSeconds: Double = 0.0

    /** True when a congestion condition was detected during the last tick. */
    var congestionDetected: Boolean = false

    /**
     * True, once all configured vehicles from all source configurations have reached a target.
     */
    var levelCompleted: Boolean = level.completed || SimulationUtils.allConfiguredVehiclesArrived(level)

    /** Managers for the simulation */
    val spawnManager = VehicleSpawnManager(this)
    private val movementManager = VehicleMovementManager(this)
    private val mergeManager = MergeManager(this)
    private val congestionDetector = CongestionDetector(this)
    private val routeManager = RouteManager(this)
    private val statisticsCollector = StatisticsCollector(this)
    private val streetUtilizationManager = StreetUtilizationManager(this)

    /** Initialization of a simulation object */
    init {
        if (levelCompleted) {
            level.completed = true
        }
    }

    /**
     * Drives one simulation step.
     *
     * The order is deliberate: new vehicles are spawned before speed and movement are
     * updated, so a freshly spawned vehicle participates in the same frame. This keeps
     * `tick(0.0)` useful for tests and makes the simulation state deterministic from
     * the very first update.
     * 
     * @param dt The time delta for this simulation step.
     */
    fun tick(dt: Double) {
        val measuredDt = if (dt.isFinite() && dt > 0.0) dt else 0.0
        spawnManager.spawnDueVehicles(dt)
        mergeManager.updateMergeGrants()
        routeManager.rerouteVehiclesAroundCongestion()
        movementManager.accelerateVehicles(dt)
        movementManager.advanceVehicles(dt)
        streetUtilizationManager.updateStreetUtilization()
        congestionDetector.checkForCongestion(dt)
        statisticsCollector.recordStatisticsSample(measuredDt)
    }

    /**
     * Advances real-time playback through deterministic fixed-size simulation steps.
     *
     * The selected playback speed changes how much simulated time is accumulated per rendered
     * frame, but every actual domain update still uses [FIXED_PLAYBACK_STEP_SECONDS]. This makes a
     * repeated run on the same level follow the same update sequence whether it is watched at 1x or
     * 10x. Invalid, infinite, zero, or negative deltas are ignored.
     *
     * @param simulatedSeconds simulated time requested by the scene for this rendered frame.
     */
    fun runPlayback(simulatedSeconds: Double) {
        if (!simulatedSeconds.isFinite() || simulatedSeconds <= 0.0) return

        fixedStepAccumulatorSeconds += simulatedSeconds
        while (fixedStepAccumulatorSeconds + FIXED_STEP_EPSILON >= FIXED_PLAYBACK_STEP_SECONDS) {
            tick(FIXED_PLAYBACK_STEP_SECONDS)
            fixedStepAccumulatorSeconds -= FIXED_PLAYBACK_STEP_SECONDS
        }
        if (fixedStepAccumulatorSeconds < FIXED_STEP_EPSILON) {
            fixedStepAccumulatorSeconds = 0.0
        }
    }

    /**
     * Returns the detailed metrics collected for the current simulation run.
     * 
     * @return The simulation statistics.
     */
    fun statistics(): SimulationStatistics {
        return statisticsCollector.statistics()
    }
}
