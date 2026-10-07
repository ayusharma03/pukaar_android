package app.pukaar.sos

import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.services.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where someone else's SOS stands, as far as this phone has heard. */
enum class NearbySosState { Open, Attending, Resolved, Safe }

data class NearbySos(
    val packet: SosPacket,
    val state: NearbySosState,
    /** When this phone last heard about it (ms). */
    val heardAt: Long,
    val handledBy: String? = null,
) {
    val id: String get() = packet.id
    val hasLocation: Boolean get() = packet.lat != null && packet.lon != null
    /** Still needs help: show it prominently on the map and in chat. */
    val open: Boolean get() = state == NearbySosState.Open || state == NearbySosState.Attending
}

/**
 * Other people's SOS heard over the mesh, so neighbours can help first (map pins, directions,
 * chat cards). The latest `seq` wins; a Safe packet or a signed ack updates the state; anything
 * not heard for [MAX_AGE_MS] drops off. This phone's own SOS is excluded.
 */
object NearbySosStore {
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private val _all = MutableStateFlow<List<NearbySos>>(emptyList())
    val all: StateFlow<List<NearbySos>> = _all.asStateFlow()
    private var started = false

    fun start(scope: CoroutineScope, ownIds: () -> Set<String>) {
        if (started) return
        started = true
        scope.launch {
            AppStateStore.publicMessages.collect { messages -> _all.value = build(messages, ownIds(), System.currentTimeMillis()) }
        }
        // Age out old ones even when no new messages arrive.
        scope.launch {
            while (true) {
                delay(10 * 60_000L)
                val cutoff = System.currentTimeMillis() - MAX_AGE_MS
                _all.value = _all.value.filter { it.heardAt >= cutoff }
            }
        }
    }

    fun byId(id: String): NearbySos? = _all.value.firstOrNull { it.id == id }

    internal fun build(
        messages: List<BitchatMessage>,
        ownIds: Set<String>,
        now: Long,
        verifyAck: (AckPacket) -> Boolean = { it.verified() },
    ): List<NearbySos> {
        val byId = linkedMapOf<String, NearbySos>()
        val safe = mutableMapOf<String, Long>()
        val acks = mutableMapOf<String, AckPacket>()
        for (msg in messages) {
            val heard = msg.timestamp.time
            when (val p = Packets.parse(msg.content)) {
                is SosPacket -> if (p.id !in ownIds) {
                    val prev = byId[p.id]
                    if (prev == null || p.seq >= prev.packet.seq) {
                        byId[p.id] = NearbySos(p, NearbySosState.Open, maxOf(heard, prev?.heardAt ?: 0L))
                    } else {
                        byId[p.id] = prev.copy(heardAt = maxOf(heard, prev.heardAt))
                    }
                }
                is SafePacket -> safe[p.id] = p.timeSec * 1000
                is AckPacket -> if (verifyAck(p)) {
                    val prev = acks[p.id]
                    if (prev == null || p.status.ordinal >= prev.status.ordinal) acks[p.id] = p
                }
                else -> Unit
            }
        }
        val cutoff = now - MAX_AGE_MS
        return byId.values
            .map { sos ->
                val ack = acks[sos.id]
                when {
                    sos.id in safe -> sos.copy(state = NearbySosState.Safe)
                    ack?.status == AckStatus.Resolved -> sos.copy(state = NearbySosState.Resolved, handledBy = ack.by)
                    ack?.status == AckStatus.Attending -> sos.copy(state = NearbySosState.Attending, handledBy = ack.by)
                    else -> sos
                }
            }
            .filter { it.heardAt >= cutoff }
            .sortedByDescending { it.packet.timeSec }
    }
}
