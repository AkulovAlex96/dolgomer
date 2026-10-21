package app.kvity.exchange

import app.kvity.domain.Direction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    @Test fun `writes bom, semicolons and decimal comma`() {
        val t = Table("T", listOf("a", "b"), listOf(listOf(cell("x"), Cell.Num(BigDecimal("23.50")))))
        assertEquals("﻿a;b\r\nx;23,5\r\n", Csv.write(t))
    }

    @Test fun `quotes fields with delimiter, quotes and newlines`() {
        val t = Table("T", listOf("h"), listOf(listOf(cell("a;b")), listOf(cell("say \"hi\"")), listOf(cell("l1\nl2"))))
        val parsed = Csv.parse(Csv.write(t))
        assertEquals(listOf(listOf("h"), listOf("a;b"), listOf("say \"hi\""), listOf("l1\nl2")), parsed)
    }

    @Test fun `neutralises spreadsheet formulas`() {
        val t = Table("T", listOf("h"), listOf(listOf(cell("=SUM(A1)"))))
        assertTrue(Csv.write(t).contains("'=SUM(A1)"))
    }

    @Test fun `parse detects delimiter and handles crlf and blank lines`() {
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), Csv.parse("a,b\r\n1,2\r\n\r\n"))
        assertEquals(listOf(listOf("a", "b")), Csv.parse("a;b"))
        assertEquals(listOf(listOf("a", "b")), Csv.parse("﻿a;b"))
    }
}

class XlsxTest {
    private fun unzip(bytes: ByteArray): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                result[e.name] = String(z.readBytes(), Charsets.UTF_8)
            }
        }
        return result
    }

    @Test fun `writes valid package with sheets, numbers and escaped text`() {
        val out = ByteArrayOutputStream()
        Xlsx.write(
            out,
            listOf(
                Table("Долги", listOf("Имя", "Сумма"), listOf(listOf(cell("A & <B>"), Cell.Num(BigDecimal("1500.5"))))),
                Table("Долги", listOf("X"), emptyList()),
            ),
        )
        val files = unzip(out.toByteArray())
        assertTrue(files.keys.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml")))
        val sheet1 = files.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet1.contains("A &amp; &lt;B&gt;"))
        assertTrue(sheet1.contains("<c r=\"B2\"><v>1500.5</v></c>"))
        // имена листов уникальны
        val wb = files.getValue("xl/workbook.xml")
        assertTrue(wb.contains("name=\"Долги\""))
        assertTrue(wb.contains("name=\"Долги 2\""))
    }

    @Test fun `column names`() {
        assertEquals("A", Xlsx.columnName(0))
        assertEquals("Z", Xlsx.columnName(25))
        assertEquals("AA", Xlsx.columnName(26))
        assertEquals("AB", Xlsx.columnName(27))
    }
}

class DebtImportTest {
    private val today = LocalDate.of(2026, 10, 21)

    @Test fun `imports rows by header names in any order`() {
        val csv = "Сумма;Контрагент;Валюта;Направление\n50к;Иван;RUB;мне должны\n1500,50;Мария;usd;я должен"
        val r = DebtImport.parse(csv, today)
        assertTrue(r.errors.isEmpty())
        assertEquals(2, r.rows.size)
        with(r.rows[0]) {
            assertEquals("Иван", counterparty); assertEquals(Direction.OWED_TO_ME, direction)
            assertEquals(5_000_000L, principalMinor); assertEquals(today, startDate); assertNull(dueDate)
        }
        assertEquals(150_050L, r.rows[1].principalMinor)
        assertEquals("USD", r.rows[1].currency)
    }

    @Test fun `parses dates payments and notes`() {
        val csv = "Контрагент;Направление;Валюта;Сумма;Выплачено;Дата выдачи;Срок;Комментарий\nИван;мне должны;₽;100 000;30к;01.09.2026;2026-12-01;заём"
        val r = DebtImport.parse(csv, today).rows.single()
        assertEquals(10_000_000L, r.principalMinor)
        assertEquals(3_000_000L, r.paidMinor)
        assertEquals(LocalDate.of(2026, 9, 1), r.startDate)
        assertEquals(LocalDate.of(2026, 12, 1), r.dueDate)
        assertEquals("RUB", r.currency)
        assertEquals("заём", r.note)
    }

    @Test fun `zero paid amount from export is accepted`() {
        val csv = "Контрагент;Направление;Валюта;Сумма;Выплачено\nИван;мне должны;RUB;100;0\nМария;я должен;RUB;100;0,00\nПётр;я должен;RUB;100;0 ₽"
        val r = DebtImport.parse(csv, today)
        assertTrue(r.errors.isEmpty())
        assertEquals(listOf(0L, 0L, 0L), r.rows.map { it.paidMinor })
    }

    @Test fun `positional columns without header`() {
        val r = DebtImport.parse("Иван;я должен;KZT;5000", today)
        assertEquals(1, r.rows.size)
        assertEquals(Direction.I_OWE, r.rows[0].direction)
    }

    @Test fun `reports errors with line numbers and keeps valid rows`() {
        val csv = "Контрагент;Направление;Валюта;Сумма;Выплачено\nИван;мне должны;RUB;100;\n;мне должны;RUB;100;\nМария;кто-то;RUB;abc;500\nПётр;я должен;RUB;100;200"
        val r = DebtImport.parse(csv, today)
        assertEquals(1, r.rows.size)
        assertEquals(listOf(3, 4, 5), r.errors.map { it.line })
        assertTrue(r.errors[0].message.contains("контрагент"))
        assertTrue(r.errors[1].message.contains("направление") && r.errors[1].message.contains("сумма"))
        assertTrue(r.errors[2].message.contains("больше"))
    }

    @Test fun `missing required column and empty file`() {
        assertTrue(DebtImport.parse("Контрагент;Валюта\nИван;RUB", today).errors.single().message.contains("Направление"))
        assertEquals(1, DebtImport.parse("", today).errors.size)
    }

    @Test fun `template parses back without errors`() {
        val r = DebtImport.parse(Csv.write(DebtImport.template()), today)
        assertTrue(r.errors.isEmpty())
        assertEquals(2, r.rows.size)
    }

    @Test fun `direction and currency aliases`() {
        assertEquals(Direction.OWED_TO_ME, DebtImport.parseDirection("Мне должны"))
        assertEquals(Direction.I_OWE, DebtImport.parseDirection("я должен"))
        assertEquals("KZT", DebtImport.parseCurrency("₸"))
        assertNull(DebtImport.parseCurrency("рубли и копейки"))
    }
}
