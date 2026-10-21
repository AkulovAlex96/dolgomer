package app.kvity.exchange

import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.zip.ZipInputStream

/** Лист книги: имя и строки (первая строка обычно заголовок). */
class SheetData(val name: String, val rows: List<List<String>>)

/**
 * Чтение .xlsx (в том числе сохранённых Excel и Google Таблицами): общие строки, встроенные строки, числа.
 * Файл разбирается без внешних библиотек, формулы читаются как их последнее вычисленное значение.
 */
object XlsxReader {
    private val sheetTag = Regex("<sheet\\b[^>]*>")
    private val relTag = Regex("<Relationship\\b[^>]*>")
    private val siTag = Regex("<si\\b[^>]*?(?:/>|>(.*?)</si>)", RegexOption.DOT_MATCHES_ALL)
    private val rowTag = Regex("<row\\b[^>]*?(?:/>|>(.*?)</row>)", RegexOption.DOT_MATCHES_ALL)
    private val cellTag = Regex("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL)
    private val textRun = Regex("<t\\b[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
    private val valueTag = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)
    private val inlineTag = Regex("<is>(.*?)</is>", RegexOption.DOT_MATCHES_ALL)

    fun isXlsx(bytes: ByteArray): Boolean = entries(bytes).containsKey("xl/workbook.xml")

    private fun entries(bytes: ByteArray): Map<String, ByteArray> {
        val map = linkedMapOf<String, ByteArray>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    map[e.name] = z.readBytes()
                }
            }
        }
        return map
    }

    fun read(bytes: ByteArray): List<SheetData> {
        val files = entries(bytes)
        val workbook = files["xl/workbook.xml"]?.toString(Charsets.UTF_8) ?: return emptyList()
        val rels = files["xl/_rels/workbook.xml.rels"]?.toString(Charsets.UTF_8).orEmpty()
        val targets = relTag.findAll(rels).associate { attr(it.value, "Id").orEmpty() to attr(it.value, "Target").orEmpty() }
        val shared = files["xl/sharedStrings.xml"]?.toString(Charsets.UTF_8)?.let(::sharedStrings).orEmpty()

        return sheetTag.findAll(workbook).mapNotNull { m ->
            val name = unescape(attr(m.value, "name").orEmpty())
            val rid = attr(m.value, "r:id") ?: attr(m.value, "id")
            val target = targets[rid]?.removePrefix("/")?.let { if (it.startsWith("xl/")) it else "xl/$it" } ?: return@mapNotNull null
            val xml = files[target]?.toString(Charsets.UTF_8) ?: return@mapNotNull null
            SheetData(name, sheetRows(xml, shared))
        }.toList()
    }

    private fun sharedStrings(xml: String): List<String> =
        siTag.findAll(xml).map { si -> textRun.findAll(si.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) } }.toList()

    private fun sheetRows(xml: String, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        for (row in rowTag.findAll(xml)) {
            val cells = mutableMapOf<Int, String>()
            var next = 0
            for (c in cellTag.findAll(row.groupValues[1])) {
                val attrs = c.groupValues[1]
                val ref = attr(attrs, "r")
                val col = ref?.takeWhile { it.isLetter() }?.let(::columnIndex) ?: next
                next = col + 1
                val type = attr(attrs, "t")
                val body = c.groupValues[2]
                val raw = valueTag.find(body)?.groupValues?.get(1)
                val text = when (type) {
                    "s" -> raw?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                    "inlineStr" -> inlineTag.find(body)?.groupValues?.get(1)
                        ?.let { s -> textRun.findAll(s).joinToString("") { unescape(it.groupValues[1]) } }.orEmpty()
                    "str", "e" -> unescape(raw.orEmpty())
                    "b" -> if (raw == "1") "да" else "нет"
                    else -> raw?.let { v -> runCatching { BigDecimal(v.trim()).stripTrailingZeros().toPlainString() }.getOrDefault(v) }.orEmpty()
                }
                cells[col] = text
            }
            val width = (cells.keys.maxOrNull() ?: -1) + 1
            rows += List(width) { cells[it].orEmpty() }
        }
        return rows.dropLastWhile { r -> r.all { it.isBlank() } }
    }

    private fun attr(tag: String, name: String): String? =
        Regex("(?:^|\\s)${Regex.escape(name)}=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)

    private fun columnIndex(letters: String): Int =
        letters.uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1

    private fun unescape(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
}
