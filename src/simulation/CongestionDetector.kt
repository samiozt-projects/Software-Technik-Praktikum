package simulation
import domain.*

/**
 * Detects congestion in the simulation.
 *
 * @property simulation The simulation instance.
 */
class CongestionDetector(private val simulation: Simulation) {
    /**
     * Tracks how long a vehicle has been effectively stopped at a merge stop line.
     * Used to detect long waits that indicate local deadlocks / jams.
     */
    private val stoppedAtMergeTimers: MutableMap<Vehicle, Double> = mutableMapOf()

    /**
     * Reference to the spawn manager to access spawn delay timers.
     */
    private val spawnManager = simulation.spawnManager

    /**
     * How long (simulated seconds) the network-wide average speed has continuously been below the
     * congestion threshold. A momentary standstill (e.g. the only vehicle yielding one tick at a
     * merge stop line) must not end the run, so the average-speed criterion only fires once this
     * exceeds [GameSimulationConfig.AVERAGE_SPEED_CONGESTION_MIN_DURATION_S].
     */
    private var lowAverageSpeedSeconds: Double = 0.0

    /**
     * Detects congestion using three criteria:
     *  - Network-wide average speed staying far below the configured initial speeds for a sustained duration
     *  - Vehicles waiting too long at a merge stop line
     *  - Spawn delays exceeding generation speed thresholds
     *
     * Sets congestionDetected when a condition fires.
     *
     * @param dt The time delta for this simulation step.
     */
    fun checkForCongestion(dt: Double) {
        simulation.congestionDetected = false

        // Check each congestion condition
        checkAverageSpeedCongestion(dt)
        checkMergeStopLineCongestion(dt)
        checkSpawnDelayCongestion()

        // Clean up timers for removed vehicles
        cleanupRemovedVehicleTimers()
    }

    /**
     * Checks if the network-wide average speed has stayed below 10% of the initial average speed
     * for at least [GameSimulationConfig.AVERAGE_SPEED_CONGESTION_MIN_DURATION_S] of continuous
     * simulated time. Any tick above the threshold (or an empty network) resets the window.
     *
     * @param dt The time delta for this simulation step.
     */
    private fun checkAverageSpeedCongestion(dt: Double) {
        val vehicles = simulation.level.vehicles
        if (vehicles.isEmpty()) {
            lowAverageSpeedSeconds = 0.0
            return
        }

        val avgCurrentSpeed = vehicles.sumOf { it.currentSpeed } / vehicles.size
        val avgInitialSpeed = vehicles.sumOf { it.sourceNodeConfiguration.initialVehicleSpeed * Simulation.KMH_TO_MS } / vehicles.size
        if (avgInitialSpeed > 0.0 && avgCurrentSpeed < 0.1 * avgInitialSpeed) {
            lowAverageSpeedSeconds += dt
            if (lowAverageSpeedSeconds >= GameSimulationConfig.AVERAGE_SPEED_CONGESTION_MIN_DURATION_S) {
                simulation.congestionDetected = true
                println(
                    "Congestion detected at average speed: $avgCurrentSpeed m/s for $avgInitialSpeed m/s " +
                        "threshold (low for ${lowAverageSpeedSeconds}s)"
                )
            }
        } else {
            lowAverageSpeedSeconds = 0.0
        }
    }

    /**
     * Checks if any vehicle has been waiting too long at a merge stop line.
     * Updates timers for vehicles stopped at merge stop lines.
     *
     * @param dt The time delta for this simulation step.
     */
    private fun checkMergeStopLineCongestion(dt: Double) {
        for (vehicle in simulation.level.vehicles) {
            val atMergeStopLine = isVehicleAtMergeStopLine(vehicle)
            
            if (atMergeStopLine) {
                updateMergeStopTimer(vehicle, dt)
                if (hasExceededWaitTimeout(vehicle)) {
                    simulation.congestionDetected = true
                    println("Congestion detected at merge stop line for vehicle $vehicle")
                    break
                }
            } else {
                stoppedAtMergeTimers.remove(vehicle)
            }
        }
    }

    /**
     * Determines if a vehicle is at a merge stop line.
     */
    private fun isVehicleAtMergeStopLine(vehicle: Vehicle): Boolean {
        return (vehicle.currentSpeed <= Simulation.STOPPED_SPEED_EPSILON_MS) &&
                (vehicle.currentStreet?.endNode is ConnectionNode) &&
                (SimulationUtils.remainingDistanceToStopLineM(vehicle) <=
                    Simulation.MERGE_STOP_LINE_EPSILON_M + GameSimulationConfig.MERGE_WAITING_STOP_LINE_EXTRA_EPSILON_M)
    }

    /**
     * Updates the timer for a vehicle waiting at a merge stop line.
     */
    private fun updateMergeStopTimer(vehicle: Vehicle, dt: Double) {
        val currentTime = stoppedAtMergeTimers[vehicle] ?: 0.0
        stoppedAtMergeTimers[vehicle] = currentTime + dt
    }

    /**
     * Checks if a vehicle has exceeded the wait timeout.
     */
    private fun hasExceededWaitTimeout(vehicle: Vehicle): Boolean {
        return (stoppedAtMergeTimers[vehicle] ?: 0.0) >= Simulation.WAIT_TIMEOUT_S
    }

    /**
     * Checks if any source configuration has been waiting to spawn a vehicle
     * for more than its generation speed threshold.
     */
    private fun checkSpawnDelayCongestion() {
        if (simulation.congestionDetected) return

        for (source in simulation.level.nodes.filterIsInstance<SourceNode>()) {
            for (cfg in source.configurations) {
                if (cfg.vehiclesSpawned >= cfg.vehiclesToSpawn) continue

                val key = source to cfg
                val accumulatedTime = spawnManager.spawnDelayTimers[key] ?: 0.0
                
                // Congestion, when spawning of a vehicle is delayed for more than twice the supposed generation time
                if (accumulatedTime > (2 * cfg.generationSpeed)) {
                    simulation.congestionDetected = true
                    println("Congestion detected at source ${source.position} (${cfg.vehicleType.name} ${cfg.color.name}): " +
                            "${accumulatedTime}s delay for ${2 * cfg.generationSpeed}s threshold")
                    break
                }
            }
            if (simulation.congestionDetected) break
        }
    }

    /**
     * Cleans up timers for vehicles that have been removed from the simulation.
     */
    private fun cleanupRemovedVehicleTimers() {
        val removed = stoppedAtMergeTimers.keys.filter { it !in simulation.level.vehicles }
        for (v in removed) stoppedAtMergeTimers.remove(v)
    }
}
