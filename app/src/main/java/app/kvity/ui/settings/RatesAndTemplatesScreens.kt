package app.kvity.ui.settings

import app.kvity.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kvity.data.ExchangeRateEntity
import app.kvity.data.ReminderTemplateEntity
import app.kvity.domain.Limits
import app.kvity.ui.components.ConfirmDialog
import app.kvity.ui.components.DropdownField
import app.kvity.ui.components.FormScreen
import app.kvity.ui.components.LabelRow
import app.kvity.ui.components.SectionCard
import app.kvity.ui.currencyOf
import app.kvity.ui.formatDate
import java.time.LocalDate

@Composable
fun RatesScreen(
    rates: List<ExchangeRateEntity>,
    currencies: List<String>,
    onSave: (from: String, to: String, rate: Double, id: String?) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<ExchangeRateEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ExchangeRateEntity?>(null) }
    pendingDelete?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.settings_rate_delete_title),
            text = "1 ${r.fromCode} = ${r.rate.toString().removeSuffix(".0").replace('.', ',')} ${r.toCode}",
            onConfirm = { pendingDelete = null; onDelete(r.id) },
            onDismiss = { pendingDelete = null },
        )
    }

    FormScreen(title = stringResource(R.string.settings_rates_title), onBack = onBack, actions = { TextButton(onClick = { adding = true }) { Text(stringResource(R.string.settings_rate_add)) } }) {
        Text(
            stringResource(R.string.settings_rates_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard {
            if (rates.isEmpty()) Text(stringResource(R.string.settings_rates_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            rates.forEach { r ->
                LabelRow(
                    onClick = { editing = r },
                    label = "1 ${r.fromCode} = ${r.rate.toString().removeSuffix(".0").replace('.', ',')} ${r.toCode}",
                    subtitle = stringResource(R.string.settings_rate_as_of, formatDate(r.date)),
                    trailing = { TextButton(onClick = { pendingDelete = r }) { Text("✕") } },
                )
            }
        }
    }

    if (adding || editing != null) {
        val e = editing
        var from by remember { mutableStateOf(e?.fromCode ?: currencies.first()) }
        var to by remember { mutableStateOf(e?.toCode ?: currencies.getOrElse(1) { currencies.first() }) }
        var text by remember { mutableStateOf(e?.rate?.toString()?.removeSuffix(".0")?.replace('.', ',') ?: "") }
        val rate = parseRate(text)
        val opts = currencies.map { it to "${currencyOf(it).symbol} $it" }
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(stringResource(if (e == null) R.string.settings_rate_new else R.string.settings_rate_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField(stringResource(R.string.settings_rate_one_unit), opts, from, { from = it })
                    DropdownField(stringResource(R.string.settings_rate_equals), opts, to, { to = it })
                    OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.settings_rate_field)) }, singleLine = true, isError = text.isNotBlank() && rate == null)
                }
            },
            confirmButton = {
                TextButton(enabled = rate != null && from != to, onClick = {
                    onSave(from, to, rate!!, e?.id); adding = false; editing = null
                }) { Text(stringResource(R.string.settings_save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false; editing = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

@Composable
fun TemplatesScreen(
    templates: List<ReminderTemplateEntity>,
    onSave: (name: String, text: String, id: String?) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<ReminderTemplateEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ReminderTemplateEntity?>(null) }
    pendingDelete?.let { t ->
        ConfirmDialog(
            title = stringResource(R.string.settings_tpl_delete_title),
            text = stringResource(R.string.settings_tpl_delete_text, t.name),
            onConfirm = { pendingDelete = null; onDelete(t.id) },
            onDismiss = { pendingDelete = null },
        )
    }

    FormScreen(title = stringResource(R.string.settings_tpl_title), onBack = onBack, actions = { TextButton(onClick = { adding = true }) { Text(stringResource(R.string.settings_tpl_add)) } }) {
        Text(
            stringResource(R.string.settings_tpl_vars),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        templates.forEach { t ->
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t.name, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = { editing = t }) { Text(stringResource(R.string.settings_tpl_edit_btn)) }
                        TextButton(onClick = { pendingDelete = t }) { Text(stringResource(R.string.settings_tpl_delete_btn)) }
                    }
                }
                Text(t.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (adding || editing != null) {
        val e = editing
        var name by remember { mutableStateOf(e?.name ?: "") }
        var text by remember { mutableStateOf(e?.text ?: "") }
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(stringResource(if (e == null) R.string.settings_tpl_new else R.string.settings_tpl_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(Limits.NAME) }, label = { Text(stringResource(R.string.settings_tpl_name)) }, singleLine = true)
                    OutlinedTextField(text, { text = it.take(Limits.TEMPLATE) }, label = { Text(stringResource(R.string.settings_tpl_text)) })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && text.isNotBlank(), onClick = {
                    onSave(name, text, e?.id); adding = false; editing = null
                }) { Text(stringResource(R.string.settings_save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false; editing = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

/** Курс из текстового поля: конечное число в разумных пределах, иначе null («1e999999999» и «Infinity» не проходят). */
internal fun parseRate(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in MIN_RATE..MAX_RATE }

private const val MIN_RATE = 1e-6
private const val MAX_RATE = 1e9
