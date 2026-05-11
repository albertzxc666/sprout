package com.transcard.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.transcard.db.TransCardDatabase
import com.transcard.domain.model.Card
import com.transcard.domain.model.GardenStage
import com.transcard.domain.repository.CardRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CardRepositoryImpl(
    private val db: TransCardDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : CardRepository {

    override fun getCardsBySpace(spaceId: Long): Flow<List<Card>> =
        db.cardQueries.selectBySpace(spaceId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun getDueCardsBySpace(spaceId: Long, now: Long): Flow<List<Card>> =
        db.cardQueries.selectDueBySpace(spaceId, now).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun countBySpace(spaceId: Long): Flow<Int> =
        db.cardQueries.countBySpace(spaceId).asFlow().mapToOne(dispatcher).map { it.toInt() }

    override fun getCardCountsBySpace(): Flow<Map<Long, Int>> =
        db.cardQueries.countAllBySpace().asFlow().mapToList(dispatcher)
            .map { rows -> rows.associate { it.spaceId to it.cnt.toInt() } }

    override fun getDueCountsBySpace(now: Long): Flow<Map<Long, Int>> =
        db.cardQueries.countDueAllBySpace(now).asFlow().mapToList(dispatcher)
            .map { rows -> rows.associate { it.spaceId to it.cnt.toInt() } }

    override fun getGardenStagesBySpace(): Flow<Map<Long, Map<GardenStage, Int>>> {
        val stages = GardenStage.values()
        return db.cardQueries.selectGardenStages().asFlow().mapToList(dispatcher)
            .map { rows ->
                rows.groupBy { it.spaceId }
                    .mapValues { (_, spaceRows) ->
                        spaceRows.associate { stages[it.stage.toInt()] to it.cnt.toInt() }
                    }
            }
    }

    override suspend fun createCard(
        spaceId: Long,
        nativeWord: String,
        targetWord: String,
        hint: String?
    ) {
        withContext(dispatcher) {
            db.cardQueries.insert(spaceId, nativeWord, targetWord, hint)
        }
    }

    override suspend fun updateCard(card: Card) {
        withContext(dispatcher) {
            db.cardQueries.update(card.nativeWord, card.targetWord, card.hint, card.id)
        }
    }

    override suspend fun updateSrs(
        cardId: Long,
        intervalDays: Double,
        easiness: Double,
        repetitions: Int,
        nextReviewAt: Long
    ) {
        withContext(dispatcher) {
            db.cardQueries.updateSrs(intervalDays, easiness, repetitions.toLong(), nextReviewAt, cardId)
        }
    }

    override suspend fun deleteCard(id: Long) {
        withContext(dispatcher) {
            db.cardQueries.deleteById(id)
        }
    }

    private fun com.transcard.db.Card.toDomain() = Card(
        id = id,
        spaceId = spaceId,
        nativeWord = nativeWord,
        targetWord = targetWord,
        hint = hint,
        intervalDays = intervalDays,
        easiness = easiness,
        repetitions = repetitions.toInt(),
        nextReviewAt = nextReviewAt
    )
}
