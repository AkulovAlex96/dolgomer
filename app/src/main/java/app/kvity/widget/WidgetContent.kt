package app.kvity.widget

import app.kvity.data.CalendarEvent
import java.time.LocalDate

/** Что показывает виджет: закрыт PIN-кодом, пуст или список ближайших событий. */
sealed interface WidgetContent {
    data object Locked : WidgetContent
    data object Empty : WidgetContent
    data class Items(val rows: List<WidgetRow>) : WidgetContent
}

/** Строка виджета. При скрытии данных [name] и [amountMinor] равны null. */
data class WidgetRow(
    val date: LocalDate,
    val name: String?,
    val amountMinor: Long?,
    val currency: String,
    val overdue: Boolean,
)

object WidgetModel {
    const val MAX_ROWS = 5

    /**
     * Ближайшие неоплаченные события: сначала просроченные (по дате), затем предстоящие.
     * Если приложение закрыто PIN-кодом ([locked]), данных нет вообще; при [hideDetails] имена и суммы скрыты.
     */
    fun build(events: List<CalendarEvent>, today: LocalDate, locked: Boolean, hideDetails: Boolean, limit: Int = MAX_ROWS): WidgetContent {
        if (locked) return WidgetContent.Locked
        val rows = events
            .filter { !it.paid && (it.overdue || !it.date.isBefore(today)) }
            .sortedWith(compareBy<CalendarEvent> { !it.overdue }.thenBy { it.date })
            .take(limit)
            .map {
                WidgetRow(
                    date = it.date,
                    name = if (hideDetails) null else it.title,
                    amountMinor = if (hideDetails) null else it.amountMinor,
                    currency = it.currency,
                    overdue = it.overdue,
                )
            }
        return if (rows.isEmpty()) WidgetContent.Empty else WidgetContent.Items(rows)
    }
}
