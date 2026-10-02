package app.pukaar.gateway

import android.content.Context
import android.util.Log
import app.pukaar.data.EmergencyContact
import app.pukaar.data.Profile
import app.pukaar.device.DeviceStatus
import app.pukaar.sos.AckPacket
import app.pukaar.sos.AckStatus
import app.pukaar.sos.MeshBridge
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
    private val knownStatus = mutableMapOf<String, AckStatus>() // others' SOS id → last status broadcast
    private val relayedOfficial = mutableSetOf<String>()
    private val helpedSenders = mutableMapOf<String, Long>()     // sender → last upload time
    private var broadcastsSince = System.currentTimeMillis() / 1000

    fun start(context: Context, scope: CoroutineScope, ownSosIds: () -> Set<String>) {
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
                    if (uploadSos(packet, via = "mesh", relayedBy = myPeer, profile = null, contacts = null) != null) {
                        uploadedSos[packet.id] = packet.seq
                        noteHelped(msg.senderPeerID ?: msg.sender)
                    }
                }
                is SafePacket -> if (packet.id !in ownIds && packet.id !in uploadedSafe) {
                    if (uploadSafe(packet)) uploadedSafe += packet.id
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
        if (othersIds.isNotEmpty()) {
            for (ack in pollStatus(othersIds)) {
                if (knownStatus[ack.id] != ack.status) {
                    knownStatus[ack.id] = ack.status
                    MeshBridge.broadcast(context, ack.encode())
                }
            }
        }

        for (official in fetchBroadcasts()) {
            if (relayedOfficial.add(official.id)) MeshBridge.broadcast(context, official.encode())
        }
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

    /** GET /v1/sos/status?ids=… */
    suspend fun pollStatus(ids: Collection<String>): List<AckPacket> {
        if (!configured || ids.isEmpty()) return emptyList()
        val response = get("/v1/sos/status?ids=" + ids.joinToString(",")) ?: return emptyList()
        return runCatching {
            gson.fromJson(response, JsonObject::class.java).getAsJsonArray("statuses").mapNotNull { el ->
                val o = el.asJsonObject
                val status = statusFromName(o.get("status")?.asString) ?: return@mapNotNull null
                AckPacket(o.get("id").asString, status, o.get("time")?.asLong ?: (System.currentTimeMillis() / 1000), o.get("by")?.asString.orEmpty())
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
                OfficialPacket(o.get("id").asString, o.get("from")?.asString.orEmpty(), o.get("text")?.asString.orEmpty())
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
                if (r.isSuccessful) r.body.string().orEmpty() else null
            }
        }.getOrNull()
    }

    private suspend fun get(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(baseUrl + path).get().build()).execute().use { r ->
                if (r.isSuccessful) r.body.string() else null
            }
        }.getOrNull()
    }
}
