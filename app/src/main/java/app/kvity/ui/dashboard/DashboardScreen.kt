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
) {
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = onAddDebt, containerColor = MaterialTheme.colorScheme.primary) {
                    Text("+", fontSize = 28.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Квиты",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item { Tabs(selected = tab, onSelect = { tab = it }) }

            if (tab == 1) {
                item {
                    SectionCard {
                        Text("Подписки появятся на следующем этапе", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
