package com.transcard.domain.repository

import com.transcard.domain.model.Card
import com.transcard.domain.model.GardenStage
import com.transcard.domain.model.Space
import com.transcard.domain.model.StudyResult
import com.transcard.domain.model.StudyResultWithSpace
import com.transcard.domain.model.TranslationSuggestion
import kotlinx.coroutines.flow.Flow

interface SpaceRepository {
    fun getAllSpaces(): Flow<List<Space>>
    suspend fun getById(id: Long): Space?
    suspend fun createSpace(name: String, nativeLang: String, targetLang: String): Long
    suspend fun deleteSpace(id: Long)
}

interface CardRepository {
    fun getCardsBySpace(spaceId: Long): Flow<List<Card>>
    fun getDueCardsBySpace(spaceId: Long, now: Long): Flow<List<Card>>
    fun countBySpace(spaceId: Long): Flow<Int>
    fun getCardCountsBySpace(): Flow<Map<Long, Int>>
    fun getDueCountsBySpace(now: Long): Flow<Map<Long, Int>>
    fun getGardenStagesBySpace(): Flow<Map<Long, Map<GardenStage, Int>>>
    suspend fun createCard(spaceId: Long, nativeWord: String, targetWord: String, hint: String?)
    suspend fun updateCard(card: Card)
    suspend fun updateSrs(cardId: Long, intervalDays: Double, easiness: Double, repetitions: Int, nextReviewAt: Long)
    suspend fun deleteCard(id: Long)
}

interface ProgressRepository {
    fun getResultsBySpace(spaceId: Long): Flow<List<StudyResult>>
    fun getAllResults(): Flow<List<StudyResultWithSpace>>
    suspend fun saveResult(cardId: Long, correct: Boolean)
}

interface TranslationRepository {
    suspend fun search(query: String, from: String, to: String, limit: Int = 8): List<TranslationSuggestion>
}
