package app.kvity.ui.settings

import app.kvity.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.kvity.data.Settings
import app.kvity.security.PinPad
import app.kvity.security.PinResult
import app.kvity.security.PinStore
import app.kvity.ui.components.DropdownField
import app.kvity.ui.components.SectionCard

private enum class PinAction { NONE, SET, DISABLE }

@Composable
fun SecuritySection(
    settings: Settings,
    pinStore: PinStore,
    biometricAvailable: Boolean,
    onChange: ((Settings) -> Settings) -> Unit,
) {
    var action by remember { mutableStateOf(PinAction.NONE) }
    var pinLength by remember { mutableStateOf(4) }
    val pinOn = settings.pinEnabled && pinStore.isSet

    SectionCard {
        GroupTitle(stringResource(R.string.settings_security))
        SettingSwitch(
            stringResource(R.string.settings_pin),
            if (pinOn) stringResource(R.string.settings_pin_on, pinStore.length) else stringResource(R.string.settings_pin_off),
            pinOn,
        ) { on -> action = if (on) PinAction.SET else PinAction.DISABLE }

        if (!pinOn) {
            DropdownField(stringResource(R.string.settings_pin_length), listOf(4 to stringResource(R.string.settings_pin_4), 6 to stringResource(R.string.settings_pin_6)), pinLength, { pinLength = it })
        } else {
            SettingSwitch(
                stringResource(R.string.settings_fingerprint), if (biometricAvailable) null else stringResource(R.string.settings_fingerprint_unavailable),
                settings.biometricEnabled && biometricAvailable,
            ) { v -> if (biometricAvailable) onChange { it.copy(biometricEnabled = v) } }
            DropdownField(
                stringResource(R.string.settings_lock_after),
                listOf(
                    0 to stringResource(R.string.settings_lock_now), 30 to stringResource(R.string.settings_lock_30s),
                    60 to stringResource(R.string.settings_lock_1m), 300 to stringResource(R.string.settings_lock_5m),
                ),
                settings.lockTimeoutSeconds, { s -> onChange { it.copy(lockTimeoutSeconds = s) } },
                Modifier.padding(top = 4.dp),
            )
        }
        SettingSwitch(
            stringResource(R.string.settings_secure_screen), stringResource(R.string.settings_secure_screen_sub),
            settings.secureScreen, { v -> onChange { it.copy(secureScreen = v) } },
        )
    }

    when (action) {
        PinAction.SET -> SetPinDialog(
            length = pinLength,
            onDone = { pin ->
                pinStore.set(pin)
                onChange { it.copy(pinEnabled = true, biometricEnabled = biometricAvailable) }
                action = PinAction.NONE
            },
            onDismiss = { action = PinAction.NONE },
        )
        PinAction.DISABLE -> if (!pinOn) action = PinAction.NONE else VerifyPinDialog(
            pinStore = pinStore,
            onVerified = {
                pinStore.clear()
                onChange { it.copy(pinEnabled = false, biometricEnabled = false) }
                action = PinAction.NONE
            },
            onDismiss = { action = PinAction.NONE },
        )
        PinAction.NONE -> Unit
    }
}

@Composable
private fun PinDialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                content()
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
            }
        }
    }
}

@Composable
private fun SetPinDialog(length: Int, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableStateOf(0) }
    val mismatchText = stringResource(R.string.settings_pin_mismatch)

    PinDialogFrame(onDismiss) {
        PinPad(
            length = length,
            title = if (first == null) stringResource(R.string.settings_pin_set_title) else stringResource(R.string.settings_pin_repeat_title),
            message = error, resetKey = round,
            onComplete = { pin ->
                val f = first
                when {
                    f == null -> { first = pin; error = null; round++ }
                    f == pin -> onDone(pin)
                    else -> { first = null; error = mismatchText; round++ }
                }
            },
        )
    }
}

@Composable
private fun VerifyPinDialog(pinStore: PinStore, onVerified: () -> Unit, onDismiss: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableStateOf(0) }
    val wrongText = stringResource(R.string.settings_pin_wrong)
    val lockedText = stringResource(R.string.settings_pin_locked)
    PinDialogFrame(onDismiss) {
        PinPad(
            length = pinStore.length, title = stringResource(R.string.settings_pin_current), message = error, resetKey = round,
            onComplete = { pin ->
                when (val r = pinStore.verify(pin)) {
                    PinResult.Ok -> onVerified()
                    is PinResult.Wrong -> { error = wrongText; round++ }
                    is PinResult.Locked -> { error = lockedText; round++ }
                }
            },
        )
    }
}

@Composable
fun NotificationsSection(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    SectionCard {
        GroupTitle(stringResource(R.string.settings_notifications))
        SettingSwitch(stringResource(R.string.settings_notify_payments), null, settings.notificationsEnabled) { v -> onChange { it.copy(notificationsEnabled = v) } }
        if (settings.notificationsEnabled) {
            DropdownField(
                stringResource(R.string.settings_notify_due),
                listOf(
                    listOf(1) to pluralStringResource(R.plurals.settings_before_days, 1, 1), listOf(3, 1) to stringResource(R.string.settings_notify_3_1),
                    listOf(7, 3, 1) to stringResource(R.string.settings_notify_7_3_1), emptyList<Int>() to stringResource(R.string.settings_notify_same_day),
                ),
                settings.notifyDaysBefore, { d -> onChange { it.copy(notifyDaysBefore = d) } },
                Modifier.padding(top = 4.dp),
            )
            DropdownField(
                stringResource(R.string.settings_notify_time),
                (7..22).map { it to "%02d:00".format(it) },
                settings.notifyHour, { h -> onChange { it.copy(notifyHour = h, notifyMinute = 0) } },
            )
            DropdownField(
                stringResource(R.string.settings_overdue_repeat),
                listOf(
                    0 to stringResource(R.string.settings_repeat_never), 1 to stringResource(R.string.settings_repeat_daily),
                    3 to pluralStringResource(R.plurals.settings_every_days, 3, 3), 7 to stringResource(R.string.settings_repeat_weekly),
                ),
                settings.overdueRepeatDays, { d -> onChange { it.copy(overdueRepeatDays = d) } },
            )
            DropdownField(
                stringResource(R.string.settings_notify_sub),
                listOf(
                    0 to stringResource(R.string.settings_sub_same_day), 1 to pluralStringResource(R.plurals.settings_before_days, 1, 1),
                    2 to pluralStringResource(R.plurals.settings_before_days, 2, 2), 3 to pluralStringResource(R.plurals.settings_before_days, 3, 3),
                ),
                settings.subscriptionNotifyDays, { d -> onChange { it.copy(subscriptionNotifyDays = d) } },
            )
            DropdownField(
                stringResource(R.string.settings_stale),
                listOf(
                    0 to stringResource(R.string.settings_stale_never), 14 to pluralStringResource(R.plurals.settings_after_days, 14, 14),
                    30 to pluralStringResource(R.plurals.settings_after_days, 30, 30), 60 to pluralStringResource(R.plurals.settings_after_days, 60, 60),
                ),
                settings.staleDebtDays, { d -> onChange { it.copy(staleDebtDays = d) } },
            )
            SettingSwitch(
                stringResource(R.string.settings_hide_amounts), stringResource(R.string.settings_hide_amounts_sub),
                settings.hideAmountsInNotifications, { v -> onChange { it.copy(hideAmountsInNotifications = v) } },
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            if (!app.kvity.notify.ReminderAlarm.canScheduleExact(context)) {
                Text(
                    stringResource(R.string.settings_exact_alarm_note),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { app.kvity.notify.ReminderAlarm.requestExactPermission(context) }) { Text(stringResource(R.string.settings_exact_alarm_allow)) }
            }
            TextButton(onClick = { app.kvity.notify.ReminderWorker.runNow(context) }) { Text(stringResource(R.string.settings_check_now)) }
        }
    }
}
