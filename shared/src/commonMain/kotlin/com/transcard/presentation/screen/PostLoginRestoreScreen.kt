package com.transcard.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.transcard.domain.repository.SnapshotHistoryEntry
import com.transcard.presentation.util.relativeTimeRu
import com.transcard.presentation.viewmodel.ErrorSource
import com.transcard.presentation.viewmodel.PostLoginRestoreUiState
import com.transcard.presentation.viewmodel.PostLoginRestoreViewModel

object PostLoginRestoreScreen : Screen {
    @Composable
    override fun Content() {
        val vm: PostLoginRestoreViewModel = koinScreenModel()
        val state by vm.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(state) {
            if (state is PostLoginRestoreUiState.Done) navigator.pop()
        }

        Scaffold { pad ->
            Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                when (val s = state) {
                    is PostLoginRestoreUiState.Loading -> ProgressBlock("Проверяем данные на сервере…")
                    is PostLoginRestoreUiState.Restoring -> ProgressBlock("Восстанавливаем…")
                    is PostLoginRestoreUiState.LoggingOut -> ProgressBlock("Отмена…")
                    is PostLoginRestoreUiState.Confirm -> RestorePrompt(
                        latest = s.latest,
                        onRestore = vm::confirmRestore,
                        onKeepLocal = vm::keepLocal,
                        onCancel = vm::cancel,
                    )
                    is PostLoginRestoreUiState.Error -> ErrorDialog(
                        message = s.message,
                        canDismissToPrompt = s.source == ErrorSource.Restore,
                        onRetry = vm::retry,
                        onBack = vm::dismissError,
                        onCancel = vm::cancel,
                    )
                    is PostLoginRestoreUiState.Done -> Unit // pop в LaunchedEffect
                }
            }
        }
    }
}

@Composable
private fun ProgressBlock(label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RestorePrompt(
    latest: SnapshotHistoryEntry,
    onRestore: () -> Unit,
    onKeepLocal: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "У этого аккаунта уже есть резервная копия",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                buildString {
                    append("Последний снапшот: ")
                    append(relativeTimeRu(latest.createdAt))
                    append(", ${latest.sizeBytes / 1024} КБ")
                    latest.clientInfo?.let { append(" · $it") }
                    append('.')
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Что сделать с текущими локальными карточками?",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Восстановить с сервера") }

            OutlinedButton(
                onClick = onKeepLocal,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Оставить локальные") }

            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Отмена") }
        }
    }
}

@Composable
private fun ErrorDialog(
    message: String,
    canDismissToPrompt: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* модально */ },
        title = { Text("Ошибка") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onRetry) { Text("Повторить") }
        },
        dismissButton = {
            if (canDismissToPrompt) {
                TextButton(onClick = onBack) { Text("Назад") }
            } else {
                TextButton(onClick = onCancel) { Text("Отмена") }
            }
        },
    )
}
