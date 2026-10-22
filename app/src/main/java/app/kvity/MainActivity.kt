package app.kvity

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kvity.data.ThemeMode
import app.kvity.notify.Notifier
import app.kvity.security.Biometrics
import app.kvity.security.LockScreen
import app.kvity.ui.AppNavigation
import app.kvity.ui.theme.KvityTheme

class MainActivity : FragmentActivity() {
    private val app get() = application as KvityApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(this)) {
            // Классический запрос: через ActivityResult он падает в FragmentActivity (код запроса шире 16 бит)
            androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001,
            )
        }
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()
            val locked by app.lock.locked.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Иконки статус-бара и навигации следуют теме приложения, а не системной: иначе в светлой теме на тёмной системе они белые на белом
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark },
                )
                onDispose {}
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

    private companion object {
        // стандартные подложки трёхкнопочной навигации из enableEdgeToEdge
        val LIGHT_NAV_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_NAV_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
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
