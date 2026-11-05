package app.kvity.domain

import app.kvity.exchange.Cell
import app.kvity.exchange.Csv
import app.kvity.exchange.Table
import app.kvity.notify.ReminderWorker
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixesTest {

    private fun d(m: Int, day: Int) = LocalDate.of(2026, m, day)

    @Test fun `paid share period keeps the amount it was paid with`() {
        val dues = SubscriptionCalc.shareDues(
            anchor = d(1, 10), periodicity = Periodicity.MONTH, interval = 1, activeFrom = d(1, 10),
            amountMinor = 1500, currencyCode = "USD", paidPeriods = setOf(d(1, 10)), horizonEnd = d(2, 28),
            paidAmounts = mapOf(d(1, 10) to 1000L),
        )
        assertEquals(listOf(1000L, 1500L), dues.map { it.amountMinor })
    }

    @Test fun `ambiguous thousands comma is rejected`() {
        assertNull(AmountParser.parseMinor("1,000"))
        assertEquals(150L, AmountParser.parseMinor("1,5"))
    }

    @Test fun `compact rounds up to millions`() {
        assertEquals("1м", AmountFormatter.compact(99_995_000))
    }

    @Test fun `csv escapes text formulas but keeps negative numbers`() {
        val t = Table("T", listOf("a", "b"), listOf(listOf(Cell.Text("+7 999"), Cell.Num(BigDecimal("-5")))))
        val out = Csv.write(t)
        assertTrue(out.contains("'+7 999"))
        assertTrue(out.contains(";-5"))
    }

    @Test fun `next reminder is tomorrow when run exactly at the set time`() {
        val now = LocalDateTime.of(2026, 11, 4, 9, 0, 30)
        assertEquals(24 * 3600 - 30L, ReminderWorker.delayUntilNext(now, 9, 0).seconds)
    }

    @Test fun `periods inside a pause are skipped and resumed ones count again`() {
        val dues = SubscriptionCalc.shareDues(
            anchor = d(1, 10), periodicity = Periodicity.MONTH, interval = 1, activeFrom = d(1, 10),
            amountMinor = 1000, currencyCode = "USD", paidPeriods = emptySet(), horizonEnd = d(6, 30),
            pauses = listOf(d(3, 1) to d(6, 1)),
        )
        assertEquals(listOf(d(1, 10), d(2, 10), d(6, 10)), dues.map { it.periodDate })
    }

    @Test fun `open pause stops all later periods`() {
        val dues = SubscriptionCalc.shareDues(
            anchor = d(1, 10), periodicity = Periodicity.MONTH, interval = 1, activeFrom = d(1, 10),
            amountMinor = 1000, currencyCode = "USD", paidPeriods = emptySet(), horizonEnd = d(6, 30),
            pauses = listOf(d(3, 1) to null),
        )
        assertEquals(listOf(d(1, 10), d(2, 10)), dues.map { it.periodDate })
    }

    @Test fun `amount change applies only from its date`() {
        val dues = SubscriptionCalc.shareDues(
            anchor = d(1, 10), periodicity = Periodicity.MONTH, interval = 1, activeFrom = d(1, 10),
            amountMinor = 1500, currencyCode = "USD", paidPeriods = emptySet(), horizonEnd = d(4, 30),
            amountHistory = listOf(d(1, 10) to 1000L, d(3, 1) to 1500L),
        )
        assertEquals(listOf(1000L, 1000L, 1500L, 1500L), dues.map { it.amountMinor })
    }

    @Test fun `csv import strips the formula guard apostrophe`() {
        assertEquals(listOf(listOf("+7 999", "x", "'plain")), Csv.parse("'+7 999;x;'plain"))
    }
}
