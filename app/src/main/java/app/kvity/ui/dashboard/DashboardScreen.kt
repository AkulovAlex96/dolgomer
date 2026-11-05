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
import androidx.compose.ui.res.stringResource
import app.kvity.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kvity.data.CalendarEvent
import app.kvity.data.DebtView
import app.kvity.domain.Bucket
import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.ui.components.LabelRow
import app.kvity.ui.components.MoneyLines
import app.kvity.ui.components.SectionCard
import app.kvity.ui.components.StatusPill
import app.kvity.ui.components.swipeHorizontally
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
    onMarkPaid: (CalendarEvent) -> Unit = {},
    onOpenSettings: () -> Unit,
    backupHint: Boolean = false,
    onOpenBackup: () -> Unit = {},
    onUnarchiveDebt: (String) -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var archiveOpen by rememberSaveable { mutableStateOf(false) }

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
                .padding(padding)
                .swipeHorizontally(onLeft = { tab = 1 }, onRight = { tab = 0 }),
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
                    Text(stringResource(R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                    androidx.compose.material3.TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.dash_settings)) }
                }
            }
            if (backupHint) {
                item {
                    SectionCard(modifier = Modifier.clickable(onClick = onOpenBackup)) {
                        Text(stringResource(R.string.dash_backup_title), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.dash_backup_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Tabs(selected = tab, onSelect = { tab = it }) }

            if (tab == 1) {
                item { SubsBalanceCard(state) }
                if (state.overdueShares.isNotEmpty()) {
                    item { OverdueSharesCard(state.overdueShares, onOpenSubscription, onMarkPaid) }
                }
                item { BucketsCard(stringResource(R.string.dash_they_pay), state.subsIncoming, MaterialTheme.kvity.income) }
                item { BucketsCard(stringResource(R.string.dash_i_pay), state.subsOutgoing, MaterialTheme.kvity.expense) }
                if (state.loaded && state.subs.isEmpty()) {
                    item {
                        SectionCard {
                            Text(stringResource(R.string.dash_no_subs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (state.subs.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.dash_all_regular), fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                    items(state.subs, key = { it.subscription.id }) { SubRow(it, onClick = { onOpenSubscription(it.subscription.id) }) }
                }
            } else {
                item { BalanceCard(state) }
                item { BucketsCard(stringResource(R.string.dash_owed_to_me), state.owedToMe, MaterialTheme.kvity.income) }
                item { BucketsCard(stringResource(R.string.dash_i_owe), state.iOwe, MaterialTheme.kvity.expense) }

                if (state.loaded && state.debts.isEmpty()) {
                    item {
                        SectionCard {
                            Text(stringResource(R.string.dash_no_debts), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (state.debts.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.dash_all_debts),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(state.debts, key = { it.debt.id }) { DebtRow(it, onClick = { onOpenDebt(it.debt.id) }) }
                }
                if (state.archivedDebts.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { archiveOpen = !archiveOpen }.padding(top = 6.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.dash_archive, state.archivedDebts.size),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(if (archiveOpen) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (archiveOpen) {
                        items(state.archivedDebts, key = { "archived-" + it.debt.id }) {
                            ArchivedDebtRow(it, onClick = { onOpenDebt(it.debt.id) }, onRestore = { onUnarchiveDebt(it.debt.id) })
                        }
                    }
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
        listOf(stringResource(R.string.dash_tab_debts), stringResource(R.string.dash_tab_regular)).forEachIndexed { i, title ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
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
            BalanceBox(stringResource(R.string.dash_owed_to_me), state.owedToMeTotal, c.incomeBg, c.income, Modifier.weight(1f))
            BalanceBox(stringResource(R.string.dash_i_owe), state.iOweTotal, c.expenseBg, c.expense, Modifier.weight(1f))
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
                    s.status == DebtStatus.CLOSED -> stringResource(R.string.dash_closed)
                    s.nextDue != null -> stringResource(R.string.dash_next_payment, formatDate(s.nextDue.dueDate))
                    s.status == DebtStatus.OVERDUE -> stringResource(R.string.dash_has_overdue)
                    else -> stringResource(R.string.dash_no_due)
                }
                Text(
                    stringResource(R.string.dash_line, if (mine) stringResource(R.string.dash_lc_owed_to_me) else stringResource(R.string.dash_lc_i_owe), sub),
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
private fun ArchivedDebtRow(view: DebtView, onClick: () -> Unit, onRestore: () -> Unit) {
    val mine = view.debt.direction == Direction.OWED_TO_ME
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(view.counterpartyName, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.dash_line, if (mine) stringResource(R.string.dash_lc_owed_to_me) else stringResource(R.string.dash_lc_i_owe), formatMoney(view.summary.remainingMinor, view.debt.currency)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                app.kvity.ui.components.Pill(stringResource(R.string.dash_archive_pill), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.TextButton(onClick = onRestore) { Text(stringResource(R.string.dash_restore)) }
            }
        }
    }
}

@Composable
private fun SubsBalanceCard(state: DashboardState) {
    val c = MaterialTheme.kvity
    SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BalanceBox(stringResource(R.string.dash_bill_month), state.subsIncomingMonthly, c.incomeBg, c.income, Modifier.weight(1f))
            BalanceBox(stringResource(R.string.dash_charges_month), state.subsOutgoingMonthly, c.expenseBg, c.expense, Modifier.weight(1f))
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
                    if (view.shares.isEmpty()) stringResource(R.string.dash_personal) else stringResource(R.string.dash_shared, view.shares.size)
                } else stringResource(R.string.dash_pays, view.payerName ?: stringResource(R.string.dash_other))
                val state = when (s.state) {
                    app.kvity.data.SubscriptionState.ACTIVE -> next?.let { stringResource(R.string.dash_next, formatDate(it)) } ?: ""
                    app.kvity.data.SubscriptionState.PAUSED -> stringResource(R.string.dash_paused)
                    app.kvity.data.SubscriptionState.CANCELLED -> stringResource(R.string.dash_cancelled)
                }
                Text("$who · $state", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMoney(s.amountMinor, s.currency), fontWeight = FontWeight.Bold)
                if (unpaid > 0) app.kvity.ui.components.Pill(stringResource(R.string.dash_unpaid, unpaid), c.expenseBg, c.expense)
            }
        }
    }
}

@Composable
private fun OverdueSharesCard(
    shares: List<CalendarEvent>,
    onOpen: (String) -> Unit,
    onMarkPaid: (CalendarEvent) -> Unit,
) {
    val c = MaterialTheme.kvity
    SectionCard {
        Text(stringResource(R.string.dash_overdue), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.expense)
        shares.forEach { e ->
            LabelRow(
                onClick = { e.refId.let(onOpen) },
                label = e.title,
                subtitle = listOfNotNull(e.subtitle, formatDate(e.date)).joinToString(" · "),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val incoming = e.flow == app.kvity.data.MoneyFlow.IN
                        Text((if (incoming) "+" else "−") + formatMoney(e.amountMinor, e.currency), color = if (incoming) c.income else c.expense, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.TextButton(onClick = { onMarkPaid(e) }) { Text(stringResource(R.string.dash_paid)) }
                    }
                },
            )
        }
    }
}
