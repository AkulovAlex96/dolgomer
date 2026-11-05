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
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (target) {
                    is PaidTarget.Debt -> app.debts.addPayment(target.debtId, LocalDate.now(), target.amountMinor)
                    is PaidTarget.Charge -> app.subscriptions.markChargePaid(target.subscriptionId, target.period, target.amountMinor)
                    is PaidTarget.Share -> app.subscriptions.markPaid(target.shareId, target.period, target.amountMinor)
                }
                cancel(context, notificationId)
            } finally {
                pending.finish()
            }
        }
    }

    /** Снимает уведомление; если в группе осталась только сводка, снимает и её. */
    private fun cancel(context: Context, id: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val group = nm.activeNotifications.firstOrNull { it.id == id }?.notification?.group
        nm.cancel(id)
        if (group != null) {
            val rest = nm.activeNotifications.filter { it.notification.group == group }
            if (rest.size == 1 && rest[0].id < 10) nm.cancel(rest[0].id)
        }
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

        private fun parse(intent: Intent): PaidTarget? {
            val id = intent.getStringExtra(EXTRA_ID) ?: return null
            val amount = intent.getLongExtra(EXTRA_AMOUNT, 0)
            if (amount <= 0) return null
            val period = intent.getLongExtra(EXTRA_PERIOD, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
            return when (intent.getStringExtra(EXTRA_TYPE)) {
                "debt" -> PaidTarget.Debt(id, amount)
                "charge" -> period?.let { PaidTarget.Charge(id, it, amount) }
                "share" -> period?.let { PaidTarget.Share(id, it, amount) }
                else -> null
            }
        }
    }
}
