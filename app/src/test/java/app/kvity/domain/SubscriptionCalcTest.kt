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
}
