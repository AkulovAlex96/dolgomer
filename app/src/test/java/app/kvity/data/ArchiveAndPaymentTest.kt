package app.kvity.data

import app.kvity.domain.ArchiveRules
import app.kvity.domain.DebtCalculator
import app.kvity.domain.DebtStatus
import app.kvity.domain.DebtSummary
import app.kvity.domain.Direction
import app.kvity.domain.PaymentAmount
import app.kvity.domain.PlannedPayment
import app.kvity.domain.TermType
import app.kvity.exchange.ExportBuilder
import app.kvity.exchange.ExportOptions
import app.kvity.exchange.ExportTable
import app.kvity.exchange.Importer
import app.kvity.exchange.Table
import app.kvity.exchange.Xlsx
import app.kvity.exchange.Cell
import app.kvity.exchange.cell
import app.kvity.ui.dashboard.DashboardViewModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveAndPaymentTest {
    private val today = LocalDate.of(2026, 11, 5)

    // ---- архивация контрагента ----

    @Test fun `person with open balance cannot be archived`() {
        assertTrue(ArchiveRules.archiveBlockReason(mapOf("RUB" to 100L), 0) != null)
        assertTrue(ArchiveRules.archiveBlockReason(mapOf("RUB" to -100L), 0) != null)
    }

    @Test fun `person with active shares cannot be archived`() {
        assertTrue(ArchiveRules.archiveBlockReason(emptyMap(), 1) != null)
    }

    @Test fun `person without debts can be archived`() {
        assertNull(ArchiveRules.archiveBlockReason(emptyMap(), 0))
        assertNull(ArchiveRules.archiveBlockReason(mapOf("RUB" to 0L), 0))
    }

    @Test fun `clean text trims, limits and nulls blanks`() {
        assertNull(ArchiveRules.cleanText("   ", 10))
        assertNull(ArchiveRules.cleanText(null, 10))
        assertEquals("Карта", ArchiveRules.cleanText("  Карта ", 10))
        assertEquals("abc", ArchiveRules.cleanText("abcdef", 3))
    }

    // ---- архивные долги на главном экране ----

    private fun view(id: String, archived: Boolean, remaining: Long = 5000, due: LocalDate = today.plusDays(10)) = DebtView(
        debt = DebtEntity(
            id = id, counterpartyId = "c", direction = Direction.OWED_TO_ME, principalMinor = remaining, currency = "RUB",
            startDate = today.minusDays(5), termType = TermType.ONE_TIME, dueDate = due, archived = archived, createdAt = 0,
        ),
        counterpartyName = "Иван", counterpartyPhone = null, payments = emptyList(),
        summary = DebtCalculator.summarize(remaining, DebtCalculator.oneTime(remaining, due), emptyList(), today),
    )

    @Test fun `archived debts go to their own list and do not affect totals`() {
        val state = DashboardViewModel.buildState(listOf(view("a", false, 5000), view("b", true, 7000)), emptyList(), today)
        assertEquals(listOf("a"), state.debts.map { it.debt.id })
        assertEquals(listOf("b"), state.archivedDebts.map { it.debt.id })
        assertEquals(mapOf("RUB" to 5000L), state.owedToMeTotal)
        // 7000 из архивного долга не попало и в периоды
        assertEquals(5000L, state.owedToMe.values.sumOf { it.values.sum() })
    }

    // ---- способ оплаты и комментарий ----

    @Test fun `old backup json without new fields is still readable`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val p = json.decodeFromString(PaymentEntity.serializer(), """{"id":"p","debtId":"d","date":"2026-10-01","amountMinor":100,"createdAt":1}""")
        assertNull(p.paymentMethod)
        val c = json.decodeFromString(CounterpartyEntity.serializer(), """{"id":"c","name":"Иван","createdAt":1}""")
        assertFalse(c.archived)
    }

    @Test fun `backup round trip keeps payment method and archived person`() {
        val data = BackupData(
            createdAt = 1L, counterparties = listOf(CounterpartyEntity("c", "Иван", createdAt = 1L, archived = true)),
            debts = emptyList(),
            payments = listOf(PaymentEntity("p", "d", today, 100, "комментарий", 1L, paymentMethod = "Карта Tinkoff")),
            subscriptions = emptyList(), shares = emptyList(), sharePayments = emptyList(), attachments = emptyList(),
            templates = emptyList(), reminderLog = emptyList(), rates = emptyList(),
        )
        val out = ByteArrayOutputStream()
        BackupCodec.write(out, data, emptyList(), null)
        val back = BackupCodec.read(ByteArrayInputStream(out.toByteArray()), null, Files.createTempDirectory("kvity-test").toFile()).data
        assertEquals(data, back)
    }

    private fun paymentTable(header: List<String>, row: List<Cell>) = Table("Платежи", header, listOf(row))

    private fun parse(vararg tables: Table) =
        Importer.parse(ByteArrayOutputStream().also { Xlsx.write(it, tables.toList()) }.toByteArray(), today)

    @Test fun `import reads payment method and works without that column`() {
        val base = listOf("Контрагент", "Направление", "Валюта", "Дата", "Сумма", "Комментарий")
        val baseRow = listOf(cell("Иван"), cell("мне должны"), cell("RUB"), cell("15.10.2026"), Cell.Num(BigDecimal("5000")), cell("часть"))
        val withMethod = parse(paymentTable(base + "Способ оплаты", baseRow + cell("Карта Tinkoff"))).payments.single()
        assertEquals("Карта Tinkoff", withMethod.method)
        assertEquals("часть", withMethod.note)
        val old = parse(paymentTable(base, baseRow)).payments.single()
        assertNull(old.method)
        assertEquals("часть", old.note)
    }

    @Test fun `export of payments includes payment method column`() {
        val debt = view("a", false)
        val v = debt.copy(payments = listOf(PaymentEntity("p", "a", today, 100, "заметка", 0, paymentMethod = "Наличные")))
        val t = ExportBuilder.build(ExportOptions(tables = setOf(ExportTable.PAYMENTS)), listOf(v), emptyList(), emptyList(), today).single()
        val i = t.header.indexOf("Способ оплаты")
        assertTrue(i >= 0)
        assertEquals("Наличные", (t.rows.single()[i] as Cell.Text).value)
    }

    // ---- правка платежа: график пересчитывается из платежей ----

    @Test fun `editing a payment amount and link changes allocation`() {
        val planned = listOf(PlannedPayment(today.plusDays(1), 1000, "p1"), PlannedPayment(today.plusDays(31), 1000, "p2"))
        // было: платёж 1000 без привязки закрывает самый старый
        val before = DebtCalculator.allocate(planned, listOf(PaymentAmount(1000)))
        assertEquals(1000L, before[0].paidMinor)
        // стало: тот же платёж привязан ко второму, а сумма уменьшена до 600
        val after = DebtCalculator.allocate(planned, listOf(PaymentAmount(600, "p2")))
        assertEquals(0L, after[0].paidMinor)
        assertEquals(600L, after[1].paidMinor)
        val summary = DebtCalculator.summarize(2000, planned, listOf(600L), today, plannedIds = listOf("p2"))
        assertEquals(1400L, summary.remainingMinor)
        assertEquals(DebtStatus.DUE_SOON, summary.status)
    }
}
