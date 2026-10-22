package app.kvity.ui

import app.kvity.domain.AmountFormatter
import app.kvity.domain.Bucket
import app.kvity.domain.Currencies
import app.kvity.domain.Currency
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Размер файла для списка вложений: «22 Б», «1,5 КБ», «5,0 МБ». */
fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "%.1f КБ".format(java.util.Locale.ROOT, bytes / 1024.0).replace('.', ',')
    else -> "%.1f МБ".format(java.util.Locale.ROOT, bytes / (1024.0 * 1024.0)).replace('.', ',')
}

fun currencyOf(code: String): Currency = Currencies.byCode(code) ?: Currency(code, code)

fun formatMoney(minor: Long, currencyCode: String): String =
    AmountFormatter.format(minor, currencyOf(currencyCode))

fun formatDate(date: LocalDate): String = date.format(dateFormat)

val Bucket.label: String
    get() = when (this) {
        Bucket.OVERDUE -> "Просрочено"
        Bucket.TODAY -> "Сегодня"
        Bucket.TOMORROW -> "Завтра"
        Bucket.THIS_WEEK -> "На этой неделе"
        Bucket.THIS_MONTH -> "В этом месяце"
        Bucket.NEXT_MONTH -> "В следующем месяце"
    }

/** Сумма для поля ввода: без символа валюты и разделителей разрядов («10000», «23,5»). */
fun minorToInput(minor: Long?, currencyCode: String): String {
    if (minor == null) return ""
    val c = currencyOf(currencyCode)
    return AmountFormatter.format(minor, c).removeSuffix("\u00A0" + c.symbol).replace("\u00A0", "")
}
