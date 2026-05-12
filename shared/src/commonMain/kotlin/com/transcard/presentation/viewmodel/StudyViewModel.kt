package com.transcard.presentation.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.transcard.domain.model.Card
import com.transcard.domain.model.GardenStage
import com.transcard.domain.model.StudyDirection
import com.transcard.domain.model.StudyMode
import com.transcard.domain.model.StudyScope
import com.transcard.domain.repository.CardRepository
import com.transcard.domain.usecase.CheckAnswerUseCase
import com.transcard.domain.usecase.ReviewCardUseCase
import com.transcard.domain.usecase.Sm2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class StudyState(
    val cards: List<Card> = emptyList(),
    val currentIndex: Int = 0,
    val inputText: String = "",
    val checked: Boolean = false,
    val isCorrect: Boolean = false,
    val correctCount: Int = 0,
    val isFinished: Boolean = false,
    val isLoading: Boolean = true,
    val nothingDue: Boolean = false,
    val nextIntervalDays: Double? = null,
    val prevStage: GardenStage? = null,
    val nextStage: GardenStage? = null
) {
    val currentCard: Card? get() = cards.getOrNull(currentIndex)
    val total: Int get() = cards.size
    val progress: Float get() = if (total == 0) 0f else (currentIndex + 1).toFloat() / total
}

class StudyViewModel(
    val scope: StudyScope,
    val direction: StudyDirection,
    val mode: StudyMode,
    private val cardRepository: CardRepository,
    private val reviewCard: ReviewCardUseCase,
    private val checkAnswer: CheckAnswerUseCase
) : ScreenModel {

    private val _state = MutableStateFlow(StudyState())
    val state: StateFlow<StudyState> = _state.asStateFlow()

    init {
        loadCards()
    }

    private fun loadCards() {
        screenModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            val cards = when (mode) {
                StudyMode.SCHEDULED -> when (val s = scope) {
                    is StudyScope.Space -> cardRepository.getDueCardsBySpace(s.spaceId, now).first()
                    is StudyScope.Group -> cardRepository.getDueCardsByGroup(s.groupId, now).first()
                }
                StudyMode.DRILL -> when (val s = scope) {
                    is StudyScope.Space -> cardRepository.getCardsBySpace(s.spaceId).first()
                    is StudyScope.Group -> cardRepository.getCardsByGroup(s.groupId).first()
                }
            }.shuffled()
            val total = when (val s = scope) {
                is StudyScope.Space -> cardRepository.countBySpace(s.spaceId).first()
                is StudyScope.Group -> cardRepository.countByGroup(s.groupId).first()
            }
            _state.value = StudyState(
                cards = cards,
                isLoading = false,
                isFinished = cards.isEmpty(),
                nothingDue = mode == StudyMode.SCHEDULED && cards.isEmpty() && total > 0
            )
        }
    }

    fun onInputChanged(text: String) {
        if (_state.value.checked) return
        _state.value = _state.value.copy(inputText = text)
    }

    fun checkAnswer() {
        val s = _state.value
        val card = s.currentCard ?: return
        if (s.checked) return
        val correct = checkAnswer(card, s.inputText, direction)
        val isScheduled = mode == StudyMode.SCHEDULED
        val (intervalDays, prevStage, nextStage) = if (isScheduled) {
            val now = Clock.System.now().toEpochMilliseconds()
            val preview = Sm2.review(card, correct, now)
            Triple(
                preview.intervalDays,
                GardenStage.fromReps(card.repetitions),
                GardenStage.fromReps(preview.repetitions)
            )
        } else {
            Triple<Double?, GardenStage?, GardenStage?>(null, null, null)
        }
        _state.value = s.copy(
            checked = true,
            isCorrect = correct,
            correctCount = s.correctCount + if (correct) 1 else 0,
            nextIntervalDays = intervalDays,
            prevStage = prevStage,
            nextStage = nextStage
        )
        screenModelScope.launch {
            reviewCard(card, correct, updateSrs = isScheduled)
        }
    }

    fun nextCard() {
        val s = _state.value
        if (!s.checked) return
        val nextIndex = s.currentIndex + 1
        if (nextIndex >= s.cards.size) {
            _state.value = s.copy(isFinished = true)
        } else {
            _state.value = s.copy(
                currentIndex = nextIndex,
                inputText = "",
                checked = false,
                isCorrect = false,
                nextIntervalDays = null,
                prevStage = null,
                nextStage = null
            )
        }
    }

    fun restart() {
        loadCards()
    }
}
