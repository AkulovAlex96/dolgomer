package app.kvity.ui

import app.kvity.domain.AmountFormatter
import app.kvity.domain.Bucket
import app.kvity.domain.Currencies
import app.kvity.domain.Currency
import app.kvity.domain.LocaleText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val dateFormatRu = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val dateFormatEn = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

/** Размер файла для списка вложений: «22 Б», «1,5 КБ», «5,0 МБ» (в английском: «22 B», «1.5 KB», «5.0 MB»). */
fun formatFileSize(bytes: Long): String {
    val en = LocaleText.isEnglish()
    fun dec(v: Double): String = "%.1f".format(Locale.ROOT, v).let { if (en) it else it.replace('.', ',') }
    return when {
        bytes < 1024 -> "$bytes " + (if (en) "B" else "Б")
        bytes < 1024 * 1024 -> dec(bytes / 1024.0) + " " + (if (en) "KB" else "КБ")
        else -> dec(bytes / (1024.0 * 1024.0)) + " " + (if (en) "MB" else "МБ")
    }
}

fun currencyOf(code: String): Currency = Currencies.byCode(code) ?: Currency(code, code)

fun formatMoney(minor: Long, currencyCode: String): String =
    AmountFormatter.format(minor, currencyOf(currencyCode))

fun formatDate(date: LocalDate): String =
    date.format(if (LocaleText.isEnglish()) dateFormatEn else dateFormatRu)

val Bucket.label: String
    get() = when (this) {
        Bucket.OVERDUE -> LocaleText.pick("Просрочено", "Overdue")
        Bucket.TODAY -> LocaleText.pick("Сегодня", "Today")
        Bucket.TOMORROW -> LocaleText.pick("Завтра", "Tomorrow")
        Bucket.THIS_WEEK -> LocaleText.pick("На этой неделе", "This week")
        Bucket.THIS_MONTH -> LocaleText.pick("В этом месяце", "This month")
        Bucket.NEXT_MONTH -> LocaleText.pick("В следующем месяце", "Next month")
    }

/** Сумма для поля ввода: без символа валюты и разделителей разрядов («10000», «23,5»). */
fun minorToInput(minor: Long?, currencyCode: String): String {
    if (minor == null) return ""
    val c = currencyOf(currencyCode)
    return AmountFormatter.format(minor, c).removeSuffix(" " + c.symbol).replace(" ", "")
}

fun formatMonth(date: LocalDate): String {
    val name = if (LocaleText.isEnglish()) {
        date.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.ENGLISH)
    } else {
        listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")[date.monthValue - 1]
    }
    return "$name ${date.year}"
}
