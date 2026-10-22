package app.kvity.notify

import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderScheduleTest {
    private fun delay(now: String, hour: Int = 9, minute: Int = 0): Duration =
        ReminderWorker.delayUntilNext(LocalDateTime.parse(now), hour, minute)

    @Test fun `before the time it waits until today`() {
        assertEquals(Duration.ofHours(4), delay("2026-10-22T05:00:00"))
    }

    @Test fun `after the time it waits until tomorrow`() {
        assertEquals(Duration.ofHours(20).plusMinutes(40), delay("2026-10-22T12:20:00"))
    }

    @Test fun `exactly at the time it waits a full day`() {
        assertEquals(Duration.ofHours(24), delay("2026-10-22T09:00:00"))
    }

    @Test fun `firing a few seconds early does not reschedule for the same moment`() {
        // сработало в 08:59:30: следующий запуск завтра, а не через 30 секунд
        assertEquals(Duration.ofHours(24).plusSeconds(30), delay("2026-10-22T08:59:30"))
    }

    @Test fun `uses the configured minutes`() {
        assertEquals(Duration.ofMinutes(45), delay("2026-10-22T07:45:00", hour = 8, minute = 30))
    }

    @Test fun `month and year boundaries`() {
        assertEquals(Duration.ofHours(9).plusMinutes(1), delay("2026-12-31T23:59:00"))
    }
}
