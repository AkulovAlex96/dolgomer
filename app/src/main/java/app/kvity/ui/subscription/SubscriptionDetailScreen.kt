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
import app.kvity.domain.SubscriptionCalc
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
    knownNames: List<String>,
    attachments: @Composable (subscriptionId: String) -> Unit = {},
) {
    var shareDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    FormScreen(
        title = view?.subscription?.name ?: "Подписка",
        onBack = onBack,
        actions = { TextButton(onClick = onEdit) { Text("Изменить") } },
    ) {
        if (view == null) {
            Text("Загрузка…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    SubscriptionState.PAUSED -> Pill("на паузе", c.warnBg, c.warn)
                    SubscriptionState.CANCELLED -> Pill("отменена", c.expenseBg, c.expense)
                }
            }
            if (s.state == SubscriptionState.ACTIVE && next != null) {
                Text("Следующее списание: ${formatDate(next)}", modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
            }
            if (s.payer == SubscriptionPayer.OTHER) {
                Text("Платит ${view.payerName ?: "другой"}, это моя доля", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.paymentMethodNote?.let { Text("Оплата: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            s.trialEndsOn?.let { Text("Пробный период до ${formatDate(it)}", color = c.warn) }
            s.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        if (s.payer == SubscriptionPayer.ME) {
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Участники", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { shareDialog = true }) { Text("+ Участник") }
                }
                if (view.shares.isEmpty()) {
                    Text("Участников нет. Добавьте тех, кто возвращает вам долю.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        OutlinedButton(onClick = { onSetState(SubscriptionState.PAUSED) }, modifier = Modifier.weight(1f)) { Text("Пауза") }
                        OutlinedButton(onClick = { onSetState(SubscriptionState.CANCELLED) }, modifier = Modifier.weight(1f)) { Text("Отменить") }
                    }
                    else -> Button(onClick = { onSetState(SubscriptionState.ACTIVE) }, modifier = Modifier.weight(1f)) { Text("Возобновить") }
                }
                OutlinedButton(onClick = { deleteDialog = true }, modifier = Modifier.weight(1f)) { Text("Удалить") }
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
            title = { Text("Удалить подписку?") },
            text = { Text("Подписка, участники и отметки об оплате будут удалены.") },
            confirmButton = { TextButton(onClick = { deleteDialog = false; onDelete() }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Отмена") } },
        )
    }
}

private fun periodText(s: app.kvity.data.SubscriptionEntity): String {
    val n = s.interval
    return when (s.periodicity) {
        app.kvity.domain.Periodicity.WEEK -> if (n == 1) "раз в неделю" else "раз в $n нед."
        app.kvity.domain.Periodicity.MONTH -> if (n == 1) "раз в месяц" else "раз в $n мес."
        app.kvity.domain.Periodicity.YEAR -> if (n == 1) "раз в год" else "раз в $n г."
        app.kvity.domain.Periodicity.DAYS -> "каждые $n дн."
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

    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(sh.counterpartyName, fontWeight = FontWeight.Bold)
                Text(
                    "${formatMoney(sh.share.amountMinor, sh.share.currency)} за период",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (overdue.isNotEmpty()) Pill("не оплатил: ${overdue.size}", c.expenseBg, c.expense)
            else Pill("оплачено", c.incomeBg, c.income)
        }
        overdue.forEach { d -> DueRow(sh, d.periodDate, "просрочено", c.expense, paid = false, onMarkPaid, onMarkUnpaid, onRemind) }
        upcoming?.let { d -> DueRow(sh, d.periodDate, "ожидается", MaterialTheme.colorScheme.onSurface, paid = false, onMarkPaid, onMarkUnpaid, onRemind) }
        if (expanded) {
            lastPaid.forEach { d -> DueRow(sh, d.periodDate, "оплачено", c.income, paid = true, onMarkPaid, onMarkUnpaid, onRemind) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (lastPaid.isNotEmpty()) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрыть оплаченные" else "Показать оплаченные") }
            else Text("")
            TextButton(onClick = onDelete) { Text("Убрать") }
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
                    TextButton(onClick = { onMarkUnpaid(sh, period) }) { Text("Отменить") }
                } else {
                    TextButton(onClick = { onRemind(sh, period) }) { Text("Напомнить") }
                    TextButton(onClick = { onMarkPaid(sh, period) }) { Text("Оплатил") }
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
        title = { Text("Новый участник") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Имя") }, singleLine = true)
                val hints = knownNames.filter { it.contains(name.trim(), true) && !it.equals(name.trim(), true) }.take(3)
                if (name.isNotBlank() && hints.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        hints.forEach { h -> androidx.compose.material3.AssistChip(onClick = { name = h }, label = { Text(h) }) }
                    }
                }
                OutlinedTextField(
                    amountText, { amountText = it }, label = { Text("Доля за период, ${cur.symbol}") },
                    singleLine = true, isError = amountText.isNotBlank() && amount == null,
                )
                DatePickerField("Учитывать с", from, { from = it })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && amount != null, onClick = { onConfirm(name, amount!!, from) }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
