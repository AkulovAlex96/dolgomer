package app.kvity.ui.exchange

import app.kvity.R
import androidx.compose.ui.res.stringResource
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
import app.kvity.exchange.Lang
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

private fun tableTitle(t: ExportTable): Int = when (t) {
    ExportTable.DEBTS -> R.string.export_table_debts
    ExportTable.PAYMENTS -> R.string.export_table_payments
    ExportTable.SUBSCRIPTIONS -> R.string.export_table_subscriptions
    ExportTable.SHARES -> R.string.export_table_shares
    ExportTable.PEOPLE -> R.string.export_table_people
}

private class ExportFile(val name: String, val mime: String, val bytes: ByteArray)

@Composable
fun ExportScreen(app: KvityApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
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
            app.debts.observeDebts(includeArchived = true).first(), app.subscriptions.observeAll().first(), app.people.observe().first(), today,
            lang = Lang.system(),
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
                withContext(Dispatchers.Main) { Toast.makeText(context, resources.getString(if (ok) R.string.export_file_saved else R.string.export_save_failed), Toast.LENGTH_SHORT).show() }
            }
        }
    }

    FormScreen(title = stringResource(R.string.export_title), onBack = onBack) {
        SectionCard {
            Text(stringResource(R.string.export_format), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipRow(Format.entries.map { it to it.title }, format, { format = it })
            Text(
                stringResource(if (format == Format.XLSX) R.string.export_xlsx_note else R.string.export_csv_note),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard {
            Text(stringResource(R.string.export_what), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExportTable.entries.forEach { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = t in tables, onCheckedChange = { on -> tables = if (on) tables + t else tables - t })
                    Text(stringResource(tableTitle(t)))
                }
            }
        }
        SectionCard {
            Text(stringResource(R.string.export_debts_and_payments), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipRow(
                listOf<Pair<Direction?, String>>(
                    null to stringResource(R.string.export_all), Direction.OWED_TO_ME to stringResource(R.string.export_owed_to_me),
                    Direction.I_OWE to stringResource(R.string.export_i_owe),
                ),
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
            ) { Text(stringResource(R.string.export_save_file)) }
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
                        context.startActivity(Intent.createChooser(send, resources.getString(R.string.export_send_file)))
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.export_share)) }
        }
    }
}

