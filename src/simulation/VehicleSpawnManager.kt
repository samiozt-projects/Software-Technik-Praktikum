package simulation
import domain.*

/**
 * Manages the spawning of vehicles in the simulation.
 *
 * @property simulation The simulation instance.
 */
class VehicleSpawnManager(private val simulation: Simulation) {
    /**
     * Time elapsed since the last spawn for each (source, config) pair.
     * Initialized to "due now" so the first tick spawns immediately.
     */
    private val spawnTimers: MutableMap<Pair<SourceNode, SourceNodeConfiguration>, Double> =
        mutableMapOf()

    /**
     * Tracks spawn delays for congestion detection.
     * Maps (source, config) pairs to the time they've been waiting to spawn.
     */
    val spawnDelayTimers: MutableMap<Pair<SourceNode, SourceNodeConfiguration>, Double> =
        mutableMapOf()

    /**
     * (source, config) pairs already logged by [warnIfRoutesDivergeFromValidator], so the diagnostic
     * fires once per run instead of every tick this configuration keeps failing to spawn.
     */
    private val loggedRouteDivergence: MutableSet<Pair<SourceNode, SourceNodeConfiguration>> = mutableSetOf()

    /**
     * Creates vehicles for every source configuration whose timer reached its spawn interval.
     *
     * The timer is tracked per `(source, configuration)` pair because one source can
     * generate different vehicle streams. Every spawned vehicle receives all directed
     * route options to a matching target and starts on the shortest one.
     *
     * @param dt The time delta for this simulation step.
     */
    fun spawnDueVehicles(dt: Double) {
        for (source in simulation.level.nodes.filterIsInstance<SourceNode>()) {
            for (cfg in source.configurations) {
                processSourceConfiguration(source, cfg, dt)
            }
        }
    }

    /**
     * Injects a vehicle that arrived from a neighboring table through the server handover flow.
     *
     * The vehicle starts at the configured local source and uses the same route selection and entry
     * clearance checks as locally spawned vehicles. It updates the source configuration spawn count
     * so the received vehicle participates in spawn progress, but it does not increment the source
     * generation counter because the vehicle was produced by a neighboring table.
     *
     * @param source local source selected by the fixed transition assignment.
     * @param cfg source configuration matching the handover vehicle's color and type.
     * @param speedMetersPerSecond current speed received from the server.
     * @return true when the vehicle was inserted into the simulation, false when no route or no
     * free entry was available.
     */
    fun injectHandoverVehicle(
        source: SourceNode,
        cfg: SourceNodeConfiguration,
        speedMetersPerSecond: Double
    ): Boolean {
        val routeOptions = StreetPathFinder.findDrivingRoutes(simulation.level.streets, source, cfg.color)
        val selectedRouteIndex = initialRouteIndex(routeOptions)
        if (routeOptions.isEmpty() || selectedRouteIndex == -1) return false

        val startStreet = routeOptions[selectedRouteIndex].streets.first()
        if (!spawnEntryFree(startStreet, cfg.vehicleType)) return false

        spawnVehicle(
            source = source,
            cfg = cfg,
            selectedRouteIndex = selectedRouteIndex,
            routeOptions = routeOptions,
            initialSpeedMetersPerSecond = speedMetersPerSecond.coerceAtLeast(0.0),
            countSourceGenerated = false,
            countConfigurationSpawned = true
        )
        return true
    }

    /**
     * Processes a single source configuration to determine if a vehicle should be spawned.
     *
     * @param source The source node
     * @param cfg The source configuration
     * @param dt The time delta for this simulation step
     */
    private fun processSourceConfiguration(source: SourceNode, cfg: SourceNodeConfiguration, dt: Double) {
        if (cfg.vehiclesSpawned >= cfg.vehiclesToSpawn) return

        val key = source to cfg
        val accumulated = (spawnTimers[key] ?: cfg.generationSpeed) + dt

        if (accumulated < cfg.generationSpeed) {
            spawnTimers[key] = accumulated
            // No spawn delay when we haven't reached the generation speed yet
            spawnDelayTimers.remove(key)
            return
        }

        // Routes are only computed once a spawn is actually due. This method runs every tick for
        // every configuration, and a full route search per tick on a large network costs real frame
        // time while its result would be thrown away until the generation timer fires anyway.
        val routeOptions = StreetPathFinder.findDrivingRoutes(simulation.level.streets, source, cfg.color)
        val selectedRouteIndex = initialRouteIndex(routeOptions)
        warnIfRoutesDivergeFromValidator(source, cfg, routeOptions)

        attemptVehicleSpawn(source, cfg, selectedRouteIndex, routeOptions, key, accumulated)
    }

    /**
     * Diagnostic only - does not change spawn behavior.
     *
     * [LevelValidator] (object-reference graph via [Node.outgoingStreets]) and [StreetPathFinder]
     * (its own string-keyed adjacency, rebuilt from [simulation.level.streets] and dropping any street
     * with fewer than two path points) walk two independently built graphs. If they ever disagree,
     * [simulation.level.networkState] can report [NetworkState.COMPLETE] while this configuration
     * still has no usable route, so nothing ever spawns here even though the UI says the network is
     * fine. Logs once per (source, config) so a real occurrence is easy to spot instead of guessed at.
     */
    private fun warnIfRoutesDivergeFromValidator(
        source: SourceNode,
        cfg: SourceNodeConfiguration,
        routeOptions: List<StreetPathFinder.DrivingRoute>
    ) {
        if (routeOptions.isNotEmpty()) return
        if (simulation.level.networkState != NetworkState.COMPLETE) return
        if (!loggedRouteDivergence.add(source to cfg)) return

        println(
            "[Diagnostic] SourceNode at ${source.position} (${cfg.vehicleType.name}, ${cfg.color.name}) has " +
                "no driving route from StreetPathFinder even though the level's networkState is COMPLETE - " +
                "LevelValidator and StreetPathFinder likely disagree about this street graph's reachability."
        )
    }

    /**
     * Attempts to spawn a vehicle if the spawn conditions are met.
     *
     * @param source The source node
     * @param cfg The source configuration
     * @param selectedRouteIndex The index of the selected route
     * @param routeOptions All available route options
     * @param key The timer key for this source/configuration pair
     * @param accumulated The accumulated time since last spawn
     */
    private fun attemptVehicleSpawn(
        source: SourceNode,
        cfg: SourceNodeConfiguration,
        selectedRouteIndex: Int,
        routeOptions: List<StreetPathFinder.DrivingRoute>,
        key: Pair<SourceNode, SourceNodeConfiguration>,
        accumulated: Double
    ) {
        // If no routes are available (incomplete network), don't spawn. A missing route is a
        // structural network problem, not traffic: the start gate only lets a simulation start when
        // every configuration has a route, and the street network cannot change mid-run. Counting
        // this wait as spawn delay used to flag "Verstopfung" on a run where no vehicle ever drove -
        // and re-flag it on every retry - so the delay timer deliberately only tracks spawns blocked
        // by actual traffic at the street entry below.
        if (routeOptions.isEmpty() || selectedRouteIndex == -1) {
            spawnTimers[key] = accumulated
            spawnDelayTimers.remove(key)
            return
        }
        
        val startStreet = routeOptions[selectedRouteIndex].streets.first()
        
        if (!spawnEntryFree(startStreet, cfg.vehicleType)) {
            spawnTimers[key] = accumulated
            // Track the delay time (time beyond the generation speed)
            val delay = accumulated - cfg.generationSpeed
            spawnDelayTimers[key] = delay.coerceAtLeast(0.0)
            return
        }
        
        spawnVehicle(
            source = source,
            cfg = cfg,
            selectedRouteIndex = selectedRouteIndex,
            routeOptions = routeOptions,
            initialSpeedMetersPerSecond = cfg.initialVehicleSpeed * Simulation.KMH_TO_MS,
            countSourceGenerated = true,
            countConfigurationSpawned = true
        )
        spawnTimers[key] = 0.0
        spawnDelayTimers.remove(key)
    }

    /**
     * Spawns a new vehicle and updates the requested source statistics.
     *
     * @param source The source node
     * @param cfg The source configuration
     * @param selectedRouteIndex The index of the selected route
     * @param routeOptions All available route options
     * @param initialSpeedMetersPerSecond Initial speed assigned to the spawned vehicle.
     * @param countSourceGenerated Whether [SourceNode.vehiclesGenerated] should be incremented.
     * @param countConfigurationSpawned Whether [SourceNodeConfiguration.vehiclesSpawned] should be incremented.
     */
    private fun spawnVehicle(
        source: SourceNode,
        cfg: SourceNodeConfiguration,
        selectedRouteIndex: Int,
        routeOptions: List<StreetPathFinder.DrivingRoute>,
        initialSpeedMetersPerSecond: Double,
        countSourceGenerated: Boolean,
        countConfigurationSpawned: Boolean
    ) {
        val selectedRoute = routeOptions[selectedRouteIndex]
        val startStreet = selectedRoute.streets.first()

        val vehicle = Vehicle(cfg.color, cfg.vehicleType, source, cfg, simulation.level).apply {
            drivingRouteOptions = routeOptions
            this.selectedRouteIndex = selectedRouteIndex
            currentRouteStreetIndex = 0
            currentStreet = startStreet
            currentSegmentIndex = 0
            distanceAlongSegment = 0.0
            currentSpeed = initialSpeedMetersPerSecond
        }

        simulation.level.addVehicle(vehicle)
        if (countSourceGenerated) source.vehiclesGenerated++
        if (countConfigurationSpawned) cfg.vehiclesSpawned++
    }

    /**
     * Whether the entry of [street] currently has at least [Simulation.MIN_DISTANCE_M] clear space for a newly
     * spawned vehicle of [vehicleType] (Lastenheft §3.3, Issue #154). A freshly spawned vehicle is
     * placed at travelled progress 0, so it must keep the same bumper-to-tail minimum distance to
     * any vehicle already driving on [street] that an ordinary follower does ([SimulationUtils.clearDistanceToLeaderM]).
     * The binding constraint is the vehicle closest to the street start; when even that one leaves
     * less than the minimum clear gap, the spawn is deferred.
     *
     * @param street The street to check for free entry.
     * @param vehicleType The type of vehicle to spawn.
     * @return True if the entry is free, false otherwise.
     */
    private fun spawnEntryFree(street: Street, vehicleType: VehicleType): Boolean {
        for (other in simulation.level.vehicles) {
            if (other.currentStreet !== street) continue
            /** Clear bumper-to-tail gap from the spawn position (progress 0) to the other's rear. */
            val clearGapM = SimulationUtils.progressAlongStreetM(other) - (other.type.length + vehicleType.length) / 2.0
            if (clearGapM < Simulation.MIN_DISTANCE_M) return false
        }
        return true
    }

    /**
     * Chooses the route a newly spawned vehicle should start on. The first route remains the default
     * shortest path; a longer option is selected only when that shortest route is already congested.
     *
     * @param routes The list of routes to choose from.
     * @return The index of the initial route.
     */
    private fun initialRouteIndex(routes: List<StreetPathFinder.DrivingRoute>): Int {
        if (routes.isEmpty()) return -1
        val selectedLoad = routeCongestion(routes.first().streets)
        if (selectedLoad < Simulation.ROUTE_SWITCH_CONGESTION_THRESHOLD) return 0

        return routes
            .mapIndexed { index, route ->
                RouteSwitchCandidate(
                    routeIndex = index,
                    currentStreetIndex = 0,
                    load = routeCongestion(route.streets),
                    remainingLength = route.length
                )
            }
            .filter { it.routeIndex == 0 || it.load + Simulation.ROUTE_SWITCH_LOAD_EPSILON < selectedLoad }
            .minWithOrNull(routeSwitchOrdering())
            ?.routeIndex ?: 0
    }

    /**
     * Maximum utilization over [streets], or free flow when the route has no remaining street.
     * Calculates the congestion of a route.
     *
     * @param streets The list of streets in the route.
     * @return The congestion of the route.
     */
    private fun routeCongestion(streets: List<Street>): Double =
        streets.maxOfOrNull { it.utilization } ?: 0.0

    /**
     * Sorts route choices by congestion first, then by the remaining Euclidean distance.
     * @return A comparator for sorting route choices.
     */
    private fun routeSwitchOrdering(): Comparator<RouteSwitchCandidate> =
        compareBy<RouteSwitchCandidate> { it.load >= Simulation.ROUTE_SWITCH_CONGESTION_THRESHOLD }
            .thenBy { it.load }
            .thenBy { it.remainingLength }
            .thenBy { it.routeIndex }

    /**
     * Data class for storing route switch candidates.
     * @property routeIndex Index of the route.
     * @property currentStreetIndex Index of the current street.
     * @property load Load of the route.
     * @property remainingLength Remaining length of the route.
     */
    private data class RouteSwitchCandidate(
        val routeIndex: Int,
        val currentStreetIndex: Int,
        val load: Double,
        val remainingLength: Double
    )
}
