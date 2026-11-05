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

    /** Код PendingIntent вторых действий уведомления: отрицательный, не пересекается с id уведомлений. */
    fun actionRequestCode(notificationId: Int): Int = notificationId.inv()

    /** Экран приложения, который открывает уведомление (маршруты как в [app.kvity.ui.Routes]). */
    fun route(e: CalendarEvent): String = if (e.kind == EventKind.DEBT) "debt/${e.refId}" else "sub/${e.refId}"

    /** Стабильный идентификатор уведомления о событии: повтор проверки заменяет, а не дублирует. */
    fun notificationId(e: CalendarEvent): Int =
        notificationId("${e.kind}|${e.refId}|${e.shareId}|${e.periodDate ?: e.date}")

    /** Идентификаторы 1..9 заняты сводками групп. */
    fun notificationId(key: String): Int = (key.hashCode() and 0x3fffffff) + 10
}

/** Команда «открыть диалог напоминания» для долга или доли участника подписки. */
sealed class RemindRequest {
    data class Debt(val debtId: String) : RemindRequest()
    data class Share(val subscriptionId: String, val shareId: String, val period: LocalDate) : RemindRequest()

    /** Строка для extra интента: `debt:<id>` или `share:<подписка>:<доля>:<epochDay>`. */
    fun encode(): String = when (this) {
        is Debt -> "debt:$debtId"
        is Share -> "share:$subscriptionId:$shareId:${period.toEpochDay()}"
    }

    companion object {
        fun decode(s: String?): RemindRequest? {
            val p = s?.split(':') ?: return null
            return when {
                p.size == 2 && p[0] == "debt" && p[1].isNotEmpty() -> Debt(p[1])
                p.size == 4 && p[0] == "share" && p[1].isNotEmpty() && p[2].isNotEmpty() ->
                    p[3].toLongOrNull()?.let { Share(p[1], p[2], LocalDate.ofEpochDay(it)) }
                else -> null
            }
        }

        fun of(e: CalendarEvent): RemindRequest? {
            val shareId = e.shareId
            val period = e.periodDate
            return when {
                e.flow != MoneyFlow.IN -> null
                e.kind == EventKind.DEBT -> Debt(e.refId)
                shareId != null && period != null -> Share(e.refId, shareId, period)
                else -> null
            }
        }
    }
}

/** Экран и диалог, которые нужно открыть после нажатия на уведомление; читаются в AppNavigation. */
object NotificationNav {
    val pendingRoute = MutableStateFlow<String?>(null)
    val pendingRemind = MutableStateFlow<RemindRequest?>(null)
    fun consume() { pendingRoute.value = null }
    fun consumeRemind() { pendingRemind.value = null }
}

/** Мягкий запрос разрешения на точные будильники: только если разрешения нет и ещё не спрашивали. */
object ExactAlarmPolicy {
    fun shouldOffer(sdk: Int, canScheduleExact: Boolean, alreadyAsked: Boolean, notificationsEnabled: Boolean, canPost: Boolean): Boolean =
        sdk >= 31 && !canScheduleExact && !alreadyAsked && notificationsEnabled && canPost
}

/** Какую сводку группы нужно снять вместе с отменой уведомления. */
object NotifyGroups {
    /** [active] — пары (id, группа) активных уведомлений ДО отмены. Сводки групп имеют id < 10. */
    fun summaryToCancel(active: List<Pair<Int, String?>>, cancelledId: Int): Int? {
        val group = active.firstOrNull { it.first == cancelledId }?.second ?: return null
        val rest = active.filter { it.second == group && it.first != cancelledId }
        return if (rest.size == 1 && rest[0].first < 10) rest[0].first else null
    }
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
