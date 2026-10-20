package app.kvity.ui.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kvity.data.SubscriptionInput
import app.kvity.data.SubscriptionPayer
import app.kvity.data.SubscriptionView
import app.kvity.domain.AmountParser
import app.kvity.domain.Periodicity
import app.kvity.ui.components.ChipRow
import app.kvity.ui.components.DatePickerField
import app.kvity.ui.components.DropdownField
import app.kvity.ui.components.FormScreen
import app.kvity.ui.currencyOf
import app.kvity.ui.minorToInput
import java.time.LocalDate

private val periods = listOf(
    Periodicity.WEEK to "Неделя",
    Periodicity.MONTH to "Месяц",
    Periodicity.YEAR to "Год",
)

@Composable
fun SubscriptionFormScreen(
    initial: SubscriptionView?,
    defaultCurrency: String,
    currencies: List<String>,
    knownNames: List<String>,
    onSave: (SubscriptionInput) -> Unit,
    onBack: () -> Unit,
) {
    val s = initial?.subscription
    var name by rememberSaveable { mutableStateOf(s?.name ?: "") }
    var currency by rememberSaveable { mutableStateOf(s?.currency ?: defaultCurrency) }
    var amountText by rememberSaveable { mutableStateOf(minorToInput(s?.amountMinor, s?.currency ?: defaultCurrency)) }
    var periodicity by rememberSaveable { mutableStateOf(s?.periodicity ?: Periodicity.MONTH) }
    var anchorEpoch by rememberSaveable { mutableStateOf<Long?>((s?.anchorDate ?: LocalDate.now()).toEpochDay()) }
    var payer by rememberSaveable { mutableStateOf(s?.payer ?: SubscriptionPayer.ME) }
    var payerName by rememberSaveable { mutableStateOf(initial?.payerName ?: "") }
    var method by rememberSaveable { mutableStateOf(s?.paymentMethodNote ?: "") }
    var trialEpoch by rememberSaveable { mutableStateOf<Long?>(s?.trialEndsOn?.toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(s?.note ?: "") }

    val amount = AmountParser.parseMinor(amountText, currencyOf(currency).fractionDigits)
    val anchor = anchorEpoch?.let(LocalDate::ofEpochDay)
    val valid = name.isNotBlank() && amount != null && anchor != null &&
        (payer == SubscriptionPayer.ME || payerName.isNotBlank())

    FormScreen(title = if (initial == null) "Новая подписка" else "Изменить подписку", onBack = onBack, backLabel = "Отмена") {
        OutlinedTextField(name, { name = it }, label = { Text("Название (ChatGPT, GitHub…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amountText, onValueChange = { amountText = it },
                label = { Text(if (payer == SubscriptionPayer.ME) "Сумма списания" else "Моя доля") },
                singleLine = true, isError = amountText.isNotBlank() && amount == null,
                modifier = Modifier.weight(1f),
            )
            DropdownField("", currencies.map { it to "${currencyOf(it).symbol} $it" }, currency, { currency = it }, Modifier.weight(0.6f))
        }

        DropdownField("Период", periods, periodicity, { periodicity = it })
        DatePickerField("Дата списания (любая известная)", anchor, { anchorEpoch = it.toEpochDay() })

        ChipRow(listOf(SubscriptionPayer.ME to "Плачу я", SubscriptionPayer.OTHER to "Платит другой"), payer, { payer = it })
        if (payer == SubscriptionPayer.OTHER) {
            OutlinedTextField(payerName, { payerName = it }, label = { Text("Кто платит") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val hints = knownNames.filter { it.contains(payerName.trim(), true) && !it.equals(payerName.trim(), true) }.take(4)
            if (payerName.isNotBlank() && hints.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    hints.forEach { h -> androidx.compose.material3.AssistChip(onClick = { payerName = h }, label = { Text(h) }) }
                }
            }
        } else {
            Text("Участников, которые возвращают долю, можно добавить на экране подписки.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedTextField(method, { method = it }, label = { Text("Способ оплаты (необязательно)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        DatePickerField("Конец пробного периода (необязательно)", trialEpoch?.let(LocalDate::ofEpochDay), { trialEpoch = it.toEpochDay() })
        OutlinedTextField(note, { note = it }, label = { Text("Комментарий") }, modifier = Modifier.fillMaxWidth())

        Button(
            enabled = valid, modifier = Modifier.fillMaxWidth(),
            onClick = {
                onSave(
                    SubscriptionInput(
                        name = name, amountMinor = amount!!, currency = currency, periodicity = periodicity,
                        anchorDate = anchor!!, payer = payer, payerName = payerName.takeIf { payer == SubscriptionPayer.OTHER },
                        paymentMethodNote = method.ifBlank { null }, trialEndsOn = trialEpoch?.let(LocalDate::ofEpochDay),
                        note = note.ifBlank { null },
                    ),
                )
            },
        ) { Text("Сохранить") }
    }
}
