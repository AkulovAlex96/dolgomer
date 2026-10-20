package app.kvity.data

import android.content.Context
import androidx.room.withTransaction
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupManager(
    private val context: Context,
    private val db: KvityDatabase,
    private val settings: SettingsStore,
) {
    private val attachmentsDir get() = File(context.filesDir, AttachmentRepository.DIR)

    suspend fun snapshot(): BackupData = BackupData(
        createdAt = System.currentTimeMillis(),
        counterparties = db.counterparties().all(),
        debts = db.debts().all(),
        payments = db.payments().all(),
        subscriptions = db.subscriptions().all(),
        shares = db.shares().all(),
        sharePayments = db.sharePayments().all(),
        attachments = db.attachments().all(),
        templates = db.templates().all(),
        reminderLog = db.reminderLog().all(),
        rates = db.rates().all(),
    )

    /** Пишет копию в [out] (файл, выбранный пользователем) и запоминает дату. */
    suspend fun create(out: OutputStream, password: String?) = withContext(Dispatchers.IO) {
        val data = snapshot()
        val files = data.attachments.map { it.path to File(attachmentsDir, it.path) }
        BackupCodec.write(out, data, files, password)
        settings.update { it.copy(lastBackupMillis = System.currentTimeMillis()) }
    }

    /** Заменяет все данные содержимым копии. Перед заменой сохраняет текущее состояние в каталог приложения. */
    suspend fun restore(input: InputStream, password: String?): BackupData = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "restore")
        val decoded = try {
            BackupCodec.read(input, password, temp)
        } catch (e: Exception) {
            temp.deleteRecursively()
            throw e
        }

        // страховка: текущие данные сохраняются, чтобы можно было откатиться вручную
        val safety = File(context.filesDir, "backups").apply { mkdirs() }
        File(safety, "before-restore-${System.currentTimeMillis()}.kvbak").outputStream().use { out ->
            val current = snapshot()
            BackupCodec.write(out, current, current.attachments.map { it.path to File(attachmentsDir, it.path) }, null)
        }
        // храним только три последние страховочные копии
        safety.listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }

        val d = decoded.data
        db.withTransaction {
            db.sharePayments().deleteAll()
            db.shares().deleteAll()
            db.subscriptions().deleteAll()
            db.attachments().deleteAll()
            db.reminderLog().deleteAll()
            db.payments().deleteAll()
            db.debts().deleteAll()
            db.templates().deleteAll()
            db.rates().deleteAll()
            db.counterparties().deleteAll()

            db.counterparties().insertAll(d.counterparties)
            db.debts().insertAll(d.debts)
            db.payments().insertAll(d.payments)
            db.subscriptions().insertAll(d.subscriptions)
            db.shares().insertAll(d.shares)
            db.sharePayments().insertAll(d.sharePayments)
            db.attachments().insertAll(d.attachments)
            db.templates().insertAll(d.templates)
            db.reminderLog().insertAll(d.reminderLog)
            db.rates().insertAll(d.rates)
        }

        attachmentsDir.deleteRecursively()
        attachmentsDir.mkdirs()
        decoded.attachmentsDir.listFiles()?.forEach { it.copyTo(File(attachmentsDir, it.name), overwrite = true) }
        temp.deleteRecursively()
        d
    }
}
