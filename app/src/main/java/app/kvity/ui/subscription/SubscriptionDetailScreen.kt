package app.kvity.ui.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kvity.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kvity.data.ShareView
import app.kvity.data.SubscriptionPayer
import app.kvity.data.SubscriptionState
import app.kvity.data.SubscriptionView
import app.kvity.domain.AmountParser
import app.kvity.domain.Limits
import app.kvity.domain.SubscriptionCalc
import app.kvity.ui.components.ConfirmDialog
import app.kvity.ui.components.DatePickerField
import app.kvity.ui.components.FormScreen
import app.kvity.ui.components.LabelRow
import app.kvity.ui.components.Pill
import app.kvity.ui.components.SectionCard
import app.kvity.ui.currencyOf
import app.kvity.ui.formatDate
import app.kvity.ui.formatMoney
import app.kvity.ui.theme.kvity
import java.time.LocalDate

@Composable
fun SubscriptionDetailScreen(
    view: SubscriptionView?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetState: (SubscriptionState) -> Unit,
    onAddShare: (name: String, amountMinor: Long, activeFrom: LocalDate) -> Unit,
    onDeleteShare: (String) -> Unit,
    onMarkPaid: (ShareView, LocalDate) -> Unit,
    onMarkUnpaid: (ShareView, LocalDate) -> Unit,
    onRemind: (ShareView, LocalDate) -> Unit,
    onMarkChargePaid: (LocalDate) -> Unit,
    onMarkChargeUnpaid: (LocalDate) -> Unit,
    knownNames: List<String>,
    attachments: @Composable (subscriptionId: String) -> Unit = {},
) {
    var shareDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    FormScreen(
        title = view?.subscription?.name ?: stringResource(R.string.sub_title_default),
        onBack = onBack,
        actions = { TextButton(onClick = onEdit) { Text(stringResource(R.string.sub_edit)) } },
    ) {
        if (view == null) {
            Text(stringResource(R.string.sub_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@FormScreen
        }
        val s = view.subscription
        val c = MaterialTheme.kvity
        val today = LocalDate.now()
        val next = SubscriptionCalc.occurrences(s.anchorDate, s.periodicity, s.interval, today, today.plusYears(2)).firstOrNull()

        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        formatMoney(s.amountMinor, s.currency),
                        fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    Text(periodText(s), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (s.state) {
                    SubscriptionState.ACTIVE -> Unit
                    SubscriptionState.PAUSED -> Pill(stringResource(R.string.sub_paused), c.warnBg, c.warn)
                    SubscriptionState.CANCELLED -> Pill(stringResource(R.string.sub_cancelled), c.expenseBg, c.expense)
                }
            }
            if (s.state == SubscriptionState.ACTIVE && next != null) {
                Text(stringResource(R.string.sub_next_charge, formatDate(next)), modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
            }
            if (s.payer == SubscriptionPayer.OTHER) {
                Text(stringResource(R.string.sub_payer_my_share, view.payerName ?: stringResource(R.string.sub_other)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.paymentMethodNote?.let { Text(stringResource(R.string.sub_payment_method, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            s.trialEndsOn?.let { Text(stringResource(R.string.sub_trial_until, formatDate(it)), color = c.warn) }
            s.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        if (s.state == SubscriptionState.ACTIVE) {
            val charges = app.kvity.data.Events.subscriptionEvents(listOf(view), today).filter { it.flow == app.kvity.data.MoneyFlow.OUT }
            val pending = charges.filter { it.markable && it.date <= today } + charges.filter { it.markable && it.date > today }.take(1)
            val lastPaid = charges.filter { it.paid }.takeLast(2)
            SectionCard {
                Text(stringResource(R.string.sub_my_charges), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pending.isEmpty() && lastPaid.isEmpty()) {
                    Text(stringResource(R.string.sub_no_charges), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                pending.forEach { e ->
                    LabelRow(
                        label = formatDate(e.date),
                        subtitle = if (e.overdue) stringResource(R.string.sub_overdue) else if (e.date == today) stringResource(R.string.sub_today) else stringResource(R.string.sub_expected),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formatMoney(e.amountMinor, e.currency), color = if (e.overdue) c.expense else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { onMarkChargePaid(e.date) }) { Text(stringResource(R.string.sub_paid_btn)) }
                            }
                        },
                    )
                }
                lastPaid.forEach { e ->
                    LabelRow(
                        label = formatDate(e.date), subtitle = stringResource(R.string.sub_paid_state),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formatMoney(e.amountMinor, e.currency), color = c.income, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { onMarkChargeUnpaid(e.date) }) { Text(stringResource(R.string.sub_undo)) }
                            }
                        },
                    )
                }
            }
        }

        if (s.payer == SubscriptionPayer.ME) {
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sub_participants), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { shareDialog = true }) { Text(stringResource(R.string.sub_add_participant)) }
                }
                if (view.shares.isEmpty()) {
                    Text(stringResource(R.string.sub_no_participants), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            view.shares.forEach { sh ->
                ShareCard(sh, today, onMarkPaid, onMarkUnpaid, onRemind, onDelete = { onDeleteShare(sh.share.id) })
            }
        }

        attachments(s.id)

        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when (s.state) {
                    SubscriptionState.ACTIVE -> {
                        OutlinedButton(onClick = { onSetState(SubscriptionState.PAUSED) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sub_pause)) }
                        OutlinedButton(onClick = { onSetState(SubscriptionState.CANCELLED) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sub_cancel_sub)) }
                    }
                    else -> Button(onClick = { onSetState(SubscriptionState.ACTIVE) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sub_resume)) }
                }
                OutlinedButton(onClick = { deleteDialog = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sub_delete)) }
            }
        }
    }

    if (shareDialog && view != null) {
        ShareDialog(
            currency = view.subscription.currency, defaultAmount = view.subscription.amountMinor, knownNames = knownNames,
            onDismiss = { shareDialog = false },
            onConfirm = { name, amount, from -> onAddShare(name, amount, from); shareDialog = false },
        )
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text(stringResource(R.string.sub_delete_title)) },
            text = { Text(stringResource(R.string.sub_delete_text)) },
            confirmButton = { TextButton(onClick = { deleteDialog = false; onDelete() }) { Text(stringResource(R.string.sub_delete)) } },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text(stringResource(R.string.sub_dismiss)) } },
        )
    }
}

@Composable
private fun periodText(s: app.kvity.data.SubscriptionEntity): String {
    val n = s.interval
    return when (s.periodicity) {
        app.kvity.domain.Periodicity.WEEK -> if (n == 1) stringResource(R.string.sub_weekly) else stringResource(R.string.sub_every_weeks, n)
        app.kvity.domain.Periodicity.MONTH -> if (n == 1) stringResource(R.string.sub_monthly) else stringResource(R.string.sub_every_months, n)
        app.kvity.domain.Periodicity.YEAR -> if (n == 1) stringResource(R.string.sub_yearly) else stringResource(R.string.sub_every_years, n)
        app.kvity.domain.Periodicity.DAYS -> stringResource(R.string.sub_every_days, n)
    }
}

@Composable
private fun ShareCard(
    sh: ShareView,
    today: LocalDate,
    onMarkPaid: (ShareView, LocalDate) -> Unit,
    onMarkUnpaid: (ShareView, LocalDate) -> Unit,
    onRemind: (ShareView, LocalDate) -> Unit,
    onDelete: () -> Unit,
) {
    val c = MaterialTheme.kvity
    val overdue = sh.dues.filter { !it.paid && it.periodDate.isBefore(today) }
    val upcoming = sh.dues.firstOrNull { !it.paid && !it.periodDate.isBefore(today) }
    val lastPaid = sh.dues.filter { it.paid }.takeLast(2)
    var expanded by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.sub_remove_title),
            text = stringResource(R.string.sub_remove_text, sh.counterpartyName),
            confirmLabel = stringResource(R.string.sub_remove),
            onConfirm = { confirmRemove = false; onDelete() },
            onDismiss = { confirmRemove = false },
        )
    }

    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(sh.counterpartyName, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.sub_per_period, formatMoney(sh.share.amountMinor, sh.share.currency)),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (overdue.isNotEmpty()) Pill(stringResource(R.string.sub_unpaid_count, overdue.size), c.expenseBg, c.expense)
            else if (upcoming != null && upcoming.periodDate == today) Pill(stringResource(R.string.sub_due_today), c.warnBg, c.warn)
            else Pill(stringResource(R.string.sub_on_time), c.incomeBg, c.income)
        }
        overdue.forEach { d -> DueRow(sh, d.periodDate, stringResource(R.string.sub_overdue), c.expense, paid = false, onMarkPaid, onMarkUnpaid, onRemind) }
        upcoming?.let { d -> DueRow(sh, d.periodDate, stringResource(R.string.sub_expected), MaterialTheme.colorScheme.onSurface, paid = false, onMarkPaid, onMarkUnpaid, onRemind) }
        if (expanded) {
            lastPaid.forEach { d -> DueRow(sh, d.periodDate, stringResource(R.string.sub_paid_state), c.income, paid = true, onMarkPaid, onMarkUnpaid, onRemind) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (lastPaid.isNotEmpty()) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) stringResource(R.string.sub_hide_paid) else stringResource(R.string.sub_show_paid)) }
            else Text("")
            TextButton(onClick = { confirmRemove = true }) { Text(stringResource(R.string.sub_remove)) }
        }
    }
}

@Composable
private fun DueRow(
    sh: ShareView,
    period: LocalDate,
    note: String,
    color: androidx.compose.ui.graphics.Color,
    paid: Boolean,
    onMarkPaid: (ShareView, LocalDate) -> Unit,
    onMarkUnpaid: (ShareView, LocalDate) -> Unit,
    onRemind: (ShareView, LocalDate) -> Unit,
) {
    LabelRow(
        label = formatDate(period), subtitle = note,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatMoney(sh.share.amountMinor, sh.share.currency), color = color, fontWeight = FontWeight.Bold)
                if (paid) {
                    TextButton(onClick = { onMarkUnpaid(sh, period) }) { Text(stringResource(R.string.sub_undo)) }
                } else {
                    TextButton(onClick = { onRemind(sh, period) }) { Text(stringResource(R.string.sub_remind)) }
                    TextButton(onClick = { onMarkPaid(sh, period) }) { Text(stringResource(R.string.sub_paid_btn)) }
                }
            }
        },
    )
}

@Composable
private fun ShareDialog(
    currency: String,
    defaultAmount: Long,
    knownNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, LocalDate) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val cur = currencyOf(currency)
    var amountText by remember {
        mutableStateOf(app.kvity.domain.AmountFormatter.format(defaultAmount, cur).substringBeforeLast(' ').replace(" ", ""))
    }
    var from by remember { mutableStateOf(LocalDate.now()) }
    val amount = AmountParser.parseMinor(amountText, cur.fractionDigits)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sub_new_participant)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(Limits.NAME) }, label = { Text(stringResource(R.string.sub_name)) }, singleLine = true)
                val hints = knownNames.filter { it.contains(name.trim(), true) && !it.equals(name.trim(), true) }.take(3)
                if (name.isNotBlank() && hints.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        hints.forEach { h -> androidx.compose.material3.AssistChip(onClick = { name = h }, label = { Text(h) }) }
                    }
                }
                OutlinedTextField(
                    amountText, { amountText = it }, label = { Text(stringResource(R.string.sub_share_label, cur.symbol)) },
                    singleLine = true, isError = amountText.isNotBlank() && amount == null,
                )
                DatePickerField(stringResource(R.string.sub_count_from), from, { from = it })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && amount != null, onClick = { onConfirm(name, amount!!, from) }) { Text(stringResource(R.string.sub_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sub_dismiss)) } },
    )
}
