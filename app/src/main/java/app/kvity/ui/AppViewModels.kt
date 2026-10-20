package app.kvity.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun delete(personId: String) {
        viewModelScope.launch { people.deleteIfUnused(personId) }
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
    fun markUnpaid(sh: ShareView, period: LocalDate) { viewModelScope.launch { repo.markUnpaid(sh.share.id, period) } }
    fun update(input: SubscriptionInput) { viewModelScope.launch { repo.update(id, input) } }
}
