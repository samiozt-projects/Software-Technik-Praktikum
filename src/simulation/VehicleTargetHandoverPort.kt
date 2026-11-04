package simulation

import domain.TargetNode
import domain.Vehicle

/**
 * Extension point for handing vehicles from a local target node to an external system.
 */
fun interface VehicleTargetHandoverPort {
    /**
     * Attempts to hand over a vehicle that reached a target node.
     *
     * @param vehicle vehicle that reached the target.
     * @param targetNode target node reached by the vehicle.
     * @return true when the target was handled as an external handover, false for normal arrival.
     */
    fun handoverVehicle(vehicle: Vehicle, targetNode: TargetNode): Boolean
}

/**
 * Default handover port used outside multi-table mode.
 */
val NoVehicleTargetHandover: VehicleTargetHandoverPort = VehicleTargetHandoverPort { _, _ -> false }
