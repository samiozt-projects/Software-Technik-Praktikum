package simulation

/**
 * Human-readable classification of traffic flow quality for a simulation run.
 */
enum class TrafficFlowRating {
    /** Most generated vehicles arrived and the network stayed lightly loaded. */
    EFFICIENT,

    /** Vehicles moved through the network without detected overload. */
    STABLE,

    /** The network was congested or heavily loaded. */
    OVERLOADED
}

/**
 * Snapshot of the detailed metrics collected for one simulation run.
 *
 * @property generatedVehicles number of vehicles spawned by all sources.
 * @property arrivedVehicles number of vehicles that reached a target.
 * @property averageSpeedKmh time-weighted average speed of active vehicles.
 * @property simulationDurationSeconds elapsed simulated time.
 * @property roadNetworkLengthMeters total street length used as resource metric.
 * @property trafficFlowRating qualitative traffic-flow assessment.
 */
data class SimulationStatistics(
    val generatedVehicles: Int,
    val arrivedVehicles: Int,
    val averageSpeedKmh: Double,
    val simulationDurationSeconds: Double,
    val roadNetworkLengthMeters: Double,
    val trafficFlowRating: TrafficFlowRating
)
