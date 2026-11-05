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

/** Раз в сутки в заданное время (см. [schedule]; точный будильник или WorkManager) проверяет сроки и показывает уведомления. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as KvityApp
        val settings = app.settings.current
        if (!settings.notificationsEnabled) return Result.success()
        // следующий запуск ставится до работы: исключение ниже не должно обрывать цепочку напоминаний
        schedule(applicationContext, settings)

        val today = LocalDate.now()
        val debts = app.debts.observeDebts().first()
        val subs = app.subscriptions.observeAll().first()
        val items = ReminderPlanner.plan(Events.build(debts, subs, today), today, settings, ReminderPlanner.overridesOf(debts, subs))
        Notifier.post(applicationContext, items, settings.hideAmountsInNotifications)
        for (stale in ReminderPlanner.planStale(debts, today, settings)) {
            Notifier.postStale(applicationContext, stale, settings.hideAmountsInNotifications)
        }
        // пробный период: не чаще раза в день на подписку (запоминаем, о чём уже сообщили)
        val trialStore = TrialNotifyStore(applicationContext)
        val trials = ReminderPlanner.planTrials(subs.map { it.subscription }, today, settings, trialStore.all())
        for (t in trials) {
            if (Notifier.postTrial(applicationContext, t, settings.hideAmountsInNotifications)) trialStore.mark(t.subscriptionId, today)
        }
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
        fun schedule(context: Context, settings: Settings, replace: Boolean = true) {
            val wm = WorkManager.getInstance(context)
            if (!settings.notificationsEnabled) {
                wm.cancelUniqueWork(NAME)
                ReminderAlarm.cancel(context)
                return
            }
            val now = LocalDateTime.now()
            // точный будильник, если разрешён (п. 3.6 ТЗ); иначе WorkManager: он может задержаться в режиме энергосбережения
            if (ReminderAlarm.scheduleExact(context, nextTrigger(now, settings.notifyHour, settings.notifyMinute))) {
                wm.cancelUniqueWork(NAME)
                return
            }
            ReminderAlarm.cancel(context)
            val delay = delayUntilNext(now, settings.notifyHour, settings.notifyMinute)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            // при старте процесса (replace = false) уже ожидающий или выполняющийся запуск не трогаем: иначе холодный старт в момент срабатывания отменяет сегодняшнюю проверку
            wm.enqueueUniqueWork(NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
        }

        /** Сколько ждать до ближайших [hour]:[minute]; если это время уже наступило (или осталась минута), берётся завтрашнее. */
        internal fun delayUntilNext(now: LocalDateTime, hour: Int, minute: Int): Duration =
            Duration.between(now, nextTrigger(now, hour, minute))

        /** Ближайшие [hour]:[minute] по тем же правилам, что и [delayUntilNext]. */
        internal fun nextTrigger(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
            var next = now.toLocalDate().atTime(hour, minute)
            // запас в минуту: запуск, сработавший чуть раньше срока, не должен сразу назначить себя же на сегодня
            if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
            return next
        }
    }
}
