package app.kvity.data

import androidx.room.withTransaction
import app.kvity.domain.Periodicity
import app.kvity.domain.ShareDue
import app.kvity.domain.SubscriptionCalc
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

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
    /** Периоды, за которые моё списание отмечено оплаченным. */
    val paidCharges: Set<LocalDate> = emptySet(),
    /** Интервалы пауз и отмены: [from, to), to = null — продолжается. */
    val pauses: List<Pair<LocalDate, LocalDate?>> = emptyList(),
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
    val category: String? = null,
)

class SubscriptionRepository(private val db: KvityDatabase, private val attachments: AttachmentRepository? = null) {

    companion object {
        /** На сколько вперёд строятся списания и доли. */
        const val HORIZON_DAYS = 400L
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeAll(today: () -> LocalDate = LocalDate::now): Flow<List<SubscriptionView>> =
        ticker(today).flatMapLatest { now -> build(now) }

    private class Core(
        val subs: List<SubscriptionEntity>,
        val shares: List<SubscriptionShareEntity>,
        val payments: List<SharePaymentEntity>,
        val people: List<CounterpartyEntity>,
        val charges: List<ChargePaymentEntity>,
    )

    private fun build(now: LocalDate): Flow<List<SubscriptionView>> =
        combine(
            combine(
                db.subscriptions().observeAll(),
                db.shares().observeAll(),
                db.sharePayments().observeAll(),
                db.counterparties().observeAll(),
                db.chargePayments().observeAll(),
            ) { subs, shares, payments, people, charges -> Core(subs, shares, payments, people, charges) },
            db.pauses().observeAll(),
            db.shareAmounts().observeAll(),
        ) { core, pauses, amounts ->
            val paidCharges = core.charges.groupBy { it.subscriptionId }.mapValues { (_, v) -> v.map { it.periodDate }.toSet() }
            val names = core.people.associateBy { it.id }
            val paidByShare = core.payments.groupBy { it.shareId }.mapValues { (_, v) -> v.associate { it.periodDate to it.amountMinor } }
            val pausesBySub = pauses.groupBy { it.subscriptionId }
            val amountsByShare = amounts.groupBy { it.shareId }
            val horizon = now.plusDays(HORIZON_DAYS)
            core.subs.map { s ->
                buildView(
                    s, core.shares.filter { it.subscriptionId == s.id }, paidByShare, names, horizon, now,
                    paidCharges[s.id].orEmpty(), pausesBySub[s.id].orEmpty(), amountsByShare,
                )
            }.sortedBy { it.subscription.name.lowercase() }
        }

    fun observe(id: String, today: () -> LocalDate = LocalDate::now): Flow<SubscriptionView?> =
        combine(observeAll(today)) { list -> list[0].firstOrNull { it.subscription.id == id } }

    private fun buildView(
        s: SubscriptionEntity,
        shares: List<SubscriptionShareEntity>,
        paidByShare: Map<String, Map<LocalDate, Long>>,
        names: Map<String, CounterpartyEntity>,
        horizon: LocalDate,
        now: LocalDate,
        paidCharges: Set<LocalDate>,
        pauseRows: List<SubscriptionPauseEntity>,
        amountsByShare: Map<String, List<ShareAmountEntity>>,
    ): SubscriptionView {
        val pauses = pauseRows.map { it.fromDate to it.toDate }
        // подписка со старым состоянием без записанной паузы (данные до появления истории): доли идут до сегодняшнего дня
        val end = if (s.state == SubscriptionState.ACTIVE || pauses.any { it.second == null }) horizon else now
        val views = shares.map { sh ->
            val dues = if (!sh.active) emptyList() else SubscriptionCalc.shareDues(
                s.anchorDate, s.periodicity, s.interval, sh.activeFrom, sh.amountMinor, sh.currency,
                paidByShare[sh.id].orEmpty().keys, end, paidByShare[sh.id].orEmpty(),
                pauses, amountsByShare[sh.id].orEmpty().map { it.fromDate to it.amountMinor },
            )
            ShareView(sh, names[sh.counterpartyId]?.name.orEmpty(), names[sh.counterpartyId]?.phone, dues)
        }
        return SubscriptionView(s, s.payerCounterpartyId?.let { names[it]?.name }, views, paidCharges, pauses)
    }

    private suspend fun person(name: String): CounterpartyEntity {
        val trimmed = name.trim()
        return db.counterparties().findByNameRevive(trimmed)
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
        val updated = toEntity(id, input, old.createdAt).copy(state = old.state, notifyMode = old.notifyMode, notifyDays = old.notifyDays)
        db.subscriptions().update(updated)
        if (old.anchorDate != updated.anchorDate || old.periodicity != updated.periodicity || old.interval != updated.interval) remapMarks(old, updated)
    }

    /** Отметки «оплачено» привязаны к датам периодов; при смене расписания переносим их на период с тем же номером. */
    private suspend fun remapMarks(old: SubscriptionEntity, new: SubscriptionEntity) {
        fun newDate(d: LocalDate, olds: List<LocalDate>): LocalDate? {
            val idx = olds.indexOf(d).takeIf { it >= 0 } ?: return null
            return SubscriptionCalc.occurrences(new.anchorDate, new.periodicity, new.interval, new.anchorDate, new.anchorDate.plusYears(100))
                .getOrNull(idx)
        }
        val shareIds = db.shares().all().filter { it.subscriptionId == old.id }.map { it.id }.toSet()
        val sharePays = db.sharePayments().all().filter { it.shareId in shareIds }
        val chargePays = db.chargePayments().all().filter { it.subscriptionId == old.id }
        val last = (sharePays.map { it.periodDate } + chargePays.map { it.periodDate }).maxOrNull() ?: return
        val olds = SubscriptionCalc.occurrences(old.anchorDate, old.periodicity, old.interval, old.anchorDate, last)
        for (p in sharePays) {
            val nd = newDate(p.periodDate, olds) ?: continue
            if (nd == p.periodDate) continue
            db.sharePayments().delete(p.id)
        }
        for (p in chargePays) {
            val nd = newDate(p.periodDate, olds) ?: continue
            if (nd != p.periodDate) db.chargePayments().deleteFor(p.subscriptionId, p.periodDate)
        }
        db.sharePayments().insertAll(sharePays.mapNotNull { p ->
            newDate(p.periodDate, olds)?.takeIf { it != p.periodDate }?.let { p.copy(periodDate = it) }
        })
        db.chargePayments().insertAll(chargePays.mapNotNull { p ->
            newDate(p.periodDate, olds)?.takeIf { it != p.periodDate }?.let { p.copy(periodDate = it) }
        })
    }

    private suspend fun toEntity(id: String, i: SubscriptionInput, createdAt: Long) = SubscriptionEntity(
        id = id, name = i.name.trim(), amountMinor = i.amountMinor, currency = i.currency,
        periodicity = i.periodicity, interval = i.interval, anchorDate = i.anchorDate, payer = i.payer,
        payerCounterpartyId = if (i.payer == SubscriptionPayer.OTHER && !i.payerName.isNullOrBlank()) person(i.payerName).id else null,
        paymentMethodNote = i.paymentMethodNote, trialEndsOn = i.trialEndsOn, note = i.note, category = i.category, createdAt = createdAt,
    )

    suspend fun setState(id: String, state: SubscriptionState) = db.withTransaction {
        val old = db.subscriptions().all().first { it.id == id }
        if (old.state == state) return@withTransaction
        val today = LocalDate.now()
        val open = db.pauses().all().filter { it.subscriptionId == id && it.toDate == null }
        if (state == SubscriptionState.ACTIVE) {
            // пауза, начатая и закрытая в один день, пустая: не оставляем после неё записи
            open.forEach { if (it.fromDate == today) db.pauses().delete(it.id) else db.pauses().update(it.copy(toDate = today)) }
        } else if (open.isEmpty()) {
            db.pauses().insert(SubscriptionPauseEntity(UUID.randomUUID().toString(), id, today, null))
        }
        db.subscriptions().update(old.copy(state = state))
    }

    /**
     * Подписки, поставленные на паузу до появления истории пауз, не имеют записи интервала и продолжали бы начислять доли.
     * Для них открывается пауза с сегодняшнего дня (точная дата остановки неизвестна). Вызывается при запуске приложения.
     */
    suspend fun repairLegacyPauses() = db.withTransaction {
        val open = db.pauses().all().filter { it.toDate == null }.map { it.subscriptionId }.toSet()
        val today = LocalDate.now()
        db.subscriptions().all().filter { it.state != SubscriptionState.ACTIVE && it.id !in open }.forEach {
            db.pauses().insert(SubscriptionPauseEntity(UUID.randomUUID().toString(), it.id, today, null))
        }
    }

    suspend fun delete(id: String) {
        attachments?.deleteOwnedBy(listOf(id))
        db.subscriptions().delete(id)
    }

    suspend fun addShare(subscriptionId: String, input: NewShare): String {
        val p = person(input.counterpartyName)
        val id = UUID.randomUUID().toString()
        db.shares().insert(
            SubscriptionShareEntity(id, subscriptionId, p.id, input.amountMinor, input.currency, input.activeFrom),
        )
        return id
    }

    suspend fun updateShare(share: SubscriptionShareEntity, amountMinor: Long, currency: String, activeFrom: LocalDate) = db.withTransaction {
        if (amountMinor != share.amountMinor) {
            // новая сумма действует с сегодняшнего дня; прошлые периоды остаются со старой
            val history = db.shareAmounts().all().filter { it.shareId == share.id }
            if (history.isEmpty()) db.shareAmounts().insert(ShareAmountEntity(UUID.randomUUID().toString(), share.id, share.activeFrom, share.amountMinor))
            val today = LocalDate.now()
            db.shareAmounts().deleteFor(share.id, today)
            db.shareAmounts().insert(ShareAmountEntity(UUID.randomUUID().toString(), share.id, today, amountMinor))
        }
        db.shares().update(share.copy(amountMinor = amountMinor, currency = currency, activeFrom = activeFrom))
    }

    suspend fun deleteShare(id: String) = db.shares().delete(id)

    suspend fun markPaid(shareId: String, period: LocalDate, amountMinor: Long, paidDate: LocalDate = LocalDate.now()) {
        db.withTransaction {
            db.sharePayments().deleteFor(shareId, period)
            db.sharePayments().insert(SharePaymentEntity(UUID.randomUUID().toString(), shareId, period, paidDate, amountMinor))
        }
    }

    suspend fun markChargePaid(subscriptionId: String, period: LocalDate, amountMinor: Long, paidDate: LocalDate = LocalDate.now()) {
        db.withTransaction {
            db.chargePayments().deleteFor(subscriptionId, period)
            db.chargePayments().insert(ChargePaymentEntity(UUID.randomUUID().toString(), subscriptionId, period, paidDate, amountMinor))
        }
    }

    /** Свои уведомления подписки: режим и (для CUSTOM) дни напоминания, например «3,1». */
    suspend fun setNotify(id: String, mode: NotifyMode, days: String?) {
        val old = db.subscriptions().all().firstOrNull { it.id == id } ?: return
        db.subscriptions().update(old.copy(notifyMode = mode, notifyDays = if (mode == NotifyMode.CUSTOM) days else null))
    }

    suspend fun markChargeUnpaid(subscriptionId: String, period: LocalDate) = db.chargePayments().deleteFor(subscriptionId, period)

    suspend fun markUnpaid(shareId: String, period: LocalDate) = db.sharePayments().deleteFor(shareId, period)
}
