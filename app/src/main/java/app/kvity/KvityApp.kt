package app.kvity

import android.app.Application
import app.kvity.data.DebtRepository
import app.kvity.data.KvityDatabase
import app.kvity.data.PeopleRepository
import app.kvity.data.ReferenceRepository
import app.kvity.data.SettingsStore
import app.kvity.data.SubscriptionRepository
import kotlinx.coroutines.launch

class KvityApp : Application() {
    lateinit var db: KvityDatabase
        private set
    lateinit var debts: DebtRepository
        private set
    lateinit var subscriptions: SubscriptionRepository
        private set
    lateinit var people: PeopleRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var reference: ReferenceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        db = KvityDatabase.create(this)
        debts = DebtRepository(db)
        subscriptions = SubscriptionRepository(db)
        people = PeopleRepository(db)
        settings = SettingsStore(this)
        reference = ReferenceRepository(db)
        kotlinx.coroutines.MainScope().launch { reference.seedDefaults() }
    }
}
