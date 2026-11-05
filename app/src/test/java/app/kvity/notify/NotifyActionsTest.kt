package app.kvity.notify

import app.kvity.data.CalendarEvent
import app.kvity.data.EventKind
import app.kvity.data.MoneyFlow
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyActionsTest {
    private val d = LocalDate.of(2026, 10, 20)

    private fun event(
        kind: EventKind, flow: MoneyFlow, shareId: String? = null, period: LocalDate? = null,
        markable: Boolean = false, paid: Boolean = false, amount: Long = 5000,
    ) = CalendarEvent(d, kind, flow, "X", null, amount, "RUB", false, "ref", shareId, period, paid, markable)

    @Test fun `debt payment is marked with the planned amount`() {
        assertEquals(PaidTarget.Debt("ref", 5000), NotifyActions.paidTarget(event(EventKind.DEBT, MoneyFlow.IN)))
    }

    @Test fun `own subscription charge is marked as charge`() {
        val e = event(EventKind.SUBSCRIPTION, MoneyFlow.OUT, period = d, markable = true)
        assertEquals(PaidTarget.Charge("ref", d, 5000), NotifyActions.paidTarget(e))
    }

    @Test fun `participant share is marked as share`() {
        val e = event(EventKind.SUBSCRIPTION, MoneyFlow.IN, shareId = "sh", period = d)
        assertEquals(PaidTarget.Share("sh", d, 5000), NotifyActions.paidTarget(e))
    }

    @Test fun `no mark action for paid, non-markable or empty events`() {
        assertNull(NotifyActions.paidTarget(event(EventKind.DEBT, MoneyFlow.IN, paid = true)))
        assertNull(NotifyActions.paidTarget(event(EventKind.DEBT, MoneyFlow.IN, amount = 0)))
        assertNull(NotifyActions.paidTarget(event(EventKind.SUBSCRIPTION, MoneyFlow.OUT, period = d, markable = false)))
    }

    @Test fun `remind only where money is owed to me`() {
        assertTrue(NotifyActions.canRemind(event(EventKind.DEBT, MoneyFlow.IN)))
        assertFalse(NotifyActions.canRemind(event(EventKind.DEBT, MoneyFlow.OUT)))
    }

    @Test fun `routes lead to debt or subscription screen`() {
        assertEquals("debt/ref", NotifyActions.route(event(EventKind.DEBT, MoneyFlow.IN)))
        assertEquals("sub/ref", NotifyActions.route(event(EventKind.SUBSCRIPTION, MoneyFlow.OUT)))
    }

    @Test fun `notification ids are stable, distinct per period and outside summary range`() {
        val a = event(EventKind.SUBSCRIPTION, MoneyFlow.IN, shareId = "sh", period = d)
        val b = event(EventKind.SUBSCRIPTION, MoneyFlow.IN, shareId = "sh", period = d.plusMonths(1))
        assertEquals(NotifyActions.notificationId(a), NotifyActions.notificationId(a.copy()))
        assertNotEquals(NotifyActions.notificationId(a), NotifyActions.notificationId(b))
        assertTrue(NotifyActions.notificationId(a) >= 10)
    }

    @Test fun `next trigger is today before the time and tomorrow after it`() {
        assertEquals(LocalDateTime.parse("2026-10-22T09:00"), ReminderWorker.nextTrigger(LocalDateTime.parse("2026-10-22T05:00"), 9, 0))
        assertEquals(LocalDateTime.parse("2026-10-23T09:00"), ReminderWorker.nextTrigger(LocalDateTime.parse("2026-10-22T09:00"), 9, 0))
    }
}
