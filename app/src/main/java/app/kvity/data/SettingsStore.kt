package app.kvity.data

import android.content.Context
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val colorBlindPalette: Boolean = false,
    val defaultCurrency: String = "RUB",
    val enabledCurrencies: List<String> = listOf("RUB", "KZT", "USD", "EUR"),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    // уведомления
    val notificationsEnabled: Boolean = true,
    val notifyDaysBefore: List<Int> = listOf(3, 1),
    val notifyHour: Int = 9,
    val notifyMinute: Int = 0,
    val overdueRepeatDays: Int = 3,
    val subscriptionNotifyDays: Int = 1,
    val hideAmountsInNotifications: Boolean = false,
    // безопасность
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 30,
    val secureScreen: Boolean = true,
    // данные
    val lastBackupMillis: Long = 0,
    val dueSoonDays: Int = 3,
)

/** Настройки в SharedPreferences. Хэш PIN-кода хранится отдельно в [PinStore]. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<Settings> = state.asStateFlow()

    val current: Settings get() = state.value

    fun update(transform: (Settings) -> Settings) {
        val next = transform(state.value)
        write(next)
        state.value = next
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            theme = prefs.getString("theme", null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            colorBlindPalette = prefs.getBoolean("cb", d.colorBlindPalette),
            defaultCurrency = prefs.getString("defaultCurrency", d.defaultCurrency) ?: d.defaultCurrency,
            enabledCurrencies = prefs.getString("currencies", null)?.split(',')?.filter { it.isNotBlank() } ?: d.enabledCurrencies,
            firstDayOfWeek = prefs.getInt("firstDow", d.firstDayOfWeek.value).let { DayOfWeek.of(it) },
            notificationsEnabled = prefs.getBoolean("notif", d.notificationsEnabled),
            notifyDaysBefore = prefs.getString("notifyDays", null)?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: d.notifyDaysBefore,
            notifyHour = prefs.getInt("notifyHour", d.notifyHour),
            notifyMinute = prefs.getInt("notifyMinute", d.notifyMinute),
            overdueRepeatDays = prefs.getInt("overdueRepeat", d.overdueRepeatDays),
            subscriptionNotifyDays = prefs.getInt("subNotifyDays", d.subscriptionNotifyDays),
            hideAmountsInNotifications = prefs.getBoolean("hideAmounts", d.hideAmountsInNotifications),
            pinEnabled = prefs.getBoolean("pin", d.pinEnabled),
            biometricEnabled = prefs.getBoolean("bio", d.biometricEnabled),
            lockTimeoutSeconds = prefs.getInt("lockTimeout", d.lockTimeoutSeconds),
            secureScreen = prefs.getBoolean("secure", d.secureScreen),
            lastBackupMillis = prefs.getLong("lastBackup", d.lastBackupMillis),
            dueSoonDays = prefs.getInt("dueSoon", d.dueSoonDays),
        )
    }

    private fun write(s: Settings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putBoolean("cb", s.colorBlindPalette)
            .putString("defaultCurrency", s.defaultCurrency)
            .putString("currencies", s.enabledCurrencies.joinToString(","))
            .putInt("firstDow", s.firstDayOfWeek.value)
            .putBoolean("notif", s.notificationsEnabled)
            .putString("notifyDays", s.notifyDaysBefore.joinToString(","))
            .putInt("notifyHour", s.notifyHour)
            .putInt("notifyMinute", s.notifyMinute)
            .putInt("overdueRepeat", s.overdueRepeatDays)
            .putInt("subNotifyDays", s.subscriptionNotifyDays)
            .putBoolean("hideAmounts", s.hideAmountsInNotifications)
            .putBoolean("pin", s.pinEnabled)
            .putBoolean("bio", s.biometricEnabled)
            .putInt("lockTimeout", s.lockTimeoutSeconds)
            .putBoolean("secure", s.secureScreen)
            .putLong("lastBackup", s.lastBackupMillis)
            .putInt("dueSoon", s.dueSoonDays)
            .apply()
    }
}
