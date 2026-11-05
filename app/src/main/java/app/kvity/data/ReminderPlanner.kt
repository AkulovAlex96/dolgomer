package app.kvity.data

import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.domain.TermType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ReminderType { TODAY, UPCOMING, OVERDUE }

/** Что именно нужно напомнить сегодня. */
data class ReminderItem(val event: CalendarEvent, val type: ReminderType, val days: Long)

/** Пробный период подписки заканчивается через [days] дней (0 — сегодня). */
data class TrialReminder(val subscriptionId: String, val name: String, val endsOn: LocalDate, val days: Long)

/** Долг без срока, по которому давно не было платежей: [days] дней с последнего платежа (или с выдачи). */
data class StaleDebtReminder(
    val debtId: String, val name: String, val owedToMe: Boolean,
    val remainingMinor: Long, val currency: String, val days: Long,
)

/** Правила уведомлений. Чистая функция: решает, о каких событиях сообщить [today]. */
object ReminderPlanner {

    fun plan(events: List<CalendarEvent>, today: LocalDate, settings: Settings): List<ReminderItem> {
        if (!settings.notificationsEnabled) return emptyList()
        val result = mutableListOf<ReminderItem>()
        for (e in events) {
            if (e.paid) continue
            val days = ChronoUnit.DAYS.between(today, e.date)
            when {
                days == 0L -> result += ReminderItem(e, ReminderType.TODAY, 0)
                days > 0 -> {
                    val before = if (e.kind == EventKind.SUBSCRIPTION && e.flow == MoneyFlow.OUT) {
                        listOf(settings.subscriptionNotifyDays).filter { it > 0 }
                    } else {
                        settings.notifyDaysBefore
                    }
                    if (days.toInt() in before) result += ReminderItem(e, ReminderType.UPCOMING, days)
                }
                // прошедшие неотмеченные списания подписок — просрочка (так решено сознательно); но то, что было
                // до заведения подписки в приложении, Events не помечает просроченным, о таком не напоминаем
                !e.overdue -> Unit
                else -> {
                    val overdue = -days
                    val every = settings.overdueRepeatDays
                    if (every > 0 && (overdue == 1L || overdue % every == 0L)) {
                        result += ReminderItem(e, ReminderType.OVERDUE, overdue)
                    }
                }
            }
        }
        return result
    }

    /**
     * Окончание пробного периода: за [Settings.subscriptionNotifyDays] дней и в день окончания.
     * [lastNotified] — когда о подписке уже напоминали; в один и тот же день повторно не напоминаем.
     */
    fun planTrials(
        subs: List<SubscriptionEntity>, today: LocalDate, settings: Settings, lastNotified: Map<String, LocalDate> = emptyMap(),
    ): List<TrialReminder> {
        if (!settings.notificationsEnabled) return emptyList()
        return subs.mapNotNull { s ->
            val ends = s.trialEndsOn ?: return@mapNotNull null
            if (s.state != SubscriptionState.ACTIVE || lastNotified[s.id] == today) return@mapNotNull null
            val days = ChronoUnit.DAYS.between(today, ends)
            val lead = settings.subscriptionNotifyDays
            if (days == 0L || (lead > 0 && days == lead.toLong())) TrialReminder(s.id, s.name, ends, days) else null
        }
    }

    /**
     * Периодическое «давно не было платежа» для долгов без срока (п. 3.15 ТЗ): через [Settings.staleDebtDays] дней
     * после последнего платежа (если платежей не было, после выдачи) и затем каждые столько же дней. 0 — выключено.
     */
    fun planStale(debts: List<DebtView>, today: LocalDate, settings: Settings): List<StaleDebtReminder> {
        val every = settings.staleDebtDays
        if (!settings.notificationsEnabled || every <= 0) return emptyList()
        return debts.mapNotNull { v ->
            val d = v.debt
            if (d.termType != TermType.NONE || d.archived || v.summary.status == DebtStatus.CLOSED) return@mapNotNull null
            val last = maxOf(d.startDate, v.payments.maxOfOrNull { it.date } ?: d.startDate)
            val days = ChronoUnit.DAYS.between(last, today)
            if (days > 0 && days % every == 0L) {
                StaleDebtReminder(d.id, v.counterpartyName, d.direction == Direction.OWED_TO_ME, v.summary.remainingMinor, d.currency, days)
            } else null
        }
    }
}
