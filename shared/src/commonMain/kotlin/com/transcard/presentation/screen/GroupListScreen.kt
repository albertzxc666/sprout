package com.transcard.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.transcard.domain.model.StudyScope
import com.transcard.presentation.components.AppCard
import com.transcard.presentation.viewmodel.GroupCardItem
import com.transcard.presentation.viewmodel.GroupListViewModel
import org.koin.core.parameter.parametersOf

data class GroupListScreen(val spaceId: Long) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm: GroupListViewModel = koinScreenModel { parametersOf(spaceId) }
        val navigator = LocalNavigator.currentOrThrow
        val state by vm.state.collectAsState()

        var showCreate by remember { mutableStateOf(false) }
        var groupToRename by remember { mutableStateOf<GroupCardItem?>(null) }
        var groupToDelete by remember { mutableStateOf<GroupCardItem?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(state.space?.name ?: "") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Группа") },
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.totalDue > 0) {
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Изучать всё пространство",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "${state.totalDue} к повторению",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        navigator.push(
                                            StudySetupScreen(StudyScope.Space(spaceId))
                                        )
                                    }
                                ) {
                                    Icon(Icons.Filled.PlayArrow, "Начать")
                                }
                            }
                        }
                    }
                }

                items(state.items, key = { it.group.id }) { item ->
                    GroupRow(
                        item = item,
                        onOpen = { navigator.push(CardListScreen(item.group.id)) },
                        onStudy = {
                            navigator.push(StudySetupScreen(StudyScope.Group(item.group.id)))
                        },
                        onRename = { groupToRename = item },
                        onDelete = { groupToDelete = item }
                    )
                }
            }
        }

        if (showCreate) {
            GroupNameDialog(
                title = "Новая группа",
                initial = "",
                onConfirm = {
                    vm.createGroup(it)
                    showCreate = false
                },
                onDismiss = { showCreate = false }
            )
        }

        groupToRename?.let { gi ->
            GroupNameDialog(
                title = "Переименовать группу",
                initial = gi.group.name,
                onConfirm = {
                    vm.renameGroup(gi.group.id, it)
                    groupToRename = null
                },
                onDismiss = { groupToRename = null }
            )
        }

        groupToDelete?.let { gi ->
            AlertDialog(
                onDismissRequest = { groupToDelete = null },
                title = { Text("Удалить группу?") },
                text = {
                    Text("«${gi.group.name}» и ${gi.cardsCount} карточек будут удалены.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteGroup(gi.group.id)
                        groupToDelete = null
                    }) { Text("Удалить") }
                },
                dismissButton = {
                    TextButton(onClick = { groupToDelete = null }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
private fun GroupRow(
    item: GroupCardItem,
    onOpen: () -> Unit,
    onStudy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.group.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${item.cardsCount} карточек" +
                            (if (item.dueCount > 0) " · ${item.dueCount} к повторению" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.dueCount > 0) {
                    IconButton(onClick = onStudy) {
                        Icon(Icons.Filled.PlayArrow, "Начать")
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, "Меню")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Переименовать") },
                            onClick = { menuOpen = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить") },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 50) name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotEmpty()
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
