package app.kvity.data

import app.kvity.domain.Periodicity
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SubscriptionRepositoryTest {

    private lateinit var db: KvityDatabase
    private lateinit var repo: SubscriptionRepository
    private val today = LocalDate.now()

    @Before fun setUp() { db = TestDb.create(); repo = SubscriptionRepository(db) }
    @After fun tearDown() { db.close() }

    private fun input(anchor: LocalDate, amount: Long = 1000) = SubscriptionInput(
        name = "Кино", amountMinor = amount, currency = "RUB", periodicity = Periodicity.MONTH,
        anchorDate = anchor, payer = SubscriptionPayer.ME,
    )

    private fun view(id: String) = runBlocking { repo.observe(id) { today }.first()!! }

    @Test fun `pause stops future dues and resume brings them back`() = runBlocking {
        val anchor = today.minusMonths(3)
        val id = repo.add(input(anchor))
        repo.addShare(id, NewShare("Аня", 500, "RUB", anchor))
        assertTrue(view(id).shares.single().dues.any { it.periodDate.isAfter(today) })

        repo.setState(id, SubscriptionState.PAUSED)
        val paused = view(id)
        assertEquals(listOf(today to null), paused.pauses)
        assertTrue(paused.shares.single().dues.none { !it.periodDate.isBefore(today) })
        // прошлые периоды остаются долгом
        assertTrue(paused.shares.single().dues.any { it.periodDate.isBefore(today) })

        // пауза, начатая и закрытая в тот же день, не оставляет записи
        repo.setState(id, SubscriptionState.ACTIVE)
        val resumed = view(id)
        assertTrue(resumed.pauses.isEmpty())
        assertTrue(resumed.shares.single().dues.any { it.periodDate.isAfter(today) })
    }

    @Test fun `resume closes older pause with end date`() = runBlocking {
        val id = repo.add(input(today.minusMonths(2)))
        db.pauses().insert(SubscriptionPauseEntity("p", id, today.minusDays(10), null))
        val sub = db.subscriptions().all().single()
        db.subscriptions().update(sub.copy(state = SubscriptionState.PAUSED))
        repo.setState(id, SubscriptionState.ACTIVE)
        assertEquals(listOf(today.minusDays(10) to today), view(id).pauses)
    }

    @Test fun `changing anchor date moves paid marks to the same period number`() = runBlocking {
        val id = repo.add(input(LocalDate.of(2026, 1, 10)))
        val share = repo.addShare(id, NewShare("Аня", 500, "RUB", LocalDate.of(2026, 1, 10)))
        repo.markPaid(share, LocalDate.of(2026, 2, 10), 500, LocalDate.of(2026, 2, 11))
        repo.markChargePaid(id, LocalDate.of(2026, 3, 10), 1000, LocalDate.of(2026, 3, 10))

        repo.update(id, input(LocalDate.of(2026, 1, 20)))

        assertEquals(listOf(LocalDate.of(2026, 2, 20)), db.sharePayments().all().map { it.periodDate })
        assertEquals(listOf(LocalDate.of(2026, 3, 20)), db.chargePayments().all().map { it.periodDate })
        // paidDate и сумма сохраняются
        assertEquals(LocalDate.of(2026, 2, 11), db.sharePayments().all().single().paidDate)
    }

    @Test fun `moving marks onto an occupied period does not violate uniqueness`() = runBlocking {
        val id = repo.add(input(LocalDate.of(2026, 1, 10)))
        // отметки периодов №1 и №2: после переноса якоря на +1 месяц период №1 попадает на место старого №2
        repo.markChargePaid(id, LocalDate.of(2026, 1, 10), 1000, LocalDate.of(2026, 1, 10))
        repo.markChargePaid(id, LocalDate.of(2026, 2, 10), 1000, LocalDate.of(2026, 2, 10))
        repo.update(id, input(LocalDate.of(2026, 2, 10)))
        val dates = db.chargePayments().all().map { it.periodDate }.sorted()
        assertEquals(listOf(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 10)), dates)
    }

    @Test fun `update keeps notify settings and state`() = runBlocking {
        val id = repo.add(input(LocalDate.of(2026, 1, 10)))
        repo.setNotify(id, NotifyMode.CUSTOM, "5,2")
        repo.update(id, input(LocalDate.of(2026, 1, 15), amount = 2000))
        val s = db.subscriptions().all().single()
        assertEquals(NotifyMode.CUSTOM, s.notifyMode)
        assertEquals("5,2", s.notifyDays)
        assertEquals(2000L, s.amountMinor)
    }

    @Test fun `repeated mark paid keeps a single mark with latest data`() = runBlocking {
        val id = repo.add(input(LocalDate.of(2026, 1, 10)))
        val share = repo.addShare(id, NewShare("Аня", 500, "RUB", LocalDate.of(2026, 1, 10)))
        val period = LocalDate.of(2026, 1, 10)
        repo.markPaid(share, period, 500, LocalDate.of(2026, 1, 11))
        repo.markPaid(share, period, 600, LocalDate.of(2026, 1, 12))
        repo.markChargePaid(id, period, 1000)
        repo.markChargePaid(id, period, 1000)
        val marks = db.sharePayments().all()
        assertEquals(1, marks.size)
        assertEquals(600L, marks.single().amountMinor)
        assertEquals(1, db.chargePayments().all().size)
        repo.markUnpaid(share, period)
        assertTrue(db.sharePayments().all().isEmpty())
    }

    @Test fun `plain insert of a duplicate mark replaces instead of failing`() = runBlocking {
        val id = repo.add(input(LocalDate.of(2026, 1, 10)))
        val share = repo.addShare(id, NewShare("Аня", 500, "RUB", LocalDate.of(2026, 1, 10)))
        val p = LocalDate.of(2026, 1, 10)
        db.sharePayments().insert(SharePaymentEntity("a", share, p, p, 1))
        db.sharePayments().insertAll(listOf(SharePaymentEntity("b", share, p, p, 2)))
        assertEquals(listOf("b"), db.sharePayments().all().map { it.id })
    }

    @Test fun `share amount change keeps history for past periods`() = runBlocking {
        val anchor = today.minusMonths(4)
        val id = repo.add(input(anchor))
        val shareId = repo.addShare(id, NewShare("Аня", 500, "RUB", anchor))
        val share = db.shares().all().single { it.id == shareId }
        repo.updateShare(share, 800, "RUB", share.activeFrom)

        val dues = view(id).shares.single().dues
        assertTrue(dues.filter { it.periodDate.isBefore(today) }.all { it.amountMinor == 500L })
        assertTrue(dues.filter { it.periodDate.isAfter(today) }.all { it.amountMinor == 800L })
        assertEquals(2, db.shareAmounts().all().size) // исходная сумма и новая

        // повторное изменение в тот же день заменяет запись дня, а не дублирует
        repo.updateShare(db.shares().all().single(), 900, "RUB", share.activeFrom)
        val history = db.shareAmounts().all()
        assertEquals(2, history.size)
        assertEquals(900L, history.maxByOrNull { it.fromDate }!!.amountMinor)
        assertFalse(view(id).shares.single().dues.any { it.periodDate.isBefore(today) && it.amountMinor != 500L })
    }

    @Test fun `paid period keeps paid amount after share amount change`() = runBlocking {
        val anchor = today.minusMonths(4)
        val id = repo.add(input(anchor))
        val shareId = repo.addShare(id, NewShare("Аня", 500, "RUB", anchor))
        repo.markPaid(shareId, anchor, 500, anchor)
        repo.updateShare(db.shares().all().single(), 800, "RUB", anchor)
        val first = view(id).shares.single().dues.first { it.periodDate == anchor }
        assertTrue(first.paid)
        assertEquals(500L, first.amountMinor)
    }
}
