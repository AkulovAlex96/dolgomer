package app.kvity.security

import app.kvity.R
import androidx.compose.ui.res.stringResource
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Ряд точек и цифровая клавиатура. Вызывает [onComplete], когда введено [length] цифр. */
@Composable
fun PinPad(
    length: Int,
    title: String,
    message: String?,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any = Unit,
    footer: @Composable () -> Unit = {},
) {
    var pin by remember(resetKey) { mutableStateOf("") }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(
            message ?: " ", color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 20.dp)) {
            repeat(length) { i ->
                Box(
                    Modifier.size(14.dp).background(
                        if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        CircleShape,
                    ),
                )
            }
        }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { k ->
                        Box(
                            Modifier.size(68.dp)
                                .clip(CircleShape)
                                .background(if (k.isEmpty()) Color0 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
                                .clickable(enabled = k.isNotEmpty()) {
                                    if (k == "⌫") pin = pin.dropLast(1)
                                    else if (pin.length < length) {
                                        pin += k
                                        if (pin.length == length) onComplete(pin)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                k, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        footer()
    }
}

private val Color0 = androidx.compose.ui.graphics.Color.Transparent

@Composable
fun LockScreen(
    pinStore: PinStore,
    biometricEnabled: Boolean,
    onBiometric: () -> Unit,
    onUnlocked: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    var message by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    // Конец блокировки в монотонном времени (elapsedRealtime): перевод системных часов его не сдвигает.
    var lockedUntil by remember { mutableStateOf(pinStore.lockRemainingMillis().let { if (it > 0) SystemClock.elapsedRealtime() + it else 0L }) }
    var secondsLeft by remember { mutableStateOf(0L) }

    LaunchedEffect(biometricEnabled) { if (biometricEnabled) onBiometric() }
    LaunchedEffect(lockedUntil) {
        while (lockedUntil > SystemClock.elapsedRealtime()) {
            secondsLeft = (lockedUntil - SystemClock.elapsedRealtime()) / 1000 + 1
            message = resources.getString(R.string.lock_too_many, secondsLeft)
            delay(500)
        }
        if (lockedUntil > 0) { message = null; lockedUntil = 0 }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        PinPad(
            length = pinStore.length, title = stringResource(R.string.app_name), message = message,
            resetKey = attempt,
            onComplete = { pin ->
                if (lockedUntil > SystemClock.elapsedRealtime()) { attempt++; return@PinPad }
                when (val r = pinStore.verify(pin)) {
                    PinResult.Ok -> onUnlocked()
                    is PinResult.Wrong -> { message = resources.getString(R.string.lock_wrong_pin); attempt++ }
                    is PinResult.Locked -> { lockedUntil = SystemClock.elapsedRealtime() + r.remainingMillis; attempt++ }
                }
            },
            footer = {
                if (biometricEnabled) {
                    TextButton(onClick = onBiometric, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.lock_biometric_button)) }
                }
            },
        )
    }
}
