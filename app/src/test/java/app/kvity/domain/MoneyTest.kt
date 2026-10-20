package app.kvity.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test fun `parses plain and grouped numbers`() {
        assertEquals(5_000_000L, AmountParser.parseMinor("50000"))
        assertEquals(5_000_000L, AmountParser.parseMinor("50 000"))
        assertEquals(5_000_000L, AmountParser.parseMinor("50\u00A0000"))
    }

    @Test fun `parses k and m suffixes`() {
        assertEquals(1_000_000L, AmountParser.parseMinor("10к"))
        assertEquals(1_000_000L, AmountParser.parseMinor("10k"))
        assertEquals(150_000L, AmountParser.parseMinor("1,5к"))
        assertEquals(200_000_000L, AmountParser.parseMinor("2м"))
    }

    @Test fun `parses decimals`() {
        assertEquals(2350L, AmountParser.parseMinor("23,5"))
        assertEquals(2350L, AmountParser.parseMinor("23.50"))
    }

    @Test fun `rejects invalid input`() {
        assertNull(AmountParser.parseMinor(""))
        assertNull(AmountParser.parseMinor("abc"))
        assertNull(AmountParser.parseMinor("-5"))
        assertNull(AmountParser.parseMinor("0"))
        assertNull(AmountParser.parseMinor("1,234"))
        assertNull(AmountParser.parseMinor("к"))
    }

    @Test fun `respects currency fraction digits`() {
        assertEquals(10_000L, AmountParser.parseMinor("10000", fractionDigits = 0))
        assertNull(AmountParser.parseMinor("10,5", fractionDigits = 0))
    }

    @Test fun `formats amounts with grouping and symbol`() {
        assertEquals("10\u00A0000\u00A0₽", AmountFormatter.format(1_000_000, Currencies.RUB))
        assertEquals("23,5\u00A0$", AmountFormatter.format(2350, Currencies.USD))
        assertEquals("0\u00A0₸", AmountFormatter.format(0, Currencies.KZT))
        assertEquals("1\u00A0234\u00A0567\u00A0₽", AmountFormatter.format(123_456_700, Currencies.RUB))
    }

    @Test fun `compact format for calendar cells`() {
        assertEquals("8к", AmountFormatter.compact(800_000))
        assertEquals("1,2м", AmountFormatter.compact(120_000_000))
        assertEquals("23,5", AmountFormatter.compact(2350))
        assertEquals("1,5к", AmountFormatter.compact(150_000))
    }
}
