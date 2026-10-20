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
