package app.kvity.notify

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.kvity.KvityApp
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Действие «Отметить оплаченным» из уведомления. После выполнения уведомление снимается. */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PAID) return
        val app = context.applicationContext as? KvityApp ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val target = parse(intent)
        if (target == null) {
            cancel(context, notificationId)
            return
        }
        // уведомление снимаем сразу: повторное нажатие, пока идёт запись, иначе дало бы двойной платёж по долгу
        cancel(context, notificationId)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (target) {
                    is PaidTarget.Debt -> app.debts.addPayment(target.debtId, LocalDate.now(), target.amountMinor)
                    is PaidTarget.Charge -> app.subscriptions.markChargePaid(target.subscriptionId, target.period, target.amountMinor)
                    is PaidTarget.Share -> app.subscriptions.markPaid(target.shareId, target.period, target.amountMinor)
                }
            } catch (e: Exception) {
                // сбой записи не должен ронять приложение
                android.util.Log.w("ReminderAction", "mark paid failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** Снимает уведомление; если в группе осталась только сводка, снимает и её. */
    private fun cancel(context: Context, id: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val summary = NotifyGroups.summaryToCancel(nm.activeNotifications.map { it.id to it.notification.group }, id)
        nm.cancel(id)
        if (summary != null) nm.cancel(summary)
    }

    companion object {
        const val ACTION_PAID = "app.kvity.action.PAID"
        const val EXTRA_NOTIFICATION_ID = "nid"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_ID = "id"
        private const val EXTRA_PERIOD = "period"
        private const val EXTRA_AMOUNT = "amount"

        fun fill(intent: Intent, target: PaidTarget) {
            when (target) {
                is PaidTarget.Debt -> intent.putExtra(EXTRA_TYPE, "debt").putExtra(EXTRA_ID, target.debtId)
                    .putExtra(EXTRA_AMOUNT, target.amountMinor)
                is PaidTarget.Charge -> intent.putExtra(EXTRA_TYPE, "charge").putExtra(EXTRA_ID, target.subscriptionId)
                    .putExtra(EXTRA_PERIOD, target.period.toEpochDay()).putExtra(EXTRA_AMOUNT, target.amountMinor)
                is PaidTarget.Share -> intent.putExtra(EXTRA_TYPE, "share").putExtra(EXTRA_ID, target.shareId)
                    .putExtra(EXTRA_PERIOD, target.period.toEpochDay()).putExtra(EXTRA_AMOUNT, target.amountMinor)
            }
        }

        private fun parse(intent: Intent): PaidTarget? = parseTarget(
            intent.getStringExtra(EXTRA_TYPE), intent.getStringExtra(EXTRA_ID),
            intent.getLongExtra(EXTRA_PERIOD, Long.MIN_VALUE), intent.getLongExtra(EXTRA_AMOUNT, 0),
        )

        /** Чистый разбор extras ([periodDay] = Long.MIN_VALUE — не задан); null — действие выполнять нельзя. */
        internal fun parseTarget(type: String?, id: String?, periodDay: Long, amount: Long): PaidTarget? {
            if (id.isNullOrEmpty() || amount <= 0) return null
            val period = periodDay.takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
            return when (type) {
                "debt" -> PaidTarget.Debt(id, amount)
                "charge" -> period?.let { PaidTarget.Charge(id, it, amount) }
                "share" -> period?.let { PaidTarget.Share(id, it, amount) }
                else -> null
            }
        }
    }
}
