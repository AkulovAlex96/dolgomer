package app.kvity.data

import app.kvity.domain.DebtCalculator
import app.kvity.domain.DebtSummary
import app.kvity.domain.Direction
import app.kvity.domain.TermType
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Долг вместе с именем контрагента, платежами и рассчитанной сводкой. */
data class DebtView(
    val debt: DebtEntity,
    val counterpartyName: String,
    val counterpartyPhone: String?,
    val payments: List<PaymentEntity>,
    val summary: DebtSummary,
)

class NewDebt(
    val counterpartyName: String,
    val direction: Direction,
    val principalMinor: Long,
    val currency: String,
    val startDate: LocalDate,
    val termType: TermType,
    val dueDate: LocalDate? = null,
    val periodicity: app.kvity.domain.Periodicity? = null,
    val installmentMinor: Long? = null,
    val firstDate: LocalDate? = null,
    val note: String? = null,
)

data class ImportSummary(val added: Int, val skippedDuplicates: Int)

/** [dueSoonDays] — за сколько дней до срока долг получает статус «скоро срок» (настройка пользователя). */
class DebtRepository(
    private val db: KvityDatabase,
    private val attachments: AttachmentRepository? = null,
    private val dueSoonDays: Flow<Int> = kotlinx.coroutines.flow.flowOf(3),
) {

    fun observeDebts(today: () -> LocalDate = LocalDate::now, includeArchived: Boolean = false): Flow<List<DebtView>> =
        combine(
            if (includeArchived) db.debts().observeAll() else db.debts().observeActive(),
            db.counterparties().observeAll(),
            db.payments().observeAll(),
            dueSoonDays,
            ticker(today),
        ) { debts, people, payments, soon, now ->
            val byId = people.associateBy { it.id }
            val byDebt = payments.groupBy { it.debtId }
            debts.map { toView(it, byId[it.counterpartyId], byDebt[it.id].orEmpty(), now, soon) }
        }

    fun observeDebt(id: String, today: () -> LocalDate = LocalDate::now): Flow<DebtView?> =
        combine(
            db.debts().observe(id),
            db.counterparties().observeAll(),
            db.payments().observeAll(),
            dueSoonDays,
            ticker(today),
        ) { debt, people, payments, soon, now ->
            debt?.let { d ->
                toView(d, people.firstOrNull { it.id == d.counterpartyId }, payments.filter { it.debtId == d.id }, now, soon)
            }
        }

    suspend fun addDebt(input: NewDebt): String {
        val now = System.currentTimeMillis()
        val name = input.counterpartyName.trim()
        val person = db.counterparties().findByName(name)
            ?: CounterpartyEntity(UUID.randomUUID().toString(), name, createdAt = now)
                .also { db.counterparties().insert(it) }

        val id = UUID.randomUUID().toString()
        db.debts().insert(
            DebtEntity(
                id = id,
                counterpartyId = person.id,
                direction = input.direction,
                principalMinor = input.principalMinor,
                currency = input.currency,
                startDate = input.startDate,
                termType = input.termType,
                dueDate = input.dueDate,
                periodicity = input.periodicity,
                installmentMinor = input.installmentMinor,
                firstDate = input.firstDate,
                note = input.note,
                createdAt = now,
            ),
        )
        return id
    }

    suspend fun updateDebt(id: String, input: NewDebt) {
        val old = db.debts().observe(id).first() ?: return
        val name = input.counterpartyName.trim()
        val person = db.counterparties().findByName(name)
            ?: CounterpartyEntity(UUID.randomUUID().toString(), name, createdAt = System.currentTimeMillis())
                .also { db.counterparties().insert(it) }
        db.debts().update(
            old.copy(
                counterpartyId = person.id, direction = input.direction, principalMinor = input.principalMinor,
                currency = input.currency, termType = input.termType, dueDate = input.dueDate,
                periodicity = input.periodicity, installmentMinor = input.installmentMinor,
                firstDate = input.firstDate, note = input.note,
            ),
        )
        if (old.counterpartyId != person.id) {
            val used = db.debts().all().any { it.counterpartyId == old.counterpartyId } ||
                db.shares().all().any { it.counterpartyId == old.counterpartyId } ||
                db.subscriptions().all().any { it.payerCounterpartyId == old.counterpartyId }
            if (!used) db.counterparties().delete(old.counterpartyId)
        }
    }

    /** Добавляет долги из импорта. Полные дубликаты (тот же человек, направление, валюта, сумма и дата выдачи) пропускаются. */
    suspend fun importDebts(rows: List<app.kvity.exchange.ImportRow>, namesWithPayments: Set<String> = emptySet()): ImportSummary {
        val people = db.counterparties().all().associateBy { it.id }
        val existing = db.debts().all().map {
            listOf(people[it.counterpartyId]?.name?.lowercase().orEmpty(), it.direction, it.currency, it.principalMinor, it.startDate)
        }.toMutableSet()
        val existingNoDate = existing.map { it.dropLast(1) }.toMutableSet()
        var added = 0
        var skipped = 0
        for (r in rows) {
            val key = listOf(r.counterparty.trim().lowercase(), r.direction, r.currency, r.principalMinor, r.startDate)
            val duplicate = if (r.startGiven) !existing.add(key) else !existingNoDate.add(key.dropLast(1)) || key in existing
            if (r.startGiven) existingNoDate.add(key.dropLast(1)) else existing.add(key)
            if (duplicate) { skipped++; continue }
            val id = addDebt(
                NewDebt(
                    counterpartyName = r.counterparty, direction = r.direction, principalMinor = r.principalMinor,
                    currency = r.currency, startDate = r.startDate,
                    termType = if (r.dueDate != null) TermType.ONE_TIME else TermType.NONE,
                    dueDate = r.dueDate, note = r.note,
                ),
            )
            // если в файле есть отдельные платежи этого человека, «Выплачено» — их сумма: не создаём платёж второй раз
            if (r.paidMinor > 0 && r.counterparty.trim().lowercase() !in namesWithPayments) addPayment(id, LocalDate.now(), r.paidMinor, "Импорт")
            added++
        }
        return ImportSummary(added, skipped)
    }

    suspend fun setArchived(id: String, archived: Boolean) = db.debts().setArchived(id, archived)

    suspend fun addPayment(debtId: String, date: LocalDate, amountMinor: Long, note: String? = null) {
        db.payments().insert(
            PaymentEntity(UUID.randomUUID().toString(), debtId, date, amountMinor, note, System.currentTimeMillis()),
        )
    }

    suspend fun deletePayment(id: String) {
        attachments?.deleteOwnedBy(listOf(id))
        db.payments().delete(id)
    }

    suspend fun deleteDebt(id: String) {
        val paymentIds = db.payments().all().filter { it.debtId == id }.map { it.id }
        attachments?.deleteOwnedBy(listOf(id) + paymentIds)
        db.debts().delete(id)
    }

    private fun toView(debt: DebtEntity, person: CounterpartyEntity?, payments: List<PaymentEntity>, today: LocalDate, dueSoonDays: Int = 3) =
        DebtView(
            debt = debt,
            counterpartyName = person?.name.orEmpty(),
            counterpartyPhone = person?.phone,
            payments = payments.sortedByDescending { it.date },
            summary = DebtCalculator.summarize(
                principalMinor = debt.principalMinor,
                planned = debt.plannedPayments(),
                paymentsMinor = payments.map { it.amountMinor },
                today = today,
                dueSoonDays = dueSoonDays,
            ),
        )
}
