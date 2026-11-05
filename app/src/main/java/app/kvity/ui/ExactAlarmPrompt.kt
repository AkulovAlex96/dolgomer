package app.kvity.ui

import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.kvity.KvityApp
import app.kvity.R
import app.kvity.data.Settings
import app.kvity.notify.ExactAlarmPolicy
import app.kvity.notify.Notifier
import app.kvity.notify.ReminderAlarm

/** Нужно ли сейчас предложить разрешение на точные будильники (пересчитывается при возврате в приложение). */
@Composable
fun rememberShouldOfferExactAlarm(settings: Settings): Boolean {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    if (tick < 0) return false // читаем tick, чтобы возврат в приложение вызывал пересчёт
    return ExactAlarmPolicy.shouldOffer(
        sdk = Build.VERSION.SDK_INT,
        canScheduleExact = ReminderAlarm.canScheduleExact(context),
        alreadyAsked = settings.exactAlarmAsked,
        notificationsEnabled = settings.notificationsEnabled,
        canPost = Notifier.canPost(context),
    )
}

/** Запоминает, что предложение показано: повторно не навязываем. */
@Composable
fun MarkExactAlarmAsked() {
    val app = LocalContext.current.applicationContext as KvityApp
    LaunchedEffect(Unit) { app.settings.update { it.copy(exactAlarmAsked = true) } }
}

/** Диалог на главном экране: один раз, с переходом в системные настройки. */
@Composable
fun ExactAlarmDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    MarkExactAlarmAsked()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exact_alarm_title)) },
        text = { Text(stringResource(R.string.exact_alarm_text)) },
        confirmButton = {
            TextButton(onClick = { ReminderAlarm.requestExactPermission(context); onDismiss() }) {
                Text(stringResource(R.string.exact_alarm_allow))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.exact_alarm_later)) } },
    )
}
