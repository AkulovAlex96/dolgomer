package app.kvity.ui.people

import app.kvity.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.material3.OutlinedButton
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
import app.kvity.domain.Limits
import app.kvity.ui.components.ConfirmDialog
import app.kvity.ui.components.FormScreen
import app.kvity.ui.components.LabelRow
import app.kvity.ui.components.SectionCard
import app.kvity.ui.components.StatusPill
import app.kvity.ui.formatMoney
import app.kvity.ui.theme.kvity

@Composable
fun PeopleScreen(
    people: List<PersonView>,
    onOpen: (String) -> Unit,
    /** Архивные контрагенты: показываются в свёрнутом разделе «Архив». */
    archived: List<PersonView> = emptyList(),
    onUnarchive: (String) -> Unit = {},
) {
    val c = MaterialTheme.kvity
    var archiveOpen by rememberSaveable { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(stringResource(R.string.people_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
            if (people.isEmpty()) {
                item {
                    SectionCard { Text(stringResource(R.string.people_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            items(people, key = { it.person.id }) { p ->
                SectionCard(modifier = Modifier.clickable { onOpen(p.person.id) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.person.name, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.people_counts, p.debtCount, p.shareCount), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            if (archived.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { archiveOpen = !archiveOpen }.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.people_archive_header, archived.size), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (archiveOpen) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (archiveOpen) {
                    items(archived, key = { "archived-" + it.person.id }) { p ->
                        SectionCard(modifier = Modifier.clickable { onOpen(p.person.id) }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.person.name, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.people_in_archive), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { onUnarchive(p.person.id) }) { Text(stringResource(R.string.people_unarchive)) }
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
    /** Удаление: результат приходит в переданный колбэк (false: удалить нельзя). */
    onDelete: (onResult: (Boolean) -> Unit) -> Unit,
    onOpenDebt: (String) -> Unit,
    onOpenSubscription: (String) -> Unit,
    /** Отправка в архив: возвращает причину отказа или null, если выполнено. */
    onArchive: () -> String? = { null },
    onUnarchive: () -> Unit = {},
    /** Имена остальных контрагентов в нижнем регистре: два контрагента с одним именем запутали бы импорт, где люди ищутся по имени. */
    otherNames: Set<String> = emptySet(),
) {
    val c = MaterialTheme.kvity
    FormScreen(title = person?.person?.name ?: stringResource(R.string.people_counterparty), onBack = onBack) {
        if (person == null) { Text(stringResource(R.string.people_loading)); return@FormScreen }
        var name by rememberSaveable(person.person.id) { mutableStateOf(person.person.name) }
        var phone by rememberSaveable(person.person.id) { mutableStateOf(person.person.phone ?: "") }
        var note by rememberSaveable(person.person.id) { mutableStateOf(person.person.note ?: "") }
        var deleteBlocked by rememberSaveable { mutableStateOf(false) }
        var archiveError by rememberSaveable { mutableStateOf<String?>(null) }
        var confirmDelete by rememberSaveable { mutableStateOf(false) }
        if (confirmDelete) {
            ConfirmDialog(
                title = stringResource(R.string.people_delete_title),
                text = stringResource(R.string.people_delete_text, person.person.name),
                onConfirm = { confirmDelete = false; onDelete { ok -> if (!ok) deleteBlocked = true } },
                onDismiss = { confirmDelete = false },
            )
        }

        SectionCard {
            val duplicate = name.trim().lowercase() in otherNames
            OutlinedTextField(
                name, { name = it.take(Limits.NAME) }, label = { Text(stringResource(R.string.people_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = duplicate, supportingText = if (duplicate) ({ Text(stringResource(R.string.people_duplicate)) }) else null,
            )
            OutlinedTextField(phone, { phone = it.take(Limits.PHONE) }, label = { Text(stringResource(R.string.people_phone)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(note, { note = it.take(Limits.NOTE) }, label = { Text(stringResource(R.string.people_note)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Button(
                enabled = name.isNotBlank() && name.trim().lowercase() !in otherNames, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = { onSave(name, phone.ifBlank { null }, note.ifBlank { null }) },
            ) { Text(stringResource(R.string.people_save)) }
        }

        if (person.balance.isNotEmpty()) {
            SectionCard {
                Text(stringResource(R.string.people_balance), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                person.balance.forEach { (code, v) ->
                    LabelRow(if (v > 0) stringResource(R.string.people_owes_me) else stringResource(R.string.people_i_owe), trailing = {
                        Text(formatMoney(kotlin.math.abs(v), code), fontWeight = FontWeight.Bold, color = if (v > 0) c.income else c.expense)
                    })
                }
            }
        }

        if (debts.isNotEmpty()) {
            SectionCard {
                Text(stringResource(R.string.people_debts), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                debts.forEach { v ->
                    val mine = v.debt.direction == Direction.OWED_TO_ME
                    LabelRow(
                        onClick = { onOpenDebt(v.debt.id) },
                        label = if (mine) stringResource(R.string.people_owes_me) else stringResource(R.string.people_i_owe),
                        subtitle = if (v.summary.status == DebtStatus.CLOSED) stringResource(R.string.people_closed) else null,
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
                Text(stringResource(R.string.people_subscriptions), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                subs.forEach { v ->
                    val unpaid = v.shares.filter { it.share.counterpartyId == person.person.id }
                        .sumOf { sh -> sh.dues.count { !it.paid && it.periodDate.isBefore(java.time.LocalDate.now().plusDays(1)) } }
                    LabelRow(
                        onClick = { onOpenSubscription(v.subscription.id) }, label = v.subscription.name,
                        subtitle = if (unpaid > 0) stringResource(R.string.people_unpaid_periods, unpaid) else null,
                        trailing = { Text(formatMoney(v.subscription.amountMinor, v.subscription.currency)) },
                    )
                }
            }
        }

        if (person.archived) {
            Text(stringResource(R.string.people_archived_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onUnarchive, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.people_unarchive)) }
        } else {
            OutlinedButton(onClick = { archiveError = onArchive() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.people_to_archive)) }
        }
        archiveError?.let { Text(it, color = c.expense) }
        TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.people_delete)) }
        if (deleteBlocked) {
            Text(
                stringResource(R.string.people_delete_blocked),
                color = c.expense,
            )
            if (!person.archived) {
                OutlinedButton(onClick = { deleteBlocked = false; archiveError = onArchive() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.people_to_archive)) }
            }
        }
    }
}
