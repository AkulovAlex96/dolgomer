package app.kvity

import android.app.Application
import app.kvity.data.DebtRepository
import app.kvity.data.KvityDatabase

class KvityApp : Application() {
    lateinit var debts: DebtRepository
        private set

    override fun onCreate() {
        super.onCreate()
        debts = DebtRepository(KvityDatabase.create(this))
    }
}
