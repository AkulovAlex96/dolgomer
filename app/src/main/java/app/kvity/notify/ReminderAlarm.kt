package app.kvity.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import app.kvity.KvityApp
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Точный будильник на время ежедневной проверки (п. 3.6 ТЗ, «где доступно»).
 * Если разрешение SCHEDULE_EXACT_ALARM не выдано (Android 12+), пользуются запасным путём — WorkManager.
 */
object ReminderAlarm {
    private const val REQUEST = 7001

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Открывает системный экран выдачи разрешения на точные будильники. */
    fun requestExactPermission(context: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        val intent = Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, ReminderAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Ставит будильник на [at]; false — если точный будильник недоступен и нужен запасной путь. */
    fun scheduleExact(context: Context, at: LocalDateTime): Boolean {
        if (!canScheduleExact(context)) return false
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return try {
            context.getSystemService(AlarmManager::class.java)
                .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(context))
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
    }
}

/** Сработал будильник: запускаем проверку; она же поставит следующий будильник. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderWorker.runNow(context)
    }
}

/** После перезагрузки, смены времени или часового пояса, обновления и изменения разрешения заново ставит расписание. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? KvityApp ?: return
        ReminderWorker.schedule(context, app.settings.current)
    }
}
