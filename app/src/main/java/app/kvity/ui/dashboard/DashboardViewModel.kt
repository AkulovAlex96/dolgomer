package app.kvity.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kvity.data.DebtRepository
import app.kvity.data.DebtView
import app.kvity.domain.Bucket
import app.kvity.domain.Buckets
import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.domain.Due
import app.kvity.domain.InstallmentState
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Суммы по периодам: период → (код валюты → сумма в минимальных единицах). */
typealias BucketTotals = Map<Bucket, Map<String, Long>>

data class DashboardState(
    val loaded: Boolean = false,
    val owedToMe: BucketTotals = emptyMap(),
    val iOwe: BucketTotals = emptyMap(),
    /** Остаток по всем долгам: валюта → сумма. */
    val owedToMeTotal: Map<String, Long> = emptyMap(),
    val iOweTotal: Map<String, Long> = emptyMap(),
    val debts: List<DebtView> = emptyList(),
)

class DashboardViewModel(repository: DebtRepository) : ViewModel() {

    val state: StateFlow<DashboardState> = repository.observeDebts()
        .map { debts -> buildState(debts, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    companion object {
        fun buildState(debts: List<DebtView>, today: LocalDate): DashboardState {
            fun duesOf(direction: Direction): List<Due> = debts
                .filter { it.debt.direction == direction && it.summary.status != DebtStatus.CLOSED }
                .flatMap { v ->
                    v.summary.plan
                        .filter { it.state != InstallmentState.PAID }
                        .map { Due(it.dueDate, it.remainingMinor, v.debt.currency) }
                }

            fun totalOf(direction: Direction): Map<String, Long> = debts
                .filter { it.debt.direction == direction }
                .groupBy { it.debt.currency }
                .mapValues { (_, list) -> list.sumOf { it.summary.remainingMinor } }
                .filterValues { it > 0 }

            return DashboardState(
                loaded = true,
                owedToMe = Buckets.aggregate(duesOf(Direction.OWED_TO_ME), today),
                iOwe = Buckets.aggregate(duesOf(Direction.I_OWE), today),
                owedToMeTotal = totalOf(Direction.OWED_TO_ME),
                iOweTotal = totalOf(Direction.I_OWE),
                debts = debts.sortedWith(
                    compareBy<DebtView> { it.summary.status == DebtStatus.CLOSED }
                        .thenBy { it.summary.status != DebtStatus.OVERDUE }
                        .thenBy { it.summary.nextDue?.dueDate ?: LocalDate.MAX },
                ),
            )
        }
    }
}
