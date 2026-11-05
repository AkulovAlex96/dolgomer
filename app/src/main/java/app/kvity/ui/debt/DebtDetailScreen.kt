package app.kvity.ui.debt

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
                title = { Text("Долг") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
                actions = {
                    TextButton(onClick = onEdit) { Text("Изменить") }
                    TextButton(onClick = { deleteDialog = true }) { Text("Удалить") }
                },
            )
        },
    ) { padding ->
        if (view == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Загрузка…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                title = { Text("Пересчитать график?") },
                text = {
                    Text(
                        "Платёж больше, чем требовал график. Сейчас ближайшие плановые платежи закрыты им, " +
                            "а срок сократился. Можно оставить как есть или уменьшить суммы оставшихся платежей, сохранив их даты.",
                    )
                },
                confirmButton = { TextButton(onClick = onRecalc) { Text("Уменьшить платёж") } },
                dismissButton = { TextButton(onClick = onDismissRecalc) { Text("Оставить, сократить срок") } },
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
                title = { Text("Удалить долг?") },
                text = { Text("Долг и все его платежи будут удалены без возможности восстановления.") },
                confirmButton = {
                    TextButton(onClick = { deleteDialog = false; onDeleteDebt() }) { Text("Удалить") }
                },
                dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Отмена") } },
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
                    (if (mine) "мне должен · " else "я должен · ") + currencyOf(view.debt.currency).code,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (view.debt.archived) {
                    Pill("архив", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
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
            "остаток из ${formatMoney(s.principalMinor, view.debt.currency)}",
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
                "возвращено ${formatMoney(s.paidMinor, view.debt.currency)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            s.nextDue?.let {
                Text(
                    "следующий платёж ${formatDate(it.dueDate)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (s.status == DebtStatus.OVERDUE) {
            Text(
                "Просрочено: ${formatMoney(s.overdueMinor, view.debt.currency)}",
                color = c.expense,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (s.status != DebtStatus.CLOSED) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddPayment, modifier = Modifier.weight(1f)) { Text("+ Платёж") }
                if (mine) OutlinedButton(onClick = onRemind, modifier = Modifier.weight(1f)) { Text("Напомнить") }
            }
        }
        TextButton(onClick = { onSetArchived(!view.debt.archived) }) {
            Text(if (view.debt.archived) "Вернуть из архива" else "В архив")
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
        Text("График", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val today = LocalDate.now()
        plan.forEach { item ->
            val (note, color) = when {
                item.state == InstallmentState.PAID -> "оплачен" to c.income
                closed -> "не требуется" to MaterialTheme.colorScheme.onSurfaceVariant
                item.state == InstallmentState.PARTIAL -> "оплачен частично" to c.warn
                item.dueDate.isBefore(today) -> "просрочен" to c.expense
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
            Text("Пропущено", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            view.skipped.forEach { item ->
                LabelRow(
                    label = formatDate(item.dueDate),
                    subtitle = "пропущен, в остаток не входит",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatMoney(item.amountMinor, view.debt.currency), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            item.id?.let { id -> TextButton(onClick = { onRestore(id) }) { Text("Вернуть") } }
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
            title = "Удалить платёж?",
            text = if (p == null) "Платёж будет удалён." else "Платёж ${formatMoney(p.amountMinor, view.debt.currency)} от ${formatDate(p.date)} будет удалён, остаток долга пересчитается.",
            onConfirm = { pendingDelete = null; onDelete(id) },
            onDismiss = { pendingDelete = null },
        )
    }
    SectionCard {
        Text("Платежи", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (view.payments.isEmpty()) {
            Text("Платежей пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        view.payments.forEach { p ->
            LabelRow(
                onClick = { onOpen(p.id) },
                label = if (p.monthOnly) formatMonth(p.date) else formatDate(p.date),
                subtitle = (if (p.monthOnly) "без точной даты · " else "") +
                    (if (p.plannedId != null) "привязан к плановому платежу · " else "") +
                    (p.paymentMethod?.let { "$it · " } ?: "") + (p.note?.let { "$it · " } ?: "") +
                    "нажмите, чтобы изменить или приложить чек",
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
        title = { Text(if (initial == null) "Новый платёж" else "Платёж") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Сумма, ${currencyOf(currencyCode).symbol}") },
                    singleLine = true,
                    isError = amountText.isNotBlank() && amount == null,
                    // переплата возможна (проценты, округление): предупреждаем, но не запрещаем
                    supportingText = if (amount != null && amount > remainingMinor) ({
                        Text(
                            "Больше остатка долга (${formatMoney(remainingMinor, currencyCode)}). Платёж всё равно можно добавить.",
                            color = MaterialTheme.kvity.warn,
                        )
                    }) else null,
                )
                DatePickerField(if (monthOnly) "Любой день нужного месяца" else "Дата", date, { date = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = monthOnly, onCheckedChange = { monthOnly = it })
                    Text("Известен только месяц", Modifier.clickable { monthOnly = !monthOnly })
                }
                if (openItems.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            val chosen = openItems.firstOrNull { it.id == plannedId }
                            Text(
                                if (chosen == null) "Закрывает: самый старый неоплаченный"
                                else "Закрывает: ${formatDate(chosen.dueDate)}",
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Самый старый неоплаченный") },
                                onClick = { plannedId = null; menuOpen = false },
                            )
                            openItems.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text("${formatDate(item.dueDate)}, осталось ${formatMoney(item.remainingMinor, currencyCode)}") },
                                    onClick = { plannedId = item.id; menuOpen = false },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = method, onValueChange = { method = it.take(Limits.NAME) }, label = { Text("Способ оплаты") },
                    placeholder = { Text("например, Карта Tinkoff") }, singleLine = true,
                )
                OutlinedTextField(value = note, onValueChange = { note = it.take(Limits.NOTE) }, label = { Text("Комментарий") })
                if (initial != null) attachments()
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null, onClick = {
                onConfirm(date, amount!!, plannedId, monthOnly, method.ifBlank { null }, note.ifBlank { null })
            }) { Text(if (initial == null) "Добавить" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
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
        title = { Text("Плановый платёж") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DatePickerField("Дата", date, { date = it })
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Сумма, ${currencyOf(currencyCode).symbol}") },
                    singleLine = true,
                    isError = amount == null,
                )
                if (item.paidMinor > 0) {
                    Text(
                        "Уже оплачено ${formatMoney(item.paidMinor, currencyCode)}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row {
                    TextButton(onClick = onSkip) { Text("Пропустить") }
                    TextButton(onClick = onDelete) { Text("Удалить") }
                }
            }
        },
        confirmButton = { TextButton(enabled = amount != null, onClick = { onSave(date, amount!!) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
