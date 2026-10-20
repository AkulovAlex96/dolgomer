package app.kvity.ui.settings

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
        GroupTitle("Безопасность")
        SettingSwitch(
            "PIN-код", if (pinOn) "Вход по ${pinStore.length}-значному коду" else "Защитить вход в приложение",
            pinOn,
        ) { on -> action = if (on) PinAction.SET else PinAction.DISABLE }

        if (!pinOn) {
            DropdownField("Длина PIN", listOf(4 to "4 цифры", 6 to "6 цифр"), pinLength, { pinLength = it })
        } else {
            SettingSwitch(
                "Отпечаток пальца", if (biometricAvailable) null else "Не настроен на устройстве",
                settings.biometricEnabled && biometricAvailable,
            ) { v -> if (biometricAvailable) onChange { it.copy(biometricEnabled = v) } }
            DropdownField(
                "Блокировать через",
                listOf(0 to "сразу", 30 to "30 секунд", 60 to "1 минуту", 300 to "5 минут"),
                settings.lockTimeoutSeconds, { s -> onChange { it.copy(lockTimeoutSeconds = s) } },
                Modifier.padding(top = 4.dp),
            )
        }
        SettingSwitch(
            "Скрывать экран", "Запрет скриншотов и скрытие в списке недавних приложений",
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
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    }
}

@Composable
private fun SetPinDialog(length: Int, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableStateOf(0) }

    PinDialogFrame(onDismiss) {
        PinPad(
            length = length,
            title = if (first == null) "Придумайте PIN-код" else "Повторите PIN-код",
            message = error, resetKey = round,
            onComplete = { pin ->
                val f = first
                when {
                    f == null -> { first = pin; error = null; round++ }
                    f == pin -> onDone(pin)
                    else -> { first = null; error = "PIN-коды не совпали, начните заново"; round++ }
                }
            },
        )
    }
}

@Composable
private fun VerifyPinDialog(pinStore: PinStore, onVerified: () -> Unit, onDismiss: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableStateOf(0) }
    PinDialogFrame(onDismiss) {
        PinPad(
            length = pinStore.length, title = "Введите текущий PIN-код", message = error, resetKey = round,
            onComplete = { pin ->
                when (val r = pinStore.verify(pin)) {
                    PinResult.Ok -> onVerified()
                    is PinResult.Wrong -> { error = "Неверный PIN"; round++ }
                    is PinResult.Locked -> { error = "Слишком много попыток, подождите"; round++ }
                }
            },
        )
    }
}

@Composable
fun NotificationsSection(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    SectionCard {
        GroupTitle("Уведомления")
        SettingSwitch("Напоминания о платежах", null, settings.notificationsEnabled) { v -> onChange { it.copy(notificationsEnabled = v) } }
        if (settings.notificationsEnabled) {
            DropdownField(
                "О сроке долга",
                listOf(
                    listOf(1) to "за 1 день", listOf(3, 1) to "за 3 и 1 день",
                    listOf(7, 3, 1) to "за 7, 3 и 1 день", emptyList<Int>() to "только в день срока",
                ),
                settings.notifyDaysBefore, { d -> onChange { it.copy(notifyDaysBefore = d) } },
                Modifier.padding(top = 4.dp),
            )
            DropdownField(
                "Время",
                (7..22).map { it to "%02d:00".format(it) },
                settings.notifyHour, { h -> onChange { it.copy(notifyHour = h, notifyMinute = 0) } },
            )
            DropdownField(
                "При просрочке повторять",
                listOf(0 to "не повторять", 1 to "каждый день", 3 to "каждые 3 дня", 7 to "раз в неделю"),
                settings.overdueRepeatDays, { d -> onChange { it.copy(overdueRepeatDays = d) } },
            )
            DropdownField(
                "О списании подписки",
                listOf(0 to "в день списания", 1 to "за 1 день", 2 to "за 2 дня", 3 to "за 3 дня"),
                settings.subscriptionNotifyDays, { d -> onChange { it.copy(subscriptionNotifyDays = d) } },
            )
            SettingSwitch(
                "Скрывать суммы и имена", "В тексте уведомлений, чтобы не было видно на экране блокировки",
                settings.hideAmountsInNotifications, { v -> onChange { it.copy(hideAmountsInNotifications = v) } },
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            TextButton(onClick = { app.kvity.notify.ReminderWorker.runNow(context) }) { Text("Проверить сейчас") }
        }
    }
}
