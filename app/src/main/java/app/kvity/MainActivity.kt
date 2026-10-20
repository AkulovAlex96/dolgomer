package app.kvity

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kvity.data.ThemeMode
import app.kvity.security.Biometrics
import app.kvity.security.LockScreen
import app.kvity.ui.AppNavigation
import app.kvity.ui.theme.KvityTheme

class MainActivity : FragmentActivity() {
    private val app get() = application as KvityApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            val locked by app.lock.locked.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Запрет скриншотов и скрытие содержимого в списке недавних приложений
            LaunchedEffect(settings.secureScreen) {
                if (settings.secureScreen) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            KvityTheme(darkTheme = dark, colorBlind = settings.colorBlindPalette) {
                if (locked && settings.pinEnabled && app.pin.isSet) {
                    LockScreen(
                        pinStore = app.pin,
                        biometricEnabled = settings.biometricEnabled && Biometrics.isAvailable(this),
                        onBiometric = { Biometrics.prompt(this) { app.lock.unlock() } },
                        onUnlocked = { app.lock.unlock() },
                    )
                } else {
                    AppNavigation()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        app.lock.onStop()
    }

    override fun onStart() {
        super.onStart()
        val s = app.settings.current
        app.lock.onStart(enabled = s.pinEnabled && app.pin.isSet, timeoutSeconds = s.lockTimeoutSeconds)
    }
}
