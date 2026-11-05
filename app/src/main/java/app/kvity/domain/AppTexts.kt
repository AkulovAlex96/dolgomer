package app.kvity.domain

import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Суффиксы тысяч и миллионов в коротких суммах: «8к»/«1,2м» по-русски, «8k»/«1.2M» по-английски. */
data class MoneySuffixes(val thousand: String, val million: String, val decimal: Char = ',') {
    companion object {
        val RU = MoneySuffixes("к", "м")
        val EN = MoneySuffixes("k", "M", '.')
    }
}

/**
 * Поставщик текстов для кода без Context (форматтеры, подписи событий, причины отказов).
 * Настоящая реализация [app.kvity.ui.ResourceTexts] читает строковые ресурсы; [RuTexts] запасной вариант для тестов.
 */
interface AppTexts {
    fun bucketLabel(bucket: Bucket): String

    /** Единица размера файла: 0 байты, 1 килобайты, 2 мегабайты. */
    fun byteUnit(index: Int): String
    val decimalSeparator: Char
    val dateFormatter: DateTimeFormatter

    /** Название месяца без склонения; [month] от 1 до 12. */
    fun monthName(month: Int): String
    fun installmentOf(index: Int, total: Int): String
    fun charge(): String
    fun repaying(name: String): String
    fun archiveBlock(block: ArchiveBlock): String
    val moneySuffixes: MoneySuffixes

    companion object {
        /** Тексты по умолчанию для кода вне экранов; приложение подставляет настоящие в KvityApp и MainActivity. */
        private val state = MutableStateFlow<AppTexts>(RuTexts)

        var default: AppTexts
            get() = state.value
            set(value) { state.value = value }

        /** Текущие тексты как поток: код, который держит готовые подписи (календарь), пересчитывает их при смене языка. */
        val flow: StateFlow<AppTexts> get() = state
    }
}

/** Русские тексты без обращения к ресурсам: значение по умолчанию для чистых функций и юнит-тестов. */
object RuTexts : AppTexts {
    private val months = listOf(
        "январь", "февраль", "март", "апрель", "май", "июнь",
        "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
    )

    override fun bucketLabel(bucket: Bucket): String = when (bucket) {
        Bucket.OVERDUE -> "Просрочено"
        Bucket.TODAY -> "Сегодня"
        Bucket.TOMORROW -> "Завтра"
        Bucket.THIS_WEEK -> "На этой неделе"
        Bucket.THIS_MONTH -> "В этом месяце"
        Bucket.NEXT_MONTH -> "В следующем месяце"
    }

    override fun byteUnit(index: Int): String = listOf("Б", "КБ", "МБ")[index]
    override val decimalSeparator: Char = ','
    override val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
    override fun monthName(month: Int): String = months[month - 1]
    override fun installmentOf(index: Int, total: Int): String = "платёж $index из $total"
    override fun charge(): String = "списание"
    override fun repaying(name: String): String = "возвращаю $name"
    override fun archiveBlock(block: ArchiveBlock): String = when (block) {
        ArchiveBlock.OPEN_DEBTS -> "Нельзя архивировать: есть неоплаченные долги. Сначала закройте их или отправьте в архив."
        ArchiveBlock.ACTIVE_SHARES -> "Нельзя архивировать: человек участвует в регулярных платежах."
    }

    override val moneySuffixes: MoneySuffixes = MoneySuffixes.RU
}
