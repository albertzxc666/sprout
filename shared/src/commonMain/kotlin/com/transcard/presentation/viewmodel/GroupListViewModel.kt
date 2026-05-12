package com.transcard.presentation.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.transcard.domain.model.CardGroup
import com.transcard.domain.model.GardenStage
import com.transcard.domain.model.Space
import com.transcard.domain.repository.CardGroupRepository
import com.transcard.domain.repository.CardRepository
import com.transcard.domain.repository.SpaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class GroupCardItem(
    val group: CardGroup,
    val cardsCount: Int,
    val dueCount: Int,
    val stages: Map<GardenStage, Int>
)

data class GroupListUiState(
    val space: Space? = null,
    val items: List<GroupCardItem> = emptyList(),
    val totalDue: Int = 0,
    val isLoading: Boolean = true
)

class GroupListViewModel(
    private val spaceId: Long,
    private val spaceRepository: SpaceRepository,
    private val cardGroupRepository: CardGroupRepository,
    private val cardRepository: CardRepository
) : ScreenModel {

    private val _space = MutableStateFlow<Space?>(null)
    val space: StateFlow<Space?> = _space.asStateFlow()

    val state: StateFlow<GroupListUiState> = combine(
        cardGroupRepository.observeBySpace(spaceId),
        cardRepository.getCardCountsByGroup(spaceId),
        cardRepository.getDueCountsByGroup(spaceId, Clock.System.now().toEpochMilliseconds()),
        cardRepository.getGardenStagesByGroup(spaceId),
        _space
    ) { groups, counts, dueCounts, stages, sp ->
        val items = groups.map { g ->
            GroupCardItem(
                group = g,
                cardsCount = counts[g.id] ?: 0,
                dueCount = dueCounts[g.id] ?: 0,
                stages = stages[g.id].orEmpty()
            )
        }
        GroupListUiState(
            space = sp,
            items = items,
            totalDue = dueCounts.values.sum(),
            isLoading = false
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), GroupListUiState())

    init {
        screenModelScope.launch {
            _space.value = spaceRepository.getById(spaceId)
        }
    }

    fun createGroup(name: String) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        screenModelScope.launch {
            cardGroupRepository.create(spaceId, trimmed)
        }
    }

    fun renameGroup(id: Long, name: String) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        screenModelScope.launch {
            cardGroupRepository.rename(id, trimmed)
        }
    }

    fun deleteGroup(id: Long) {
        screenModelScope.launch {
            cardGroupRepository.delete(id)
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 50
    }
}
