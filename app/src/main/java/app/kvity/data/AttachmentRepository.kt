package app.kvity.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AttachmentTooLargeException(val limitBytes: Long) : Exception("Файл больше ${limitBytes / 1024 / 1024} МБ")

/** Вложения (фото расписок, чеки, документы). Файлы копируются во внутреннее хранилище приложения. */
class AttachmentRepository(private val context: Context, private val db: KvityDatabase) {

    companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
        const val DIR = "attachments"
    }

    fun observe(type: AttachmentOwner, ownerId: String): Flow<List<AttachmentEntity>> =
        db.attachments().observeFor(type, ownerId)

    fun fileOf(att: AttachmentEntity): File = File(File(context.filesDir, DIR), att.path)

    suspend fun add(type: AttachmentOwner, ownerId: String, source: Uri, fallbackName: String? = null): AttachmentEntity =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            var name = fallbackName
            var size = -1L
            resolver.query(source, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                }
            }
            if (size > MAX_BYTES) throw AttachmentTooLargeException(MAX_BYTES)

            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val id = UUID.randomUUID().toString()
            val ext = name?.substringAfterLast('.', "")?.takeIf { it.length in 1..5 }?.let { ".$it" } ?: ""
            val target = File(dir, id + ext)
            var written = 0L
            resolver.openInputStream(source)!!.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        written += n
                        if (written > MAX_BYTES) { target.delete(); throw AttachmentTooLargeException(MAX_BYTES) }
                        out.write(buf, 0, n)
                    }
                }
            }
            val entity = AttachmentEntity(
                id = id, ownerType = type, ownerId = ownerId, fileName = name ?: target.name,
                mimeType = resolver.getType(source) ?: "application/octet-stream",
                sizeBytes = written, path = target.name, createdAt = System.currentTimeMillis(),
            )
            db.attachments().insert(entity)
            entity
        }

    suspend fun delete(att: AttachmentEntity) = withContext(Dispatchers.IO) {
        fileOf(att).delete()
        db.attachments().delete(att.id)
    }

    /** Удаляет вложения всех перечисленных владельцев (при удалении долга, платежей, подписки). */
    suspend fun deleteOwnedBy(ownerIds: Collection<String>) = withContext(Dispatchers.IO) {
        ownerIds.forEach { owner ->
            db.attachments().forOwner(owner).forEach { delete(it) }
        }
    }

    fun openIntent(att: AttachmentEntity): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", fileOf(att))
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, att.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Файл во временной папке для камеры: приложение камеры пишет в него по ссылке FileProvider. */
    fun newCameraTarget(): Pair<File, Uri> {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
