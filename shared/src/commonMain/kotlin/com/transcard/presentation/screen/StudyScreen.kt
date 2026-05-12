package com.transcard.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.transcard.data.preferences.PrefKeys
import com.transcard.data.preferences.Preferences
import com.transcard.domain.model.GardenStage
import com.transcard.domain.model.StudyDirection
import com.transcard.domain.model.StudyMode
import com.transcard.domain.model.StudyScope
import com.transcard.presentation.components.AppCard
import com.transcard.presentation.util.humanizeIntervalRu
import com.transcard.presentation.viewmodel.StudyViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
private fun SrsTooltipBanner(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("💡", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Так работает повторение",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Правильно ответили — слово вернётся через несколько дней. Так оно лучше запомнится надолго.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Закрыть",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SrsHint(days: Double, prevStage: GardenStage?, nextStage: GardenStage?) {
    val grew = prevStage != null && nextStage != null && prevStage != nextStage
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (grew) {
            Text(prevStage!!.emoji, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.size(4.dp))
            Text(
                "→",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.size(4.dp))
            Text(nextStage!!.emoji, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.size(8.dp))
        } else if (nextStage != null) {
            Text(nextStage.emoji, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.size(8.dp))
        }
        Text(
            humanizeIntervalRu(days) + " снова",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

data class StudyScreen(
    val scope: StudyScope,
    val direction: StudyDirection,
    val mode: StudyMode
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm: StudyViewModel = koinScreenModel { parametersOf(scope, direction, mode) }
        val navigator = LocalNavigator.currentOrThrow
        val state by vm.state.collectAsState()

        val prefs: Preferences = koinInject()
        val tooltipPermanentlyDismissed = remember {
            prefs.getBoolean(PrefKeys.SRS_TOOLTIP_SEEN, false)
        }
        var tooltipVisible by remember { mutableStateOf(false) }
        LaunchedEffect(state.checked) {
            if (state.checked && !tooltipPermanentlyDismissed && mode == StudyMode.SCHEDULED) {
                tooltipVisible = true
            }
        }

        LaunchedEffect(state.isFinished) {
            if (state.isFinished && state.cards.isNotEmpty()) {
                navigator.replace(
                    StudyResultScreen(
                        scope = scope,
                        direction = direction,
                        mode = mode,
                        correct = state.correctCount,
                        total = state.total
                    )
                )
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (mode) {
                                StudyMode.SCHEDULED -> "Расписание"
                                StudyMode.DRILL -> "Тренажёр"
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                        .height(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${state.currentIndex + 1} / ${state.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedVisibility(
                    visible = tooltipVisible,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(180))
                ) {
                    SrsTooltipBanner(onDismiss = {
                        tooltipVisible = false
                        prefs.setBoolean(PrefKeys.SRS_TOOLTIP_SEEN, true)
                    })
                }

                Spacer(Modifier.height(24.dp))

                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Загрузка…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    return@Column
                }

                if (state.cards.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (state.nothingDue) "🌱" else "📭",
                                style = MaterialTheme.typography.displayLarge
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (state.nothingDue) "Всё на сегодня повторено"
                                else "В этом пространстве пока нет карточек",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                            if (state.nothingDue) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Возвращайтесь позже — слова всплывут, когда придёт время повторить",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    }
                    return@Column
                }

                val card = state.currentCard ?: return@Column
                val sourceWord = when (direction) {
                    StudyDirection.NATIVE_TO_TARGET -> card.nativeWord
                    StudyDirection.TARGET_TO_NATIVE -> card.targetWord
                }
                val expectedWord = when (direction) {
                    StudyDirection.NATIVE_TO_TARGET -> card.targetWord
                    StudyDirection.TARGET_TO_NATIVE -> card.nativeWord
                }

                AnimatedContent(
                    targetState = state.currentIndex,
                    transitionSpec = {
                        val enter = slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { it / 4 } + fadeIn(tween(220))
                        val exit = slideOutHorizontally(
                            animationSpec = tween(220)
                        ) { -it / 4 } + fadeOut(tween(180))
                        enter togetherWith exit
                    },
                    label = "card-content"
                ) { _ ->
                    val borderColor = when {
                        !state.checked -> null
                        state.isCorrect -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .let {
                                if (borderColor != null) {
                                    it.border(
                                        BorderStroke(1.5.dp, borderColor),
                                        MaterialTheme.shapes.large
                                    )
                                } else it
                            }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                sourceWord,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center
                            )
                            card.hint?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "💡 $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedVisibility(
                                visible = state.checked,
                                enter = fadeIn(tween(200)) + scaleIn(
                                    initialScale = 0.85f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ),
                                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.95f)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(Modifier.height(16.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (state.isCorrect) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = null,
                                            tint = if (state.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.size(6.dp))
                                        Text(
                                            text = if (state.isCorrect) "Верно!" else "Правильный ответ:",
                                            color = if (state.isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    if (!state.isCorrect) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            expectedWord,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    state.nextIntervalDays?.let { days ->
                                        Spacer(Modifier.height(14.dp))
                                        SrsHint(
                                            days = days,
                                            prevStage = state.prevStage,
                                            nextStage = state.nextStage
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = vm::onInputChanged,
                    label = { Text("Ваш перевод") },
                    singleLine = true,
                    enabled = !state.checked,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = {
                        if (state.checked) vm.nextCard() else vm.checkAnswer()
                    },
                    enabled = state.checked || state.inputText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Text(
                        if (state.checked) "Следующая" else "Проверить",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
