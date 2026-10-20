package app.kvity.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BucketsTest {

    // Вторник, 20 октября 2026. Неделя до воскресенья 25.10, месяц до 31.10.
    private val today = LocalDate.of(2026, 10, 20)

    private fun of(month: Int, day: Int, year: Int = 2026) = Buckets.of(LocalDate.of(year, month, day), today)

    @Test fun `past date is only overdue`() {
        assertEquals(setOf(Bucket.OVERDUE), of(10, 19))
    }

    @Test fun `today belongs to today, week and month`() {
        assertEquals(setOf(Bucket.TODAY, Bucket.THIS_WEEK, Bucket.THIS_MONTH), of(10, 20))
    }

    @Test fun `tomorrow belongs to tomorrow, week and month`() {
        assertEquals(setOf(Bucket.TOMORROW, Bucket.THIS_WEEK, Bucket.THIS_MONTH), of(10, 21))
    }

    @Test fun `end of week is inclusive and next monday is not in the week`() {
        assertEquals(setOf(Bucket.THIS_WEEK, Bucket.THIS_MONTH), of(10, 25))
        assertEquals(setOf(Bucket.THIS_MONTH), of(10, 26))
    }

    @Test fun `next month boundaries`() {
        assertEquals(setOf(Bucket.THIS_MONTH), of(10, 31))
        assertEquals(setOf(Bucket.NEXT_MONTH), of(11, 1))
        assertEquals(setOf(Bucket.NEXT_MONTH), of(11, 30))
        assertEquals(emptySet<Bucket>(), of(12, 1))
    }

    @Test fun `week crossing into next month`() {
        val thursday = LocalDate.of(2026, 10, 29) // неделя до 01.11
        assertEquals(
            setOf(Bucket.THIS_WEEK, Bucket.THIS_MONTH),
            Buckets.of(LocalDate.of(2026, 10, 31), thursday),
        )
        assertEquals(
            setOf(Bucket.THIS_WEEK, Bucket.NEXT_MONTH),
            Buckets.of(LocalDate.of(2026, 11, 1), thursday),
        )
    }

    @Test fun `aggregate keeps currencies separate`() {
        val dues = listOf(
            Due(LocalDate.of(2026, 10, 21), 1_000_000, "RUB"),
            Due(LocalDate.of(2026, 10, 24), 1_000_000, "RUB"),
            Due(LocalDate.of(2026, 10, 28), 1_000_000, "KZT"),
            Due(LocalDate.of(2026, 10, 1), 4_000_000, "KZT"),
            Due(LocalDate.of(2026, 11, 1), 2_000_000, "RUB"),
        )
        val totals = Buckets.aggregate(dues, today)

        assertEquals(mapOf("KZT" to 4_000_000L), totals.getValue(Bucket.OVERDUE))
        assertEquals(mapOf("RUB" to 1_000_000L), totals.getValue(Bucket.TOMORROW))
        assertEquals(mapOf("RUB" to 2_000_000L), totals.getValue(Bucket.THIS_WEEK))
        assertEquals(mapOf("RUB" to 2_000_000L, "KZT" to 1_000_000L), totals.getValue(Bucket.THIS_MONTH))
        assertEquals(mapOf("RUB" to 2_000_000L), totals.getValue(Bucket.NEXT_MONTH))
        assertEquals(emptyMap<String, Long>(), totals.getValue(Bucket.TODAY))
    }
}
