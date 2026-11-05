package app.kvity.data

import app.kvity.domain.Direction
import java.time.LocalDate
import java.time.YearMonth

/** Оплаченная доля участника или моё списание: дата оплаты, сумма и валюта. */
data class PaidRecord(val paidDate: LocalDate, val amountMinor: Long, val currency: String)

/**
 * Итог месяца в одной валюте: [receivedMinor] — мне вернули, [paidMinor] — я вернул/заплатил,
 * [expectedInMinor] и [expectedOutMinor] — что ещё ожидается получить и заплатить до конца месяца.
 */
data class MonthSummaryRow(
    val currency: String,
    val receivedMinor: Long,
    val paidMinor: Long,
    val expectedInMinor: Long,
    val expectedOutMinor: Long,
)

object MonthSummary {

    /** Сводка за [month] по валютам (валюты не складываются): основная первой, остальные по алфавиту. Нулевые валюты опускаются. */
    fun build(
        debts: List<DebtView>,
        subs: List<SubscriptionView>,
        today: LocalDate,
        month: YearMonth = YearMonth.from(today),
        mainCurrency: String? = null,
    ): List<MonthSummaryRow> {
        val active = debts.filter { !it.debt.archived }
        val received = mutableMapOf<String, Long>()
        val paid = mutableMapOf<String, Long>()
        val expIn = mutableMapOf<String, Long>()
        val expOut = mutableMapOf<String, Long>()
        fun MutableMap<String, Long>.add(code: String, v: Long) = merge(code, v, Long::plus)

        for (v in active) {
            val target = if (v.debt.direction == Direction.OWED_TO_ME) received else paid
            for (p in v.payments) if (YearMonth.from(p.date) == month) target.add(v.debt.currency, p.amountMinor)
        }
        for (s in subs) {
            for (r in s.paidShares) if (YearMonth.from(r.paidDate) == month) received.add(r.currency, r.amountMinor)
            for (r in s.paidChargeRecords) if (YearMonth.from(r.paidDate) == month) paid.add(r.currency, r.amountMinor)
        }
        // Ожидается то, что ещё не оплачено: просроченное в этом месяце и предстоящее
        for (e in Events.build(active, subs, today)) {
            if (e.paid || YearMonth.from(e.date) != month) continue
            if (e.date.isBefore(today) && !e.overdue) continue
            (if (e.flow == MoneyFlow.IN) expIn else expOut).add(e.currency, e.amountMinor)
        }
        val codes = received.keys + paid.keys + expIn.keys + expOut.keys
        return codes.map {
            MonthSummaryRow(it, received[it] ?: 0, paid[it] ?: 0, expIn[it] ?: 0, expOut[it] ?: 0)
        }.filter { it.receivedMinor != 0L || it.paidMinor != 0L || it.expectedInMinor != 0L || it.expectedOutMinor != 0L }
            .sortedWith(compareBy<MonthSummaryRow> { it.currency != mainCurrency }.thenBy { it.currency })
    }
}
