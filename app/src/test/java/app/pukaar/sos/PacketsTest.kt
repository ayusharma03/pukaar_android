package app.pukaar.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketsTest {

    private val sample = SosPacket(
        id = "a1b2c3d4",
        seq = 2,
        lat = 25.9814,
        lon = 85.6721,
        accuracyM = 12,
        timeSec = 1_790_000_000,
        battery = 30,
        people = 4,
        flags = setOf(SosFlag.Trapped, SosFlag.ChildOrElderly),
        name = "Meena Kumari",
        message = "On the roof of the blue house",
    )

    @Test
    fun `sos packet encodes to the documented golden string`() {
        assertEquals(
            "PKSOS1|a1b2c3d4|2|25.98140,85.67210|12|1790000000|30|4|18|Meena Kumari|On the roof of the blue house",
            sample.encode(),
        )
    }

    @Test
    fun `sos packet round trips`() {
        assertEquals(sample, SosPacket.decode(sample.encode()))
        assertEquals(sample, Packets.parse(sample.encode()))
    }

    @Test
    fun `sos packet without location round trips with null coordinates`() {
        val noFix = sample.copy(lat = null, lon = null, accuracyM = null)
        val decoded = SosPacket.decode(noFix.encode())!!
        assertNull(decoded.lat)
        assertNull(decoded.lon)
        assertNull(decoded.accuracyM)
    }

    @Test
    fun `sos packet never exceeds one LoRa packet even with a long Hindi message`() {
        val long = sample.copy(name = "बहुत लंबा नाम वाला व्यक्ति जिसका नाम लंबा है", message = "छत पर हैं, पानी बढ़ रहा है। ".repeat(20))
        val bytes = long.encode().toByteArray(Charsets.UTF_8).size
        assertTrue("SOS packet was $bytes bytes", bytes <= Packets.MAX_SOS_BYTES)
        // Truncation must not split a character.
        assertTrue(SosPacket.decode(long.encode())!!.message.none { it == '�' })
    }

    @Test
    fun `message budget leaves room for the fixed fields`() {
        val budget = SosPacket.messageBudget("Meena Kumari")
        val filled = sample.copy(message = "x".repeat(budget), seq = 99, battery = 100, people = 99, accuracyM = 9999, flags = SosFlag.entries.toSet())
        assertEquals("x".repeat(budget), SosPacket.decode(filled.encode())!!.message)
    }

    @Test
    fun `pipes and newlines in the message cannot break the fields`() {
        val tricky = sample.copy(name = "A|B", message = "line1\nline2 | PKACK1|x|R|0|me")
        val decoded = SosPacket.decode(tricky.encode())!!
        assertEquals("A/B", decoded.name)
        assertEquals("line1 line2 / PKACK1/x/R/0/me", decoded.message)
        assertEquals(sample.flags, decoded.flags)
    }

    @Test
    fun `flags mask round trips every combination`() {
        for (mask in 0 until 32) assertEquals(mask, SosFlag.toMask(SosFlag.fromMask(mask)))
    }

    @Test
    fun `ack safe and official packets round trip`() {
        val ack = AckPacket("a1b2c3d4", AckStatus.Attending, 1_790_000_100, smsSent = true, by = "District Control Room, Darbhanga", sig = "SIG")
        assertEquals("PKACK1|a1b2c3d4|A|1790000100|1|SIG|District Control Room, Darbhanga", ack.encode())
        assertEquals(ack, Packets.parse(ack.encode()))

        val safe = SafePacket("a1b2c3d4", 1_790_000_200)
        assertEquals("PKSAFE1|a1b2c3d4|1790000200", safe.encode())
        assertEquals(safe, Packets.parse(safe.encode()))

        val official = OfficialPacket("b7", "District Control Room", "Boats are going to Rampur school | from 4 pm.", "SIG")
        assertEquals(official, Packets.parse(official.encode()))
    }

    @Test
    fun `ordinary chat text and malformed packets are not parsed as packets`() {
        assertNull(Packets.parse("hello everyone"))
        assertNull(Packets.parse("PKSOS1|too|few"))
        assertNull(Packets.parse("PKACK1|id|Z|1|x"))
        assertNull(Packets.parse("PKSOS1|id|notanumber||||||||"))
    }

    @Test
    fun `location tag is added and split back out`() {
        val content = Packets.withLocation("Water near the temple", 25.98141, 85.67209)
        assertEquals("Water near the temple [loc:25.98141,85.67209]", content)
        val (text, loc) = Packets.splitLocation(content)
        assertEquals("Water near the temple", text)
        assertEquals(25.98141, loc!!.first, 1e-9)
        assertEquals(85.67209, loc.second, 1e-9)
        assertEquals("no tag" to null, Packets.splitLocation("no tag"))
    }

    @Test
    fun `truncateBytes keeps whole characters`() {
        assertEquals("पु", Packets.truncateBytes("पुकार", 7))
        assertEquals("", Packets.truncateBytes("abc", 0))
        assertEquals("abc", Packets.truncateBytes("abc", 10))
    }
}
