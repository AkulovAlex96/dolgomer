package app.kvity.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kvity.data.CalendarEvent
import app.kvity.data.DebtRepository
import app.kvity.data.EventKind
import app.kvity.data.Events
import app.kvity.data.SubscriptionRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalendarViewModel(
    private val debts: DebtRepository,
    private val subscriptions: SubscriptionRepository,
) : ViewModel() {

    val events: StateFlow<List<CalendarEvent>> = combine(debts.observeDebts(), subscriptions.observeAll()) { d, s ->
        Events.build(d, s, LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markSharePaid(e: CalendarEvent) {
        if (e.kind == EventKind.DEBT) {
            // платёж по долгу: вносится плановая сумма на сегодня
            viewModelScope.launch { debts.addPayment(e.refId, LocalDate.now(), e.amountMinor) }
            return
        }
        val period = e.periodDate ?: return
        viewModelScope.launch {
            if (e.shareId != null) subscriptions.markPaid(e.shareId, period, e.amountMinor)
            else subscriptions.markChargePaid(e.refId, period, e.amountMinor)
        }
    }
}
