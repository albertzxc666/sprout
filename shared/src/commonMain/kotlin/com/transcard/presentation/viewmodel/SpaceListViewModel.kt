package com.transcard.presentation.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.transcard.domain.model.GardenStage
import com.transcard.domain.model.Space
import com.transcard.domain.model.StudyResultWithSpace
import com.transcard.domain.repository.CardRepository
import com.transcard.domain.repository.ProgressRepository
import com.transcard.domain.repository.SpaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

data class SpaceCardItem(
    val space: Space,
    val totalCards: Int,
    val studiedCards: Int,
    val dueCount: Int,
    val correctRatio: Float?,
    val lastStudiedAt: Long?,
    val stages: Map<GardenStage, Int>
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val totalWords: Int = 0,
    val streakDays: Int = 0,
    val accuracy: Float? = null,
    val dueToday: Int = 0,
    val spaces: List<SpaceCardItem> = emptyList()
)

class SpaceListViewModel(
    private val spaceRepository: SpaceRepository,
    private val cardRepository: CardRepository,
    private val progressRepository: ProgressRepository
) : ScreenModel {

    val spaces: StateFlow<List<Space>> = spaceRepository.getAllSpaces()
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val homeState: StateFlow<HomeUiState> = combine(
        spaceRepository.getAllSpaces(),
        cardRepository.getCardCountsBySpace(),
        cardRepository.getDueCountsBySpace(Clock.System.now().toEpochMilliseconds()),
        cardRepository.getGardenStagesBySpace(),
        progressRepository.getAllResults()
    ) { spaces, counts, dueCounts, stagesBySpace, results ->
        val resultsBySpace = results.groupBy { it.spaceId }
        val items = spaces.map { space ->
            val spaceResults = resultsBySpace[space.id].orEmpty()
            SpaceCardItem(
                space = space,
                totalCards = counts[space.id] ?: 0,
                studiedCards = spaceResults.map { it.cardId }.distinct().size,
                dueCount = dueCounts[space.id] ?: 0,
                correctRatio = spaceResults.takeIf { it.isNotEmpty() }
                    ?.let { it.count { r -> r.correct }.toFloat() / it.size },
                lastStudiedAt = spaceResults.maxOfOrNull { it.timestamp },
                stages = stagesBySpace[space.id].orEmpty()
            )
        }
        HomeUiState(
            isLoading = false,
            totalWords = counts.values.sum(),
            streakDays = computeStreak(results),
            accuracy = if (results.isEmpty()) null
                else results.count { it.correct }.toFloat() / results.size,
            dueToday = dueCounts.values.sum(),
            spaces = items
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun createSpace(name: String, nativeLang: String, targetLang: String) {
        if (name.isBlank() || nativeLang.isBlank() || targetLang.isBlank()) return
        screenModelScope.launch {
            spaceRepository.createSpace(name.trim(), nativeLang, targetLang)
        }
    }

    fun deleteSpace(id: Long) {
        screenModelScope.launch {
            spaceRepository.deleteSpace(id)
        }
    }

    private fun computeStreak(results: List<StudyResultWithSpace>): Int {
        if (results.isEmpty()) return 0
        val tz = TimeZone.currentSystemDefault()
        val activeDays: Set<LocalDate> = results.asSequence()
            .map { Instant.fromEpochMilliseconds(it.timestamp).toLocalDateTime(tz).date }
            .toSet()

        val today = Clock.System.now().toLocalDateTime(tz).date
        var anchor = if (today in activeDays) today else today.minus(1, DateTimeUnit.DAY)
        if (anchor !in activeDays) return 0

        var streak = 0
        while (anchor in activeDays) {
            streak++
            anchor = anchor.minus(1, DateTimeUnit.DAY)
        }
        return streak
    }
}
