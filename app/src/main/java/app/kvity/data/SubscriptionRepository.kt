package app.kvity.data

import app.kvity.domain.Periodicity
import app.kvity.domain.ShareDue
import app.kvity.domain.SubscriptionCalc
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Доля участника с рассчитанными периодами. */
data class ShareView(
    val share: SubscriptionShareEntity,
    val counterpartyName: String,
    val counterpartyPhone: String?,
    val dues: List<ShareDue>,
)

data class SubscriptionView(
    val subscription: SubscriptionEntity,
    val payerName: String?,
    val shares: List<ShareView>,
)

class NewShare(val counterpartyName: String, val amountMinor: Long, val currency: String, val activeFrom: LocalDate)

class SubscriptionInput(
    val name: String,
    val amountMinor: Long,
    val currency: String,
    val periodicity: Periodicity,
    val interval: Int = 1,
    val anchorDate: LocalDate,
    val payer: SubscriptionPayer,
    val payerName: String? = null,
    val paymentMethodNote: String? = null,
    val trialEndsOn: LocalDate? = null,
    val note: String? = null,
)

class SubscriptionRepository(private val db: KvityDatabase) {

    companion object {
        /** На сколько вперёд строятся списания и доли. */
        const val HORIZON_DAYS = 400L
    }

    fun observeAll(today: () -> LocalDate = LocalDate::now): Flow<List<SubscriptionView>> =
        combine(
            db.subscriptions().observeAll(),
            db.shares().observeAll(),
            db.sharePayments().observeAll(),
            db.counterparties().observeAll(),
        ) { subs, shares, payments, people ->
            val names = people.associateBy { it.id }
            val paidByShare = payments.groupBy { it.shareId }.mapValues { (_, v) -> v.map { it.periodDate }.toSet() }
            val horizon = today().plusDays(HORIZON_DAYS)
            subs.map { s -> buildView(s, shares.filter { it.subscriptionId == s.id }, paidByShare, names, horizon) }
                .sortedBy { it.subscription.name.lowercase() }
        }

    fun observe(id: String, today: () -> LocalDate = LocalDate::now): Flow<SubscriptionView?> =
        combine(observeAll(today)) { list -> list[0].firstOrNull { it.subscription.id == id } }

    private fun buildView(
        s: SubscriptionEntity,
        shares: List<SubscriptionShareEntity>,
        paidByShare: Map<String, Set<LocalDate>>,
        names: Map<String, CounterpartyEntity>,
        horizon: LocalDate,
    ): SubscriptionView {
        val end = if (s.state == SubscriptionState.ACTIVE) horizon else LocalDate.now()
        val views = shares.map { sh ->
            val dues = if (!sh.active) emptyList() else SubscriptionCalc.shareDues(
                s.anchorDate, s.periodicity, s.interval, sh.activeFrom, sh.amountMinor, sh.currency,
                paidByShare[sh.id].orEmpty(), end,
            )
            ShareView(sh, names[sh.counterpartyId]?.name.orEmpty(), names[sh.counterpartyId]?.phone, dues)
        }
        return SubscriptionView(s, s.payerCounterpartyId?.let { names[it]?.name }, views)
    }

    private suspend fun person(name: String): CounterpartyEntity {
        val trimmed = name.trim()
        return db.counterparties().findByName(trimmed)
            ?: CounterpartyEntity(UUID.randomUUID().toString(), trimmed, createdAt = System.currentTimeMillis())
                .also { db.counterparties().insert(it) }
    }

    suspend fun add(input: SubscriptionInput): String {
        val id = UUID.randomUUID().toString()
        db.subscriptions().insert(toEntity(id, input, System.currentTimeMillis()))
        return id
    }

    suspend fun update(id: String, input: SubscriptionInput) {
        val old = db.subscriptions().all().first { it.id == id }
        db.subscriptions().update(toEntity(id, input, old.createdAt).copy(state = old.state))
    }

    private suspend fun toEntity(id: String, i: SubscriptionInput, createdAt: Long) = SubscriptionEntity(
        id = id, name = i.name.trim(), amountMinor = i.amountMinor, currency = i.currency,
        periodicity = i.periodicity, interval = i.interval, anchorDate = i.anchorDate, payer = i.payer,
        payerCounterpartyId = if (i.payer == SubscriptionPayer.OTHER && !i.payerName.isNullOrBlank()) person(i.payerName).id else null,
        paymentMethodNote = i.paymentMethodNote, trialEndsOn = i.trialEndsOn, note = i.note, createdAt = createdAt,
    )

    suspend fun setState(id: String, state: SubscriptionState) {
        val old = db.subscriptions().all().first { it.id == id }
        db.subscriptions().update(old.copy(state = state))
    }

    suspend fun delete(id: String) = db.subscriptions().delete(id)

    suspend fun addShare(subscriptionId: String, input: NewShare): String {
        val p = person(input.counterpartyName)
        val id = UUID.randomUUID().toString()
        db.shares().insert(
            SubscriptionShareEntity(id, subscriptionId, p.id, input.amountMinor, input.currency, input.activeFrom),
        )
        return id
    }

    suspend fun updateShare(share: SubscriptionShareEntity, amountMinor: Long, currency: String, activeFrom: LocalDate) {
        db.shares().update(share.copy(amountMinor = amountMinor, currency = currency, activeFrom = activeFrom))
    }

    suspend fun deleteShare(id: String) = db.shares().delete(id)

    suspend fun markPaid(shareId: String, period: LocalDate, amountMinor: Long, paidDate: LocalDate = LocalDate.now()) {
        db.sharePayments().deleteFor(shareId, period)
        db.sharePayments().insert(SharePaymentEntity(UUID.randomUUID().toString(), shareId, period, paidDate, amountMinor))
    }

    suspend fun markUnpaid(shareId: String, period: LocalDate) = db.sharePayments().deleteFor(shareId, period)
}
