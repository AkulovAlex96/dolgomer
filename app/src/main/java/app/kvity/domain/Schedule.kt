package app.kvity.domain

import java.time.LocalDate
import java.time.YearMonth

enum class Periodicity { WEEK, MONTH, YEAR, DAYS }

/**
 * Правило графика: «10 000 раз в месяц до 10 числа, начиная с 10.11».
 * [count] = null означает «до полного погашения».
 */
data class ScheduleRule(
    val periodicity: Periodicity,
    val installmentMinor: Long,
    val firstDate: LocalDate,
    val interval: Int = 1,
    val dayOfMonth: Int? = null,
    val count: Int? = null,
)

data class PlannedPayment(val dueDate: LocalDate, val amountMinor: Long)

object ScheduleGenerator {
    private const val MAX_ITEMS = 1200

    fun generate(rule: ScheduleRule, principalMinor: Long): List<PlannedPayment> {
        require(rule.interval >= 1) { "interval must be >= 1" }
        if (principalMinor <= 0 || rule.installmentMinor <= 0) return emptyList()

        val result = mutableListOf<PlannedPayment>()
        var remaining = principalMinor
        var i = 0
        while (remaining > 0 && result.size < MAX_ITEMS && (rule.count == null || i < rule.count)) {
            val isLastByCount = rule.count != null && i == rule.count - 1
            val amount = if (isLastByCount) remaining else minOf(rule.installmentMinor, remaining)
            result += PlannedPayment(dateAt(rule, i), amount)
            remaining -= amount
            i++
        }
        return result
    }

    /** Дата платежа с номером [index] (с нуля). Для повторяющихся событий, например подписок. */
    fun dateAt(rule: ScheduleRule, index: Int): LocalDate {
        if (index == 0) return rule.firstDate
        val step = rule.interval.toLong() * index
        return when (rule.periodicity) {
            Periodicity.WEEK -> rule.firstDate.plusDays(7 * step)
            Periodicity.DAYS -> rule.firstDate.plusDays(step)
            Periodicity.MONTH -> monthlyDate(rule, step)
            Periodicity.YEAR -> monthlyDate(rule, 12 * step)
        }
    }

    private fun monthlyDate(rule: ScheduleRule, months: Long): LocalDate {
        val ym = YearMonth.from(rule.firstDate).plusMonths(months)
        val day = rule.dayOfMonth ?: rule.firstDate.dayOfMonth
        return ym.atDay(minOf(day, ym.lengthOfMonth()))
    }
}
