package com.transcard.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.transcard.domain.model.StudyDirection
import com.transcard.domain.model.StudyMode
import com.transcard.presentation.components.AppCard

data class StudySetupScreen(val spaceId: Long) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var direction by remember { mutableStateOf(StudyDirection.NATIVE_TO_TARGET) }
        var mode by remember { mutableStateOf(StudyMode.SCHEDULED) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Настройка сессии") },
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
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Режим",
                    style = MaterialTheme.typography.titleMedium
                )
                AppCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.selectableGroup().padding(8.dp)) {
                        DirectionOption(
                            title = "🌱 Расписание",
                            subtitle = "Только слова, которые пора повторить. Сад растёт, серия дней идёт.",
                            selected = mode == StudyMode.SCHEDULED,
                            onSelect = { mode = StudyMode.SCHEDULED }
                        )
                        DirectionOption(
                            title = "🔁 Тренажёр",
                            subtitle = "Все слова в случайном порядке. Без интервалов — для быстрой прокачки.",
                            selected = mode == StudyMode.DRILL,
                            onSelect = { mode = StudyMode.DRILL }
                        )
                    }
                }

                Text(
                    "Направление перевода",
                    style = MaterialTheme.typography.titleMedium
                )
                AppCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.selectableGroup().padding(8.dp)) {
                        DirectionOption(
                            title = "Родной → Изучаемый",
                            subtitle = "Видите слово на родном языке, вводите перевод",
                            selected = direction == StudyDirection.NATIVE_TO_TARGET,
                            onSelect = { direction = StudyDirection.NATIVE_TO_TARGET }
                        )
                        DirectionOption(
                            title = "Изучаемый → Родной",
                            subtitle = "Видите слово на изучаемом языке, вводите перевод",
                            selected = direction == StudyDirection.TARGET_TO_NATIVE,
                            onSelect = { direction = StudyDirection.TARGET_TO_NATIVE }
                        )
                    }
                }

                Button(
                    onClick = {
                        navigator.replace(StudyScreen(spaceId, direction, mode))
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = MaterialTheme.shapes.medium,
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Text("Начать", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun DirectionOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
