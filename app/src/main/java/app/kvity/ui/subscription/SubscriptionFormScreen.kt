package app.kvity.ui.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kvity.R
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
import app.kvity.domain.Limits
import app.kvity.domain.Periodicity
import app.kvity.ui.components.ChipRow
import app.kvity.ui.components.DatePickerField
import app.kvity.ui.components.DropdownField
import app.kvity.ui.components.FormChanges
import app.kvity.ui.components.FormScreen
import app.kvity.ui.currencyOf
import app.kvity.ui.minorToInput
import java.time.LocalDate

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
    var category by rememberSaveable { mutableStateOf(s?.category ?: "") }
    var intervalText by rememberSaveable { mutableStateOf((s?.interval ?: 1).toString()) }
    val snapshot = FormChanges.snapshot(name, currency, amountText, periodicity, anchorEpoch, payer, payerName, method, trialEpoch, note, category, intervalText)
    val initialSnapshot = rememberSaveable { snapshot }
    val interval = intervalText.toIntOrNull()?.takeIf { it in 1..99 }

    val amount = AmountParser.parseMinor(amountText, currencyOf(currency).fractionDigits)
    val anchor = anchorEpoch?.let(LocalDate::ofEpochDay)
    val valid = name.isNotBlank() && amount != null && anchor != null && interval != null &&
        (payer == SubscriptionPayer.ME || payerName.isNotBlank())

    val periods = listOf(
        Periodicity.WEEK to stringResource(R.string.sub_week),
        Periodicity.MONTH to stringResource(R.string.sub_month),
        Periodicity.YEAR to stringResource(R.string.sub_year),
    )
    FormScreen(title = if (initial == null) stringResource(R.string.sub_form_new) else stringResource(R.string.sub_form_edit), onBack = onBack, backLabel = stringResource(R.string.sub_dismiss), hasChanges = FormChanges.isDirty(initialSnapshot, snapshot)) {
        OutlinedTextField(name, { name = it.take(Limits.NAME) }, label = { Text(stringResource(R.string.sub_form_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amountText, onValueChange = { amountText = it },
                label = { Text(if (payer == SubscriptionPayer.ME) stringResource(R.string.sub_form_amount) else stringResource(R.string.sub_form_my_share)) },
                singleLine = true, isError = amountText.isNotBlank() && amount == null,
                modifier = Modifier.weight(1f),
            )
            DropdownField("", currencies.map { it to "${currencyOf(it).symbol} $it" }, currency, { currency = it }, Modifier.weight(0.6f))
        }

        DropdownField(stringResource(R.string.sub_form_period), periods, periodicity, { periodicity = it })
        OutlinedTextField(
            intervalText, { intervalText = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.sub_form_interval)) },
            singleLine = true, isError = interval == null, modifier = Modifier.fillMaxWidth(),
        )
        DatePickerField(stringResource(R.string.sub_form_anchor), anchor, { anchorEpoch = it.toEpochDay() })

        ChipRow(listOf(SubscriptionPayer.ME to stringResource(R.string.sub_form_payer_me), SubscriptionPayer.OTHER to stringResource(R.string.sub_form_payer_other)), payer, { payer = it })
        if (payer == SubscriptionPayer.OTHER) {
            OutlinedTextField(payerName, { payerName = it.take(Limits.NAME) }, label = { Text(stringResource(R.string.sub_form_payer_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val hints = knownNames.filter { it.contains(payerName.trim(), true) && !it.equals(payerName.trim(), true) }.take(4)
            if (payerName.isNotBlank() && hints.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    hints.forEach { h -> androidx.compose.material3.AssistChip(onClick = { payerName = h }, label = { Text(h) }) }
                }
            }
        } else {
            Text(stringResource(R.string.sub_form_participants_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedTextField(method, { method = it.take(Limits.NAME) }, label = { Text(stringResource(R.string.sub_form_method)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        DatePickerField(stringResource(R.string.sub_form_trial), trialEpoch?.let(LocalDate::ofEpochDay), { trialEpoch = it.toEpochDay() })
        OutlinedTextField(category, { category = it.take(Limits.NAME) }, label = { Text(stringResource(R.string.sub_form_category)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it.take(Limits.NOTE) }, label = { Text(stringResource(R.string.sub_form_note)) }, modifier = Modifier.fillMaxWidth())

        Button(
            enabled = valid, modifier = Modifier.fillMaxWidth(),
            onClick = {
                onSave(
                    SubscriptionInput(
                        name = name, amountMinor = amount!!, currency = currency, periodicity = periodicity, interval = interval!!,
                        anchorDate = anchor!!, payer = payer, payerName = payerName.takeIf { payer == SubscriptionPayer.OTHER },
                        paymentMethodNote = method.ifBlank { null }, trialEndsOn = trialEpoch?.let(LocalDate::ofEpochDay),
                        note = note.ifBlank { null }, category = category.trim().ifBlank { null },
                    ),
                )
            },
        ) { Text(stringResource(R.string.sub_form_save)) }
    }
}
