package app.kvity.data

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

class PeopleRepository(private val db: KvityDatabase) {

    fun observe(today: () -> LocalDate = LocalDate::now): Flow<List<PersonView>> = combine(
        db.counterparties().observeAll(),
        db.debts().observeAll(),
        db.payments().observeAll(),
        db.shares().observeAll(),
        db.sharePayments().observeAll(),
    ) { people, debts, payments, shares, sharePayments ->
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
            val myShares = shares.filter { it.counterpartyId == p.id && it.active }
            PersonView(p, balance.filterValues { it != 0L }, myDebts.size, myShares.size)
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
