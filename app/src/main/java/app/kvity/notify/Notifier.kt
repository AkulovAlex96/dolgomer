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
        val channel = NotificationChannel(CHANNEL, "Платежи и подписки", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Сроки платежей по долгам и списания подписок" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Группирует напоминания в несколько уведомлений: мне должны, я должен, подписки. */
    fun post(context: Context, items: List<ReminderItem>, hideDetails: Boolean) {
        if (items.isEmpty() || !canPost(context)) return
        createChannel(context)
        val groups = listOf(
            Triple(1, "Мне должны", items.filter { it.event.kind == EventKind.DEBT && it.event.flow == MoneyFlow.IN }),
            Triple(2, "Я должен", items.filter { it.event.kind == EventKind.DEBT && it.event.flow == MoneyFlow.OUT }),
            Triple(3, "Подписки", items.filter { it.event.kind == EventKind.SUBSCRIPTION }),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        for ((id, title, list) in groups) {
            if (list.isEmpty()) continue
            val lines = if (hideDetails) listOf("Платежей: ${list.size}") else list.map(::line)
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(lines.first())
                .setStyle(NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach(s::addLine) })
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(if (hideDetails) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
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
