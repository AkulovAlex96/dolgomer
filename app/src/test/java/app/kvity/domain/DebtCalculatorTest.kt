package app.kvity.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebtCalculatorTest {

    private val today = LocalDate.of(2026, 10, 20)

    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test fun `payments close the oldest installments first`() {
        val planned = listOf(
            PlannedPayment(d(10, 10), 1_000),
            PlannedPayment(d(11, 10), 1_000),
            PlannedPayment(d(12, 10), 1_000),
        )
        val plan = DebtCalculator.allocate(planned, totalPaidMinor = 1_500)

        assertEquals(listOf(InstallmentState.PAID, InstallmentState.PARTIAL, InstallmentState.PENDING), plan.map { it.state })
        assertEquals(500L, plan[1].remainingMinor)
    }

    @Test fun `overpayment rolls over to next installments`() {
        val planned = listOf(PlannedPayment(d(11, 1), 1_000), PlannedPayment(d(12, 1), 1_000))
        val plan = DebtCalculator.allocate(planned, 2_000)
        assertEquals(listOf(InstallmentState.PAID, InstallmentState.PAID), plan.map { it.state })
    }

    @Test fun `closed when remaining is zero`() {
        val s = DebtCalculator.summarize(1_000, DebtCalculator.oneTime(1_000, d(10, 1)), listOf(400, 600), today)
        assertEquals(DebtStatus.CLOSED, s.status)
        assertEquals(0L, s.remainingMinor)
        assertEquals(0L, s.overdueMinor)
        assertNull(s.nextDue)
    }

    @Test fun `one time debt past due is overdue with day counter`() {
        // Дима, 40к ₸ до 01.10: на 20.10 просрочка 19 дней
        val s = DebtCalculator.summarize(4_000_000, DebtCalculator.oneTime(4_000_000, d(10, 1)), emptyList(), today)
        assertEquals(DebtStatus.OVERDUE, s.status)
        assertEquals(19L, s.overdueDays)
        assertEquals(4_000_000L, s.overdueMinor)
    }

    @Test fun `partial payment reduces overdue amount`() {
        val s = DebtCalculator.summarize(4_000_000, DebtCalculator.oneTime(4_000_000, d(10, 1)), listOf(1_000_000), today)
        assertEquals(DebtStatus.OVERDUE, s.status)
        assertEquals(3_000_000L, s.overdueMinor)
        assertEquals(3_000_000L, s.remainingMinor)
    }

    @Test fun `due soon within three days`() {
        val planned = listOf(PlannedPayment(d(10, 22), 1_000))
        val s = DebtCalculator.summarize(1_000, planned, emptyList(), today)
        assertEquals(DebtStatus.DUE_SOON, s.status)
        assertEquals(d(10, 22), s.nextDue?.dueDate)
    }

    @Test fun `active when next payment is far`() {
        val planned = listOf(PlannedPayment(d(11, 20), 1_000))
        val s = DebtCalculator.summarize(1_000, planned, emptyList(), today)
        assertEquals(DebtStatus.ACTIVE, s.status)
    }

    @Test fun `debt without schedule is active and never overdue`() {
        val s = DebtCalculator.summarize(1_000, emptyList(), listOf(100), today)
        assertEquals(DebtStatus.ACTIVE, s.status)
        assertEquals(900L, s.remainingMinor)
    }

    @Test fun `schedule scenario from notes - 100k with 4 payments of 10k`() {
        val rule = ScheduleRule(Periodicity.MONTH, installmentMinor = 1_000_000, firstDate = d(8, 14), count = 10)
        val planned = ScheduleGenerator.generate(rule, 10_000_000)
        val s = DebtCalculator.summarize(10_000_000, planned, List(4) { 1_000_000L }, today)

        assertEquals(6_000_000L, s.remainingMinor)
        // 4 платежа по 10к закрыли 14.08, 14.09, 14.10 и 14.11 (оплата вперёд), просрочек нет
        assertEquals(DebtStatus.ACTIVE, s.status)
        assertEquals(LocalDate.of(2026, 12, 14), s.nextDue?.dueDate)
    }
}
