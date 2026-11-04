package domain

/**
 * Stores and evaluates the active challenge-mode rule set.
 */
object ChallengeMode {
    /** Whether the challenge mode currently affects the active level. */
    var active: Boolean = false
        private set

    /**
     * Activates challenge mode if the current level can satisfy its rules.
     *
     * Existing player configuration changes are reverted before activation so
     * the challenge starts from the level's original values.
     *
     * @param level The level that should enter challenge mode.
     * @return True when the challenge mode was activated, false when the level
     * violates the challenge rules.
     */
    fun activate(level: Level): Boolean {
        if (!canActivate(level)) return false
        GameState.resetCurrentLevelConfigurationToOriginal()
        active = true
        return true
    }

    /**
     * Deactivates challenge mode.
     */
    fun deactivate() {
        active = false
    }

    /**
     * Returns whether [level] currently satisfies the challenge entry rules.
     */
    fun canActivate(level: Level): Boolean {
        return connectionNodeCount(level) <= level.challengeConnectionNodeLimit
    }

    /**
     * Returns all challenge validation errors for [level].
     */
    fun validationErrors(level: Level): List<ValidationError> {
        if (!active) return emptyList()
        val count = connectionNodeCount(level)
        val limit = level.challengeConnectionNodeLimit
        if (count <= limit) return emptyList()
        return listOf(
            ValidationError(
                "Challenge mode allows at most $limit connection nodes, but this level has $count."
            )
        )
    }

    /**
     * Returns the challenge result that should currently be shown to the user.
     *
     * A rule violation only becomes a failed challenge after the simulation has
     * started. During construction, validation can still report the rule issue
     * without showing a final failure result.
     *
     * @param level The level whose challenge state is evaluated.
     * @param levelCompleted Whether the regular simulation goal has been completed.
     * @param simulationStarted Whether the player has started the simulation.
     * @return The visible challenge result, or null while the challenge is still running.
     */
    fun resultFor(level: Level, levelCompleted: Boolean, simulationStarted: Boolean): ChallengeResult? {
        if (!active) return null
        if (simulationStarted && validationErrors(level).isNotEmpty()) return ChallengeResult.FAILED
        if (levelCompleted) return ChallengeResult.COMPLETED
        return null
    }

    /**
     * Returns the current challenge result and ends challenge mode after a successful completion.
     *
     * Failed challenges remain active so their rule violation stays visible until the player
     * explicitly leaves or changes the level.
     */
    fun resultForAndDeactivateOnCompleted(
        level: Level,
        levelCompleted: Boolean,
        simulationStarted: Boolean
    ): ChallengeResult? {
        val result = resultFor(level, levelCompleted, simulationStarted)
        if (result == ChallengeResult.COMPLETED) {
            deactivate()
        }
        return result
    }

    /**
     * Returns the user-facing text for a [result].
     */
    fun resultText(result: ChallengeResult?): String {
        return when (result) {
            ChallengeResult.COMPLETED -> "Challenge geschafft"
            ChallengeResult.FAILED -> "Challenge nicht geschafft"
            null -> ""
        }
    }

    /**
     * Counts connection nodes in [level].
     */
    fun connectionNodeCount(level: Level): Int {
        return level.nodes.count { it is ConnectionNode }
    }
}

/**
 * Describes the final visible outcome of an active challenge.
 */
enum class ChallengeResult {
    /** The level was completed while all challenge rules were still satisfied. */
    COMPLETED,

    /** At least one challenge rule was violated. */
    FAILED
}
