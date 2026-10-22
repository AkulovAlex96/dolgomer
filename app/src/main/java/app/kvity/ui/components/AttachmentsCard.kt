package app.kvity.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kvity.data.AttachmentEntity
import app.kvity.data.AttachmentOwner
import app.kvity.data.AttachmentRepository
import app.kvity.data.AttachmentTooLargeException
import app.kvity.ui.formatFileSize
import kotlinx.coroutines.launch

/** Список вложений владельца с добавлением фото и файлов. */
@Composable
fun AttachmentsCard(
    repo: AttachmentRepository,
    type: AttachmentOwner,
    ownerId: String,
    title: String = "Вложения",
    framed: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by repo.observe(type, ownerId).collectAsStateWithLifecycle(emptyList())
    var confirmDelete by remember { mutableStateOf<AttachmentEntity?>(null) }
    var cameraTarget by remember { mutableStateOf<Pair<java.io.File, Uri>?>(null) }

    fun add(uri: Uri, name: String? = null) {
        scope.launch {
            try {
                repo.add(type, ownerId, uri, name)
            } catch (e: AttachmentTooLargeException) {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось добавить файл", Toast.LENGTH_LONG).show()
            }
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { add(it) } }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val target = cameraTarget
        if (ok && target != null) add(target.second, "Фото.jpg")
        cameraTarget?.first?.delete()
        cameraTarget = null
    }

    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = { cameraTarget = repo.newCameraTarget().also { takePhoto.launch(it.second) } }) { Text("Фото") }
                TextButton(onClick = { pickFile.launch(arrayOf("*/*")) }) { Text("Файл") }
            }
        }
        if (items.isEmpty()) Text("Расписки, чеки и другие файлы можно приложить сюда.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { att ->
            LabelRow(
                onClick = { runCatching { context.startActivity(repo.openIntent(att)) }.onFailure {
                    Toast.makeText(context, "Нет приложения для открытия файла", Toast.LENGTH_SHORT).show()
                } },
                label = att.fileName,
                subtitle = formatFileSize(att.sizeBytes),
                trailing = { TextButton(onClick = { confirmDelete = att }) { Text("✕") } },
            )
        }
    }
    if (framed) SectionCard { body() } else Column { body() }

    confirmDelete?.let { att ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить вложение?") },
            text = { Text(att.fileName) },
            confirmButton = { TextButton(onClick = { scope.launch { repo.delete(att) }; confirmDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
        )
    }
}
