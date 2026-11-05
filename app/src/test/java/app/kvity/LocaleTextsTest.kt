package app.kvity

import app.kvity.domain.AppTexts
import app.kvity.domain.ArchiveBlock
import app.kvity.domain.ArchiveRules
import app.kvity.domain.Bucket
import app.kvity.domain.MoneySuffixes
import app.kvity.domain.RuTexts
import app.kvity.ui.formatDate
import app.kvity.ui.formatFileSize
import app.kvity.ui.formatMonth
import app.kvity.ui.settings.AppLanguage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Провайдер текстов на английском: проверяет, что чистые функции берут тексты у провайдера, а не из кода. */
private object EnTexts : AppTexts by RuTexts {
    override fun bucketLabel(bucket: Bucket) = "EN-" + bucket.name
    override fun byteUnit(index: Int) = listOf("B", "KB", "MB")[index]
    override val decimalSeparator = '.'
    override val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    override fun monthName(month: Int) = "March"
    override fun archiveBlock(block: ArchiveBlock) = "en:" + block.name
    override val moneySuffixes = MoneySuffixes.EN
}

class LocaleTextsTest {
    @Test fun `file size follows provider`() {
        assertEquals("22 Б", formatFileSize(22))
        assertEquals("1,5 КБ", formatFileSize(1536))
        assertEquals("1.5 KB", formatFileSize(1536, EnTexts))
        assertEquals("5.0 MB", formatFileSize(5L * 1024 * 1024, EnTexts))
    }

    @Test fun `dates and months follow provider`() {
        val d = LocalDate.of(2026, 3, 9)
        assertEquals("09.03.2026", formatDate(d))
        assertEquals("Mar 9, 2026", formatDate(d, EnTexts))
        assertEquals("март 2026", formatMonth(d))
        assertEquals("March 2026", formatMonth(d, EnTexts))
    }

    @Test fun `archive reason follows provider`() {
        assertEquals("en:OPEN_DEBTS", ArchiveRules.archiveBlockReason(mapOf("RUB" to 5L), 0, EnTexts))
        assertEquals("en:ACTIVE_SHARES", ArchiveRules.archiveBlockReason(emptyMap(), 2, EnTexts))
        assertNull(ArchiveRules.archiveBlockReason(emptyMap(), 0, EnTexts))
    }

    @Test fun `app language from tags`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags(""))
        assertEquals(AppLanguage.RU, AppLanguage.fromTags("ru"))
        assertEquals(AppLanguage.EN, AppLanguage.fromTags("en-US"))
        assertEquals(AppLanguage.EN, AppLanguage.fromTags("en,ru"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTags("de"))
    }
}
