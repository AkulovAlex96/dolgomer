package app.kvity.ui.components

import androidx.compose.ui.res.stringResource
import app.kvity.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Экран с верхней панелью и прокручиваемым содержимым. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreen(
    title: String,
    onBack: () -> Unit,
    backLabel: String = stringResource(R.string.comp_back),
    hasChanges: Boolean = false,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val exit = rememberExitGuard(hasChanges, onBack)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = exit) { Text(backLabel) } },
                actions = { actions() },
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
        ) { content() }
    }
}
