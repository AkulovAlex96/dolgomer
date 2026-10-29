package app.kvity.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kvity.data.CalendarEvent
import app.kvity.data.DebtRepository
import app.kvity.data.DebtView
import app.kvity.data.EventKind
import app.kvity.data.Events
import app.kvity.data.MoneyFlow
import app.kvity.data.SettingsStore
import app.kvity.data.SubscriptionPayer
import app.kvity.data.SubscriptionRepository
import app.kvity.data.SubscriptionState
import app.kvity.data.SubscriptionView
import app.kvity.domain.Bucket
import app.kvity.domain.Buckets
import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.domain.Due
import app.kvity.domain.InstallmentState
import app.kvity.domain.SubscriptionCalc
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    // подписки
    val subsIncoming: BucketTotals = emptyMap(),
    val subsOutgoing: BucketTotals = emptyMap(),
    val subsIncomingMonthly: Map<String, Long> = emptyMap(),
    val subsOutgoingMonthly: Map<String, Long> = emptyMap(),
    val subs: List<SubscriptionView> = emptyList(),
    /** Неоплаченные доли участников с прошедшим сроком. */
    val overdueShares: List<CalendarEvent> = emptyList(),
)

class DashboardViewModel(
    debts: DebtRepository,
    private val subscriptions: SubscriptionRepository,
    settings: SettingsStore,
) : ViewModel() {

    fun markSharePaid(e: CalendarEvent) {
        val shareId = e.shareId ?: return
        val period = e.periodDate ?: return
        viewModelScope.launch { subscriptions.markPaid(shareId, period, e.amountMinor) }
    }

    val state: StateFlow<DashboardState> = combine(
        debts.observeDebts(), subscriptions.observeAll(), settings.settings,
    ) { d, s, set -> buildState(d, s, LocalDate.now(), set.firstDayOfWeek) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    companion object {
        fun buildState(
            debts: List<DebtView>,
            subs: List<SubscriptionView>,
            today: LocalDate,
            firstDay: DayOfWeek = DayOfWeek.MONDAY,
        ): DashboardState {
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

            val events = Events.subscriptionEvents(subs, today).filter { it.kind == EventKind.SUBSCRIPTION }
            // Прошедшие собственные списания уже произошли: просрочкой они не являются, отметить их нельзя
            fun subDues(flow: MoneyFlow) = events
                .filter { it.flow == flow && (flow == MoneyFlow.IN || !it.date.isBefore(today)) }
                .map { Due(it.date, it.amountMinor, it.currency) }

            val monthlyIn = mutableMapOf<String, Long>()
            val monthlyOut = mutableMapOf<String, Long>()
            for (v in subs.filter { it.subscription.state == SubscriptionState.ACTIVE }) {
                val s = v.subscription
                monthlyOut.merge(s.currency, SubscriptionCalc.monthlyMinor(s.amountMinor, s.periodicity, s.interval), Long::plus)
                if (s.payer == SubscriptionPayer.ME) {
                    for (sh in v.shares.filter { it.share.active }) {
                        monthlyIn.merge(sh.share.currency, SubscriptionCalc.monthlyMinor(sh.share.amountMinor, s.periodicity, s.interval), Long::plus)
                    }
                }
            }

            return DashboardState(
                loaded = true,
                owedToMe = Buckets.aggregate(duesOf(Direction.OWED_TO_ME), today, firstDay),
                iOwe = Buckets.aggregate(duesOf(Direction.I_OWE), today, firstDay),
                owedToMeTotal = totalOf(Direction.OWED_TO_ME),
                iOweTotal = totalOf(Direction.I_OWE),
                debts = debts.sortedWith(
                    compareBy<DebtView> { it.summary.status == DebtStatus.CLOSED }
                        .thenBy { it.summary.status != DebtStatus.OVERDUE }
                        .thenBy { it.summary.nextDue?.dueDate ?: LocalDate.MAX },
                ),
                subsIncoming = Buckets.aggregate(subDues(MoneyFlow.IN), today, firstDay),
                subsOutgoing = Buckets.aggregate(subDues(MoneyFlow.OUT), today, firstDay),
                subsIncomingMonthly = monthlyIn,
                subsOutgoingMonthly = monthlyOut,
                subs = subs,
                overdueShares = events.filter { it.flow == MoneyFlow.IN && it.overdue }.sortedBy { it.date },
            )
        }
    }
}
