package app.kvity.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kvity.R
import app.kvity.data.NotifyMode
import app.kvity.data.ReminderPlanner

/** Кнопка «Уведомления» с текущим режимом; по нажатию открывает диалог выбора (ТЗ 3.6). */
@Composable
fun NotifyRulesButton(
    mode: NotifyMode, days: String?, onSave: (NotifyMode, String?) -> Unit, modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val summary = when (mode) {
        NotifyMode.DEFAULT -> stringResource(R.string.nr_mode_default)
        NotifyMode.OFF -> stringResource(R.string.nr_mode_off)
        NotifyMode.CUSTOM -> stringResource(R.string.nr_summary_custom, ReminderPlanner.parseDays(days).joinToString(", "))
    }
    OutlinedButton(onClick = { open = true }, modifier = modifier.fillMaxWidth()) {
        Text(stringResource(R.string.nr_title) + ": " + summary)
    }
    if (open) NotifyRulesDialog(mode, days, onDismiss = { open = false }, onSave = { m, d -> open = false; onSave(m, d) })
}

@Composable
private fun NotifyRulesDialog(mode: NotifyMode, days: String?, onDismiss: () -> Unit, onSave: (NotifyMode, String?) -> Unit) {
    var selected by remember { mutableStateOf(mode) }
    var text by remember { mutableStateOf(days.orEmpty()) }
    val parsed = ReminderPlanner.parseDays(text)
    val valid = selected != NotifyMode.CUSTOM || parsed.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nr_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    NotifyMode.DEFAULT to R.string.nr_mode_default,
                    NotifyMode.OFF to R.string.nr_mode_off,
                    NotifyMode.CUSTOM to R.string.nr_mode_custom,
                ).forEach { (m, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = m },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == m, onClick = { selected = m })
                        Text(stringResource(label))
                    }
                }
                if (selected == NotifyMode.CUSTOM) {
                    OutlinedTextField(
                        text, { text = it.take(30) }, label = { Text(stringResource(R.string.nr_days_label)) },
                        placeholder = { Text(stringResource(R.string.nr_days_hint)) },
                        singleLine = true, isError = parsed.isEmpty(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(selected, if (selected == NotifyMode.CUSTOM) parsed.joinToString(",") else null)
            }) { Text(stringResource(R.string.nr_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.nr_cancel)) } },
    )
}
