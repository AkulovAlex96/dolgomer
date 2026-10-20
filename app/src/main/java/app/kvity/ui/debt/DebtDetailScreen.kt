package app.kvity.ui.debt

import androidx.compose.foundation.background
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
import app.kvity.domain.AmountParser
import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.domain.InstallmentState
import app.kvity.ui.components.DatePickerField
import app.kvity.ui.components.LabelRow
import app.kvity.ui.components.SectionCard
import app.kvity.ui.components.StatusPill
import app.kvity.ui.currencyOf
import app.kvity.ui.formatDate
import app.kvity.ui.formatMoney
import app.kvity.ui.theme.kvity
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDetailScreen(
    view: DebtView?,
    onBack: () -> Unit,
    onAddPayment: (LocalDate, Long) -> Unit,
    onDeletePayment: (String) -> Unit,
    onDeleteDebt: () -> Unit,
) {
    var paymentDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Долг") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
                actions = { TextButton(onClick = { deleteDialog = true }) { Text("Удалить") } },
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
            Hero(view, onAddPayment = { paymentDialog = true })
            ScheduleCard(view)
            PaymentsCard(view, onDeletePayment)
        }

        if (paymentDialog) {
            PaymentDialog(
                currencyCode = view.debt.currency,
                onDismiss = { paymentDialog = false },
                onConfirm = { date, minor ->
                    onAddPayment(date, minor)
                    paymentDialog = false
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
private fun Hero(view: DebtView, onAddPayment: () -> Unit) {
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
            StatusPill(s.status, s.overdueDays)
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
            Button(onClick = onAddPayment, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("+ Платёж") }
        }
    }
}

@Composable
private fun ScheduleCard(view: DebtView) {
    val plan = view.summary.plan
    if (plan.isEmpty()) return
    val c = MaterialTheme.kvity
    SectionCard {
        Text("График", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val today = LocalDate.now()
        plan.forEach { item ->
            val (note, color) = when {
                item.state == InstallmentState.PAID -> "оплачен" to c.income
                item.state == InstallmentState.PARTIAL -> "оплачен частично" to c.warn
                item.dueDate.isBefore(today) -> "просрочен" to c.expense
                else -> "" to MaterialTheme.colorScheme.onSurface
            }
            LabelRow(
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
    }
}

@Composable
private fun PaymentsCard(view: DebtView, onDelete: (String) -> Unit) {
    SectionCard {
        Text("Платежи", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (view.payments.isEmpty()) {
            Text("Платежей пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        view.payments.forEach { p ->
            LabelRow(
                label = formatDate(p.date),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatMoney(p.amountMinor, view.debt.currency), fontWeight = FontWeight.Bold)
                        TextButton(onClick = { onDelete(p.id) }) { Text("✕") }
                    }
                },
            )
        }
    }
}

@Composable
private fun PaymentDialog(
    currencyCode: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, Long) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val amount = AmountParser.parseMinor(amountText, currencyOf(currencyCode).fractionDigits)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый платёж") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Сумма, ${currencyOf(currencyCode).symbol}") },
                    singleLine = true,
                    isError = amountText.isNotBlank() && amount == null,
                )
                DatePickerField("Дата", date, { date = it })
            }
        },
        confirmButton = {
            TextButton(enabled = amount != null, onClick = { onConfirm(date, amount!!) }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
