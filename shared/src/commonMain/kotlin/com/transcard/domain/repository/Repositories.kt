package com.transcard.domain.repository

import com.transcard.domain.model.Card
import com.transcard.domain.model.CardGroup
import com.transcard.domain.model.GardenStage
import com.transcard.domain.model.Space
import com.transcard.domain.model.StudyResult
import com.transcard.domain.model.StudyResultWithSpace
import com.transcard.domain.model.TranslationSuggestion
import com.transcard.domain.sync.AuthState
import kotlinx.coroutines.flow.Flow

interface SpaceRepository {
    fun getAllSpaces(): Flow<List<Space>>
    suspend fun getById(id: Long): Space?
    suspend fun createSpace(name: String, nativeLang: String, targetLang: String): Long
    suspend fun deleteSpace(id: Long)
}

interface CardGroupRepository {
    fun observeBySpace(spaceId: Long): Flow<List<CardGroup>>
    suspend fun getById(id: Long): CardGroup?
    suspend fun create(spaceId: Long, name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun delete(id: Long)
}

interface CardRepository {
    fun getCardsBySpace(spaceId: Long): Flow<List<Card>>
    fun getCardsByGroup(groupId: Long): Flow<List<Card>>
    fun getDueCardsBySpace(spaceId: Long, now: Long): Flow<List<Card>>
    fun getDueCardsByGroup(groupId: Long, now: Long): Flow<List<Card>>
    fun countBySpace(spaceId: Long): Flow<Int>
    fun countByGroup(groupId: Long): Flow<Int>
    fun getCardCountsBySpace(): Flow<Map<Long, Int>>
    fun getDueCountsBySpace(now: Long): Flow<Map<Long, Int>>
    fun getCardCountsByGroup(spaceId: Long): Flow<Map<Long, Int>>
    fun getDueCountsByGroup(spaceId: Long, now: Long): Flow<Map<Long, Int>>
    fun getGardenStagesBySpace(): Flow<Map<Long, Map<GardenStage, Int>>>
    fun getGardenStagesByGroup(spaceId: Long): Flow<Map<Long, Map<GardenStage, Int>>>
    suspend fun createCard(spaceId: Long, groupId: Long, nativeWord: String, targetWord: String, hint: String?)
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

interface AuthRepository {
    /** Текущее состояние аккаунта. Hot Flow — обновляется при login/logout. */
    fun observeAuthState(): Flow<AuthState>

    /** 201 — успех. Кидает IllegalStateException при email_taken / invalid_body / сетевой ошибке. */
    suspend fun register(email: String, password: String)

    /** 200 — успех. Кидает IllegalStateException при invalid_credentials / сетевой ошибке. */
    suspend fun login(email: String, password: String)

    /** Отзывает refresh на сервере и чистит local storage. */
    suspend fun logout()
}

