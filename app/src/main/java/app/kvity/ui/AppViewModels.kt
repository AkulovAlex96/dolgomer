package app.kvity.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kvity.domain.AppTexts
import app.kvity.domain.ArchiveRules
import app.kvity.data.DebtRepository
import app.kvity.data.DebtView
import app.kvity.data.NewShare
import app.kvity.data.PeopleRepository
import app.kvity.data.PersonView
import app.kvity.data.ShareView
import app.kvity.data.SubscriptionInput
import app.kvity.data.SubscriptionRepository
import app.kvity.data.SubscriptionState
import app.kvity.data.SubscriptionView
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleViewModel(
    private val people: PeopleRepository,
    debts: DebtRepository,
    subscriptions: SubscriptionRepository,
) : ViewModel() {
    val persons: StateFlow<List<PersonView>> = people.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val debts: StateFlow<List<DebtView>> = debts.observeDebts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val subs: StateFlow<List<SubscriptionView>> = subscriptions.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(personId: String, name: String, phone: String?, note: String?) {
        val p = persons.value.firstOrNull { it.person.id == personId }?.person ?: return
        viewModelScope.launch { people.update(p.copy(name = name.trim(), phone = phone, note = note)) }
    }

    /** Удаляет контрагента; [onResult] получает false, если удалить нельзя (есть долги, в том числе закрытые, или доли). */
    fun delete(personId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(people.deleteIfUnused(personId)) }
    }

    /** Отправляет в архив. Возвращает причину отказа или null, если архивация выполнена. */
    fun archive(personId: String, texts: AppTexts = AppTexts.default): String? {
        val p = persons.value.firstOrNull { it.person.id == personId } ?: return null
        val reason = ArchiveRules.archiveBlockReason(p.balance, p.activeShareCount, texts)
        if (reason == null) viewModelScope.launch { people.setArchived(personId, true) }
        return reason
    }

    fun unarchive(personId: String) {
        viewModelScope.launch { people.setArchived(personId, false) }
    }
}

class SubscriptionDetailViewModel(
    private val repo: SubscriptionRepository,
    private val id: String,
) : ViewModel() {
    val view: StateFlow<SubscriptionView?> = repo.observe(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete() { viewModelScope.launch { repo.delete(id) } }
    fun setState(state: SubscriptionState) { viewModelScope.launch { repo.setState(id, state) } }
    fun addShare(name: String, amountMinor: Long, from: LocalDate) {
        val s = view.value?.subscription ?: return
        viewModelScope.launch { repo.addShare(id, NewShare(name, amountMinor, s.currency, from)) }
    }
    fun deleteShare(shareId: String) { viewModelScope.launch { repo.deleteShare(shareId) } }
    fun markPaid(sh: ShareView, period: LocalDate) {
        viewModelScope.launch { repo.markPaid(sh.share.id, period, sh.share.amountMinor) }
    }
    fun markChargePaid(period: LocalDate) {
        val s = view.value?.subscription ?: return
        viewModelScope.launch { repo.markChargePaid(id, period, s.amountMinor) }
    }
    fun markChargeUnpaid(period: LocalDate) { viewModelScope.launch { repo.markChargeUnpaid(id, period) } }
    fun markUnpaid(sh: ShareView, period: LocalDate) { viewModelScope.launch { repo.markUnpaid(sh.share.id, period) } }
    fun update(input: SubscriptionInput) { viewModelScope.launch { repo.update(id, input) } }
}
