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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kvity.data.CalendarEvent
import app.kvity.data.EventKind
import app.kvity.data.Events
import app.kvity.data.MonthTotal
import app.kvity.data.MoneyFlow
import app.kvity.domain.AmountFormatter
import app.kvity.ui.components.LabelRow
import app.kvity.ui.components.Pill
import app.kvity.ui.components.SectionCard
import app.kvity.ui.components.swipeHorizontally
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
                .swipeHorizontally(onLeft = { month = month.plusMonths(1) }, onRight = { month = month.minusMonths(1) })
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
                listOf(CalendarFilter.ALL to "Всё", CalendarFilter.DEBTS to "Долги", CalendarFilter.SUBSCRIPTIONS to "Регулярные платежи").forEach { (f, t) ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(t) })
                }
            }

            if (filter != CalendarFilter.SUBSCRIPTIONS) {
                MonthSummary("Долги", Events.monthTotals(filtered.filter { it.kind == EventKind.DEBT }, month, mainCurrency), legend = true)
            }
            if (filter != CalendarFilter.DEBTS) {
                MonthSummary("Регулярные платежи", Events.monthTotals(filtered.filter { it.kind == EventKind.SUBSCRIPTION }, month, mainCurrency), legend = filter == CalendarFilter.SUBSCRIPTIONS)
            }
            MonthGrid(month, firstDayOfWeek, today, selected, byDate, mainCurrency) { selected = it; month = YearMonth.from(it) }

            selected?.let { day -> DayDetails(day, byDate[day].orEmpty(), onOpenEvent, onMarkSharePaid) }
        }
    }
}

/** Итог показанного месяца: три рамки, в каждой суммы по валютам столбиком (как на главной); ниже пояснение к красной точке. */
@Composable
private fun MonthSummary(title: String, totals: List<MonthTotal>, legend: Boolean) {
    val c = MaterialTheme.kvity
    // строка на каждую валюту в каждой рамке, «—» там, где суммы нет: так строки трёх рамок выровнены
    fun lines(sign: (Long) -> String, value: (MonthTotal) -> Long) =
        totals.map { if (value(it) == 0L) "—" else sign(value(it)) + formatMoney(kotlin.math.abs(value(it)), it.currency) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryBox("Мне вернут", lines({ "+" }) { it.incomingMinor }, c.incomeBg, c.income, Modifier.weight(1f))
            SummaryBox("Я верну", lines({ "−" }) { it.outgoingMinor }, c.expenseBg, c.expense, Modifier.weight(1f))
            SummaryBox(
                "Итог", lines({ if (it > 0) "+" else "−" }) { it.incomingMinor - it.outgoingMinor },
                MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f),
            )
        }
        if (legend) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(c.expense, CircleShape))
            Text("просрочка", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryBox(title: String, lines: List<String>, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (lines.isEmpty()) Text("—", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
        lines.forEach { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg) }
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
                    val date = month.atDay(1).plusDays((dayNum - 1).toLong())
                    val inMonth = dayNum in 1..days
                    DayCell(
                        date, today, date == selected, byDate[date].orEmpty(), mainCurrency,
                        Modifier.weight(1f).then(if (inMonth) Modifier else Modifier.alpha(0.45f)),
                    ) { onSelect(date) }
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
    val debts = events.filter { it.kind == EventKind.DEBT }
    val regular = events.filter { it.kind == EventKind.SUBSCRIPTION }
    // долги по каждой валюте и направлению: основная валюта первой, входящие раньше исходящих
    val debtLines = debts.groupBy { it.currency to it.flow }
        .toSortedMap(compareBy<Pair<String, MoneyFlow>> { it.first != mainCurrency }.thenBy { it.first }.thenBy { it.second })
        .map { (key, list) ->
            val cur = currencyOf(key.first)
            val text = (if (key.second == MoneyFlow.IN) "+" else "−") + AmountFormatter.compact(list.sumOf { it.amountMinor }, cur.fractionDigits) + cur.symbol
            text to key.second
        }
    val debtShown = if (debtLines.size > 2) listOf(debtLines[0], "ещё ${debtLines.size - 1}" to null) else debtLines
    // регулярные платежи считаются отдельно от долгов и по каждой валюте (основная первой): итог со знаком
    val regularLines = regular.groupBy { it.currency }
        .toSortedMap(compareBy<String> { it != mainCurrency }.thenBy { it })
        .map { (code, list) -> code to list.sumOf { if (it.flow == MoneyFlow.IN) it.amountMinor else -it.amountMinor } }
        .map { (code, net) ->
            val cur = currencyOf(code)
            (if (net < 0) "−" else "+") + AmountFormatter.compact(kotlin.math.abs(net), cur.fractionDigits) + cur.symbol
        }
    // две строки под регулярные; если валют больше, вторая строка сворачивается в «ещё N»
    val regularShown = if (regularLines.size > 2) listOf(regularLines[0], "ещё ${regularLines.size - 1}") else regularLines
    val overdue = events.any { it.overdue }

    // высоты в sp: при крупном шрифте системы ячейка растёт вместе с текстом, а сумма внизу не обрезается
    val density = LocalDensity.current
    val cellHeight = with(density) { 104.sp.toDp() }
    val amountHeight = with(density) { 15.sp.toDp() }

    Column(
        modifier = modifier
            .height(cellHeight)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(9.dp))
            .border(
                if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(9.dp),
            )
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        val numberSize = with(density) { 20.sp.toDp() }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(numberSize)) {
            // один и тот же квадрат под число у всех дней: кружок «сегодня» не смещает цифры и симметричен рамке
            val isToday = date == today
            Box(
                Modifier.size(numberSize).then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (overdue) {
                Box(Modifier.padding(start = 3.dp).size(6.dp).background(c.expense, CircleShape))
            }
        }
        Box(Modifier.weight(1f))
        for (i in 0 until 2) {
            val (text, flow) = debtShown.getOrElse(i) { "" to null }
            Text(
                text,
                color = when (flow) { MoneyFlow.IN -> c.income; MoneyFlow.OUT -> c.expense; null -> MaterialTheme.colorScheme.onSurfaceVariant },
                fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, softWrap = false,
                modifier = Modifier.height(amountHeight).padding(start = 2.dp),
            )
        }
        for (i in 0 until 2) {
            Text(
                regularShown.getOrElse(i) { "" },
                color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, softWrap = false,
                modifier = Modifier.height(amountHeight).padding(start = 2.dp),
            )
        }
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
        listOf(EventKind.DEBT to "Долги", EventKind.SUBSCRIPTION to "Регулярные платежи").forEach { (kind, heading) ->
            val part = events.filter { it.kind == kind }
            if (part.isEmpty()) return@forEach
            Text(heading, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            part.map { it.currency }.distinct().forEach { code ->
                val inn = part.filter { it.currency == code && it.flow == MoneyFlow.IN }.sumOf { it.amountMinor }
                val out = part.filter { it.currency == code && it.flow == MoneyFlow.OUT }.sumOf { it.amountMinor }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TotalBox("Входящие", if (inn > 0) "+" + formatMoney(inn, code) else "—", c.incomeBg, c.income, Modifier.weight(1f))
                    TotalBox("Исходящие", if (out > 0) "−" + formatMoney(out, code) else "—", c.expenseBg, c.expense, Modifier.weight(1f))
                    val net = inn - out
                    TotalBox("Итог", (if (net > 0) "+" else if (net < 0) "−" else "") + formatMoney(kotlin.math.abs(net), code),
                        MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
            }
            part.sortedBy { it.flow }.forEach { e ->
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
}

@Composable
private fun TotalBox(title: String, value: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}
