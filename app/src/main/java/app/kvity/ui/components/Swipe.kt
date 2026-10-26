package app.kvity.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Горизонтальный свайп: влево — [onLeft] (следующее), вправо — [onRight] (предыдущее). Вертикальная прокрутка не мешает. */
@Composable
fun Modifier.swipeHorizontally(onLeft: () -> Unit, onRight: () -> Unit): Modifier {
    val left by rememberUpdatedState(onLeft)
    val right by rememberUpdatedState(onRight)
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    return pointerInput(threshold) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (total <= -threshold) left() else if (total >= threshold) right()
                total = 0f
            },
            onDragCancel = { total = 0f },
        ) { _, delta -> total += delta }
    }
}
