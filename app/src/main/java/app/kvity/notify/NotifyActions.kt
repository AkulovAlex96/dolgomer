package app.kvity.notify

import android.content.Context
import app.kvity.data.CalendarEvent
import app.kvity.data.EventKind
import app.kvity.data.MoneyFlow
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow

/** Что именно сделает действие «Отметить оплаченным» из уведомления. */
sealed class PaidTarget {
    /** Платёж по долгу плановой суммой на сегодня. */
    data class Debt(val debtId: String, val amountMinor: Long) : PaidTarget()
    /** Моё списание подписки за период. */
    data class Charge(val subscriptionId: String, val period: LocalDate, val amountMinor: Long) : PaidTarget()
    /** Доля участника за период. */
    data class Share(val shareId: String, val period: LocalDate, val amountMinor: Long) : PaidTarget()
}

/** Чистая логика уведомлений: какие действия положены событию и куда ведёт «Открыть». */
object NotifyActions {

    fun paidTarget(e: CalendarEvent): PaidTarget? {
        val period = e.periodDate
        val shareId = e.shareId
        return when {
            e.paid || e.amountMinor <= 0 -> null
            e.kind == EventKind.DEBT -> PaidTarget.Debt(e.refId, e.amountMinor)
            shareId != null && period != null -> PaidTarget.Share(shareId, period, e.amountMinor)
            e.markable && period != null -> PaidTarget.Charge(e.refId, period, e.amountMinor)
            else -> null
        }
    }

    /** «Напомнить» есть там, где деньги должны мне: долг «мне должны» и доли участников подписок. */
    fun canRemind(e: CalendarEvent): Boolean = e.flow == MoneyFlow.IN

    /** Экран приложения, который открывает уведомление (маршруты как в [app.kvity.ui.Routes]). */
    fun route(e: CalendarEvent): String = if (e.kind == EventKind.DEBT) "debt/${e.refId}" else "sub/${e.refId}"

    /** Стабильный идентификатор уведомления о событии: повтор проверки заменяет, а не дублирует. */
    fun notificationId(e: CalendarEvent): Int =
        notificationId("${e.kind}|${e.refId}|${e.shareId}|${e.periodDate ?: e.date}")

    /** Идентификаторы 1..9 заняты сводками групп. */
    fun notificationId(key: String): Int = (key.hashCode() and 0x3fffffff) + 10
}

/** Экран, который нужно открыть после нажатия на уведомление; читается в AppNavigation. */
object NotificationNav {
    val pendingRoute = MutableStateFlow<String?>(null)
    fun consume() { pendingRoute.value = null }
}

/** Когда в последний раз напоминали об окончании пробного периода подписки (чтобы не повторяться в один день). */
class TrialNotifyStore(context: Context) {
    private val prefs = context.getSharedPreferences("trial_notified", Context.MODE_PRIVATE)

    fun all(): Map<String, LocalDate> =
        prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to LocalDate.ofEpochDay(it) } }.toMap()

    fun mark(subscriptionId: String, day: LocalDate) {
        prefs.edit().putLong(subscriptionId, day.toEpochDay()).apply()
    }
}
