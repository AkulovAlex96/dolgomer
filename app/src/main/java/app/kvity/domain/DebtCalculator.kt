package app.kvity.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Фактический платёж для распределения: [plannedId] это привязка к плановому платежу. */
data class PaymentAmount(val amountMinor: Long, val plannedId: String? = null)

enum class InstallmentState { PAID, PARTIAL, PENDING }

data class PlanItem(
    val dueDate: LocalDate,
    val amountMinor: Long,
    val paidMinor: Long,
    val id: String? = null,
) {
    val remainingMinor: Long get() = amountMinor - paidMinor
    val state: InstallmentState
        get() = when {
            paidMinor >= amountMinor -> InstallmentState.PAID
            paidMinor > 0 -> InstallmentState.PARTIAL
            else -> InstallmentState.PENDING
        }
}

enum class DebtStatus { ACTIVE, DUE_SOON, OVERDUE, CLOSED }

data class DebtSummary(
    val principalMinor: Long,
    val paidMinor: Long,
    val remainingMinor: Long,
    val status: DebtStatus,
    val overdueMinor: Long,
    val overdueDays: Long,
    val plan: List<PlanItem>,
    val nextDue: PlanItem?,
)

object DebtCalculator {

    /**
     * Платежи закрывают плановые платежи по порядку дат: самый старый закрывается первым.
     * Переплата переходит на следующие платежи. Пропущенные плановые платежи не учитываются.
     */
    fun allocate(planned: List<PlannedPayment>, totalPaidMinor: Long): List<PlanItem> =
        allocate(planned, listOf(PaymentAmount(totalPaidMinor)))

    /**
     * То же, но с привязкой: платёж с [PaymentAmount.plannedId] сначала закрывает свой плановый платёж,
     * то, что не поместилось (или платёж без привязки), распределяется от самых старых к новым.
     */
    fun allocate(planned: List<PlannedPayment>, payments: List<PaymentAmount>): List<PlanItem> {
        val items = planned.filter { !it.skipped }.sortedBy { it.dueDate }
        val covered = LongArray(items.size)
        var pool = 0L
        for (pay in payments) {
            val amount = pay.amountMinor.coerceAtLeast(0)
            val idx = pay.plannedId?.let { id -> items.indexOfFirst { it.id == id } } ?: -1
            if (idx >= 0) {
                val take = minOf(amount, items[idx].amountMinor - covered[idx])
                covered[idx] += take
                pool += amount - take
            } else {
                pool += amount
            }
        }
        for (i in items.indices) {
            val take = minOf(pool, items[i].amountMinor - covered[i])
            covered[i] += take
            pool -= take
        }
        return items.mapIndexed { i, p -> PlanItem(p.dueDate, p.amountMinor, covered[i], p.id) }
    }

    /** Разовый долг превращается в один плановый платёж, долг «без срока» не имеет плановых платежей. */
    fun oneTime(principalMinor: Long, dueDate: LocalDate): List<PlannedPayment> =
        listOf(PlannedPayment(dueDate, principalMinor))

    fun summarize(
        principalMinor: Long,
        planned: List<PlannedPayment>,
        paymentsMinor: List<Long>,
        today: LocalDate,
        dueSoonDays: Int = 3,
        plannedIds: List<String?> = emptyList(),
    ): DebtSummary {
        val paid = paymentsMinor.sum()
        val remaining = (principalMinor - paid).coerceAtLeast(0)
        val plan = allocate(planned, paymentsMinor.mapIndexed { i, m -> PaymentAmount(m, plannedIds.getOrNull(i)) })

        if (remaining == 0L) {
            return DebtSummary(principalMinor, paid, 0, DebtStatus.CLOSED, 0, 0, plan, null)
        }

        val overdue = plan.filter { it.state != InstallmentState.PAID && it.dueDate.isBefore(today) }
        val overdueMinor = overdue.sumOf { it.remainingMinor }
        val overdueDays = overdue.minOfOrNull { it.dueDate }
            ?.let { ChronoUnit.DAYS.between(it, today) } ?: 0
        val nextDue = plan.firstOrNull { it.state != InstallmentState.PAID && !it.dueDate.isBefore(today) }

        val status = when {
            overdue.isNotEmpty() -> DebtStatus.OVERDUE
            nextDue != null && ChronoUnit.DAYS.between(today, nextDue.dueDate) <= dueSoonDays -> DebtStatus.DUE_SOON
            else -> DebtStatus.ACTIVE
        }
        return DebtSummary(principalMinor, paid, remaining, status, overdueMinor, overdueDays, plan, nextDue)
    }
}
