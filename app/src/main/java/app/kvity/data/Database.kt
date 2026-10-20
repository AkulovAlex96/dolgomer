package app.kvity.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
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

    @Query("SELECT * FROM counterparties WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CounterpartyEntity?

    @Query("SELECT * FROM counterparties ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CounterpartyEntity>>
}

@Dao
interface DebtDao {
    @Insert suspend fun insert(debt: DebtEntity)

    @Query("SELECT * FROM debts WHERE archived = 0")
    fun observeActive(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE id = :id")
    fun observe(id: String): Flow<DebtEntity?>

    @Query("UPDATE debts SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PaymentDao {
    @Insert suspend fun insert(payment: PaymentEntity)

    @Query("SELECT * FROM payments")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [CounterpartyEntity::class, DebtEntity::class, PaymentEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KvityDatabase : RoomDatabase() {
    abstract fun counterparties(): CounterpartyDao
    abstract fun debts(): DebtDao
    abstract fun payments(): PaymentDao

    companion object {
        fun create(context: Context): KvityDatabase =
            Room.databaseBuilder(context, KvityDatabase::class.java, "kvity.db").build()
    }
}
