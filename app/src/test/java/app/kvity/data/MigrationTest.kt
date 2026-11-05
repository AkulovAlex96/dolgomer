package app.kvity.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), KvityDatabase::class.java, emptyList(),
        androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory(),
    )

    private val dbName: String
        get() = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-test").absolutePath

    private fun createV7WithDebt() = helper.createDatabase(dbName, 7).apply {
        execSQL("INSERT INTO counterparties (id, name, createdAt, archived) VALUES ('p1', 'Аня', 0, 0)")
        execSQL(
            "INSERT INTO debts (id, counterpartyId, direction, principalMinor, currency, startDate, termType, interval, archived, createdAt, planMaterialized) " +
                "VALUES ('d1', 'p1', 'OWED_TO_ME', 1000, 'RUB', '2026-01-01', 'NONE', 1, 0, 0, 0)",
        )
    }

    @Test fun `migration 7 to 8 removes duplicate marks keeps latest and adds notify columns`() {
        createV7WithDebt().apply {
            execSQL(
                "INSERT INTO subscriptions (id, name, amountMinor, currency, periodicity, interval, anchorDate, payer, state, createdAt) " +
                    "VALUES ('s1', 'Кино', 500, 'RUB', 'MONTH', 1, '2026-01-10', 'ME', 'ACTIVE', 0)",
            )
            execSQL("INSERT INTO subscription_shares (id, subscriptionId, counterpartyId, amountMinor, currency, activeFrom, active) VALUES ('sh1', 's1', 'p1', 250, 'RUB', '2026-01-10', 1)")
            // дубли доли: по паре (sh1, 2026-01-10) три записи, одна с самой поздней paidDate
            execSQL("INSERT INTO share_payments VALUES ('a', 'sh1', '2026-01-10', '2026-01-11', 250)")
            execSQL("INSERT INTO share_payments VALUES ('b', 'sh1', '2026-01-10', '2026-01-15', 260)")
            execSQL("INSERT INTO share_payments VALUES ('c', 'sh1', '2026-01-10', '2026-01-12', 270)")
            execSQL("INSERT INTO share_payments VALUES ('d', 'sh1', '2026-02-10', '2026-02-11', 250)")
            // дубли списаний подписки с одинаковой paidDate: остаётся одна (по id)
            execSQL("INSERT INTO subscription_payments VALUES ('x', 's1', '2026-01-10', '2026-01-10', 500)")
            execSQL("INSERT INTO subscription_payments VALUES ('y', 's1', '2026-01-10', '2026-01-10', 500)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT id, amountMinor FROM share_payments ORDER BY periodDate").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst(); assertEquals("b", c.getString(0)); assertEquals(260L, c.getLong(1))
            c.moveToNext(); assertEquals("d", c.getString(0))
        }
        db.query("SELECT id FROM subscription_payments").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst(); assertEquals("y", c.getString(0))
        }
        db.query("SELECT notifyMode, notifyDays FROM debts").use { c ->
            c.moveToFirst(); assertEquals("DEFAULT", c.getString(0)); assertTrue(c.isNull(1))
        }
        db.query("SELECT notifyMode FROM subscriptions").use { c ->
            c.moveToFirst(); assertEquals("DEFAULT", c.getString(0))
        }
        // уникальность действует
        val failed = try {
            db.execSQL("INSERT INTO share_payments VALUES ('z', 'sh1', '2026-02-10', '2026-02-12', 1)")
            false
        } catch (e: Exception) { true }
        assertTrue(failed)
    }

    @Test fun `migrated database opens through Room and reads new fields`() {
        createV7WithDebt().close()
        helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8).close()
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.databaseBuilder(ctx, KvityDatabase::class.java, "migration-test").addMigrations(MIGRATION_7_8).allowMainThreadQueries().build()
        try {
            val d = runBlocking { room.debts().all() }.single()
            assertEquals(NotifyMode.DEFAULT, d.notifyMode)
            assertEquals(null, d.notifyDays)
            assertEquals(LocalDate.of(2026, 1, 1), d.startDate)
        } finally { room.close() }
    }
}
