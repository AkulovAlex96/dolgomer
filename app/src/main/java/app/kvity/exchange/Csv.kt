package app.kvity.exchange

import java.math.BigDecimal

/** Значение ячейки таблицы для экспорта. */
sealed interface Cell {
    data class Text(val value: String) : Cell
    data class Num(val value: BigDecimal) : Cell
    data object Empty : Cell
}

fun cell(value: String?): Cell = if (value.isNullOrEmpty()) Cell.Empty else Cell.Text(value)

class Table(val name: String, val header: List<String>, val rows: List<List<Cell>>)

object Csv {
    const val BOM = "﻿"

    /** CSV для Excel: UTF-8 с BOM, разделитель «;», десятичная запятая. */
    fun write(table: Table, delimiter: Char = ';'): String {
        val sb = StringBuilder(BOM)
        sb.append(line(table.header.map { Cell.Text(it) }, delimiter))
        table.rows.forEach { sb.append(line(it, delimiter)) }
        return sb.toString()
    }

    private fun line(cells: List<Cell>, delimiter: Char): String =
        cells.joinToString(delimiter.toString(), postfix = "\r\n") { escape(text(it), delimiter) }

    private fun text(c: Cell): String = when (c) {
        is Cell.Text -> c.value
        is Cell.Num -> c.value.stripTrailingZeros().toPlainString().replace('.', ',')
        Cell.Empty -> ""
    }

    private fun escape(value: String, delimiter: Char): String {
        // защита от «формул» при открытии в Excel
        val safe = if (value.isNotEmpty() && value[0] in "=@" ) "'$value" else value
        val needsQuotes = safe.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** Разбирает CSV с кавычками. Разделитель определяется по первой строке, если не задан. */
    fun parse(input: String, delimiter: Char? = null): List<List<String>> {
        val text = input.removePrefix(BOM)
        val sep = delimiter ?: detectDelimiter(text)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        fun endField() { row.add(field.toString()); field.setLength(0) }
        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = mutableListOf()
        }
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> when {
                    ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    ch == '"' -> inQuotes = false
                    else -> field.append(ch)
                }
                ch == '"' -> inQuotes = true
                ch == sep -> endField()
                ch == '\r' -> { if (i + 1 < text.length && text[i + 1] == '\n') i++; endRow() }
                ch == '\n' -> endRow()
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    private fun detectDelimiter(text: String): Char {
        val first = text.lineSequence().firstOrNull().orEmpty()
        return listOf(';', ',', '\t').maxByOrNull { d -> first.count { it == d } }?.takeIf { d -> first.contains(d) } ?: ';'
    }
}
