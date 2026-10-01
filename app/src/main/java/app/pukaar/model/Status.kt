package app.pukaar.model

/** How this phone can currently reach the outside world. Drives the pill, the Home ring and the composer. */
enum class ConnectionType { Online, Mesh, Radio, Isolated, Gateway }

/**
 * Suggested state from the design handoff: `connection {type, peers, reachable, gatewayHelping}`.
 *
 * @param peers phones directly connected over Bluetooth.
 * @param reachable phones reachable through the mesh (peers plus their peers).
 * @param gatewayHelping people whose messages this phone is uploading (Gateway only).
 */
data class ConnectionStatus(
    val type: ConnectionType,
    val peers: Int = 0,
    val reachable: Int = 0,
    val gatewayHelping: Int = 0,
) {
    companion object {
        val Online = ConnectionStatus(ConnectionType.Online)
        fun mesh(peers: Int, reachable: Int = peers) = ConnectionStatus(ConnectionType.Mesh, peers, reachable)
        val Radio = ConnectionStatus(ConnectionType.Radio)
        val Isolated = ConnectionStatus(ConnectionType.Isolated)
        fun gateway(helping: Int) = ConnectionStatus(ConnectionType.Gateway, gatewayHelping = helping)
    }
}

/** Where a message or SOS has got to, in order. See handoff "Delivery states". */
sealed interface DeliveryStatus {
    data object Sending : DeliveryStatus
    /** @param hops phones it passed through, or null if unknown. */
    data class Relayed(val hops: Int? = null) : DeliveryStatus
    data object SentByRadio : DeliveryStatus
    data object HelpNotified : DeliveryStatus
    data object RescuerAttending : DeliveryStatus
    data object Resolved : DeliveryStatus
}
