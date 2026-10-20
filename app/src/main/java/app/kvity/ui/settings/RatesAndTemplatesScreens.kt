package app.kvity.ui.settings

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

    FormScreen(title = "Курсы валют", onBack = onBack, actions = { TextButton(onClick = { adding = true }) { Text("+ Курс") } }) {
        Text(
            "Курсы вводятся вручную и нужны только для необязательного пересчёта итогов в одну валюту. Без них все суммы показываются раздельно.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard {
            if (rates.isEmpty()) Text("Курсов нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            rates.forEach { r ->
                LabelRow(
                    onClick = { editing = r },
                    label = "1 ${r.fromCode} = ${r.rate.toString().removeSuffix(".0").replace('.', ',')} ${r.toCode}",
                    subtitle = "на ${formatDate(r.date)}",
                    trailing = { TextButton(onClick = { onDelete(r.id) }) { Text("✕") } },
                )
            }
        }
    }

    if (adding || editing != null) {
        val e = editing
        var from by remember { mutableStateOf(e?.fromCode ?: currencies.first()) }
        var to by remember { mutableStateOf(e?.toCode ?: currencies.getOrElse(1) { currencies.first() }) }
        var text by remember { mutableStateOf(e?.rate?.toString()?.removeSuffix(".0")?.replace('.', ',') ?: "") }
        val rate = text.trim().replace(',', '.').toDoubleOrNull()
        val opts = currencies.map { it to "${currencyOf(it).symbol} $it" }
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(if (e == null) "Новый курс" else "Изменить курс") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField("1 единица", opts, from, { from = it })
                    DropdownField("равна", opts, to, { to = it })
                    OutlinedTextField(text, { text = it }, label = { Text("Курс") }, singleLine = true, isError = text.isNotBlank() && (rate == null || rate <= 0))
                }
            },
            confirmButton = {
                TextButton(enabled = rate != null && rate > 0 && from != to, onClick = {
                    onSave(from, to, rate!!, e?.id); adding = false; editing = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { adding = false; editing = null }) { Text("Отмена") } },
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

    FormScreen(title = "Шаблоны сообщений", onBack = onBack, actions = { TextButton(onClick = { adding = true }) { Text("+ Шаблон") } }) {
        Text(
            "Переменные: {имя}, {сумма}, {остаток}, {срок}, {дней_просрочки}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        templates.forEach { t ->
            SectionCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t.name, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = { editing = t }) { Text("Изменить") }
                        TextButton(onClick = { onDelete(t.id) }) { Text("Удалить") }
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
            title = { Text(if (e == null) "Новый шаблон" else "Изменить шаблон") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(text, { text = it }, label = { Text("Текст") })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && text.isNotBlank(), onClick = {
                    onSave(name, text, e?.id); adding = false; editing = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { adding = false; editing = null }) { Text("Отмена") } },
        )
    }
}
