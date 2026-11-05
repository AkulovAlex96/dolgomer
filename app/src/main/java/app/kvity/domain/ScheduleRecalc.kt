package app.kvity.domain

import java.time.LocalDate

/** Изменение планового платежа при пересчёте: [newAmountMinor] = null означает «удалить». */
data class PlanChange(val id: String, val newAmountMinor: Long?)

/**
 * Пересчёт графика при досрочном или нестандартном платеже.
 *
 * Вариант «сократить срок» не требует действий: платёж по умолчанию закрывает ближайшие плановые платежи,
 * поэтому в графике остаются более поздние (см. [DebtCalculator.allocate]). Вариант «уменьшить платёж» это [reducePayment].
 */
object ScheduleRecalc {

    /**
     * Нужно ли предложить пересчёт после платежа от [paymentDate]: всего выплачено больше,
     * чем по графику требовалось к этой дате, долг не закрыт и впереди есть неоплаченные плановые платежи.
     * Недоплата (меньше планового) пересчёт не предлагает: она видна как частично оплаченный или просроченный платёж.
     */
    fun shouldOffer(
        planned: List<PlannedPayment>,
        payments: List<PaymentAmount>,
        principalMinor: Long,
        paymentDate: LocalDate,
    ): Boolean {
        val paid = payments.sumOf { it.amountMinor }
        if (paid <= 0 || principalMinor - paid <= 0) return false
        val plan = DebtCalculator.allocate(planned, payments)
        val dueByNow = plan.filter { !it.dueDate.isAfter(paymentDate) }.sumOf { it.amountMinor }
        val hasFutureOpen = plan.any { it.dueDate.isAfter(paymentDate) && it.paidMinor < it.amountMinor }
        return hasFutureOpen && paid > dueByNow
    }

    /**
     * Равномерно распределяет остаток долга по неоплаченным плановым платежам, начиная с даты [from]
     * (просроченные остаются как есть). Количество платежей не меняется, уменьшается их сумма:
     * лишние платежи, которым не хватило остатка, удаляются. Платежи без id (не сохранённые) пропускаются.
     */
    fun reducePayment(plan: List<PlanItem>, remainingMinor: Long, from: LocalDate): List<PlanChange> {
        val open = plan.filter { it.id != null && it.remainingMinor > 0 && !it.dueDate.isBefore(from) }
        if (open.isEmpty()) return emptyList()
        val before = plan.filter { it.remainingMinor > 0 && it.dueDate.isBefore(from) }.sumOf { it.remainingMinor }
        var left = (remainingMinor - before).coerceAtLeast(0)
        val share = (left + open.size - 1) / open.size
        val changes = mutableListOf<PlanChange>()
        for (item in open) {
            val r = minOf(share, left)
            left -= r
            val amount = item.paidMinor + r
            if (amount == item.amountMinor) continue
            changes += PlanChange(item.id!!, if (amount == 0L) null else amount)
        }
        return changes
    }
}
