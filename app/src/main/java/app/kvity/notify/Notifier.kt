package app.kvity.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.kvity.MainActivity
import app.kvity.data.EventKind
import app.kvity.data.MoneyFlow
import app.kvity.data.ReminderItem
import app.kvity.data.ReminderType
import app.kvity.data.StaleDebtReminder
import app.kvity.data.TrialReminder
import app.kvity.ui.formatDate
import app.kvity.ui.formatMoney

object Notifier {
    const val CHANNEL = "payments"
    const val EXTRA_ROUTE = "route"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL, "Платежи", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Сроки платежей по долгам и списания подписок" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Открывает [route] в приложении (пустой маршрут — просто приложение). */
    private fun openIntent(context: Context, requestCode: Int, route: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (route != null) intent.putExtra(EXTRA_ROUTE, route)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun builder(context: Context, group: String, hideDetails: Boolean) =
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setGroup(group)
            .setAutoCancel(true)
            .setVisibility(if (hideDetails) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)

    @Suppress("MissingPermission")
    private fun notify(context: Context, id: Int, b: NotificationCompat.Builder) {
        NotificationManagerCompat.from(context).notify(id, b.build())
    }

    /**
     * Каждое напоминание — отдельное уведомление с действиями «Отметить оплаченным», «Напомнить» и «Открыть»;
     * по трём группам (мне должны, я должен, регулярные платежи) при нескольких платежах добавляется сводка.
     */
    fun post(context: Context, items: List<ReminderItem>, hideDetails: Boolean) {
        if (items.isEmpty() || !canPost(context)) return
        createChannel(context)
        val groups = listOf(
            Triple(1, "Мне должны", items.filter { it.event.kind == EventKind.DEBT && it.event.flow == MoneyFlow.IN }),
            Triple(2, "Я должен", items.filter { it.event.kind == EventKind.DEBT && it.event.flow == MoneyFlow.OUT }),
            Triple(3, "Регулярные платежи", items.filter { it.event.kind == EventKind.SUBSCRIPTION }),
        )
        for ((groupId, title, list) in groups) {
            if (list.isEmpty()) continue
            val group = "payments_$groupId"
            for (item in list) postItem(context, item, title, group, hideDetails)
            if (list.size > 1) {
                // сводка группы: свёрнутое уведомление с количеством и общей суммой
                val collapsed = if (hideDetails) "Платежей: ${list.size}" else summary(list)
                val summary = builder(context, group, hideDetails)
                    .setContentTitle(title)
                    .setContentText(collapsed)
                    .setStyle(NotificationCompat.InboxStyle().also { s -> if (!hideDetails) list.forEach { s.addLine(line(it)) } })
                    .setGroupSummary(true)
                    .setContentIntent(openIntent(context, groupId, null))
                notify(context, groupId, summary)
            }
        }
    }

    private fun postItem(context: Context, item: ReminderItem, title: String, group: String, hideDetails: Boolean) {
        val e = item.event
        val id = NotifyActions.notificationId(e)
        val text = if (hideDetails) "Платёж: ${whenText(item)}" else line(item)
        val b = builder(context, group, hideDetails)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(context, id, NotifyActions.route(e)))
        NotifyActions.paidTarget(e)?.let { target ->
            val intent = Intent(context, ReminderActionReceiver::class.java)
                .setAction(ReminderActionReceiver.ACTION_PAID)
                .putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, id)
            ReminderActionReceiver.fill(intent, target)
            val paid = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(0, "Отметить оплаченным", paid)
        }
        if (NotifyActions.canRemind(e)) {
            // открывает экран долга/подписки, где есть кнопка «Напомнить» с выбором канала и шаблона
            b.addAction(0, "Напомнить", openIntent(context, id + 1, NotifyActions.route(e)))
        }
        notify(context, id, b)
    }

    /** Окончание пробного периода подписки. Возвращает true, если уведомление показано. */
    fun postTrial(context: Context, trial: TrialReminder, hideDetails: Boolean): Boolean {
        if (!canPost(context)) return false
        createChannel(context)
        val id = NotifyActions.notificationId("trial|${trial.subscriptionId}")
        val text = if (hideDetails) "Пробный период заканчивается" else trialText(trial)
        val route = "sub/${trial.subscriptionId}"
        val b = builder(context, "payments_3", hideDetails)
            .setContentTitle("Пробный период")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(context, id, route))
            .addAction(0, "Открыть", openIntent(context, id + 1, route))
        notify(context, id, b)
        return true
    }

    /** Долг без срока, по которому давно не было платежа. */
    fun postStale(context: Context, stale: StaleDebtReminder, hideDetails: Boolean) {
        if (!canPost(context)) return
        createChannel(context)
        val id = NotifyActions.notificationId("stale|${stale.debtId}")
        val text = if (hideDetails) "Давно не было платежа" else staleText(stale)
        val route = "debt/${stale.debtId}"
        val b = builder(context, if (stale.owedToMe) "payments_1" else "payments_2", hideDetails)
            .setContentTitle(if (stale.owedToMe) "Мне должны" else "Я должен")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(context, id, route))
            .addAction(0, "Открыть", openIntent(context, id + 1, route))
        notify(context, id, b)
    }

    internal fun trialText(t: TrialReminder): String = when (t.days) {
        0L -> "${t.name}: пробный период заканчивается сегодня"
        else -> "${t.name}: пробный период заканчивается через ${t.days} дн. (${formatDate(t.endsOn)})"
    }

    internal fun staleText(s: StaleDebtReminder): String =
        "${s.name}: ${formatMoney(s.remainingMinor, s.currency)}, давно не было платежа (${s.days} дн.)"

    /** «3 платежа: 4 777 ₽ + 50 $»: количество и итог по каждой валюте. */
    internal fun summary(items: List<ReminderItem>): String {
        val totals = items.groupBy { it.event.currency }
            .map { (code, list) -> code to list.sumOf { it.event.amountMinor } }
            .sortedByDescending { it.second }
        return "${items.size} ${plural(items.size)}: " + totals.joinToString(" + ") { (code, minor) -> formatMoney(minor, code) }
    }

    internal fun plural(n: Int): String = when {
        n % 100 in 11..14 -> "платежей"
        n % 10 == 1 -> "платёж"
        n % 10 in 2..4 -> "платежа"
        else -> "платежей"
    }

    private fun whenText(item: ReminderItem): String = when (item.type) {
        ReminderType.TODAY -> "сегодня"
        ReminderType.UPCOMING -> "через ${item.days} дн. (${formatDate(item.event.date)})"
        ReminderType.OVERDUE -> "просрочено на ${item.days} дн."
    }

    private fun line(item: ReminderItem): String {
        val e = item.event
        return "${e.title}: ${formatMoney(e.amountMinor, e.currency)}, ${whenText(item)}"
    }
}
