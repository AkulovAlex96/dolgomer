package app.kvity.ui

import androidx.compose.runtime.Composable
import app.kvity.domain.AmountFormatter
import app.kvity.domain.AppTexts
import app.kvity.domain.Bucket
import app.kvity.domain.Currencies
import app.kvity.domain.Currency
import java.time.LocalDate
import java.util.Locale

/** Размер файла для списка вложений: «22 Б», «1,5 КБ», «5,0 МБ» (в английском: «22 B», «1.5 KB», «5.0 MB»). */
fun formatFileSize(bytes: Long, texts: AppTexts = AppTexts.default): String {
    fun dec(v: Double): String = "%.1f".format(Locale.ROOT, v).replace('.', texts.decimalSeparator)
    return when {
        bytes < 1024 -> "$bytes " + texts.byteUnit(0)
        bytes < 1024 * 1024 -> dec(bytes / 1024.0) + " " + texts.byteUnit(1)
        else -> dec(bytes / (1024.0 * 1024.0)) + " " + texts.byteUnit(2)
    }
}

fun currencyOf(code: String): Currency = Currencies.byCode(code) ?: Currency(code, code)

fun formatMoney(minor: Long, currencyCode: String): String =
    AmountFormatter.format(minor, currencyOf(currencyCode))

fun formatDate(date: LocalDate, texts: AppTexts = AppTexts.default): String =
    date.format(texts.dateFormatter)

/** Подпись группы; тексты берутся из ресурсов текущего языка. */
val Bucket.label: String
    @Composable get() = rememberTexts().bucketLabel(this)

/** Сумма для поля ввода: без символа валюты и разделителей разрядов («10000», «23,5»). */
fun minorToInput(minor: Long?, currencyCode: String): String {
    if (minor == null) return ""
    val c = currencyOf(currencyCode)
    return AmountFormatter.format(minor, c).removeSuffix(" " + c.symbol).replace(" ", "")
}

fun formatMonth(date: LocalDate, texts: AppTexts = AppTexts.default): String =
    "${texts.monthName(date.monthValue)} ${date.year}"
