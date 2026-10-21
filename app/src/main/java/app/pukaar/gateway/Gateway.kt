package app.pukaar.gateway

import android.content.Context
import android.util.Log
import app.pukaar.data.EmergencyContact
import app.pukaar.data.Profile
import app.pukaar.device.DeviceStatus
import app.pukaar.sos.AckPacket
import app.pukaar.sos.AckStatus
import app.pukaar.data.PukaarStore
import app.pukaar.sos.ContactsPacket
import app.pukaar.sos.FamilyNotice
import app.pukaar.sos.FamilyStatus
import app.pukaar.sos.MeshBridge
import app.pukaar.sos.MeshHops
import app.pukaar.sos.MessagePacket
import app.pukaar.sos.OfficialPacket
import app.pukaar.sos.Packets
import app.pukaar.sos.SafePacket
import app.pukaar.sos.SosPacket
import com.bitchat.android.BuildConfig
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.services.AppStateStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** What the server said about an SOS. */
data class SosServerStatus(val status: AckStatus, val by: String, val smsSent: Boolean)

/** One SOS in the status poll: its signed ack and the latest control-room messages (up to 3). */
data class StatusResult(val ack: AckPacket, val messages: List<MessagePacket>)

/**
 * Body of `POST /v1/sos/sms`: the family texts the phone sent itself. Only finished texts are
 * reported (sent or failed); waiting ones and those the server sent are left out.
 */
internal fun familyReportBody(sosId: String, family: List<FamilyNotice>, nowSec: Long = System.currentTimeMillis() / 1000): Map<String, Any> =
    mapOf(
        "id" to sosId,
        "results" to family
            .filter { it.status == FamilyStatus.SentDirect || it.status == FamilyStatus.Failed }
            .take(5)
            .map {
                mapOf(
                    "name" to it.name,
                    "phone" to it.phone,
                    "ok" to (it.status == FamilyStatus.SentDirect),
                    "time" to (if (it.at > 0) it.at / 1000 else nowSec),
                )
            },
    )

/**
 * Uploads to the rescuer server when this phone has internet (FR-4), and brings the server's
 * replies back into the mesh. The server is any HTTPS backend (for example Firebase Cloud
 * Functions) that implements the contract in docs/protocol.md. Set its base URL with the Gradle
 * property `PUKAAR_GATEWAY_URL`; with no URL the gateway stays off and SOS still go over mesh and SMS.
 */
object Gateway {
    private const val TAG = "PukaarGateway"
    private val baseUrl: String = BuildConfig.PUKAAR_GATEWAY_URL.trimEnd('/')
    val configured: Boolean get() = baseUrl.isNotBlank()

    private val gson = Gson()
    private val json = "application/json".toMediaType()
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Public chat message ids this phone has uploaded, for "Help notified" on own messages. */
    private val _uploadedMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val uploadedMessageIds: StateFlow<Set<String>> = _uploadedMessageIds.asStateFlow()

    private val uploadedSos = mutableMapOf<String, Int>()      // others' SOS id → highest seq uploaded
    private val uploadedSafe = mutableSetOf<String>()
    private val uploadedContacts = mutableSetOf<String>()
    private val knownStatus = mutableMapOf<String, AckStatus>() // others' SOS id → last status broadcast
    private val relayedOfficial = mutableSetOf<String>()
    private val relayedMessages = mutableSetOf<String>()        // control-room message ids sent into the mesh
    private val helpedSenders = mutableMapOf<String, Long>()     // sender → last upload time
    private var broadcastsSince = System.currentTimeMillis() / 1000
    private var activeOwnSosId: () -> String? = { null }
    private var onOwnAck: (AckPacket) -> Unit = {}
    private var onOwnMessage: (MessagePacket) -> Unit = {}

    /**
     * @param ownSosIds every SOS this phone has sent (never re-uploaded as someone else's)
     * @param activeOwnSosId this phone's current SOS, whose status is polled while online
     * @param onOwnAck signed status updates for that SOS
     * @param onOwnMessage signed control-room messages for that SOS
     */
    fun start(
        context: Context,
        scope: CoroutineScope,
        ownSosIds: () -> Set<String>,
        activeOwnSosId: () -> String? = { null },
        onOwnAck: (AckPacket) -> Unit = {},
        onOwnMessage: (MessagePacket) -> Unit = {},
    ) {
        this.activeOwnSosId = activeOwnSosId
        this.onOwnAck = onOwnAck
        this.onOwnMessage = onOwnMessage
        if (!configured) {
            Log.i(TAG, "No PUKAAR_GATEWAY_URL set; gateway off")
            return
        }
        scope.launch(Dispatchers.IO) {
            while (true) {
                if (DeviceStatus.internet.value) {
                    runCatching { relayRound(context, ownSosIds()) }.onFailure { Log.w(TAG, "Gateway round failed: ${it.message}") }
                }
                val hourAgo = System.currentTimeMillis() - 3_600_000
                synchronized(helpedSenders) {
                    helpedSenders.values.removeAll { it < hourAgo }
                    DeviceStatus.gatewayHelping.value = helpedSenders.size
                }
                delay(30_000)
            }
        }
    }

    /** One pass: upload what the mesh brought us, then bring server replies back into the mesh. */
    private suspend fun relayRound(context: Context, ownIds: Set<String>) {
        val messages = AppStateStore.publicMessages.value
        val myPeer = MeshBridge.myPeerId(context)

        val chat = mutableListOf<BitchatMessage>()
        for (msg in messages) {
            when (val packet = Packets.parse(msg.content)) {
                is SosPacket -> if (packet.id !in ownIds && (uploadedSos[packet.id] ?: -1) < packet.seq) {
                    if (uploadSos(packet, via = "mesh", relayedBy = myPeer, profile = null, contacts = null, hops = MeshHops.get(msg.id)) != null) {
                        uploadedSos[packet.id] = packet.seq
                        noteHelped(msg.senderPeerID ?: msg.sender)
                    }
                }
                is SafePacket -> if (packet.id !in ownIds && packet.id !in uploadedSafe) {
                    if (uploadSafe(packet)) uploadedSafe += packet.id
                }
                is ContactsPacket -> if (packet.id !in ownIds && packet.id !in uploadedContacts) {
                    if (uploadContacts(packet)) uploadedContacts += packet.id
                }
                null -> if (msg.id !in _uploadedMessageIds.value) chat += msg
                else -> Unit
            }
        }
        if (chat.isNotEmpty() && uploadMessages(chat)) {
            _uploadedMessageIds.value = _uploadedMessageIds.value + chat.map { it.id }
            chat.filter { it.senderPeerID != myPeer }.forEach { noteHelped(it.senderPeerID ?: it.sender) }
        }

        // Status changes for SOS this phone relayed go back into the mesh so the sender learns.
        val othersIds = uploadedSos.keys.toList()
        val ownId = activeOwnSosId()
        val pollIds = othersIds + listOfNotNull(ownId)
        if (pollIds.isNotEmpty()) {
            for ((ack, controlMessages) in pollStatus(pollIds)) {
                // Relay only what the server really signed; phones drop anything else anyway.
                val messagesOk = controlMessages.filter { it.verified() }
                if (ack.id == ownId) {
                    if (ack.verified()) onOwnAck(ack)
                    messagesOk.forEach(onOwnMessage)
                    continue
                }
                if (ack.verified() && knownStatus[ack.id] != ack.status) {
                    knownStatus[ack.id] = ack.status
                    MeshBridge.broadcast(context, ack.encode())
                }
                // The phone that brought the SOS in is the one most likely to reach the sender again.
                messagesOk.forEach { if (relayedMessages.add(it.id)) MeshBridge.broadcast(context, it.encode()) }
            }
        }

        for (official in fetchBroadcasts()) {
            if (!official.verified()) continue
            if (relayedOfficial.add(official.id)) MeshBridge.broadcast(context, official.encode())
        }

        reportBroadcastsSeen()
    }

    /** POST /v1/broadcasts/seen: which official messages this phone has shown, for reach on the dashboard. */
    private suspend fun reportBroadcastsSeen() {
        val ids = PukaarStore.unreportedBroadcasts().take(20)
        if (ids.isEmpty()) return
        if (post("/v1/broadcasts/seen", gson.toJson(mapOf("device" to PukaarStore.installId(), "ids" to ids))) != null) {
            PukaarStore.markBroadcastsReported(ids)
        }
    }

    /** POST /v1/sos/sms: family texts the phone sent itself. Over HTTPS only; the body has phone numbers. */
    suspend fun reportFamilySms(sosId: String, family: List<FamilyNotice>): Boolean {
        if (!configured) return false
        val body = familyReportBody(sosId, family)
        if ((body["results"] as List<*>).isEmpty()) return true
        return post("/v1/sos/sms", gson.toJson(body)) != null
    }

    private fun noteHelped(sender: String) {
        synchronized(helpedSenders) {
            helpedSenders[sender] = System.currentTimeMillis()
            DeviceStatus.gatewayHelping.value = helpedSenders.size
        }
    }

    /** POST /v1/sos. Profile and contacts are only sent for this phone's own SOS, never over mesh. */
    suspend fun uploadSos(
        packet: SosPacket,
        via: String,
        relayedBy: String?,
        profile: Profile?,
        contacts: List<EmergencyContact>?,
        hops: Int? = null,
        locationAtSec: Long? = null,
    ): SosServerStatus? {
        if (!configured) return null
        val body = JsonObject().apply {
            addProperty("id", packet.id)
            addProperty("seq", packet.seq)
            packet.lat?.let { addProperty("lat", it) }
            packet.lon?.let { addProperty("lon", it) }
            packet.accuracyM?.let { addProperty("accuracyM", it) }
            addProperty("time", packet.timeSec)
            addProperty("battery", packet.battery)
            addProperty("people", packet.people)
            add("flags", gson.toJsonTree(packet.flags.map { it.name }))
            addProperty("name", packet.name)
            addProperty("message", packet.message)
            addProperty("via", via)
            relayedBy?.let { addProperty("relayedBy", it) }
            // Bluetooth links the copy travelled (mesh uploads only), and how old the fix is when it's stale.
            if (via == "mesh") hops?.takeIf { it in 0..50 }?.let { addProperty("hops", it) }
            locationAtSec?.let { addProperty("locationAt", it) }
            profile?.let {
                addProperty("phone", it.phone)
                it.bloodGroup?.let { b -> addProperty("bloodGroup", b) }
                addProperty("medicalNotes", it.medicalNotes)
            }
            contacts?.let { list ->
                add("contacts", gson.toJsonTree(list.map { mapOf("name" to it.name, "phone" to it.phone) }))
            }
        }
        val response = post("/v1/sos", body.toString()) ?: return null
        return runCatching {
            val obj = gson.fromJson(response, JsonObject::class.java)
            SosServerStatus(
                status = statusFromName(obj.get("status")?.asString) ?: AckStatus.Notified,
                by = obj.get("by")?.asString.orEmpty(),
                smsSent = obj.get("smsSent")?.asBoolean ?: false,
            )
        }.getOrDefault(SosServerStatus(AckStatus.Notified, "", false))
    }

    /** POST /v1/contacts: who to text for an SOS, sealed to the server's key (or plain if none is built in). */
    private suspend fun uploadContacts(packet: ContactsPacket): Boolean =
        post("/v1/contacts", gson.toJson(mapOf("id" to packet.id, "encrypted" to packet.encrypted, "data" to packet.data))) != null

    suspend fun uploadSafe(packet: SafePacket): Boolean =
        post("/v1/safe", gson.toJson(mapOf("id" to packet.id, "time" to packet.timeSec))) != null

    private suspend fun uploadMessages(list: List<BitchatMessage>): Boolean {
        val items = list.map { msg ->
            val (text, loc) = Packets.splitLocation(msg.content)
            buildMap {
                put("id", msg.id)
                put("sender", msg.sender)
                put("text", text)
                put("time", msg.timestamp.time / 1000)
                loc?.let { put("lat", it.first); put("lon", it.second) }
            }
        }
        return post("/v1/messages", gson.toJson(mapOf("messages" to items))) != null
    }

    /** GET /v1/sos/status?ids=…: each SOS's signed status, plus its latest control-room messages. */
    suspend fun pollStatus(ids: Collection<String>): List<StatusResult> {
        if (!configured || ids.isEmpty()) return emptyList()
        val response = get("/v1/sos/status?ids=" + ids.joinToString(",")) ?: return emptyList()
        return runCatching {
            gson.fromJson(response, JsonObject::class.java).getAsJsonArray("statuses").mapNotNull { el ->
                val o = el.asJsonObject
                val status = statusFromName(o.get("status")?.asString) ?: return@mapNotNull null
                val id = o.get("id").asString
                val ack = AckPacket(
                    id = id,
                    status = status,
                    timeSec = o.get("time")?.asLong ?: return@mapNotNull null,
                    smsSent = o.get("smsSent")?.asBoolean ?: false,
                    by = o.get("by")?.asString.orEmpty(),
                    sig = o.get("sig")?.asString.orEmpty(),
                )
                val messages = o.getAsJsonArray("messages")?.mapNotNull { m ->
                    runCatching {
                        val mo = m.asJsonObject
                        MessagePacket(
                            sosId = id,
                            id = mo.get("id").asString,
                            timeSec = mo.get("time").asLong,
                            from = mo.get("from")?.asString.orEmpty(),
                            text = mo.get("text")?.asString.orEmpty(),
                            sig = mo.get("sig")?.asString.orEmpty(),
                        )
                    }.getOrNull()
                }.orEmpty()
                StatusResult(ack, messages)
            }
        }.getOrDefault(emptyList())
    }

    /** GET /v1/broadcasts?since=… — official messages from the dashboard (D4). */
    private suspend fun fetchBroadcasts(): List<OfficialPacket> {
        val response = get("/v1/broadcasts?since=$broadcastsSince") ?: return emptyList()
        return runCatching {
            val items = gson.fromJson(response, JsonObject::class.java).getAsJsonArray("items")
            items.map { el ->
                val o = el.asJsonObject
                o.get("time")?.asLong?.let { t -> if (t > broadcastsSince) broadcastsSince = t }
                OfficialPacket(o.get("id").asString, o.get("from")?.asString.orEmpty(), o.get("text")?.asString.orEmpty(), o.get("sig")?.asString.orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    private fun statusFromName(name: String?) = when (name?.lowercase()) {
        "new", "notified" -> AckStatus.Notified
        "attended", "attending" -> AckStatus.Attending
        "resolved" -> AckStatus.Resolved
        else -> null
    }

    private suspend fun post(path: String, body: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(baseUrl + path).post(body.toRequestBody(json)).build()).execute().use { r ->
                if (r.isSuccessful) r.body.string().orEmpty() else null.also { Log.w(TAG, "POST $path -> HTTP ${r.code}") }
            }
        }.onFailure { Log.w(TAG, "POST $path failed: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull()
    }

    private suspend fun get(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(baseUrl + path).get().build()).execute().use { r ->
                if (r.isSuccessful) r.body.string() else null.also { Log.w(TAG, "GET $path -> HTTP ${r.code}") }
            }
        }.onFailure { Log.w(TAG, "GET $path failed: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull()
    }
}
