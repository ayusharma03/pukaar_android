package app.pukaar.sos

import app.pukaar.gateway.familyReportBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure rules behind SosManager and Gateway: ack ordering (undo, reopen) and the family-text report. */
class SosStatusRulesTest {

    @Test
    fun `newer ack moves forward`() {
        assertEquals(SosStage.RescuerAttending, nextStage(SosStage.HelpNotified, 100, AckStatus.Attending, 200, safe = false))
    }

    @Test
    fun `undo moves back to help notified, never to sending`() {
        assertEquals(SosStage.HelpNotified, nextStage(SosStage.RescuerAttending, 200, AckStatus.Notified, 300, safe = false))
    }

    @Test
    fun `reopen from resolved goes back to attending`() {
        assertEquals(SosStage.RescuerAttending, nextStage(SosStage.Resolved, 300, AckStatus.Attending, 400, safe = false))
    }

    @Test
    fun `reopen is ignored once the person said they are safe`() {
        assertNull(nextStage(SosStage.Resolved, 300, AckStatus.Attending, 400, safe = true))
    }

    @Test
    fun `stale and same-time acks are ignored`() {
        assertNull(nextStage(SosStage.RescuerAttending, 300, AckStatus.Notified, 200, safe = false))
        assertNull(nextStage(SosStage.RescuerAttending, 300, AckStatus.Notified, 300, safe = false))
    }

    @Test
    fun `first signed ack after local stages applies`() {
        assertEquals(SosStage.HelpNotified, nextStage(SosStage.Relayed, 0, AckStatus.Notified, 100, safe = false))
    }

    @Test
    fun `family report includes only the phone's finished texts`() {
        val family = listOf(
            FamilyNotice("1", "Raju Kumar", "9835122140", FamilyStatus.SentDirect, at = 1_790_000_200_000),
            FamilyNotice("2", "Priya Singh", "+919812345678", FamilyStatus.Failed, at = 1_790_000_210_000),
            FamilyNotice("3", "Waiting", "1", FamilyStatus.Waiting),
            FamilyNotice("4", "Sending", "2", FamilyStatus.Sending),
            FamilyNotice("5", "By server", "3", FamilyStatus.SentByServer),
        )
        val body = familyReportBody("k9wd2024", family, nowSec = 1)
        assertEquals("k9wd2024", body["id"])
        assertEquals(
            listOf(
                mapOf("name" to "Raju Kumar", "phone" to "9835122140", "ok" to true, "time" to 1_790_000_200L),
                mapOf("name" to "Priya Singh", "phone" to "+919812345678", "ok" to false, "time" to 1_790_000_210L),
            ),
            body["results"],
        )
        assertEquals("9835122140=SentDirect;+919812345678=Failed", familyReport(family))
    }
}
