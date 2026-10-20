package app.kvity.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kvity.data.CalendarEvent
import app.kvity.data.EventKind
import app.kvity.data.MoneyFlow
import app.kvity.domain.AmountFormatter
import app.kvity.ui.components.LabelRow
import app.kvity.ui.components.Pill
import app.kvity.ui.components.SectionCard
import app.kvity.ui.currencyOf
import app.kvity.ui.formatDate
import app.kvity.ui.formatMoney
import app.kvity.ui.theme.kvity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class CalendarFilter { ALL, DEBTS, SUBSCRIPTIONS }

private val monthNames = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

@Composable
fun CalendarScreen(
    events: List<CalendarEvent>,
    mainCurrency: String,
    firstDayOfWeek: DayOfWeek,
    onOpenEvent: (CalendarEvent) -> Unit,
    onMarkSharePaid: (CalendarEvent) -> Unit,
) {
    val today = LocalDate.now()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf<LocalDate?>(today) }
    var filter by remember { mutableStateOf(CalendarFilter.ALL) }

    val filtered = events.filter {
        when (filter) {
            CalendarFilter.ALL -> true
            CalendarFilter.DEBTS -> it.kind == EventKind.DEBT
            CalendarFilter.SUBSCRIPTIONS -> it.kind == EventKind.SUBSCRIPTION
        }
    }
    val byDate = filtered.groupBy { it.date }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                Text("${monthNames[month.monthValue - 1]} ${month.year}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(CalendarFilter.ALL to "Всё", CalendarFilter.DEBTS to "Долги", CalendarFilter.SUBSCRIPTIONS to "Подписки").forEach { (f, t) ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(t) })
                }
            }

            Legend()
            MonthGrid(month, firstDayOfWeek, today, selected, byDate, mainCurrency) { selected = it }

            selected?.let { day -> DayDetails(day, byDate[day].orEmpty(), onOpenEvent, onMarkSharePaid) }
        }
    }
}

@Composable
private fun Legend() {
    val c = MaterialTheme.kvity
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("+8к", color = c.income, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("мне вернут", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("−2к", color = c.expense, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("я верну", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.size(7.dp).background(c.expense, CircleShape))
        Text("просрочка", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    firstDay: DayOfWeek,
    today: LocalDate,
    selected: LocalDate?,
    byDate: Map<LocalDate, List<CalendarEvent>>,
    mainCurrency: String,
    onSelect: (LocalDate) -> Unit,
) {
    val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
    val days = month.lengthOfMonth()
    val rows = (offset + days + 6) / 7

    Row(Modifier.fillMaxWidth()) {
        for (i in 0 until 7) {
            val dow = firstDay.plus(i.toLong())
            Text(
                dow.getDisplayName(TextStyle.SHORT, Locale("ru")).replaceFirstChar { it.uppercase() },
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (col in 0 until 7) {
                    val dayNum = r * 7 + col - offset + 1
                    if (dayNum < 1 || dayNum > days) {
                        Box(Modifier.weight(1f).height(70.dp))
                    } else {
                        val date = month.atDay(dayNum)
                        DayCell(date, today, date == selected, byDate[date].orEmpty(), mainCurrency, Modifier.weight(1f)) { onSelect(date) }
                    }
                }
            }
        }
    }
}

/**
 * Ячейка дня. Строки сумм занимают фиксированные места: входящие всегда сверху, исходящие всегда снизу,
 * даже если одной из них нет, чтобы цифры не «прыгали» от дня к дню.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    today: LocalDate,
    selected: Boolean,
    events: List<CalendarEvent>,
    mainCurrency: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.kvity
    val incoming = events.filter { it.flow == MoneyFlow.IN && it.currency == mainCurrency }.sumOf { it.amountMinor }
    val outgoing = events.filter { it.flow == MoneyFlow.OUT && it.currency == mainCurrency }.sumOf { it.amountMinor }
    val hasOther = events.any { it.currency != mainCurrency }
    val overdue = events.any { it.overdue }
    val digits = currencyOf(mainCurrency).fractionDigits

    Column(
        modifier = modifier
            .height(70.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(9.dp))
            .border(
                if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (date == today) {
                Box(Modifier.size(18.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Text(date.dayOfMonth.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                Text(date.dayOfMonth.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (overdue) {
                Box(Modifier.padding(start = 3.dp).size(6.dp).background(c.expense, CircleShape))
            }
            if (hasOther) Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
        }
        Box(Modifier.weight(1f))
        Text(
            if (incoming > 0) "+" + AmountFormatter.compact(incoming, digits) else "",
            color = c.income, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1, modifier = Modifier.height(15.dp),
        )
        Text(
            if (outgoing > 0) "−" + AmountFormatter.compact(outgoing, digits) else "",
            color = c.expense, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1, modifier = Modifier.height(15.dp),
        )
    }
}

@Composable
private fun DayDetails(
    day: LocalDate,
    events: List<CalendarEvent>,
    onOpen: (CalendarEvent) -> Unit,
    onMarkSharePaid: (CalendarEvent) -> Unit,
) {
    val c = MaterialTheme.kvity
    SectionCard {
        Text(formatDate(day), fontWeight = FontWeight.Bold)
        if (events.isEmpty()) {
            Text("В этот день ничего нет", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            return@SectionCard
        }
        val currencies = events.map { it.currency }.distinct()
        currencies.forEach { code ->
            val inn = events.filter { it.currency == code && it.flow == MoneyFlow.IN }.sumOf { it.amountMinor }
            val out = events.filter { it.currency == code && it.flow == MoneyFlow.OUT }.sumOf { it.amountMinor }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalBox("Входящие", if (inn > 0) "+" + formatMoney(inn, code) else "—", c.incomeBg, c.income, Modifier.weight(1f))
                TotalBox("Исходящие", if (out > 0) "−" + formatMoney(out, code) else "—", c.expenseBg, c.expense, Modifier.weight(1f))
                val net = inn - out
                TotalBox("Итог", (if (net > 0) "+" else if (net < 0) "−" else "") + formatMoney(kotlin.math.abs(net), code),
                    MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
        }
        events.sortedBy { it.flow }.forEach { e ->
            LabelRow(
                onClick = { onOpen(e) },
                label = e.title, subtitle = listOfNotNull(e.subtitle, if (e.overdue) "просрочено" else null).joinToString(" · ").ifEmpty { null },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (e.flow == MoneyFlow.IN) "+" else "−") + formatMoney(e.amountMinor, e.currency),
                            color = if (e.flow == MoneyFlow.IN) c.income else c.expense, fontWeight = FontWeight.Bold,
                        )
                        if (e.shareId != null) TextButton(onClick = { onMarkSharePaid(e) }) { Text("Оплатил") }
                    }
                },
            )
        }
    }
}

@Composable
private fun TotalBox(title: String, value: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}
