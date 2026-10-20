package app.kvity.ui.reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.kvity.data.ReminderTemplateEntity
import app.kvity.data.TemplateText
import app.kvity.ui.components.DropdownField

/** Данные для подстановки в шаблон. */
data class ReminderContext(
    val name: String,
    val phone: String?,
    val amount: String,
    val remaining: String,
    val due: String,
    val overdueDays: Long,
    val debtId: String? = null,
    val shareId: String? = null,
    val counterpartyId: String? = null,
)

object ReminderSender {
    /** Открывает системный выбор приложения (WhatsApp, Telegram и т.д.) с готовым текстом. */
    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(send, "Отправить напоминание").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun sms(context: Context, phone: String, text: String) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply { putExtra("sms_body", text) }
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun dial(context: Context, phone: String) {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun ReminderDialog(
    ctx: ReminderContext,
    templates: List<ReminderTemplateEntity>,
    onLog: (channel: String, template: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(templates.firstOrNull()?.id) }
    val template = templates.firstOrNull { it.id == selected }
    fun render(t: ReminderTemplateEntity?) = TemplateText.render(
        t?.text ?: "{имя}, напоминаю про долг: {остаток}.", ctx.name, ctx.amount, ctx.remaining, ctx.due, ctx.overdueDays,
    )
    var text by remember(selected) { mutableStateOf(render(template)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Напомнить: ${ctx.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (templates.isNotEmpty()) {
                    DropdownField("Шаблон", templates.map { it.id to it.name }, selected ?: templates.first().id, { selected = it })
                }
                OutlinedTextField(text, { text = it }, label = { Text("Сообщение") })
                if (ctx.phone != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { ReminderSender.sms(context, ctx.phone, text); onLog("SMS", template?.name); onDismiss() }) { Text("SMS") }
                        TextButton(onClick = { ReminderSender.dial(context, ctx.phone); onLog("Звонок", null); onDismiss() }) { Text("Позвонить") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { ReminderSender.share(context, text); onLog("Мессенджер", template?.name); onDismiss() }) {
                Text("Отправить через…")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
