package app.kvity.domain

import app.kvity.R

/** Причина отказа в архивации контрагента. */
enum class ArchiveBlock(val messageRes: Int) {
    OPEN_DEBTS(R.string.debt_archive_block_debts),
    ACTIVE_SHARES(R.string.debt_archive_block_shares),
}

/** Правила архивации и нормализация необязательных текстовых полей. */
object ArchiveRules {
    /**
     * Причина, по которой контрагента нельзя отправить в архив, или null, если можно.
     * [balance] это неоплаченные остатки по активным (не архивным) долгам и долям, [activeShares] число действующих долей.
     */
    fun archiveBlock(balance: Map<String, Long>, activeShares: Int): ArchiveBlock? = when {
        balance.any { it.value != 0L } -> ArchiveBlock.OPEN_DEBTS
        activeShares > 0 -> ArchiveBlock.ACTIVE_SHARES
        else -> null
    }

    /** То же, но готовым текстом на языке приложения (для кода без Context). */
    fun archiveBlockReason(balance: Map<String, Long>, activeShares: Int, texts: AppTexts = AppTexts.default): String? =
        archiveBlock(balance, activeShares)?.let { texts.archiveBlock(it) }

    /** Обрезает пробелы и длину; пустое значение превращается в null. */
    fun cleanText(text: String?, limit: Int): String? = text?.trim()?.take(limit)?.ifEmpty { null }
}
