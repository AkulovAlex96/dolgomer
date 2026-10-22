package app.kvity.notify

import app.kvity.data.CalendarEvent
import app.kvity.data.EventKind
import app.kvity.data.MoneyFlow
import app.kvity.data.ReminderItem
import app.kvity.data.ReminderType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class NotifierSummaryTest {
    private fun item(amount: Long, currency: String = "RUB") = ReminderItem(
        CalendarEvent(
            date = LocalDate.of(2026, 10, 24), kind = EventKind.DEBT, flow = MoneyFlow.IN, title = "t", subtitle = null,
            amountMinor = amount, currency = currency, overdue = false, refId = "r",
        ),
        ReminderType.UPCOMING, 3,
    )

    @Test fun `sums amounts of the same currency`() {
        assertEquals("3 платежа: 4 777 ₽", Notifier.summary(listOf(item(300_000), item(100_000), item(77_700))))
    }

    @Test fun `lists each currency, largest first`() {
        assertEquals("2 платежа: 3 000 ₽ + 50 $", Notifier.summary(listOf(item(5_000, "USD"), item(300_000))))
    }

    @Test fun `plural forms`() {
        val forms = listOf(1, 2, 4, 5, 11, 12, 14, 21, 22, 25, 101, 111).associateWith { Notifier.plural(it) }
        assertEquals("платёж", forms[1]); assertEquals("платежа", forms[2]); assertEquals("платежа", forms[4])
        assertEquals("платежей", forms[5]); assertEquals("платежей", forms[11]); assertEquals("платежей", forms[12])
        assertEquals("платежей", forms[14]); assertEquals("платёж", forms[21]); assertEquals("платежа", forms[22])
        assertEquals("платежей", forms[25]); assertEquals("платёж", forms[101]); assertEquals("платежей", forms[111])
    }
}
