package app.pukaar.sos

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.text.format.DateFormat
import app.pukaar.data.PukaarStore
import app.pukaar.device.DeviceStatus
import app.pukaar.device.Locations
import app.pukaar.gateway.Gateway
import com.bitchat.android.R
import com.bitchat.android.services.AppStateStore
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import java.util.UUID

/** What the user entered on the countdown screen. */
data class SosDetails(
    val people: Int = 1,
    val flags: Set<SosFlag> = emptySet(),
    val message: String = "",
)

/** Delivery stages in order (handoff "Delivery states"). */
enum class SosStage { Sending, Relayed, SentByRadio, HelpNotified, RescuerAttending, Resolved }

/** [back] is true when the control room moved the SOS back (undo, or reopened after resolved). */
data class SosEvent(val stage: SosStage, val at: Long, val detail: String? = null, val back: Boolean = false)

data class SosLocation(val lat: Double, val lon: Double, val accuracyM: Int?, val at: Long)

enum class FamilyStatus { Waiting, Sending, SentDirect, SentByServer, Failed }

/** [at] is when the phone's own text was sent or failed (ms), for the report to the server. */
data class FamilyNotice(val contactId: String, val name: String, val phone: String, val status: FamilyStatus, val attempts: Int = 0, val at: Long = 0)

/** A signed message from the control room for this SOS (dashboard D3, `PKMSG1`). */
data class ControlMessage(val id: String, val from: String, val text: String, val at: Long)

data class ActiveSos(
    val id: String,
    val seq: Int,
    val startedAt: Long,
    val details: SosDetails,
    val location: SosLocation?,
    val battery: Int,
    val stage: SosStage,
    val events: List<SosEvent>,
    /** Nearby phones that took the SOS when it was relayed. */
    val relayPeers: Int = 0,
    val family: List<FamilyNotice>,
    val handledBy: String? = null,
    val safeAt: Long? = null,
    val lastMeshSendAt: Long = 0,
    val meshPeersSeen: Set<String> = emptySet(),
    /** Server time (s) of the last status applied, so an older ack arriving late is ignored. */
    val lastAckTime: Long = 0,
    /** Oldest first. Nullable: older saved JSON has no such field. */
    val messages: List<ControlMessage>? = null,
    /** [familyReport] last reported to the server, so each change to the phone's own texts is sent once. */
    val familyReported: String? = null,
) {
    val closed: Boolean get() = safeAt != null || stage == SosStage.Resolved
    val hasLocation: Boolean get() = location != null
}

/**
 * Sends and keeps alive the user's SOS (FR-9 to FR-15).
 *
 * - Mesh: broadcast a [SosPacket] at once, again when new phones come near, and on a backoff timer.
 * - SMS: when there is signal, text each emergency contact directly (FR-10); retried when signal returns.
 * - Server: when online, upload directly with profile and contacts so the server can SMS family (FR-11).
 * - Acks from the server come back as [AckPacket]s, through this phone or a gateway phone.
 */
object SosManager {
    private const val PREFS = "pukaar_sos"
    private val gson = Gson()
    private val mutex = Mutex()
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var scope: CoroutineScope

    private val _active = MutableStateFlow<ActiveSos?>(null)
    /** The current SOS, including one that was just resolved or marked safe (so its screen still shows). */
    val active: StateFlow<ActiveSos?> = _active.asStateFlow()

    /** Ids of every SOS this phone has sent, so the gateway doesn't treat them as someone else's. */
    val ownIds: Set<String> get() = prefs.getStringSet("own_ids", emptySet()).orEmpty()

    fun init(context: Context, scope: CoroutineScope) {
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        this.scope = scope
        // Gson can leave non-null Kotlin fields null if the saved JSON is from an older version.
        @Suppress("SENSELESS_COMPARISON")
        val restored = prefs.getString("active", null)?.let { runCatching { gson.fromJson(it, ActiveSos::class.java) }.getOrNull() }
            ?.takeIf { it.events != null && it.family != null && it.details != null }
        _active.value = restored
            ?.let { s -> s.copy(family = s.family.map { if (it.status == FamilyStatus.Sending) it.copy(status = FamilyStatus.Failed) else it }) }

        // Resend as soon as new phones come into range.
        scope.launch {
            AppStateStore.directPeers.map { it }.distinctUntilChanged().collect { peers -> onPeersChanged(peers) }
        }
        // Background keep-alive: mesh backoff, SMS when signal returns, direct upload when online.
        scope.launch {
            while (true) {
                delay(15_000)
                runCatching { tick() }
            }
        }
        _active.value?.let { if (!it.closed) SosNotifier.showActive(appContext, it) }
    }

    /** Starts a new SOS. Location is fetched here if the countdown didn't already get a fix. */
    fun start(details: SosDetails, knownLocation: Location?) {
        scope.launch {
            val loc = knownLocation ?: Locations.current(appContext, 6_000)
            val now = System.currentTimeMillis()
            val sos = mutex.withLock {
                val contacts = PukaarStore.contacts.value
                ActiveSos(
                    id = UUID.randomUUID().toString().replace("-", "").take(8),
                    seq = 1,
                    startedAt = now,
                    details = details.copy(people = details.people.coerceIn(1, 99)),
                    location = loc?.toSosLocation(),
                    battery = DeviceStatus.batteryNow(),
                    stage = SosStage.Sending,
                    events = listOf(SosEvent(SosStage.Sending, now)),
                    family = contacts.map { FamilyNotice(it.id, it.name, it.phone, FamilyStatus.Waiting) },
                ).also {
                    prefs.edit().putStringSet("own_ids", ownIds + it.id).apply()
                    save(it)
                }
            }
            SosNotifier.showActive(appContext, sos)
            sendOverMesh(force = true)
            sendFamilySms()
            uploadDirect()
        }
    }

    /** "Update details": sends the SOS again with a new sequence number. */
    fun updateDetails(details: SosDetails) {
        scope.launch {
            val loc = Locations.current(appContext, 4_000)
            cachedContacts = null
            update { it.copy(seq = it.seq + 1, details = details, location = loc?.toSosLocation() ?: it.location, battery = DeviceStatus.batteryNow()) }
            sendOverMesh(force = true)
            uploadDirect(force = true)
        }
    }

    /** "I'm safe now": tells the mesh, the server and (if there is signal) family. */
    fun markSafe() {
        scope.launch {
            val sos = _active.value ?: return@launch
            if (sos.safeAt != null) return@launch
            val now = System.currentTimeMillis()
            update { it.copy(safeAt = now) }
            val packet = SafePacket(sos.id, now / 1000)
            MeshBridge.broadcast(appContext, packet.encode())
            if (DeviceStatus.internet.value) Gateway.uploadSafe(packet)
            if (DeviceStatus.cellSignal.value && SmsSender.canSend(appContext)) {
                val text = appContext.getString(R.string.pk_sms_safe, senderName())
                sos.family.filter { it.status == FamilyStatus.SentDirect || it.status == FamilyStatus.SentByServer }
                    .forEach { SmsSender.send(appContext, it.phone, text) }
            }
            SosNotifier.cancelActive(appContext)
        }
    }

    /** Hides a finished SOS from Home once the user has seen it. */
    fun dismiss() {
        val sos = _active.value ?: return
        if (!sos.closed) return
        _active.value = null
        prefs.edit().remove("active").apply()
    }

    /** Called for every Pukaar packet seen on the mesh. */
    fun onPacket(packet: PukaarPacket) {
        val sos = _active.value ?: return
        when (packet) {
            is AckPacket -> {
                if (packet.id != sos.id) return
                // Only the server's signature makes an ack real; anyone on the mesh could forge one.
                if (!packet.verified()) return
                applyServerStatus(packet.status, packet.by, packet.timeSec * 1000, packet.smsSent, ackTimeSec = packet.timeSec)
            }
            is MessagePacket -> {
                if (packet.sosId != sos.id || !packet.verified()) return
                addControlMessage(packet)
            }
            else -> Unit
        }
    }

    private fun addControlMessage(packet: MessagePacket) {
        scope.launch {
            val message = ControlMessage(packet.id, packet.from, packet.text, packet.timeSec * 1000)
            var added = false
            update { s ->
                val list = s.messages.orEmpty()
                if (s.id != packet.sosId || list.any { it.id == message.id }) s
                else s.copy(messages = (list + message).takeLast(MAX_MESSAGES)).also { added = true }
            } ?: return@launch
            if (added) SosNotifier.showControlMessage(appContext, message)
        }
    }

    /**
     * Applies a status from the server. Signed acks ([ackTimeSec] set) are ordered by the server's
     * time, so the control room can move an SOS back (undo, reopen). The direct-upload reply has no
     * server time and only ever moves the SOS forward.
     */
    private fun applyServerStatus(status: AckStatus, by: String, at: Long, smsSent: Boolean = false, ackTimeSec: Long? = null) {
        scope.launch {
            val changed = update { current ->
                val sos = if (smsSent) current.copy(family = current.family.map {
                    if (it.status != FamilyStatus.SentDirect) it.copy(status = FamilyStatus.SentByServer) else it
                }) else current
                val stage = if (ackTimeSec != null) {
                    nextStage(sos.stage, sos.lastAckTime, status, ackTimeSec, safe = sos.safeAt != null) ?: return@update sos
                } else {
                    status.toStage().takeIf { it.ordinal > sos.stage.ordinal } ?: return@update sos
                }
                val timed = if (ackTimeSec != null) sos.copy(lastAckTime = ackTimeSec) else sos
                if (stage == timed.stage) timed
                else timed.copy(
                    stage = stage,
                    handledBy = by.ifBlank { timed.handledBy },
                    events = timed.events + SosEvent(stage, at, by.ifBlank { null }, back = stage.ordinal < timed.stage.ordinal),
                )
            }
            changed?.let { if (it.closed) SosNotifier.cancelActive(appContext) else SosNotifier.showActive(appContext, it) }
        }
    }

    private suspend fun tick() {
        val sos = _active.value ?: return
        reportFamilySms()
        if (sos.closed) return
        val age = System.currentTimeMillis() - sos.startedAt
        val interval = if (age < 5 * 60_000) 30_000L else 120_000L
        if (System.currentTimeMillis() - sos.lastMeshSendAt >= interval) sendOverMesh(force = false)
        sendFamilySms()
        uploadDirect()
    }

    private suspend fun onPeersChanged(peers: Set<String>) {
        val sos = _active.value ?: return
        if (sos.closed) return
        if ((peers - sos.meshPeersSeen).isNotEmpty()) sendOverMesh(force = true)
    }

    private suspend fun sendOverMesh(force: Boolean) {
        val sos = _active.value ?: return
        if (sos.closed) return
        if (!force && System.currentTimeMillis() - sos.lastMeshSendAt < 10_000) return
        val sent = MeshBridge.broadcast(appContext, sos.toPacket().encode())
        // Who to tell travels with the SOS, so the server can text family whichever phone uploads it.
        if (sent) contactsPacket(sos)?.let { MeshBridge.broadcast(appContext, it.encode()) }
        val peers = AppStateStore.directPeers.value
        val now = System.currentTimeMillis()
        val updated = update { s ->
            var next = s.copy(lastMeshSendAt = now, meshPeersSeen = s.meshPeersSeen + peers)
            if (sent && peers.isNotEmpty() && s.stage == SosStage.Sending) {
                next = next.copy(
                    stage = SosStage.Relayed,
                    relayPeers = peers.size,
                    events = s.events + SosEvent(SosStage.Relayed, now, peers.size.toString()),
                )
            }
            next
        }
        updated?.let { SosNotifier.showActive(appContext, it) }
    }

    private suspend fun sendFamilySms() {
        val sos = _active.value ?: return
        if (sos.closed || !DeviceStatus.cellSignal.value || !SmsSender.canSend(appContext)) return
        val text = smsText(sos)
        val due = sos.family.filter { (it.status == FamilyStatus.Waiting || it.status == FamilyStatus.Failed) && it.attempts < 3 }
        if (due.isEmpty()) return
        val dueIds = due.map { it.contactId }.toSet()
        update { s ->
            s.copy(family = s.family.map { if (it.contactId in dueIds) it.copy(status = FamilyStatus.Sending, attempts = it.attempts + 1) else it })
        }
        due.forEach { notice ->
            SmsSender.send(appContext, notice.phone, text, onSent = { ok ->
                scope.launch {
                    update { s -> s.withFamily(notice.contactId) { it.copy(status = if (ok) FamilyStatus.SentDirect else FamilyStatus.Failed, at = System.currentTimeMillis()) } }
                }
            })
        }
    }

    private var lastUploadSeq = 0
    private var lastUploadId = ""

    private suspend fun uploadDirect(force: Boolean = false) {
        val sos = _active.value ?: return
        if (sos.closed || !DeviceStatus.internet.value || !Gateway.configured) return
        if (!force && sos.stage.ordinal >= SosStage.HelpNotified.ordinal && lastUploadId == sos.id && lastUploadSeq >= sos.seq) return
        val result = Gateway.uploadSos(
            sos.toPacket(), via = "direct", relayedBy = null, profile = PukaarStore.profile.value, contacts = PukaarStore.contacts.value,
            locationAtSec = sos.staleLocationAt()?.div(1000),
        ) ?: return
        lastUploadId = sos.id
        lastUploadSeq = sos.seq
        applyServerStatus(result.status, result.by, System.currentTimeMillis(), result.smsSent)
        // The SOS now certainly exists on the server, so texts already sent can be reported.
        reportFamilySms()
    }

    /**
     * Tells the server about family texts this phone sent itself (`POST /v1/sos/sms`), so the
     * dashboard doesn't say "no contacts" for an SOS that came in only through the mesh. Numbers go
     * only over HTTPS from this phone, never over the mesh. A failure (404 while the SOS hasn't
     * reached the server yet) is retried on the next tick.
     */
    private suspend fun reportFamilySms() {
        val sos = _active.value ?: return
        if (!DeviceStatus.internet.value || !Gateway.configured) return
        val report = familyReport(sos.family)
        if (report.isEmpty() || report == sos.familyReported) return
        if (Gateway.reportFamilySms(sos.id, sos.family)) update { if (it.id == sos.id) it.copy(familyReported = report) else it }
    }

    // MARK: helpers

    private const val MAX_MESSAGES = 20

    private var cachedContacts: Pair<String, ContactsPacket?>? = null

    /**
     * The contacts packet for [sos]: sealed to the server's key when one is built in, so relaying
     * phones can't read it. Without a key, only the phone numbers go, in plain form (docs/protocol.md).
     */
    private fun contactsPacket(sos: ActiveSos): ContactsPacket? {
        cachedContacts?.let { (id, packet) -> if (id == sos.id) return packet }
        val profile = PukaarStore.profile.value
        val contacts = PukaarStore.contacts.value.map { mapOf("name" to it.name, "phone" to it.phone) }
        val packet = if (contacts.isEmpty()) null else {
            val key = ServerCrypto.boxKey
            if (key != null) {
                val json = gson.toJson(mapOf(
                    "name" to profile.name, "phone" to profile.phone, "bloodGroup" to profile.bloodGroup,
                    "medicalNotes" to profile.medicalNotes, "contacts" to contacts,
                ))
                ContactsPacket(sos.id, encrypted = true, data = ServerCrypto.seal(json.toByteArray(Charsets.UTF_8), key))
            } else {
                val json = gson.toJson(mapOf("name" to profile.name, "phone" to profile.phone, "contacts" to contacts))
                ContactsPacket(sos.id, encrypted = false, data = ServerCrypto.encode(json.toByteArray(Charsets.UTF_8)))
            }
        }
        cachedContacts = sos.id to packet
        return packet
    }

    private fun ActiveSos.toPacket() = SosPacket(
        id = id,
        seq = seq,
        lat = location?.lat,
        lon = location?.lon,
        accuracyM = location?.accuracyM,
        timeSec = startedAt / 1000,
        battery = battery,
        people = details.people,
        flags = details.flags,
        name = senderName(),
        message = details.message,
    )

    private fun ActiveSos.withFamily(id: String, change: (FamilyNotice) -> FamilyNotice) =
        copy(family = family.map { if (it.contactId == id) change(it) else it })

    private fun Location.toSosLocation() = SosLocation(latitude, longitude, if (hasAccuracy()) accuracy.toInt() else null, time)

    private fun senderName() = PukaarStore.profile.value.name.ifBlank { appContext.getString(R.string.pk_someone) }

    /** The SMS family receives, as previewed in Settings > Emergency contacts (2w). */
    fun smsText(sos: ActiveSos): String = buildSmsText(
        appContext, senderName(), sos.details, sos.location?.lat, sos.location?.lon, sos.startedAt, sos.battery,
    )

    fun buildSmsText(
        context: Context,
        name: String,
        details: SosDetails,
        lat: Double?,
        lon: Double?,
        at: Long,
        battery: Int,
    ): String {
        val res = context.resources
        val parts = mutableListOf(
            context.getString(R.string.pk_sms_sos_from, name),
            res.getQuantityString(R.plurals.pk_sms_people, details.people, details.people),
        )
        if (details.flags.isNotEmpty()) parts += details.flags.joinToString(", ") { context.getString(flagLabel(it)) } + "."
        if (details.message.isNotBlank()) parts += "\"${details.message.trim()}\""
        parts += if (lat != null && lon != null) {
            context.getString(R.string.pk_sms_location, Packets.coord(lat), Packets.coord(lon))
        } else {
            context.getString(R.string.pk_sms_no_location)
        }
        val time = DateFormat.getTimeFormat(context).format(Date(at))
        return parts.joinToString(" ") + " · " + context.getString(R.string.pk_sms_time_battery, time, battery)
    }

    fun flagLabel(flag: SosFlag) = when (flag) {
        SosFlag.Injured -> R.string.pk_flag_injured
        SosFlag.Trapped -> R.string.pk_flag_trapped
        SosFlag.NeedWater -> R.string.pk_flag_water
        SosFlag.NeedMedicine -> R.string.pk_flag_medicine
        SosFlag.ChildOrElderly -> R.string.pk_flag_child
    }

    private suspend fun update(change: (ActiveSos) -> ActiveSos): ActiveSos? = mutex.withLock {
        val current = _active.value ?: return@withLock null
        val next = change(current)
        if (next != current) save(next)
        next
    }

    private fun save(sos: ActiveSos) {
        _active.value = sos
        prefs.edit().putString("active", gson.toJson(sos)).apply()
    }
}

/** Fix time (ms) when the location is more than 2 minutes older than the SOS, else null. */
fun ActiveSos.staleLocationAt(): Long? = location?.at?.takeIf { it > 0 && it < startedAt - 120_000 }

internal fun AckStatus.toStage() = when (this) {
    AckStatus.Notified -> SosStage.HelpNotified
    AckStatus.Attending -> SosStage.RescuerAttending
    AckStatus.Resolved -> SosStage.Resolved
}

/**
 * The stage after applying a signed server ack, or null to ignore it.
 *
 * Acks are ordered by the server's time, not by stage, so an undo ("attended" back to "new") or a
 * reopen ("resolved" back to "attended") reaches the person. An ack that isn't newer than the last
 * one applied is stale. Once the person has said they're safe, the control room can't move the
 * SOS back on their phone: their choice wins.
 */
internal fun nextStage(current: SosStage, lastAckTime: Long, ack: AckStatus, ackTime: Long, safe: Boolean): SosStage? {
    if (ackTime <= lastAckTime) return null
    val target = ack.toStage()
    if (safe && target.ordinal < current.ordinal) return null
    return target
}

/** The phone's own texts worth reporting (sent or failed), as a stable key for "already reported". */
internal fun familyReport(family: List<FamilyNotice>): String =
    family.filter { it.status == FamilyStatus.SentDirect || it.status == FamilyStatus.Failed }
        .joinToString(";") { "${it.phone}=${it.status}" }
