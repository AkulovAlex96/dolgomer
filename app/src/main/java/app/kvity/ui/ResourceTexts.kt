package app.kvity.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import app.kvity.R
import app.kvity.domain.AppTexts
import app.kvity.domain.ArchiveBlock
import app.kvity.domain.Bucket
import app.kvity.domain.MoneySuffixes
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Тексты из строковых ресурсов текущего языка приложения. */
class ResourceTexts(private val res: Resources) : AppTexts {
    private val locale: Locale = Locale.forLanguageTag(res.getString(R.string.format_locale_tag))
    private val months: Array<String> = res.getStringArray(R.array.format_months)

    override fun bucketLabel(bucket: Bucket): String = res.getString(
        when (bucket) {
            Bucket.OVERDUE -> R.string.bucket_overdue
            Bucket.TODAY -> R.string.bucket_today
            Bucket.TOMORROW -> R.string.bucket_tomorrow
            Bucket.THIS_WEEK -> R.string.bucket_this_week
            Bucket.THIS_MONTH -> R.string.bucket_this_month
            Bucket.NEXT_MONTH -> R.string.bucket_next_month
        },
    )

    override fun byteUnit(index: Int): String = res.getString(
        when (index) {
            0 -> R.string.unit_byte
            1 -> R.string.unit_kilobyte
            else -> R.string.unit_megabyte
        },
    )

    override val decimalSeparator: Char = res.getString(R.string.format_decimal_separator).first()
    override val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(res.getString(R.string.format_date_pattern), locale)
    override fun monthName(month: Int): String = months[month - 1]
    override fun installmentOf(index: Int, total: Int): String = res.getString(R.string.event_installment_of, index, total)
    override fun charge(): String = res.getString(R.string.event_charge)
    override fun repaying(name: String): String = res.getString(R.string.event_repaying, name)
    override fun archiveBlock(block: ArchiveBlock): String = res.getString(block.messageRes)
    override val moneySuffixes: MoneySuffixes =
        MoneySuffixes(res.getString(R.string.money_suffix_thousand), res.getString(R.string.money_suffix_million), decimalSeparator)
}

/** Тексты текущего языка для экрана; пересоздаются при смене конфигурации (в том числе языка). */
@Composable
fun rememberTexts(): AppTexts {
    val res = LocalResources.current
    val config = LocalConfiguration.current
    return remember(res, config) { ResourceTexts(res) }
}
