package app.kvity.domain

import java.time.LocalDate

/** Доля участника за один период подписки. */
data class ShareDue(val periodDate: LocalDate, val amountMinor: Long, val currencyCode: String, val paid: Boolean)

object SubscriptionCalc {
    private const val MAX_OCCURRENCES = 5000

    /** Даты списаний подписки в отрезке [from]..[to] включительно. */
    fun occurrences(
        anchor: LocalDate,
        periodicity: Periodicity,
        interval: Int,
        from: LocalDate,
        to: LocalDate,
    ): List<LocalDate> {
        val rule = ScheduleRule(periodicity, installmentMinor = 1, firstDate = anchor, interval = interval.coerceAtLeast(1))
        val result = mutableListOf<LocalDate>()
        var i = 0
        while (i < MAX_OCCURRENCES) {
            val date = ScheduleGenerator.dateAt(rule, i)
            if (date.isAfter(to)) break
            if (!date.isBefore(from)) result += date
            i++
        }
        return result
    }

    /**
     * Периоды, за которые участник должен вернуть долю, начиная с [activeFrom].
     * Прошлые неоплаченные периоды остаются долгом; будущие строятся до [horizonEnd].
     */
    fun shareDues(
        anchor: LocalDate,
        periodicity: Periodicity,
        interval: Int,
        activeFrom: LocalDate,
        amountMinor: Long,
        currencyCode: String,
        paidPeriods: Set<LocalDate>,
        horizonEnd: LocalDate,
        paidAmounts: Map<LocalDate, Long> = emptyMap(),
        pauses: List<Pair<LocalDate, LocalDate?>> = emptyList(),
        amountHistory: List<Pair<LocalDate, Long>> = emptyList(),
    ): List<ShareDue> =
        occurrences(anchor, periodicity, interval, maxOf(activeFrom, anchor), horizonEnd)
            .filter { it in paidPeriods || !isPaused(it, pauses) } // оплаченный период остаётся в истории, даже если позже попал в паузу
            .map {
                // оплаченный период хранит сумму оплаты; неоплаченный берёт сумму, действовавшую на его дату
                val amount = if (it in paidPeriods) paidAmounts[it] ?: amountAt(it, amountMinor, amountHistory) else amountAt(it, amountMinor, amountHistory)
                ShareDue(it, amount, currencyCode, it in paidPeriods)
            }

    /** Попадает ли дата периода в интервал паузы: [from, to), открытая пауза (to = null) не заканчивается. */
    fun isPaused(date: LocalDate, pauses: List<Pair<LocalDate, LocalDate?>>): Boolean =
        pauses.any { (from, to) -> !date.isBefore(from) && (to == null || date.isBefore(to)) }

    /** Сумма доли на дату периода по истории изменений; до первой записи действует первая сумма, без истории — текущая. */
    fun amountAt(date: LocalDate, current: Long, history: List<Pair<LocalDate, Long>>): Long {
        if (history.isEmpty()) return current
        val sorted = history.sortedBy { it.first }
        return (sorted.lastOrNull { !it.first.isAfter(date) } ?: sorted.first()).second
    }

    /** Приблизительная стоимость в месяц (для итогов «в месяц / в год»). Для периодов дольше месяца делится. */
    fun monthlyMinor(amountMinor: Long, periodicity: Periodicity, interval: Int): Long {
        val n = interval.coerceAtLeast(1).toLong()
        return when (periodicity) {
            Periodicity.WEEK -> amountMinor * 52 / 12 / n
            Periodicity.MONTH -> amountMinor / n
            Periodicity.YEAR -> amountMinor / (12 * n)
            Periodicity.DAYS -> amountMinor * 365 / 12 / n
        }
    }
}
