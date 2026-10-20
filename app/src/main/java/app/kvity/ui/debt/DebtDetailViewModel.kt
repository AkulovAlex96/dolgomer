package app.kvity.ui.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kvity.data.DebtRepository
import app.kvity.data.DebtView
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DebtDetailViewModel(
    private val repository: DebtRepository,
    private val debtId: String,
) : ViewModel() {

    val view: StateFlow<DebtView?> = repository.observeDebt(debtId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addPayment(date: LocalDate, amountMinor: Long) {
        viewModelScope.launch { repository.addPayment(debtId, date, amountMinor) }
    }

    fun deletePayment(id: String) {
        viewModelScope.launch { repository.deletePayment(id) }
    }

    fun deleteDebt() {
        viewModelScope.launch { repository.deleteDebt(debtId) }
    }
}
