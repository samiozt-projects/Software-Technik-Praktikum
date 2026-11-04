package domain

import config.ConfigManager
import rendering.GameAreaScale

/**
 * Central configuration for gameplay and simulation balancing values.
 *
 * UI layout constants intentionally stay in their scene or rendering classes. This object only
 * contains values that influence domain behaviour, level defaults, routing, or simulation physics.
 */
object GameSimulationConfig {
    /** Simulation configuration loaded from config file or using defaults */
    private val simulationConfig: ConfigManager.SimulationConfig by lazy {
        ConfigManager.loadSimulationConfig()
    }
    /** Default level-wide adhesion modifier used when a level file does not define one. */
    const val DEFAULT_ADHESION_COEFFICIENT_MODIFIER: Double = 1.0

    /** Default playback speed multiplier stored on newly created levels. */
    const val DEFAULT_LEVEL_SIMULATION_SPEED: Double = 1.0

    /** Default challenge-mode limit for connection nodes. */
    const val DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT: Int = 4

    /** Default number of vehicles a source configuration spawns for completion. */
    const val DEFAULT_VEHICLES_TO_SPAWN: Int = 7

    /** Gets the configured simulation speed from the configuration system. */
    fun getConfiguredSimulationSpeed(): Double = simulationConfig.speed

    /** Gets the configured challenge node limit from the configuration system. */
    fun getConfiguredChallengeNodeLimit(): Int = simulationConfig.challengeNodeLimit

    /** Gets the configured vehicles to spawn from the configuration system. */
    fun getConfiguredVehiclesToSpawn(): Int = simulationConfig.vehiclesToSpawn

    /** Width of the playable level area in screen pixels, used for level validation. */
    const val LEVEL_WIDTH_PX: Double = GameAreaScale.DESK_W_PX.toDouble()

    /** Height of the playable level area in screen pixels, used for level validation. */
    const val LEVEL_HEIGHT_PX: Double = GameAreaScale.DESK_H_PX.toDouble()

    /** Conversion factor from kilometres per hour to metres per second. */
    const val KMH_TO_MS: Double = 1.0 / 3.6

    /** Conversion factor from virtual metres to screen pixels. */
    const val PX_PER_METER: Double = GameAreaScale.M_TO_PX_MULTIPLIER

    /** Standard gravity in m/s² for curve-speed physics. */
    const val GRAVITY_MS2: Double = 9.81

    /** Weight of street density in utilization scoring. */
    const val UTILIZATION_DENSITY_WEIGHT: Double = 0.5

    /** Weight of speed deficit in utilization scoring. */
    const val UTILIZATION_SPEED_WEIGHT: Double = 0.5

    /** Minimum bumper-to-tail gap vehicles must keep on the same street. */
    const val MIN_DISTANCE_M: Double = 2.0

    /** Distance from which same-street following starts to smooth braking. */
    const val LEADING_VEHICLE_LOOKAHEAD_M: Double = 10.0

    /** Multiplier for local same-street following braking. */
    const val LEADING_VEHICLE_BRAKE_MULTIPLIER: Double = 5.0

    /** Rear-bumper clearance past a merge node before the merge is considered clear. */
    const val MERGE_CLEARANCE_M: Double = 0.5

    /** Tolerance for treating a vehicle as being at a merge stop line. */
    const val MERGE_STOP_LINE_EPSILON_M: Double = 0.05

    /** Additional tolerance for stopped-at-merge waiting detection. */
    const val MERGE_WAITING_STOP_LINE_EXTRA_EPSILON_M: Double = 0.01

    /** Front-bumper distance from a merge node where yielding vehicles stop. */
    const val STOP_LINE_FRONT_OFFSET_M: Double = 3.0

    /** Immediate restart speed in km/h after a vehicle receives merge right of way. */
    const val MERGE_START_SPEED_KMH: Double = 10.0

    /** Speed below which a vehicle is considered stopped for merge restart purposes. */
    const val STOPPED_SPEED_EPSILON_MS: Double = 0.1

    /** Utilization from which a route option is considered congested enough to switch routes. */
    const val ROUTE_SWITCH_CONGESTION_THRESHOLD: Double = 0.5

    /** Margin that prevents route switching between nearly identical loads. */
    const val ROUTE_SWITCH_LOAD_EPSILON: Double = 0.0001

    /** Deterministic fixed simulation step used during real-time playback. */
    const val FIXED_PLAYBACK_STEP_SECONDS: Double = 1.0 / 60.0

    /** Floating-point tolerance for draining the fixed-step accumulator. */
    const val FIXED_STEP_EPSILON: Double = 1e-12

    /** Normal playback speed multiplier. */
    const val SIMULATION_SPEED_X1_MULTIPLIER: Double = 1.0

    /** Double playback speed multiplier. */
    const val SIMULATION_SPEED_X2_MULTIPLIER: Double = 2.0

    /** Five-times playback speed multiplier. */
    const val SIMULATION_SPEED_X5_MULTIPLIER: Double = 5.0

    /** Ten-times playback speed multiplier. */
    const val SIMULATION_SPEED_X10_MULTIPLIER: Double = 10.0

    /** Single-street utilization threshold that makes traffic flow overloaded. */
    const val TRAFFIC_FLOW_OVERLOADED_MAX_UTILIZATION_THRESHOLD: Double = 0.85

    /** Average network utilization threshold that makes traffic flow overloaded. */
    const val TRAFFIC_FLOW_OVERLOADED_AVERAGE_UTILIZATION_THRESHOLD: Double = 0.70

    /** Minimum arrival share required for efficient traffic flow. */
    const val TRAFFIC_FLOW_EFFICIENT_ARRIVAL_RATIO_THRESHOLD: Double = 0.90

    /** Maximum average utilization still considered efficient traffic flow. */
    const val TRAFFIC_FLOW_EFFICIENT_AVERAGE_UTILIZATION_THRESHOLD: Double = 0.35

    /** Average speed below which traffic flow is considered overloaded. */
    const val TRAFFIC_FLOW_OVERLOADED_AVERAGE_SPEED_KMH_THRESHOLD: Double = 10.0

    /** Minimum average speed required for efficient traffic flow. */
    const val TRAFFIC_FLOW_EFFICIENT_AVERAGE_SPEED_KMH_THRESHOLD: Double = 30.0

    /** Time in seconds after which a waiting merge vehicle may trigger congestion handling. */
    const val WAIT_TIMEOUT_S: Double = 10.0

    /**
     * How long (simulated seconds) the network-wide average speed must stay below the congestion
     * threshold before congestion is reported. A single vehicle waiting one tick at a merge stop
     * line already pulls the instantaneous average to zero, so without this persistence window the
     * very first yield of the very first vehicle ended the run as "congested" - the value only needs
     * to outlast normal, recoverable stop-and-go phases (a granted merge restart takes well under
     * this), while a genuine gridlock keeps the average low for far longer.
     */
    const val AVERAGE_SPEED_CONGESTION_MIN_DURATION_S: Double = 5.0

    /** Minimum number of junctions needed before roundabout detection can create a circle. */
    const val MIN_ROUNDABOUT_JUNCTIONS: Int = 3

    /**
     * Maximum radius deviation for roundabout junction inliers, in detector pixels. Callers working
     * in virtual metres must pass [ROUNDABOUT_RADIUS_TOLERANCE_METERS] to
     * [RoundaboutGeometry.detect] instead - using this pixel value in metre space would accept
     * junctions up to 80 m off the circle, a third of the whole play area.
     */
    const val ROUNDABOUT_RADIUS_TOLERANCE: Double = 80.0

    /** Maximum radius deviation for roundabout junction inliers, in virtual metres (80 px / 8 px per m). */
    const val ROUNDABOUT_RADIUS_TOLERANCE_METERS: Double = 10.0

    /**
     * Maximum circle radius a detected roundabout may have, in virtual metres. A physical roundabout
     * element on the table is compact (about 20 m in game scale); anything bigger that happens to
     * pass the circle fit is an ordinary street loop or a city-block outline, and forcing ring
     * directions onto it corrupts normal one-way streets.
     */
    const val MAX_ROUNDABOUT_RADIUS_METERS: Double = 30.0

    /**
     * How far (as a fraction of the circle radius) a ring segment's path may leave the fitted
     * circle before the candidate is rejected. A real roundabout built from straight pieces bows
     * inward by 1 - cos(PI/n) of the radius (6 segments: ~13%), while a rectangular city block's
     * edges bow by ~29% - so 0.22 keeps real roundabouts and rejects block outlines.
     */
    const val ROUNDABOUT_MAX_PATH_RADIUS_DEVIATION_FRACTION: Double = 0.22

    /** Ramer-Douglas-Peucker epsilon for simplifying detected street paths. */
    const val PATH_SIMPLIFICATION_EPSILON: Double = 3.0

    /** Maximum number of route options stored per source-target pair. */
    const val MAX_ROUTE_OPTIONS: Int = 10
}
