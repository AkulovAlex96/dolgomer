package app.kvity.data

import app.kvity.domain.ArchiveRules
import app.kvity.domain.DebtCalculator
import app.kvity.domain.Limits
import app.kvity.domain.DebtSummary
import app.kvity.domain.PaymentAmount
import app.kvity.domain.PlannedPayment
import app.kvity.domain.ScheduleRecalc
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
    /** Пропущенные плановые платежи (в [summary] не входят). */
    val skipped: List<PlannedPayment> = emptyList(),
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
    val interval: Int = 1,
    val dayOfMonth: Int? = null,
    val paymentCount: Int? = null,
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
            combine(db.payments().observeAll(), db.plannedPayments().observeAll()) { pay, plan -> pay to plan },
            dueSoonDays,
            ticker(today),
        ) { debts, people, (payments, planned), soon, now ->
            val byId = people.associateBy { it.id }
            val byDebt = payments.groupBy { it.debtId }
            val plannedByDebt = planned.groupBy { it.debtId }
            debts.map { toView(it, byId[it.counterpartyId], byDebt[it.id].orEmpty(), now, soon, plannedByDebt[it.id].orEmpty()) }
        }

    fun observeDebt(id: String, today: () -> LocalDate = LocalDate::now): Flow<DebtView?> =
        combine(
            db.debts().observe(id),
            db.counterparties().observeAll(),
            combine(db.payments().observeAll(), db.plannedPayments().observeAll()) { pay, plan -> pay to plan },
            dueSoonDays,
            ticker(today),
        ) { debt, people, (payments, planned), soon, now ->
            debt?.let { d ->
                toView(
                    d, people.firstOrNull { it.id == d.counterpartyId }, payments.filter { it.debtId == d.id }, now, soon,
                    planned.filter { it.debtId == d.id },
                )
            }
        }

    suspend fun addDebt(input: NewDebt): String {
        val now = System.currentTimeMillis()
        val name = input.counterpartyName.trim()
        val person = db.counterparties().findByNameRevive(name)
            ?: CounterpartyEntity(UUID.randomUUID().toString(), name, createdAt = now)
                .also { db.counterparties().insert(it) }

        val id = UUID.randomUUID().toString()
        val debt =
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
                interval = input.interval,
                dayOfMonth = input.dayOfMonth,
                paymentCount = input.paymentCount,
                installmentMinor = input.installmentMinor,
                firstDate = input.firstDate,
                note = input.note,
                createdAt = now,
                planMaterialized = true,
            )
        db.debts().insert(debt)
        db.plannedPayments().insertMany(materialize(debt))
        return id
    }

    suspend fun updateDebt(id: String, input: NewDebt) {
        val old = db.debts().observe(id).first() ?: return
        val name = input.counterpartyName.trim()
        val person = db.counterparties().findByNameRevive(name)
            ?: CounterpartyEntity(UUID.randomUUID().toString(), name, createdAt = System.currentTimeMillis())
                .also { db.counterparties().insert(it) }
        val updated = old.copy(
            counterpartyId = person.id, direction = input.direction, principalMinor = input.principalMinor,
            currency = input.currency, termType = input.termType, dueDate = input.dueDate,
            periodicity = input.periodicity, interval = input.interval, dayOfMonth = input.dayOfMonth,
            paymentCount = input.paymentCount, installmentMinor = input.installmentMinor,
            firstDate = input.firstDate, note = input.note,
        )
        // ручные правки графика сохраняются, пока не изменились сами условия возврата
        val sameTerms = old.planMaterialized && old.principalMinor == updated.principalMinor &&
            old.termType == updated.termType && old.dueDate == updated.dueDate && old.periodicity == updated.periodicity &&
            old.interval == updated.interval && old.dayOfMonth == updated.dayOfMonth && old.paymentCount == updated.paymentCount &&
            old.installmentMinor == updated.installmentMinor && old.firstDate == updated.firstDate
        if (sameTerms) {
            db.debts().update(updated)
        } else {
            db.debts().update(updated.copy(planMaterialized = true))
            db.plannedPayments().deleteForDebt(id)
            db.plannedPayments().insertMany(materialize(updated))
            // привязки к удалённым плановым платежам больше не действуют
            db.payments().unlinkDebt(id)
        }
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

    /**
     * Добавляет платёж. [plannedId] привязывает его к плановому платежу, [monthOnly] означает «известен только месяц»
     * (в [date] тогда хранится первое число месяца). Возвращает true, если стоит предложить пересчитать график.
     */
    suspend fun addPayment(
        debtId: String, date: LocalDate, amountMinor: Long, note: String? = null,
        plannedId: String? = null, monthOnly: Boolean = false, paymentMethod: String? = null,
    ): Boolean {
        db.payments().insert(
            PaymentEntity(
                UUID.randomUUID().toString(), debtId, if (monthOnly) date.withDayOfMonth(1) else date, amountMinor,
                ArchiveRules.cleanText(note, Limits.NOTE),
                System.currentTimeMillis(), plannedId, monthOnly, ArchiveRules.cleanText(paymentMethod, Limits.NAME),
            ),
        )
        return offerRecalc(debtId, date)
    }

    /**
     * Меняет внесённый платёж. Остаток и график пересчитываются сами (они считаются из платежей),
     * как и при добавлении. Возвращает true, если стоит предложить пересчитать график.
     */
    suspend fun updatePayment(
        id: String, date: LocalDate, amountMinor: Long, plannedId: String?, monthOnly: Boolean,
        paymentMethod: String?, note: String?,
    ): Boolean {
        val old = db.payments().get(id) ?: return false
        // привязка действует только к плановому платежу этого же долга
        val planned = plannedId?.takeIf { pid -> db.plannedPayments().get(pid)?.debtId == old.debtId }
        db.payments().update(
            old.copy(
                date = if (monthOnly) date.withDayOfMonth(1) else date, amountMinor = amountMinor, plannedId = planned,
                monthOnly = monthOnly, paymentMethod = ArchiveRules.cleanText(paymentMethod, Limits.NAME),
                note = ArchiveRules.cleanText(note, Limits.NOTE),
            ),
        )
        return offerRecalc(old.debtId, date)
    }

    private suspend fun offerRecalc(debtId: String, date: LocalDate): Boolean {
        val debt = db.debts().observe(debtId).first() ?: return false
        if (debt.termType != TermType.SCHEDULE) return false
        val planned = plannedOf(debt)
        val pays = db.payments().all().filter { it.debtId == debtId }
        return ScheduleRecalc.shouldOffer(planned, pays.map { PaymentAmount(it.amountMinor, it.plannedId) }, debt.principalMinor, date)
    }

    /** Старые долги (до версии 6) хранят график только в условиях: при первом открытии он записывается в таблицу. */
    suspend fun ensureMaterialized(debtId: String) {
        val debt = db.debts().observe(debtId).first() ?: return
        if (debt.planMaterialized) return
        db.plannedPayments().deleteForDebt(debtId)
        db.plannedPayments().insertMany(materialize(debt))
        db.debts().update(debt.copy(planMaterialized = true))
    }

    suspend fun updatePlanned(id: String, date: LocalDate, amountMinor: Long) {
        val p = db.plannedPayments().get(id) ?: return
        db.plannedPayments().update(p.copy(dueDate = date, amountMinor = amountMinor))
    }

    suspend fun setPlannedSkipped(id: String, skipped: Boolean) {
        val p = db.plannedPayments().get(id) ?: return
        db.plannedPayments().update(p.copy(skipped = skipped))
    }

    suspend fun deletePlanned(id: String) {
        db.payments().unlink(id)
        db.plannedPayments().delete(id)
    }

    /** Уменьшает суммы будущих плановых платежей так, чтобы остаток равномерно лёг на оставшиеся даты. */
    suspend fun recalcReducePayment(debtId: String, today: LocalDate = LocalDate.now()) {
        ensureMaterialized(debtId)
        val debt = db.debts().observe(debtId).first() ?: return
        val pays = db.payments().all().filter { it.debtId == debtId }
        val summary = DebtCalculator.summarize(
            debt.principalMinor, plannedOf(debt), pays.map { it.amountMinor }, today, plannedIds = pays.map { it.plannedId },
        )
        for (c in ScheduleRecalc.reducePayment(summary.plan, summary.remainingMinor, today)) {
            if (c.newAmountMinor == null) {
                deletePlanned(c.id)
            } else {
                db.plannedPayments().get(c.id)?.let { db.plannedPayments().update(it.copy(amountMinor = c.newAmountMinor)) }
            }
        }
    }

    private suspend fun plannedOf(debt: DebtEntity): List<PlannedPayment> =
        if (debt.planMaterialized) db.plannedPayments().forDebt(debt.id).map { it.toPlanned() } else debt.plannedPayments()

    private fun materialize(debt: DebtEntity): List<PlannedPaymentEntity> =
        debt.plannedPayments().map { PlannedPaymentEntity(UUID.randomUUID().toString(), debt.id, it.dueDate, it.amountMinor) }

    private fun PlannedPaymentEntity.toPlanned() = PlannedPayment(dueDate, amountMinor, id, skipped)

    suspend fun deletePayment(id: String) {
        attachments?.deleteOwnedBy(listOf(id))
        db.payments().delete(id)
    }

    suspend fun deleteDebt(id: String) {
        val paymentIds = db.payments().all().filter { it.debtId == id }.map { it.id }
        attachments?.deleteOwnedBy(listOf(id) + paymentIds)
        db.debts().delete(id)
    }

    private fun toView(
        debt: DebtEntity, person: CounterpartyEntity?, payments: List<PaymentEntity>, today: LocalDate, dueSoonDays: Int = 3,
        plannedRows: List<PlannedPaymentEntity> = emptyList(),
    ): DebtView {
        val planned = if (debt.planMaterialized) plannedRows.map { it.toPlanned() } else debt.plannedPayments()
        return DebtView(
            debt = debt,
            counterpartyName = person?.name.orEmpty(),
            counterpartyPhone = person?.phone,
            payments = payments.sortedByDescending { it.date },
            summary = DebtCalculator.summarize(
                principalMinor = debt.principalMinor,
                planned = planned,
                paymentsMinor = payments.map { it.amountMinor },
                today = today,
                dueSoonDays = dueSoonDays,
                plannedIds = payments.map { it.plannedId },
            ),
            skipped = planned.filter { it.skipped }.sortedBy { it.dueDate },
        )
    }
}
