package app.kvity.data

import app.kvity.domain.Direction
import app.kvity.domain.Periodicity
import app.kvity.domain.TermType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Настройки уведомлений отдельного долга и подписки в [ReminderPlanner]: чистые правила и путь через настоящую базу. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReminderOverrideTest {

    private val today = LocalDate.of(2026, 10, 20)
    private val settings = Settings(notifyDaysBefore = listOf(3, 1), overdueRepeatDays = 3, subscriptionNotifyDays = 1)
    private lateinit var db: KvityDatabase

    @Before fun setUp() { db = TestDb.create() }
    @After fun tearDown() { db.close() }

    private fun event(date: LocalDate, ref: String, kind: EventKind = EventKind.DEBT, flow: MoneyFlow = MoneyFlow.IN) =
        CalendarEvent(date, kind, flow, "X", null, 1000, "RUB", date.isBefore(today), ref)

    private fun plan(overrides: Map<String, NotifyOverride>, vararg e: CalendarEvent) =
        ReminderPlanner.plan(e.toList(), today, settings, overrides).map { it.event.refId to (it.type to it.days) }

    @Test fun `parse days accepts separators and drops garbage`() {
        assertEquals(listOf(5, 3, 1), ReminderPlanner.parseDays("1, 3;5 x 0 -2 3"))
        assertTrue(ReminderPlanner.parseDays(null).isEmpty())
        assertTrue(ReminderPlanner.parseDays("abc").isEmpty())
    }

    @Test fun `off silences every kind of reminder for that item only`() {
        val off = mapOf("a" to NotifyOverride(NotifyMode.OFF, emptyList()))
        val events = arrayOf(
            event(today, "a"), event(today.plusDays(3), "a"), event(today.minusDays(1), "a"),
            event(today.plusDays(3), "b"),
        )
        assertEquals(listOf("b" to (ReminderType.UPCOMING to 3L)), plan(off, *events))
    }

    @Test fun `custom days replace global days for debt and subscription`() {
        val custom = mapOf("a" to NotifyOverride(NotifyMode.CUSTOM, listOf(5, 2)))
        assertEquals(listOf("a" to (ReminderType.UPCOMING to 5L)), plan(custom, event(today.plusDays(5), "a")))
        assertEquals(listOf("a" to (ReminderType.UPCOMING to 2L)), plan(custom, event(today.plusDays(2), "a")))
        // глобальные 3 и 1 больше не действуют
        assertTrue(plan(custom, event(today.plusDays(3), "a"), event(today.plusDays(1), "a")).isEmpty())
        // для списания подписки свои дни тоже заменяют настройку подписок
        val sub = event(today.plusDays(5), "a", EventKind.SUBSCRIPTION, MoneyFlow.OUT)
        assertEquals(1, plan(custom, sub).size)
        // в день срока и просрочка по-прежнему приходят
        assertEquals(1, plan(custom, event(today, "a")).size)
        assertEquals(1, plan(custom, event(today.minusDays(1), "a")).size)
    }

    @Test fun `custom without valid days only keeps today and overdue`() {
        val custom = mapOf("a" to NotifyOverride(NotifyMode.CUSTOM, emptyList()))
        assertTrue(plan(custom, event(today.plusDays(3), "a")).isEmpty())
        assertEquals(1, plan(custom, event(today, "a")).size)
    }

    @Test fun `trial and stale reminders respect off`() {
        val sub = SubscriptionEntity(
            "s", "Кино", null, 100, "RUB", Periodicity.MONTH, 1, today.minusMonths(1), trialEndsOn = today, createdAt = 0,
        )
        assertEquals(1, ReminderPlanner.planTrials(listOf(sub), today, settings).size)
        assertTrue(ReminderPlanner.planTrials(listOf(sub.copy(notifyMode = NotifyMode.OFF)), today, settings).isEmpty())
    }

    @Test fun `overrides built from the database reach the planner`() = runBlocking {
        val debts = DebtRepository(db)
        val subs = SubscriptionRepository(db)
        val quiet = debts.addDebt(NewDebt("Аня", Direction.OWED_TO_ME, 100_000, "RUB", today.minusDays(5), TermType.ONE_TIME, dueDate = today.plusDays(3)))
        val custom = debts.addDebt(NewDebt("Боря", Direction.I_OWE, 100_000, "RUB", today.minusDays(5), TermType.ONE_TIME, dueDate = today.plusDays(5)))
        val normal = debts.addDebt(NewDebt("Вика", Direction.OWED_TO_ME, 100_000, "RUB", today.minusDays(5), TermType.ONE_TIME, dueDate = today.plusDays(3)))
        val sub = subs.add(
            SubscriptionInput("Кино", 500, "RUB", Periodicity.MONTH, anchorDate = today.plusDays(4), payer = SubscriptionPayer.ME),
        )
        debts.setNotify(quiet, NotifyMode.OFF, "7")
        debts.setNotify(custom, NotifyMode.CUSTOM, "5")
        subs.setNotify(sub, NotifyMode.CUSTOM, "4")

        // days сохраняются только для CUSTOM
        assertEquals(null, db.debts().all().first { it.id == quiet }.notifyDays)

        val debtViews = debts.observeDebts({ today }).first()
        val subViews = subs.observeAll { today }.first()
        val overrides = ReminderPlanner.overridesOf(debtViews, subViews)
        assertEquals(setOf(quiet, custom, sub), overrides.keys)

        val items = ReminderPlanner.plan(Events.build(debtViews, subViews, today), today, settings, overrides)
        val byRef = items.groupBy { it.event.refId }
        assertTrue(quiet !in byRef)
        assertEquals(5L, byRef.getValue(custom).single().days)
        assertEquals(3L, byRef.getValue(normal).single().days)
        assertEquals(4L, byRef.getValue(sub).single().days)
    }

    @Test fun `old backups without notify fields still decode`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = """{"id":"d","counterpartyId":"p","direction":"OWED_TO_ME","principalMinor":1,"currency":"RUB","startDate":"2026-01-01","termType":"NONE","createdAt":0}"""
        val d = json.decodeFromString(DebtEntity.serializer(), old)
        assertEquals(NotifyMode.DEFAULT, d.notifyMode)
        assertEquals(null, d.notifyDays)
        val oldSub = """{"id":"s","name":"n","amountMinor":1,"currency":"RUB","periodicity":"MONTH","anchorDate":"2026-01-01","createdAt":0}"""
        assertEquals(NotifyMode.DEFAULT, json.decodeFromString(SubscriptionEntity.serializer(), oldSub).notifyMode)
    }
}
