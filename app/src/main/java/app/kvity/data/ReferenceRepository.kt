package app.kvity.data

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** Справочники: курсы валют, шаблоны напоминаний, журнал напоминаний. */
class ReferenceRepository(private val db: KvityDatabase) {

    val rates: Flow<List<ExchangeRateEntity>> = db.rates().observeAll()
    val templates: Flow<List<ReminderTemplateEntity>> = db.templates().observeAll()

    suspend fun saveRate(from: String, to: String, rate: Double, date: LocalDate = LocalDate.now(), id: String? = null) {
        db.rates().upsert(ExchangeRateEntity(id ?: UUID.randomUUID().toString(), from, to, rate, date))
    }

    suspend fun deleteRate(id: String) = db.rates().delete(id)

    suspend fun saveTemplate(name: String, text: String, id: String? = null) {
        db.templates().upsert(ReminderTemplateEntity(id ?: UUID.randomUUID().toString(), name.trim(), text))
    }

    suspend fun deleteTemplate(id: String) = db.templates().delete(id)

    suspend fun seedDefaults() {
        if (db.templates().count() > 0) return
        saveTemplate(
            "Мягкий",
            "Привет, {имя}! Напоминаю про долг: осталось {остаток}, срок возврата {срок}. Когда сможешь вернуть?",
        )
        saveTemplate(
            "Строгий",
            "{имя}, срок возврата долга ({остаток}) {срок}. Просрочка {дней_просрочки} дн. Прошу вернуть в ближайшее время.",
        )
    }

    suspend fun logReminder(debtId: String?, shareId: String?, counterpartyId: String?, channel: String, template: String?) {
        db.reminderLog().insert(
            ReminderLogEntity(UUID.randomUUID().toString(), debtId, shareId, counterpartyId, channel, template, System.currentTimeMillis()),
        )
    }

    val log: Flow<List<ReminderLogEntity>> = db.reminderLog().observeAll()
}

/** Подстановка переменных шаблона. */
object TemplateText {
    private val overdueSentence = Regex("""\s*[^.!?]*\{дней_просрочки\}[^.!?]*[.!?]?""")
    private val otherPlaceholder = Regex("""\{(?!дней_просрочки\})""")

    /**
     * Если долг не просрочен, предложение вида «Просрочка {дней_просрочки} дн.» убирается целиком (иначе выйдет «Просрочка 0 дн.»).
     * Предложения, где есть и другие переменные, и шаблон, который после этого опустел бы, остаются как есть.
     */
    internal fun withoutOverdueSentence(template: String, overdueDays: Long): String {
        if (overdueDays > 0) return template
        val cut = template.replace(overdueSentence) { if (otherPlaceholder.containsMatchIn(it.value)) it.value else "" }.trim()
        return cut.ifBlank { template }
    }

    fun render(
        template: String,
        name: String,
        amount: String,
        remaining: String,
        due: String,
        overdueDays: Long,
    ): String = withoutOverdueSentence(template, overdueDays)
        .replace("{имя}", name)
        .replace("{сумма}", amount)
        .replace("{остаток}", remaining)
        .replace("{срок}", due)
        .replace("{дней_просрочки}", overdueDays.toString())
}
