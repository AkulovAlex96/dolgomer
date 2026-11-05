package app.kvity.data

import app.kvity.domain.DebtCalculator
import app.kvity.domain.Direction
import app.kvity.domain.Periodicity
import app.kvity.domain.TermType
import app.kvity.widget.WidgetContent
import app.kvity.widget.WidgetModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthSummaryTest {
    private val today = LocalDate.of(2026, 11, 10)

    private fun debt(
        id: String, dir: Direction, currency: String, principal: Long, due: LocalDate,
        payments: List<PaymentEntity> = emptyList(), archived: Boolean = false,
    ) = DebtView(
        debt = DebtEntity(
            id = id, counterpartyId = "c", direction = dir, principalMinor = principal, currency = currency,
            startDate = today.minusDays(40), termType = TermType.ONE_TIME, dueDate = due, archived = archived, createdAt = 0,
        ),
        counterpartyName = "Иван", counterpartyPhone = null, payments = payments,
        summary = DebtCalculator.summarize(principal, DebtCalculator.oneTime(principal, due), payments.map { it.amountMinor }, today),
    )

    private fun pay(id: String, date: LocalDate, amount: Long) = PaymentEntity(id, "d", date, amount, createdAt = 0)

    private fun sub(paidShares: List<PaidRecord> = emptyList(), paidCharges: List<PaidRecord> = emptyList()) = SubscriptionView(
        subscription = SubscriptionEntity(
            "s", "Netflix", null, 1000, "USD", Periodicity.MONTH, 1, LocalDate.of(2026, 1, 25),
            state = SubscriptionState.PAUSED, createdAt = 0,
        ),
        payerName = null, shares = emptyList(), paidShares = paidShares, paidChargeRecords = paidCharges,
    )

    @Test fun `received and paid are taken from payments of this month only`() {
        val debts = listOf(
            debt("a", Direction.OWED_TO_ME, "RUB", 10_000, today.plusDays(20), listOf(pay("1", LocalDate.of(2026, 11, 2), 3_000), pay("2", LocalDate.of(2026, 10, 30), 999))),
            debt("b", Direction.I_OWE, "RUB", 5_000, today.plusDays(20), listOf(pay("3", LocalDate.of(2026, 11, 8), 1_000))),
        )
        val row = MonthSummary.build(debts, emptyList(), today).single()
        assertEquals(3_000, row.receivedMinor)
        assertEquals(1_000, row.paidMinor)
    }

    @Test fun `expected counts unpaid planned payments of the month`() {
        val debts = listOf(
            debt("a", Direction.OWED_TO_ME, "RUB", 10_000, LocalDate.of(2026, 11, 25), listOf(pay("1", LocalDate.of(2026, 11, 2), 4_000))),
            debt("b", Direction.I_OWE, "RUB", 5_000, LocalDate.of(2026, 12, 5)),
        )
        val row = MonthSummary.build(debts, emptyList(), today).single()
        assertEquals(6_000, row.expectedInMinor)
        assertEquals(0, row.expectedOutMinor)
    }

    @Test fun `overdue in this month stays expected`() {
        val row = MonthSummary.build(listOf(debt("a", Direction.I_OWE, "RUB", 700, LocalDate.of(2026, 11, 3))), emptyList(), today).single()
        assertEquals(700, row.expectedOutMinor)
    }

    @Test fun `currencies are not mixed`() {
        val debts = listOf(
            debt("a", Direction.OWED_TO_ME, "USD", 100, today.plusDays(1)),
            debt("b", Direction.OWED_TO_ME, "RUB", 500, today.plusDays(1)),
        )
        val rows = MonthSummary.build(debts, emptyList(), today, mainCurrency = "RUB")
        assertEquals(listOf("RUB", "USD"), rows.map { it.currency })
        assertEquals(listOf(500L, 100L), rows.map { it.expectedInMinor })
    }

    @Test fun `paid shares and charges of subscriptions count by paid date`() {
        val s = sub(
            paidShares = listOf(PaidRecord(LocalDate.of(2026, 11, 5), 300, "USD"), PaidRecord(LocalDate.of(2026, 10, 5), 999, "USD")),
            paidCharges = listOf(PaidRecord(LocalDate.of(2026, 11, 7), 1_000, "USD")),
        )
        val row = MonthSummary.build(emptyList(), listOf(s), today).single()
        assertEquals(300, row.receivedMinor)
        assertEquals(1_000, row.paidMinor)
    }

    @Test fun `archived debts are ignored and empty month gives no rows`() {
        val archived = debt("a", Direction.OWED_TO_ME, "RUB", 100, today.plusDays(1), archived = true)
        assertTrue(MonthSummary.build(listOf(archived), emptyList(), today).isEmpty())
        assertTrue(MonthSummary.build(listOf(debt("b", Direction.OWED_TO_ME, "RUB", 100, LocalDate.of(2027, 1, 5))), emptyList(), today).isEmpty())
    }

    // ---- виджет ----

    private fun ev(date: LocalDate, overdue: Boolean = false, paid: Boolean = false, title: String = "Иван") = CalendarEvent(
        date = date, kind = EventKind.DEBT, flow = MoneyFlow.IN, title = title, subtitle = null,
        amountMinor = 100, currency = "RUB", overdue = overdue, refId = "r", paid = paid,
    )

    @Test fun `widget is closed by pin and carries no data`() {
        assertEquals(WidgetContent.Locked, WidgetModel.build(listOf(ev(today)), today, locked = true, hideDetails = false))
    }

    @Test fun `widget puts overdue first, skips paid and limits rows`() {
        val events = listOf(
            ev(today.plusDays(1)), ev(today.plusDays(2)), ev(today.plusDays(3)), ev(today.plusDays(4)), ev(today.plusDays(5)),
            ev(today.minusDays(3), overdue = true, title = "Долг"), ev(today, paid = true),
        )
        val rows = (WidgetModel.build(events, today, locked = false, hideDetails = false) as WidgetContent.Items).rows
        assertEquals(5, rows.size)
        assertTrue(rows[0].overdue)
        assertEquals("Долг", rows[0].name)
        assertEquals(today.plusDays(4), rows.last().date)
    }

    @Test fun `widget hides names and amounts on request`() {
        val rows = (WidgetModel.build(listOf(ev(today)), today, locked = false, hideDetails = true) as WidgetContent.Items).rows
        assertNull(rows[0].name)
        assertNull(rows[0].amountMinor)
    }

    @Test fun `widget is empty without upcoming events`() {
        assertEquals(WidgetContent.Empty, WidgetModel.build(listOf(ev(today.minusDays(2))), today, locked = false, hideDetails = false))
    }
}
