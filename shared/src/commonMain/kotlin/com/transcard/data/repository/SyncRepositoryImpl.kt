package com.transcard.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.transcard.data.remote.CardGroupSnapshotDto
import com.transcard.data.remote.CardSnapshotDto
import com.transcard.data.remote.SnapshotPayload
import com.transcard.data.remote.SpaceSnapshotDto
import com.transcard.data.remote.SproutApi
import com.transcard.data.remote.StudyResultSnapshotDto
import com.transcard.data.remote.dto.PushRequest
import com.transcard.data.remote.dto.SnapshotDto
import com.transcard.data.sync.platformClientInfo
import com.transcard.db.TransCardDatabase
import com.transcard.domain.repository.AuthRepository
import com.transcard.domain.repository.SnapshotHistoryEntry
import com.transcard.domain.repository.SyncRepository
import com.transcard.domain.sync.SyncStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
class SyncRepositoryImpl(
    private val db: TransCardDatabase,
    private val api: SproutApi,
    private val authRepository: AuthRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    scope: CoroutineScope? = null,
) : SyncRepository {

    private val internalScope: CoroutineScope =
        scope ?: CoroutineScope(SupervisorJob() + dispatcher)

    private val statusFlow = MutableStateFlow<SyncStatus>(SyncStatus.NotAuthenticated)
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    private var loopJob: Job? = null

    init {
        startAuthLoop()
    }

    override fun observeStatus(): Flow<SyncStatus> = statusFlow.asStateFlow()

    override fun markDirty() {
        db.syncStateQueries.markDirty()
    }

    override suspend fun pushNow(): Result<Unit> = runCatching { doPush(force = true) }

    override suspend fun pullOnStart(): Result<Unit> = runCatching {
        val auth = authRepository.observeAuthState().first()
        if (!auth.isAuthenticated) return@runCatching
        val state = db.syncStateQueries.selectState().executeAsOne()
        if (state.pendingPush == 1L) {
            val pushed = runCatching { doPush(force = true) }
            if (pushed.isFailure) return@runCatching
        }
        doPull()
    }

    override suspend fun fetchHistory(): Result<List<SnapshotHistoryEntry>> = runCatching {
        api.fetchHistory().map {
            SnapshotHistoryEntry(
                id = it.id,
                createdAt = it.createdAt,
                sizeBytes = it.sizeBytes,
                clientInfo = it.clientInfo,
            )
        }
    }

    override suspend fun restoreSnapshot(snapshotId: String): Result<Unit> = runCatching {
        statusFlow.value = SyncStatus.Pulling
        val envelope = api.restoreSnapshot(snapshotId)
        applySnapshotLocally(envelope.snapshot)
        statusFlow.value = SyncStatus.Idle
    }.onFailure { statusFlow.value = SyncStatus.error(it.message ?: "restore failed", recoverable = true) }

    private fun startAuthLoop() {
        internalScope.launch {
            authRepository.observeAuthState().collectLatest { state ->
                loopJob?.cancel()
                if (state.isAuthenticated) {
                    syncSessionToDb(state.userId, state.email)
                    statusFlow.value = SyncStatus.Idle
                    loopJob = launch { runPendingPushLoop() }
                } else {
                    db.syncStateQueries.clearSession()
                    statusFlow.value = SyncStatus.NotAuthenticated
                }
            }
        }
    }

    private fun syncSessionToDb(userId: String?, email: String?) {
        val current = db.syncStateQueries.selectState().executeAsOne()
        if (current.userId != userId) {
            db.syncStateQueries.setSession(userId, email)
        }
    }

    private suspend fun runPendingPushLoop() {
        db.syncStateQueries.selectState().asFlow().mapToOne(dispatcher)
            .map { it.pendingPush }
            .distinctUntilChanged()
            .filter { it == 1L }
            .debounce(DEBOUNCE.inWholeMilliseconds)
            .collect { runCatching { doPush(force = false) } }
    }

    private suspend fun doPush(force: Boolean) {
        val state = db.syncStateQueries.selectState().executeAsOne()
        if (state.userId == null) return
        if (!force && state.pendingPush == 0L) return

        statusFlow.value = SyncStatus.Pushing
        try {
            val payload = withContext(dispatcher) { buildSnapshotPayload() }
            val resp = api.pushSnapshot(
                PushRequest(
                    schemaVersion = LOCAL_SCHEMA_VERSION,
                    payload = json.encodeToJsonElement(SnapshotPayload.serializer(), payload),
                    clientInfo = platformClientInfo,
                )
            )
            db.syncStateQueries.markPushed(
                lastPushedAt = Clock.System.now().toEpochMilliseconds(),
                lastServerSnapshotAt = resp.createdAt,
            )
            statusFlow.value = SyncStatus.Idle
        } catch (e: Throwable) {
            statusFlow.value = SyncStatus.error(e.message ?: "push failed", recoverable = true)
            throw e
        }
    }

    private suspend fun doPull() {
        val state = db.syncStateQueries.selectState().executeAsOne()
        if (state.userId == null) return

        statusFlow.value = SyncStatus.Pulling
        try {
            val envelope = api.fetchLatestSnapshot(sinceTimestamp = state.lastServerSnapshotAt)
            if (envelope == null) {
                statusFlow.value = SyncStatus.Idle
                return
            }
            applySnapshotLocally(envelope.snapshot)
            statusFlow.value = SyncStatus.Idle
        } catch (e: Throwable) {
            statusFlow.value = SyncStatus.error(e.message ?: "pull failed", recoverable = true)
            throw e
        }
    }

    private fun buildSnapshotPayload(): SnapshotPayload {
        val spaces = db.spaceQueries.selectAll().executeAsList().map {
            SpaceSnapshotDto(it.id, it.name, it.nativeLang, it.targetLang, it.createdAt)
        }
        val cardGroups = db.cardGroupQueries.selectAll().executeAsList().map {
            CardGroupSnapshotDto(it.id, it.spaceId, it.name, it.createdAt)
        }
        val cards = db.cardQueries.selectAll().executeAsList().map {
            CardSnapshotDto(
                id = it.id,
                spaceId = it.spaceId,
                groupId = it.groupId,
                nativeWord = it.nativeWord,
                targetWord = it.targetWord,
                hint = it.hint,
                intervalDays = it.intervalDays,
                easiness = it.easiness,
                repetitions = it.repetitions.toInt(),
                nextReviewAt = it.nextReviewAt,
            )
        }
        val studyResults = db.studyResultQueries.selectAll().executeAsList().map {
            StudyResultSnapshotDto(it.id, it.cardId, it.correct == 1L, it.timestamp)
        }
        return SnapshotPayload(
            schemaVersion = LOCAL_SCHEMA_VERSION,
            exportedAt = Clock.System.now().toEpochMilliseconds(),
            spaces = spaces,
            cardGroups = cardGroups,
            cards = cards,
            studyResults = studyResults,
        )
    }

    private suspend fun applySnapshotLocally(snapshot: SnapshotDto) {
        val payload = json.decodeFromJsonElement(SnapshotPayload.serializer(), snapshot.payload)
        withContext(dispatcher) {
            db.transaction {
                db.studyResultQueries.deleteAll()
                db.cardQueries.deleteAll()
                db.cardGroupQueries.deleteAll()
                db.spaceQueries.deleteAll()

                payload.spaces.forEach { s ->
                    db.spaceQueries.insertWithId(s.id, s.name, s.nativeLang, s.targetLang, s.createdAt)
                }
                payload.cardGroups.forEach { g ->
                    db.cardGroupQueries.insertWithId(g.id, g.spaceId, g.name, g.createdAt)
                }
                payload.cards.forEach { c ->
                    db.cardQueries.insertWithId(
                        c.id, c.spaceId, c.groupId, c.nativeWord, c.targetWord, c.hint,
                        c.intervalDays, c.easiness, c.repetitions.toLong(), c.nextReviewAt,
                    )
                }
                payload.studyResults.forEach { r ->
                    db.studyResultQueries.insertWithId(r.id, r.cardId, if (r.correct) 1L else 0L, r.timestamp)
                }

                db.syncStateQueries.markPulled(
                    lastPulledAt = Clock.System.now().toEpochMilliseconds(),
                    lastServerSnapshotAt = snapshot.createdAt,
                )
            }
        }
    }

    private companion object {
        /** Версия локальной SQLDelight-схемы. Совпадает с TransCardDatabase. */
        const val LOCAL_SCHEMA_VERSION = 3
        val DEBOUNCE = 5.seconds
    }
}
