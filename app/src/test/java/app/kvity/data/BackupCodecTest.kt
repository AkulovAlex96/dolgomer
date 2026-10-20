package app.kvity.data

import app.kvity.domain.Direction
import app.kvity.domain.Periodicity
import app.kvity.domain.TermType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun sample(): BackupData {
        val person = CounterpartyEntity("p1", "Иван", "+7 900", "заметка", 1L)
        val debt = DebtEntity(
            "d1", "p1", Direction.OWED_TO_ME, "заём", 10_000_000, "RUB", LocalDate.of(2026, 10, 1),
            TermType.SCHEDULE, null, Periodicity.MONTH, 1, 1_000_000, LocalDate.of(2026, 11, 10), false, null, 2L,
        )
        val sub = SubscriptionEntity("s1", "ChatGPT", null, 2350, "USD", Periodicity.MONTH, 1, LocalDate.of(2026, 11, 2), createdAt = 3L)
        val share = SubscriptionShareEntity("sh1", "s1", "p1", 2350, "USD", LocalDate.of(2026, 10, 2))
        return BackupData(
            createdAt = 10L, counterparties = listOf(person), debts = listOf(debt),
            payments = listOf(PaymentEntity("pay1", "d1", LocalDate.of(2026, 10, 18), 1_000_000, null, 4L)),
            subscriptions = listOf(sub), shares = listOf(share),
            sharePayments = listOf(SharePaymentEntity("sp1", "sh1", LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3), 2350)),
            attachments = listOf(AttachmentEntity("a1", AttachmentOwner.DEBT, "d1", "расписка.jpg", "image/jpeg", 5, "a1.jpg", 5L)),
            templates = listOf(ReminderTemplateEntity("t1", "Мягкий", "Привет, {имя}")),
            reminderLog = emptyList(), rates = listOf(ExchangeRateEntity("r1", "USD", "RUB", 95.5, LocalDate.of(2026, 10, 1))),
        )
    }

    private fun tmp(): File = Files.createTempDirectory("kvity-test").toFile()

    private fun encode(data: BackupData, password: String?, files: List<Pair<String, File>> = emptyList()): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCodec.write(out, data, files, password)
        return out.toByteArray()
    }

    @Test fun `plain backup round trip keeps all data and attachments`() {
        val dir = tmp()
        val file = File(dir, "a1.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val bytes = encode(sample(), null, listOf("a1.jpg" to file))

        val out = tmp()
        val decoded = BackupCodec.read(ByteArrayInputStream(bytes), null, out)
        assertEquals(sample(), decoded.data)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), File(decoded.attachmentsDir, "a1.jpg").readBytes())
    }

    @Test fun `encrypted backup round trip`() {
        val bytes = encode(sample(), "секрет-123")
        assertTrue(BackupCodec.isEncrypted(bytes))
        val decoded = BackupCodec.read(ByteArrayInputStream(bytes), "секрет-123", tmp())
        assertEquals(sample(), decoded.data)
    }

    @Test fun `encrypted backup does not contain readable data`() {
        val bytes = encode(sample(), "pass")
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("ChatGPT"))
    }

    @Test fun `wrong password is rejected`() {
        val bytes = encode(sample(), "right")
        assertThrows(WrongPasswordException::class.java) { BackupCodec.read(ByteArrayInputStream(bytes), "wrong", tmp()) }
        assertThrows(WrongPasswordException::class.java) { BackupCodec.read(ByteArrayInputStream(bytes), null, tmp()) }
    }

    @Test fun `garbage is rejected`() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.read(ByteArrayInputStream("hello world".toByteArray()), null, tmp()) }
        assertThrows(BackupFormatException::class.java) { BackupCodec.read(ByteArrayInputStream(ByteArray(0)), null, tmp()) }
    }

    @Test fun `plain backup is not marked encrypted`() {
        assertFalse(BackupCodec.isEncrypted(encode(sample(), null)))
    }
}
