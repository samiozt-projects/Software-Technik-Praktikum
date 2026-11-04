package imageprocessing

import domain.Node
import domain.Street
import kotlin.math.roundToInt

/**
 * A directed-edge fingerprint of a street network, independent of the actual [Node] object
 * instances (every detection run creates fresh ones). Two [Street] lists that connect the same
 * positions/elevations in the same directions produce an equal signature.
 *
 * Coordinates are rounded to [POSITION_BUCKET_SIZE]-unit buckets rather than to the nearest whole
 * unit: real camera captures shift a node's detected position by a unit or two between otherwise
 * identical frames, and rounding to single units means that tiny wobble alone can push a coordinate
 * across a rounding boundary and change its key, even though nothing physically moved. Junctions on
 * a real network are far more than a few units apart, so this coarser bucketing does not risk
 * merging two genuinely different nodes.
 */
fun streetNetworkSignature(streets: List<Street>): Set<String> =
    streets.map(::streetEdgeKey).toSet()

/** One street's directed-edge key, the unit [streetNetworkSignature] is built from. */
fun streetEdgeKey(street: Street): String = "${nodeKey(street.startNode)}->${nodeKey(street.endNode)}"

/** Builds a position-and-elevation key for logical nodes, bucketed by [POSITION_BUCKET_SIZE]. */
private fun nodeKey(node: Node): String {
    val bucketX = (node.position.x / POSITION_BUCKET_SIZE).roundToInt()
    val bucketY = (node.position.y / POSITION_BUCKET_SIZE).roundToInt()
    return "$bucketX:$bucketY:${node.elevation}"
}

/** Bucket size (in virtual metres) coordinates are rounded to before comparing signatures. */
private const val POSITION_BUCKET_SIZE = 3.0

/**
 * Confirms a newly detected street network once the same topology has been observed on two
 * matching detections, tolerating at most one differing capture in between while accumulating
 * toward that second match.
 *
 * Camera-based detection ([StreetDetection]) runs repeatedly on live capture and is noisy: a single
 * frame can show a spurious extra junction, momentarily miss a segment, or flip one segment's
 * direction, even though the physical street layout on the table has not changed at all. Applying
 * every raw detection result directly to the live level would flicker the rendered network,
 * spuriously invalidate the level (aborting an in-progress countdown to start the simulation), and
 * — worse — risk a vehicle spawning onto a network that only ever existed for one noisy frame.
 * Requiring the same signature to repeat before a result is trusted filters out that transient
 * noise; a genuine change (the player actually redrew a street) still shows up consistently across
 * captures and gets confirmed within a couple of detection cycles.
 *
 * **Why one tolerated interruption, not a strict two-in-a-row:** the original design required the
 * repeat to be on two *immediately consecutive* captures. In practice a network that is otherwise
 * completely settled can still show one single stray capture (e.g. one borderline segment's
 * direction briefly flips before landing back where it was) - under the strict rule that resets
 * progress back to zero, so a table that is genuinely done can sit at PENDING indefinitely if noise
 * recurs about as often as two clean captures land in a row. [resolveAction] tolerates exactly one
 * such interruption per candidate: reaching the candidate signature twice, with at most one
 * differing capture anywhere in between, still confirms it.
 *
 * **Why this does not reintroduce the 2026-07-08 regression** (see the project's `dev-regression-
 * analysis` notes): that design's flaw was applying a result to the live level from a single
 * observation merely "close enough" (within a position/edge-count tolerance) to the *previous*
 * capture - no independent second confirmation was ever required at all, so one noisy frame could
 * immediately corrupt the level and a genuinely unstable network could keep reapplying a different
 * result every cycle. This tracker never relaxes that: every signature it ever confirms has still
 * been observed *byte-for-byte identical* on two separate captures - only the *strictness of
 * adjacency* between those two captures is relaxed, by exactly one capture, never the requirement
 * for an exact repeat itself.
 *
 * **Why this does not flap forever under a genuinely, persistently oscillating raw signal** (e.g. a
 * detection bug that keeps alternating between exactly two topologies every single capture): a
 * naive "does the current signature appear a majority of times within the last 3 captures"
 * formulation looks equivalent to the above but is not - by the pigeonhole principle, any period-2
 * alternation trivially satisfies "majority within any window of 3", so a sliding-window majority
 * would flip [confirmedSignature] back and forth every single capture once it starts, forever. This
 * tracker avoids that by being deliberately asymmetric: reproducing the *already-confirmed* network
 * always short-circuits to [Action.UNCHANGED] and resets any competing candidate's progress back to
 * zero ([resolveAction]'s first branch) - so for a genuine A/B/A/B/... oscillation, once A is
 * confirmed, B can never accumulate its second match because every recurrence of A wipes out
 * whatever partial progress B had made. [confirmedSignature] only ever changes again once some
 * *other* signature manages two matches (one interruption tolerated) without the current confirmed
 * network reappearing in between - see [StreetNetworkStabilityTrackerTest] for a worked-through
 * trace of exactly this scenario.
 */
class StreetNetworkStabilityTracker {
    /** What the caller should do with a newly observed detection result. */
    enum class Action {
        /** Identical to the already-confirmed network; nothing to apply. */
        UNCHANGED,

        /** Reached its second match (at most one interruption in between); safe to apply now. */
        CONFIRMED,

        /** Still accumulating matches toward confirmation, or just reset after a second interruption. */
        PENDING
    }

    private var confirmedSignature: Set<String>? = null

    /** Diagnostic only - the most recently observed raw signature, so [logSignatureDrift] can diff against it. */
    private var previousObservedSignature: Set<String>? = null

    /** The signature currently accumulating matches toward confirmation, if any - see class doc. */
    private var pendingCandidate: Set<String>? = null

    /** How many times [pendingCandidate] has been observed so far (including the observation that set it). */
    private var pendingMatchCount: Int = 0

    /** Whether the one tolerated interruption has already been used for the current [pendingCandidate]. */
    private var usedGraceSkip: Boolean = false

    /** Records one freshly detected network's [signature] and returns what the caller should do with it. */
    fun observe(signature: Set<String>): Action {
        val action = resolveAction(signature)
        if (action == Action.PENDING) {
            logSignatureDrift(signature)
        }
        previousObservedSignature = signature
        return action
    }

    /**
     * Decides [signature]'s [Action] and updates [pendingCandidate]/[pendingMatchCount]/
     * [usedGraceSkip] accordingly - see the class doc for the reasoning behind each branch.
     */
    private fun resolveAction(signature: Set<String>): Action {
        if (signature == confirmedSignature) {
            // Reproducing the already-confirmed network: nothing to do, and any competing candidate
            // that was accumulating matches loses its progress - this is what keeps a persistent
            // two-state oscillation from ever flipping the confirmed network back and forth (see
            // class doc's "why this does not flap forever" section).
            resetPendingCandidate()
            return Action.UNCHANGED
        }

        if (signature == pendingCandidate) {
            pendingMatchCount++
            if (pendingMatchCount < REQUIRED_MATCHES) return Action.PENDING

            confirmedSignature = signature
            resetPendingCandidate()
            return Action.CONFIRMED
        }

        // signature differs from both the confirmed network and the current candidate (if any).
        val toleratingThisAsAnInterruption = pendingCandidate != null && !usedGraceSkip && pendingMatchCount >= 1
        if (toleratingThisAsAnInterruption) {
            usedGraceSkip = true
            return Action.PENDING
        }

        // No candidate yet, or the one tolerated interruption was already used up: signature becomes
        // the new candidate the caller starts accumulating matches for.
        pendingCandidate = signature
        pendingMatchCount = 1
        usedGraceSkip = false
        return Action.PENDING
    }

    private fun resetPendingCandidate() {
        pendingCandidate = null
        pendingMatchCount = 0
        usedGraceSkip = false
    }

    /**
     * Diagnostic only. A PENDING observation means this capture's topology differs from the previous
     * capture's - either the player genuinely changed the network (expected, confirms within a
     * couple of cycles), or detection noise keeps flipping some edge back and forth, in which case
     * the network never confirms at all and the countdown/construction markers never appear. Logging
     * exactly which edges appeared/disappeared makes the flickering edge identifiable on the real
     * table instead of guessing: a genuine rebuild logs once or twice, a noisy edge logs the same +/-
     * pair every cycle.
     */
    private fun logSignatureDrift(signature: Set<String>) {
        val previous = previousObservedSignature ?: return
        val added = signature - previous
        val removed = previous - signature
        if (added.isEmpty() && removed.isEmpty()) return

        val addedText = if (added.isEmpty()) "" else " +${added.size}: ${added.joinToString()}"
        val removedText = if (removed.isEmpty()) "" else " -${removed.size}: ${removed.joinToString()}"
        println("[Detection] Network signature changed since last capture (not confirmed yet):$addedText$removedText")
    }

    companion object {
        /** Matches of the same candidate signature required before it is confirmed - see class doc. */
        private const val REQUIRED_MATCHES = 2
    }
}
