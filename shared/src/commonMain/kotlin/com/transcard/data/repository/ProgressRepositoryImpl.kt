package com.transcard.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.transcard.db.TransCardDatabase
import com.transcard.domain.model.StudyResult
import com.transcard.domain.model.StudyResultWithSpace
import com.transcard.domain.repository.ProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class ProgressRepositoryImpl(
    private val db: TransCardDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ProgressRepository {

    override fun getResultsBySpace(spaceId: Long): Flow<List<StudyResult>> =
        db.studyResultQueries.selectBySpace(spaceId).asFlow().mapToList(dispatcher)
            .map { rows ->
                rows.map {
                    StudyResult(
                        cardId = it.cardId,
                        correct = it.correct == 1L,
                        timestamp = it.timestamp
                    )
                }
            }

    override fun getAllResults(): Flow<List<StudyResultWithSpace>> =
        db.studyResultQueries.selectAllWithSpace().asFlow().mapToList(dispatcher)
            .map { rows ->
                rows.map {
                    StudyResultWithSpace(
                        cardId = it.cardId,
                        spaceId = it.spaceId,
                        correct = it.correct == 1L,
                        timestamp = it.timestamp
                    )
                }
            }

    override suspend fun saveResult(cardId: Long, correct: Boolean) {
        withContext(dispatcher) {
            val now = Clock.System.now().toEpochMilliseconds()
            db.studyResultQueries.insert(cardId, if (correct) 1 else 0, now)
        }
    }
}
