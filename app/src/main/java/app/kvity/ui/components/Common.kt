package app.kvity.ui.components

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.kvity.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kvity.domain.DebtStatus
import app.kvity.ui.formatMoney
import app.kvity.ui.theme.kvity

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        // clip до clickable из modifier, иначе ripple нажатия получается прямоугольным поверх скруглённой карточки
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).then(modifier).fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { content() }
    }
}

/** Подтверждение необратимого действия (удаление). */
@Composable
fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmLabel: String = stringResource(R.string.comp_delete)) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.comp_cancel)) } },
    )
}

@Composable
fun Pill(text: String, background: Color, foreground: Color) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, color = foreground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusPill(status: DebtStatus, overdueDays: Long = 0) {
    val c = MaterialTheme.kvity
    when (status) {
        DebtStatus.OVERDUE -> Pill(pluralStringResource(R.plurals.comp_overdue_days_plural, overdueDays.toInt(), overdueDays.toInt()), c.expenseBg, c.expense)
        DebtStatus.DUE_SOON -> Pill(stringResource(R.string.comp_due_soon), c.warnBg, c.warn)
        DebtStatus.CLOSED -> Pill(stringResource(R.string.comp_closed), c.incomeBg, c.income)
        DebtStatus.ACTIVE -> Unit
    }
}

/** Суммы по валютам столбиком, справа. Если сумм нет, показывает «—». */
@Composable
fun MoneyLines(amounts: Map<String, Long>, color: Color, bold: Boolean = true) {
    if (amounts.isEmpty()) {
        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Column(horizontalAlignment = Alignment.End) {
        amounts.entries.sortedByDescending { it.value }.forEach { (code, minor) ->
            Text(
                formatMoney(minor, code),
                color = color,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
fun LabelRow(label: String, trailing: @Composable () -> Unit, subtitle: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}
