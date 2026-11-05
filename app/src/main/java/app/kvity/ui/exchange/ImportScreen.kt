package app.kvity.ui.exchange

import app.kvity.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.kvity.KvityApp
import app.kvity.data.ImportReport
import app.kvity.domain.Direction
import app.kvity.exchange.Lang
import app.kvity.exchange.Csv
import app.kvity.exchange.DebtImport
import app.kvity.exchange.ImportBatch
import app.kvity.exchange.Importer
import app.kvity.ui.components.FormScreen
import app.kvity.ui.components.LabelRow
import app.kvity.ui.components.SectionCard
import app.kvity.ui.formatDate
import app.kvity.ui.formatMoney
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_BYTES = 10 * 1024 * 1024

/** Читает не больше [limit] байт (InputStream.readNBytes есть только с API 33, а minSdk у приложения 26). */
private fun InputStream.readUpTo(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (total < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - total))
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}

/** Имя файла, которое видит пользователь; lastPathSegment для документов из «Загрузок» даёт просто число. */
private fun displayNameOf(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

@Composable
fun ImportScreen(app: KvityApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    var batch by remember { mutableStateOf<ImportBatch?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<ImportReport?>(null) }
    var busy by remember { mutableStateOf(false) }

    val saveTemplate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(Csv.write(DebtImport.template(Lang.system())).toByteArray()) }
                }.isSuccess
                withContext(Dispatchers.Main) { Toast.makeText(context, resources.getString(if (ok) R.string.import_ui_template_saved else R.string.import_ui_save_failed), Toast.LENGTH_SHORT).show() }
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = null
            report = null
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { it.readUpTo(MAX_BYTES + 1) }.takeIf { it.size <= MAX_BYTES }
                }.getOrNull()
            }
            if (bytes == null) {
                error = resources.getString(R.string.import_ui_read_failed)
                batch = null
            } else {
                fileName = displayNameOf(context, uri)
                batch = withContext(Dispatchers.Default) { Importer.parse(bytes, lang = Lang.system()) }
            }
        }
    }

    FormScreen(title = stringResource(R.string.import_ui_title), onBack = onBack) {
        SectionCard {
            Text(
                stringResource(R.string.import_ui_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.import_ui_columns),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = { saveTemplate.launch("kvity-import-template.csv") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_ui_download_template)) }
        Button(enabled = !busy, onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_ui_pick)) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        report?.let { r ->
            SectionCard {
                Text(stringResource(R.string.import_ui_done, r.added), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(
                    listOf(
                        stringResource(R.string.import_ui_l_debts) to r.debts, stringResource(R.string.import_ui_l_payments) to r.payments,
                        stringResource(R.string.import_ui_l_subs) to r.subscriptions, stringResource(R.string.import_ui_l_shares) to r.shares,
                        stringResource(R.string.import_ui_l_people) to r.people,
                    ).filter { it.second > 0 }.joinToString(", ") { "${it.first}: ${it.second}" }.ifEmpty { stringResource(R.string.import_ui_nothing_new) },
                )
                if (r.skippedDuplicates > 0) Text(stringResource(R.string.import_ui_skipped_duplicates, r.skippedDuplicates), color = MaterialTheme.colorScheme.onSurfaceVariant)
                r.problems.forEach { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
        }

        // сколько записей файла уже есть в приложении: считается по тем же правилам, что и при применении
        val duplicates by produceState(0, batch) { value = batch?.let { runCatching { app.importer.countDuplicates(it) }.getOrDefault(0) } ?: 0 }
        batch?.let { b ->
            SectionCard {
                Text(fileName ?: stringResource(R.string.import_ui_file), fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(
                        stringResource(R.string.import_ui_l_debts) to b.debts.size, stringResource(R.string.import_ui_l_payments) to b.payments.size,
                        stringResource(R.string.import_ui_l_subs) to b.subscriptions.size, stringResource(R.string.import_ui_l_shares) to b.shares.size,
                        stringResource(R.string.import_ui_l_people) to b.people.size,
                    ).filter { it.second > 0 }.joinToString(", ") { "${it.first}: ${it.second}" }.ifEmpty { stringResource(R.string.import_ui_nothing_found) },
                    color = MaterialTheme.colorScheme.primary,
                )
                if (duplicates > 0) Text(stringResource(R.string.import_ui_already_have, duplicates), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (b.errors.isNotEmpty()) Text(stringResource(R.string.import_ui_error_rows, b.errors.size), color = MaterialTheme.colorScheme.error)
                if (b.ignoredSheets.isNotEmpty()) Text(stringResource(R.string.import_ui_ignored_sheets, b.ignoredSheets.joinToString()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (b.errors.isNotEmpty()) {
                SectionCard {
                    Text(stringResource(R.string.import_ui_errors_title), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    b.errors.take(20).forEach { e ->
                        val where = listOfNotNull(e.sheet, if (e.line > 0) stringResource(R.string.import_ui_line, e.line) else null).joinToString(", ")
                        Text((if (where.isNotEmpty()) "$where: " else "") + e.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    if (b.errors.size > 20) Text(stringResource(R.string.import_ui_and_more, b.errors.size - 20), fontSize = 12.sp)
                }
            }
            if (b.debts.isNotEmpty()) {
                SectionCard {
                    Text(stringResource(R.string.import_ui_preview), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    b.debts.take(6).forEach { row ->
                        LabelRow(
                            label = row.counterparty,
                            subtitle = stringResource(if (row.direction == Direction.OWED_TO_ME) R.string.import_ui_owed_to_me else R.string.import_ui_i_owe) +
                                (row.dueDate?.let { " · " + stringResource(R.string.import_ui_until, formatDate(it)) } ?: ""),
                            trailing = { Text(formatMoney(row.principalMinor, row.currency), fontWeight = FontWeight.Bold) },
                        )
                    }
                    if (b.debts.size > 6) Text(stringResource(R.string.import_ui_and_more, b.debts.size - 6), fontSize = 12.sp)
                }
            }
            if (b.total > 0 && b.total - duplicates <= 0) {
                Text(stringResource(R.string.import_ui_all_present), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (b.total > 0) {
                Button(
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            busy = true
                            report = runCatching { app.importer.apply(b) }.getOrElse {
                                error = resources.getString(R.string.import_ui_failed, it.message)
                                null
                            }
                            if (report != null) batch = null
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.import_ui_import_n, b.total - duplicates)) }
            }
        }
    }
}
