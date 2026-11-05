package app.kvity.data

import app.kvity.domain.Direction
import app.kvity.domain.Periodicity
import app.kvity.exchange.ImportBatch
import app.kvity.exchange.ImportPayment
import app.kvity.exchange.ImportPerson
import app.kvity.exchange.ImportRow
import app.kvity.exchange.ImportShare
import app.kvity.exchange.ImportSubState
import app.kvity.exchange.ImportSubscription
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ImportApplierTest {

    private lateinit var db: KvityDatabase
    private lateinit var applier: ImportApplier

    @Before fun setUp() {
        db = TestDb.create()
        applier = ImportApplier(db, DebtRepository(db), SubscriptionRepository(db))
    }
    @After fun tearDown() { db.close() }

    private val start = LocalDate.of(2026, 1, 10)

    private val batch = ImportBatch(
        people = listOf(ImportPerson(2, "Аня", "+7", null)),
        debts = listOf(
            ImportRow(2, "Аня", Direction.OWED_TO_ME, "RUB", 100_000, 0, LocalDate.of(2026, 1, 1), null, null),
        ),
        payments = listOf(ImportPayment(2, "Аня", Direction.OWED_TO_ME, "RUB", LocalDate.of(2026, 1, 5), 10_000, null)),
        subscriptions = listOf(
            ImportSubscription(2, "Кино", null, 50_000, "RUB", Periodicity.MONTH, 1, start, ImportSubState.ACTIVE, null, null),
        ),
        shares = listOf(
            ImportShare(2, "Кино", "Боря", start, 25_000, "RUB", paid = true),
            ImportShare(3, "Кино", "Боря", start.plusMonths(1), 25_000, "RUB", paid = true),
            ImportShare(4, "Кино", "Боря", start.plusMonths(2), 25_000, "RUB", paid = false),
        ),
    )

    private suspend fun counts() = listOf(
        db.counterparties().all().size, db.debts().all().size, db.payments().all().size, db.subscriptions().all().size,
        db.shares().all().size, db.sharePayments().all().size,
    )

    @Test fun `second import of the same file adds nothing and creates no duplicate marks`() = runBlocking {
        val first = applier.apply(batch)
        assertEquals(1, first.debts)
        assertEquals(1, first.payments)
        assertEquals(1, first.subscriptions)
        assertEquals(1, first.shares)
        val after1 = counts()
        assertEquals(2, db.sharePayments().all().size)

        val second = applier.apply(batch)
        assertEquals(0, second.added)
        assertTrue(second.skippedDuplicates > 0)
        assertEquals(after1, counts())
        assertEquals(second.skippedDuplicates, applier.countDuplicates(batch))
    }

    @Test fun `import marks existing share period only once even when file lists it twice`() = runBlocking {
        val twice = batch.copy(shares = batch.shares + batch.shares.first().copy(line = 9))
        applier.apply(twice)
        val marks = db.sharePayments().all()
        assertEquals(marks.size, marks.map { it.shareId to it.periodDate }.toSet().size)
        assertEquals(2, marks.size)
    }
}
