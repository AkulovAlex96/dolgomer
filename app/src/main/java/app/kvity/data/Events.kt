package app.kvity.data

import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.domain.InstallmentState
import app.kvity.domain.SubscriptionCalc
import java.time.LocalDate

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

object Events {

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
            // Мои списания (только будущие и сегодняшние): что и когда спишется
            if (s.state == SubscriptionState.ACTIVE) {
                SubscriptionCalc.occurrences(s.anchorDate, s.periodicity, s.interval, today, horizon).forEach { d ->
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
