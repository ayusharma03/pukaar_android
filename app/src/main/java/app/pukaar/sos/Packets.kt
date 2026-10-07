package app.pukaar.sos

import java.util.Locale

/**
 * Pukaar messages carried inside bitchat public mesh messages. Text-based so they survive any
 * bitchat client, and small: an SOS packet fits in one LoRa packet (NFR-3, 200 bytes).
 * Format reference: docs/protocol.md.
 */
object Packets {
    const val SOS = "PKSOS1"
    const val SAFE = "PKSAFE1"
    const val ACK = "PKACK1"
    const val OFFICIAL = "PKOFF1"
    const val CONTACTS = "PKCT1"
    const val MESSAGE = "PKMSG1"
    const val MAX_SOS_BYTES = 200
    private const val MAX_NAME_BYTES = 24
    private val LOCATION_TAG = Regex("""\s*\[loc:(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)]\s*$""")

    fun parse(content: String): PukaarPacket? = when {
        content.startsWith("$SOS|") -> SosPacket.decode(content)
        content.startsWith("$SAFE|") -> SafePacket.decode(content)
        content.startsWith("$ACK|") -> AckPacket.decode(content)
        content.startsWith("$OFFICIAL|") -> OfficialPacket.decode(content)
        content.startsWith("$CONTACTS|") -> ContactsPacket.decode(content)
        content.startsWith("$MESSAGE|") -> MessagePacket.decode(content)
        else -> null
    }

    /** Appends a location tag to a chat message (FR-8). */
    fun withLocation(text: String, lat: Double, lon: Double): String =
        "$text [loc:${coord(lat)},${coord(lon)}]"

    /** Splits a chat message into its text and attached location, if any. */
    fun splitLocation(content: String): Pair<String, Pair<Double, Double>?> {
        val match = LOCATION_TAG.find(content) ?: return content to null
        val lat = match.groupValues[1].toDoubleOrNull()
        val lon = match.groupValues[2].toDoubleOrNull()
        if (lat == null || lon == null) return content to null
        return content.removeRange(match.range).trimEnd() to (lat to lon)
    }

    internal fun coord(value: Double) = String.format(Locale.US, "%.5f", value)

    /** Truncates to at most [maxBytes] UTF-8 bytes without splitting a character. */
    fun truncateBytes(text: String, maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
        val out = StringBuilder()
        var used = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val chars = Character.charCount(cp)
            val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (used + bytes > maxBytes) break
            out.appendCodePoint(cp)
            used += bytes
            i += chars
        }
        return out.toString()
    }

    internal fun clean(text: String) = text.replace('|', '/').replace('\n', ' ').trim()

    internal fun nameField(name: String) = truncateBytes(clean(name), MAX_NAME_BYTES)
}

sealed interface PukaarPacket

enum class SosFlag(val bit: Int) {
    Injured(1), Trapped(2), NeedWater(4), NeedMedicine(8), ChildOrElderly(16);

    companion object {
        fun fromMask(mask: Int) = entries.filter { mask and it.bit != 0 }.toSet()
        fun toMask(flags: Set<SosFlag>) = flags.fold(0) { acc, f -> acc or f.bit }
    }
}

/**
 * `PKSOS1|id|seq|lat,lon|acc|time|battery|people|flags|name|message`
 * Location fields are empty when there is no fix. `message` is last so it may contain anything.
 */
data class SosPacket(
    val id: String,
    val seq: Int,
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Int?,
    val timeSec: Long,
    val battery: Int,
    val people: Int,
    val flags: Set<SosFlag>,
    val name: String,
    val message: String,
) : PukaarPacket {

    fun encode(): String {
        val head = listOf(
            Packets.SOS,
            id,
            seq.toString(),
            if (lat != null && lon != null) "${Packets.coord(lat)},${Packets.coord(lon)}" else "",
            accuracyM?.toString() ?: "",
            timeSec.toString(),
            battery.toString(),
            people.toString(),
            SosFlag.toMask(flags).toString(),
            Packets.nameField(name),
        ).joinToString("|") + "|"
        val room = Packets.MAX_SOS_BYTES - head.toByteArray(Charsets.UTF_8).size
        return head + Packets.truncateBytes(Packets.clean(message), room)
    }

    companion object {
        /** Bytes left for the custom message after the fixed fields, for the composer's counter. */
        fun messageBudget(name: String): Int {
            val sample = SosPacket("00000000", 99, -90.0, -180.0, 9999, 9_999_999_999, 100, 99, SosFlag.entries.toSet(), name, "")
            return Packets.MAX_SOS_BYTES - sample.encode().toByteArray(Charsets.UTF_8).size
        }

        fun decode(content: String): SosPacket? {
            val parts = content.split("|", limit = 11)
            if (parts.size < 11 || parts[0] != Packets.SOS) return null
            val coords = parts[3].split(",").mapNotNull { it.toDoubleOrNull() }
            return runCatching {
                SosPacket(
                    id = parts[1],
                    seq = parts[2].toInt(),
                    lat = coords.getOrNull(0),
                    lon = coords.getOrNull(1),
                    accuracyM = parts[4].toIntOrNull(),
                    timeSec = parts[5].toLong(),
                    battery = parts[6].toInt(),
                    people = parts[7].toInt(),
                    flags = SosFlag.fromMask(parts[8].toInt()),
                    name = parts[9],
                    message = parts[10],
                )
            }.getOrNull()
        }
    }
}

/** `PKSAFE1|id|time`: the sender marked themselves safe (FR-15). */
data class SafePacket(val id: String, val timeSec: Long) : PukaarPacket {
    fun encode() = "${Packets.SAFE}|$id|$timeSec"

    companion object {
        fun decode(content: String): SafePacket? {
            val p = content.split("|")
            if (p.size < 3) return null
            return p[2].toLongOrNull()?.let { SafePacket(p[1], it) }
        }
    }
}

enum class AckStatus(val code: String) {
    Notified("N"), Attending("A"), Resolved("R");

    companion object {
        fun fromCode(code: String) = entries.firstOrNull { it.code == code }
    }
}

/**
 * `PKACK1|id|status|time|sms|sig|by`: the server updated an SOS. Gateways broadcast it back into the mesh.
 * `sms` is 1 when the server has texted the family. `sig` is the server's Ed25519 signature over
 * [signedText], the packet without the `sig` field. Unsigned or badly signed acks must be ignored.
 */
data class AckPacket(
    val id: String,
    val status: AckStatus,
    val timeSec: Long,
    val smsSent: Boolean,
    val by: String,
    val sig: String,
) : PukaarPacket {
    fun signedText() = "${Packets.ACK}|$id|${status.code}|$timeSec|${if (smsSent) 1 else 0}|${Packets.clean(by)}"
    fun encode() = "${Packets.ACK}|$id|${status.code}|$timeSec|${if (smsSent) 1 else 0}|$sig|${Packets.clean(by)}"
    fun verified(key: ByteArray? = ServerCrypto.signKey) = ServerCrypto.verify(signedText(), sig, key)

    companion object {
        fun decode(content: String): AckPacket? {
            val p = content.split("|", limit = 7)
            if (p.size < 7) return null
            val status = AckStatus.fromCode(p[2]) ?: return null
            val time = p[3].toLongOrNull() ?: return null
            return AckPacket(p[1], status, time, p[4] == "1", p[6], p[5])
        }
    }
}

/**
 * `PKOFF1|id|sig|from|text`: an official broadcast from the dashboard, relayed by a gateway phone.
 * `sig` signs [signedText] (`PKOFF1|id|from|text`). Unsigned or badly signed ones must be ignored.
 */
data class OfficialPacket(val id: String, val from: String, val text: String, val sig: String) : PukaarPacket {
    fun signedText() = "${Packets.OFFICIAL}|$id|${Packets.clean(from)}|$text"
    fun encode() = "${Packets.OFFICIAL}|$id|$sig|${Packets.clean(from)}|$text"
    fun verified(key: ByteArray? = ServerCrypto.signKey) = ServerCrypto.verify(signedText(), sig, key)

    companion object {
        fun decode(content: String): OfficialPacket? {
            val p = content.split("|", limit = 5)
            if (p.size < 5) return null
            return OfficialPacket(p[1], p[3], p[4], p[2])
        }
    }
}

/**
 * `PKCT1|id|mode|data`: who to tell about SOS `id`, so the server can text family even when the
 * SOS reached it only through other phones (FR-11). `mode` is `e` when `data` is sealed to the
 * server's key ([ServerCrypto.seal]); `p` when no server key is built in and `data` is plain
 * base64url JSON with only the phone numbers. See docs/protocol.md.
 */
data class ContactsPacket(val id: String, val encrypted: Boolean, val data: String) : PukaarPacket {
    fun encode() = "${Packets.CONTACTS}|$id|${if (encrypted) "e" else "p"}|$data"

    companion object {
        fun decode(content: String): ContactsPacket? {
            val p = content.split("|", limit = 4)
            if (p.size < 4 || (p[2] != "e" && p[2] != "p")) return null
            return ContactsPacket(p[1], p[2] == "e", p[3])
        }
    }
}

/**
 * `PKMSG1|sosId|msgId|time|sig|from|text`: a control-room message for the person who sent SOS
 * `sosId` (dashboard D3), relayed by a gateway phone. `sig` signs [signedText]
 * (`PKMSG1|sosId|msgId|time|from|text`). Unsigned or badly signed ones must be ignored.
 */
data class MessagePacket(
    val sosId: String,
    val id: String,
    val timeSec: Long,
    val from: String,
    val text: String,
    val sig: String,
) : PukaarPacket {
    fun signedText() = "${Packets.MESSAGE}|$sosId|$id|$timeSec|${Packets.clean(from)}|$text"
    fun encode() = "${Packets.MESSAGE}|$sosId|$id|$timeSec|$sig|${Packets.clean(from)}|$text"
    fun verified(key: ByteArray? = ServerCrypto.signKey) = ServerCrypto.verify(signedText(), sig, key)

    companion object {
        fun decode(content: String): MessagePacket? {
            val p = content.split("|", limit = 7)
            if (p.size < 7) return null
            val time = p[3].toLongOrNull() ?: return null
            return MessagePacket(p[1], p[2], time, p[5], p[6], p[4])
        }
    }
}
