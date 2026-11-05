package app.kvity.exchange

import app.kvity.domain.AmountParser
import app.kvity.domain.Currencies
import app.kvity.domain.Direction
import app.kvity.domain.Limits
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ImportRow(
    val line: Int,
    val counterparty: String,
    val direction: Direction,
    val currency: String,
    val principalMinor: Long,
    val paidMinor: Long,
    val startDate: LocalDate,
    val dueDate: LocalDate?,
    val note: String?,
    /** Дата выдачи указана в файле; иначе подставлена сегодняшняя и в сравнении дубликатов не участвует. */
    val startGiven: Boolean = true,
)

data class ImportError(val line: Int, val message: String, val sheet: String? = null)

data class ImportParseResult(val rows: List<ImportRow>, val errors: List<ImportError>)

object DebtImport {

    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ISO_LOCAL_DATE,
    )

    /** Образец файла для заполнения. Имена вымышленные. */
    fun template(lang: Lang = Lang.RU): Table = Table(
        lang.t("Долги", "Debts"), DebtColumns.importable(lang),
        listOf(
            listOf(
                cell(lang.t("Иван П", "John P")), cell(ExportBuilder.directionLabel(Direction.OWED_TO_ME, lang)), cell("RUB"), cell("50к"), cell("10к"),
                cell("01.09.2026"), cell("01.12.2026"), cell(lang.t("Пример: разовый долг со сроком", "Example: one-off debt with a due date")),
            ),
            listOf(
                cell(lang.t("Мария К", "Mary K")), cell(ExportBuilder.directionLabel(Direction.I_OWE, lang)), cell("KZT"), cell("120000"), Cell.Empty,
                cell("15.10.2026"), Cell.Empty, cell(lang.t("Пример: без срока", "Example: no due date")),
            ),
        ),
    )

    fun parse(text: String, today: LocalDate = LocalDate.now(), lang: Lang = Lang.RU): ImportParseResult =
        parseRows(Csv.parse(text), today, sheet = null, requireHeader = false, lang = lang)

    /** Разбор таблицы долгов. Без заголовка колонки берутся по порядку шаблона (только если [requireHeader] = false). */
    fun parseRows(table: List<List<String>>, today: LocalDate, sheet: String?, requireHeader: Boolean, lang: Lang = Lang.RU): ImportParseResult {
        if (table.isEmpty()) return ImportParseResult(emptyList(), listOf(ImportError(0, lang.t("Файл пуст", "The file is empty"), sheet)))

        val firstNormalized = Headers.canon(table[0], TableKind.DEBTS)
        val hasHeader = DebtColumns.COUNTERPARTY.lowercase() in firstNormalized
        if (!hasHeader && requireHeader) return ImportParseResult(emptyList(), listOf(ImportError(1, lang.t("Нет строки заголовков", "No header row"), sheet)))
        val columns: Map<String, Int> = if (hasHeader) {
            DebtColumns.IMPORTABLE.mapNotNull { name ->
                firstNormalized.indexOf(name.lowercase()).takeIf { it >= 0 }?.let { name to it }
            }.toMap()
        } else {
            DebtColumns.IMPORTABLE.withIndex().associate { (i, n) -> n to i }
        }
        val missing = listOf(DebtColumns.COUNTERPARTY, DebtColumns.DIRECTION, DebtColumns.CURRENCY, DebtColumns.AMOUNT)
            .filter { it !in columns }
        if (missing.isNotEmpty()) {
            return ImportParseResult(
                emptyList(),
                listOf(ImportError(1, lang.t("Нет обязательных колонок: ", "Missing required columns: ") + missing.joinToString(", ") { lang.h(it) }, sheet)),
            )
        }

        val rows = mutableListOf<ImportRow>()
        val errors = mutableListOf<ImportError>()
        val start = if (hasHeader) 1 else 0
        for (i in start until table.size) {
            val line = i + 1
            val r = table[i]
            fun get(name: String) = columns[name]?.let { r.getOrNull(it) }?.trim().orEmpty()
            if (r.all { it.isBlank() }) continue

            val name = get(DebtColumns.COUNTERPARTY)
            val direction = parseDirection(get(DebtColumns.DIRECTION))
            val currency = parseCurrency(get(DebtColumns.CURRENCY))
            val problems = mutableListOf<String>()
            if (name.isEmpty()) problems += lang.t("не указан контрагент", "counterparty is missing")
            else if (name.length > Limits.NAME) problems += Importer.tooLong(lang)
            if (direction == null) problems += lang.t("направление должно быть «мне должны» или «я должен»", "direction must be \"owed to me\" or \"I owe\"")
            if (currency == null) problems += lang.t("неизвестная валюта «${get(DebtColumns.CURRENCY)}»", "unknown currency \"${get(DebtColumns.CURRENCY)}\"")

            val digits = currency?.let { Currencies.byCode(it)?.fractionDigits } ?: 2
            val principal = parseAmount(get(DebtColumns.AMOUNT), digits)
            if (principal == null) problems += lang.t("некорректная сумма «${get(DebtColumns.AMOUNT)}»", "invalid amount \"${get(DebtColumns.AMOUNT)}\"")

            val paidText = get(DebtColumns.PAID)
            val paid = if (paidText.isEmpty() || isZero(paidText)) 0L else parseAmount(paidText, digits)
            if (paid == null) problems += lang.t("некорректная сумма выплат «$paidText»", "invalid paid amount \"$paidText\"")
            else if (principal != null && paid > principal) problems += lang.t("выплачено больше суммы долга", "paid amount exceeds the debt")

            val startText = get(DebtColumns.START)
            val startDate = if (startText.isEmpty()) today else parseDate(startText)
            if (startDate == null) problems += lang.t("некорректная дата выдачи «$startText»", "invalid start date \"$startText\"")

            val dueText = get(DebtColumns.DUE)
            val dueDate = if (dueText.isEmpty()) null else parseDate(dueText)
            if (dueText.isNotEmpty() && dueDate == null) problems += lang.t("некорректный срок «$dueText»", "invalid due date \"$dueText\"")

            if (problems.isNotEmpty()) {
                errors += ImportError(line, problems.joinToString("; "), sheet)
            } else {
                rows += ImportRow(line, name, direction!!, currency!!, principal!!, paid!!, startDate!!, dueDate, get(DebtColumns.NOTE).ifEmpty { null }, startText.isNotEmpty())
            }
        }
        return ImportParseResult(rows, errors)
    }

    /** Направление долга на русском («мне должны», «я должен») и английском («owed to me», «I owe»). */
    fun parseDirection(text: String): Direction? {
        val t = text.trim().lowercase()
        return when {
            t.isEmpty() -> null
            t == "+" || t == "in" || t.startsWith("мне") || t.startsWith("owed") || t.startsWith("they owe") -> Direction.OWED_TO_ME
            t == "-" || t == "−" || t == "out" || t.startsWith("я ") || t == "я" || t.startsWith("i owe") -> Direction.I_OWE
            else -> null
        }
    }

    /** Настоящая валюта ISO 4217: «XXX» (нет валюты), «XTS» (тест) и выдуманные коды не проходят. */
    private fun isRealCurrency(code: String): Boolean =
        runCatching { java.util.Currency.getInstance(code).defaultFractionDigits >= 0 }.getOrDefault(false)

    fun parseCurrency(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return when (t) {
            "₽", "руб", "руб.", "р", "р." -> "RUB"
            "₸", "тг", "тенге" -> "KZT"
            "$" -> "USD"
            "€" -> "EUR"
            else -> t.uppercase().takeIf { it.length == 3 && it.all { c -> c in 'A'..'Z' } && isRealCurrency(it) }
        }
    }

    /** «0», «0,00», «0 ₽»: выгрузка записывает отсутствие выплат нулём. */
    private fun isZero(text: String): Boolean =
        text.trim().trimEnd('₽', '₸', '$', '€').replace(" ", "").replace(" ", "").replace(" ", "").replace(',', '.')
            .toBigDecimalOrNull()?.signum() == 0

    private fun parseAmount(text: String, digits: Int): Long? {
        val cleaned = text.trim().trimEnd('₽', '₸', '$', '€').trim()
        return AmountParser.parseMinor(cleaned, digits)
    }

    fun parseDate(text: String): LocalDate? {
        val t = text.trim()
        // Excel хранит даты числом дней от 30.12.1899
        t.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20_000.0..80_000.0 }?.let {
            return LocalDate.of(1899, 12, 30).plusDays(it.toLong())
        }
        for (f in dateFormats) {
            try { return LocalDate.parse(t, f) } catch (_: DateTimeParseException) { }
        }
        return null
    }
}
