package app.kvity.data

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Все данные приложения для резервной копии. */
@Serializable
data class BackupData(
    val version: Int = BackupCodec.VERSION,
    val createdAt: Long,
    val counterparties: List<CounterpartyEntity>,
    val debts: List<DebtEntity>,
    val payments: List<PaymentEntity>,
    val subscriptions: List<SubscriptionEntity>,
    val shares: List<SubscriptionShareEntity>,
    val sharePayments: List<SharePaymentEntity>,
    val attachments: List<AttachmentEntity>,
    val templates: List<ReminderTemplateEntity>,
    val reminderLog: List<ReminderLogEntity>,
    val rates: List<ExchangeRateEntity>,
)

class BackupFormatException(message: String) : Exception(message)
class WrongPasswordException : Exception("Неверный пароль или файл повреждён")

/**
 * Формат файла: 4 байта заголовка, затем архив zip (data.json и папка attachments).
 * «KVB0» архив без пароля. «KVB1» архив зашифрован AES-256-GCM, ключ из пароля через PBKDF2.
 */
object BackupCodec {
    const val VERSION = 1
    private const val PLAIN = "KVB0"
    private const val ENCRYPTED = "KVB1"
    private const val ITERATIONS = 120_000

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun isEncrypted(head: ByteArray): Boolean = String(head, 0, minOf(4, head.size), Charsets.ISO_8859_1) == ENCRYPTED

    fun write(out: OutputStream, data: BackupData, files: List<Pair<String, File>>, password: String?) {
        var target = out
        if (password.isNullOrEmpty()) {
            out.write(PLAIN.toByteArray(Charsets.ISO_8859_1))
        } else {
            out.write(ENCRYPTED.toByteArray(Charsets.ISO_8859_1))
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            out.write(salt)
            out.write(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                .apply { init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
            target = CipherOutputStream(out, cipher)
        }
        val zip = ZipOutputStream(target)
        zip.putNextEntry(ZipEntry("data.json"))
        zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        for ((name, file) in files) {
            if (!file.exists()) continue
            zip.putNextEntry(ZipEntry("attachments/$name"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
        if (target !== out) target.close() else out.flush()
    }

    /** Результат чтения: данные и каталог с распакованными вложениями. */
    class Decoded(val data: BackupData, val attachmentsDir: File)

    fun read(input: InputStream, password: String?, tempDir: File): Decoded {
        val header = ByteArray(4)
        if (readFully(input, header) < 4) throw BackupFormatException("Файл не является копией «Квитов»")
        val magic = String(header, Charsets.ISO_8859_1)
        val stream: InputStream = when (magic) {
            PLAIN -> input
            ENCRYPTED -> {
                if (password.isNullOrEmpty()) throw WrongPasswordException()
                val salt = ByteArray(16)
                val iv = ByteArray(12)
                if (readFully(input, salt) < 16 || readFully(input, iv) < 12) throw BackupFormatException("Файл повреждён")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    .apply { init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
                CipherInputStream(input, cipher)
            }
            else -> throw BackupFormatException("Файл не является копией «Квитов»")
        }

        tempDir.deleteRecursively()
        tempDir.mkdirs()
        var data: BackupData? = null
        try {
            ZipInputStream(stream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.name == "data.json" ->
                            data = json.decodeFromString(BackupData.serializer(), String(zip.readBytes(), Charsets.UTF_8))
                        entry.name.startsWith("attachments/") -> {
                            val name = entry.name.removePrefix("attachments/")
                            // защита от выхода за пределы каталога
                            if (name.isNotEmpty() && !name.contains('/') && !name.contains("..")) {
                                File(tempDir, name).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                }
            }
        } catch (e: GeneralSecurityException) {
            throw WrongPasswordException()
        } catch (e: java.io.IOException) {
            // ошибка проверки подлинности GCM приходит как IOException от CipherInputStream
            throw if (magic == ENCRYPTED) WrongPasswordException() else BackupFormatException("Файл повреждён")
        } catch (e: kotlinx.serialization.SerializationException) {
            throw BackupFormatException("Файл повреждён")
        }
        val result = data ?: throw BackupFormatException("В файле нет данных")
        if (result.version > VERSION) throw BackupFormatException("Копия создана более новой версией приложения")
        return Decoded(result, tempDir)
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun bytesInput(bytes: ByteArray): InputStream = ByteArrayInputStream(bytes)
}
