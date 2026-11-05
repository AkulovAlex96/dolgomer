package app.kvity.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val attemptsLeftBeforeDelay: Int) : PinResult
    data class Locked(val remainingMillis: Long) : PinResult
}

/** Источники времени для блокировки; в тестах подменяются. */
interface PinClock {
    /** Системные часы (можно перевести). */
    fun wallMillis(): Long
    /** Монотонное время с загрузки устройства (SystemClock.elapsedRealtime). */
    fun elapsedMillis(): Long
    /** Идентификатор загрузки устройства; [UNKNOWN_BOOT], если определить нельзя. */
    fun bootId(): Long

    companion object {
        const val UNKNOWN_BOOT = -1L
    }
}

private class SystemPinClock(private val context: Context) : PinClock {
    override fun wallMillis() = System.currentTimeMillis()
    override fun elapsedMillis() = SystemClock.elapsedRealtime()
    override fun bootId(): Long = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1).toLong()
    }.getOrDefault(-1L)
}

/** Состояние блокировки, как оно хранится рядом с lockedUntil. */
data class PinLock(
    val lockedUntil: Long = 0,
    val setWall: Long = 0,
    val setElapsed: Long = 0,
    val bootId: Long = PinClock.UNKNOWN_BOOT,
) {
    /**
     * Сколько ещё осталось ждать. В рамках одной загрузки считаем по монотонным часам — перевод
     * системных часов не влияет. После перезагрузки — по системным часам, но если они ушли назад
     * относительно момента установки блокировки, остаток не сокращаем.
     */
    fun remainingMillis(clock: PinClock): Long {
        if (lockedUntil <= 0) return 0
        val total = (lockedUntil - setWall).coerceAtLeast(0)
        val elapsed = clock.elapsedMillis()
        val sameBoot = setWall > 0 && elapsed >= setElapsed &&
            bootId != PinClock.UNKNOWN_BOOT && bootId == clock.bootId()
        val wall = clock.wallMillis()
        val left = when {
            sameBoot -> total - (elapsed - setElapsed)
            setWall > 0 && wall < setWall -> total // часы ушли назад: не сокращаем
            else -> lockedUntil - wall
        }
        return left.coerceIn(0, total)
    }

    companion object {
        fun start(clock: PinClock, delayMillis: Long): PinLock {
            val wall = clock.wallMillis()
            return PinLock(wall + delayMillis, wall, clock.elapsedMillis(), clock.bootId())
        }
    }
}

/** Хранит PIN-код только в виде хэша PBKDF2 с солью. Ограничивает частоту подбора. */
class PinStore(context: Context, private val clock: PinClock = SystemPinClock(context.applicationContext)) {
    private val prefs = context.getSharedPreferences("pin", Context.MODE_PRIVATE)

    val isSet: Boolean get() = prefs.contains("hash")
    val length: Int get() = prefs.getInt("length", 4)

    fun set(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .putInt("length", pin.length)
            .putInt("fails", 0)
            .putLong("lockedUntil", 0)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun readLock() = PinLock(
        lockedUntil = prefs.getLong("lockedUntil", 0),
        setWall = prefs.getLong("lockSetWall", 0),
        setElapsed = prefs.getLong("lockSetElapsed", 0),
        bootId = prefs.getLong("lockBootId", PinClock.UNKNOWN_BOOT),
    )

    /** Сколько миллисекунд ещё действует блокировка (0 — не заблокировано). */
    fun lockRemainingMillis(): Long = readLock().remainingMillis(clock)

    fun verify(pin: String): PinResult {
        val left = lockRemainingMillis()
        if (left > 0) return PinResult.Locked(left)

        val salt = Base64.decode(prefs.getString("salt", "") ?: "", Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString("hash", "") ?: "", Base64.NO_WRAP)
        if (MessageDigest.isEqual(expected, hash(pin, salt))) {
            prefs.edit().putInt("fails", 0).putLong("lockedUntil", 0).apply()
            return PinResult.Ok
        }

        val fails = prefs.getInt("fails", 0) + 1
        val editor = prefs.edit().putInt("fails", fails)
        if (fails >= FREE_ATTEMPTS) {
            val delaySec = (30L shl (fails - FREE_ATTEMPTS).coerceAtMost(7)).coerceAtMost(3600)
            val lock = PinLock.start(clock, delaySec * 1000)
            editor.putLong("lockedUntil", lock.lockedUntil)
                .putLong("lockSetWall", lock.setWall)
                .putLong("lockSetElapsed", lock.setElapsed)
                .putLong("lockBootId", lock.bootId)
                .apply()
            return PinResult.Locked(delaySec * 1000)
        }
        editor.apply()
        return PinResult.Wrong(FREE_ATTEMPTS - fails)
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded

    private companion object {
        const val FREE_ATTEMPTS = 5
    }
}
