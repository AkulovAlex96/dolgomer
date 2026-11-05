package app.kvity.domain

/** Правила архивации и нормализация необязательных текстовых полей. */
object ArchiveRules {
    /**
     * Причина, по которой контрагента нельзя отправить в архив, или null, если можно.
     * [balance] это неоплаченные остатки по активным (не архивным) долгам и долям, [activeShares] число действующих долей.
     */
    fun archiveBlockReason(balance: Map<String, Long>, activeShares: Int): String? = when {
        balance.any { it.value != 0L } -> "Нельзя архивировать: есть неоплаченные долги. Сначала закройте их или отправьте в архив."
        activeShares > 0 -> "Нельзя архивировать: человек участвует в регулярных платежах."
        else -> null
    }

    /** Обрезает пробелы и длину; пустое значение превращается в null. */
    fun cleanText(text: String?, limit: Int): String? = text?.trim()?.take(limit)?.ifEmpty { null }
}
