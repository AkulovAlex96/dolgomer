package app.kvity.data

import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.domain.InstallmentState
import app.kvity.domain.SubscriptionCalc
import java.time.LocalDate
import java.time.YearMonth

enum class EventKind { DEBT, SUBSCRIPTION }
enum class MoneyFlow { IN, OUT }

/** Событие календаря: ожидаемое поступление или платёж. */
data class CalendarEvent(
    val date: LocalDate,
    val kind: EventKind,
    val flow: MoneyFlow,
    val title: String,
    val subtitle: String?,
    val amountMinor: Long,
    val currency: String,
    val overdue: Boolean,
    val refId: String,
    val shareId: String? = null,
    val periodDate: LocalDate? = null,
)

/** Итог календарного месяца в одной валюте: сколько вернут мне и сколько верну я. */
data class MonthTotal(val currency: String, val incomingMinor: Long, val outgoingMinor: Long)

object Events {

    /** Суммы событий за месяц по валютам: основная валюта первой, остальные по алфавиту. */
    fun monthTotals(events: List<CalendarEvent>, month: YearMonth, mainCurrency: String): List<MonthTotal> =
        events.filter { YearMonth.from(it.date) == month }
            .groupBy { it.currency }
            .map { (code, list) ->
                MonthTotal(
                    code,
                    incomingMinor = list.filter { it.flow == MoneyFlow.IN }.sumOf { it.amountMinor },
                    outgoingMinor = list.filter { it.flow == MoneyFlow.OUT }.sumOf { it.amountMinor },
                )
            }
            .sortedWith(compareBy<MonthTotal> { it.currency != mainCurrency }.thenBy { it.currency })

    fun build(debts: List<DebtView>, subs: List<SubscriptionView>, today: LocalDate): List<CalendarEvent> =
        debtEvents(debts, today) + subscriptionEvents(subs, today)

    fun debtEvents(debts: List<DebtView>, today: LocalDate): List<CalendarEvent> =
        debts.filter { it.summary.status != DebtStatus.CLOSED }.flatMap { v ->
            val flow = if (v.debt.direction == Direction.OWED_TO_ME) MoneyFlow.IN else MoneyFlow.OUT
            val items = v.summary.plan
            items.filter { it.state != InstallmentState.PAID }.map {
                val index = items.indexOf(it) + 1
                CalendarEvent(
                    date = it.dueDate, kind = EventKind.DEBT, flow = flow,
                    title = v.counterpartyName,
                    subtitle = if (items.size > 1) "платёж $index из ${items.size}" else v.debt.title,
                    amountMinor = it.remainingMinor, currency = v.debt.currency,
                    overdue = it.dueDate.isBefore(today), refId = v.debt.id,
                )
            }
        }

    fun subscriptionEvents(subs: List<SubscriptionView>, today: LocalDate): List<CalendarEvent> {
        val horizon = today.plusDays(SubscriptionRepository.HORIZON_DAYS)
        val out = mutableListOf<CalendarEvent>()
        for (v in subs) {
            val s = v.subscription
            // Мои списания: прошедшие (за последний год) и будущие, чтобы месяцы календаря не были пустыми
            if (s.state == SubscriptionState.ACTIVE) {
                SubscriptionCalc.occurrences(s.anchorDate, s.periodicity, s.interval, today.minusDays(365), horizon).forEach { d ->
                    out += CalendarEvent(
                        date = d, kind = EventKind.SUBSCRIPTION, flow = MoneyFlow.OUT, title = s.name,
                        subtitle = if (s.payer == SubscriptionPayer.ME) "списание" else "возвращаю ${v.payerName.orEmpty()}",
                        amountMinor = s.amountMinor, currency = s.currency, overdue = false, refId = s.id,
                    )
                }
            }
            // Доли участников: неоплаченные, в том числе просроченные
            for (sh in v.shares) for (due in sh.dues.filter { !it.paid }) {
                out += CalendarEvent(
                    date = due.periodDate, kind = EventKind.SUBSCRIPTION, flow = MoneyFlow.IN,
                    title = sh.counterpartyName, subtitle = s.name,
                    amountMinor = due.amountMinor, currency = due.currencyCode,
                    overdue = due.periodDate.isBefore(today), refId = s.id,
                    shareId = sh.share.id, periodDate = due.periodDate,
                )
            }
        }
        return out
    }
}
