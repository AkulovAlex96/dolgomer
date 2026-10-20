package app.kvity.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ReminderType { TODAY, UPCOMING, OVERDUE }

/** Что именно нужно напомнить сегодня. */
data class ReminderItem(val event: CalendarEvent, val type: ReminderType, val days: Long)

/** Правила уведомлений. Чистая функция: решает, о каких событиях сообщить [today]. */
object ReminderPlanner {

    fun plan(events: List<CalendarEvent>, today: LocalDate, settings: Settings): List<ReminderItem> {
        if (!settings.notificationsEnabled) return emptyList()
        val result = mutableListOf<ReminderItem>()
        for (e in events) {
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
}
