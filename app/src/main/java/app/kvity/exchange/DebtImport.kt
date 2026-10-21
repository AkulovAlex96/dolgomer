package app.kvity.exchange

import app.kvity.domain.AmountParser
import app.kvity.domain.Currencies
import app.kvity.domain.Direction
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
)

data class ImportError(val line: Int, val message: String)

data class ImportParseResult(val rows: List<ImportRow>, val errors: List<ImportError>)

object DebtImport {

    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ISO_LOCAL_DATE,
    )

    /** Образец файла для заполнения. Имена вымышленные. */
    fun template(): Table = Table(
        "Долги", DebtColumns.IMPORTABLE,
        listOf(
            listOf(cell("Иван П"), cell("мне должны"), cell("RUB"), cell("50к"), cell("10к"), cell("01.09.2026"), cell("01.12.2026"), cell("Пример: разовый долг со сроком")),
            listOf(cell("Мария К"), cell("я должен"), cell("KZT"), cell("120000"), Cell.Empty, cell("15.10.2026"), Cell.Empty, cell("Пример: без срока")),
        ),
    )

    fun parse(text: String, today: LocalDate = LocalDate.now()): ImportParseResult {
        val table = Csv.parse(text)
        if (table.isEmpty()) return ImportParseResult(emptyList(), listOf(ImportError(0, "Файл пуст")))

        val firstNormalized = table[0].map { it.trim().lowercase() }
        val hasHeader = DebtColumns.COUNTERPARTY.lowercase() in firstNormalized
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
            return ImportParseResult(emptyList(), listOf(ImportError(1, "Нет обязательных колонок: ${missing.joinToString(", ")}")))
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
            if (name.isEmpty()) problems += "не указан контрагент"
            if (direction == null) problems += "направление должно быть «мне должны» или «я должен»"
            if (currency == null) problems += "неизвестная валюта «${get(DebtColumns.CURRENCY)}»"

            val digits = currency?.let { Currencies.byCode(it)?.fractionDigits } ?: 2
            val principal = parseAmount(get(DebtColumns.AMOUNT), digits)
            if (principal == null) problems += "некорректная сумма «${get(DebtColumns.AMOUNT)}»"

            val paidText = get(DebtColumns.PAID)
            val paid = if (paidText.isEmpty()) 0L else parseAmount(paidText, digits)
            if (paid == null) problems += "некорректная сумма выплат «$paidText»"
            else if (principal != null && paid > principal) problems += "выплачено больше суммы долга"

            val startText = get(DebtColumns.START)
            val startDate = if (startText.isEmpty()) today else parseDate(startText)
            if (startDate == null) problems += "некорректная дата выдачи «$startText»"

            val dueText = get(DebtColumns.DUE)
            val dueDate = if (dueText.isEmpty()) null else parseDate(dueText)
            if (dueText.isNotEmpty() && dueDate == null) problems += "некорректный срок «$dueText»"

            if (problems.isNotEmpty()) {
                errors += ImportError(line, problems.joinToString("; "))
            } else {
                rows += ImportRow(line, name, direction!!, currency!!, principal!!, paid!!, startDate!!, dueDate, get(DebtColumns.NOTE).ifEmpty { null })
            }
        }
        return ImportParseResult(rows, errors)
    }

    fun parseDirection(text: String): Direction? {
        val t = text.trim().lowercase()
        return when {
            t.isEmpty() -> null
            t == "+" || t == "in" || t.startsWith("мне") -> Direction.OWED_TO_ME
            t == "-" || t == "−" || t == "out" || t.startsWith("я ") || t == "я" -> Direction.I_OWE
            else -> null
        }
    }

    fun parseCurrency(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return when (t) {
            "₽", "руб", "руб.", "р", "р." -> "RUB"
            "₸", "тг", "тенге" -> "KZT"
            "$" -> "USD"
            "€" -> "EUR"
            else -> t.uppercase().takeIf { it.length == 3 && it.all { c -> c in 'A'..'Z' } }
        }
    }

    private fun parseAmount(text: String, digits: Int): Long? {
        val cleaned = text.trim().trimEnd('₽', '₸', '$', '€').trim()
        return AmountParser.parseMinor(cleaned, digits)
    }

    fun parseDate(text: String): LocalDate? {
        val t = text.trim()
        for (f in dateFormats) {
            try { return LocalDate.parse(t, f) } catch (_: DateTimeParseException) { }
        }
        return null
    }
}
