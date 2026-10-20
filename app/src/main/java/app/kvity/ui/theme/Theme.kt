package app.kvity.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Смысловые цвета: «мне» (входящее), «я» (исходящее), внимание. Не зависят от обоев (dynamic color). */
data class KvityColors(
    val income: Color,
    val incomeBg: Color,
    val expense: Color,
    val expenseBg: Color,
    val warn: Color,
    val warnBg: Color,
    val subscription: Color,
    val subscriptionBg: Color,
)

private val LightKvity = KvityColors(
    income = Color(0xFF1F8F5F), incomeBg = Color(0xFFDFF3E8),
    expense = Color(0xFFC8433D), expenseBg = Color(0xFFFBE3E1),
    warn = Color(0xFFB7791F), warnBg = Color(0xFFFCEFD4),
    subscription = Color(0xFF7A4FD6), subscriptionBg = Color(0xFFECE4FB),
)

private val DarkKvity = KvityColors(
    income = Color(0xFF4CC790), incomeBg = Color(0xFF12332A),
    expense = Color(0xFFFF7A73), expenseBg = Color(0xFF3B1D1F),
    warn = Color(0xFFF0B955), warnBg = Color(0xFF3A2D14),
    subscription = Color(0xFFB79CFF), subscriptionBg = Color(0xFF2A2145),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4A67E8), onPrimary = Color.White,
    background = Color(0xFFF4F6FA), onBackground = Color(0xFF141821),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF141821),
    surfaceVariant = Color(0xFFECEFF6), onSurfaceVariant = Color(0xFF5D6678),
    outlineVariant = Color(0xFFDFE3EC),
    error = Color(0xFFC8433D),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7B93FF), onPrimary = Color(0xFF0E1118),
    background = Color(0xFF0E1118), onBackground = Color(0xFFEEF1F7),
    surface = Color(0xFF171B25), onSurface = Color(0xFFEEF1F7),
    surfaceVariant = Color(0xFF1F2431), onSurfaceVariant = Color(0xFF97A0B3),
    outlineVariant = Color(0xFF2A3041),
    error = Color(0xFFFF7A73),
)

private val LocalKvityColors = staticCompositionLocalOf { LightKvity }

val MaterialTheme.kvity: KvityColors
    @Composable @ReadOnlyComposable get() = LocalKvityColors.current

@Composable
fun KvityTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKvityColors provides if (darkTheme) DarkKvity else LightKvity) {
        MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, content = content)
    }
}
