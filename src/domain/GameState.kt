package domain

/**
 * A globally accessible object that holds the overarching state of the game.
 * It tracks the currently loaded level and its active simulation state.
 */
object GameState {
    /** The currently active level. Null if no level is loaded (e.g., in the Main Menu). */
    var currentLevel: Level? = null
        set(value) {
            field = value
            activeSimulation = null
            automaticStreetDetectionEnabled = true
            if(tutorialActivated && value?.name != "Tutorial") {
                tutorialActivated = false
            }
            lastSavedLevelSnapshot = null
            originalLevelConfiguration = value?.let { LevelConfigurationSnapshot.capture(it) }
            ChallengeMode.deactivate()
        }

    /** The current state of the simulation within the active level. */
    var simulationState: SimulationState = SimulationState.NO_LEVEL

    /** The predefined level slot currently loaded from the level menu, if any. */
    var currentPredefinedLevelId: Int? = null

    /** The currently active simulation. Null if no simulation is active. */
    var activeSimulation: simulation.Simulation? = null

    /** User-selected simulation speed applied to simulation ticks in the level scene. */
    var simulationSpeed: SimulationSpeed = SimulationSpeed.DEFAULT

    /** Whether construction mode may replace the current streets from periodic camera captures. */
    var automaticStreetDetectionEnabled: Boolean = true

    /** Stores whether the tutorial level is currently active or not */
    var tutorialActivated = false

    /** Serialized snapshot of the active level at the last successful save or saved-level load. */
    private var lastSavedLevelSnapshot: String? = null

    /**
     * Returns whether the current level may be saved for the given serialized construction state.
     * @param snapshot The serialized construction state.
     * @return True if the current level may be saved for the given serialized construction state, false otherwise.
     */
    fun canSaveCurrentLevelSnapshot(snapshot: String): Boolean {
        return lastSavedLevelSnapshot != snapshot
    }

    /**
     * Remembers the serialized construction state that was last saved for the current level.
     * @param snapshot The serialized construction state.
     */
    fun markCurrentLevelSaved(snapshot: String) {
        lastSavedLevelSnapshot = snapshot
    }

    /** Original configurable values of the current level, captured when the level is loaded. */
    private var originalLevelConfiguration: LevelConfigurationSnapshot? = null

    /**
     * Restores all source configurations and vehicle settings to the state captured
     * when [currentLevel] was assigned.
     *
     * @return True when a level and snapshot were available, otherwise false.
     */
    fun resetCurrentLevelConfigurationToOriginal(): Boolean {
        val level = currentLevel ?: return false
        val snapshot = originalLevelConfiguration ?: return false
        snapshot.restore(level)
        level.resetSimulationProgress()
        activeSimulation = null
        return true
    }

    /**
     * Clears the current simulation run (active vehicles and per-source progress counters) while
     * keeping the player's level configuration intact.
     *
     * Used when recovering from a congestion abort: the deliberate vehicle and source edits the
     * player made before starting the run must survive (issue #162). This must therefore NOT
     * restore the original configuration snapshot the way [resetCurrentLevelConfigurationToOriginal]
     * does.
     *
     * @return True when a level was loaded, otherwise false.
     */
    fun resetSimulationRunKeepingConfiguration(): Boolean {
        val level = currentLevel ?: return false
        level.resetSimulationProgress()
        activeSimulation = null
        return true
    }

    /**
     * Builds the diagnostic message logged when a scene that needs a loaded level finds none.
     *
     * Callers pass their own runtime scene name so a subclass such as the tutorial reports the
     * actually affected scene instead of the shared base scene (issue #184).
     *
     * @param sceneName runtime name of the scene reporting the missing level.
     * @return a message naming the affected scene.
     */
    fun noLevelLoadedMessage(sceneName: String): String =
        "$sceneName: no level loaded in GameState"

    /**
     * Returns the active level for a scene that requires one, or null after recording a recovery.
     *
     * A scene transition can be triggered before a level was assigned, for example through fast or
     * overlapping scene changes. Instead of crashing, this logs which scene was affected and
     * normalizes the global state back to a consistent level-less baseline so no stale simulation
     * survives. The caller then falls back to a safe scene.
     *
     * @param sceneName runtime name of the requesting scene, used for the log message.
     * @return the loaded level, or null when the scene should abort and recover.
     */
    fun levelForSceneOrRecover(sceneName: String): Level? {
        val level = currentLevel
        if (level == null) {
            println(noLevelLoadedMessage(sceneName))
            activeSimulation = null
            simulationState = SimulationState.NO_LEVEL
            return null
        }
        return level
    }

    /** Tracks whether a scene transition claimed through [tryBeginSceneTransition] is still running. */
    private var sceneTransitionInProgress = false

    /**
     * Tries to claim the single in-flight scene-transition slot.
     *
     * Overlapping scene changes must not run at the same time, otherwise a recovery navigation
     * could stack on an already running transition and leave the navigation in an inconsistent
     * state (issue #184).
     *
     * @return true if the caller may start its transition; false if one is already in progress.
     */
    fun tryBeginSceneTransition(): Boolean {
        if (sceneTransitionInProgress) return false
        sceneTransitionInProgress = true
        return true
    }

    /** Releases the scene-transition slot claimed by [tryBeginSceneTransition]. */
    fun endSceneTransition() {
        sceneTransitionInProgress = false
    }
}

/** Represents the current phase of the simulation within an active level. */
enum class SimulationState {
    /** No level is currently loaded (e.g., in the Main Menu). */
    NO_LEVEL,

    /** The player is currently building or modifying the street network. */
    CONSTRUCTION,

    /** The simulation is actively running and vehicles are moving. */
    SIMULATION,

    /** The simulation is temporarily paused. */
    PAUSED
}

/**
 * User-selectable multipliers for simulation time.
 *
 * Rendering and input still run at normal frame speed; only the simulated time accumulated by
 * [simulation.Simulation.runPlayback] is multiplied by [multiplier].
 * 
 * @property multiplier The multiplier for the simulation speed.
 * @property label The label for the simulation speed.
 */
enum class SimulationSpeed(val multiplier: Double, val label: String) {
    /** Normal simulation time. */
    X1(GameSimulationConfig.SIMULATION_SPEED_X1_MULTIPLIER, "1x"),

    /** Twice as much simulated time per rendered second. */
    X2(GameSimulationConfig.SIMULATION_SPEED_X2_MULTIPLIER, "2x"),

    /** Five times as much simulated time per rendered second. */
    X5(GameSimulationConfig.SIMULATION_SPEED_X5_MULTIPLIER, "5x"),

    /** Ten times as much simulated time per rendered second. */
    X10(GameSimulationConfig.SIMULATION_SPEED_X10_MULTIPLIER, "10x");

    companion object {
        /** Default speed used when the player has not selected anything else. */
        val DEFAULT: SimulationSpeed by lazy {
            val configuredSpeed = GameSimulationConfig.getConfiguredSimulationSpeed()
            fromMultiplierOrDefault(configuredSpeed)
        }

        /** Ordered list used by the UI selector. */
        val OPTIONS: List<SimulationSpeed> = listOf(X1, X2, X5, X10)

        /**
         * Maps a raw multiplier back to a supported option.
         *
         * @param multiplier requested multiplier.
         * @return matching supported speed, or [DEFAULT] for unsupported, non-finite, or
         * non-positive values.
         */
        fun fromMultiplierOrDefault(multiplier: Double): SimulationSpeed {
            if (!multiplier.isFinite() || multiplier <= 0.0) return DEFAULT
            return OPTIONS.firstOrNull { it.multiplier == multiplier } ?: DEFAULT
        }
    }

    /**
     * Returns the next selectable speed, wrapping from the last option to the first.
     *
     * @return next speed in [OPTIONS].
     */
    fun next(): SimulationSpeed {
        val index = OPTIONS.indexOf(this)
        return OPTIONS[(index + 1).mod(OPTIONS.size)]
    }

    /**
     * Converts real elapsed seconds into simulated seconds for this speed.
     *
     * @param realDeltaSeconds seconds elapsed in real time.
     * @return non-negative simulated seconds, or `0.0` for invalid frame deltas.
     */
    fun scaledDeltaSeconds(realDeltaSeconds: Double): Double {
        if (!realDeltaSeconds.isFinite() || realDeltaSeconds <= 0.0) return 0.0
        return realDeltaSeconds * multiplier
    }
}

/**
 * Stores whether the color-blindness mode is currently enabled in the pause menu.
 */
var colorBlindnessModeActivated = false

/**
 * Captures the source configurations that can be changed through configuration scenes.
 */
private class LevelConfigurationSnapshot(
    private val sourceSnapshots: List<SourceNodeSnapshot>
) {
    companion object {
        /**
         * Creates a snapshot for every source node in [level].
         * @param level The level to create a snapshot for.
         * @return The snapshot of the level.
         */
        fun capture(level: Level): LevelConfigurationSnapshot {
            return LevelConfigurationSnapshot(
                level.nodes.filterIsInstance<SourceNode>().map { source ->
                    SourceNodeSnapshot(
                        source = source,
                        configurations = source.configurations.map { SourceNodeConfigurationSnapshot.capture(it) }
                    )
                }
            )
        }
    }

    /**
     * Restores all captured source configurations on [level].
     * @param level The level to restore the configurations on.
     */
    fun restore(level: Level) {
        val activeSources = level.nodes.filterIsInstance<SourceNode>().toSet()
        for (sourceSnapshot in sourceSnapshots) {
            if (sourceSnapshot.source in activeSources) {
                sourceSnapshot.restore()
            }
        }
    }
}

/**
 * Captures the configurable values of one source node.
 * @property source The source node.
 * @property configurations The list of configurations.
 */
private class SourceNodeSnapshot(
    val source: SourceNode,
    private val configurations: List<SourceNodeConfigurationSnapshot>
) {
    /**
     * Restores the source configuration list to its captured values.
     */
    fun restore() {
        source.configurations = configurations.map { it.toDomain() }
    }
}

/**
 * Captures one source configuration including the mutable vehicle type values.
 * @param color The color of the vehicle.
 * @param generationSpeed The generation speed of the vehicle.
 * @param initialVehicleSpeed The initial vehicle speed of the vehicle.
 * @param vehicleType The type of the vehicle.
 * @param vehiclesToSpawn The number of vehicles to spawn.
 */
private class SourceNodeConfigurationSnapshot(
    private val color: Color,
    private val generationSpeed: Double,
    private val initialVehicleSpeed: Double,
    private val vehicleType: VehicleTypeSnapshot,
    private val vehiclesToSpawn: Int
) {
    companion object {
        /**
         * Creates a snapshot from a mutable [SourceNodeConfiguration].
         * @param configuration The source node configuration to capture.
         * @return The snapshot of the source node configuration.
         */
        fun capture(configuration: SourceNodeConfiguration): SourceNodeConfigurationSnapshot {
            return SourceNodeConfigurationSnapshot(
                color = configuration.color,
                generationSpeed = configuration.generationSpeed,
                initialVehicleSpeed = configuration.initialVehicleSpeed,
                vehicleType = VehicleTypeSnapshot.capture(configuration.vehicleType),
                vehiclesToSpawn = configuration.vehiclesToSpawn
            )
        }
    }

    /**
     * Creates a restored domain configuration.
     * @return The restored domain configuration.
     */
    fun toDomain(): SourceNodeConfiguration {
        return SourceNodeConfiguration(
            color = color,
            generationSpeed = generationSpeed,
            initialVehicleSpeed = initialVehicleSpeed,
            vehicleType = vehicleType.toDomain(),
            vehiclesToSpawn = vehiclesToSpawn
        )
    }
}

/**
 * Captures mutable vehicle type fields.
 * @param name The name of the vehicle type.
 * @param maxSpeed The maximum speed of the vehicle type.
 * @param length The length of the vehicle type.
 * @param adhesionCoefficient The adhesion coefficient of the vehicle type.
 * @param acceleration The acceleration of the vehicle type.
 * @param graphicRef The graphic reference of the vehicle type.
 */
private class VehicleTypeSnapshot(
    private val name: String,
    private val maxSpeed: Double,
    private val length: Double,
    private val adhesionCoefficient: Double,
    private val acceleration: Double,
    private val graphicRef: String
) {
    companion object {
        /**
         * Creates a snapshot from [vehicleType].
         * @param vehicleType The vehicle type to capture.
         * @return The snapshot of the vehicle type.
         */
        fun capture(vehicleType: VehicleType): VehicleTypeSnapshot {
            return VehicleTypeSnapshot(
                name = vehicleType.name,
                maxSpeed = vehicleType.maxSpeed,
                length = vehicleType.length,
                adhesionCoefficient = vehicleType.adhesionCoefficient,
                acceleration = vehicleType.acceleration,
                graphicRef = vehicleType.graphicRef
            )
        }
    }

    /**
     * Creates a restored vehicle type instance.
     * @return The restored vehicle type instance.
     */
    fun toDomain(): VehicleType {
        return VehicleType(name, maxSpeed, length, adhesionCoefficient, acceleration, graphicRef)
    }
}
