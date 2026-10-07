package app.pukaar.sos

import com.bitchat.android.model.BitchatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class NearbySosTest {
    private val now = 1_790_000_000_000L

    private fun msg(content: String, at: Long = now - 60_000) = BitchatMessage(sender = "peer", content = content, timestamp = Date(at))

    private fun sos(id: String, seq: Int = 1, people: Int = 2) =
        SosPacket(id, seq, 26.15, 85.9, 10, (now / 1000) - 120, 40, people, setOf(SosFlag.Trapped), "Ramesh", "").encode()

    private fun ack(id: String, status: AckStatus, sig: String = "good") = AckPacket(id, status, now / 1000, false, "Control", sig).encode()

    // Signatures are tested in ServerCryptoTest; here "good" stands for a valid one.
    private val verify: (AckPacket) -> Boolean = { it.sig == "good" }

    @Test
    fun `latest update of an SOS wins and it is open`() {
        val list = NearbySosStore.build(listOf(msg(sos("a1", 1, 2)), msg(sos("a1", 2, 5)), msg(sos("a1", 1, 2))), emptySet(), now, verify)
        assertEquals(1, list.size)
        assertEquals(5, list[0].packet.people)
        assertEquals(NearbySosState.Open, list[0].state)
        assertTrue(list[0].open)
    }

    @Test
    fun `safe and signed acks change the state, forged acks do not`() {
        val messages = listOf(
            msg(sos("a1")), msg(SafePacket("a1", now / 1000).encode()),
            msg(sos("b2")), msg(ack("b2", AckStatus.Attending)),
            msg(sos("c3")), msg(ack("c3", AckStatus.Resolved, sig = "forged")),
        )
        val byId = NearbySosStore.build(messages, emptySet(), now, verify).associateBy { it.id }
        assertEquals(NearbySosState.Safe, byId["a1"]!!.state)
        assertEquals(NearbySosState.Attending, byId["b2"]!!.state)
        assertEquals("Control", byId["b2"]!!.handledBy)
        assertTrue(byId["b2"]!!.open) // still needs help until resolved
        assertEquals(NearbySosState.Open, byId["c3"]!!.state)
    }

    @Test
    fun `own SOS and old SOS are left out`() {
        val messages = listOf(msg(sos("mine")), msg(sos("old"), at = now - 25 * 60 * 60 * 1000L), msg(sos("new")))
        val ids = NearbySosStore.build(messages, setOf("mine"), now, verify).map { it.id }
        assertEquals(listOf("new"), ids)
    }
}
