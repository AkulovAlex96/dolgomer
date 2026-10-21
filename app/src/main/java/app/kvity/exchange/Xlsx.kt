package app.kvity.exchange

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Минимальный писатель .xlsx без сторонних библиотек: листы, числа, строки, жирный заголовок. */
object Xlsx {

    fun write(out: OutputStream, tables: List<Table>) {
        require(tables.isNotEmpty()) { "Нужна хотя бы одна таблица" }
        val names = uniqueSheetNames(tables.map { it.name })
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes(tables.size))
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", workbook(names))
            entry("xl/_rels/workbook.xml.rels", workbookRels(tables.size))
            entry("xl/styles.xml", STYLES)
            tables.forEachIndexed { i, t -> entry("xl/worksheets/sheet${i + 1}.xml", sheet(t)) }
        }
    }

    private fun uniqueSheetNames(raw: List<String>): List<String> {
        val used = mutableSetOf<String>()
        return raw.map { n ->
            var base = n.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().ifEmpty { "Лист" }.take(31)
            var name = base
            var k = 2
            while (!used.add(name.lowercase())) { name = base.take(28) + " " + k++ }
            name
        }
    }

    private fun contentTypes(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..n) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("</Types>")
    }

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbook(names: List<String>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        names.forEachIndexed { i, n -> append("""<sheet name="${esc(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..n) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs></styleSheet>"""

    private fun sheet(t: Table) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        append("<cols>")
        t.header.indices.forEach { append("""<col min="${it + 1}" max="${it + 1}" width="${width(t, it)}" customWidth="1"/>""") }
        append("</cols><sheetData>")
        row(this, 1, t.header.map { Cell.Text(it) }, bold = true)
        t.rows.forEachIndexed { i, r -> row(this, i + 2, r, bold = false) }
        append("</sheetData></worksheet>")
    }

    private fun width(t: Table, col: Int): Int {
        val longest = (listOf(t.header[col].length) + t.rows.map { r ->
            when (val c = r.getOrNull(col)) {
                is Cell.Text -> c.value.length
                is Cell.Num -> c.value.toPlainString().length
                else -> 0
            }
        }).max()
        return (longest + 3).coerceIn(10, 60)
    }

    private fun row(sb: StringBuilder, index: Int, cells: List<Cell>, bold: Boolean) {
        sb.append("""<row r="$index">""")
        cells.forEachIndexed { c, cell ->
            val ref = "${columnName(c)}$index"
            val style = if (bold) """ s="1"""" else ""
            when (cell) {
                is Cell.Text -> sb.append("""<c r="$ref" t="inlineStr"$style><is><t xml:space="preserve">${esc(cell.value)}</t></is></c>""")
                is Cell.Num -> sb.append("""<c r="$ref"$style><v>${cell.value.toPlainString()}</v></c>""")
                Cell.Empty -> Unit
            }
        }
        sb.append("</row>")
    }

    fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        do { sb.insert(0, 'A' + n % 26); n = n / 26 - 1 } while (n >= 0)
        return sb.toString()
    }

    private fun esc(s: String): String = buildString {
        for (ch in s) when {
            ch == '&' -> append("&amp;")
            ch == '<' -> append("&lt;")
            ch == '>' -> append("&gt;")
            ch == '"' -> append("&quot;")
            ch.code < 0x20 && ch != '\n' && ch != '\t' && ch != '\r' -> Unit // недопустимые в XML символы
            else -> append(ch)
        }
    }
}
