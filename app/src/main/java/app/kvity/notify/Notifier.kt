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
import app.kvity.R
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
    const val EXTRA_REMIND = "remind"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.notify_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.notify_channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Открывает [route] в приложении (пустой маршрут — просто приложение). */
    private fun openIntent(context: Context, requestCode: Int, route: String?, remind: RemindRequest? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (route != null) intent.putExtra(EXTRA_ROUTE, route)
        if (remind != null) intent.putExtra(EXTRA_REMIND, remind.encode())
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
            Triple(1, context.getString(R.string.notify_group_owed_to_me), items.filter { it.event.kind == EventKind.DEBT && it.event.flow == MoneyFlow.IN }),
            Triple(2, context.getString(R.string.notify_group_i_owe), items.filter { it.event.kind == EventKind.DEBT && it.event.flow == MoneyFlow.OUT }),
            Triple(3, context.getString(R.string.notify_group_subscriptions), items.filter { it.event.kind == EventKind.SUBSCRIPTION }),
        )
        for ((groupId, title, list) in groups) {
            if (list.isEmpty()) continue
            val group = "payments_$groupId"
            for (item in list) postItem(context, item, title, group, hideDetails)
            if (list.size > 1) {
                // сводка группы: свёрнутое уведомление с количеством и общей суммой
                val collapsed = if (hideDetails) context.getString(R.string.notify_count_hidden, list.size) else summary(list, ContextNotifyStrings(context))
                val summary = builder(context, group, hideDetails)
                    .setContentTitle(title)
                    .setContentText(collapsed)
                    .setStyle(NotificationCompat.InboxStyle().also { s -> if (!hideDetails) list.forEach { s.addLine(line(context, it)) } })
                    .setGroupSummary(true)
                    .setContentIntent(openIntent(context, groupId, null))
                notify(context, groupId, summary)
            }
        }
    }

    private fun postItem(context: Context, item: ReminderItem, title: String, group: String, hideDetails: Boolean) {
        val e = item.event
        val id = NotifyActions.notificationId(e)
        val text = if (hideDetails) context.getString(R.string.notify_payment_hidden, whenText(context, item)) else line(context, item)
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
            b.addAction(0, context.getString(R.string.notify_action_paid), paid)
        }
        val remind = if (NotifyActions.canRemind(e)) RemindRequest.of(e) else null
        if (remind != null) {
            // открывает экран долга/подписки и сразу диалог выбора канала и шаблона
            b.addAction(
                0, context.getString(R.string.notify_action_remind),
                openIntent(context, NotifyActions.actionRequestCode(id), NotifyActions.route(e), remind),
            )
        }
        notify(context, id, b)
    }

    /** Окончание пробного периода подписки. Возвращает true, если уведомление показано. */
    fun postTrial(context: Context, trial: TrialReminder, hideDetails: Boolean): Boolean {
        if (!canPost(context)) return false
        createChannel(context)
        val id = NotifyActions.notificationId("trial|${trial.subscriptionId}")
        val text = if (hideDetails) context.getString(R.string.notify_trial_hidden) else trialText(context, trial)
        val route = "sub/${trial.subscriptionId}"
        val b = builder(context, "payments_3", hideDetails)
            .setContentTitle(context.getString(R.string.notify_trial_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(context, id, route))
            .addAction(0, context.getString(R.string.notify_action_open), openIntent(context, NotifyActions.actionRequestCode(id), route))
        notify(context, id, b)
        return true
    }

    /** Долг без срока, по которому давно не было платежа. */
    fun postStale(context: Context, stale: StaleDebtReminder, hideDetails: Boolean) {
        if (!canPost(context)) return
        createChannel(context)
        val id = NotifyActions.notificationId("stale|${stale.debtId}")
        val text = if (hideDetails) context.getString(R.string.notify_stale_hidden) else staleText(context, stale)
        val route = "debt/${stale.debtId}"
        val b = builder(context, if (stale.owedToMe) "payments_1" else "payments_2", hideDetails)
            .setContentTitle(context.getString(if (stale.owedToMe) R.string.notify_group_owed_to_me else R.string.notify_group_i_owe))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(context, id, route))
            .addAction(0, context.getString(R.string.notify_action_open), openIntent(context, NotifyActions.actionRequestCode(id), route))
        notify(context, id, b)
    }

    internal fun trialText(context: Context, t: TrialReminder): String = when (t.days) {
        0L -> context.getString(R.string.notify_trial_today, t.name)
        else -> context.getString(R.string.notify_trial_days, t.name, t.days.toInt(), formatDate(t.endsOn))
    }

    internal fun staleText(context: Context, s: StaleDebtReminder): String =
        context.getString(R.string.notify_stale_text, s.name, formatMoney(s.remainingMinor, s.currency), s.days)

    /** Подпись количества платежей в сводке: из ресурсов (plurals) или, в тестах без Context, русская. */
    internal interface NotifyStrings { fun paymentsCount(n: Int): String }

    internal object RuNotifyStrings : NotifyStrings {
        override fun paymentsCount(n: Int) = "$n ${plural(n)}"
    }

    internal class ContextNotifyStrings(private val context: Context) : NotifyStrings {
        override fun paymentsCount(n: Int) = context.resources.getQuantityString(R.plurals.notify_payments_count, n, n)
    }

    /** «3 платежа: 4 777 ₽ + 50 $»: количество и итог по каждой валюте. */
    internal fun summary(items: List<ReminderItem>, strings: NotifyStrings = RuNotifyStrings): String {
        val totals = items.groupBy { it.event.currency }
            .map { (code, list) -> code to list.sumOf { it.event.amountMinor } }
            .sortedByDescending { it.second }
        return strings.paymentsCount(items.size) + ": " + totals.joinToString(" + ") { (code, minor) -> formatMoney(minor, code) }
    }

    internal fun plural(n: Int): String = when {
        n % 100 in 11..14 -> "платежей"
        n % 10 == 1 -> "платёж"
        n % 10 in 2..4 -> "платежа"
        else -> "платежей"
    }

    private fun whenText(context: Context, item: ReminderItem): String = when (item.type) {
        ReminderType.TODAY -> context.getString(R.string.notify_when_today)
        ReminderType.UPCOMING -> context.getString(R.string.notify_when_upcoming, item.days, formatDate(item.event.date))
        ReminderType.OVERDUE -> context.getString(R.string.notify_when_overdue, item.days)
    }

    private fun line(context: Context, item: ReminderItem): String {
        val e = item.event
        return context.getString(R.string.notify_line, e.title, formatMoney(e.amountMinor, e.currency), whenText(context, item))
    }
}
