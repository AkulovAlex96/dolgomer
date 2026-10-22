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
import app.kvity.ui.formatDate
import app.kvity.ui.formatMoney

object Notifier {
    const val CHANNEL = "payments"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(CHANNEL, "Платежи", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Сроки платежей по долгам и списания подписок" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Группирует напоминания в несколько уведомлений: мне должны, я должен, регулярные платежи. */
    fun post(context: Context, items: List<ReminderItem>, hideDetails: Boolean) {
        if (items.isEmpty() || !canPost(context)) return
        createChannel(context)
        val groups = listOf(
            Triple(1, "Мне должны", items.filter { it.event.kind == EventKind.DEBT && it.event.flow == MoneyFlow.IN }),
            Triple(2, "Я должен", items.filter { it.event.kind == EventKind.DEBT && it.event.flow == MoneyFlow.OUT }),
            Triple(3, "Регулярные платежи", items.filter { it.event.kind == EventKind.SUBSCRIPTION }),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        for ((id, title, list) in groups) {
            if (list.isEmpty()) continue
            val lines = list.map(::line)
            // свёрнутое уведомление: при нескольких платежах количество и общая сумма, иначе единственная строка как есть
            val collapsed = if (hideDetails) "Платежей: ${list.size}" else if (list.size > 1) summary(list) else lines.first()
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(collapsed)
                .setStyle(NotificationCompat.BigTextStyle().bigText(if (hideDetails) collapsed else lines.joinToString("\n")))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(if (hideDetails) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
    }

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

    private fun line(item: ReminderItem): String {
        val e = item.event
        val amount = formatMoney(e.amountMinor, e.currency)
        val what = when (item.type) {
            ReminderType.TODAY -> "сегодня"
            ReminderType.UPCOMING -> "через ${item.days} дн. (${formatDate(e.date)})"
            ReminderType.OVERDUE -> "просрочено на ${item.days} дн."
        }
        return "${e.title}: $amount, $what"
    }
}
