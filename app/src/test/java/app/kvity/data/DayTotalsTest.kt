package app.kvity.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class DayTotalsTest {
    private fun ev(flow: MoneyFlow, amount: Long, currency: String = "RUB", overdue: Boolean = false, paid: Boolean = false) = CalendarEvent(
        date = LocalDate.parse("2026-10-05"), kind = EventKind.DEBT, flow = flow, title = "t", subtitle = null,
        amountMinor = amount, currency = currency, overdue = overdue, refId = "r", paid = paid,
    )

    @Test fun `overdue events are not mixed into day totals`() {
        val events = listOf(ev(MoneyFlow.IN, 100_00), ev(MoneyFlow.IN, 900_00, overdue = true), ev(MoneyFlow.OUT, 50_00, overdue = true))
        assertEquals(listOf(MonthTotal("RUB", 100_00, 0)), Events.dayTotals(events, "RUB"))
    }

    @Test fun `paid events are not counted and main currency is first`() {
        val events = listOf(ev(MoneyFlow.OUT, 1, "USD"), ev(MoneyFlow.OUT, 5, "RUB"), ev(MoneyFlow.OUT, 7, "RUB", paid = true))
        assertEquals(listOf(MonthTotal("RUB", 0, 5), MonthTotal("USD", 0, 1)), Events.dayTotals(events, "RUB"))
    }

    @Test fun `day with only overdue gives no totals`() {
        assertEquals(emptyList<MonthTotal>(), Events.dayTotals(listOf(ev(MoneyFlow.IN, 1, overdue = true)), "RUB"))
    }

    @Test fun `undated totals are per month and currency`() {
        val oct = YearMonth.of(2026, 10)
        val items = listOf(
            UndatedPayment(oct, MoneyFlow.IN, "RUB", 100), UndatedPayment(oct, MoneyFlow.IN, "RUB", 50),
            UndatedPayment(oct, MoneyFlow.OUT, "USD", 7), UndatedPayment(YearMonth.of(2026, 11), MoneyFlow.IN, "RUB", 999),
        )
        assertEquals(listOf(MonthTotal("RUB", 150, 0), MonthTotal("USD", 0, 7)), Events.undatedTotals(items, oct, "RUB"))
        assertEquals(emptyList<MonthTotal>(), Events.undatedTotals(items, YearMonth.of(2026, 9), "RUB"))
    }
}
