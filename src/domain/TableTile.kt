package domain

/**
 * Represents one selectable table tile in the multi-table grid.
 *
 * This model is used for both local placeholder layouts and real server field layouts. In
 * server-backed worlds [id] is the field ID used for WebSocket registration.
 *
 * @property id Display identifier or real server field ID for the tile.
 * @property occupiedByOtherTable True when another table already claimed this tile.
 */
data class TableTile(
    val id: String,
    val occupiedByOtherTable: Boolean
)
