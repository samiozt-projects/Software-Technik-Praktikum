package domain

/**
 * Represents a level in the simulation, containing the street network.
 *
 * @property name The name of the level.
 * @property number The level number.
 * @property adhesionCoefficientModifier A global multiplier for the adhesion coefficient.
 * @property simulationSpeed A multiplier for the simulation speed.
 * @property weatherCondition The weather condition this level starts with.
 * @property challengeConnectionNodeLimit Maximum connection nodes allowed in challenge mode.
 * @property completed Whether all configured vehicles have reached a matching target.
 * @property networkState The current state of the street network's connectivity.
 * @property nodes The list of nodes in the level.
 * @property streets The list of streets in the level.
 * @property vehicles The list of active vehicles in the level.
 * @property vehicleTypes The list of the vehicle types in the level.
 */
class Level(
    val name: String,
    val number: Int,
    var adhesionCoefficientModifier: Double = GameSimulationConfig.DEFAULT_ADHESION_COEFFICIENT_MODIFIER,
    var simulationSpeed: Double = GameSimulationConfig.DEFAULT_LEVEL_SIMULATION_SPEED,
    var weatherCondition: WeatherCondition = WeatherCondition.DEFAULT,
    var challengeConnectionNodeLimit: Int = GameSimulationConfig.getConfiguredChallengeNodeLimit(),
    var completed: Boolean = false,
    var networkState: NetworkState = NetworkState.EMPTY
) {
    companion object {
        /** Default challenge limit for levels that do not define their own limit. */
        const val DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT = GameSimulationConfig.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT

        /** The total width of the game area in pixels */
        const val WIDTH: Double = GameSimulationConfig.LEVEL_WIDTH_PX
        /** The total height of the game area in pixels */
        const val HEIGHT: Double = GameSimulationConfig.LEVEL_HEIGHT_PX
    }

    // created so it can be accessed from the UI and tested.
    val vehicleTypes: List<VehicleType>
        get() = nodes.filterIsInstance<SourceNode>()
            .flatMap { it.configurations }
            .map { it.vehicleType }
            .distinctBy { it.name }


    private val _nodes: MutableList<Node> = mutableListOf()
    private val _streets: MutableList<Street> = mutableListOf()
    private val _vehicles: MutableList<Vehicle> = mutableListOf()

    val nodes: List<Node> get() = _nodes
    val streets: List<Street> get() = _streets
    val vehicles: List<Vehicle> get() = _vehicles

    /**
     * Returns the level-wide adhesion modifier after applying the current weather condition.
     *
     * [adhesionCoefficientModifier] remains the base level tuning knob. Weather multiplies that
     * base value through [WeatherCondition.adhesionMultiplier]. Non-finite or negative base values
     * are treated as `0.0` so malformed level data cannot produce invalid curve, acceleration, or
     * braking behavior.
     *
     * @return effective non-negative adhesion modifier used by simulation physics.
     */
    fun effectiveAdhesionCoefficientModifier(): Double {
        val safeBaseModifier = if (adhesionCoefficientModifier.isFinite()) {
            adhesionCoefficientModifier.coerceAtLeast(0.0)
        } else {
            0.0
        }
        return safeBaseModifier * weatherCondition.adhesionMultiplier
    }

    /** Most recent validation result, reused by the scene to render missing-connection markers. */
    var validationErrors: List<ValidationError> = emptyList()
        private set

    /**
     * Unique level-space positions where construction markers may be shown.
     *
     * Markers stay hidden while no streets exist so an untouched level does not immediately show
     * missing-path warnings before the player starts building.
     */
    val constructionMarkerPositions: Set<korlibs.math.geom.Point>
        get() = if (streets.isEmpty()) {
            emptySet()
        } else {
            validationErrors.flatMap { it.constructionPositions }.toSet()
        }

    /**
     * Unique nodes used by the scene to place construction markers with node-specific offsets.
     *
     * Source and target markers need their node orientation, so the UI consumes nodes instead of
     * plain positions when rendering the construction symbol.
     */
    val constructionMarkerNodes: Set<Node>
        get() = if (streets.isEmpty()) {
            emptySet()
        } else {
            validationErrors.flatMap { it.constructionNodes }.toSet()
        }

    /**
     * Validates the level's network integrity and updates its networkState.
     *
     * @return A list of [ValidationError] objects. If empty, the level is valid.
     */
    fun validate(): List<ValidationError> {
        val validator = LevelValidator(this)
        val errors = validator.validate()

        // Update network state based on validation results
        updateNetworkState(errors)

        validationErrors = errors
        return validationErrors
    }

    /** Updates the network state based on validation errors.
     * 
     * @param errors The list of validation errors to update the network state based on.
     */
    private fun updateNetworkState(errors: List<ValidationError>) {
        networkState = when {
            errors.isEmpty() -> NetworkState.COMPLETE
            streets.isEmpty() -> NetworkState.EMPTY
            else -> NetworkState.INCOMPLETE
        }
    }

    /** Adds a node to the level. */
    fun addNode(node: Node) {
        if (!_nodes.contains(node)) {
            _nodes.add(node)
        }
    }

    /** Adds a street to the level. */
    fun addStreet(street: Street) {
        if (!_streets.contains(street)) {
            _streets.add(street)
        }
    }

    /**
     * Removes a node from the level and deletes all streets connected to it.
     *
     * @param node Node to remove.
     * @return true when the node existed in this level.
     */
    fun removeNode(node: Node): Boolean {
        if (node !in _nodes) return false

        (node.incomingStreets + node.outgoingStreets).toSet().forEach(::removeStreet)
        return _nodes.remove(node)
    }

    /**
     * Removes a street from the level and unlinks it from its nodes. If the connected nodes are
     * ConnectionNodes and have no other streets, they are automatically removed.
     */
    fun removeStreet(street: Street) {
        if (_streets.remove(street)) {
            val start = street.startNode
            val end = street.endNode

            street.delete()

            if (start is ConnectionNode && start.outgoingStreets.isEmpty() && start.incomingStreets.isEmpty()) {
                _nodes.remove(start)
            }
            if (end is ConnectionNode && end.outgoingStreets.isEmpty() && end.incomingStreets.isEmpty()) {
                _nodes.remove(end)
            }
        }
    }

    /**
     * Removes the complete constructed street network while keeping the level's fixed nodes and
     * source configurations intact.
     *
     * This is used before camera re-detection and when retrying after congestion, so stale detected
     * roads cannot make the next run start from the previous network.
     */
    fun clearStreetNetwork() {
        _streets.toList().forEach(::removeStreet)
        _nodes.removeAll { node ->
            node is ConnectionNode && node.incomingStreets.isEmpty() && node.outgoingStreets.isEmpty()
        }
        validationErrors = emptyList()
        networkState = NetworkState.EMPTY
    }

    /** Adds a vehicle to the level. */
    internal fun addVehicle(vehicle: Vehicle) {
        if (!_vehicles.contains(vehicle)) {
            _vehicles.add(vehicle)
        }
    }

    /** Removes a vehicle from the level. */
    internal fun removeVehicle(vehicle: Vehicle) {
        _vehicles.remove(vehicle)
    }

    /**
     * Clears all runtime-only simulation progress after a configuration change.
     *
     * Source and target counters belong to the previous simulation run and must not leak into
     * the next one. Active vehicles are also removed so a restarted simulation begins from an
     * empty road network using the current source configurations.
     */
    fun resetSimulationProgress() {
        completed = false
        _vehicles.clear()

        nodes.filterIsInstance<SourceNode>().forEach { source ->
            source.vehiclesGenerated = 0
            source.configurations.forEach { configuration ->
                configuration.vehiclesSpawned = 0
                configuration.vehiclesArrived = 0
            }
        }
        nodes.filterIsInstance<TargetNode>().forEach { target ->
            target.vehiclesArrived = 0
        }
    }
}

/** Represents the current state of a level's street network. */
enum class NetworkState {
    /** The network is empty (only source and target nodes exist, no streets). */
    EMPTY,

    /** Streets are present, but they aren't connected the correct way. */
    INCOMPLETE,

    /** The network is fully connected and valid. */
    COMPLETE
}

/**
 * Represents a specific error found during level validation.
 *
 * @property message A human-readable description of the error.
 * @property position The 2D coordinates associated with the error, if applicable.
 * @property color The color associated with the error, if applicable.
 * @property affectedNodes Nodes that belong to or frame the invalid part of the network.
 * @property affectedStreets Streets that are reachable from the source but still do not complete
 * the required path.
 * @property constructionNodes Nodes where the scene should render construction markers.
 * @property constructionPositions Level-space marker positions kept for consumers that do not need
 * node orientation.
 */
data class ValidationError(
    val message: String,
    val position: korlibs.math.geom.Point? = null,
    val color: Color? = null,
    val affectedNodes: List<Node> = emptyList(),
    val affectedStreets: List<Street> = emptyList(),
    val constructionNodes: List<Node> = emptyList(),
    val constructionPositions: List<korlibs.math.geom.Point> =
        position?.let { listOf(it) } ?: emptyList()
)
