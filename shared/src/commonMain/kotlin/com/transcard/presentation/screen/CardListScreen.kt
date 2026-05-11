package com.transcard.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
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
import androidx.compose.runtime.DisposableEffect
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
import com.transcard.domain.model.Card as CardModel
import com.transcard.domain.model.SuggestionSource
import com.transcard.domain.model.TranslationSuggestion
import com.transcard.presentation.components.AppCard
import com.transcard.presentation.components.EmptyState
import com.transcard.presentation.viewmodel.CardListViewModel
import org.koin.core.parameter.parametersOf

data class CardListScreen(val spaceId: Long) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm: CardListViewModel = koinScreenModel { parametersOf(spaceId) }
        val navigator = LocalNavigator.currentOrThrow
        val cards by vm.cards.collectAsState()
        val space by vm.space.collectAsState()

        var showCreate by remember { mutableStateOf(false) }
        var cardToEdit by remember { mutableStateOf<CardModel?>(null) }
        var cardToDelete by remember { mutableStateOf<CardModel?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(space?.name ?: "Карточки") },
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
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Добавить") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (cards.isNotEmpty()) {
                    Button(
                        onClick = { navigator.push(StudySetupScreen(spaceId)) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = MaterialTheme.shapes.medium
                    ) { Text("Начать обучение") }
                }
                if (cards.isEmpty()) {
                    Box(Modifier.fillMaxSize()) {
                        EmptyState(
                            title = "Добавьте первую карточку",
                            subtitle = "Карточка — это пара слов: на родном языке и на изучаемом. Sprout будет показывать одно и проверять перевод.",
                            icon = Icons.Outlined.Inbox
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(cards, key = { it.id }) { card ->
                            CardItem(
                                card = card,
                                onEdit = { cardToEdit = card },
                                onDelete = { cardToDelete = card }
                            )
                        }
                    }
                }
            }
        }

        if (showCreate) {
            CardDialog(
                title = "Новая карточка",
                initial = null,
                vm = vm,
                onDismiss = { showCreate = false },
                onConfirm = { native, target, hint ->
                    vm.addCard(native, target, hint)
                    showCreate = false
                }
            )
        }

        cardToEdit?.let { c ->
            CardDialog(
                title = "Редактировать карточку",
                initial = c,
                vm = vm,
                onDismiss = { cardToEdit = null },
                onConfirm = { native, target, hint ->
                    vm.updateCard(c.copy(nativeWord = native, targetWord = target, hint = hint?.takeIf { it.isNotBlank() }))
                    cardToEdit = null
                }
            )
        }

        cardToDelete?.let { c ->
            AlertDialog(
                onDismissRequest = { cardToDelete = null },
                title = { Text("Удалить карточку?") },
                text = { Text("«${c.nativeWord}» — «${c.targetWord}»") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteCard(c.id)
                        cardToDelete = null
                    }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { cardToDelete = null }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
private fun CardItem(
    card: CardModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(card.nativeWord, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "↔  ${card.targetWord}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                card.hint?.let {
                    Text(
                        "💡 $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Редактировать", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CardDialog(
    title: String,
    initial: CardModel?,
    vm: CardListViewModel,
    onDismiss: () -> Unit,
    onConfirm: (native: String, target: String, hint: String?) -> Unit
) {
    var native by remember { mutableStateOf(initial?.nativeWord ?: "") }
    var target by remember { mutableStateOf(initial?.targetWord ?: "") }
    var hint by remember { mutableStateOf(initial?.hint ?: "") }

    val suggestions by vm.suggestions.collectAsState()

    LaunchedEffect(native) { vm.onNativeWordChanged(native) }
    DisposableEffect(Unit) {
        onDispose { vm.clearSuggestions() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = native,
                    onValueChange = { native = it },
                    label = { Text("Слово (родной)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )

                SuggestionsBlock(
                    fromCards = suggestions.fromCards,
                    fromDictionary = suggestions.fromDictionary,
                    onPick = { target = it.text }
                )

                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Слово (изучаемый)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    label = { Text("Подсказка (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(native, target, hint.ifBlank { null }) },
                enabled = native.isNotBlank() && target.isNotBlank(),
                shape = MaterialTheme.shapes.medium
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun SuggestionsBlock(
    fromCards: List<TranslationSuggestion>,
    fromDictionary: List<TranslationSuggestion>,
    onPick: (TranslationSuggestion) -> Unit
) {
    if (fromCards.isEmpty() && fromDictionary.isEmpty()) return
    val hasOnline = fromDictionary.any { it.source == SuggestionSource.ONLINE_DICTIONARY }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (fromCards.isNotEmpty()) {
            SuggestionGroup(
                label = "Из ваших карточек",
                items = fromCards,
                onPick = onPick
            )
        }
        if (fromDictionary.isNotEmpty()) {
            SuggestionGroup(
                label = "Из словаря",
                items = fromDictionary,
                onPick = onPick
            )
        }
        if (hasOnline) {
            Text(
                text = "Реализовано с помощью сервиса «Яндекс.Словарь»",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionGroup(
    label: String,
    items: List<TranslationSuggestion>,
    onPick: (TranslationSuggestion) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEach { s ->
                val containerColor = when (s.source) {
                    SuggestionSource.USER_CARDS -> MaterialTheme.colorScheme.primaryContainer
                    SuggestionSource.ONLINE_DICTIONARY -> MaterialTheme.colorScheme.surface
                    SuggestionSource.DICTIONARY -> MaterialTheme.colorScheme.surface
                }
                val labelText = when (s.source) {
                    SuggestionSource.ONLINE_DICTIONARY -> "☁ ${s.text}"
                    else -> s.text
                }
                AssistChip(
                    onClick = { onPick(s) },
                    label = {
                        Text(
                            labelText,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(containerColor = containerColor)
                )
            }
        }
    }
}
