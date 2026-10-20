package app.kvity.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val attemptsLeftBeforeDelay: Int) : PinResult
    data class Locked(val untilMillis: Long) : PinResult
}

/** Хранит PIN-код только в виде хэша PBKDF2 с солью. Ограничивает частоту подбора. */
class PinStore(context: Context, private val now: () -> Long = System::currentTimeMillis) {
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

    fun lockedUntil(): Long = prefs.getLong("lockedUntil", 0).takeIf { it > now() } ?: 0

    fun verify(pin: String): PinResult {
        val until = lockedUntil()
        if (until > 0) return PinResult.Locked(until)

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
            val lockedUntil = now() + delaySec * 1000
            editor.putLong("lockedUntil", lockedUntil).apply()
            return PinResult.Locked(lockedUntil)
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
