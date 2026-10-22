package app.kvity

import app.kvity.domain.Limits
import app.kvity.domain.Periodicity
import app.kvity.domain.ScheduleGenerator
import app.kvity.domain.ScheduleRule
import app.kvity.exchange.Importer
import app.kvity.ui.formatFileSize
import app.kvity.ui.settings.parseRate
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RateParserTest {
    @Test fun `accepts ordinary rates with comma or dot`() {
        assertEquals(85.0, parseRate("85")!!, 0.0)
        assertEquals(0.0118, parseRate("0,0118")!!, 1e-9)
        assertEquals(420.5, parseRate(" 420.5 ")!!, 0.0)
    }

    @Test fun `rejects infinity, absurd, zero and negative rates`() {
        assertNull(parseRate("1e999999999"))
        assertNull(parseRate("Infinity"))
        assertNull(parseRate("NaN"))
        assertNull(parseRate("0"))
        assertNull(parseRate("-5"))
        assertNull(parseRate("1e12"))
        assertNull(parseRate("abc"))
        assertNull(parseRate(""))
    }
}

class FileSizeTest {
    @Test fun `formats bytes, kilobytes and megabytes`() {
        assertEquals("22 Б", formatFileSize(22))
        assertEquals("0 Б", formatFileSize(0))
        assertEquals("1,5 КБ", formatFileSize(1536))
        assertEquals("5,0 МБ", formatFileSize(5L * 1024 * 1024))
    }
}

class ScheduleCoverageTest {
    private val start = LocalDate.of(2026, 10, 22)

    @Test fun `ordinary schedule covers the debt`() {
        assertTrue(ScheduleGenerator.covers(ScheduleRule(Periodicity.MONTH, 1_000_000, start), 5_000_000))
    }

    @Test fun `schedule that would need more than the cap does not cover the debt`() {
        // 1 000 000 ₽ платежами по 1 ₽ — больше MAX_ITEMS платежей
        assertFalse(ScheduleGenerator.covers(ScheduleRule(Periodicity.MONTH, 100, start), 100_000_000))
    }

    @Test fun `exactly the cap still covers`() {
        assertTrue(ScheduleGenerator.covers(ScheduleRule(Periodicity.MONTH, 100, start), 100L * ScheduleGenerator.MAX_ITEMS))
    }
}

class ImportValidationTest {
    private val today = LocalDate.of(2026, 10, 22)
    private val head = "Контрагент;Направление;Валюта;Сумма;Выплачено;Дата выдачи;Срок;Комментарий\r\n"

    private fun parse(vararg rows: String) = Importer.parse((head + rows.joinToString("") { it + "\r\n" }).toByteArray(), today)

    @Test fun `rejects names longer than the limit`() {
        val b = parse("${"X".repeat(Limits.NAME + 1)};мне должны;RUB;100;;01.09.2026;;c", "${"Y".repeat(Limits.NAME)};мне должны;RUB;100;;01.09.2026;;c")
        assertEquals(1, b.debts.size)
        assertEquals(1, b.errors.size)
        assertTrue(b.errors.single().message.contains("слишком длинное имя"))
    }

    @Test fun `accepts real ISO currencies and rejects made-up or non-currency codes`() {
        val b = parse(
            "A;мне должны;PLN;100;;01.09.2026;;c",
            "B;мне должны;XXX;100;;01.09.2026;;c",
            "C;мне должны;ZZZ;100;;01.09.2026;;c",
            "D;мне должны;XTS;100;;01.09.2026;;c",
        )
        assertEquals(listOf("A"), b.debts.map { it.counterparty })
        assertEquals(3, b.errors.size)
    }
}

class ImportTextDecodingTest {
    private val head = "Контрагент;Направление;Валюта;Сумма\r\n"
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun `windows-1251 csv is read correctly`() {
        val bytes = (head + "Иван;мне должны;RUB;300\r\n").toByteArray(charset("windows-1251"))
        val b = Importer.parse(bytes, today)
        assertEquals(listOf("Иван"), b.debts.map { it.counterparty })
        assertTrue(b.errors.isEmpty())
    }

    @Test fun `utf-8 csv with bom is read correctly`() {
        val b = Importer.parse(("﻿" + head + "Иван;мне должны;RUB;300\r\n").toByteArray(), today)
        assertEquals(listOf("Иван"), b.debts.map { it.counterparty })
    }

    @Test fun `binary garbage is not treated as csv`() {
        val junk = ByteArray(5000) { (it * 31 % 251).toByte() }
        val b = Importer.parse(junk, today)
        assertEquals(1, b.errors.size)
        assertTrue(b.errors.single().message.startsWith("Не удалось прочитать файл"))
    }
}
