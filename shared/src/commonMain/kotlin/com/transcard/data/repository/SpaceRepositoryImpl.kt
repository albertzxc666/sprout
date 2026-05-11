package com.transcard.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.transcard.db.TransCardDatabase
import com.transcard.domain.model.Space
import com.transcard.domain.repository.SpaceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class SpaceRepositoryImpl(
    private val db: TransCardDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : SpaceRepository {

    override fun getAllSpaces(): Flow<List<Space>> =
        db.spaceQueries.selectAll().asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: Long): Space? = withContext(dispatcher) {
        db.spaceQueries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun createSpace(name: String, nativeLang: String, targetLang: String): Long =
        withContext(dispatcher) {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transactionWithResult {
                db.spaceQueries.insert(name, nativeLang, targetLang, now)
                db.spaceQueries.lastInsertedId().executeAsOne()
            }
        }

    override suspend fun deleteSpace(id: Long) {
        withContext(dispatcher) {
            db.spaceQueries.deleteById(id)
        }
    }

    private fun com.transcard.db.Space.toDomain() = Space(
        id = id,
        name = name,
        nativeLang = nativeLang,
        targetLang = targetLang,
        createdAt = createdAt
    )
}
