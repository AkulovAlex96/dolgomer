package app.kvity.ui.exchange

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.kvity.KvityApp
import app.kvity.domain.Direction
import app.kvity.exchange.Csv
import app.kvity.exchange.ExportBuilder
import app.kvity.exchange.ExportOptions
import app.kvity.exchange.ExportTable
import app.kvity.exchange.Xlsx
import app.kvity.ui.components.ChipRow
import app.kvity.ui.components.FormScreen
import app.kvity.ui.components.SectionCard
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Format(val title: String) { XLSX("Excel (.xlsx)"), CSV("CSV") }

private class ExportFile(val name: String, val mime: String, val bytes: ByteArray)

@Composable
fun ExportScreen(app: KvityApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var format by remember { mutableStateOf(Format.XLSX) }
    var direction by remember { mutableStateOf<Direction?>(null) }
    var tables by remember { mutableStateOf(setOf(ExportTable.DEBTS, ExportTable.PAYMENTS, ExportTable.SUBSCRIPTIONS)) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ExportFile?>(null) }

    suspend fun build(): ExportFile = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val built = ExportBuilder.build(
            ExportOptions(tables, direction),
            app.debts.observeDebts().first(), app.subscriptions.observeAll().first(), app.people.observe().first(), today,
        )
        val stamp = "%d-%02d-%02d".format(today.year, today.monthValue, today.dayOfMonth)
        val out = ByteArrayOutputStream()
        when {
            format == Format.XLSX -> {
                Xlsx.write(out, built)
                ExportFile("kvity-$stamp.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray())
            }
            built.size == 1 -> ExportFile("kvity-${built[0].name.lowercase().replace(' ', '-')}-$stamp.csv", "text/csv", Csv.write(built[0]).toByteArray())
            else -> {
                ZipOutputStream(out).use { z ->
                    built.forEach { t ->
                        z.putNextEntry(ZipEntry("${t.name}.csv"))
                        z.write(Csv.write(t).toByteArray())
                        z.closeEntry()
                    }
                }
                ExportFile("kvity-$stamp.zip", "application/zip", out.toByteArray())
            }
        }
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = pending
        pending = null
        if (uri != null && file != null) {
            scope.launch(Dispatchers.IO) {
                val ok = runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(file.bytes) } }.isSuccess
                withContext(Dispatchers.Main) { Toast.makeText(context, if (ok) "Файл сохранён" else "Не удалось сохранить", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    FormScreen(title = "Экспорт", onBack = onBack) {
        SectionCard {
            Text("Формат", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipRow(Format.entries.map { it to it.title }, format, { format = it })
            Text(
                if (format == Format.XLSX) "Одна книга, каждая таблица на своём листе."
                else "Для нескольких таблиц получится архив .zip с отдельным файлом на каждую. Разделитель «;», кодировка UTF-8: открывается в Excel.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard {
            Text("Что выгрузить", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExportTable.entries.forEach { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = t in tables, onCheckedChange = { on -> tables = if (on) tables + t else tables - t })
                    Text(t.title)
                }
            }
        }
        SectionCard {
            Text("Долги и платежи", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipRow(
                listOf<Pair<Direction?, String>>(null to "Все", Direction.OWED_TO_ME to "Мне должны", Direction.I_OWE to "Я должен"),
                direction, { direction = it },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = tables.isNotEmpty() && !busy, modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        busy = true
                        val f = build()
                        pending = f
                        save.launch(f.name)
                        busy = false
                    }
                },
            ) { Text("Сохранить в файл") }
            OutlinedButton(
                enabled = tables.isNotEmpty() && !busy, modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        busy = true
                        val f = build()
                        val file = withContext(Dispatchers.IO) {
                            File(context.cacheDir, "share").apply { mkdirs() }.let { dir ->
                                dir.listFiles()?.forEach { it.delete() }
                                File(dir, f.name).also { it.writeBytes(f.bytes) }
                            }
                        }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = f.mime
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, "Отправить файл"))
                        busy = false
                    }
                },
            ) { Text("Поделиться") }
        }
    }
}

