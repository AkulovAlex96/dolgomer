package app.kvity.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kvity.R
import app.kvity.notify.Notifier
import kotlinx.coroutines.launch

/** [icon] — декоративный эмодзи (не локализуется). */
private class OnboardingPage(val icon: String, val title: Int, val text: Int, val notifications: Boolean = false)

private val pages = listOf(
    OnboardingPage("📋", R.string.onboarding_debts_title, R.string.onboarding_debts_text),
    OnboardingPage("🔁", R.string.onboarding_recurring_title, R.string.onboarding_recurring_text),
    OnboardingPage("🔔", R.string.onboarding_notifications_title, R.string.onboarding_notifications_text, notifications = true),
    OnboardingPage("🔒", R.string.onboarding_data_title, R.string.onboarding_data_text),
)

/** Вводные слайды при первом запуске. [onFinish] вызывается и по «Начать», и по «Пропустить». */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = state.currentPage == pages.size - 1

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!last) TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
            }
            HorizontalPager(state, Modifier.weight(1f).fillMaxWidth()) { i -> PageContent(pages[i]) }
            StepIndicator(state.currentPage, pages.size, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp))
            Button(
                onClick = {
                    if (last) onFinish() else scope.launch { state.animateScrollToPage(state.currentPage + 1) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next)) }
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    val context = LocalContext.current
    // перечитывается при каждой перекомпоновке (после ответа на системный диалог activity возобновляется)
    var allowed by remember { mutableStateOf(Notifier.canPost(context)) }
    allowed = Notifier.canPost(context)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(page.icon, fontSize = 48.sp)
        }
        Spacer(Modifier.height(32.dp))
        Text(stringResource(page.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(page.text), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        if (page.notifications) ExactAlarmOffer()
        if (page.notifications && Build.VERSION.SDK_INT >= 33) {
            Spacer(Modifier.height(24.dp))
            if (allowed) {
                Text(stringResource(R.string.onboarding_notifications_allowed), color = MaterialTheme.colorScheme.primary)
            } else {
                OutlinedButton(onClick = {
                    (context as? android.app.Activity)?.let {
                        androidx.core.app.ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                    }
                }) { Text(stringResource(R.string.onboarding_notifications_allow)) }
            }
        }
    }
}

/**
 * Мягкое предложение точных напоминаний на слайде «Напоминания»: только когда уведомления разрешены,
 * точных будильников нет и ещё не спрашивали. Показ помечается сразу, поэтому повторно не навязывается.
 */
@Composable
private fun ExactAlarmOffer() {
    val kvityApp = LocalContext.current.applicationContext as app.kvity.KvityApp
    val settings by kvityApp.settings.settings.collectAsStateWithLifecycle()
    val offer = app.kvity.ui.rememberShouldOfferExactAlarm(settings)
    var shown by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    if (offer) shown = true
    if (!shown || done) return
    app.kvity.ui.MarkExactAlarmAsked()
    val context = LocalContext.current
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.exact_alarm_text), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { app.kvity.notify.ReminderAlarm.requestExactPermission(context); done = true }) {
            Text(stringResource(R.string.exact_alarm_allow))
        }
        TextButton(onClick = { done = true }) { Text(stringResource(R.string.exact_alarm_later)) }
    }
}

@Composable
private fun StepIndicator(current: Int, count: Int, modifier: Modifier = Modifier) {
    val desc = stringResource(R.string.onboarding_step_description, current + 1, count)
    Row(modifier.semantics { contentDescription = desc }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            val color = if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(Modifier.size(if (i == current) 10.dp else 8.dp).clip(CircleShape).background(color))
        }
    }
}
