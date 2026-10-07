package app.pukaar

import android.app.Application
import android.content.Context
import android.text.format.DateFormat
import app.pukaar.data.PukaarStore
import app.pukaar.device.DeviceStatus
import app.pukaar.gateway.Gateway
import app.pukaar.map.OfflineMaps
import app.pukaar.sos.NearbySosStore
import app.pukaar.sos.OfficialPacket
import app.pukaar.sos.Packets
import app.pukaar.sos.SosManager
import app.pukaar.sos.SosNotifier
import app.pukaar.sos.SosPacket
import app.pukaar.system.ShakeTrigger
import app.pukaar.system.SosWidgets
import com.bitchat.android.R
import com.bitchat.android.services.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.pukaar.ui.screens.chat.ChatOutbox
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Pukaar's own version, shown in Settings and About (bitchat's versionName is kept for its release tooling). */
const val PUKAAR_VERSION = "1.0"

/** Intent extras that open a Pukaar screen from notifications, the tile and widgets. */
object PukaarIntents {
    const val EXTRA_ROUTE = "pukaar_route"
    const val ROUTE_SOS_STATUS = "sos/status"
    const val ROUTE_CHAT = "chat"
}

/**
 * Starts Pukaar's background parts. Called once from bitchat's Application.onCreate, so the SOS
 * engine, shake trigger and gateway run whenever the process (kept alive by the mesh service) runs.
 */
object PukaarRuntime {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun init(app: Application) {
        if (started) return
        started = true
        PukaarStore.init(app)
        SosNotifier.ensureChannels(app)
        DeviceStatus.start(app, scope)
        SosManager.init(app, scope)
        Gateway.start(
            app, scope,
            ownSosIds = { SosManager.ownIds },
            // Polled until the person dismisses it, so a reopen or a message after "resolved" still arrives.
            activeOwnSosId = { SosManager.active.value?.id },
            onOwnAck = { SosManager.onPacket(it) },
            onOwnMessage = { SosManager.onPacket(it) },
        )
        ShakeTrigger.start(app, scope)
        OfflineMaps.init(app)
        // Once the offline map is saved, Home stops reminding about it.
        scope.launch {
            OfflineMaps.state.collect { if (it is OfflineMaps.State.Saved) PukaarStore.setOfflinePending(false) }
        }
        watchPackets(app)
        NearbySosStore.start(scope) { SosManager.ownIds }
        ChatOutbox.start(app, scope)
        scope.launch {
            combine(DeviceStatus.connection, SosManager.active) { c, s -> c to s }.collect { SosWidgets.updateAll(app) }
        }
        // Settings > Battery saver drives bitchat's scan policy (NFR-5).
        scope.launch {
            PukaarStore.settings.map { it.batterySaverNow to it.batterySaverAuto }.distinctUntilChanged().collect { (now, auto) ->
                com.bitchat.android.mesh.PowerManager.getInstance(app).setPukaarBatterySaver(now, auto)
            }
        }
    }

    /** Reacts to Pukaar packets arriving on the mesh: acks for our SOS, nearby SOS, official messages. */
    private fun watchPackets(context: Context) {
        scope.launch {
            val seen = mutableSetOf<String>()
            var first = true
            AppStateStore.publicMessages.collect { messages ->
                val fresh = messages.filter { seen.add(it.id) }
                // Messages restored at startup are history: apply acks but don't notify.
                val notify = !first
                first = false
                val own = SosManager.ownIds
                for (msg in fresh) {
                    val packet = Packets.parse(msg.content) ?: continue
                    SosManager.onPacket(packet)
                    // Verified officials show in the chat; count them for broadcast reach.
                    if (packet is OfficialPacket && packet.verified()) PukaarStore.noteBroadcastSeen(packet.id)
                    if (!notify) continue
                    when (packet) {
                        is SosPacket -> if (packet.id !in own) SosNotifier.showNearbySos(context, packet, sosSummary(context, packet))
                        is OfficialPacket -> if (packet.verified()) SosNotifier.showOfficial(context, packet)
                        else -> Unit
                    }
                }
            }
        }
    }

    fun sosSummary(context: Context, packet: SosPacket): String {
        val parts = mutableListOf(context.resources.getQuantityString(R.plurals.pk_people_count, packet.people, packet.people))
        packet.flags.forEach { parts += context.getString(SosManager.flagLabel(it)) }
        val time = DateFormat.getTimeFormat(context).format(java.util.Date(packet.timeSec * 1000))
        return parts.joinToString(" · ") + " · " + time
    }
}
