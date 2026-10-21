package app.kvity.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kvity.data.DebtView
import app.kvity.domain.Bucket
import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.ui.components.LabelRow
import app.kvity.ui.components.MoneyLines
import app.kvity.ui.components.SectionCard
import app.kvity.ui.components.StatusPill
import app.kvity.ui.formatDate
import app.kvity.ui.formatMoney
import app.kvity.ui.label
import app.kvity.ui.theme.kvity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardState,
    onAddDebt: () -> Unit,
    onOpenDebt: (String) -> Unit,
    onAddSubscription: () -> Unit,
    onOpenSubscription: (String) -> Unit,
    onOpenSettings: () -> Unit,
    backupHint: Boolean = false,
    onOpenBackup: () -> Unit = {},
) {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            run {
                FloatingActionButton(onClick = if (tab == 0) onAddDebt else onAddSubscription, containerColor = MaterialTheme.colorScheme.primary) {
                    Text("+", fontSize = 28.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // запас снизу, чтобы кнопка «+» не закрывала последние строки
            contentPadding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Квиты", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                    androidx.compose.material3.TextButton(onClick = onOpenSettings) { Text("Настройки") }
                }
            }
            if (backupHint) {
                item {
                    SectionCard(modifier = Modifier.clickable(onClick = onOpenBackup)) {
                        Text("Резервная копия не создавалась больше 30 дней", fontWeight = FontWeight.SemiBold)
                        Text("Нажмите, чтобы сохранить данные", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Tabs(selected = tab, onSelect = { tab = it }) }

            if (tab == 1) {
                item { SubsBalanceCard(state) }
                item { BucketsCard("Мне платят", state.subsIncoming, MaterialTheme.kvity.income) }
                item { BucketsCard("Я плачу", state.subsOutgoing, MaterialTheme.kvity.expense) }
                if (state.loaded && state.subs.isEmpty()) {
                    item {
                        SectionCard {
                            Text("Подписок пока нет. Нажмите «+», чтобы добавить.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (state.subs.isNotEmpty()) {
                    item {
                        Text("Все подписки", fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                    items(state.subs, key = { it.subscription.id }) { SubRow(it, onClick = { onOpenSubscription(it.subscription.id) }) }
                }
            } else {
                item { BalanceCard(state) }
                item { BucketsCard("Мне должны", state.owedToMe, MaterialTheme.kvity.income) }
                item { BucketsCard("Я должен", state.iOwe, MaterialTheme.kvity.expense) }

                if (state.loaded && state.debts.isEmpty()) {
                    item {
                        SectionCard {
                            Text("Долгов пока нет. Нажмите «+», чтобы добавить первый.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (state.debts.isNotEmpty()) {
                    item {
                        Text(
                            "Все долги",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(state.debts, key = { it.debt.id }) { DebtRow(it, onClick = { onOpenDebt(it.debt.id) }) }
                }
            }
        }
    }
}

@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
            .padding(3.dp),
    ) {
        listOf("Долги", "Подписки").forEachIndexed { i, title ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent, RoundedCornerShape(999.dp))
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(state: DashboardState) {
    val c = MaterialTheme.kvity
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BalanceBox("Мне должны", state.owedToMeTotal, c.incomeBg, c.income, Modifier.weight(1f))
            BalanceBox("Я должен", state.iOweTotal, c.expenseBg, c.expense, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BalanceBox(title: String, totals: Map<String, Long>, bg: Color, fg: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(bg, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (totals.isEmpty()) {
            Text("—", fontWeight = FontWeight.Bold, color = fg)
        } else {
            totals.entries.sortedByDescending { it.value }.forEach { (code, minor) ->
                Text(formatMoney(minor, code), fontWeight = FontWeight.Bold, color = fg)
            }
        }
    }
}

@Composable
private fun BucketsCard(title: String, totals: BucketTotals, color: Color) {
    SectionCard {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Bucket.entries.forEach { bucket ->
            val amounts = totals[bucket].orEmpty()
            if (bucket == Bucket.OVERDUE && amounts.isEmpty()) return@forEach
            val rowColor = if (bucket == Bucket.OVERDUE) MaterialTheme.kvity.expense else color
            LabelRow(bucket.label, trailing = { MoneyLines(amounts, rowColor) })
        }
    }
}

@Composable
private fun DebtRow(view: DebtView, onClick: () -> Unit) {
    val c = MaterialTheme.kvity
    val mine = view.debt.direction == Direction.OWED_TO_ME
    val s = view.summary
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(view.counterpartyName, fontWeight = FontWeight.SemiBold)
                val sub = when {
                    s.status == DebtStatus.CLOSED -> "закрыт"
                    s.nextDue != null -> "следующий платёж ${formatDate(s.nextDue.dueDate)}"
                    s.status == DebtStatus.OVERDUE -> "есть просрочка"
                    else -> "без срока"
                }
                Text(
                    (if (mine) "мне должны · " else "я должен · ") + sub,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(s.remainingMinor, view.debt.currency),
                    fontWeight = FontWeight.Bold,
                    color = if (mine) c.income else c.expense,
                )
                StatusPill(s.status, s.overdueDays)
            }
        }
    }
}

@Composable
private fun SubsBalanceCard(state: DashboardState) {
    val c = MaterialTheme.kvity
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BalanceBox("Выставить счёт / мес", state.subsIncomingMonthly, c.incomeBg, c.income, Modifier.weight(1f))
            BalanceBox("Мои списания / мес", state.subsOutgoingMonthly, c.expenseBg, c.expense, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SubRow(view: app.kvity.data.SubscriptionView, onClick: () -> Unit) {
    val s = view.subscription
    val c = MaterialTheme.kvity
    val today = java.time.LocalDate.now()
    val next = app.kvity.domain.SubscriptionCalc.occurrences(s.anchorDate, s.periodicity, s.interval, today, today.plusYears(2)).firstOrNull()
    val unpaid = view.shares.flatMap { sh -> sh.dues.filter { !it.paid && it.periodDate.isBefore(today) } }.size
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, fontWeight = FontWeight.SemiBold)
                val who = if (s.payer == app.kvity.data.SubscriptionPayer.ME) {
                    if (view.shares.isEmpty()) "личная" else "общая · ${view.shares.size} уч."
                } else "платит ${view.payerName ?: "другой"}"
                val state = when (s.state) {
                    app.kvity.data.SubscriptionState.ACTIVE -> next?.let { "след. ${formatDate(it)}" } ?: ""
                    app.kvity.data.SubscriptionState.PAUSED -> "на паузе"
                    app.kvity.data.SubscriptionState.CANCELLED -> "отменена"
                }
                Text("$who · $state", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(s.amountMinor, s.currency), fontWeight = FontWeight.Bold)
                if (unpaid > 0) app.kvity.ui.components.Pill("не оплатили: $unpaid", c.expenseBg, c.expense)
            }
        }
    }
}
