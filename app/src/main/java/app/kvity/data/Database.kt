package app.kvity.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update
import androidx.room.AutoMigration
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}

@Dao
interface CounterpartyDao {
    @Insert suspend fun insert(counterparty: CounterpartyEntity)
    @Update suspend fun update(counterparty: CounterpartyEntity)

    @Query("SELECT * FROM counterparties WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CounterpartyEntity?

    @Query("SELECT * FROM counterparties ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CounterpartyEntity>>

    @Query("SELECT * FROM counterparties") suspend fun all(): List<CounterpartyEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<CounterpartyEntity>)
    @Query("DELETE FROM counterparties") suspend fun deleteAll()

    @Query("DELETE FROM counterparties WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface DebtDao {
    @Insert suspend fun insert(debt: DebtEntity)
    @Update suspend fun update(debt: DebtEntity)

    @Query("SELECT * FROM debts")
    fun observeAll(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE archived = 0")
    fun observeActive(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE id = :id")
    fun observe(id: String): Flow<DebtEntity?>

    @Query("UPDATE debts SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM debts") suspend fun all(): List<DebtEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<DebtEntity>)
    @Query("DELETE FROM debts") suspend fun deleteAll()
}

@Dao
interface PaymentDao {
    @Insert suspend fun insert(payment: PaymentEntity)

    @Query("SELECT * FROM payments")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM payments") suspend fun all(): List<PaymentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<PaymentEntity>)
    @Query("DELETE FROM payments") suspend fun deleteAll()
}

@Dao
interface SubscriptionDao {
    @Insert suspend fun insert(item: SubscriptionEntity)
    @Update suspend fun update(item: SubscriptionEntity)
    @Query("DELETE FROM subscriptions WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM subscriptions") fun observeAll(): Flow<List<SubscriptionEntity>>
    @Query("SELECT * FROM subscriptions WHERE id = :id") fun observe(id: String): Flow<SubscriptionEntity?>
    @Query("SELECT * FROM subscriptions") suspend fun all(): List<SubscriptionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<SubscriptionEntity>)
    @Query("DELETE FROM subscriptions") suspend fun deleteAll()
}

@Dao
interface ShareDao {
    @Insert suspend fun insert(item: SubscriptionShareEntity)
    @Update suspend fun update(item: SubscriptionShareEntity)
    @Query("DELETE FROM subscription_shares WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM subscription_shares") fun observeAll(): Flow<List<SubscriptionShareEntity>>
    @Query("SELECT * FROM subscription_shares") suspend fun all(): List<SubscriptionShareEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<SubscriptionShareEntity>)
    @Query("DELETE FROM subscription_shares") suspend fun deleteAll()
}

@Dao
interface SharePaymentDao {
    @Insert suspend fun insert(item: SharePaymentEntity)
    @Query("DELETE FROM share_payments WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM share_payments WHERE shareId = :shareId AND periodDate = :periodDate")
    suspend fun deleteFor(shareId: String, periodDate: LocalDate)
    @Query("SELECT * FROM share_payments") fun observeAll(): Flow<List<SharePaymentEntity>>
    @Query("SELECT * FROM share_payments") suspend fun all(): List<SharePaymentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<SharePaymentEntity>)
    @Query("DELETE FROM share_payments") suspend fun deleteAll()
}

@Dao
interface AttachmentDao {
    @Insert suspend fun insert(item: AttachmentEntity)
    @Query("DELETE FROM attachments WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM attachments WHERE ownerType = :type AND ownerId = :ownerId ORDER BY createdAt")
    fun observeFor(type: AttachmentOwner, ownerId: String): Flow<List<AttachmentEntity>>
    @Query("SELECT * FROM attachments WHERE ownerId = :ownerId") suspend fun forOwner(ownerId: String): List<AttachmentEntity>
    @Query("SELECT * FROM attachments") suspend fun all(): List<AttachmentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<AttachmentEntity>)
    @Query("DELETE FROM attachments") suspend fun deleteAll()
}

@Dao
interface TemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: ReminderTemplateEntity)
    @Query("DELETE FROM reminder_templates WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM reminder_templates ORDER BY name") fun observeAll(): Flow<List<ReminderTemplateEntity>>
    @Query("SELECT COUNT(*) FROM reminder_templates") suspend fun count(): Int
    @Query("SELECT * FROM reminder_templates") suspend fun all(): List<ReminderTemplateEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ReminderTemplateEntity>)
    @Query("DELETE FROM reminder_templates") suspend fun deleteAll()
}

@Dao
interface ReminderLogDao {
    @Insert suspend fun insert(item: ReminderLogEntity)
    @Query("SELECT * FROM reminder_log ORDER BY sentAt DESC") fun observeAll(): Flow<List<ReminderLogEntity>>
    @Query("SELECT * FROM reminder_log") suspend fun all(): List<ReminderLogEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ReminderLogEntity>)
    @Query("DELETE FROM reminder_log") suspend fun deleteAll()
}

@Dao
interface RateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: ExchangeRateEntity)
    @Query("DELETE FROM exchange_rates WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM exchange_rates ORDER BY fromCode, toCode") fun observeAll(): Flow<List<ExchangeRateEntity>>
    @Query("SELECT * FROM exchange_rates") suspend fun all(): List<ExchangeRateEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ExchangeRateEntity>)
    @Query("DELETE FROM exchange_rates") suspend fun deleteAll()
}

@Database(
    entities = [
        CounterpartyEntity::class, DebtEntity::class, PaymentEntity::class,
        SubscriptionEntity::class, SubscriptionShareEntity::class, SharePaymentEntity::class,
        AttachmentEntity::class, ReminderTemplateEntity::class, ReminderLogEntity::class,
        ExchangeRateEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class KvityDatabase : RoomDatabase() {
    abstract fun counterparties(): CounterpartyDao
    abstract fun debts(): DebtDao
    abstract fun payments(): PaymentDao
    abstract fun subscriptions(): SubscriptionDao
    abstract fun shares(): ShareDao
    abstract fun sharePayments(): SharePaymentDao
    abstract fun attachments(): AttachmentDao
    abstract fun templates(): TemplateDao
    abstract fun reminderLog(): ReminderLogDao
    abstract fun rates(): RateDao

    companion object {
        private const val NAME = "kvity.db"

        /** База шифруется SQLCipher. Старая незашифрованная база (если есть) переносится в зашифрованную. */
        fun create(context: Context): KvityDatabase {
            System.loadLibrary("sqlcipher")
            val pass = DatabaseKey.get(context)
            migratePlainIfNeeded(context, pass)
            return Room.databaseBuilder(context, KvityDatabase::class.java, NAME)
                .openHelperFactory(net.zetetic.database.sqlcipher.SupportOpenHelperFactory(pass.toByteArray()))
                .build()
        }

        private fun isPlain(file: java.io.File): Boolean {
            if (!file.exists() || file.length() < 16) return false
            val header = ByteArray(16)
            file.inputStream().use { it.read(header) }
            return String(header, Charsets.ISO_8859_1).startsWith("SQLite format 3")
        }

        /**
         * Только для сборок разработки: до появления шифрования база создавалась без пароля.
         * Такая база не открывается с ключом, поэтому пересоздаётся. В релизных установках её нет.
         */
        private fun migratePlainIfNeeded(context: Context, @Suppress("UNUSED_PARAMETER") pass: String) {
            val file = context.getDatabasePath(NAME)
            if (!isPlain(file)) return
            listOf("", "-wal", "-shm", "-journal").forEach { java.io.File(file.path + it).delete() }
        }
    }
}
