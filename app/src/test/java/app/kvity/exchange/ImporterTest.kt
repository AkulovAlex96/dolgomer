package app.kvity.exchange

import app.kvity.domain.Direction
import app.kvity.domain.Periodicity
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImporterTest {
    private val today = LocalDate.of(2026, 10, 21)

    private fun n(v: String) = Cell.Num(BigDecimal(v))

    private val tables = listOf(
        Table(
            "Долги", DebtColumns.IMPORTABLE + listOf("Остаток", "Статус"),
            listOf(listOf(cell("Иван"), cell("мне должны"), cell("RUB"), n("50000"), n("10000"), cell("01.09.2026"), cell("01.12.2026"), cell("заём"), n("40000"), cell("в срок"))),
        ),
        Table(
            "Платежи", listOf("Контрагент", "Направление", "Валюта", "Дата", "Сумма", "Комментарий"),
            listOf(listOf(cell("Иван"), cell("мне должны"), cell("RUB"), cell("15.10.2026"), n("5000"), Cell.Empty)),
        ),
        Table(
            "Подписки", listOf("Название", "Кто платит", "Сумма", "Валюта", "Период", "Дата списания", "Статус", "Способ оплаты", "Комментарий"),
            listOf(
                listOf(cell("ChatGPT"), cell("я"), n("23.5"), cell("USD"), cell("месяц"), cell("02.11.2026"), cell("активна"), Cell.Empty, Cell.Empty),
                listOf(cell("Netflix"), cell("Мария"), n("9.99"), cell("USD"), cell("каждые 2 мес."), cell("05.11.2026"), cell("на паузе"), Cell.Empty, Cell.Empty),
            ),
        ),
        Table(
            "Доли в подписках", listOf("Подписка", "Участник", "Период", "Доля", "Валюта", "Статус"),
            listOf(
                listOf(cell("ChatGPT"), cell("Антон"), cell("02.10.2026"), n("23.5"), cell("USD"), cell("оплачено")),
                listOf(cell("ChatGPT"), cell("Антон"), cell("02.11.2026"), n("23.5"), cell("USD"), cell("ожидается")),
            ),
        ),
        Table("Контрагенты", listOf("Имя", "Телефон", "Заметка", "Баланс"), listOf(listOf(cell("Иван"), cell("+7 900 000-00-00"), Cell.Empty, cell("+40000 RUB")))),
    )

    private fun xlsx(): ByteArray = ByteArrayOutputStream().also { Xlsx.write(it, tables) }.toByteArray()

    @Test fun `xlsx written by export is read back completely`() {
        val b = Importer.parse(xlsx(), today)
        assertTrue(b.errors.joinToString { "${it.sheet}:${it.line}:${it.message}" }, b.errors.isEmpty())
        assertEquals(1, b.debts.size)
        assertEquals(1, b.payments.size)
        assertEquals(2, b.subscriptions.size)
        assertEquals(2, b.shares.size)
        assertEquals(1, b.people.size)

        val d = b.debts[0]
        assertEquals(5_000_000L, d.principalMinor); assertEquals(1_000_000L, d.paidMinor)
        assertEquals(LocalDate.of(2026, 12, 1), d.dueDate)
        assertEquals(Direction.OWED_TO_ME, b.payments[0].direction)
        assertEquals(500_000L, b.payments[0].amountMinor)

        val chat = b.subscriptions.first { it.name == "ChatGPT" }
        assertEquals(null, chat.payerName); assertEquals(2350L, chat.amountMinor); assertEquals(Periodicity.MONTH, chat.periodicity)
        val netflix = b.subscriptions.first { it.name == "Netflix" }
        assertEquals("Мария", netflix.payerName); assertEquals(2, netflix.interval); assertEquals(ImportSubState.PAUSED, netflix.state)

        assertTrue(b.shares[0].paid); assertEquals(false, b.shares[1].paid)
        assertEquals("+7 900 000-00-00", b.people[0].phone)
    }

    @Test fun `zip of csv files is read like workbook`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            tables.forEach { t ->
                z.putNextEntry(ZipEntry("${t.name}.csv"))
                z.write(Csv.write(t).toByteArray())
                z.closeEntry()
            }
        }
        val b = Importer.parse(out.toByteArray(), today)
        assertTrue(b.errors.isEmpty())
        assertEquals(listOf(1, 1, 2, 2, 1), listOf(b.debts.size, b.payments.size, b.subscriptions.size, b.shares.size, b.people.size))
    }

    @Test fun `single csv with a known header is detected by columns`() {
        val csv = Csv.write(tables[2])
        val b = Importer.parse(csv.toByteArray(), today)
        assertEquals(2, b.subscriptions.size)
        assertEquals(0, b.debts.size)
    }

    @Test fun `headerless csv is treated as debts`() {
        val b = Importer.parse("Иван;я должен;KZT;5000".toByteArray(), today)
        assertEquals(1, b.debts.size)
    }

    @Test fun `unknown tables are ignored and empty import reports an error`() {
        val out = ByteArrayOutputStream()
        Xlsx.write(out, listOf(Table("Лист1", listOf("a", "b"), listOf(listOf(cell("1"), cell("2"))))))
        val b = Importer.parse(out.toByteArray(), today)
        assertEquals(listOf("Лист1"), b.ignoredSheets)
        assertEquals(1, b.errors.size)
    }

    @Test fun `errors carry sheet name and line`() {
        val t = Table("Подписки", listOf("Название", "Сумма", "Валюта", "Дата списания"), listOf(listOf(cell("X"), cell("abc"), cell("USD"), cell("не дата"))))
        val b = Importer.parse(ByteArrayOutputStream().also { Xlsx.write(it, listOf(t)) }.toByteArray(), today)
        assertEquals("Подписки", b.errors.single().sheet)
        assertEquals(2, b.errors.single().line)
        assertTrue(b.errors.single().message.contains("сумма") && b.errors.single().message.contains("дата"))
    }

    @Test fun `garbage file is rejected`() {
        assertEquals(1, Importer.parse(ByteArray(0), today).errors.size)
        assertEquals(1, Importer.parse(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 1, 2, 3), today).errors.size)
    }

    @Test fun `excel serial dates and shared strings`() {
        val sharedXlsx = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                fun put(name: String, c: String) { z.putNextEntry(ZipEntry(name)); z.write(c.toByteArray()); z.closeEntry() }
                put("xl/workbook.xml", """<workbook xmlns:r="x"><sheets><sheet name="Долги" sheetId="1" r:id="rId1"/></sheets></workbook>""")
                put("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId1" Type="t" Target="worksheets/sheet1.xml"/></Relationships>""")
                put("xl/sharedStrings.xml", """<sst><si><t>Контрагент</t></si><si><t>Направление</t></si><si><t>Валюта</t></si><si><t>Сумма</t></si><si><t>Дата выдачи</t></si><si><t>Иван</t></si><si><r><t>мне </t></r><r><t>должны</t></r></si><si><t>RUB</t></si></sst>""")
                put(
                    "xl/worksheets/sheet1.xml",
                    """<worksheet><sheetData>
                    <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c><c r="E1" t="s"><v>4</v></c></row>
                    <row r="2"><c r="A2" t="s"><v>5</v></c><c r="B2" t="s"><v>6</v></c><c r="C2" t="s"><v>7</v></c><c r="D2"><v>1.5E4</v></c><c r="E2"><v>46286</v></c></row>
                    </sheetData></worksheet>""",
                )
            }
        }.toByteArray()
        val b = Importer.parse(sharedXlsx, today)
        assertTrue(b.errors.joinToString { it.message }, b.errors.isEmpty())
        val d = b.debts.single()
        assertEquals("Иван", d.counterparty)
        assertEquals(Direction.OWED_TO_ME, d.direction)
        assertEquals(1_500_000L, d.principalMinor)
        // 46286 дней от 30.12.1899
        assertEquals(LocalDate.of(1899, 12, 30).plusDays(46286), d.startDate)
    }
}
