package app.kvity.ui.exchange

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.kvity.KvityApp
import app.kvity.exchange.Csv
import app.kvity.exchange.DebtImport
import app.kvity.exchange.ImportParseResult
import app.kvity.ui.components.FormScreen
import app.kvity.ui.components.LabelRow
import app.kvity.ui.components.SectionCard
import app.kvity.ui.formatDate
import app.kvity.ui.formatMoney
import app.kvity.domain.Direction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_BYTES = 5 * 1024 * 1024

@Composable
fun ImportScreen(app: KvityApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ImportParseResult?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val saveTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(Csv.write(DebtImport.template()).toByteArray()) }
                }.isSuccess
                withContext(Dispatchers.Main) { Toast.makeText(context, if (ok) "Шаблон сохранён" else "Не удалось сохранить", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = null
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        val bytes = input.readNBytes(MAX_BYTES + 1)
                        if (bytes.size > MAX_BYTES) null else String(bytes, Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (text == null) {
                message = "Не удалось прочитать файл или он больше 5 МБ"
                result = null
            } else {
                fileName = uri.lastPathSegment?.substringAfterLast('/')
                result = DebtImport.parse(text)
            }
        }
    }

    FormScreen(title = "Импорт долгов", onBack = onBack) {
        SectionCard {
            Text(
                "Загрузите CSV-файл с долгами. Обязательные колонки: Контрагент, Направление («мне должны» или «я должен»), Валюта (RUB, KZT, USD…), Сумма (можно «50к»). " +
                    "Необязательные: Выплачено, Дата выдачи, Срок, Комментарий. Файл, выгруженный из этого приложения, тоже подходит.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { saveTemplate.launch("kvity-import-template.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Скачать шаблон") }
        Button(enabled = !busy, onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Выбрать файл CSV") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        result?.let { r ->
            SectionCard {
                Text(fileName ?: "Файл", fontWeight = FontWeight.SemiBold)
                Text("Готово к добавлению: ${r.rows.size}", color = MaterialTheme.colorScheme.primary)
                if (r.errors.isNotEmpty()) Text("Строк с ошибками: ${r.errors.size}", color = MaterialTheme.colorScheme.error)
            }
            if (r.errors.isNotEmpty()) {
                SectionCard {
                    Text("Ошибки (эти строки не будут добавлены)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    r.errors.take(20).forEach { e ->
                        Text(if (e.line > 0) "Строка ${e.line}: ${e.message}" else e.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    if (r.errors.size > 20) Text("…и ещё ${r.errors.size - 20}", fontSize = 12.sp)
                }
            }
            if (r.rows.isNotEmpty()) {
                SectionCard {
                    Text("Предпросмотр", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    r.rows.take(8).forEach { row ->
                        LabelRow(
                            label = row.counterparty,
                            subtitle = (if (row.direction == Direction.OWED_TO_ME) "мне должны" else "я должен") +
                                (row.dueDate?.let { " · до ${formatDate(it)}" } ?: ""),
                            trailing = { Text(formatMoney(row.principalMinor, row.currency), fontWeight = FontWeight.Bold) },
                        )
                    }
                    if (r.rows.size > 8) Text("…и ещё ${r.rows.size - 8}", fontSize = 12.sp)
                }
                Button(
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            busy = true
                            val summary = app.debts.importDebts(r.rows)
                            message = "Добавлено: ${summary.added}" +
                                if (summary.skippedDuplicates > 0) ", пропущено дубликатов: ${summary.skippedDuplicates}" else ""
                            result = null
                            busy = false
                        }
                    },
                ) { Text("Добавить ${r.rows.size}") }
            }
        }
        if (result == null) message?.takeIf { it.startsWith("Добавлено") }?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
