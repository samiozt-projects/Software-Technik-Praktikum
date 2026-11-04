package network

import domain.TargetNode
import domain.TransitionAssignmentRegistry
import domain.TransitionAssignmentResult
import simulation.Simulation

/**
 * Applies fixed transition assignments to incoming server vehicle arrivals.
 *
 * The coupling server identifies the physical crossing by its transition ID; the domain
 * assignment registry works with the local sink node. This handler bridges both worlds through a
 * fixed transition-to-sink mapping and injects accepted vehicles into the local simulation.
 * Server-generated neutral source messages are intentionally ignored.
 *
 * @param simulation active local simulation that receives accepted vehicles.
 * @param registry fixed `(sink, vehicle type) -> local source` assignment registry.
 * @param transitionSinkResolver resolves server transition IDs to local sink nodes.
 * @property simulation active local simulation that receives accepted vehicles.
 * @property registry fixed `(sink, vehicle type) -> local source` assignment registry.
 * @property transitionSinkResolver resolves server transition IDs to local sink nodes.
 */
internal class FixedTransitionHandoverHandler(
    private val simulation: Simulation,
    private val registry: TransitionAssignmentRegistry,
    private val transitionSinkResolver: TransitionSinkResolver
) {
    /**
     * Handles one WebSocket vehicle callback.
     *
     * Only `VEHICLE_ARRIVAL` messages can inject vehicles. Arrivals without a known transition,
     * without valid handover JSON, on non-zero levels, without a fixed assignment, or mapped to a
     * non-matching/neutral source are discarded.
     *
     * @param data vehicle callback data from [TrafficSimWebSocketClient].
     * @return result describing whether the vehicle was accepted or why it was ignored.
     */
    fun handle(data: VehicleMessageData): FixedTransitionHandoverResult {
        if (data.messageType != VehicleMessageType.VEHICLE_ARRIVAL) {
            return FixedTransitionHandoverResult.IgnoredNonArrival
        }

        val transitionId = data.transitionNodeId ?: return FixedTransitionHandoverResult.Discarded
        val sink = transitionSinkResolver.sinkForTransition(transitionId) as? TargetNode
            ?: return FixedTransitionHandoverResult.Discarded
        val vehicle = VehicleWebSocketConverter.webSocketPropertiesToHandoverProperties(data.vehicleProperties)
            ?: return FixedTransitionHandoverResult.Discarded

        val assignmentResult = registry.applyIncomingVehicle(
            sink = sink,
            level = data.level ?: NON_ZERO_LEVEL_FALLBACK,
            vehicle = vehicle
        )

        if (assignmentResult !is TransitionAssignmentResult.Accepted) {
            return FixedTransitionHandoverResult.Discarded
        }

        val injected = simulation.spawnManager.injectHandoverVehicle(
            source = assignmentResult.source,
            cfg = assignmentResult.configuration,
            speedMetersPerSecond = assignmentResult.speedMetersPerSecond
        )

        return if (injected) {
            FixedTransitionHandoverResult.Accepted
        } else {
            FixedTransitionHandoverResult.Discarded
        }
    }

    private companion object {
        /** Missing levels must not accidentally pass the level-0-only acceptance rule. */
        const val NON_ZERO_LEVEL_FALLBACK = -1
    }
}

/**
 * Resolves server transition IDs to local sink nodes.
 */
internal fun interface TransitionSinkResolver {
    /**
     * Looks up the local sink bound to a server transition.
     *
     * @param transitionId server transition ID from an incoming arrival.
     * @return local sink object for the transition, or null when no binding exists.
     */
    fun sinkForTransition(transitionId: String): Any?
}

/**
 * Outcome of handling one incoming server vehicle message.
 */
enum class FixedTransitionHandoverResult {
    /** A vehicle was inserted into the local simulation. */
    Accepted,

    /** The message was a server-side neutral source event, not a neighbor-table arrival. */
    IgnoredNonArrival,

    /** The arrival did not match a valid fixed assignment and was dropped. */
    Discarded
}
