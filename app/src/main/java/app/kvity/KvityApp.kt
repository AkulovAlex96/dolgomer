package app.kvity

import android.app.Application
import app.kvity.data.AttachmentRepository
import app.kvity.data.DebtRepository
import app.kvity.data.KvityDatabase
import app.kvity.data.PeopleRepository
import app.kvity.data.ReferenceRepository
import app.kvity.data.SettingsStore
import app.kvity.data.SubscriptionRepository
import app.kvity.security.LockManager
import app.kvity.security.PinStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class KvityApp : Application() {
    lateinit var db: KvityDatabase
        private set
    lateinit var attachments: AttachmentRepository
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
    lateinit var pin: PinStore
        private set
    lateinit var lock: LockManager
        private set

    override fun onCreate() {
        super.onCreate()
        db = KvityDatabase.create(this)
        attachments = AttachmentRepository(this, db)
        debts = DebtRepository(db, attachments)
        subscriptions = SubscriptionRepository(db, attachments)
        people = PeopleRepository(db)
        settings = SettingsStore(this)
        reference = ReferenceRepository(db)
        pin = PinStore(this)
        lock = LockManager(initiallyLocked = settings.current.pinEnabled && pin.isSet)
        val scope = kotlinx.coroutines.MainScope()
        scope.launch { reference.seedDefaults() }
        app.kvity.notify.Notifier.createChannel(this)
        scope.launch {
            // Расписание уведомлений следует за настройками (включение и время)
            settings.settings
                .map { it.notificationsEnabled to (it.notifyHour * 60 + it.notifyMinute) }
                .distinctUntilChanged()
                .collect { app.kvity.notify.ReminderWorker.schedule(this@KvityApp, settings.current) }
        }
    }
}
