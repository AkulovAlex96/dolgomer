package app.kvity.ui.debt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kvity.data.DebtView
import app.kvity.data.NewDebt
import app.kvity.domain.AmountFormatter
import app.kvity.domain.AmountParser
import app.kvity.domain.Currencies
import app.kvity.domain.Direction
import app.kvity.domain.Periodicity
import app.kvity.domain.ScheduleGenerator
import app.kvity.domain.ScheduleRule
import app.kvity.domain.TermType
import app.kvity.ui.components.DatePickerField
import app.kvity.ui.components.SectionCard
import app.kvity.ui.currencyOf
import app.kvity.ui.formatDate
import app.kvity.ui.formatMoney
import app.kvity.ui.minorToInput
import java.time.LocalDate

private val periodicities = listOf(
    Periodicity.WEEK to "Раз в неделю",
    Periodicity.MONTH to "Раз в месяц",
    Periodicity.YEAR to "Раз в год",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDebtScreen(
    onSave: (NewDebt) -> Unit,
    onBack: () -> Unit,
    initial: DebtView? = null,
    defaultCurrency: String = Currencies.RUB.code,
    currencies: List<String> = Currencies.defaults.map { it.code },
    knownNames: List<String> = emptyList(),
) {
    val d = initial?.debt
    fun minorText(minor: Long?, code: String) = minorToInput(minor, code)
    var direction by rememberSaveable { mutableStateOf(d?.direction ?: Direction.OWED_TO_ME) }
    var name by rememberSaveable { mutableStateOf(initial?.counterpartyName ?: "") }
    var amountText by rememberSaveable { mutableStateOf(minorText(d?.principalMinor, d?.currency ?: defaultCurrency)) }
    var currencyCode by rememberSaveable { mutableStateOf(d?.currency ?: defaultCurrency) }
    var termType by rememberSaveable { mutableStateOf(d?.termType ?: TermType.ONE_TIME) }
    var dueEpoch by rememberSaveable { mutableStateOf<Long?>(d?.dueDate?.toEpochDay()) }
    var installmentText by rememberSaveable { mutableStateOf(minorText(d?.installmentMinor, d?.currency ?: defaultCurrency)) }
    var periodicity by rememberSaveable { mutableStateOf(d?.periodicity ?: Periodicity.MONTH) }
    var firstEpoch by rememberSaveable { mutableStateOf<Long?>(d?.firstDate?.toEpochDay()) }
    var note by rememberSaveable { mutableStateOf(d?.note ?: "") }

    val currency = currencyOf(currencyCode)
    val principal = AmountParser.parseMinor(amountText, currency.fractionDigits)
    val installment = AmountParser.parseMinor(installmentText, currency.fractionDigits)
    val dueDate = dueEpoch?.let(LocalDate::ofEpochDay)
    val firstDate = firstEpoch?.let(LocalDate::ofEpochDay)

    val valid = name.isNotBlank() && principal != null && when (termType) {
        TermType.ONE_TIME -> dueDate != null
        TermType.SCHEDULE -> installment != null && firstDate != null
        TermType.NONE -> true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Новый долг" else "Изменить долг") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Отмена") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = direction == Direction.OWED_TO_ME,
                    onClick = { direction = Direction.OWED_TO_ME },
                    label = { Text("Мне должны") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = direction == Direction.I_OWE,
                    onClick = { direction = Direction.I_OWE },
                    label = { Text("Я должен") },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Кто") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val suggestions = knownNames.filter { it.contains(name.trim(), ignoreCase = true) && !it.equals(name.trim(), ignoreCase = true) }.take(4)
            if (name.isNotBlank() && suggestions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { n -> androidx.compose.material3.AssistChip(onClick = { name = n }, label = { Text(n) }) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Сумма (можно «50к»)") },
                    singleLine = true,
                    isError = amountText.isNotBlank() && principal == null,
                    modifier = Modifier.weight(1f),
                )
                CurrencyMenu(currencyCode, currencies, onSelect = { currencyCode = it }, modifier = Modifier.weight(0.6f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TermType.ONE_TIME to "Разовый",
                    TermType.SCHEDULE to "По графику",
                    TermType.NONE to "Без срока",
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = termType == type,
                        onClick = { termType = type },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            when (termType) {
                TermType.ONE_TIME -> DatePickerField("Срок возврата", dueDate, { dueEpoch = it.toEpochDay() })
                TermType.SCHEDULE -> {
                    OutlinedTextField(
                        value = installmentText,
                        onValueChange = { installmentText = it },
                        label = { Text("Сумма платежа") },
                        singleLine = true,
                        isError = installmentText.isNotBlank() && installment == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PeriodicityMenu(periodicity, onSelect = { periodicity = it })
                    DatePickerField("Первый платёж", firstDate, { firstEpoch = it.toEpochDay() })

                    if (principal != null && installment != null && firstDate != null) {
                        SchedulePreview(principal, installment, periodicity, firstDate, currencyCode)
                    }
                }
                TermType.NONE -> Text(
                    "Срок не задан: платежи вносятся по мере возврата.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Комментарий") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onSave(
                        NewDebt(
                            counterpartyName = name,
                            direction = direction,
                            principalMinor = principal!!,
                            currency = currencyCode,
                            startDate = d?.startDate ?: LocalDate.now(),
                            termType = termType,
                            dueDate = dueDate.takeIf { termType == TermType.ONE_TIME },
                            periodicity = periodicity.takeIf { termType == TermType.SCHEDULE },
                            installmentMinor = installment.takeIf { termType == TermType.SCHEDULE },
                            firstDate = firstDate.takeIf { termType == TermType.SCHEDULE },
                            note = note.ifBlank { null },
                        ),
                    )
                },
            ) { Text("Сохранить") }
        }
    }
}

@Composable
private fun SchedulePreview(
    principal: Long,
    installment: Long,
    periodicity: Periodicity,
    firstDate: LocalDate,
    currencyCode: String,
) {
    val plan = ScheduleGenerator.generate(ScheduleRule(periodicity, installment, firstDate), principal)
    SectionCard {
        Text("Будет создано платежей: ${plan.size}", style = MaterialTheme.typography.titleSmall)
        val shown = plan.take(4).joinToString(" · ") { formatDate(it.dueDate) }
        Text(
            if (plan.size > 4) "$shown · …" else shown,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        plan.lastOrNull()?.let {
            Text(
                "Последний: ${formatDate(it.dueDate)}, ${formatMoney(it.amountMinor, currencyCode)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CurrencyMenu(selectedCode: String, codes: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("${currencyOf(selectedCode).symbol} $selectedCode")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            codes.map(::currencyOf).forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.symbol} ${c.code}") },
                    onClick = { onSelect(c.code); open = false },
                )
            }
        }
    }
}

@Composable
private fun PeriodicityMenu(selected: Periodicity, onSelect: (Periodicity) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(periodicities.first { it.first == selected }.second)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            periodicities.forEach { (p, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(p); open = false })
            }
        }
    }
}
