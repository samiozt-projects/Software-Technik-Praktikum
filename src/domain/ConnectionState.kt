package domain

/**
 * Connection state of the local table towards the multi-table server.
 *
 * This is a placeholder for the upcoming server feature: the states already cover the
 * transitions shown by the UI mockups (connecting, connected, disconnecting), but the
 * actual transport logic is implemented in a follow-up issue.
 *
 * @property label German UI label shown for the state in the server menu scene.
 */
enum class ConnectionState(val label: String) {
    /** No connection attempt has been made yet, or the table is fully disconnected. */
    DISCONNECTED("Getrennt"),

    /** A connection attempt to the server is in progress. */
    CONNECTING("Verbindung zum Server wird hergestellt..."),

    /** The table is connected to the server. */
    CONNECTED("Verbunden"),

    /** The table is in the process of disconnecting from the server. */
    DISCONNECTING("Verbindung zum Server wird getrennt..."),

    /**
     * The connection was lost unexpectedly and an automatic reconnect attempt is running.
     *
     * Reached from [CONNECTED] via `ServerConnection.loseConnection` when the WebSocket drops or the
     * server becomes unreachable. The server menu scene shows the "Verbindung zum Server verloren"
     * overlay for this state; a successful reconnect returns to [CONNECTED], cancelling it goes to
     * [DISCONNECTED].
     */
    RECONNECTING("Verbindung zum Server verloren");

    companion object {
        /** Default state before any connection attempt has been made. */
        val DEFAULT: ConnectionState = DISCONNECTED
    }
}
