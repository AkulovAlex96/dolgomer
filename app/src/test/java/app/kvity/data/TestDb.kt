package app.kvity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

/** Настоящая Room в памяти (Robolectric), без шифрования: для тестов репозиториев. */
object TestDb {
    fun create(): KvityDatabase {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(ctx, KvityDatabase::class.java).allowMainThreadQueries().build()
    }
}
