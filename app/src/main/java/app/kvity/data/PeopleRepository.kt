package app.kvity.data

import app.kvity.domain.SubscriptionCalc
import app.kvity.domain.Direction
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Контрагент с накопленным балансом по валютам: плюс «мне должны», минус «я должен». */
data class PersonView(
    val person: CounterpartyEntity,
    val balance: Map<String, Long>,
    val debtCount: Int,
    val shareCount: Int,
)

private class SubsData(
    val shares: List<SubscriptionShareEntity>,
    val subscriptions: List<SubscriptionEntity>,
    val pauses: List<SubscriptionPauseEntity>,
    val amounts: List<ShareAmountEntity>,
)

class PeopleRepository(private val db: KvityDatabase) {

    fun observe(today: () -> LocalDate = LocalDate::now): Flow<List<PersonView>> = combine(
        db.counterparties().observeAll(),
        db.debts().observeAll(),
        db.payments().observeAll(),
        combine(db.shares().observeAll(), db.subscriptions().observeAll(), db.pauses().observeAll(), db.shareAmounts().observeAll()) { sh, sb, pa, am -> SubsData(sh, sb, pa, am) },
        db.sharePayments().observeAll(),
    ) { people, debts, payments, sharesAndSubs, sharePayments ->
        val shares = sharesAndSubs.shares
        val subscriptions = sharesAndSubs.subscriptions
        val now = today()
        val paidByDebt = payments.groupBy { it.debtId }.mapValues { (_, v) -> v.sumOf { it.amountMinor } }
        people.map { p ->
            val balance = mutableMapOf<String, Long>()
            val myDebts = debts.filter { it.counterpartyId == p.id && !it.archived }
            for (d in myDebts) {
                val rest = (d.principalMinor - (paidByDebt[d.id] ?: 0)).coerceAtLeast(0)
                val signed = if (d.direction == Direction.OWED_TO_ME) rest else -rest
                if (rest > 0) balance.merge(d.currency, signed, Long::plus)
            }
            // неоплаченные доли в общих подписках — это тоже долг участника передо мной
            for (sh in shares.filter { it.counterpartyId == p.id && it.active }) {
                val sub = subscriptions.firstOrNull { it.id == sh.subscriptionId } ?: continue
                val pauses = sharesAndSubs.pauses.filter { it.subscriptionId == sub.id }.map { it.fromDate to it.toDate }
                val history = sharesAndSubs.amounts.filter { it.shareId == sh.id }.map { it.fromDate to it.amountMinor }
                val paid = sharePayments.filter { it.shareId == sh.id }.map { it.periodDate }.toSet()
                val unpaid = SubscriptionCalc.shareDues(sub.anchorDate, sub.periodicity, sub.interval, sh.activeFrom, sh.amountMinor, sh.currency, paid, now, pauses = pauses, amountHistory = history)
                    .filter { !it.paid }
                if (unpaid.isNotEmpty()) balance.merge(sh.currency, unpaid.sumOf { it.amountMinor }, Long::plus)
            }
            // подписки человека: он участник (возвращает долю) или он плательщик
            val subIds = shares.filter { it.counterpartyId == p.id && it.active }.map { it.subscriptionId }.toSet() +
                subscriptions.filter { it.payerCounterpartyId == p.id }.map { it.id }
            PersonView(p, balance.filterValues { it != 0L }, myDebts.size, subIds.size)
        }
    }

    suspend fun update(p: CounterpartyEntity) = db.counterparties().update(p)

    /** Удаляет контрагента, если у него нет долгов и долей. Возвращает false, если удалить нельзя. */
    suspend fun deleteIfUnused(id: String): Boolean {
        val hasDebts = db.debts().all().any { it.counterpartyId == id }
        val hasShares = db.shares().all().any { it.counterpartyId == id }
        val isPayer = db.subscriptions().all().any { it.payerCounterpartyId == id }
        if (hasDebts || hasShares || isPayer) return false
        db.counterparties().delete(id)
        return true
    }
}
