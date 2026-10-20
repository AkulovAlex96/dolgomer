package app.kvity.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {

    @Test fun `monthly schedule 100k by 10k gives ten payments on the 10th`() {
        val rule = ScheduleRule(Periodicity.MONTH, installmentMinor = 1_000_000, firstDate = LocalDate.of(2026, 11, 10))
        val plan = ScheduleGenerator.generate(rule, principalMinor = 10_000_000)

        assertEquals(10, plan.size)
        assertEquals(LocalDate.of(2026, 11, 10), plan.first().dueDate)
        assertEquals(LocalDate.of(2027, 8, 10), plan.last().dueDate)
        assertEquals(10_000_000L, plan.sumOf { it.amountMinor })
    }

    @Test fun `weekly schedule 50k by 10k gives five payments`() {
        val rule = ScheduleRule(Periodicity.WEEK, installmentMinor = 1_000_000, firstDate = LocalDate.of(2026, 10, 27))
        val plan = ScheduleGenerator.generate(rule, principalMinor = 5_000_000)

        assertEquals(
            listOf("2026-10-27", "2026-11-03", "2026-11-10", "2026-11-17", "2026-11-24"),
            plan.map { it.dueDate.toString() },
        )
    }

    @Test fun `last installment takes the remainder`() {
        val rule = ScheduleRule(Periodicity.MONTH, installmentMinor = 300_000, firstDate = LocalDate.of(2026, 1, 5))
        val plan = ScheduleGenerator.generate(rule, principalMinor = 1_000_000)

        assertEquals(listOf(300_000L, 300_000L, 300_000L, 100_000L), plan.map { it.amountMinor })
    }

    @Test fun `monthly date is clamped to short months`() {
        val rule = ScheduleRule(Periodicity.MONTH, installmentMinor = 100, firstDate = LocalDate.of(2026, 1, 31), count = 3)
        val plan = ScheduleGenerator.generate(rule, principalMinor = 300)

        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31"),
            plan.map { it.dueDate.toString() },
        )
    }

    @Test fun `fixed count puts the remainder into the last payment`() {
        val rule = ScheduleRule(Periodicity.MONTH, installmentMinor = 300, firstDate = LocalDate.of(2026, 1, 1), count = 2)
        val plan = ScheduleGenerator.generate(rule, principalMinor = 1_000)

        assertEquals(listOf(300L, 700L), plan.map { it.amountMinor })
    }

    @Test fun `yearly schedule and every N days`() {
        val yearly = ScheduleRule(Periodicity.YEAR, installmentMinor = 100, firstDate = LocalDate.of(2024, 2, 29), count = 2)
        assertEquals(
            listOf("2024-02-29", "2025-02-28"),
            ScheduleGenerator.generate(yearly, 200).map { it.dueDate.toString() },
        )

        val days = ScheduleRule(Periodicity.DAYS, installmentMinor = 100, firstDate = LocalDate.of(2026, 1, 1), interval = 10, count = 3)
        assertEquals(
            listOf("2026-01-01", "2026-01-11", "2026-01-21"),
            ScheduleGenerator.generate(days, 300).map { it.dueDate.toString() },
        )
    }

    @Test fun `invalid input gives empty plan`() {
        val rule = ScheduleRule(Periodicity.MONTH, installmentMinor = 0, firstDate = LocalDate.of(2026, 1, 1))
        assertTrue(ScheduleGenerator.generate(rule, 1000).isEmpty())
        assertTrue(ScheduleGenerator.generate(rule.copy(installmentMinor = 100), 0).isEmpty())
    }
}
