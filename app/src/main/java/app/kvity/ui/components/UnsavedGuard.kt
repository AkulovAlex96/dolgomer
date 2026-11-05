package app.kvity.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Чистая логика «есть несохранённые изменения»: состояние формы сворачивается в строку и сравнивается с исходной. */
object FormChanges {
    /** Снимок значений полей; разделитель не встречается в тексте, поэтому разные наборы не склеиваются в одинаковую строку. */
    fun snapshot(vararg values: Any?): String = values.joinToString("\u0001") { it?.toString() ?: "\u0000" }

    fun isDirty(initial: String, current: String): Boolean = initial != current
}

/**
 * Защита выхода из формы. Возвращает действие «выйти»: без изменений оно сразу вызывает [onExit],
 * с изменениями показывает диалог «Отменить изменения?». Системная кнопка Назад обрабатывается так же.
 */
@Composable
fun rememberExitGuard(dirty: Boolean, onExit: () -> Unit): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    BackHandler { if (dirty) asking = true else onExit() }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Отменить изменения?") },
            text = { Text("Введённые данные не сохранены и будут потеряны.") },
            confirmButton = { TextButton(onClick = { asking = false; onExit() }) { Text("Выйти") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Остаться") } },
        )
    }
    return { if (dirty) asking = true else onExit() }
}
