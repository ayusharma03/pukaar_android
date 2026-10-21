package app.pukaar.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * Golden vectors made with server/functions/src/pukaar-crypto.js (Node), using test-only keys
 * built from fixed bytes (sign seed 0x01…, box key 0x02…, ephemeral 0x03…). They prove the app and a
 * Node server agree on signatures and sealed contacts.
 */
class ServerCryptoTest {
    private val signPub = ServerCrypto.decodeKey("iojj3XQJ8ZX9UtstPLpdcspnCb8dlBIb83SIAbQPb1w")!!
    private val boxPub = ServerCrypto.decodeKey("zo060cy2M-x7cMF4FKXHbs0CloUFDTRHRboFhw5YfVk")!!
    private val boxPriv = ServerCrypto.decodeKey("AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI")!!

    private val ackSig = "WLYFIZ31WDv_NJcsnZxi6XBFkE1T0kZecm5bSsSVjqweq-sYjz85cNryi87uJc_Wnu4aKIV9tSmPIAxRZdAVBA"
    private val offSig = "v0xnvoyD4XHFOe1efSyuYphEzHtZBuiJnK_GsXhIGqxX6kSOTce1dyIFxJu6Vp1M627OYK5f7XDJpGAgPJm7AA"
    private val nodeSealed = "Xf7dO2vUf2-ijuFdlp1bsOpTd01Ii9r53xxuASSz7yKWVLcwWF9m9-wLqdn3n8s25cVaChSKAZJvNhWvbAJOD29wHl3XdGXVvgiRu0-A8xNoixKOH0L8bmkdK3wKiroSdk8iexNmRetqSlZYHLwLLRf0x7Mwmglh0J-R8lCA-nqocHW3mH1vszbYx1XiSulWrOepb3dowfZ7"

    private val ack = AckPacket("a1b2c3d4", AckStatus.Attending, 1_790_000_100, smsSent = true, by = "District Control Room, Darbhanga", sig = ackSig)
    private val official = OfficialPacket("b7", "District Control Room", "Boats are going to Rampur school | from 4 pm.", offSig)

    @Test
    fun `server-signed ack verifies`() {
        assertEquals("PKACK1|a1b2c3d4|A|1790000100|1|District Control Room, Darbhanga", ack.signedText())
        assertTrue(ack.verified(signPub))
    }

    @Test
    fun `server-signed official message verifies after a trip through the mesh text`() {
        val relayed = Packets.parse(official.encode()) as OfficialPacket
        assertEquals(official, relayed)
        assertTrue(relayed.verified(signPub))
    }

    @Test
    fun `forged or altered acks are rejected`() {
        assertFalse(ack.copy(status = AckStatus.Resolved).verified(signPub))
        assertFalse(ack.copy(smsSent = false).verified(signPub))
        assertFalse(ack.copy(id = "deadbeef").verified(signPub))
        assertFalse(ack.copy(sig = "").verified(signPub))
        assertFalse(ack.copy(sig = "not-base64!").verified(signPub))
        assertFalse(official.copy(text = "Go to the bridge, it is safe.").verified(signPub))
    }

    @Test
    fun `everything is rejected when no server key is built in`() {
        assertFalse(ack.verified(null))
        assertFalse(official.verified(null))
    }

    @Test
    fun `contacts sealed by a Node server opens in the app`() {
        val json = String(ServerCrypto.open(nodeSealed, boxPriv)!!, Charsets.UTF_8)
        assertEquals("""{"name":"Meena Kumari","phone":"+919845012345","contacts":[{"name":"Suresh Kumar","phone":"9845012399"}]}""", json)
    }

    @Test
    fun `contacts sealed by the app open with the server key and nothing else`() {
        val plain = """{"phone":"+919845012345","contacts":[{"name":"Priya","phone":"9931044821"}]}""".toByteArray()
        val sealed = ServerCrypto.seal(plain, boxPub, SecureRandom())
        assertEquals(String(plain), String(ServerCrypto.open(sealed, boxPriv)!!))
        // A relaying phone has only public keys; a wrong private key gets nothing.
        assertNull(ServerCrypto.open(sealed, ByteArray(32) { 9 }))
        // Tampering is detected.
        val bytes = java.util.Base64.getUrlDecoder().decode(sealed).also { it[40] = (it[40].toInt() xor 1).toByte() }
        assertNull(ServerCrypto.open(ServerCrypto.encode(bytes), boxPriv))
    }

    @Test
    fun `contacts packet round trips`() {
        val packet = ContactsPacket("a1b2c3d4", encrypted = true, data = nodeSealed)
        assertEquals("PKCT1|a1b2c3d4|e|$nodeSealed", packet.encode())
        assertEquals(packet, Packets.parse(packet.encode()))
        assertNull(Packets.parse("PKCT1|a1b2c3d4|x|data"))
    }

    @Test
    fun `keys must be 32 bytes`() {
        assertNotNull(ServerCrypto.decodeKey("iojj3XQJ8ZX9UtstPLpdcspnCb8dlBIb83SIAbQPb1w"))
        assertNull(ServerCrypto.decodeKey(""))
        assertNull(ServerCrypto.decodeKey("c2hvcnQ"))
    }
}
