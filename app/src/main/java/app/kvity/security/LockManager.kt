package app.kvity.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Состояние блокировки приложения. */
class LockManager(initiallyLocked: Boolean) {
    private val _locked = MutableStateFlow(initiallyLocked)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long = 0

    fun unlock() { _locked.value = false }

    fun lock() { _locked.value = true }

    fun onStop() { backgroundedAt = SystemClock.elapsedRealtime() }

    /** Блокирует, если приложение было в фоне дольше [timeoutSeconds]. */
    fun onStart(enabled: Boolean, timeoutSeconds: Int) {
        if (!enabled) { _locked.value = false; return }
        if (backgroundedAt == 0L) return
        val away = (SystemClock.elapsedRealtime() - backgroundedAt) / 1000
        if (away >= timeoutSeconds) _locked.value = true
    }
}
