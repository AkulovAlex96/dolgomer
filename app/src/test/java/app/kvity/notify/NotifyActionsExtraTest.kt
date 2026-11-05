package app.kvity.notify

import app.kvity.data.CalendarEvent
import app.kvity.data.EventKind
import app.kvity.data.MoneyFlow
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyActionsExtraTest {
    private val d = LocalDate.of(2026, 10, 20)
    private fun event(
        kind: EventKind, flow: MoneyFlow, shareId: String? = null, period: LocalDate? = null,
        markable: Boolean = false,
    ) = CalendarEvent(d, kind, flow, "X", null, 5000, "RUB", false, "ref", shareId, period, false, markable)

    @Test fun `remind request for debt and share, none for outgoing or incomplete`() {
        assertEquals(RemindRequest.Debt("ref"), RemindRequest.of(event(EventKind.DEBT, MoneyFlow.IN)))
        assertEquals(RemindRequest.Share("ref", "sh", d), RemindRequest.of(event(EventKind.SUBSCRIPTION, MoneyFlow.IN, "sh", d)))
        assertNull(RemindRequest.of(event(EventKind.DEBT, MoneyFlow.OUT)))
        assertNull(RemindRequest.of(event(EventKind.SUBSCRIPTION, MoneyFlow.IN)))
    }

    @Test fun `remind request survives encode and decode`() {
        val reqs = listOf(RemindRequest.Debt("a-b"), RemindRequest.Share("s1", "sh2", d))
        reqs.forEach { assertEquals(it, RemindRequest.decode(it.encode())) }
    }

    @Test fun `bad remind strings are rejected`() {
        listOf(null, "", "debt", "debt:", "share:a:b", "share:a:b:x", "share::b:1", "foo:1", "debt:a:b").forEach {
            assertNull(it, RemindRequest.decode(it))
        }
    }

    @Test fun `action request codes never collide with notification ids`() {
        val ids = listOf(10, 12345, NotifyActions.notificationId("x"), 0x3fffffff + 10)
        ids.forEach { id ->
            val code = NotifyActions.actionRequestCode(id)
            assertNotEquals(id, code)
            assertTrue(code !in ids)
        }
    }

    @Test fun `exact alarm offered only when missing, not asked, notifications on`() {
        assertTrue(ExactAlarmPolicy.shouldOffer(34, false, false, true, true))
        assertFalse(ExactAlarmPolicy.shouldOffer(30, false, false, true, true))
        assertFalse(ExactAlarmPolicy.shouldOffer(34, true, false, true, true))
        assertFalse(ExactAlarmPolicy.shouldOffer(34, false, true, true, true))
        assertFalse(ExactAlarmPolicy.shouldOffer(34, false, false, false, true))
        assertFalse(ExactAlarmPolicy.shouldOffer(34, false, false, true, false))
    }

    @Test fun `summary is cancelled when last child goes away`() {
        val active = listOf(1 to "g", 100 to "g", 101 to "g", 200 to "h")
        assertNull(NotifyGroups.summaryToCancel(active, 100))
        assertEquals(1, NotifyGroups.summaryToCancel(listOf(1 to "g", 100 to "g"), 100))
        assertNull(NotifyGroups.summaryToCancel(listOf(1 to "g", 100 to "g", 101 to "g"), 999))
        assertNull(NotifyGroups.summaryToCancel(listOf(100 to null), 100))
        assertNull(NotifyGroups.summaryToCancel(listOf(100 to "g"), 100))
    }

    @Test fun `receiver parses every target type and rejects broken extras`() {
        val none = Long.MIN_VALUE
        val day = d.toEpochDay()
        assertEquals(PaidTarget.Debt("a", 7), ReminderActionReceiver.parseTarget("debt", "a", none, 7))
        assertEquals(PaidTarget.Charge("a", d, 7), ReminderActionReceiver.parseTarget("charge", "a", day, 7))
        assertEquals(PaidTarget.Share("a", d, 7), ReminderActionReceiver.parseTarget("share", "a", day, 7))
        assertNull(ReminderActionReceiver.parseTarget("charge", "a", none, 7))
        assertNull(ReminderActionReceiver.parseTarget("share", "a", none, 7))
        assertNull(ReminderActionReceiver.parseTarget("debt", "a", none, 0))
        assertNull(ReminderActionReceiver.parseTarget("debt", null, none, 7))
        assertNull(ReminderActionReceiver.parseTarget("debt", "", none, 7))
        assertNull(ReminderActionReceiver.parseTarget("zzz", "a", none, 7))
        assertNull(ReminderActionReceiver.parseTarget(null, "a", none, 7))
    }

    @Test fun `share event has priority over markable charge`() {
        val both = event(EventKind.SUBSCRIPTION, MoneyFlow.IN, "sh", d, markable = true)
        assertEquals(PaidTarget.Share("sh", d, 5000), NotifyActions.paidTarget(both))
        assertNull(NotifyActions.paidTarget(event(EventKind.SUBSCRIPTION, MoneyFlow.IN, "sh", null)))
    }
}
