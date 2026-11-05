package app.kvity.exchange

import java.util.Locale

/**
 * Язык файлов обмена и сообщений об ошибках для кода без Context. Русский по умолчанию (на нём работают тесты).
 * Импорт понимает заголовки и значения на обоих языках независимо от [Lang]; он влияет только на экспорт и тексты ошибок.
 */
enum class Lang {
    RU, EN;

    fun t(ru: String, en: String): String = if (this == RU) ru else en

    /** Заголовок колонки (по русскому названию) на этом языке. */
    fun h(ru: String): String = if (this == EN) Headers.RU_TO_EN[ru] ?: ru else ru

    companion object {
        /** Язык системы: английский, если выбран английский, иначе русский. */
        fun system(): Lang = if (Locale.getDefault().language == "en") EN else RU
    }
}

/** Виды таблиц обмена; нужны, чтобы однозначно сопоставить английский заголовок русскому. */
internal enum class TableKind(val columns: Set<String>) {
    DEBTS(setOf("Контрагент", "Направление", "Валюта", "Сумма", "Выплачено", "Дата выдачи", "Срок", "Комментарий", "Остаток", "Статус", "Условия", "Ближайший платёж")),
    PAYMENTS(setOf("Контрагент", "Направление", "Валюта", "Дата", "Сумма", "Комментарий", "Способ оплаты")),
    SUBSCRIPTIONS(setOf("Название", "Кто платит", "Сумма", "Валюта", "Период", "Дата списания", "Статус", "Способ оплаты", "Комментарий", "Дата начала")),
    SHARES(setOf("Подписка", "Участник", "Период", "Доля", "Валюта", "Статус")),
    PEOPLE(setOf("Имя", "Телефон", "Заметка", "Баланс")),
}

/** Соответствие русских и английских заголовков колонок. Внутри импорта всё приводится к русским названиям в нижнем регистре. */
internal object Headers {
    val RU_TO_EN: Map<String, String> = mapOf(
        "Контрагент" to "Counterparty", "Направление" to "Direction", "Валюта" to "Currency", "Сумма" to "Amount",
        "Выплачено" to "Paid", "Дата выдачи" to "Start date", "Срок" to "Due date", "Комментарий" to "Note",
        "Остаток" to "Remaining", "Статус" to "Status", "Условия" to "Terms", "Ближайший платёж" to "Next payment",
        "Дата" to "Date", "Способ оплаты" to "Payment method",
        "Название" to "Name", "Кто платит" to "Payer", "Период" to "Period", "Дата списания" to "Next charge date",
        "Дата начала" to "Start date",
        "Подписка" to "Subscription", "Участник" to "Participant", "Доля" to "Share",
        "Имя" to "Name", "Телефон" to "Phone", "Заметка" to "Note", "Баланс" to "Balance",
    )

    /** Английский заголовок (нижний регистр) -> возможные русские названия (нижний регистр). */
    private val candidates: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        fun add(en: String, vararg ru: String) { getOrPut(en.lowercase()) { mutableListOf() }.addAll(ru.map { it.lowercase() }) }
        for ((ru, en) in RU_TO_EN) add(en, ru)
        // синонимы
        add("comment", "Комментарий", "Заметка")
        add("start date", "Дата выдачи", "Дата начала")
        add("issue date", "Дата выдачи")
        add("issued", "Дата выдачи")
        add("due", "Срок")
        add("due date", "Срок")
        add("deadline", "Срок")
        add("name", "Название", "Имя")
        add("title", "Название")
        add("person", "Контрагент")
        add("payment date", "Дата")
        add("charge date", "Дата списания")
        add("next charge", "Дата списания")
        add("remainder", "Остаток")
        add("share amount", "Доля")
        add("participant", "Участник")
        add("method", "Способ оплаты")
        add("paid amount", "Выплачено")
        add("tel", "Телефон")
    }

    /** Все возможные русские названия колонки (нижний регистр): сама колонка, если она русская, или кандидаты по английскому. */
    fun possibleCanon(raw: String): List<String> {
        val h = raw.trim().lowercase()
        return candidates[h] ?: listOf(h)
    }

    /** Заголовок таблицы вида [kind] в русских названиях (нижний регистр). Неизвестные колонки остаются как есть. */
    fun canon(header: List<String>, kind: TableKind): List<String> {
        val allowed = kind.columns.map { it.lowercase() }.toSet()
        return header.map { raw ->
            val p = possibleCanon(raw)
            p.firstOrNull { it in allowed } ?: p.first()
        }
    }

    /** Заголовок без привязки к виду: набор всех возможных русских названий (для определения вида таблицы). */
    fun canonAny(header: List<String>): List<String> = header.flatMap { possibleCanon(it) }
}
