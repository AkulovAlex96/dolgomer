package app.kvity.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.kvity.KvityApp
import app.kvity.MainActivity
import app.kvity.R
import app.kvity.data.DebtView
import app.kvity.data.Events
import app.kvity.data.SubscriptionView
import app.kvity.ui.formatDate
import app.kvity.ui.formatMoney
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Виджет главного экрана: ближайшие платежи и просрочки. */
class KvityWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refreshNow(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private fun ids(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return manager.getAppWidgetIds(ComponentName(context, KvityWidget::class.java))
        }

        /** Перерисовывает виджеты (если они есть на экране): вызывать после изменения данных. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            if (ids(app).isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { runCatching { refreshNow(app) } }
        }

        private suspend fun refreshNow(context: Context) {
            if (ids(context).isEmpty()) return
            val app = context as? KvityApp ?: return
            update(context, app.debts.observeDebts().first(), app.subscriptions.observeAll().first())
        }

        /** Рисует виджеты по уже загруженным данным. */
        fun update(context: Context, debts: List<DebtView>, subs: List<SubscriptionView>) {
            val app = context.applicationContext as? KvityApp ?: return
            val manager = AppWidgetManager.getInstance(context)
            val ids = ids(context)
            if (ids.isEmpty()) return
            val settings = app.settings.current
            val locked = settings.pinEnabled && app.pin.isSet
            val today = LocalDate.now()
            val content = WidgetModel.build(
                if (locked) emptyList() else Events.build(debts.filter { !it.debt.archived }, subs, today),
                today, locked, settings.hideAmountsInNotifications,
            )
            for (id in ids) manager.updateAppWidget(id, render(context, content))
        }

        private fun render(context: Context, content: WidgetContent): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_payments)
            views.removeAllViews(R.id.widget_rows)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            val message = when (content) {
                WidgetContent.Locked -> R.string.widget_locked
                WidgetContent.Empty -> R.string.widget_empty
                is WidgetContent.Items -> null
            }
            if (message != null) {
                views.setViewVisibility(R.id.widget_message, View.VISIBLE)
                views.setTextViewText(R.id.widget_message, context.getString(message))
                return views
            }
            views.setViewVisibility(R.id.widget_message, View.GONE)
            for (row in (content as WidgetContent.Items).rows) {
                val item = RemoteViews(context.packageName, R.layout.widget_payment_item)
                item.setTextViewText(R.id.item_date, formatDate(row.date))
                item.setTextViewText(R.id.item_name, row.name ?: context.getString(R.string.widget_hidden_item))
                item.setTextViewText(
                    R.id.item_amount,
                    row.amountMinor?.let { formatMoney(it, row.currency) } ?: context.getString(R.string.widget_amount_hidden),
                )
                item.setViewVisibility(R.id.item_overdue, if (row.overdue) View.VISIBLE else View.INVISIBLE)
                views.addView(R.id.widget_rows, item)
            }
            return views
        }
    }
}
