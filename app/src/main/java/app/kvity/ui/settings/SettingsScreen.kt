package app.kvity.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.kvity.data.Settings
import app.kvity.data.ThemeMode
import app.kvity.domain.Currencies
import app.kvity.ui.components.DropdownField
import app.kvity.ui.components.FormScreen
import app.kvity.ui.components.SectionCard
import app.kvity.ui.currencyOf
import java.time.DayOfWeek

@Composable
fun SettingRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle) { Switch(checked = checked, onCheckedChange = onChange) }
}

@Composable
fun GroupTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun GeneralSettingsScreen(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
    onOpenRates: () -> Unit,
    onOpenTemplates: () -> Unit,
    sections: @Composable () -> Unit = {},
) {
    var currenciesDialog by remember { mutableStateOf(false) }

    FormScreen(title = "Настройки", onBack = onBack) {
        SectionCard {
            GroupTitle("Внешний вид")
            DropdownField(
                "Тема",
                listOf(ThemeMode.SYSTEM to "Системная", ThemeMode.LIGHT to "Светлая", ThemeMode.DARK to "Тёмная"),
                settings.theme, { t -> onChange { it.copy(theme = t) } },
                Modifier.padding(top = 6.dp),
            )
            SettingSwitch(
                "Палитра для дальтоников", "Синий и оранжевый вместо зелёного и красного",
                settings.colorBlindPalette, { v -> onChange { it.copy(colorBlindPalette = v) } },
            )
            DropdownField(
                "Первый день недели",
                listOf(DayOfWeek.MONDAY to "Понедельник", DayOfWeek.SUNDAY to "Воскресенье"),
                settings.firstDayOfWeek, { d -> onChange { it.copy(firstDayOfWeek = d) } },
            )
        }

        SectionCard {
            GroupTitle("Валюты")
            DropdownField(
                "Основная",
                settings.enabledCurrencies.map { it to "${currencyOf(it).symbol} $it" },
                settings.defaultCurrency, { c -> onChange { it.copy(defaultCurrency = c) } },
                Modifier.padding(top = 6.dp),
            )
            TextButton(onClick = { currenciesDialog = true }) { Text("Мои валюты: ${settings.enabledCurrencies.joinToString(", ")}") }
            TextButton(onClick = onOpenRates) { Text("Курсы валют") }
        }

        SectionCard {
            GroupTitle("Напоминания должникам")
            TextButton(onClick = onOpenTemplates) { Text("Шаблоны сообщений") }
        }

        sections()
    }

    if (currenciesDialog) {
        var selected by remember { mutableStateOf(settings.enabledCurrencies.toSet()) }
        AlertDialog(
            onDismissRequest = { currenciesDialog = false },
            title = { Text("Мои валюты") },
            text = {
                Column {
                    (Currencies.defaults.map { it.code } + listOf("GBP", "CNY", "UAH", "BYN", "TRY", "AED", "GEL", "UZS", "KGS"))
                        .distinct().forEach { code ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = code in selected,
                                    onCheckedChange = { on -> selected = if (on) selected + code else selected - code },
                                )
                                Text("${currencyOf(code).symbol} $code")
                            }
                        }
                }
            },
            confirmButton = {
                TextButton(enabled = selected.isNotEmpty(), onClick = {
                    val list = selected.toList().sorted()
                    onChange { s -> s.copy(enabledCurrencies = list, defaultCurrency = if (s.defaultCurrency in list) s.defaultCurrency else list.first()) }
                    currenciesDialog = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { currenciesDialog = false }) { Text("Отмена") } },
        )
    }
}
