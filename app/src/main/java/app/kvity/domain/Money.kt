package app.kvity.domain

import java.math.BigDecimal
import java.math.RoundingMode

data class Currency(val code: String, val symbol: String, val fractionDigits: Int = 2)

object Currencies {
    val RUB = Currency("RUB", "₽")
    val KZT = Currency("KZT", "₸")
    val USD = Currency("USD", "$")
    val EUR = Currency("EUR", "€")

    val defaults: List<Currency> = listOf(RUB, KZT, USD, EUR)

    fun byCode(code: String): Currency? = defaults.firstOrNull { it.code == code }
}

/** Деньги в минимальных единицах (копейки, центы), чтобы не терять точность. */
data class Money(val minor: Long, val currencyCode: String)

object AmountParser {
    private const val MAX_INTEGER_DIGITS = 15

    /**
     * Разбирает ввод пользователя: «50000», «50 000», «1,5к», «10к», «2м».
     * Возвращает сумму в минимальных единицах или null, если ввод некорректен.
     */
    fun parseMinor(input: String, fractionDigits: Int = 2): Long? {
        var text = input.trim().lowercase().replace(" ", "").replace("\u00A0", "").replace(',', '.')
        if (text.isEmpty()) return null

        var multiplier = BigDecimal.ONE
        when (text.last()) {
            'к', 'k' -> { multiplier = BigDecimal(1_000); text = text.dropLast(1) }
            'м', 'm' -> { multiplier = BigDecimal(1_000_000); text = text.dropLast(1) }
        }
        if (text.isEmpty() || text.startsWith("-")) return null

        val value = text.toBigDecimalOrNull()?.multiply(multiplier) ?: return null
        if (value.signum() <= 0) return null
        if (value.stripTrailingZeros().scale() > fractionDigits) return null
        // целая часть должна помещаться в Long (с запасом на копейки): иначе «1e999999999» вешает setScale, а длинные числа бросают Overflow
        if (value.precision() - value.scale() > MAX_INTEGER_DIGITS) return null

        return try {
            value.setScale(fractionDigits, RoundingMode.UNNECESSARY)
                .movePointRight(fractionDigits)
                .longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }
}

object AmountFormatter {
    /** «10 000 ₽», «1 500,50 $». Пробел между разрядами неразрывный. */
    fun format(minor: Long, currency: Currency): String {
        val negative = minor < 0
        val abs = kotlin.math.abs(minor)
        val divisor = pow10(currency.fractionDigits)
        val whole = abs / divisor
        val fraction = abs % divisor
        val wholeText = groupThousands(whole.toString())
        val body = if (currency.fractionDigits > 0 && fraction != 0L) {
            wholeText + "," + fraction.toString().padStart(currency.fractionDigits, '0').trimEnd('0')
        } else {
            wholeText
        }
        return (if (negative) "−" else "") + body + "\u00A0" + currency.symbol
    }

    /** Короткая запись для ячейки календаря: «8к», «1,2м», «23,5». Знак и валюта добавляются снаружи. */
    fun compact(minor: Long, fractionDigits: Int = 2): String {
        val whole = kotlin.math.abs(minor) / pow10(fractionDigits).toDouble()
        return when {
            whole >= 1_000_000 -> trimmed(whole / 1_000_000) + "м"
            whole >= 1_000 -> trimmed(whole / 1_000) + "к"
            else -> trimmed(whole)
        }
    }

    private fun trimmed(value: Double): String {
        val rounded = BigDecimal(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
        return rounded.toPlainString().replace('.', ',')
    }

    private fun groupThousands(digits: String): String =
        digits.reversed().chunked(3).joinToString("\u00A0").reversed()

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}
