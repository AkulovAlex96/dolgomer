package app.kvity.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.kvity.domain.DebtCalculator
import app.kvity.domain.Direction
import app.kvity.domain.PlannedPayment
import app.kvity.domain.Periodicity
import app.kvity.domain.ScheduleGenerator
import app.kvity.domain.ScheduleRule
import app.kvity.domain.TermType
import java.time.LocalDate

// Идентификаторы строковые (UUID), чтобы позже можно было добавить синхронизацию без смены схемы.

@Entity(tableName = "counterparties")
data class CounterpartyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String? = null,
    val note: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = CounterpartyEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterpartyId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("counterpartyId")],
)
data class DebtEntity(
    @PrimaryKey val id: String,
    val counterpartyId: String,
    val direction: Direction,
    val title: String? = null,
    val principalMinor: Long,
    val currency: String,
    val startDate: LocalDate,
    val termType: TermType,
    val dueDate: LocalDate? = null,
    val periodicity: Periodicity? = null,
    val interval: Int = 1,
    val installmentMinor: Long? = null,
    val firstDate: LocalDate? = null,
    val archived: Boolean = false,
    val note: String? = null,
    val createdAt: Long,
) {
    /** Плановые платежи строятся из условий возврата. */
    fun plannedPayments(): List<PlannedPayment> = when (termType) {
        TermType.ONE_TIME -> dueDate?.let { DebtCalculator.oneTime(principalMinor, it) } ?: emptyList()
        TermType.SCHEDULE -> {
            val p = periodicity
            val inst = installmentMinor
            val first = firstDate
            if (p != null && inst != null && first != null) {
                ScheduleGenerator.generate(ScheduleRule(p, inst, first, interval), principalMinor)
            } else {
                emptyList()
            }
        }
        TermType.NONE -> emptyList()
    }
}

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("debtId")],
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val debtId: String,
    val date: LocalDate,
    val amountMinor: Long,
    val note: String? = null,
    val createdAt: Long,
)

// ---- Подписки ----

enum class SubscriptionPayer { ME, OTHER }
enum class SubscriptionState { ACTIVE, PAUSED, CANCELLED }
enum class AttachmentOwner { DEBT, PAYMENT, SUBSCRIPTION }

/**
 * Подписка. Одна запись это одна подписка.
 * [anchorDate] это известная дата списания, от неё считаются все следующие.
 * Если платит другой ([payer] = OTHER), [amountMinor] это моя доля, которую я возвращаю плательщику.
 */
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String? = null,
    val amountMinor: Long,
    val currency: String,
    val periodicity: Periodicity,
    val interval: Int = 1,
    val anchorDate: LocalDate,
    val payer: SubscriptionPayer = SubscriptionPayer.ME,
    val payerCounterpartyId: String? = null,
    val state: SubscriptionState = SubscriptionState.ACTIVE,
    val paymentMethodNote: String? = null,
    val trialEndsOn: LocalDate? = null,
    val note: String? = null,
    val createdAt: Long,
)

/** Доля участника общей подписки: сколько он возвращает мне за каждый период. */
@Entity(
    tableName = "subscription_shares",
    foreignKeys = [
        ForeignKey(SubscriptionEntity::class, ["id"], ["subscriptionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CounterpartyEntity::class, ["id"], ["counterpartyId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("subscriptionId"), Index("counterpartyId")],
)
data class SubscriptionShareEntity(
    @PrimaryKey val id: String,
    val subscriptionId: String,
    val counterpartyId: String,
    val amountMinor: Long,
    val currency: String,
    /** С какой даты доля учитывается; более ранние периоды не считаются долгом. */
    val activeFrom: LocalDate,
    val active: Boolean = true,
)

/** Отметка «участник оплатил свою долю за период, который начался в [periodDate]». */
@Entity(
    tableName = "share_payments",
    foreignKeys = [ForeignKey(SubscriptionShareEntity::class, ["id"], ["shareId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("shareId")],
)
data class SharePaymentEntity(
    @PrimaryKey val id: String,
    val shareId: String,
    val periodDate: LocalDate,
    val paidDate: LocalDate,
    val amountMinor: Long,
)

// ---- Вложения, шаблоны, журнал напоминаний, курсы ----

@Entity(tableName = "attachments", indices = [Index("ownerId")])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val ownerType: AttachmentOwner,
    val ownerId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Путь относительно каталога вложений приложения. */
    val path: String,
    val createdAt: Long,
)

@Entity(tableName = "reminder_templates")
data class ReminderTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val text: String,
)

@Entity(tableName = "reminder_log")
data class ReminderLogEntity(
    @PrimaryKey val id: String,
    val debtId: String? = null,
    val shareId: String? = null,
    val counterpartyId: String? = null,
    val channel: String,
    val templateName: String? = null,
    val sentAt: Long,
)

@Entity(tableName = "exchange_rates")
data class ExchangeRateEntity(
    @PrimaryKey val id: String,
    val fromCode: String,
    val toCode: String,
    val rate: Double,
    val date: LocalDate,
)
