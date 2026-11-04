package imageprocessing

import domain.Street

/**
 * Smooths the on-screen detection preview against single-capture dropouts, purely visually.
 *
 * Camera detection is noisy: a borderline segment can be present in one capture and missing in the
 * next even though the physical street never moved. Rendering each raw detection directly makes that
 * segment visibly blink in and out once per capture. This buffer keeps a street on screen until it
 * has been missing for [removalThreshold] consecutive captures, while newly detected streets still
 * appear immediately - so genuine changes (player adds/removes a street) show up with at most a
 * one-capture delay on removal, but one-frame detection dropouts no longer flicker.
 *
 * Display only: the level, validation, countdown, and [StreetNetworkStabilityTracker] must keep
 * receiving each capture's raw detection result, never this buffer's output - otherwise a noisy edge
 * would be held alive long enough to be double-observed and wrongly confirmed into the level.
 *
 * Streets are identified by [streetEdgeKey], the same position-bucketed directed-edge identity the
 * confirmation signature uses, so "the same street" tolerates the same small per-capture position
 * wobble. A re-detected street replaces its stored instance, so the preview always renders the
 * freshest geometry for each edge.
 *
 * @property removalThreshold Consecutive captures a street must be missing from before it is removed.
 */
class DetectionPreviewBuffer(private val removalThreshold: Int = DEFAULT_REMOVAL_THRESHOLD) {

    private val streetsByEdge = LinkedHashMap<String, Street>()
    private val missingStreakByEdge = mutableMapOf<String, Int>()

    /**
     * Records one capture's raw [detected] streets and returns the streets the preview should render:
     * everything currently detected, plus recently missing streets still within [removalThreshold].
     */
    fun update(detected: List<Street>): List<Street> {
        val currentKeys = mutableSetOf<String>()
        for (street in detected) {
            val key = streetEdgeKey(street)
            currentKeys.add(key)
            streetsByEdge[key] = street
            missingStreakByEdge.remove(key)
        }

        val iterator = streetsByEdge.keys.iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()
            if (key in currentKeys) continue

            val streak = (missingStreakByEdge[key] ?: 0) + 1
            if (streak >= removalThreshold) {
                iterator.remove()
                missingStreakByEdge.remove(key)
            } else {
                missingStreakByEdge[key] = streak
            }
        }

        return streetsByEdge.values.toList()
    }

    companion object {
        /**
         * One missed capture is forgiven, the second removes the street. Keeps removal latency at a
         * single extra capture cycle while already absorbing the most common one-frame dropouts.
         */
        const val DEFAULT_REMOVAL_THRESHOLD = 2
    }
}
