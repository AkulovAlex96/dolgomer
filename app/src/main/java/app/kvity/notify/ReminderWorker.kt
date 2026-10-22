package app.kvity.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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

/** Раз в сутки в заданное время (см. [schedule]) проверяет сроки и показывает уведомления. */
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
        // следующий запуск ставится заново на ближайшее заданное время: так расписание не «уплывает» от фактического времени этого запуска
        schedule(applicationContext, settings)
        return Result.success()
    }

    companion object {
        private const val NAME = "daily_reminders"

        /** Выполняет проверку немедленно (кнопка «Проверить сейчас» в настройках). */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
        }

        /**
         * Ставит один запуск на ближайшее время суток из настроек (9:00 и т.п.). Каждый запуск планирует следующий.
         * PeriodicWorkRequest для этого не годится: он повторяется «примерно раз в сутки» от прошлого запуска
         * и со временем уходит от заданного часа (уведомления приходили в 5:04 при настройке 9:00).
         */
        fun schedule(context: Context, settings: Settings) {
            val wm = WorkManager.getInstance(context)
            if (!settings.notificationsEnabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val delay = delayUntilNext(LocalDateTime.now(), settings.notifyHour, settings.notifyMinute)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** Сколько ждать до ближайших [hour]:[minute]; если это время уже наступило (или осталась минута), берётся завтрашнее. */
        internal fun delayUntilNext(now: LocalDateTime, hour: Int, minute: Int): Duration {
            var next = now.toLocalDate().atTime(hour, minute)
            // запас в минуту: запуск, сработавший чуть раньше срока, не должен сразу назначить себя же на сегодня
            if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
