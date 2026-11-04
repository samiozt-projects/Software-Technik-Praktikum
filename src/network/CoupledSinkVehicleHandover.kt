package network

import domain.TargetNode
import domain.Vehicle
import kotlinx.serialization.json.JsonObject
import simulation.VehicleTargetHandoverPort
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Server transition data needed to report that a vehicle leaves this field.
 *
 * @param transitionNodeId server transition used by the vehicle.
 * @param fromFieldId local field ID.
 * @param toFieldId neighboring field ID.
 * @param level transition elevation level.
 * @property transitionNodeId server transition used by the vehicle.
 * @property fromFieldId local field ID.
 * @property toFieldId neighboring field ID.
 * @property level transition elevation level.
 */
data class VehicleDepartureBinding(
    val transitionNodeId: String,
    val fromFieldId: String,
    val toFieldId: String,
    val level: Int
)

/**
 * Mapping from each coupled local sink to the server transition data used for departures.
 */
typealias VehicleDepartureBindingsBySink = Map<TargetNode, VehicleDepartureBinding>

/**
 * Sends vehicles that reach coupled local sinks to the coupling server.
 *
 * @param bindingsBySink fixed server transition binding for each coupled local sink.
 * @param departureSender transport used to send the serialized handover message.
 * @property bindingsBySink fixed server transition binding for each coupled local sink.
 * @property departureSender transport used to send the serialized handover message.
 */
class CoupledSinkVehicleHandover(
    private val bindingsBySink: VehicleDepartureBindingsBySink,
    private val departureSender: VehicleDepartureSender
) : VehicleTargetHandoverPort {
    private val sentVehicles = Collections.newSetFromMap(IdentityHashMap<Vehicle, Boolean>())

    /**
     * Sends exactly one departure message when [targetNode] is configured as a coupled sink.
     *
     * @param vehicle vehicle that reached the target.
     * @param targetNode target node reached by the vehicle.
     * @return true when the target is coupled, false when the simulation should treat it normally.
     */
    override fun handoverVehicle(vehicle: Vehicle, targetNode: TargetNode): Boolean {
        val binding = bindingsBySink[targetNode] ?: return false
        if (!sentVehicles.add(vehicle)) return true

        departureSender.sendVehicleDeparture(
            transitionNodeId = binding.transitionNodeId,
            fromFieldId = binding.fromFieldId,
            toFieldId = binding.toFieldId,
            level = binding.level,
            vehicleProperties = VehicleWebSocketConverter.vehicleToWebSocketProperties(vehicle)
        )
        return true
    }

    companion object {
        /**
         * Creates a handover adapter that sends through [TrafficSimWebSocketClient].
         *
         * Kept as the intended integration entry point for wiring simulation handovers to the
         * already connected field WebSocket.
         *
         * @param bindingsBySink fixed server transition binding for each coupled local sink.
         * @param webSocketClient connected coupling-server WebSocket client.
         * @return handover adapter for the simulation.
         */
        @Suppress("unused")
        fun fromWebSocketClient(
            bindingsBySink: VehicleDepartureBindingsBySink,
            webSocketClient: TrafficSimWebSocketClient
        ): CoupledSinkVehicleHandover =
            CoupledSinkVehicleHandover(
                bindingsBySink = bindingsBySink,
                departureSender = webSocketClient::sendVehicleDeparture
            )
    }
}

/**
 * Transport abstraction for outgoing vehicle departure messages.
 */
fun interface VehicleDepartureSender {
    /**
     * Sends the already serialized handover payload to the server.
     *
     * @param transitionNodeId server transition used by the vehicle.
     * @param fromFieldId local field ID.
     * @param toFieldId neighboring field ID.
     * @param level transition elevation level.
     * @param vehicleProperties serialized vehicle payload in the agreed team format.
     */
    fun sendVehicleDeparture(
        transitionNodeId: String,
        fromFieldId: String,
        toFieldId: String,
        level: Int,
        vehicleProperties: JsonObject
    )
}
