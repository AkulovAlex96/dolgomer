package app.kvity.exchange

import app.kvity.domain.Direction
import app.kvity.domain.Periodicity
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeEnglishTest {
    private val today = LocalDate.of(2026, 11, 5)

    private fun xlsx(vararg t: Table) = ByteArrayOutputStream().also { Xlsx.write(it, t.toList()) }.toByteArray()

    @Test fun `english debts csv is imported`() {
        val csv = "Counterparty;Direction;Currency;Amount;Paid;Start date;Due date;Note\nJohn;owed to me;USD;100;10;01.09.2026;01.12.2026;hi\nMary;I owe;EUR;5;;;;"
        val r = DebtImport.parse(csv, today)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        assertEquals(2, r.rows.size)
        assertEquals(Direction.OWED_TO_ME, r.rows[0].direction)
        assertEquals(10_00L, r.rows[0].paidMinor)
        assertEquals(LocalDate.of(2026, 9, 1), r.rows[0].startDate)
        assertEquals(LocalDate.of(2026, 12, 1), r.rows[0].dueDate)
        assertEquals("hi", r.rows[0].note)
        assertEquals(Direction.I_OWE, r.rows[1].direction)
    }

    @Test fun `english template round-trips`() {
        val r = DebtImport.parse(Csv.write(DebtImport.template(Lang.EN)), today)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        assertEquals(2, r.rows.size)
    }

    @Test fun `directions in both languages`() {
        assertEquals(Direction.OWED_TO_ME, DebtImport.parseDirection("Owed to me"))
        assertEquals(Direction.I_OWE, DebtImport.parseDirection("I owe"))
        assertEquals(Direction.I_OWE, DebtImport.parseDirection("я должен"))
    }

    @Test fun `english error messages`() {
        val e = DebtImport.parse("Counterparty;Currency\nJohn;USD", today, Lang.EN).errors.single().message
        assertTrue(e, e.contains("Direction"))
        assertTrue(e.startsWith("Missing"))
    }

    @Test fun `english xlsx with all sheets is imported`() {
        val tables = listOf(
            Table("Debts", DebtColumns.importable(Lang.EN), listOf(listOf(cell("John"), cell("owed to me"), cell("RUB"), cell("100"), Cell.Empty, cell("01.09.2026"), Cell.Empty, Cell.Empty))),
            Table("Payments", listOf("Counterparty", "Direction", "Currency", "Date", "Amount", "Note", "Payment method"),
                listOf(listOf(cell("John"), cell("owed to me"), cell("RUB"), cell("02.09.2026"), cell("10"), Cell.Empty, cell("cash")))),
            Table("Subscriptions", listOf("Name", "Payer", "Amount", "Currency", "Period", "Next charge date", "Status", "Payment method", "Note", "Start date"),
                listOf(listOf(cell("Music"), cell("me"), cell("5"), cell("USD"), cell("every 2 mo."), cell("01.12.2026"), cell("paused"), Cell.Empty, Cell.Empty, cell("01.10.2026")))),
            Table("Subscription shares", listOf("Subscription", "Participant", "Period", "Share", "Currency", "Status"),
                listOf(listOf(cell("Music"), cell("Ann"), cell("01.10.2026"), cell("2"), cell("USD"), cell("paid")))),
            Table("Counterparties", listOf("Name", "Phone", "Note", "Balance"), listOf(listOf(cell("John"), cell("123"), cell("friend"), Cell.Empty))),
        )
        val b = Importer.parse(xlsx(*tables.toTypedArray()), today, Lang.EN)
        assertTrue(b.errors.toString(), b.errors.isEmpty())
        assertEquals(1, b.debts.size)
        assertEquals(1, b.payments.size)
        assertEquals("cash", b.payments[0].method)
        val s = b.subscriptions.single()
        assertEquals(ImportSubState.PAUSED, s.state)
        assertEquals(Periodicity.MONTH to 2, s.periodicity to s.interval)
        assertEquals(LocalDate.of(2026, 10, 1), s.anchor)
        assertEquals(null, s.payerName)
        assertTrue(b.shares.single().paid)
        assertEquals("friend", b.people.single().note)
        assertEquals("123", b.people.single().phone)
    }

    @Test fun `english export headers and sheet names`() {
        val t = ExportBuilder.build(ExportOptions(), emptyList(), emptyList(), emptyList(), today, Lang.EN)
        assertEquals(listOf("Debts", "Payments", "Subscriptions", "Subscription shares", "Counterparties"), t.map { it.name })
        assertEquals("Counterparty", t[0].header[0])
        // русский вариант не изменился
        val ru = ExportBuilder.build(ExportOptions(), emptyList(), emptyList(), emptyList(), today)
        assertEquals("Долги", ru[0].name)
        assertEquals("Контрагент", ru[0].header[0])
    }
}
