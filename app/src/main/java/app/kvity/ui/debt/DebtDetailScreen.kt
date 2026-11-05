package app.kvity.ui.debt

import androidx.compose.ui.res.stringResource
import app.kvity.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import app.kvity.data.DebtView
import app.kvity.data.PaymentEntity
import app.kvity.domain.Limits
import app.kvity.ui.components.Pill
import app.kvity.domain.AmountParser
import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.domain.InstallmentState
import app.kvity.domain.PlanItem
import app.kvity.ui.components.ConfirmDialog
import app.kvity.ui.components.DatePickerField
import app.kvity.ui.components.LabelRow
import app.kvity.ui.components.SectionCard
import app.kvity.ui.components.StatusPill
import app.kvity.ui.currencyOf
import app.kvity.ui.formatDate
import app.kvity.ui.formatMoney
import app.kvity.ui.formatMonth
import app.kvity.ui.minorToInput
import app.kvity.ui.theme.kvity
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDetailScreen(
    view: DebtView?,
    onBack: () -> Unit,
    onAddPayment: (date: LocalDate, amountMinor: Long, plannedId: String?, monthOnly: Boolean, paymentMethod: String?, note: String?) -> Unit,
    onDeletePayment: (String) -> Unit,
    onUpdatePayment: (
        id: String, date: LocalDate, amountMinor: Long, plannedId: String?, monthOnly: Boolean,
        paymentMethod: String?, note: String?,
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onSetArchived: (Boolean) -> Unit = {},
    onDeleteDebt: () -> Unit,
    onUpdatePlanned: (id: String, date: LocalDate, amountMinor: Long) -> Unit = { _, _, _ -> },
    onSkipPlanned: (id: String, skipped: Boolean) -> Unit = { _, _ -> },
    onDeletePlanned: (String) -> Unit = {},
    recalcOffer: Boolean = false,
    onRecalc: () -> Unit = {},
    onDismissRecalc: () -> Unit = {},
    onEdit: () -> Unit = {},
    onRemind: () -> Unit = {},
    extra: @Composable () -> Unit = {},
    paymentAttachments: @Composable (paymentId: String) -> Unit = {},
) {
    var paymentDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var openedPayment by remember { mutableStateOf<String?>(null) }
    var editedPlanned by remember { mutableStateOf<PlanItem?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debt_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.debt_back)) } },
                actions = {
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.debt_edit)) }
                    TextButton(onClick = { deleteDialog = true }) { Text(stringResource(R.string.debt_delete)) }
                },
            )
        },
    ) { padding ->
        if (view == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.debt_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Hero(view, onAddPayment = { paymentDialog = true }, onRemind = onRemind, onSetArchived = onSetArchived)
            ScheduleCard(view, onEdit = { editedPlanned = it }, onRestore = { onSkipPlanned(it, false) })
            PaymentsCard(view, onDeletePayment, onOpen = { openedPayment = it })
            extra()
        }

        if (paymentDialog) {
            PaymentDialog(
                currencyCode = view.debt.currency,
                remainingMinor = view.summary.remainingMinor,
                openItems = view.summary.plan.filter { it.id != null && it.state != InstallmentState.PAID },
                onDismiss = { paymentDialog = false },
                onConfirm = { date, minor, plannedId, monthOnly, method, note ->
                    onAddPayment(date, minor, plannedId, monthOnly, method, note)
                    paymentDialog = false
                },
            )
        }
        editedPlanned?.let { item ->
            PlannedDialog(
                item = item,
                currencyCode = view.debt.currency,
                onDismiss = { editedPlanned = null },
                onSave = { date, minor -> onUpdatePlanned(item.id!!, date, minor); editedPlanned = null },
                onSkip = { onSkipPlanned(item.id!!, true); editedPlanned = null },
                onDelete = { onDeletePlanned(item.id!!); editedPlanned = null },
            )
        }
        if (recalcOffer) {
            AlertDialog(
                onDismissRequest = onDismissRecalc,
                title = { Text(stringResource(R.string.debt_recalc_title)) },
                text = {
                    Text(stringResource(R.string.debt_recalc_text))
                },
                confirmButton = { TextButton(onClick = onRecalc) { Text(stringResource(R.string.debt_recalc_reduce)) } },
                dismissButton = { TextButton(onClick = onDismissRecalc) { Text(stringResource(R.string.debt_recalc_keep)) } },
            )
        }
        openedPayment?.let { pid ->
            val p = view.payments.firstOrNull { it.id == pid }
            if (p == null) openedPayment = null else PaymentDialog(
                currencyCode = view.debt.currency,
                // при правке сам платёж не считается: иначе предупреждение о переплате сработало бы зря
                remainingMinor = view.summary.remainingMinor + p.amountMinor,
                openItems = view.summary.plan.filter { it.id != null },
                initial = p,
                attachments = { paymentAttachments(pid) },
                onDismiss = { openedPayment = null },
                onConfirm = { date, minor, plannedId, monthOnly, method, note ->
                    onUpdatePayment(pid, date, minor, plannedId, monthOnly, method, note)
                    openedPayment = null
                },
            )
        }
        if (deleteDialog) {
            AlertDialog(
                onDismissRequest = { deleteDialog = false },
                title = { Text(stringResource(R.string.debt_delete_title)) },
                text = { Text(stringResource(R.string.debt_delete_text)) },
                confirmButton = {
                    TextButton(onClick = { deleteDialog = false; onDeleteDebt() }) { Text(stringResource(R.string.debt_delete)) }
                },
                dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text(stringResource(R.string.debt_cancel)) } },
            )
        }
    }
}

@Composable
private fun Hero(view: DebtView, onAddPayment: () -> Unit, onRemind: () -> Unit, onSetArchived: (Boolean) -> Unit) {
    val c = MaterialTheme.kvity
    val s = view.summary
    val mine = view.debt.direction == Direction.OWED_TO_ME
    val progress = if (s.principalMinor > 0) (s.paidMinor.toFloat() / s.principalMinor).coerceIn(0f, 1f) else 0f

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(view.counterpartyName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    (if (mine) stringResource(R.string.debt_owes_me) else stringResource(R.string.debt_i_owe)) + " · " + currencyOf(view.debt.currency).code,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (view.debt.archived) {
                    Pill(stringResource(R.string.debt_archive_pill), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(s.status, s.overdueDays)
            }
        }

        Text(
            formatMoney(s.remainingMinor, view.debt.currency),
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (mine) c.income else c.expense,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            stringResource(R.string.debt_remaining_of, formatMoney(s.principalMinor, view.debt.currency)),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(10.dp)
                    .background(c.income, RoundedCornerShape(6.dp)),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.debt_returned, formatMoney(s.paidMinor, view.debt.currency)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            s.nextDue?.let {
                Text(
                    stringResource(R.string.debt_next_payment, formatDate(it.dueDate)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (s.status == DebtStatus.OVERDUE) {
            Text(
                stringResource(R.string.debt_overdue_amount, formatMoney(s.overdueMinor, view.debt.currency)),
                color = c.expense,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (s.status != DebtStatus.CLOSED) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddPayment, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.debt_add_payment)) }
                if (mine) OutlinedButton(onClick = onRemind, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.debt_remind)) }
            }
        }
        TextButton(onClick = { onSetArchived(!view.debt.archived) }) {
            Text(if (view.debt.archived) stringResource(R.string.debt_unarchive) else stringResource(R.string.debt_archive))
        }
    }
}

@Composable
private fun ScheduleCard(view: DebtView, onEdit: (PlanItem) -> Unit, onRestore: (String) -> Unit) {
    val plan = view.summary.plan
    if (plan.isEmpty() && view.skipped.isEmpty()) return
    val closed = view.summary.status == DebtStatus.CLOSED
    val c = MaterialTheme.kvity
    SectionCard {
        Text(stringResource(R.string.debt_schedule), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val today = LocalDate.now()
        plan.forEach { item ->
            val (note, color) = when {
                item.state == InstallmentState.PAID -> stringResource(R.string.debt_plan_paid) to c.income
                closed -> stringResource(R.string.debt_plan_not_needed) to MaterialTheme.colorScheme.onSurfaceVariant
                item.state == InstallmentState.PARTIAL -> stringResource(R.string.debt_plan_partial) to c.warn
                item.dueDate.isBefore(today) -> stringResource(R.string.debt_plan_overdue) to c.expense
                else -> "" to MaterialTheme.colorScheme.onSurface
            }
            LabelRow(
                onClick = if (item.id != null && !closed) ({ onEdit(item) }) else null,
                label = formatDate(item.dueDate),
                subtitle = note.ifEmpty { null },
                trailing = {
                    val shown = if (item.state == InstallmentState.PAID) item.amountMinor else item.remainingMinor
                    Text(
                        (if (item.state == InstallmentState.PAID) "✓ " else "") + formatMoney(shown, view.debt.currency),
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                },
            )
        }
        if (view.skipped.isNotEmpty()) {
            Text(stringResource(R.string.debt_skipped), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            view.skipped.forEach { item ->
                LabelRow(
                    label = formatDate(item.dueDate),
                    subtitle = stringResource(R.string.debt_skipped_note),
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatMoney(item.amountMinor, view.debt.currency), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            item.id?.let { id -> TextButton(onClick = { onRestore(id) }) { Text(stringResource(R.string.debt_restore)) } }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PaymentsCard(view: DebtView, onDelete: (String) -> Unit, onOpen: (String) -> Unit) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    pendingDelete?.let { id ->
        val p = view.payments.firstOrNull { it.id == id }
        ConfirmDialog(
            title = stringResource(R.string.debt_pay_delete_title),
            text = if (p == null) stringResource(R.string.debt_pay_delete_simple) else stringResource(R.string.debt_pay_delete_text, formatMoney(p.amountMinor, view.debt.currency), formatDate(p.date)),
            onConfirm = { pendingDelete = null; onDelete(id) },
            onDismiss = { pendingDelete = null },
        )
    }
    SectionCard {
        Text(stringResource(R.string.debt_payments), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (view.payments.isEmpty()) {
            Text(stringResource(R.string.debt_no_payments), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        view.payments.forEach { p ->
            LabelRow(
                onClick = { onOpen(p.id) },
                label = if (p.monthOnly) formatMonth(p.date) else formatDate(p.date),
                subtitle = (if (p.monthOnly) stringResource(R.string.debt_pay_month_only) else "") +
                    (if (p.plannedId != null) stringResource(R.string.debt_pay_linked) else "") +
                    (p.paymentMethod?.let { "$it · " } ?: "") + (p.note?.let { "$it · " } ?: "") +
                    stringResource(R.string.debt_pay_tap),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatMoney(p.amountMinor, view.debt.currency), fontWeight = FontWeight.Bold)
                        TextButton(onClick = { pendingDelete = p.id }) { Text("✕") }
                    }
                },
            )
        }
    }
}

@Composable
private fun PaymentDialog(
    currencyCode: String,
    remainingMinor: Long,
    openItems: List<PlanItem>,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, Long, String?, Boolean, String?, String?) -> Unit,
    /** Редактируемый платёж; null: новый. */
    initial: PaymentEntity? = null,
    attachments: @Composable () -> Unit = {},
) {
    var amountText by remember { mutableStateOf(initial?.let { minorToInput(it.amountMinor, currencyCode) } ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var monthOnly by remember { mutableStateOf(initial?.monthOnly ?: false) }
    var plannedId by remember { mutableStateOf(initial?.plannedId) }
    var method by remember { mutableStateOf(initial?.paymentMethod ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var menuOpen by remember { mutableStateOf(false) }
    val amount = AmountParser.parseMinor(amountText, currencyOf(currencyCode).fractionDigits)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.debt_new_payment) else stringResource(R.string.debt_payment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.debt_amount_with_symbol, currencyOf(currencyCode).symbol)) },
                    singleLine = true,
                    isError = amountText.isNotBlank() && amount == null,
                    // переплата возможна (проценты, округление): предупреждаем, но не запрещаем
                    supportingText = if (amount != null && amount > remainingMinor) ({
                        Text(
                            stringResource(R.string.debt_overpay, formatMoney(remainingMinor, currencyCode)),
                            color = MaterialTheme.kvity.warn,
                        )
                    }) else null,
                )
                DatePickerField(if (monthOnly) stringResource(R.string.debt_any_day) else stringResource(R.string.debt_date), date, { date = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = monthOnly, onCheckedChange = { monthOnly = it })
                    Text(stringResource(R.string.debt_month_only), Modifier.clickable { monthOnly = !monthOnly })
                }
                if (openItems.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            val chosen = openItems.firstOrNull { it.id == plannedId }
                            Text(
                                if (chosen == null) stringResource(R.string.debt_closes_oldest)
                                else stringResource(R.string.debt_closes, formatDate(chosen.dueDate)),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.debt_oldest_unpaid)) },
                                onClick = { plannedId = null; menuOpen = false },
                            )
                            openItems.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.debt_item_left, formatDate(item.dueDate), formatMoney(item.remainingMinor, currencyCode))) },
                                    onClick = { plannedId = item.id; menuOpen = false },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = method, onValueChange = { method = it.take(Limits.NAME) }, label = { Text(stringResource(R.string.debt_pay_method)) },
                    placeholder = { Text(stringResource(R.string.debt_pay_method_hint)) }, singleLine = true,
                )
                OutlinedTextField(value = note, onValueChange = { note = it.take(Limits.NOTE) }, label = { Text(stringResource(R.string.debt_comment)) })
                if (initial != null) attachments()
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null, onClick = {
                onConfirm(date, amount!!, plannedId, monthOnly, method.ifBlank { null }, note.ifBlank { null })
            }) { Text(if (initial == null) stringResource(R.string.debt_add) else stringResource(R.string.debt_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.debt_cancel)) } },
    )
}

@Composable
private fun PlannedDialog(
    item: PlanItem,
    currencyCode: String,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Long) -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
) {
    val digits = currencyOf(currencyCode).fractionDigits
    var amountText by remember { mutableStateOf(minorToInput(item.amountMinor, currencyCode)) }
    var date by remember { mutableStateOf(item.dueDate) }
    val amount = AmountParser.parseMinor(amountText, digits)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debt_planned_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DatePickerField("Дата", date, { date = it })
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.debt_amount_with_symbol, currencyOf(currencyCode).symbol)) },
                    singleLine = true,
                    isError = amount == null,
                )
                if (item.paidMinor > 0) {
                    Text(
                        stringResource(R.string.debt_already_paid, formatMoney(item.paidMinor, currencyCode)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.debt_skip)) }
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.debt_delete)) }
                }
            }
        },
        confirmButton = { TextButton(enabled = amount != null, onClick = { onSave(date, amount!!) }) { Text(stringResource(R.string.debt_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.debt_cancel)) } },
    )
}
