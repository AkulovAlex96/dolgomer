package app.kvity.exchange

import app.kvity.data.DebtView
import app.kvity.data.PersonView
import app.kvity.data.SubscriptionEntity
import app.kvity.data.SubscriptionPayer
import app.kvity.data.SubscriptionRepository
import app.kvity.data.SubscriptionState
import app.kvity.data.SubscriptionView
import app.kvity.domain.Currencies
import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.domain.Periodicity
import app.kvity.domain.SubscriptionCalc
import app.kvity.domain.TermType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** [title] — русское название листа (по умолчанию); [title] с языком даёт название на нужном языке. */
enum class ExportTable(val title: String, private val titleEn: String) {
    DEBTS("Долги", "Debts"),
    PAYMENTS("Платежи", "Payments"),
    SUBSCRIPTIONS("Регулярные платежи", "Subscriptions"),
    SHARES("Доли в регулярных платежах", "Subscription shares"),
    PEOPLE("Контрагенты", "Counterparties");

    fun title(lang: Lang): String = lang.t(title, titleEn)
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

    /** Те же колонки с заголовками на языке [lang]. */
    fun importable(lang: Lang) = IMPORTABLE.map { lang.h(it) }
}

object ExportBuilder {
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun date(d: LocalDate?): String = d?.format(dateFormat).orEmpty()

    fun money(minor: Long, currencyCode: String): Cell =
        Cell.Num(BigDecimal.valueOf(minor, Currencies.byCode(currencyCode)?.fractionDigits ?: 2))

    fun directionLabel(d: Direction, lang: Lang = Lang.RU) =
        if (d == Direction.OWED_TO_ME) lang.t("мне должны", "owed to me") else lang.t("я должен", "I owe")

    /** Заголовки колонок на языке [lang] по русским названиям. */
    private fun heads(lang: Lang, vararg ru: String) = ru.map { lang.h(it) }

    fun build(
        options: ExportOptions,
        debts: List<DebtView>,
        subs: List<SubscriptionView>,
        people: List<PersonView>,
        today: LocalDate,
        lang: Lang = Lang.RU,
    ): List<Table> {
        val filteredDebts = debts.filter { options.direction == null || it.debt.direction == options.direction }
            .sortedBy { it.counterpartyName.lowercase() }
        return ExportTable.entries.filter { it in options.tables }.map { t ->
            when (t) {
                ExportTable.DEBTS -> debtsTable(filteredDebts, lang)
                ExportTable.PAYMENTS -> paymentsTable(filteredDebts, lang)
                ExportTable.SUBSCRIPTIONS -> subscriptionsTable(subs, today, lang)
                ExportTable.SHARES -> sharesTable(subs, today, lang)
                ExportTable.PEOPLE -> peopleTable(people, lang)
            }
        }
    }

    private fun statusLabel(s: DebtStatus, lang: Lang) = when (s) {
        DebtStatus.ACTIVE -> lang.t("в срок", "on time")
        DebtStatus.DUE_SOON -> lang.t("скоро срок", "due soon")
        DebtStatus.OVERDUE -> lang.t("просрочен", "overdue")
        DebtStatus.CLOSED -> lang.t("закрыт", "closed")
    }

    private fun debtsTable(debts: List<DebtView>, lang: Lang) = Table(
        ExportTable.DEBTS.title(lang),
        DebtColumns.importable(lang) + heads(lang, "Остаток", "Статус", "Условия", "Ближайший платёж"),
        debts.map { v ->
            val d = v.debt
            val s = v.summary
            listOf(
                cell(v.counterpartyName), cell(directionLabel(d.direction, lang)), cell(d.currency),
                money(d.principalMinor, d.currency), money(s.paidMinor, d.currency),
                cell(date(d.startDate)),
                cell(if (d.termType == TermType.ONE_TIME) date(d.dueDate) else ""),
                cell(d.note),
                money(s.remainingMinor, d.currency), cell(if (d.archived) lang.t("архив", "archived") else statusLabel(s.status, lang)),
                cell(when (d.termType) {
                    TermType.ONE_TIME -> lang.t("разовый", "one-time")
                    TermType.SCHEDULE -> lang.t("по графику", "scheduled")
                    TermType.NONE -> lang.t("без срока", "no due date")
                }),
                cell(date(s.nextDue?.dueDate)),
            )
        },
    )

    private fun paymentsTable(debts: List<DebtView>, lang: Lang) = Table(
        ExportTable.PAYMENTS.title(lang),
        heads(lang, "Контрагент", "Направление", "Валюта", "Дата", "Сумма", "Комментарий", "Способ оплаты"),
        debts.flatMap { v ->
            v.payments.sortedBy { it.date }.map { p ->
                listOf(
                    cell(v.counterpartyName), cell(directionLabel(v.debt.direction, lang)), cell(v.debt.currency),
                    cell(date(p.date)), money(p.amountMinor, v.debt.currency), cell(p.note), cell(p.paymentMethod),
                )
            }
        },
    )

    private fun periodLabel(p: Periodicity, interval: Int, lang: Lang) = when (p) {
        Periodicity.WEEK -> if (interval == 1) lang.t("неделя", "week") else lang.t("каждые $interval нед.", "every $interval wk.")
        Periodicity.MONTH -> if (interval == 1) lang.t("месяц", "month") else lang.t("каждые $interval мес.", "every $interval mo.")
        Periodicity.YEAR -> if (interval == 1) lang.t("год", "year") else lang.t("каждые $interval г.", "every $interval yr.")
        Periodicity.DAYS -> lang.t("каждые $interval дн.", "every $interval d.")
    }

    private fun subscriptionsTable(subs: List<SubscriptionView>, today: LocalDate, lang: Lang) = Table(
        ExportTable.SUBSCRIPTIONS.title(lang),
        heads(lang, "Название", "Кто платит", "Сумма", "Валюта", "Период", "Дата списания", "Статус", "Способ оплаты", "Комментарий", "Дата начала"),
        subs.map { v ->
            val s = v.subscription
            listOf(
                cell(s.name),
                cell(if (s.payer == SubscriptionPayer.ME) lang.t("я", "me") else v.payerName ?: lang.t("другой", "other")),
                money(s.amountMinor, s.currency), cell(s.currency), cell(periodLabel(s.periodicity, s.interval, lang)),
                nextChargeCell(s, today),
                cell(when (s.state) {
                    SubscriptionState.ACTIVE -> lang.t("активна", "active")
                    SubscriptionState.PAUSED -> lang.t("на паузе", "paused")
                    SubscriptionState.CANCELLED -> lang.t("отменена", "cancelled")
                }),
                cell(s.paymentMethodNote), cell(s.note), cell(date(s.anchorDate)),
            )
        },
    )

    /** Ближайшее списание (сегодня или позже); у приостановленных и отменённых подписок пусто. */
    private fun nextChargeCell(s: SubscriptionEntity, today: LocalDate): Cell {
        if (s.state != SubscriptionState.ACTIVE) return Cell.Empty
        val next = SubscriptionCalc.occurrences(s.anchorDate, s.periodicity, s.interval, today, today.plusDays(SubscriptionRepository.HORIZON_DAYS)).firstOrNull()
        return if (next == null) Cell.Empty else cell(date(next))
    }

    private fun sharesTable(subs: List<SubscriptionView>, today: LocalDate, lang: Lang) = Table(
        ExportTable.SHARES.title(lang),
        heads(lang, "Подписка", "Участник", "Период", "Доля", "Валюта", "Статус"),
        subs.flatMap { v ->
            v.shares.flatMap { sh ->
                // история и просрочки до сегодняшнего дня плюс ближайший период
                val upTo = sh.dues.firstOrNull { it.periodDate.isAfter(today) }?.periodDate ?: today
                sh.dues.filter { !it.periodDate.isAfter(upTo) }.map { d ->
                    listOf(
                        cell(v.subscription.name), cell(sh.counterpartyName), cell(date(d.periodDate)),
                        money(d.amountMinor, d.currencyCode), cell(d.currencyCode),
                        cell(when {
                            d.paid -> lang.t("оплачено", "paid")
                            d.periodDate.isBefore(today) -> lang.t("просрочено", "overdue")
                            else -> lang.t("ожидается", "expected")
                        }),
                    )
                }
            }
        },
    )

    private fun peopleTable(people: List<PersonView>, lang: Lang) = Table(
        ExportTable.PEOPLE.title(lang),
        heads(lang, "Имя", "Телефон", "Заметка", "Баланс"),
        people.map { p ->
            val balance = p.balance.entries.joinToString("; ") { (code, v) ->
                (if (v > 0) "+" else "-") + BigDecimal.valueOf(kotlin.math.abs(v), Currencies.byCode(code)?.fractionDigits ?: 2)
                    .stripTrailingZeros().toPlainString() + " " + code
            }
            listOf(cell(p.person.name), cell(p.person.phone), cell(p.person.note), cell(balance))
        },
    )
}
