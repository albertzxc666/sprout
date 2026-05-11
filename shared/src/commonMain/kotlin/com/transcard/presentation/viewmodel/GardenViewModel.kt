package com.transcard.presentation.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.transcard.domain.model.Card
import com.transcard.domain.model.GardenStage
import com.transcard.domain.model.Space
import com.transcard.domain.repository.CardRepository
import com.transcard.domain.repository.SpaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class GardenUiState(
    val space: Space? = null,
    val totalCards: Int = 0,
    val byStage: Map<GardenStage, List<Card>> = emptyMap()
)

class GardenViewModel(
    private val spaceId: Long,
    private val spaceRepository: SpaceRepository,
    private val cardRepository: CardRepository
) : ScreenModel {

    val state: StateFlow<GardenUiState> = cardRepository.getCardsBySpace(spaceId)
        .map { cards ->
            val grouped = cards.groupBy { GardenStage.fromReps(it.repetitions) }
            GardenUiState(
                space = spaceRepository.getById(spaceId),
                totalCards = cards.size,
                byStage = GardenStage.values().associateWith { stage ->
                    (grouped[stage] ?: emptyList()).sortedByDescending { it.repetitions }
                }
            )
        }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), GardenUiState())
}
