package app.kvity.data

import app.kvity.exchange.ImportBatch
import app.kvity.exchange.ImportSubState
import java.time.LocalDate
import java.util.UUID

data class ImportReport(
    val debts: Int = 0,
    val payments: Int = 0,
    val subscriptions: Int = 0,
    val shares: Int = 0,
    val people: Int = 0,
    val skippedDuplicates: Int = 0,
    val problems: List<String> = emptyList(),
) {
    val added: Int get() = debts + payments + subscriptions + shares + people
}

/** Записывает разобранный файл в базу. Порядок: люди, долги, платежи, подписки, доли. Дубликаты пропускаются. */
class ImportApplier(
    private val db: KvityDatabase,
    private val debts: DebtRepository,
    private val subscriptions: SubscriptionRepository,
) {

    suspend fun apply(batch: ImportBatch): ImportReport {
        var skipped = 0
        val problems = mutableListOf<String>()

        // Контрагенты
        var people = 0
        for (p in batch.people) {
            val existing = db.counterparties().findByName(p.name.trim())
            if (existing == null) {
                db.counterparties().insert(CounterpartyEntity(UUID.randomUUID().toString(), p.name.trim(), p.phone, p.note, System.currentTimeMillis()))
                people++
            } else if ((existing.phone == null && p.phone != null) || (existing.note == null && p.note != null)) {
                db.counterparties().update(existing.copy(phone = existing.phone ?: p.phone, note = existing.note ?: p.note))
                people++
            } else skipped++
        }

        // Долги
        val debtSummary = debts.importDebts(batch.debts)
        skipped += debtSummary.skippedDuplicates

        // Платежи: привязываются к долгу того же человека (и направления/валюты, если они указаны в файле)
        var payments = 0
        val namesById = db.counterparties().all().associate { it.id to it.name.lowercase() }
        val allDebts = db.debts().all().filter { !it.archived }
        val seen = db.payments().all().map { Triple(it.debtId, it.date, it.amountMinor) }.toMutableSet()
        for (p in batch.payments) {
            val candidates = allDebts.filter {
                namesById[it.counterpartyId] == p.counterparty.trim().lowercase() &&
                    (p.direction == null || it.direction == p.direction) && (p.currency == null || it.currency == p.currency)
            }
            val target = candidates.maxByOrNull { it.startDate }
            if (target == null) { problems += "Платёж, строка ${p.line}: нет долга для «${p.counterparty}»"; continue }
            if (!seen.add(Triple(target.id, p.date, p.amountMinor))) { skipped++; continue }
            debts.addPayment(target.id, p.date, p.amountMinor, p.note)
            payments++
        }

        // Подписки
        var subs = 0
        val existingSubs = db.subscriptions().all()
        val payerNames = db.counterparties().all().associate { it.id to it.name.lowercase() }
        val subKeys = existingSubs.map { subKey(it.name, it.currency, it.amountMinor, it.anchorDate, it.payerCounterpartyId?.let(payerNames::get)) }.toMutableSet()
        for (s in batch.subscriptions) {
            if (!subKeys.add(subKey(s.name, s.currency, s.amountMinor, s.anchor, s.payerName?.lowercase()))) { skipped++; continue }
            val id = subscriptions.add(
                SubscriptionInput(
                    name = s.name, amountMinor = s.amountMinor, currency = s.currency, periodicity = s.periodicity, interval = s.interval,
                    anchorDate = s.anchor, payer = if (s.payerName == null) SubscriptionPayer.ME else SubscriptionPayer.OTHER,
                    payerName = s.payerName, paymentMethodNote = s.method, note = s.note,
                ),
            )
            when (s.state) {
                ImportSubState.PAUSED -> subscriptions.setState(id, SubscriptionState.PAUSED)
                ImportSubState.CANCELLED -> subscriptions.setState(id, SubscriptionState.CANCELLED)
                ImportSubState.ACTIVE -> Unit
            }
            subs++
        }

        // Доли участников: по одной записи на пару «подписка + участник», оплаченные периоды отмечаются
        var shares = 0
        val allSubs = db.subscriptions().all().sortedBy { it.createdAt }
        val allShares = db.shares().all()
        val allPeople = db.counterparties().all()
        val paidMarks = db.sharePayments().all().map { it.shareId to it.periodDate }.toMutableSet()
        val groups = batch.shares.groupBy { it.subscription.trim().lowercase() to it.participant.trim().lowercase() }
        for ((key, rows) in groups) {
            val sub = allSubs.firstOrNull { it.name.trim().lowercase() == key.first }
            if (sub == null) { problems += "Доля, строка ${rows.first().line}: нет подписки «${rows.first().subscription}»"; continue }
            val person = allPeople.firstOrNull { it.name.trim().lowercase() == key.second }
            val existing = person?.let { p -> allShares.firstOrNull { it.subscriptionId == sub.id && it.counterpartyId == p.id } }
            val shareId = existing?.id ?: run {
                val last = rows.maxByOrNull { it.period }!!
                shares++
                subscriptions.addShare(sub.id, NewShare(rows.first().participant, last.amountMinor, last.currency, rows.minOf { it.period }))
            }
            if (existing != null) skipped++
            for (r in rows.filter { it.paid }) {
                if (paidMarks.add(shareId to r.period)) subscriptions.markPaid(shareId, r.period, r.amountMinor, LocalDate.now())
            }
        }

        return ImportReport(debtSummary.added, payments, subs, shares, people, skipped, problems)
    }

    private fun subKey(name: String, currency: String, amount: Long, anchor: LocalDate, payer: String?) =
        listOf(name.trim().lowercase(), currency, amount, anchor, payer.orEmpty())
}
