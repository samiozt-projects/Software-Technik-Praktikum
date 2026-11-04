package scenes

import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street

/**
 * Start-gate route check, cached per committed street network: (source, configuration) pairs the
 * router cannot serve on the level's current streets. The gate must run every frame as a
 * countdown-start prerequisite, but the committed network only ever changes when a detection commit
 * replaces [Level.streets] wholesale - so the expensive route search only reruns when the street
 * instances differ from the last checked list.
 *
 * The cache starts *unchecked* (null), not with an empty street list: an empty [Level.streets] must
 * never look "already checked and fine". A level whose detection has not been committed yet has no
 * routes for any source configuration, and the gate has to say exactly that - the old empty-list
 * initial value made an uncommitted network compare "unchanged" against the initial empty result,
 * letting the countdown start a simulation on a level without a single street (no vehicle could
 * ever spawn until the level was reloaded).
 */
class StartGateRouteCache {
    /** The street list (by instance identity) the gate last ran against, or null before any check. */
    private var checkedStreets: List<Street>? = null

    /** The gate result for [checkedStreets]. */
    private var missingRoutes: List<Pair<SourceNode, SourceNodeConfiguration>> = emptyList()

    /**
     * Returns the (source, configuration) pairs without any driving route on [level]'s current
     * street network, recomputing only when the street instances changed since the last call.
     * Logs the blockers once per network state instead of once per frame or countdown cycle.
     */
    fun missingRoutesFor(level: Level): List<Pair<SourceNode, SourceNodeConfiguration>> {
        val currentStreets = level.streets.toList()
        val checked = checkedStreets
        val unchanged = checked != null &&
            currentStreets.size == checked.size &&
            currentStreets.indices.all { currentStreets[it] === checked[it] }

        if (!unchanged) {
            checkedStreets = currentStreets
            missingRoutes = LevelScene.sourceConfigurationsWithoutRoutes(level)
            LevelScene.logBlockedSimulationStart(missingRoutes)
        }
        return missingRoutes
    }
}
