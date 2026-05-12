package com.transcard.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.transcard.domain.repository.SnapshotHistoryEntry
import com.transcard.presentation.util.relativeTimeRu
import com.transcard.presentation.viewmodel.AccountViewModel

object SnapshotHistoryScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm: AccountViewModel = koinScreenModel()
        val state by vm.history.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        var pendingRestore by remember { mutableStateOf<SnapshotHistoryEntry?>(null) }

        LaunchedEffect(Unit) { vm.loadHistory() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("История снапшотов") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            }
        ) { pad ->
            Box(modifier = Modifier.fillMaxSize().padding(pad)) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    state.error != null -> Text(
                        state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    state.items.isEmpty() -> Text(
                        "На сервере пока нет снапшотов",
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                    ) {
                        items(state.items) { item ->
                            HistoryRow(item, onClick = { pendingRestore = item })
                        }
                    }
                }
            }
        }

        pendingRestore?.let { entry ->
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text("Восстановить снапшот?") },
                text = {
                    Text(
                        "Все текущие локальные карточки будут заменены данными из этого снапшота (${relativeTimeRu(entry.createdAt)}, ${entry.sizeBytes / 1024} КБ).",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val id = entry.id
                        pendingRestore = null
                        vm.restoreSnapshot(id) { navigator.pop() }
                    }) { Text("Восстановить") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRestore = null }) { Text("Отмена") }
                },
            )
        }
    }
}

@Composable
private fun HistoryRow(item: SnapshotHistoryEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(relativeTimeRu(item.createdAt), style = MaterialTheme.typography.titleSmall)
            Text(
                buildString {
                    append("${item.sizeBytes / 1024} КБ")
                    if (item.clientInfo != null) append(" · ${item.clientInfo}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
