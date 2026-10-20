package app.kvity.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Периоды дашборда. Периоды «сегодня», «завтра», «неделя», «месяц» накопительные:
 * платёж на завтра входит и в «завтра», и в «эту неделю», и в «этот месяц».
 * «Просрочено» и «следующий месяц» не пересекаются с остальными.
 */
enum class Bucket { OVERDUE, TODAY, TOMORROW, THIS_WEEK, THIS_MONTH, NEXT_MONTH }

/** Платёж к получению или к оплате. [amountMinor] всегда положительное число. */
data class Due(val date: LocalDate, val amountMinor: Long, val currencyCode: String)

object Buckets {

    fun of(date: LocalDate, today: LocalDate, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): Set<Bucket> {
        if (date.isBefore(today)) return setOf(Bucket.OVERDUE)

        val result = mutableSetOf<Bucket>()
        if (date == today) result += Bucket.TODAY
        if (date == today.plusDays(1)) result += Bucket.TOMORROW

        val lastDayOfWeek = firstDayOfWeek.minus(1)
        val endOfWeek = today.with(TemporalAdjusters.nextOrSame(lastDayOfWeek))
        if (!date.isAfter(endOfWeek)) result += Bucket.THIS_WEEK

        val thisMonth = YearMonth.from(today)
        val dateMonth = YearMonth.from(date)
        if (dateMonth == thisMonth) result += Bucket.THIS_MONTH
        if (dateMonth == thisMonth.plusMonths(1)) result += Bucket.NEXT_MONTH
        return result
    }

    /** Суммы по периодам, раздельно по валютам: период → (код валюты → сумма). */
    fun aggregate(
        dues: List<Due>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): Map<Bucket, Map<String, Long>> {
        val totals = Bucket.entries.associateWith { mutableMapOf<String, Long>() }
        for (due in dues) {
            for (bucket in of(due.date, today, firstDayOfWeek)) {
                totals.getValue(bucket).merge(due.currencyCode, due.amountMinor, Long::plus)
            }
        }
        return totals
    }
}
