package app.kvity.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {

    private val today = LocalDate.of(2026, 10, 20)
    private val settings = Settings(notifyDaysBefore = listOf(3, 1), overdueRepeatDays = 3, subscriptionNotifyDays = 1)

    private fun event(
        date: LocalDate,
        kind: EventKind = EventKind.DEBT,
        flow: MoneyFlow = MoneyFlow.IN,
    ) = CalendarEvent(date, kind, flow, "X", null, 1000, "RUB", date.isBefore(today), "id")

    private fun types(vararg e: CalendarEvent) = ReminderPlanner.plan(e.toList(), today, settings).map { it.type to it.days }

    @Test fun `due today and upcoming on configured days`() {
        assertEquals(listOf(ReminderType.TODAY to 0L), types(event(today)))
        assertEquals(listOf(ReminderType.UPCOMING to 3L), types(event(today.plusDays(3))))
        assertEquals(listOf(ReminderType.UPCOMING to 1L), types(event(today.plusDays(1))))
        assertTrue(types(event(today.plusDays(2))).isEmpty())
        assertTrue(types(event(today.plusDays(10))).isEmpty())
    }

    @Test fun `overdue is reported on first day and then every N days`() {
        assertEquals(listOf(ReminderType.OVERDUE to 1L), types(event(today.minusDays(1))))
        assertTrue(types(event(today.minusDays(2))).isEmpty())
        assertEquals(listOf(ReminderType.OVERDUE to 3L), types(event(today.minusDays(3))))
        assertEquals(listOf(ReminderType.OVERDUE to 6L), types(event(today.minusDays(6))))
    }

    @Test fun `overdue repeat can be switched off`() {
        val s = settings.copy(overdueRepeatDays = 0)
        assertTrue(ReminderPlanner.plan(listOf(event(today.minusDays(1))), today, s).isEmpty())
    }

    @Test fun `own subscription charge uses subscription lead time`() {
        val charge = event(today.plusDays(1), EventKind.SUBSCRIPTION, MoneyFlow.OUT)
        assertEquals(listOf(ReminderType.UPCOMING to 1L), types(charge))
        assertTrue(types(event(today.plusDays(3), EventKind.SUBSCRIPTION, MoneyFlow.OUT)).isEmpty())
    }

    @Test fun `nothing when notifications are disabled`() {
        val s = settings.copy(notificationsEnabled = false)
        assertTrue(ReminderPlanner.plan(listOf(event(today)), today, s).isEmpty())
    }
}
