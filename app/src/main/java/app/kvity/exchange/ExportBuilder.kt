package app.kvity.exchange

import app.kvity.data.DebtView
import app.kvity.data.PersonView
import app.kvity.data.SubscriptionPayer
import app.kvity.data.SubscriptionState
import app.kvity.data.SubscriptionView
import app.kvity.domain.Currencies
import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.domain.Periodicity
import app.kvity.domain.TermType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ExportTable(val title: String) {
    DEBTS("Долги"),
    PAYMENTS("Платежи"),
    SUBSCRIPTIONS("Подписки"),
    SHARES("Доли в подписках"),
    PEOPLE("Контрагенты"),
}

data class ExportOptions(
    val tables: Set<ExportTable> = ExportTable.entries.toSet(),
    /** Фильтр по направлению для долгов и платежей; null — все. */
    val direction: Direction? = null,
)

/** Названия колонок таблицы «Долги». Первые восемь совпадают с форматом импорта. */
object DebtColumns {
    const val COUNTERPARTY = "Контрагент"
    const val DIRECTION = "Направление"
    const val CURRENCY = "Валюта"
    const val AMOUNT = "Сумма"
    const val PAID = "Выплачено"
    const val START = "Дата выдачи"
    const val DUE = "Срок"
    const val NOTE = "Комментарий"
    val IMPORTABLE = listOf(COUNTERPARTY, DIRECTION, CURRENCY, AMOUNT, PAID, START, DUE, NOTE)
}

object ExportBuilder {
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun date(d: LocalDate?): String = d?.format(dateFormat).orEmpty()

    fun money(minor: Long, currencyCode: String): Cell =
        Cell.Num(BigDecimal.valueOf(minor, Currencies.byCode(currencyCode)?.fractionDigits ?: 2))

    fun directionLabel(d: Direction) = if (d == Direction.OWED_TO_ME) "мне должны" else "я должен"

    fun build(
        options: ExportOptions,
        debts: List<DebtView>,
        subs: List<SubscriptionView>,
        people: List<PersonView>,
        today: LocalDate,
    ): List<Table> {
        val filteredDebts = debts.filter { options.direction == null || it.debt.direction == options.direction }
            .sortedBy { it.counterpartyName.lowercase() }
        return ExportTable.entries.filter { it in options.tables }.map { t ->
            when (t) {
                ExportTable.DEBTS -> debtsTable(filteredDebts)
                ExportTable.PAYMENTS -> paymentsTable(filteredDebts)
                ExportTable.SUBSCRIPTIONS -> subscriptionsTable(subs)
                ExportTable.SHARES -> sharesTable(subs, today)
                ExportTable.PEOPLE -> peopleTable(people)
            }
        }
    }

    private fun statusLabel(s: DebtStatus) = when (s) {
        DebtStatus.ACTIVE -> "в срок"
        DebtStatus.DUE_SOON -> "скоро срок"
        DebtStatus.OVERDUE -> "просрочен"
        DebtStatus.CLOSED -> "закрыт"
    }

    private fun debtsTable(debts: List<DebtView>) = Table(
        ExportTable.DEBTS.title,
        DebtColumns.IMPORTABLE + listOf("Остаток", "Статус", "Условия", "Ближайший платёж"),
        debts.map { v ->
            val d = v.debt
            val s = v.summary
            listOf(
                cell(v.counterpartyName), cell(directionLabel(d.direction)), cell(d.currency),
                money(d.principalMinor, d.currency), money(s.paidMinor, d.currency),
                cell(date(d.startDate)),
                cell(if (d.termType == TermType.ONE_TIME) date(d.dueDate) else ""),
                cell(d.note),
                money(s.remainingMinor, d.currency), cell(statusLabel(s.status)),
                cell(when (d.termType) { TermType.ONE_TIME -> "разовый"; TermType.SCHEDULE -> "по графику"; TermType.NONE -> "без срока" }),
                cell(date(s.nextDue?.dueDate)),
            )
        },
    )

    private fun paymentsTable(debts: List<DebtView>) = Table(
        ExportTable.PAYMENTS.title,
        listOf("Контрагент", "Направление", "Валюта", "Дата", "Сумма", "Комментарий"),
        debts.flatMap { v ->
            v.payments.sortedBy { it.date }.map { p ->
                listOf(
                    cell(v.counterpartyName), cell(directionLabel(v.debt.direction)), cell(v.debt.currency),
                    cell(date(p.date)), money(p.amountMinor, v.debt.currency), cell(p.note),
                )
            }
        },
    )

    private fun periodLabel(p: Periodicity, interval: Int) = when (p) {
        Periodicity.WEEK -> if (interval == 1) "неделя" else "каждые $interval нед."
        Periodicity.MONTH -> if (interval == 1) "месяц" else "каждые $interval мес."
        Periodicity.YEAR -> if (interval == 1) "год" else "каждые $interval г."
        Periodicity.DAYS -> "каждые $interval дн."
    }

    private fun subscriptionsTable(subs: List<SubscriptionView>) = Table(
        ExportTable.SUBSCRIPTIONS.title,
        listOf("Название", "Кто платит", "Сумма", "Валюта", "Период", "Дата списания", "Статус", "Способ оплаты", "Комментарий"),
        subs.map { v ->
            val s = v.subscription
            listOf(
                cell(s.name),
                cell(if (s.payer == SubscriptionPayer.ME) "я" else v.payerName ?: "другой"),
                money(s.amountMinor, s.currency), cell(s.currency), cell(periodLabel(s.periodicity, s.interval)),
                cell(date(s.anchorDate)),
                cell(when (s.state) { SubscriptionState.ACTIVE -> "активна"; SubscriptionState.PAUSED -> "на паузе"; SubscriptionState.CANCELLED -> "отменена" }),
                cell(s.paymentMethodNote), cell(s.note),
            )
        },
    )

    private fun sharesTable(subs: List<SubscriptionView>, today: LocalDate) = Table(
        ExportTable.SHARES.title,
        listOf("Подписка", "Участник", "Период", "Доля", "Валюта", "Статус"),
        subs.flatMap { v ->
            v.shares.flatMap { sh ->
                // история и просрочки до сегодняшнего дня плюс ближайший период
                val upTo = sh.dues.firstOrNull { it.periodDate.isAfter(today) }?.periodDate ?: today
                sh.dues.filter { !it.periodDate.isAfter(upTo) }.map { d ->
                    listOf(
                        cell(v.subscription.name), cell(sh.counterpartyName), cell(date(d.periodDate)),
                        money(d.amountMinor, d.currencyCode), cell(d.currencyCode),
                        cell(when { d.paid -> "оплачено"; d.periodDate.isBefore(today) -> "просрочено"; else -> "ожидается" }),
                    )
                }
            }
        },
    )

    private fun peopleTable(people: List<PersonView>) = Table(
        ExportTable.PEOPLE.title,
        listOf("Имя", "Телефон", "Заметка", "Баланс"),
        people.map { p ->
            val balance = p.balance.entries.joinToString("; ") { (code, v) ->
                (if (v > 0) "+" else "-") + BigDecimal.valueOf(kotlin.math.abs(v), Currencies.byCode(code)?.fractionDigits ?: 2)
                    .stripTrailingZeros().toPlainString() + " " + code
            }
            listOf(cell(p.person.name), cell(p.person.phone), cell(p.person.note), cell(balance))
        },
    )
}
