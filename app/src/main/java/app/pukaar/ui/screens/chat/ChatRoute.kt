package app.pukaar.ui.screens.chat

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.pukaar.data.PukaarStore
import app.pukaar.device.DeviceStatus
import app.pukaar.device.Locations
import app.pukaar.gateway.Gateway
import app.pukaar.model.DeliveryStatus
import app.pukaar.sos.AckPacket
import app.pukaar.sos.ContactsPacket
import app.pukaar.sos.ActiveSos
import app.pukaar.sos.MeshBridge
import app.pukaar.sos.OfficialPacket
import app.pukaar.sos.Packets
import app.pukaar.sos.SafePacket
import app.pukaar.sos.SosManager
import app.pukaar.sos.SosPacket
import com.bitchat.android.geohash.ChannelID
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.services.AppStateStore
import com.bitchat.android.ui.ChatViewModel
import kotlinx.coroutines.launch

/** Disaster Relief = bitchat's public mesh timeline, shown the Pukaar way. */
@Composable
fun ChatRoute(chatViewModel: ChatViewModel, onSos: () -> Unit, onNetwork: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages by chatViewModel.messages.collectAsState()
    val nickname by chatViewModel.nickname.collectAsState()
    val connection by DeviceStatus.connection.collectAsState()
    val radio by DeviceStatus.radioPaired.collectAsState()
    val uploaded by Gateway.uploadedMessageIds.collectAsState()
    val outbox by ChatOutbox.waiting.collectAsState()
    val ownSos by SosManager.active.collectAsState()
    val directPeers by AppStateStore.directPeers.collectAsState()
    val myLocation by produceState<Location?>(null) { value = Locations.lastKnown(context) }
    val myPeer = remember { MeshBridge.myPeerId(context) }

    // Pukaar only uses the public mesh timeline: leave any geohash channel or bitchat channel.
    LaunchedEffect(Unit) {
        if (chatViewModel.selectedLocationChannel.value !is ChannelID.Mesh) chatViewModel.selectLocationChannel(ChannelID.Mesh)
        if (chatViewModel.currentChannel.value != null) chatViewModel.switchToChannel(null)
    }
    DisposableEffect(Unit) {
        PukaarStore.markChatSeen()
        onDispose { PukaarStore.markChatSeen() }
    }
    LaunchedEffect(messages.size) { PukaarStore.markChatSeen() }

    val items = remember(messages, uploaded, outbox, ownSos, directPeers, myLocation, nickname) {
        buildChatItems(messages, myPeer, nickname, uploaded, outbox.toSet(), ownSos, directPeers.isNotEmpty(), myLocation)
    }

    ChatScreen(
        state = ChatState(items, connection, radio),
        onSend = { text, attach ->
            scope.launch {
                val content = if (attach) {
                    Locations.current(context, 6_000)?.let { Packets.withLocation(text, it.latitude, it.longitude) } ?: text
                } else text
                if (AppStateStore.directPeers.value.isEmpty()) ChatOutbox.add(content)
                chatViewModel.sendMessage(content)
            }
        },
        onSos = onSos,
        onNetwork = onNetwork,
    )
}

internal fun buildChatItems(
    messages: List<BitchatMessage>,
    myPeer: String?,
    nickname: String,
    uploaded: Set<String>,
    outbox: Set<String>,
    ownSos: ActiveSos?,
    hasPeers: Boolean,
    me: Location?,
): List<ChatItem> {
    val sosById = linkedMapOf<String, ChatItem.Sos>()
    val namesBySos = mutableMapOf<String, String>()
    val officials = linkedMapOf<String, ChatItem.Official>()
    val out = mutableListOf<ChatItem>()
    val ownIds = SosManager.ownIds

    fun distanceTo(lat: Double?, lon: Double?): Float? =
        if (me != null && lat != null && lon != null) Locations.distanceM(me.latitude, me.longitude, lat, lon) else null

    for (msg in messages) {
        if (msg.sender == "system") continue
        val own = (myPeer != null && msg.senderPeerID == myPeer) || msg.sender == nickname
        when (val packet = Packets.parse(msg.content)) {
            is SosPacket -> {
                namesBySos[packet.id] = packet.name
                val existing = sosById[packet.id]
                if (existing == null || existing.packet.seq < packet.seq) {
                    sosById[packet.id] = ChatItem.Sos("sos-${packet.id}", packet, distanceTo(packet.lat, packet.lon), packet.id in ownIds, packet.timeSec * 1000)
                }
            }
            is SafePacket -> out += ChatItem.Safe("safe-${packet.id}", namesBySos[packet.id] ?: msg.sender, packet.timeSec * 1000)
            // Forged "official" messages are dropped: only the server's signature counts.
            is OfficialPacket -> if (packet.verified()) {
                officials.getOrPut(packet.id) { ChatItem.Official("off-${packet.id}", packet.from, packet.text, msg.timestamp.time) }
            }
            is AckPacket, is ContactsPacket -> Unit
            null -> {
                val (text, loc) = Packets.splitLocation(msg.content)
                val delivery: DeliveryStatus? = if (!own) null else when {
                    msg.content in outbox -> DeliveryStatus.Sending
                    msg.deliveryStatus is com.bitchat.android.model.DeliveryStatus.Sending -> DeliveryStatus.Sending
                    msg.id in uploaded -> DeliveryStatus.HelpNotified
                    hasPeers -> DeliveryStatus.Relayed()
                    else -> DeliveryStatus.Sending
                }
                out += ChatItem.Message(
                    key = msg.id,
                    sender = msg.sender,
                    text = text,
                    distanceM = loc?.let { distanceTo(it.first, it.second) },
                    hasLocation = loc != null,
                    own = own,
                    delivery = delivery,
                    time = msg.timestamp.time,
                )
            }
        }
    }
    // The user's own SOS is sent straight to the mesh, so it isn't in the timeline: show it from the SOS engine.
    if (ownSos != null && !sosById.containsKey(ownSos.id)) {
        val loc = ownSos.location
        val packet = SosPacket(ownSos.id, ownSos.seq, loc?.lat, loc?.lon, loc?.accuracyM, ownSos.startedAt / 1000, ownSos.battery,
            ownSos.details.people, ownSos.details.flags, PukaarStore.profile.value.name, ownSos.details.message)
        sosById[ownSos.id] = ChatItem.Sos("sos-${ownSos.id}", packet, null, true, ownSos.startedAt)
    }
    return (out + sosById.values + officials.values).sortedBy { it.time }
}

/** Unread count for the Home Chat tile. */
fun unreadCount(messages: List<BitchatMessage>, seenAt: Long, myPeer: String?, nickname: String): Int =
    messages.count { m ->
        m.timestamp.time > seenAt && m.sender != "system" && m.senderPeerID != myPeer && m.sender != nickname &&
            Packets.parse(m.content).let { it == null || it is SosPacket || (it is OfficialPacket && it.verified()) }
    }
