package app.kvity.data

import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.domain.InstallmentState
import app.kvity.domain.SubscriptionCalc
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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
    /** Платёж уже отмечен оплаченным. */
    val paid: Boolean = false,
    /** Своё списание, которое можно отметить оплаченным. */
    val markable: Boolean = false,
)

/** Итог календарного месяца в одной валюте: сколько вернут мне и сколько верну я. */
data class MonthTotal(val currency: String, val incomingMinor: Long, val outgoingMinor: Long)

/** Платёж по долгу, у которого известен только месяц (ТЗ 3.15): в ячейки дней не ставится. */
data class UndatedPayment(val month: YearMonth, val flow: MoneyFlow, val currency: String, val amountMinor: Long)

object Events {

    /** Платежи «только месяц» по всем долгам: входящие (мне вернули) и исходящие (я вернул). */
    fun undatedPayments(debts: List<DebtView>): List<UndatedPayment> =
        debts.flatMap { v ->
            val flow = if (v.debt.direction == Direction.OWED_TO_ME) MoneyFlow.IN else MoneyFlow.OUT
            v.payments.filter { it.monthOnly }.map { UndatedPayment(YearMonth.from(it.date), flow, v.debt.currency, it.amountMinor) }
        }

    private fun totalsOf(events: List<CalendarEvent>, mainCurrency: String): List<MonthTotal> =
        events.groupBy { it.currency }
            .map { (code, list) ->
                MonthTotal(
                    code,
                    incomingMinor = list.filter { it.flow == MoneyFlow.IN }.sumOf { it.amountMinor },
                    outgoingMinor = list.filter { it.flow == MoneyFlow.OUT }.sumOf { it.amountMinor },
                )
            }
            .sortedWith(compareBy<MonthTotal> { it.currency != mainCurrency }.thenBy { it.currency })

    /** Суммы платежей без точной даты за месяц по валютам (основная первой). */
    fun undatedTotals(items: List<UndatedPayment>, month: YearMonth, mainCurrency: String): List<MonthTotal> =
        totalsOf(
            items.filter { it.month == month }.map {
                CalendarEvent(
                    it.month.atDay(1), EventKind.DEBT, it.flow, "", null, it.amountMinor, it.currency, overdue = false, refId = "",
                )
            },
            mainCurrency,
        )

    /**
     * Суммы одного дня по валютам: оплаченное и просроченное не входят (просрочка не смешивается с суммой дня,
     * она видна в списке дня и красной точкой). Основная валюта первой, остальные по алфавиту.
     */
    fun dayTotals(events: List<CalendarEvent>, mainCurrency: String): List<MonthTotal> =
        totalsOf(events.filter { !it.paid && !it.overdue }, mainCurrency)

    /**
     * Суммы событий за месяц по валютам: основная валюта первой, остальные по алфавиту.
     * Единое правило: считается то, что ещё предстоит, оплаченные события (свои списания и доли участников) в итог не входят,
     * как и оплаченные взносы по долгам.
     */
    fun monthTotals(events: List<CalendarEvent>, month: YearMonth, mainCurrency: String): List<MonthTotal> =
        events.filter { YearMonth.from(it.date) == month && !it.paid }
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
            // Мои списания: прошедшие (за последний год) и будущие, чтобы месяцы календаря не были пустыми.
            // Просрочкой считаются только неотмеченные списания с момента, как подписка заведена в приложении
            val trackedFrom = Instant.ofEpochMilli(s.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            if (s.state == SubscriptionState.ACTIVE) {
                SubscriptionCalc.occurrences(s.anchorDate, s.periodicity, s.interval, today.minusDays(365), horizon).filter { it in v.paidCharges || !SubscriptionCalc.isPaused(it, v.pauses) }.forEach { d ->
                    out += CalendarEvent(
                        date = d, kind = EventKind.SUBSCRIPTION, flow = MoneyFlow.OUT, title = s.name,
                        subtitle = if (s.payer == SubscriptionPayer.ME) "списание" else "возвращаю ${v.payerName.orEmpty()}",
                        amountMinor = s.amountMinor, currency = s.currency, refId = s.id,
                        overdue = d !in v.paidCharges && d.isBefore(today) && !d.isBefore(trackedFrom),
                        periodDate = d, paid = d in v.paidCharges,
                        markable = d !in v.paidCharges && !d.isBefore(trackedFrom),
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
