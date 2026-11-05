package app.kvity.data

import app.kvity.domain.DebtStatus
import app.kvity.domain.DebtSummary
import app.kvity.domain.Direction
import app.kvity.domain.Periodicity
import app.kvity.domain.TermType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialAndStaleReminderTest {

    private val today = LocalDate.of(2026, 10, 20)
    private val settings = Settings(subscriptionNotifyDays = 2, staleDebtDays = 30)

    private fun sub(id: String = "s", trialEnds: LocalDate?, state: SubscriptionState = SubscriptionState.ACTIVE) = SubscriptionEntity(
        id = id, name = "Music", amountMinor = 1000, currency = "RUB", periodicity = Periodicity.MONTH,
        anchorDate = today, state = state, trialEndsOn = trialEnds, createdAt = 0,
    )

    private fun trials(vararg s: SubscriptionEntity, last: Map<String, LocalDate> = emptyMap(), st: Settings = settings) =
        ReminderPlanner.planTrials(s.toList(), today, st, last).map { it.subscriptionId to it.days }

    @Test fun `trial reminder N days before and on the last day`() {
        assertEquals(listOf("s" to 2L), trials(sub(trialEnds = today.plusDays(2))))
        assertEquals(listOf("s" to 0L), trials(sub(trialEnds = today)))
        assertTrue(trials(sub(trialEnds = today.plusDays(1))).isEmpty())
        assertTrue(trials(sub(trialEnds = today.plusDays(3))).isEmpty())
    }

    @Test fun `trial that already ended or absent is ignored`() {
        assertTrue(trials(sub(trialEnds = today.minusDays(1))).isEmpty())
        assertTrue(trials(sub(trialEnds = null)).isEmpty())
    }

    @Test fun `trial reminder is not repeated on the same day`() {
        assertTrue(trials(sub(trialEnds = today), last = mapOf("s" to today)).isEmpty())
        assertEquals(1, trials(sub(trialEnds = today), last = mapOf("s" to today.minusDays(2))).size)
    }

    @Test fun `zero lead time means only the last day`() {
        val s = settings.copy(subscriptionNotifyDays = 0)
        assertTrue(trials(sub(trialEnds = today.plusDays(1)), st = s).isEmpty())
        assertEquals(1, trials(sub(trialEnds = today), st = s).size)
    }

    @Test fun `paused subscription and disabled notifications give no trial reminder`() {
        assertTrue(trials(sub(trialEnds = today, state = SubscriptionState.PAUSED)).isEmpty())
        assertTrue(trials(sub(trialEnds = today), st = settings.copy(notificationsEnabled = false)).isEmpty())
    }

    private fun debt(
        termType: TermType = TermType.NONE, start: LocalDate = today.minusDays(60), payments: List<LocalDate> = emptyList(),
        status: DebtStatus = DebtStatus.ACTIVE, archived: Boolean = false,
    ) = DebtView(
        debt = DebtEntity(
            id = "d", counterpartyId = "c", direction = Direction.OWED_TO_ME, principalMinor = 5000, currency = "RUB",
            startDate = start, termType = termType, archived = archived, createdAt = 0,
        ),
        counterpartyName = "Иван", counterpartyPhone = null,
        payments = payments.map { PaymentEntity("p$it", "d", it, 100, null, 0) },
        summary = DebtSummary(5000, 0, 5000, status, 0, 0, emptyList(), null),
    )

    private fun stale(d: DebtView, st: Settings = settings) = ReminderPlanner.planStale(listOf(d), today, st).map { it.days }

    @Test fun `stale debt counts from issue date when there are no payments`() {
        assertEquals(listOf(60L), stale(debt(start = today.minusDays(60))))
        assertTrue(stale(debt(start = today.minusDays(45))).isEmpty())
    }

    @Test fun `stale debt counts from the last payment and repeats every N days`() {
        assertEquals(listOf(30L), stale(debt(payments = listOf(today.minusDays(100), today.minusDays(30)))))
        assertTrue(stale(debt(payments = listOf(today.minusDays(29)))).isEmpty())
    }

    @Test fun `stale reminder is off by default and only for debts without term`() {
        assertTrue(stale(debt(), settings.copy(staleDebtDays = 0)).isEmpty())
        assertTrue(stale(debt(termType = TermType.ONE_TIME)).isEmpty())
        assertTrue(stale(debt(status = DebtStatus.CLOSED)).isEmpty())
        assertTrue(stale(debt(archived = true)).isEmpty())
        assertTrue(stale(debt(), settings.copy(notificationsEnabled = false)).isEmpty())
    }
}
