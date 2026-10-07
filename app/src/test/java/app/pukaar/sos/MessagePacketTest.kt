package app.pukaar.sos

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64

/**
 * `PKMSG1` control-room messages. The vector was made with server/functions/src/pukaar-crypto.js
 * `sign()` over `messageSignedText(...)`, using the fixed test sign seed (32 × 0x01) from
 * [ServerCryptoTest].
 */
class MessagePacketTest {
    private val signPub = ServerCrypto.decodeKey("iojj3XQJ8ZX9UtstPLpdcspnCb8dlBIb83SIAbQPb1w")!!
    private val sig = "BaeZp1Jq8StqB0pbp5zicpONAWhH-GJbBiuFzm2Yz8ek3M2-UEQYX0ebN10kZXTD6BD28UbNwJC9dmEtO1Y7Bg"
    private val packet = MessagePacket("k9wd2024", "mfx3k2a", 1_790_000_123, "Kavita Rao", "Boat coming in 20 minutes. Stay on the roof.", sig)

    @Test
    fun `server vector verifies`() {
        assertEquals("PKMSG1|k9wd2024|mfx3k2a|1790000123|Kavita Rao|Boat coming in 20 minutes. Stay on the roof.", packet.signedText())
        assertTrue(packet.verified(signPub))
    }

    @Test
    fun `encode then decode gives the same packet and it still verifies`() {
        val back = MessagePacket.decode(packet.encode())
        assertEquals(packet, back)
        assertTrue(back!!.verified(signPub))
    }

    @Test
    fun `parse returns a MessagePacket so it never shows as chat`() {
        assertEquals(packet, Packets.parse(packet.encode()))
    }

    @Test
    fun `changing text, sender or sos id breaks the signature`() {
        assertFalse(packet.copy(text = "Boat coming in 2 hours. Stay on the roof.").verified(signPub))
        assertFalse(packet.copy(from = "Someone else").verified(signPub))
        assertFalse(packet.copy(sosId = "aaaaaaaa").verified(signPub))
    }

    @Test
    fun `a pipe inside the text survives the round trip`() {
        val withPipe = packet.copy(text = "Go to Rampur school | not the temple")
        assertEquals(withPipe, MessagePacket.decode(withPipe.encode()))
    }

    @Test
    fun `missing fields give null`() {
        assertNull(MessagePacket.decode("PKMSG1|k9wd2024|mfx3k2a|1790000123|$sig|Kavita Rao"))
        assertNull(MessagePacket.decode("PKMSG1|k9wd2024|mfx3k2a|soon|$sig|Kavita Rao|text"))
    }

    @Test
    fun `a message signed by another key is rejected`() {
        val other = Ed25519PrivateKeyParameters(SecureRandom())
        val data = packet.signedText().toByteArray(Charsets.UTF_8)
        val forged = Ed25519Signer().run {
            init(true, other)
            update(data, 0, data.size)
            Base64.getUrlEncoder().withoutPadding().encodeToString(generateSignature())
        }
        val packetByOther = packet.copy(sig = forged)
        assertTrue(packetByOther.verified(other.generatePublicKey().encoded))
        assertFalse(packetByOther.verified(signPub))
    }
}
