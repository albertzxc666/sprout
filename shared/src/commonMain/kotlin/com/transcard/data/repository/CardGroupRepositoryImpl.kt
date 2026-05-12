package com.transcard.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.transcard.data.sync.SyncTrigger
import com.transcard.db.TransCardDatabase
import com.transcard.domain.model.CardGroup
import com.transcard.domain.repository.CardGroupRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class CardGroupRepositoryImpl(
    private val db: TransCardDatabase,
    private val syncTrigger: SyncTrigger,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : CardGroupRepository {

    override fun observeBySpace(spaceId: Long): Flow<List<CardGroup>> =
        db.cardGroupQueries.selectBySpace(spaceId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: Long): CardGroup? = withContext(dispatcher) {
        db.cardGroupQueries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun create(spaceId: Long, name: String): Long = withContext(dispatcher) {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = db.transactionWithResult {
            db.cardGroupQueries.insert(spaceId, name, now)
            db.cardGroupQueries.lastInsertedId().executeAsOne()
        }
        syncTrigger.markDirty()
        id
    }

    override suspend fun rename(id: Long, name: String) {
        withContext(dispatcher) {
            db.cardGroupQueries.update(name, id)
            syncTrigger.markDirty()
        }
    }

    override suspend fun delete(id: Long) {
        withContext(dispatcher) {
            db.cardGroupQueries.deleteById(id)
            syncTrigger.markDirty()
        }
    }

    private fun com.transcard.db.CardGroup.toDomain() = CardGroup(
        id = id,
        spaceId = spaceId,
        name = name,
        createdAt = createdAt
    )
}
