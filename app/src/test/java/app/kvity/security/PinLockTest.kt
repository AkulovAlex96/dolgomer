package app.kvity.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PinLockTest {

    private class FakeClock(var wall: Long, var elapsed: Long, var boot: Long = 5) : PinClock {
        override fun wallMillis() = wall
        override fun elapsedMillis() = elapsed
        override fun bootId() = boot
    }

    private fun clock() = FakeClock(wall = 1_000_000_000L, elapsed = 50_000L)

    @Test fun `нормальный ход - остаток убывает`() {
        val c = clock()
        val lock = PinLock.start(c, 30_000)
        assertEquals(30_000, lock.remainingMillis(c))
        c.wall += 10_000; c.elapsed += 10_000
        assertEquals(20_000, lock.remainingMillis(c))
        c.wall += 25_000; c.elapsed += 25_000
        assertEquals(0, lock.remainingMillis(c))
    }

    @Test fun `перевод часов назад не сокращает блокировку`() {
        val c = clock()
        val lock = PinLock.start(c, 30_000)
        c.wall -= 3_600_000; c.elapsed += 10_000
        assertEquals(20_000, lock.remainingMillis(c))
    }

    @Test fun `перевод часов вперёд не сокращает блокировку в той же загрузке`() {
        val c = clock()
        val lock = PinLock.start(c, 30_000)
        c.wall += 3_600_000; c.elapsed += 10_000
        assertEquals(20_000, lock.remainingMillis(c))
    }

    @Test fun `после перезагрузки считаем по системным часам`() {
        val c = clock()
        val lock = PinLock.start(c, 30_000)
        c.boot = 6; c.elapsed = 1_000; c.wall += 12_000
        assertEquals(18_000, lock.remainingMillis(c))
        c.wall += 30_000
        assertEquals(0, lock.remainingMillis(c))
    }

    @Test fun `после перезагрузки часы назад - остаток не сокращается`() {
        val c = clock()
        val lock = PinLock.start(c, 30_000)
        c.boot = 6; c.elapsed = 1_000; c.wall -= 3_600_000
        assertEquals(30_000, lock.remainingMillis(c))
    }

    @Test fun `неизвестный id загрузки - перезагрузку выдаёт уменьшение elapsed`() {
        val c = FakeClock(1_000_000_000L, 50_000L, PinClock.UNKNOWN_BOOT)
        val lock = PinLock.start(c, 30_000)
        c.elapsed = 2_000; c.wall += 5_000
        assertEquals(25_000, lock.remainingMillis(c))
    }

    @Test fun `нет блокировки`() {
        assertEquals(0, PinLock().remainingMillis(clock()))
    }

    @Test fun `старая запись без служебных полей считается по системным часам`() {
        val c = clock()
        val lock = PinLock(lockedUntil = c.wall + 15_000)
        assertEquals(15_000, lock.remainingMillis(c))
        c.wall += 20_000
        assertEquals(0, lock.remainingMillis(c))
    }
}
