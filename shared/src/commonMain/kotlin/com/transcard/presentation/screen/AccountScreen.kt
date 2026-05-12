package com.transcard.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.transcard.domain.sync.SyncStatus
import com.transcard.presentation.viewmodel.AccountViewModel

object AccountScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm: AccountViewModel = koinScreenModel()
        val auth by vm.authState.collectAsState()
        val sync by vm.syncStatus.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Аккаунт") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            }
        ) { pad ->
            Column(
                modifier = Modifier.fillMaxSize().padding(pad).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!auth.isAuthenticated) {
                    AnonymousState(
                        onLoginClick = { navigator.push(LoginScreen) },
                        onRegisterClick = { navigator.push(RegisterScreen) },
                    )
                } else {
                    AuthenticatedState(
                        email = auth.email ?: "—",
                        status = sync,
                        onSyncNowClick = vm::syncNow,
                        onHistoryClick = { navigator.push(SnapshotHistoryScreen) },
                        onSignOutClick = vm::signOut,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnonymousState(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
) {
    Text(
        "Войдите или создайте аккаунт, чтобы хранить копию карточек на сервере и восстанавливать их на новом устройстве.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onLoginClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Войти") }
    OutlinedButton(
        onClick = onRegisterClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Создать аккаунт") }
}

@Composable
private fun AuthenticatedState(
    email: String,
    status: SyncStatus,
    onSyncNowClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Вы вошли как", style = MaterialTheme.typography.bodySmall)
            Text(email, style = MaterialTheme.typography.titleMedium)
            SyncStatusLine(status)
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onSyncNowClick,
        enabled = status !is SyncStatus.Pushing && status !is SyncStatus.Pulling,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Синхронизировать сейчас") }
    OutlinedButton(
        onClick = onHistoryClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("История снапшотов") }
    TextButton(
        onClick = onSignOutClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Выйти", color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun SyncStatusLine(status: SyncStatus) {
    Box(modifier = Modifier.fillMaxWidth()) {
        when (status) {
            SyncStatus.Idle -> Text("Синхронизировано", style = MaterialTheme.typography.bodySmall)
            SyncStatus.Pushing -> InlineProgress("Отправка изменений…")
            SyncStatus.Pulling -> InlineProgress("Получение с сервера…")
            SyncStatus.NotAuthenticated -> Text("Не подключено", style = MaterialTheme.typography.bodySmall)
            is SyncStatus.Error -> Text(
                "Ошибка: ${status.message}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun InlineProgress(label: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
