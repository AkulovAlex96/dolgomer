package app.kvity.ui

import app.kvity.domain.AmountFormatter
import app.kvity.domain.Bucket
import app.kvity.domain.Currencies
import app.kvity.domain.Currency
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

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
