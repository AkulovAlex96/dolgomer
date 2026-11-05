package app.kvity.ui.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kvity.data.DebtRepository
import app.kvity.data.DebtView
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _recalcOffer = MutableStateFlow(false)
    /** После досрочного или нестандартного платежа true: экран предлагает пересчитать график. */
    val recalcOffer: StateFlow<Boolean> = _recalcOffer

    init {
        // долги старых версий переносят график в таблицу, чтобы его можно было править
        viewModelScope.launch { repository.ensureMaterialized(debtId) }
    }

    fun addPayment(
        date: LocalDate, amountMinor: Long, plannedId: String? = null, monthOnly: Boolean = false,
        paymentMethod: String? = null, note: String? = null,
    ) {
        viewModelScope.launch {
            _recalcOffer.value = repository.addPayment(
                debtId, date, amountMinor, note, plannedId = plannedId, monthOnly = monthOnly, paymentMethod = paymentMethod,
            )
        }
    }

    fun updatePayment(
        id: String, date: LocalDate, amountMinor: Long, plannedId: String?, monthOnly: Boolean,
        paymentMethod: String?, note: String?,
    ) {
        viewModelScope.launch {
            _recalcOffer.value = repository.updatePayment(id, date, amountMinor, plannedId, monthOnly, paymentMethod, note)
        }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { repository.setArchived(debtId, archived) }
    }

    fun setNotify(mode: app.kvity.data.NotifyMode, days: String?) {
        viewModelScope.launch { repository.setNotify(debtId, mode, days) }
    }

    fun recalc() {
        _recalcOffer.value = false
        viewModelScope.launch { repository.recalcReducePayment(debtId) }
    }

    fun dismissRecalc() { _recalcOffer.value = false }

    fun updatePlanned(id: String, date: LocalDate, amountMinor: Long) {
        viewModelScope.launch { repository.updatePlanned(id, date, amountMinor) }
    }

    fun skipPlanned(id: String, skipped: Boolean) {
        viewModelScope.launch { repository.setPlannedSkipped(id, skipped) }
    }

    fun deletePlanned(id: String) {
        viewModelScope.launch { repository.deletePlanned(id) }
    }

    fun deletePayment(id: String) {
        viewModelScope.launch { repository.deletePayment(id) }
    }

    fun deleteDebt() {
        viewModelScope.launch { repository.deleteDebt(debtId) }
    }
}
