package com.transcard.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.transcard.domain.model.GardenStage
import com.transcard.domain.model.LanguagePair
import com.transcard.domain.model.Space
import com.transcard.domain.model.StudyScope
import com.transcard.presentation.components.AppCard
import com.transcard.presentation.components.EmptyState
import com.transcard.presentation.components.pressable
import com.transcard.presentation.util.languageFlag
import com.transcard.presentation.util.pluralRu
import com.transcard.presentation.util.relativeTimeRu
import com.transcard.presentation.viewmodel.HomeUiState
import com.transcard.presentation.viewmodel.SpaceCardItem
import com.transcard.presentation.viewmodel.SpaceListViewModel
import kotlin.math.roundToInt

object SpaceListScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm: SpaceListViewModel = koinScreenModel()
        val navigator = LocalNavigator.currentOrThrow
        val home by vm.homeState.collectAsState()
        var showCreate by remember { mutableStateOf(false) }
        var spaceToDelete by remember { mutableStateOf<Space?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Мои пространства") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Создать") },
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
            if (home.spaces.isEmpty() && !home.isLoading) {
                Box(Modifier.padding(padding)) {
                    EmptyState(
                        title = "Создайте первое пространство",
                        subtitle = "Пространство — это коллекция карточек для одного языка. Например: «Французский» с парой RU → FR.",
                        icon = Icons.Outlined.Style
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item("hero") {
                        HeroStats(home)
                    }
                    if (home.dueToday > 0) {
                        item("review-banner") {
                            ReviewBanner(home.dueToday)
                        }
                    }
                    items(home.spaces, key = { it.space.id }) { item ->
                        SpaceItem(
                            item = item,
                            onOpen = { navigator.push(GroupListScreen(item.space.id)) },
                            onStudy = { navigator.push(StudySetupScreen(StudyScope.Space(item.space.id))) },
                            onGarden = { navigator.push(GardenScreen(item.space.id)) },
                            onDelete = { spaceToDelete = item.space }
                        )
                    }
                }
            }
        }

        if (showCreate) {
            CreateSpaceDialog(
                onDismiss = { showCreate = false },
                onConfirm = { name, native, target ->
                    vm.createSpace(name, native, target)
                    showCreate = false
                }
            )
        }

        spaceToDelete?.let { sp ->
            AlertDialog(
                onDismissRequest = { spaceToDelete = null },
                title = { Text("Удалить пространство?") },
                text = { Text("«${sp.name}» и все его карточки будут удалены.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteSpace(sp.id)
                        spaceToDelete = null
                    }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { spaceToDelete = null }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
private fun HeroStats(state: HomeUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatColumn(
            value = state.totalWords.toString(),
            label = pluralRu(state.totalWords, "слово", "слова", "слов"),
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        StatColumn(
            value = state.streakDays.toString(),
            label = if (state.streakDays > 0) "🔥 " + pluralRu(state.streakDays, "день", "дня", "дней")
                    else pluralRu(state.streakDays, "день", "дня", "дней"),
            modifier = Modifier.weight(1f)
        )
        StatDivider()
        StatColumn(
            value = state.accuracy?.let { "${(it * 100).roundToInt()}%" } ?: "—",
            label = "точность",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(36.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun ReviewBanner(due: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📖", style = MaterialTheme.typography.titleLarge)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                "Пора повторить",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "$due ${pluralRu(due, "слово ждёт", "слова ждут", "слов ждут")} вашего внимания",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SpaceItem(
    item: SpaceCardItem,
    onOpen: () -> Unit,
    onStudy: () -> Unit,
    onGarden: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = if (item.totalCards == 0) 0f
                   else item.studiedCards.toFloat() / item.totalCards
    val percent = (progress * 100).roundToInt()

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LanguagePill(item.space.nativeLang, item.space.targetLang)
                Box(Modifier.weight(1f))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                item.space.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    "${item.totalCards} ${pluralRu(item.totalCards, "слово", "слова", "слов")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.dueCount > 0) {
                    Text(
                        "  ·  ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        " ${item.dueCount} ${pluralRu(item.dueCount, "на повторение", "на повторение", "на повторение")}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (item.lastStudiedAt != null) {
                    Text(
                        "  ·  ${relativeTimeRu(item.lastStudiedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.totalCards > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                    FilledTonalIconButton(
                        onClick = onStudy,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Учить",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (item.totalCards > 0) {
                GardenStrip(
                    stages = item.stages,
                    onClick = onGarden,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun GardenStrip(
    stages: Map<GardenStage, Int>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.background)
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GardenStage.values().forEachIndexed { i, stage ->
                if (i > 0) {
                    Text(
                        "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
                Text(stage.emoji, style = MaterialTheme.typography.bodyLarge)
                Text(
                    " ${stages[stage] ?: 0}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "Сад →",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun LanguagePill(nativeLang: String, targetLang: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            languageFlag(nativeLang),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "  ${nativeLang.uppercase()}  →  ${targetLang.uppercase()}  ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            languageFlag(targetLang),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSpaceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, nativeLang: String, targetLang: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var native by remember { mutableStateOf(LanguagePair.DEFAULT.first()) }
    var target by remember { mutableStateOf(LanguagePair.DEFAULT[1]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое пространство") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
                LanguageDropdown(
                    label = "Родной язык",
                    selected = native,
                    onSelect = { native = it }
                )
                LanguageDropdown(
                    label = "Изучаемый язык",
                    selected = target,
                    onSelect = { target = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, native.code, target.code) },
                enabled = name.isNotBlank() && native.code != target.code,
                shape = MaterialTheme.shapes.medium
            ) { Text("Создать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
    label: String,
    selected: LanguagePair,
    onSelect: (LanguagePair) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = "${selected.nativeLabel} (${selected.code.uppercase()})",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(
                    type = MenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            LanguagePair.DEFAULT.forEach { lp ->
                DropdownMenuItem(
                    text = { Text("${lp.nativeLabel} (${lp.code.uppercase()})") },
                    onClick = {
                        onSelect(lp)
                        expanded = false
                    }
                )
            }
        }
    }
}
