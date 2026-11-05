package app.kvity.exchange

import app.kvity.domain.AmountParser
import app.kvity.domain.Currencies
import app.kvity.domain.Direction
import app.kvity.domain.Limits
import app.kvity.domain.Periodicity
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.util.zip.ZipInputStream

data class ImportPayment(
    val line: Int, val counterparty: String, val direction: Direction?, val currency: String?,
    val date: LocalDate, val amountMinor: Long, val note: String?,
    /** Колонка «Способ оплаты»; в старых файлах её нет. */
    val method: String? = null,
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

/**
 * Читает файл выгрузки (xlsx, csv или zip с csv) и разбирает все известные таблицы.
 * Заголовки и значения принимаются и на русском, и на английском; [lang] задаёт только язык сообщений об ошибках.
 */
object Importer {

    fun parse(bytes: ByteArray, today: LocalDate = LocalDate.now(), lang: Lang = Lang.RU): ImportBatch {
        val sheets = readSheets(bytes)
            ?: return ImportBatch(errors = listOf(ImportError(0, lang.t(
                "Не удалось прочитать файл. Нужен .xlsx, .csv или .zip с файлами .csv",
                "Could not read the file. An .xlsx, .csv or a .zip with .csv files is required",
            ))))
        if (sheets.size == 1 && sheets[0].name == CSV_SHEET && !hasKnownHeader(sheets[0].rows)) {
            // CSV без заголовка: считаем таблицей долгов с колонками по порядку шаблона
            val r = DebtImport.parseRows(sheets[0].rows, today, null, requireHeader = false, lang = lang)
            return ImportBatch(debts = r.rows, errors = r.errors)
        }

        var batch = ImportBatch()
        val ignored = mutableListOf<String>()
        for (sheet in sheets) {
            val raw = sheet.rows.firstOrNull().orEmpty()
            val kind = kindOf(Headers.canonAny(raw))
            val header = if (kind == null) emptyList() else Headers.canon(raw, kind.table)
            batch = when (kind) {
                Kind.DEBTS -> DebtImport.parseRows(sheet.rows, today, sheet.name, requireHeader = true, lang = lang)
                    .let { batch.copy(debts = batch.debts + it.rows, errors = batch.errors + it.errors) }
                Kind.PAYMENTS -> parsePayments(sheet, header, lang).let { (rows, errs) -> batch.copy(payments = batch.payments + rows, errors = batch.errors + errs) }
                Kind.SUBSCRIPTIONS -> parseSubscriptions(sheet, header, today, lang).let { (rows, errs) -> batch.copy(subscriptions = batch.subscriptions + rows, errors = batch.errors + errs) }
                Kind.SHARES -> parseShares(sheet, header, lang).let { (rows, errs) -> batch.copy(shares = batch.shares + rows, errors = batch.errors + errs) }
                Kind.PEOPLE -> parsePeople(sheet, header, lang).let { (rows, errs) -> batch.copy(people = batch.people + rows, errors = batch.errors + errs) }
                null -> { ignored += sheet.name; batch }
            }
        }
        if (batch.total == 0 && batch.errors.isEmpty()) {
            return batch.copy(
                errors = listOf(ImportError(0, lang.t(
                    "В файле не найдено таблиц с известными заголовками (Долги, Платежи, Регулярные платежи, Доли в регулярных платежах, Контрагенты)",
                    "No tables with known headers found in the file (Debts, Payments, Subscriptions, Subscription shares, Counterparties)",
                ))),
                ignoredSheets = ignored,
            )
        }
        return batch.copy(ignoredSheets = ignored)
    }

    private const val CSV_SHEET = "CSV"

    internal fun tooLong(lang: Lang) =
        lang.t("слишком длинное имя (максимум ${Limits.NAME} символов)", "name is too long (maximum ${Limits.NAME} characters)")

    private enum class Kind(val table: TableKind) {
        DEBTS(TableKind.DEBTS), PAYMENTS(TableKind.PAYMENTS), SUBSCRIPTIONS(TableKind.SUBSCRIPTIONS),
        SHARES(TableKind.SHARES), PEOPLE(TableKind.PEOPLE),
    }

    private fun hasKnownHeader(rows: List<List<String>>) = kindOf(Headers.canonAny(rows.firstOrNull().orEmpty())) != null

    /** [h] — все возможные русские названия колонок (нижний регистр), см. [Headers.canonAny]. */
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
                        val text = decodeText(z.readBytes()) ?: continue
                        sheets += SheetData(e.name.substringAfterLast('/').removeSuffix(".csv"), Csv.parse(text))
                    }
                }
            }
            return sheets.ifEmpty { null }
        }
        val text = decodeText(bytes)
        if (text.isNullOrBlank()) return null
        return listOf(SheetData(CSV_SHEET, Csv.parse(text)))
    }

    /**
     * Текст файла: UTF-8, а если байты не образуют корректный UTF-8, то windows-1251 (так Excel сохраняет «CSV» по-русски).
     * Бинарный файл (с нулевыми байтами) текстом не считается: null.
     */
    internal fun decodeText(bytes: ByteArray): String? {
        if (bytes.take(8192).any { it == 0.toByte() }) return null
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1251"))
        }
    }

    /** [header] уже приведён к русским названиям в нижнем регистре ([Headers.canon]). */
    private class Cols(header: List<String>, private val row: List<String>) {
        private val index = header.withIndex().associate { (i, n) -> n to i }
        operator fun get(name: String): String = index[name.lowercase()]?.let { row.getOrNull(it) }?.trim().orEmpty()
        fun has(name: String) = name.lowercase() in index
    }

    private fun <T> parseTable(
        sheet: SheetData, header: List<String>, required: List<String>, lang: Lang,
        row: (line: Int, c: Cols, problems: MutableList<String>) -> T?,
    ): Pair<List<T>, List<ImportError>> {
        val missing = required.filter { it.lowercase() !in header }
        if (missing.isNotEmpty()) {
            return emptyList<T>() to listOf(ImportError(1, lang.t("Нет колонок: ", "Missing columns: ") + missing.joinToString(", ") { lang.h(it) }, sheet.name))
        }
        val ok = mutableListOf<T>()
        val errs = mutableListOf<ImportError>()
        sheet.rows.drop(1).forEachIndexed { i, r ->
            if (r.all { it.isBlank() }) return@forEachIndexed
            val problems = mutableListOf<String>()
            val v = row(i + 2, Cols(header, r), problems)
            if (problems.isNotEmpty() || v == null) errs += ImportError(i + 2, problems.joinToString("; ").ifEmpty { lang.t("некорректная строка", "invalid row") }, sheet.name)
            else ok += v
        }
        return ok to errs
    }

    private fun amount(text: String, currency: String?, problems: MutableList<String>, field: String, lang: Lang): Long? {
        val digits = currency?.let { Currencies.byCode(it)?.fractionDigits } ?: 2
        val v = AmountParser.parseMinor(text.trim().trimEnd('₽', '₸', '$', '€').trim(), digits)
        if (v == null) problems += lang.t("некорректная сумма в колонке «$field»: «$text»", "invalid amount in column \"${lang.h(field)}\": \"$text\"")
        return v
    }

    private fun parsePayments(sheet: SheetData, header: List<String>, lang: Lang) =
        parseTable(sheet, header, listOf("Контрагент", "Дата", "Сумма"), lang) { line, c, problems ->
            val name = c["Контрагент"]
            if (name.isEmpty()) problems += lang.t("не указан контрагент", "counterparty is missing")
            else if (name.length > Limits.NAME) problems += tooLong(lang)
            val currencyText = c["Валюта"]
            val currency = if (currencyText.isEmpty()) null else DebtImport.parseCurrency(currencyText)
            if (currencyText.isNotEmpty() && currency == null) problems += lang.t("неизвестная валюта «$currencyText»", "unknown currency \"$currencyText\"")
            val date = DebtImport.parseDate(c["Дата"])
            if (date == null) problems += lang.t("некорректная дата «${c["Дата"]}»", "invalid date \"${c["Дата"]}\"")
            val sum = amount(c["Сумма"], currency, problems, "Сумма", lang)
            if (problems.isEmpty()) ImportPayment(line, name, DebtImport.parseDirection(c["Направление"]), currency, date!!, sum!!, c["Комментарий"].ifEmpty { null }, c["Способ оплаты"].take(Limits.NAME).ifEmpty { null }) else null
        }

    internal fun parsePeriod(text: String): Pair<Periodicity, Int>? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return Periodicity.MONTH to 1
        val n = Regex("(\\d+)").find(t)?.value?.toIntOrNull() ?: 1
        if (n < 1) return null
        return when {
            t.contains("нед") || t.contains("week") || Regex("\\bwks?\\b").containsMatchIn(t) -> Periodicity.WEEK to n
            t.contains("мес") || t.contains("month") || Regex("\\bmos?\\b").containsMatchIn(t) -> Periodicity.MONTH to n
            t.contains("год") || Regex("\\bг\\.?").containsMatchIn(t) || t.contains("year") || Regex("\\byrs?\\b").containsMatchIn(t) -> Periodicity.YEAR to n
            t.contains("дн") || t.contains("дне") || t.contains("day") || Regex("\\bd\\b").containsMatchIn(t) -> Periodicity.DAYS to n
            else -> null
        }
    }

    private fun parseSubscriptions(sheet: SheetData, header: List<String>, today: LocalDate, lang: Lang) =
        parseTable(sheet, header, listOf("Название", "Сумма", "Валюта", "Дата списания"), lang) { line, c, problems ->
            val name = c["Название"]
            if (name.isEmpty()) problems += lang.t("не указано название", "name is missing")
            else if (name.length > Limits.NAME) problems += tooLong(lang)
            if (c["Кто платит"].length > Limits.NAME) problems += tooLong(lang)
            val currency = DebtImport.parseCurrency(c["Валюта"])
            if (currency == null) problems += lang.t("неизвестная валюта «${c["Валюта"]}»", "unknown currency \"${c["Валюта"]}\"")
            val sum = amount(c["Сумма"], currency, problems, "Сумма", lang)
            val period = parsePeriod(c["Период"])
            if (period == null) problems += lang.t("непонятный период «${c["Период"]}»", "unrecognized period \"${c["Период"]}\"")
            // «Дата начала» хранит исходную дату расписания; «Дата списания» в выгрузке — ближайшее списание
            val anchorText = c["Дата начала"].ifEmpty { c["Дата списания"] }
            val anchor = DebtImport.parseDate(anchorText)
            if (anchor == null) problems += lang.t("некорректная дата списания «$anchorText»", "invalid charge date \"$anchorText\"")
            val payerText = c["Кто платит"]
            val payer = when (payerText.lowercase()) { "", "я", "me", "i", "myself", "мной", "сам" -> null; else -> payerText }
            val state = when (c["Статус"].lowercase()) {
                "на паузе", "пауза", "paused", "pause", "on hold" -> ImportSubState.PAUSED
                "отменена", "отменено", "cancelled", "canceled" -> ImportSubState.CANCELLED
                else -> ImportSubState.ACTIVE
            }
            if (problems.isEmpty()) ImportSubscription(line, name, payer, sum!!, currency!!, period!!.first, period.second, anchor!!, state, c["Способ оплаты"].ifEmpty { null }, c["Комментарий"].ifEmpty { null }) else null
        }

    private fun parseShares(sheet: SheetData, header: List<String>, lang: Lang) =
        parseTable(sheet, header, listOf("Подписка", "Участник", "Период", "Доля", "Валюта"), lang) { line, c, problems ->
            if (c["Подписка"].isEmpty()) problems += lang.t("не указана подписка", "subscription is missing")
            if (c["Участник"].isEmpty()) problems += lang.t("не указан участник", "participant is missing")
            else if (c["Участник"].length > Limits.NAME) problems += tooLong(lang)
            val currency = DebtImport.parseCurrency(c["Валюта"])
            if (currency == null) problems += lang.t("неизвестная валюта «${c["Валюта"]}»", "unknown currency \"${c["Валюта"]}\"")
            val period = DebtImport.parseDate(c["Период"])
            if (period == null) problems += lang.t("некорректный период «${c["Период"]}»", "invalid period \"${c["Период"]}\"")
            val sum = amount(c["Доля"], currency, problems, "Доля", lang)
            val paid = c["Статус"].lowercase().let { it == "оплачено" || it == "оплачен" || it == "да" || it == "paid" || it == "yes" }
            if (problems.isEmpty()) ImportShare(line, c["Подписка"], c["Участник"], period!!, sum!!, currency!!, paid) else null
        }

    private fun parsePeople(sheet: SheetData, header: List<String>, lang: Lang) =
        parseTable(sheet, header, listOf("Имя"), lang) { line, c, problems ->
            if (c["Имя"].isEmpty()) problems += lang.t("не указано имя", "name is missing")
            else if (c["Имя"].length > Limits.NAME) problems += tooLong(lang)
            if (problems.isEmpty()) ImportPerson(line, c["Имя"], c["Телефон"].take(Limits.PHONE).ifEmpty { null }, c["Заметка"].take(Limits.NOTE).ifEmpty { null }) else null
        }
}
