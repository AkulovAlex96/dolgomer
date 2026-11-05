package app.kvity.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRecalcTest {

    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private fun plan(count: Int, amount: Long = 1_000) =
        (1..count).map { PlannedPayment(d(it, 10), amount, "p$it") }

    @Test fun `offer when paid more than the schedule required`() {
        // к 15.01 требовалось 1000, внесено 2500
        assertTrue(ScheduleRecalc.shouldOffer(plan(5), listOf(PaymentAmount(2_500)), 5_000, d(1, 15)))
    }

    @Test fun `no offer for a regular payment`() {
        assertFalse(ScheduleRecalc.shouldOffer(plan(5), listOf(PaymentAmount(1_000)), 5_000, d(1, 10)))
    }

    @Test fun `no offer for underpayment`() {
        assertFalse(ScheduleRecalc.shouldOffer(plan(5), listOf(PaymentAmount(300)), 5_000, d(1, 10)))
    }

    @Test fun `no offer when debt is closed`() {
        assertFalse(ScheduleRecalc.shouldOffer(plan(5), listOf(PaymentAmount(5_000)), 5_000, d(1, 10)))
    }

    @Test fun `no offer when nothing is left ahead in the schedule`() {
        assertFalse(ScheduleRecalc.shouldOffer(plan(2), listOf(PaymentAmount(2_500)), 3_000, d(6, 1)))
    }

    @Test fun `reduce payment changes nothing when remaining already equals future payments`() {
        val items = DebtCalculator.allocate(plan(5), listOf(PaymentAmount(2_000))) // оплачены 1 и 2
        assertTrue(ScheduleRecalc.reducePayment(items, remainingMinor = 3_000, from = d(1, 15)).isEmpty())
    }

    @Test fun `reduce payment spreads remainder over open payments`() {
        // 10 платежей по 1000, внесено 3500: остаток 6500 на 7 открытых платежей (p4 частично оплачен)
        val items = DebtCalculator.allocate(plan(10), listOf(PaymentAmount(3_500)))
        val changes = ScheduleRecalc.reducePayment(items, remainingMinor = 6_500, from = d(1, 15))
        val byId = changes.associate { it.id to it.newAmountMinor }
        // доля ceil(6500 / 7) = 929; у p4 уже оплачено 500
        assertEquals(500L + 929L, byId["p4"])
        assertEquals(929L, byId["p5"])
        assertEquals(6_500L - 6 * 929L, byId["p10"])
        assertEquals(7, changes.size)
    }

    @Test fun `reduce payment deletes payments that are no longer needed`() {
        val planned = listOf(PlannedPayment(d(2, 1), 1_000, "a"), PlannedPayment(d(3, 1), 1_000, "b"), PlannedPayment(d(4, 1), 1_000, "c"))
        val items = DebtCalculator.allocate(planned, 0)
        val changes = ScheduleRecalc.reducePayment(items, remainingMinor = 1, from = d(1, 1))
        assertEquals(PlanChange("a", 1), changes[0])
        assertEquals(listOf(PlanChange("b", null), PlanChange("c", null)), changes.drop(1))
    }

    @Test fun `overdue payments keep their remainder`() {
        val items = DebtCalculator.allocate(plan(4), 0)
        // с 25.02 просрочены p1 и p2 (по 1000); остаток 3000: на p3 и p4 остаётся по 500
        val reduced = ScheduleRecalc.reducePayment(items, remainingMinor = 3_000, from = d(2, 25))
        assertEquals(listOf(PlanChange("p3", 500), PlanChange("p4", 500)), reduced)
    }
}
