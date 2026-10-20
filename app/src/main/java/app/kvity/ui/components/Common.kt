package app.kvity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kvity.domain.DebtStatus
import app.kvity.ui.formatMoney
import app.kvity.ui.theme.kvity

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) { content() }
    }
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
        DebtStatus.OVERDUE -> Pill("Просрочен $overdueDays дн.", c.expenseBg, c.expense)
        DebtStatus.DUE_SOON -> Pill("Скоро срок", c.warnBg, c.warn)
        DebtStatus.CLOSED -> Pill("Закрыт", c.incomeBg, c.income)
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
        Column {
            Text(label, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}
