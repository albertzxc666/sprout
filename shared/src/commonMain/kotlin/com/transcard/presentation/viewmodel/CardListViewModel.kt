package com.transcard.presentation.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.transcard.domain.model.Card
import com.transcard.domain.model.Space
import com.transcard.domain.model.SuggestionSource
import com.transcard.domain.model.TranslationSuggestion
import com.transcard.domain.repository.CardRepository
import com.transcard.domain.repository.SpaceRepository
import com.transcard.domain.repository.TranslationRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SuggestionsState(
    val query: String = "",
    val fromCards: List<TranslationSuggestion> = emptyList(),
    val fromDictionary: List<TranslationSuggestion> = emptyList()
)

class CardListViewModel(
    private val spaceId: Long,
    private val spaceRepository: SpaceRepository,
    private val cardRepository: CardRepository,
    private val translationRepository: TranslationRepository
) : ScreenModel {

    val cards: StateFlow<List<Card>> = cardRepository.getCardsBySpace(spaceId)
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _space = MutableStateFlow<Space?>(null)
    val space: StateFlow<Space?> = _space.asStateFlow()

    private val nativeQuery = MutableStateFlow("")
    private val _suggestions = MutableStateFlow(SuggestionsState())
    val suggestions: StateFlow<SuggestionsState> = _suggestions.asStateFlow()

    init {
        screenModelScope.launch {
            _space.value = spaceRepository.getById(spaceId)
        }
        observeQuery()
    }

    @OptIn(FlowPreview::class)
    private fun observeQuery() {
        screenModelScope.launch {
            nativeQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { raw ->
                    val q = raw.trim()
                    if (q.isEmpty()) {
                        _suggestions.value = SuggestionsState()
                        return@collect
                    }
                    val sp = _space.value ?: return@collect

                    val fromCards = matchCards(q, cards.value)
                    val cardWords = fromCards.map { it.text.lowercase() }.toSet()
                    val fromDict = translationRepository.search(q, sp.nativeLang, sp.targetLang)
                        .filter { it.text.lowercase() !in cardWords }

                    _suggestions.value = SuggestionsState(
                        query = q,
                        fromCards = fromCards,
                        fromDictionary = fromDict
                    )
                }
        }
    }

    private fun matchCards(query: String, cards: List<Card>): List<TranslationSuggestion> {
        val needle = query.lowercase()
        val seen = linkedSetOf<String>()
        for (c in cards) {
            if (c.nativeWord.lowercase().startsWith(needle)) {
                seen += c.targetWord
            }
            if (seen.size >= MAX_CARD_SUGGESTIONS) break
        }
        return seen.map { TranslationSuggestion(it, SuggestionSource.USER_CARDS) }
    }

    fun onNativeWordChanged(text: String) {
        nativeQuery.value = text
    }

    fun clearSuggestions() {
        nativeQuery.value = ""
        _suggestions.value = SuggestionsState()
    }

    fun addCard(nativeWord: String, targetWord: String, hint: String?) {
        if (nativeWord.isBlank() || targetWord.isBlank()) return
        screenModelScope.launch {
            cardRepository.createCard(
                spaceId = spaceId,
                nativeWord = nativeWord.trim(),
                targetWord = targetWord.trim(),
                hint = hint?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
    }

    fun updateCard(card: Card) {
        screenModelScope.launch {
            cardRepository.updateCard(card)
        }
    }

    fun deleteCard(id: Long) {
        screenModelScope.launch {
            cardRepository.deleteCard(id)
        }
    }

    private companion object {
        const val MAX_CARD_SUGGESTIONS = 5
    }
}
