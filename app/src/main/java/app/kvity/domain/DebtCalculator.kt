package app.kvity.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class InstallmentState { PAID, PARTIAL, PENDING }

data class PlanItem(
    val dueDate: LocalDate,
    val amountMinor: Long,
    val paidMinor: Long,
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
     * Переплата переходит на следующие платежи.
     */
    fun allocate(planned: List<PlannedPayment>, totalPaidMinor: Long): List<PlanItem> {
        var pool = totalPaidMinor.coerceAtLeast(0)
        return planned.sortedBy { it.dueDate }.map { p ->
            val covered = minOf(pool, p.amountMinor)
            pool -= covered
            PlanItem(p.dueDate, p.amountMinor, covered)
        }
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
    ): DebtSummary {
        val paid = paymentsMinor.sum()
        val remaining = (principalMinor - paid).coerceAtLeast(0)
        val plan = allocate(planned, paid)

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
