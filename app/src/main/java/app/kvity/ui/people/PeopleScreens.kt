package app.kvity.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kvity.data.DebtView
import app.kvity.data.PersonView
import app.kvity.data.SubscriptionView
import app.kvity.domain.DebtStatus
import app.kvity.domain.Direction
import app.kvity.ui.components.FormScreen
import app.kvity.ui.components.LabelRow
import app.kvity.ui.components.SectionCard
import app.kvity.ui.components.StatusPill
import app.kvity.ui.formatMoney
import app.kvity.ui.theme.kvity

@Composable
fun PeopleScreen(people: List<PersonView>, onOpen: (String) -> Unit) {
    val c = MaterialTheme.kvity
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Люди", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
            if (people.isEmpty()) {
                item {
                    SectionCard { Text("Здесь появятся люди, с которыми есть долги или общие подписки.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            items(people, key = { it.person.id }) { p ->
                SectionCard(modifier = Modifier.clickable { onOpen(p.person.id) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.person.name, fontWeight = FontWeight.SemiBold)
                            Text("долгов: ${p.debtCount} · подписок: ${p.shareCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (p.balance.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            p.balance.forEach { (code, v) ->
                                Text(
                                    (if (v > 0) "+" else "−") + formatMoney(kotlin.math.abs(v), code),
                                    fontWeight = FontWeight.Bold, color = if (v > 0) c.income else c.expense,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonDetailScreen(
    person: PersonView?,
    debts: List<DebtView>,
    subs: List<SubscriptionView>,
    onBack: () -> Unit,
    onSave: (name: String, phone: String?, note: String?) -> Unit,
    onDelete: () -> Unit,
    onOpenDebt: (String) -> Unit,
    onOpenSubscription: (String) -> Unit,
) {
    val c = MaterialTheme.kvity
    FormScreen(title = person?.person?.name ?: "Контрагент", onBack = onBack) {
        if (person == null) { Text("Загрузка…"); return@FormScreen }
        var name by rememberSaveable(person.person.id) { mutableStateOf(person.person.name) }
        var phone by rememberSaveable(person.person.id) { mutableStateOf(person.person.phone ?: "") }
        var note by rememberSaveable(person.person.id) { mutableStateOf(person.person.note ?: "") }
        var error by rememberSaveable { mutableStateOf(false) }

        SectionCard {
            OutlinedTextField(name, { name = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("Телефон") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(note, { note = it }, label = { Text("Заметка") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Button(
                enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = { onSave(name, phone.ifBlank { null }, note.ifBlank { null }) },
            ) { Text("Сохранить") }
        }

        if (person.balance.isNotEmpty()) {
            SectionCard {
                Text("Баланс", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                person.balance.forEach { (code, v) ->
                    LabelRow(if (v > 0) "мне должен" else "я должен", trailing = {
                        Text(formatMoney(kotlin.math.abs(v), code), fontWeight = FontWeight.Bold, color = if (v > 0) c.income else c.expense)
                    })
                }
            }
        }

        if (debts.isNotEmpty()) {
            SectionCard {
                Text("Долги", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                debts.forEach { v ->
                    val mine = v.debt.direction == Direction.OWED_TO_ME
                    LabelRow(
                        onClick = { onOpenDebt(v.debt.id) },
                        label = if (mine) "мне должен" else "я должен",
                        subtitle = if (v.summary.status == DebtStatus.CLOSED) "закрыт" else null,
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatMoney(v.summary.remainingMinor, v.debt.currency), fontWeight = FontWeight.Bold, color = if (mine) c.income else c.expense)
                                StatusPill(v.summary.status, v.summary.overdueDays)
                            }
                        },
                    )
                }
            }
        }

        if (subs.isNotEmpty()) {
            SectionCard {
                Text("Подписки", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                subs.forEach { v ->
                    val unpaid = v.shares.filter { it.share.counterpartyId == person.person.id }
                        .sumOf { sh -> sh.dues.count { !it.paid && it.periodDate.isBefore(java.time.LocalDate.now().plusDays(1)) } }
                    LabelRow(
                        onClick = { onOpenSubscription(v.subscription.id) }, label = v.subscription.name,
                        subtitle = if (unpaid > 0) "не оплачено периодов: $unpaid" else null,
                        trailing = { Text(formatMoney(v.subscription.amountMinor, v.subscription.currency)) },
                    )
                }
            }
        }

        TextButton(onClick = {
            if (person.debtCount == 0 && person.shareCount == 0) onDelete() else error = true
        }) { Text("Удалить контрагента") }
        if (error) Text("Нельзя удалить: есть долги или подписки.", color = c.expense)
    }
}
