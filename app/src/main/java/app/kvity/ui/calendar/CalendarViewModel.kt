package app.kvity.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kvity.data.CalendarEvent
import app.kvity.data.DebtRepository
import app.kvity.data.Events
import app.kvity.data.SubscriptionRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CalendarViewModel(
    debts: DebtRepository,
    private val subscriptions: SubscriptionRepository,
) : ViewModel() {

    val events: StateFlow<List<CalendarEvent>> = combine(debts.observeDebts(), subscriptions.observeAll()) { d, s ->
        Events.build(d, s, LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markSharePaid(e: CalendarEvent) {
        val shareId = e.shareId ?: return
        val period = e.periodDate ?: return
        viewModelScope.launch { subscriptions.markPaid(shareId, period, e.amountMinor) }
    }
}
