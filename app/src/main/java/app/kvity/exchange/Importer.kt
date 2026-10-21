package app.kvity.exchange

import app.kvity.domain.AmountParser
import app.kvity.domain.Currencies
import app.kvity.domain.Direction
import app.kvity.domain.Periodicity
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

data class ImportPayment(
    val line: Int, val counterparty: String, val direction: Direction?, val currency: String?,
    val date: LocalDate, val amountMinor: Long, val note: String?,
)

enum class ImportSubState { ACTIVE, PAUSED, CANCELLED }

data class ImportSubscription(
    val line: Int, val name: String,
    /** null — платит сам пользователь. */
    val payerName: String?,
    val amountMinor: Long, val currency: String, val periodicity: Periodicity, val interval: Int,
    val anchor: LocalDate, val state: ImportSubState, val method: String?, val note: String?,
)

data class ImportShare(
    val line: Int, val subscription: String, val participant: String, val period: LocalDate,
    val amountMinor: Long, val currency: String, val paid: Boolean,
)

data class ImportPerson(val line: Int, val name: String, val phone: String?, val note: String?)

data class ImportBatch(
    val debts: List<ImportRow> = emptyList(),
    val payments: List<ImportPayment> = emptyList(),
    val subscriptions: List<ImportSubscription> = emptyList(),
    val shares: List<ImportShare> = emptyList(),
    val people: List<ImportPerson> = emptyList(),
    val errors: List<ImportError> = emptyList(),
    val ignoredSheets: List<String> = emptyList(),
) {
    val total: Int get() = debts.size + payments.size + subscriptions.size + shares.size + people.size
}

/** Читает файл выгрузки (xlsx, csv или zip с csv) и разбирает все известные таблицы. */
object Importer {

    fun parse(bytes: ByteArray, today: LocalDate = LocalDate.now()): ImportBatch {
        val sheets = readSheets(bytes)
            ?: return ImportBatch(errors = listOf(ImportError(0, "Не удалось прочитать файл. Нужен .xlsx, .csv или .zip с файлами .csv")))
        if (sheets.size == 1 && sheets[0].name == CSV_SHEET && !hasKnownHeader(sheets[0].rows)) {
            // CSV без заголовка: считаем таблицей долгов с колонками по порядку шаблона
            val r = DebtImport.parseRows(sheets[0].rows, today, null, requireHeader = false)
            return ImportBatch(debts = r.rows, errors = r.errors)
        }

        var batch = ImportBatch()
        val ignored = mutableListOf<String>()
        for (sheet in sheets) {
            val header = sheet.rows.firstOrNull()?.map { it.trim().lowercase() }.orEmpty()
            batch = when (kindOf(header)) {
                Kind.DEBTS -> DebtImport.parseRows(sheet.rows, today, sheet.name, requireHeader = true)
                    .let { batch.copy(debts = batch.debts + it.rows, errors = batch.errors + it.errors) }
                Kind.PAYMENTS -> parsePayments(sheet, header).let { (rows, errs) -> batch.copy(payments = batch.payments + rows, errors = batch.errors + errs) }
                Kind.SUBSCRIPTIONS -> parseSubscriptions(sheet, header, today).let { (rows, errs) -> batch.copy(subscriptions = batch.subscriptions + rows, errors = batch.errors + errs) }
                Kind.SHARES -> parseShares(sheet, header).let { (rows, errs) -> batch.copy(shares = batch.shares + rows, errors = batch.errors + errs) }
                Kind.PEOPLE -> parsePeople(sheet, header).let { (rows, errs) -> batch.copy(people = batch.people + rows, errors = batch.errors + errs) }
                null -> { ignored += sheet.name; batch }
            }
        }
        if (batch.total == 0 && batch.errors.isEmpty()) {
            return batch.copy(errors = listOf(ImportError(0, "В файле не найдено таблиц с известными заголовками (Долги, Платежи, Подписки, Доли в подписках, Контрагенты)")), ignoredSheets = ignored)
        }
        return batch.copy(ignoredSheets = ignored)
    }

    private const val CSV_SHEET = "CSV"

    private enum class Kind { DEBTS, PAYMENTS, SUBSCRIPTIONS, SHARES, PEOPLE }

    private fun hasKnownHeader(rows: List<List<String>>) = kindOf(rows.firstOrNull()?.map { it.trim().lowercase() }.orEmpty()) != null

    private fun kindOf(h: List<String>): Kind? = when {
        "контрагент" in h && "дата" in h && "сумма" in h -> Kind.PAYMENTS
        "контрагент" in h && "направление" in h && "сумма" in h -> Kind.DEBTS
        "название" in h && "дата списания" in h -> Kind.SUBSCRIPTIONS
        "подписка" in h && "участник" in h -> Kind.SHARES
        "имя" in h && ("телефон" in h || "заметка" in h || "баланс" in h) -> Kind.PEOPLE
        else -> null
    }

    private fun readSheets(bytes: ByteArray): List<SheetData>? {
        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            if (XlsxReader.isXlsx(bytes)) return XlsxReader.read(bytes)
            val sheets = mutableListOf<SheetData>()
            runCatching {
                ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (e.isDirectory || !e.name.lowercase().endsWith(".csv")) continue
                        sheets += SheetData(e.name.substringAfterLast('/').removeSuffix(".csv"), Csv.parse(String(z.readBytes(), Charsets.UTF_8)))
                    }
                }
            }
            return sheets.ifEmpty { null }
        }
        val text = String(bytes, Charsets.UTF_8)
        if (text.isBlank()) return null
        return listOf(SheetData(CSV_SHEET, Csv.parse(text)))
    }

    private class Cols(header: List<String>, private val row: List<String>) {
        private val index = header.withIndex().associate { (i, n) -> n to i }
        operator fun get(name: String): String = index[name.lowercase()]?.let { row.getOrNull(it) }?.trim().orEmpty()
        fun has(name: String) = name.lowercase() in index
    }

    private fun <T> parseTable(
        sheet: SheetData, header: List<String>, required: List<String>,
        row: (line: Int, c: Cols, problems: MutableList<String>) -> T?,
    ): Pair<List<T>, List<ImportError>> {
        val missing = required.filter { it.lowercase() !in header }
        if (missing.isNotEmpty()) return emptyList<T>() to listOf(ImportError(1, "Нет колонок: ${missing.joinToString(", ")}", sheet.name))
        val ok = mutableListOf<T>()
        val errs = mutableListOf<ImportError>()
        sheet.rows.drop(1).forEachIndexed { i, r ->
            if (r.all { it.isBlank() }) return@forEachIndexed
            val problems = mutableListOf<String>()
            val v = row(i + 2, Cols(header, r), problems)
            if (problems.isNotEmpty() || v == null) errs += ImportError(i + 2, problems.joinToString("; ").ifEmpty { "некорректная строка" }, sheet.name)
            else ok += v
        }
        return ok to errs
    }

    private fun amount(text: String, currency: String?, problems: MutableList<String>, field: String): Long? {
        val digits = currency?.let { Currencies.byCode(it)?.fractionDigits } ?: 2
        val v = AmountParser.parseMinor(text.trim().trimEnd('₽', '₸', '$', '€').trim(), digits)
        if (v == null) problems += "некорректная сумма в колонке «$field»: «$text»"
        return v
    }

    private fun parsePayments(sheet: SheetData, header: List<String>) =
        parseTable(sheet, header, listOf("Контрагент", "Дата", "Сумма")) { line, c, problems ->
            val name = c["Контрагент"]
            if (name.isEmpty()) problems += "не указан контрагент"
            val currencyText = c["Валюта"]
            val currency = if (currencyText.isEmpty()) null else DebtImport.parseCurrency(currencyText)
            if (currencyText.isNotEmpty() && currency == null) problems += "неизвестная валюта «$currencyText»"
            val date = DebtImport.parseDate(c["Дата"])
            if (date == null) problems += "некорректная дата «${c["Дата"]}»"
            val sum = amount(c["Сумма"], currency, problems, "Сумма")
            if (problems.isEmpty()) ImportPayment(line, name, DebtImport.parseDirection(c["Направление"]), currency, date!!, sum!!, c["Комментарий"].ifEmpty { null }) else null
        }

    internal fun parsePeriod(text: String): Pair<Periodicity, Int>? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return Periodicity.MONTH to 1
        val n = Regex("(\\d+)").find(t)?.value?.toIntOrNull() ?: 1
        if (n < 1) return null
        return when {
            t.contains("нед") -> Periodicity.WEEK to n
            t.contains("мес") -> Periodicity.MONTH to n
            t.contains("год") || Regex("\\bг\\.?").containsMatchIn(t) -> Periodicity.YEAR to n
            t.contains("дн") || t.contains("дне") -> Periodicity.DAYS to n
            else -> null
        }
    }

    private fun parseSubscriptions(sheet: SheetData, header: List<String>, today: LocalDate) =
        parseTable(sheet, header, listOf("Название", "Сумма", "Валюта", "Дата списания")) { line, c, problems ->
            val name = c["Название"]
            if (name.isEmpty()) problems += "не указано название"
            val currency = DebtImport.parseCurrency(c["Валюта"])
            if (currency == null) problems += "неизвестная валюта «${c["Валюта"]}»"
            val sum = amount(c["Сумма"], currency, problems, "Сумма")
            val period = parsePeriod(c["Период"])
            if (period == null) problems += "непонятный период «${c["Период"]}»"
            val anchor = DebtImport.parseDate(c["Дата списания"])
            if (anchor == null) problems += "некорректная дата списания «${c["Дата списания"]}»"
            val payerText = c["Кто платит"]
            val payer = when (payerText.lowercase()) { "", "я", "me", "мной", "сам" -> null; else -> payerText }
            val state = when (c["Статус"].lowercase()) {
                "на паузе", "пауза" -> ImportSubState.PAUSED
                "отменена", "отменено" -> ImportSubState.CANCELLED
                else -> ImportSubState.ACTIVE
            }
            if (problems.isEmpty()) ImportSubscription(line, name, payer, sum!!, currency!!, period!!.first, period.second, anchor ?: today, state, c["Способ оплаты"].ifEmpty { null }, c["Комментарий"].ifEmpty { null }) else null
        }

    private fun parseShares(sheet: SheetData, header: List<String>) =
        parseTable(sheet, header, listOf("Подписка", "Участник", "Период", "Доля", "Валюта")) { line, c, problems ->
            if (c["Подписка"].isEmpty()) problems += "не указана подписка"
            if (c["Участник"].isEmpty()) problems += "не указан участник"
            val currency = DebtImport.parseCurrency(c["Валюта"])
            if (currency == null) problems += "неизвестная валюта «${c["Валюта"]}»"
            val period = DebtImport.parseDate(c["Период"])
            if (period == null) problems += "некорректный период «${c["Период"]}»"
            val sum = amount(c["Доля"], currency, problems, "Доля")
            val paid = c["Статус"].lowercase().let { it == "оплачено" || it == "оплачен" || it == "да" }
            if (problems.isEmpty()) ImportShare(line, c["Подписка"], c["Участник"], period!!, sum!!, currency!!, paid) else null
        }

    private fun parsePeople(sheet: SheetData, header: List<String>) =
        parseTable(sheet, header, listOf("Имя")) { line, c, problems ->
            if (c["Имя"].isEmpty()) problems += "не указано имя"
            if (problems.isEmpty()) ImportPerson(line, c["Имя"], c["Телефон"].ifEmpty { null }, c["Заметка"].ifEmpty { null }) else null
        }
}
