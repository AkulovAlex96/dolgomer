package app.kvity.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionCalcTest {

    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    @Test fun `monthly occurrences inside a range`() {
        val dates = SubscriptionCalc.occurrences(d(9, 10), Periodicity.MONTH, 1, d(10, 1), d(12, 31))
        assertEquals(listOf(d(10, 10), d(11, 10), d(12, 10)), dates)
    }

    @Test fun `occurrences never start before the anchor`() {
        val dates = SubscriptionCalc.occurrences(d(10, 20), Periodicity.MONTH, 1, d(1, 1), d(11, 30))
        assertEquals(listOf(d(10, 20), d(11, 20)), dates)
    }

    @Test fun `yearly subscription`() {
        val dates = SubscriptionCalc.occurrences(d(10, 1, 2025), Periodicity.YEAR, 1, d(1, 1), d(12, 31))
        assertEquals(listOf(d(10, 1)), dates)
    }

    @Test fun `share dues mark paid periods and keep overdue ones`() {
        val dues = SubscriptionCalc.shareDues(
            anchor = d(8, 2), periodicity = Periodicity.MONTH, interval = 1, activeFrom = d(8, 2),
            amountMinor = 2350, currencyCode = "USD",
            paidPeriods = setOf(d(8, 2), d(9, 2)), horizonEnd = d(11, 30),
        )
        assertEquals(listOf(d(8, 2), d(9, 2), d(10, 2), d(11, 2)), dues.map { it.periodDate })
        assertEquals(listOf(true, true, false, false), dues.map { it.paid })
    }

    @Test fun `share is not charged before it became active`() {
        val dues = SubscriptionCalc.shareDues(
            anchor = d(1, 10), periodicity = Periodicity.MONTH, interval = 1, activeFrom = d(10, 1),
            amountMinor = 100, currencyCode = "USD", paidPeriods = emptySet(), horizonEnd = d(11, 30),
        )
        assertEquals(listOf(d(10, 10), d(11, 10)), dues.map { it.periodDate })
    }

    @Test fun `monthly cost conversion`() {
        assertEquals(1000L, SubscriptionCalc.monthlyMinor(1000, Periodicity.MONTH, 1))
        assertEquals(1000L, SubscriptionCalc.monthlyMinor(12000, Periodicity.YEAR, 1))
    }

    // --- паузы и история сумм ---

    private fun dues(
        pauses: List<Pair<LocalDate, LocalDate?>> = emptyList(), history: List<Pair<LocalDate, Long>> = emptyList(),
        paid: Set<LocalDate> = emptySet(), activeFrom: LocalDate = d(1, 5),
    ) = SubscriptionCalc.shareDues(
        d(1, 5), Periodicity.MONTH, 1, activeFrom, 100, "RUB", paid, d(6, 30), emptyMap(), pauses, history,
    )

    @Test fun `pause interval is half-open, the day of resume is charged`() {
        val p = listOf<Pair<LocalDate, LocalDate?>>(d(2, 5) to d(4, 5))
        assertEquals(listOf(d(1, 5), d(4, 5), d(5, 5), d(6, 5)), dues(p).map { it.periodDate })
        assertEquals(true, SubscriptionCalc.isPaused(d(2, 5), p))
        assertEquals(true, SubscriptionCalc.isPaused(d(4, 4), p))
        assertEquals(false, SubscriptionCalc.isPaused(d(4, 5), p))
        assertEquals(false, SubscriptionCalc.isPaused(d(2, 4), p))
    }

    @Test fun `pause and resume on the same day is empty`() {
        val p = listOf<Pair<LocalDate, LocalDate?>>(d(3, 5) to d(3, 5))
        assertEquals(false, SubscriptionCalc.isPaused(d(3, 5), p))
        assertEquals(6, dues(p).size)
    }

    @Test fun `several intervals and an open pause`() {
        val p = listOf<Pair<LocalDate, LocalDate?>>(d(2, 1) to d(3, 1), d(5, 1) to null, d(3, 20) to d(4, 6))
        // янв, март (5-е: пауза 2.01-1.03 уже закончилась), апрель выпадает во вторую паузу, май и далее в открытую
        assertEquals(listOf(d(1, 5), d(3, 5)), dues(p).map { it.periodDate })
    }

    @Test fun `overlapping intervals do not break anything`() {
        val p = listOf<Pair<LocalDate, LocalDate?>>(d(2, 1) to d(4, 1), d(3, 1) to d(3, 10))
        assertEquals(listOf(d(1, 5), d(4, 5), d(5, 5), d(6, 5)), dues(p).map { it.periodDate })
    }

    @Test fun `paid period stays visible even inside a pause`() {
        val p = listOf<Pair<LocalDate, LocalDate?>>(d(2, 1) to d(4, 1))
        val result = dues(p, paid = setOf(d(3, 5)))
        assertEquals(listOf(d(1, 5), d(3, 5), d(4, 5), d(5, 5), d(6, 5)), result.map { it.periodDate })
        assertEquals(true, result.first { it.periodDate == d(3, 5) }.paid)
    }

    @Test fun `amount history picks the amount in force on the period date`() {
        val h = listOf(d(1, 5) to 100L, d(3, 10) to 200L)
        assertEquals(100L, SubscriptionCalc.amountAt(d(3, 5), 200, h))
        assertEquals(200L, SubscriptionCalc.amountAt(d(3, 10), 200, h)) // в день смены действует новая сумма
        assertEquals(200L, SubscriptionCalc.amountAt(d(5, 5), 200, h))
        assertEquals(100L, SubscriptionCalc.amountAt(d(1, 1), 200, h)) // до первой записи - первая сумма
        assertEquals(200L, SubscriptionCalc.amountAt(d(1, 1), 200, emptyList()))
    }

    @Test fun `history order does not matter`() {
        val h = listOf(d(5, 1) to 300L, d(1, 5) to 100L, d(3, 10) to 200L)
        assertEquals(listOf(100L, 100L, 100L, 200L, 300L, 300L), dues(history = h).map { it.amountMinor })
    }

    @Test fun `amounts and pauses combine`() {
        val result = dues(
            pauses = listOf(d(2, 1) to d(3, 1)), history = listOf(d(1, 5) to 100L, d(3, 5) to 250L),
        )
        assertEquals(listOf(d(1, 5), d(3, 5), d(4, 5), d(5, 5), d(6, 5)), result.map { it.periodDate })
        assertEquals(listOf(100L, 250L, 250L, 250L, 250L), result.map { it.amountMinor })
    }
}
