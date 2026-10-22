package app.kvity.exchange

import app.kvity.data.SubscriptionEntity
import app.kvity.data.SubscriptionState
import app.kvity.data.SubscriptionView
import app.kvity.domain.Periodicity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSubscriptionsTest {
    private val today = LocalDate.of(2026, 10, 21)

    private fun sub(
        anchor: LocalDate, periodicity: Periodicity = Periodicity.MONTH, state: SubscriptionState = SubscriptionState.ACTIVE,
    ) = SubscriptionView(
        SubscriptionEntity(
            id = "s", name = "ChatGPT", amountMinor = 2350, currency = "USD", periodicity = periodicity,
            anchorDate = anchor, state = state, createdAt = 0,
        ),
        payerName = null, shares = emptyList(),
    )

    private fun row(view: SubscriptionView): Map<String, String> {
        val t = ExportBuilder.build(ExportOptions(tables = setOf(ExportTable.SUBSCRIPTIONS)), emptyList(), listOf(view), emptyList(), today).single()
        val cells = t.rows.single().map { (it as? Cell.Text)?.value.orEmpty() }
        return t.header.zip(cells).toMap()
    }

    @Test fun `charge date is the next one, start date keeps the anchor`() {
        val r = row(sub(LocalDate.of(2026, 10, 2)))
        assertEquals("02.11.2026", r["Дата списания"])
        assertEquals("02.10.2026", r["Дата начала"])
    }

    @Test fun `charge today counts as next`() {
        assertEquals("21.10.2026", row(sub(LocalDate.of(2026, 9, 21)))["Дата списания"])
    }

    @Test fun `future anchor is its own next charge`() {
        assertEquals("01.12.2026", row(sub(LocalDate.of(2026, 12, 1)))["Дата списания"])
    }

    @Test fun `paused subscription has no next charge but keeps start date`() {
        val r = row(sub(LocalDate.of(2026, 10, 2), state = SubscriptionState.PAUSED))
        assertEquals("", r["Дата списания"])
        assertEquals("02.10.2026", r["Дата начала"])
    }

    @Test fun `export and import keep the original anchor`() {
        val tables = ExportBuilder.build(ExportOptions(tables = setOf(ExportTable.SUBSCRIPTIONS)), emptyList(), listOf(sub(LocalDate.of(2026, 1, 31))), emptyList(), today)
        val bytes = java.io.ByteArrayOutputStream().also { Xlsx.write(it, tables) }.toByteArray()
        val s = Importer.parse(bytes, today).subscriptions.single()
        assertEquals(LocalDate.of(2026, 1, 31), s.anchor)
    }

    @Test fun `old files with only charge date still import`() {
        val t = Table("Подписки", listOf("Название", "Сумма", "Валюта", "Дата списания"), listOf(listOf(cell("X"), Cell.Num(java.math.BigDecimal("5")), cell("USD"), cell("05.11.2026"))))
        val bytes = java.io.ByteArrayOutputStream().also { Xlsx.write(it, listOf(t)) }.toByteArray()
        assertEquals(LocalDate.of(2026, 11, 5), Importer.parse(bytes, today).subscriptions.single().anchor)
        assertTrue(Importer.parse(bytes, today).errors.isEmpty())
    }
}
