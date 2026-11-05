package app.kvity.ui.backup

import app.kvity.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.kvity.data.BackupCodec
import app.kvity.data.BackupFormatException
import app.kvity.data.BackupManager
import app.kvity.data.BackupSummary
import app.kvity.data.WrongPasswordException
import app.kvity.ui.components.FormScreen
import app.kvity.ui.components.SectionCard
import app.kvity.ui.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

private sealed interface Step {
    data object Idle : Step
    data object AskCreatePassword : Step
    data class AskRestorePassword(val uri: Uri, val wrong: Boolean) : Step
    data class ConfirmRestore(val uri: Uri, val password: String?, val summary: BackupSummary) : Step
}

@Composable
fun BackupScreen(manager: BackupManager, lastBackupMillis: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<Step>(Step.Idle) }
    var password by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            message = try {
                context.contentResolver.openOutputStream(uri)!!.use { manager.create(it, password) }
                resources.getString(R.string.backup_saved)
            } catch (e: Exception) {
                resources.getString(R.string.backup_save_failed, e.message)
            }
            busy = false
        }
    }

    /** Проверяет файл (и пароль) до вопроса о замене данных: мусор и повреждённые копии отклоняются сразу. */
    fun inspect(uri: Uri, pass: String?) {
        scope.launch {
            busy = true
            try {
                val summary = context.contentResolver.openInputStream(uri)!!.use { manager.inspect(it, pass) }
                step = Step.ConfirmRestore(uri, pass, summary)
            } catch (e: WrongPasswordException) {
                step = Step.AskRestorePassword(uri, wrong = true)
            } catch (e: BackupFormatException) {
                message = e.message
                step = Step.Idle
            } catch (e: Exception) {
                message = resources.getString(R.string.backup_read_failed_detail, e.message)
                step = Step.Idle
            }
            busy = false
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val head = ByteArray(4)
        val read = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.read(head) } }.getOrDefault(-1)
        when {
            read < 4 -> { message = resources.getString(R.string.backup_read_failed); step = Step.Idle }
            BackupCodec.isEncrypted(head) -> step = Step.AskRestorePassword(uri, wrong = false)
            else -> inspect(uri, null)
        }
    }

    fun restore(uri: Uri, pass: String?) {
        scope.launch {
            busy = true
            message = try {
                context.contentResolver.openInputStream(uri)!!.use { manager.restore(it, pass) }
                resources.getString(R.string.backup_restored)
            } catch (e: WrongPasswordException) {
                step = Step.AskRestorePassword(uri, wrong = true)
                null
            } catch (e: BackupFormatException) {
                e.message
            } catch (e: Exception) {
                resources.getString(R.string.backup_restore_failed, e.message)
            }
            busy = false
        }
    }

    FormScreen(title = stringResource(R.string.backup_title), onBack = onBack) {
        SectionCard {
            Text(
                if (lastBackupMillis == 0L) stringResource(R.string.backup_none_yet)
                else stringResource(R.string.backup_last, Instant.ofEpochMilli(lastBackupMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    .let { "%02d.%02d.%d".format(it.dayOfMonth, it.monthValue, it.year) }),
            )
            Text(
                stringResource(R.string.backup_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(enabled = !busy, onClick = { step = Step.AskCreatePassword }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_create)) }
        OutlinedButton(enabled = !busy, onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_restore)) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (busy) Text(stringResource(R.string.backup_wait), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    when (val s = step) {
        Step.Idle -> Unit
        Step.AskCreatePassword -> PasswordDialog(
            title = stringResource(R.string.backup_password_title), hint = stringResource(R.string.backup_password_hint),
            wrong = false, confirmLabel = stringResource(R.string.backup_next), repeat = true, onDismiss = { step = Step.Idle },
            onConfirm = { p ->
                password = p.ifEmpty { null }
                step = Step.Idle
                val d = LocalDate.now()
                create.launch("kvity-%d-%02d-%02d.kvbak".format(d.year, d.monthValue, d.dayOfMonth))
            },
        )
        is Step.AskRestorePassword -> PasswordDialog(
            title = stringResource(R.string.backup_protected), hint = null, wrong = s.wrong, confirmLabel = stringResource(R.string.backup_next),
            onDismiss = { step = Step.Idle },
            onConfirm = { p -> step = Step.Idle; inspect(s.uri, p) },
        )
        is Step.ConfirmRestore -> AlertDialog(
            onDismissRequest = { step = Step.Idle },
            title = { Text(stringResource(R.string.backup_replace_title)) },
            text = {
                val c = s.summary
                Text(
                    stringResource(
                        R.string.backup_replace_text,
                        formatDate(Instant.ofEpochMilli(c.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()),
                        c.debts, c.payments, c.subscriptions, c.counterparties,
                    ),
                )
            },
            confirmButton = { TextButton(onClick = { step = Step.Idle; restore(s.uri, s.password) }) { Text(stringResource(R.string.backup_do_restore)) } },
            dismissButton = { TextButton(onClick = { step = Step.Idle }) { Text(stringResource(R.string.backup_cancel)) } },
        )
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    hint: String?,
    wrong: Boolean,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    repeat: Boolean = false,
) {
    var text by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    // при создании пароль вводится дважды: опечатка сделала бы копию нечитаемой, а забытый пароль восстановить нельзя
    val mismatch = repeat && text != again
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hint?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.backup_password)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), isError = wrong,
                    supportingText = if (wrong) ({ Text(stringResource(R.string.backup_wrong_password)) }) else null,
                )
                if (repeat && text.isNotEmpty()) {
                    OutlinedTextField(
                        value = again, onValueChange = { again = it }, label = { Text(stringResource(R.string.backup_repeat_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), isError = again.isNotEmpty() && mismatch,
                        supportingText = if (again.isNotEmpty() && mismatch) ({ Text(stringResource(R.string.backup_passwords_differ)) }) else null,
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = !mismatch, onClick = { onConfirm(text) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}
