package app.kvity.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kvity.KvityApp
import app.kvity.data.Events
import app.kvity.data.ReminderPlanner
import app.kvity.data.Settings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Раз в сутки в заданное время проверяет сроки и показывает уведомления. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as KvityApp
        val settings = app.settings.current
        if (!settings.notificationsEnabled) return Result.success()

        val today = LocalDate.now()
        val debts = app.debts.observeDebts().first()
        val subs = app.subscriptions.observeAll().first()
        val items = ReminderPlanner.plan(Events.build(debts, subs, today), today, settings)
        Notifier.post(applicationContext, items, settings.hideAmountsInNotifications)
        return Result.success()
    }

    companion object {
        private const val NAME = "daily_reminders"

        /** Выполняет проверку немедленно (кнопка «Проверить сейчас» в настройках). */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(androidx.work.OneTimeWorkRequestBuilder<ReminderWorker>().build())
        }

        /** Пересоздаёт расписание под настройки (время дня). Безопасно вызывать при каждом изменении. */
        fun schedule(context: Context, settings: Settings) {
            val wm = WorkManager.getInstance(context)
            if (!settings.notificationsEnabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(settings.notifyHour, settings.notifyMinute)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
