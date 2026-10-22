package app.kvity.data

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthTotalsTest {
    private fun ev(date: String, flow: MoneyFlow, amount: Long, currency: String = "RUB") = CalendarEvent(
        date = LocalDate.parse(date), kind = EventKind.DEBT, flow = flow, title = "t", subtitle = null,
        amountMinor = amount, currency = currency, overdue = false, refId = "r",
    )

    private val oct = YearMonth.of(2026, 10)

    @Test fun `sums only the requested month by flow`() {
        val events = listOf(
            ev("2026-10-05", MoneyFlow.IN, 1_000_00), ev("2026-10-28", MoneyFlow.IN, 500_00),
            ev("2026-10-10", MoneyFlow.OUT, 200_00), ev("2026-11-01", MoneyFlow.IN, 9_999_00), ev("2026-09-30", MoneyFlow.OUT, 9_999_00),
        )
        assertEquals(listOf(MonthTotal("RUB", 1_500_00, 200_00)), Events.monthTotals(events, oct, "RUB"))
    }

    @Test fun `main currency comes first, others alphabetically`() {
        val events = listOf(ev("2026-10-01", MoneyFlow.IN, 1, "USD"), ev("2026-10-01", MoneyFlow.IN, 1, "KZT"), ev("2026-10-01", MoneyFlow.OUT, 1, "RUB"))
        assertEquals(listOf("RUB", "KZT", "USD"), Events.monthTotals(events, oct, "RUB").map { it.currency })
    }

    @Test fun `empty month gives no totals`() {
        assertEquals(emptyList<MonthTotal>(), Events.monthTotals(listOf(ev("2026-11-01", MoneyFlow.IN, 1)), oct, "RUB"))
    }
}
