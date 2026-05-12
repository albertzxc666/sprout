# Card Groups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить плоскую группировку карточек внутри каждого пространства; учёба запускается и на уровне пространства, и на уровне отдельной группы. Offline-only, без аккаунтов.

**Architecture:** Новая таблица `CardGroup` (FK → Space, ON DELETE CASCADE); в `Card` появляется `groupId NOT NULL` (FK → CardGroup, ON DELETE CASCADE). При создании пространства транзакционно создаётся одна группа `«Общее»`. Существующие данные мигрируют через пересоздание таблицы `Card` в `1.sqm`. В навигации появляется новый экран `GroupListScreen` между `SpaceListScreen` и `CardListScreen`. `StudySetupScreen` / `StudyScreen` принимают `StudyScope` (Space | Group). На iOS — параллельные изменения через FlowObserver + KoinResolver.

**Tech Stack:** Kotlin Multiplatform 2.1.0, Compose Multiplatform 1.7.3, SQLDelight 2.0.2, Koin 3.5.6, Voyager 1.1.0-beta02, SwiftUI (iOS).

**Тестирование:** В проекте нет юнит-тестов (явная политика — см. `.claude/CLAUDE.md`). Верификация каждого блока — `./gradlew build` + ручной smoke-тест в конце.

---

## Спек

Спек: [docs/superpowers/specs/2026-05-12-card-groups-design.md](../specs/2026-05-12-card-groups-design.md).

---

## Структура файлов

**Создаём:**
- `shared/src/commonMain/sqldelight/com/transcard/db/CardGroup.sq`
- `shared/src/commonMain/sqldelight/com/transcard/db/migrations/1.sqm`
- `shared/src/commonMain/kotlin/com/transcard/data/repository/CardGroupRepositoryImpl.kt`
- `shared/src/commonMain/kotlin/com/transcard/presentation/viewmodel/GroupListViewModel.kt`
- `shared/src/commonMain/kotlin/com/transcard/presentation/screen/GroupListScreen.kt`
- `iosApp/iosApp/Screens/GroupListView.swift`

**Модифицируем:**
- `shared/build.gradle.kts` — включить `verifyMigrations`
- `shared/src/androidMain/kotlin/com/transcard/data/db/DatabaseDriverFactory.android.kt` — включить FK
- `shared/src/iosMain/kotlin/com/transcard/data/db/DatabaseDriverFactory.ios.kt` — включить FK
- `shared/src/commonMain/sqldelight/com/transcard/db/Card.sq` — добавить `groupId` + новые запросы
- `shared/src/commonMain/kotlin/com/transcard/domain/model/Models.kt` — добавить `CardGroup`, `StudyScope`, `Card.groupId`
- `shared/src/commonMain/kotlin/com/transcard/domain/repository/Repositories.kt` — `CardGroupRepository` + новые методы в `CardRepository`
- `shared/src/commonMain/kotlin/com/transcard/data/repository/SpaceRepositoryImpl.kt` — транзакция с дефолтной группой
- `shared/src/commonMain/kotlin/com/transcard/data/repository/CardRepositoryImpl.kt` — новые методы + groupId в `createCard`
- `shared/src/commonMain/kotlin/com/transcard/presentation/viewmodel/CardListViewModel.kt` — `groupId` вместо `spaceId`
- `shared/src/commonMain/kotlin/com/transcard/presentation/viewmodel/StudyViewModel.kt` — `StudyScope`
- `shared/src/commonMain/kotlin/com/transcard/presentation/screen/SpaceListScreen.kt` — `onOpen` → push `GroupListScreen`
- `shared/src/commonMain/kotlin/com/transcard/presentation/screen/CardListScreen.kt` — `groupId` вместо `spaceId`, заголовок «группа · пространство»
- `shared/src/commonMain/kotlin/com/transcard/presentation/screen/StudySetupScreen.kt` — `StudyScope`
- `shared/src/commonMain/kotlin/com/transcard/presentation/screen/StudyScreen.kt` — `StudyScope`
- `shared/src/commonMain/kotlin/com/transcard/presentation/screen/StudyResultScreen.kt` — `StudyScope`
- `shared/src/commonMain/kotlin/com/transcard/di/Modules.kt` — регистрация `CardGroupRepository`, `GroupListViewModel`, изменения сигнатур
- `shared/src/iosMain/kotlin/com/transcard/ios/KoinIOS.kt` — резолверы для GroupListViewModel + смена сигнатур
- `iosApp/iosApp/Bridges/KoinResolver.swift` — фасад для новых вызовов
- `iosApp/iosApp/Screens/SpaceListView.swift` — `NavigationLink` → `GroupListView`
- `iosApp/iosApp/Screens/CardListView.swift` — `groupId` вместо `spaceId`
- `iosApp/iosApp/Screens/StudySetupView.swift` — `StudyScope`
- `iosApp/iosApp/Screens/StudyView.swift` — `StudyScope`
- `iosApp/iosApp/Screens/StudyResultView.swift` — `StudyScope`

---

## Phase 1 — Schema & migration

### Task 1: Включить foreign_keys на Android и iOS драйверах

Текущий код полагается на `ON DELETE CASCADE`, но FK на Android/iOS не enforced.

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/transcard/data/db/DatabaseDriverFactory.android.kt`
- Modify: `shared/src/iosMain/kotlin/com/transcard/data/db/DatabaseDriverFactory.ios.kt`

- [ ] **Step 1: Изменить Android factory**

`shared/src/androidMain/kotlin/com/transcard/data/db/DatabaseDriverFactory.android.kt`:
```kotlin
package com.transcard.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.transcard.db.TransCardDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver = AndroidSqliteDriver(
        schema = TransCardDatabase.Schema,
        context = context,
        name = "transcard.db",
        callback = object : AndroidSqliteDriver.Callback(TransCardDatabase.Schema) {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
            }
        }
    )
}
```

- [ ] **Step 2: Изменить iOS factory**

`shared/src/iosMain/kotlin/com/transcard/data/db/DatabaseDriverFactory.ios.kt`:
```kotlin
package com.transcard.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.transcard.db.TransCardDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(
        configuration = DatabaseConfiguration(
            name = "transcard.db",
            version = TransCardDatabase.Schema.version.toInt(),
            create = { connection ->
                wrapConnection(connection) { TransCardDatabase.Schema.create(it) }
            },
            upgrade = { connection, oldVersion, newVersion ->
                wrapConnection(connection) {
                    TransCardDatabase.Schema.migrate(it, oldVersion.toLong(), newVersion.toLong())
                }
            },
            extendedConfig = DatabaseConfiguration.Extended(
                foreignKeyConstraints = true
            )
        )
    )
}

private fun wrapConnection(
    connection: co.touchlab.sqliter.DatabaseConnection,
    block: (app.cash.sqldelight.driver.native.wrapper.SqlSchema.() -> Unit) = {}
) {
    // helper нужен только если соберётся compile error — иначе удалить
}
```

> **Примечание для исполнителя:** на iOS у `NativeSqliteDriver` есть две формы конструктора — простая (`name = ...`) и расширенная через `DatabaseConfiguration`. Если расширенная форма ломает существующие импорты, есть более простой путь: оставить `NativeSqliteDriver(schema, name)` и **в первом запросе после открытия БД** выполнить `driver.execute(null, "PRAGMA foreign_keys=ON", 0)`. Используй то, что собирается без ошибок.

Альтернативный минимальный вариант iOS (если расширенная конфигурация не находится в проекте):
```kotlin
actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver {
        val driver = NativeSqliteDriver(TransCardDatabase.Schema, "transcard.db")
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        return driver
    }
}
```

- [ ] **Step 3: Build**

```powershell
./gradlew :shared:compileKotlinAndroid :shared:compileKotlinIosX64
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
git add shared/src/androidMain/kotlin/com/transcard/data/db/DatabaseDriverFactory.android.kt shared/src/iosMain/kotlin/com/transcard/data/db/DatabaseDriverFactory.ios.kt
git commit -m "fix: enable foreign_keys PRAGMA on Android and iOS drivers"
```

---

### Task 2: Создать `CardGroup.sq`

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/transcard/db/CardGroup.sq`

- [ ] **Step 1: Написать схему и запросы**

```sql
CREATE TABLE CardGroup (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    spaceId INTEGER NOT NULL,
    name TEXT NOT NULL,
    createdAt INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (spaceId) REFERENCES Space(id) ON DELETE CASCADE
);

CREATE INDEX cardgroup_space_idx ON CardGroup(spaceId);

selectBySpace:
SELECT *
FROM CardGroup
WHERE spaceId = ?
ORDER BY createdAt ASC, id ASC;

selectById:
SELECT *
FROM CardGroup
WHERE id = ?;

insert:
INSERT INTO CardGroup(spaceId, name, createdAt)
VALUES (?, ?, ?);

update:
UPDATE CardGroup
SET name = ?
WHERE id = ?;

deleteById:
DELETE FROM CardGroup
WHERE id = ?;

countBySpace:
SELECT COUNT(*)
FROM CardGroup
WHERE spaceId = ?;

lastInsertedId:
SELECT last_insert_rowid();
```

- [ ] **Step 2: Build (sqldelight gen)**

```powershell
./gradlew :shared:generateCommonMainTransCardDatabaseInterface
```
Expected: BUILD SUCCESSFUL. В `shared/build/generated/sqldelight/code/TransCardDatabase/commonMain/com/transcard/db/` должен появиться `CardGroupQueries.kt`.

- [ ] **Step 3: Commit**

```powershell
git add shared/src/commonMain/sqldelight/com/transcard/db/CardGroup.sq
git commit -m "feat: add CardGroup table and queries"
```

---

### Task 3: Изменить `Card.sq` — добавить `groupId` и новые запросы

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/transcard/db/Card.sq`

- [ ] **Step 1: Переписать файл полностью**

```sql
CREATE TABLE Card (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    spaceId INTEGER NOT NULL,
    groupId INTEGER NOT NULL,
    nativeWord TEXT NOT NULL,
    targetWord TEXT NOT NULL,
    hint TEXT,
    intervalDays REAL NOT NULL DEFAULT 0,
    easiness REAL NOT NULL DEFAULT 2.5,
    repetitions INTEGER NOT NULL DEFAULT 0,
    nextReviewAt INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (spaceId) REFERENCES Space(id) ON DELETE CASCADE,
    FOREIGN KEY (groupId) REFERENCES CardGroup(id) ON DELETE CASCADE
);

CREATE INDEX card_space_idx ON Card(spaceId);
CREATE INDEX card_due_idx ON Card(spaceId, nextReviewAt);
CREATE INDEX card_group_idx ON Card(groupId);

selectBySpace:
SELECT *
FROM Card
WHERE spaceId = ?
ORDER BY id ASC;

selectByGroup:
SELECT *
FROM Card
WHERE groupId = ?
ORDER BY id ASC;

selectById:
SELECT *
FROM Card
WHERE id = ?;

selectDueBySpace:
SELECT *
FROM Card
WHERE spaceId = ? AND nextReviewAt <= :now
ORDER BY nextReviewAt ASC;

selectDueByGroup:
SELECT *
FROM Card
WHERE groupId = ? AND nextReviewAt <= :now
ORDER BY nextReviewAt ASC;

countBySpace:
SELECT COUNT(*)
FROM Card
WHERE spaceId = ?;

countByGroup:
SELECT COUNT(*)
FROM Card
WHERE groupId = ?;

countAllBySpace:
SELECT spaceId, COUNT(*) AS cnt
FROM Card
GROUP BY spaceId;

countAllByGroupInSpace:
SELECT groupId, COUNT(*) AS cnt
FROM Card
WHERE spaceId = ?
GROUP BY groupId;

countDueAllBySpace:
SELECT spaceId, COUNT(*) AS cnt
FROM Card
WHERE nextReviewAt <= :now
GROUP BY spaceId;

countDueAllByGroupInSpace:
SELECT groupId, COUNT(*) AS cnt
FROM Card
WHERE spaceId = ? AND nextReviewAt <= :now
GROUP BY groupId;

countDueAll:
SELECT COUNT(*)
FROM Card
WHERE nextReviewAt <= :now;

selectGardenStages:
SELECT spaceId,
    CASE
        WHEN repetitions < 1 THEN 0
        WHEN repetitions < 3 THEN 1
        WHEN repetitions < 5 THEN 2
        WHEN repetitions < 8 THEN 3
        ELSE 4
    END AS stage,
    COUNT(*) AS cnt
FROM Card
GROUP BY spaceId, stage;

selectGardenStagesByGroupInSpace:
SELECT groupId,
    CASE
        WHEN repetitions < 1 THEN 0
        WHEN repetitions < 3 THEN 1
        WHEN repetitions < 5 THEN 2
        WHEN repetitions < 8 THEN 3
        ELSE 4
    END AS stage,
    COUNT(*) AS cnt
FROM Card
WHERE spaceId = ?
GROUP BY groupId, stage;

insert:
INSERT INTO Card(spaceId, groupId, nativeWord, targetWord, hint)
VALUES (?, ?, ?, ?, ?);

update:
UPDATE Card
SET nativeWord = ?, targetWord = ?, hint = ?
WHERE id = ?;

updateSrs:
UPDATE Card
SET intervalDays = ?, easiness = ?, repetitions = ?, nextReviewAt = ?
WHERE id = ?;

deleteById:
DELETE FROM Card
WHERE id = ?;
```

- [ ] **Step 2: Build (генерация Queries)**

```powershell
./gradlew :shared:generateCommonMainTransCardDatabaseInterface
```
Expected: BUILD SUCCESSFUL. `CardQueries.kt` обновлён с новыми методами. *Сам компил проекта пока упадёт — это ожидаемо, мы починим в следующих тасках.*

- [ ] **Step 3: Commit**

```powershell
git add shared/src/commonMain/sqldelight/com/transcard/db/Card.sq
git commit -m "feat: add groupId column and group-scoped queries to Card.sq"
```

---

### Task 4: Миграция `1.sqm`

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/transcard/db/migrations/1.sqm`

- [ ] **Step 1: Создать файл миграции**

```sql
CREATE TABLE CardGroup (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    spaceId INTEGER NOT NULL,
    name TEXT NOT NULL,
    createdAt INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (spaceId) REFERENCES Space(id) ON DELETE CASCADE
);

CREATE INDEX cardgroup_space_idx ON CardGroup(spaceId);

INSERT INTO CardGroup(spaceId, name, createdAt)
SELECT id, 'Общее', strftime('%s', 'now') * 1000
FROM Space;

CREATE TABLE Card_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    spaceId INTEGER NOT NULL,
    groupId INTEGER NOT NULL,
    nativeWord TEXT NOT NULL,
    targetWord TEXT NOT NULL,
    hint TEXT,
    intervalDays REAL NOT NULL DEFAULT 0,
    easiness REAL NOT NULL DEFAULT 2.5,
    repetitions INTEGER NOT NULL DEFAULT 0,
    nextReviewAt INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (spaceId) REFERENCES Space(id) ON DELETE CASCADE,
    FOREIGN KEY (groupId) REFERENCES CardGroup(id) ON DELETE CASCADE
);

INSERT INTO Card_new (id, spaceId, groupId, nativeWord, targetWord, hint,
                     intervalDays, easiness, repetitions, nextReviewAt)
SELECT c.id, c.spaceId,
       (SELECT g.id FROM CardGroup g WHERE g.spaceId = c.spaceId LIMIT 1),
       c.nativeWord, c.targetWord, c.hint,
       c.intervalDays, c.easiness, c.repetitions, c.nextReviewAt
FROM Card c;

DROP TABLE Card;
ALTER TABLE Card_new RENAME TO Card;

CREATE INDEX card_space_idx ON Card(spaceId);
CREATE INDEX card_due_idx ON Card(spaceId, nextReviewAt);
CREATE INDEX card_group_idx ON Card(groupId);
```

- [ ] **Step 2: Включить `verifyMigrations` в `build.gradle.kts`**

В `shared/build.gradle.kts` обновить блок `sqldelight`:
```kotlin
sqldelight {
    databases {
        create("TransCardDatabase") {
            packageName.set("com.transcard.db")
            verifyMigrations.set(true)
        }
    }
}
```

- [ ] **Step 3: Build с проверкой миграции**

```powershell
./gradlew :shared:verifySqlDelightMigration
```
Expected: BUILD SUCCESSFUL. SQLDelight подтверждает, что миграция приводит схему v1 к v2 без расхождений.

- [ ] **Step 4: Commit**

```powershell
git add shared/src/commonMain/sqldelight/com/transcard/db/migrations/1.sqm shared/build.gradle.kts
git commit -m "feat: add migration v1 -> v2 backfilling default groups"
```

---

## Phase 2 — Domain & Data layer

### Task 5: Domain-модель `CardGroup` + `Card.groupId` + `StudyScope`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/transcard/domain/model/Models.kt`

- [ ] **Step 1: Добавить `groupId` в `Card`**

В `Models.kt`, изменить `data class Card`:
```kotlin
data class Card(
    val id: Long,
    val spaceId: Long,
    val groupId: Long,
    val nativeWord: String,
    val targetWord: String,
    val hint: String? = null,
    val intervalDays: Double = 0.0,
    val easiness: Double = 2.5,
    val repetitions: Int = 0,
    val nextReviewAt: Long = 0L
)
```

- [ ] **Step 2: Добавить `CardGroup` и `StudyScope`**

В конец `Models.kt`:
```kotlin
data class CardGroup(
    val id: Long,
    val spaceId: Long,
    val name: String,
    val createdAt: Long
)

sealed class StudyScope {
    data class Space(val spaceId: Long) : StudyScope()
    data class Group(val groupId: Long) : StudyScope()
}
```

- [ ] **Step 3: Build**

```powershell
./gradlew :shared:compileKotlinMetadata
```
Expected: BUILD сейчас упадёт в репозиториях / ViewModels — это ожидаемо, починим в следующих тасках. Если падает только в `data/` и `presentation/`, идём дальше. Если падает в самой `Models.kt` — синтаксическая ошибка, исправить.

- [ ] **Step 4: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/domain/model/Models.kt
git commit -m "feat: add CardGroup and StudyScope models, groupId on Card"
```

---

### Task 6: Интерфейс `CardGroupRepository` + новые методы `CardRepository`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/transcard/domain/repository/Repositories.kt`

- [ ] **Step 1: Добавить интерфейс `CardGroupRepository` и обновить `CardRepository`**

Полная новая версия `Repositories.kt`:
```kotlin
package com.transcard.domain.repository

import com.transcard.domain.model.Card
import com.transcard.domain.model.CardGroup
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
```

- [ ] **Step 2: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/domain/repository/Repositories.kt
git commit -m "feat: add CardGroupRepository and group-scoped CardRepository methods"
```

---

### Task 7: Имплементация `CardGroupRepositoryImpl`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/transcard/data/repository/CardGroupRepositoryImpl.kt`

- [ ] **Step 1: Написать имплементацию**

```kotlin
package com.transcard.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
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
        db.transactionWithResult {
            db.cardGroupQueries.insert(spaceId, name, now)
            db.cardGroupQueries.lastInsertedId().executeAsOne()
        }
    }

    override suspend fun rename(id: Long, name: String) {
        withContext(dispatcher) {
            db.cardGroupQueries.update(name, id)
        }
    }

    override suspend fun delete(id: Long) {
        withContext(dispatcher) {
            db.cardGroupQueries.deleteById(id)
        }
    }

    private fun com.transcard.db.CardGroup.toDomain() = CardGroup(
        id = id,
        spaceId = spaceId,
        name = name,
        createdAt = createdAt
    )
}
```

- [ ] **Step 2: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/data/repository/CardGroupRepositoryImpl.kt
git commit -m "feat: implement CardGroupRepository"
```

---

### Task 8: Обновить `CardRepositoryImpl` (новые методы + `groupId` в insert)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/transcard/data/repository/CardRepositoryImpl.kt`

- [ ] **Step 1: Переписать файл полностью**

```kotlin
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

    override fun getCardsByGroup(groupId: Long): Flow<List<Card>> =
        db.cardQueries.selectByGroup(groupId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun getDueCardsBySpace(spaceId: Long, now: Long): Flow<List<Card>> =
        db.cardQueries.selectDueBySpace(spaceId, now).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun getDueCardsByGroup(groupId: Long, now: Long): Flow<List<Card>> =
        db.cardQueries.selectDueByGroup(groupId, now).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun countBySpace(spaceId: Long): Flow<Int> =
        db.cardQueries.countBySpace(spaceId).asFlow().mapToOne(dispatcher).map { it.toInt() }

    override fun countByGroup(groupId: Long): Flow<Int> =
        db.cardQueries.countByGroup(groupId).asFlow().mapToOne(dispatcher).map { it.toInt() }

    override fun getCardCountsBySpace(): Flow<Map<Long, Int>> =
        db.cardQueries.countAllBySpace().asFlow().mapToList(dispatcher)
            .map { rows -> rows.associate { it.spaceId to it.cnt.toInt() } }

    override fun getDueCountsBySpace(now: Long): Flow<Map<Long, Int>> =
        db.cardQueries.countDueAllBySpace(now).asFlow().mapToList(dispatcher)
            .map { rows -> rows.associate { it.spaceId to it.cnt.toInt() } }

    override fun getCardCountsByGroup(spaceId: Long): Flow<Map<Long, Int>> =
        db.cardQueries.countAllByGroupInSpace(spaceId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.associate { it.groupId to it.cnt.toInt() } }

    override fun getDueCountsByGroup(spaceId: Long, now: Long): Flow<Map<Long, Int>> =
        db.cardQueries.countDueAllByGroupInSpace(spaceId, now).asFlow().mapToList(dispatcher)
            .map { rows -> rows.associate { it.groupId to it.cnt.toInt() } }

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

    override fun getGardenStagesByGroup(spaceId: Long): Flow<Map<Long, Map<GardenStage, Int>>> {
        val stages = GardenStage.values()
        return db.cardQueries.selectGardenStagesByGroupInSpace(spaceId).asFlow().mapToList(dispatcher)
            .map { rows ->
                rows.groupBy { it.groupId }
                    .mapValues { (_, groupRows) ->
                        groupRows.associate { stages[it.stage.toInt()] to it.cnt.toInt() }
                    }
            }
    }

    override suspend fun createCard(
        spaceId: Long,
        groupId: Long,
        nativeWord: String,
        targetWord: String,
        hint: String?
    ) {
        withContext(dispatcher) {
            db.cardQueries.insert(spaceId, groupId, nativeWord, targetWord, hint)
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
        groupId = groupId,
        nativeWord = nativeWord,
        targetWord = targetWord,
        hint = hint,
        intervalDays = intervalDays,
        easiness = easiness,
        repetitions = repetitions.toInt(),
        nextReviewAt = nextReviewAt
    )
}
```

- [ ] **Step 2: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/data/repository/CardRepositoryImpl.kt
git commit -m "feat: extend CardRepositoryImpl with group-scoped queries"
```

---

### Task 9: `SpaceRepositoryImpl.createSpace` создаёт дефолтную группу транзакционно

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/transcard/data/repository/SpaceRepositoryImpl.kt`

- [ ] **Step 1: Переписать имплементацию**

```kotlin
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
                val spaceId = db.spaceQueries.lastInsertedId().executeAsOne()
                db.cardGroupQueries.insert(spaceId, DEFAULT_GROUP_NAME, now)
                spaceId
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

    private companion object {
        const val DEFAULT_GROUP_NAME = "Общее"
    }
}
```

- [ ] **Step 2: Build**

```powershell
./gradlew :shared:compileKotlinMetadata
```
Expected: BUILD SUCCESSFUL для `data/` (presentation ещё может ругаться).

- [ ] **Step 3: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/data/repository/SpaceRepositoryImpl.kt
git commit -m "feat: auto-create default 'Общее' group on space creation"
```

---

## Phase 3 — Presentation layer

### Task 10: `GroupListViewModel`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/transcard/presentation/viewmodel/GroupListViewModel.kt`

- [ ] **Step 1: Написать VM**

```kotlin
package com.transcard.presentation.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.transcard.domain.model.CardGroup
import com.transcard.domain.model.GardenStage
import com.transcard.domain.model.Space
import com.transcard.domain.repository.CardGroupRepository
import com.transcard.domain.repository.CardRepository
import com.transcard.domain.repository.SpaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class GroupCardItem(
    val group: CardGroup,
    val cardsCount: Int,
    val dueCount: Int,
    val stages: Map<GardenStage, Int>
)

data class GroupListUiState(
    val space: Space? = null,
    val items: List<GroupCardItem> = emptyList(),
    val totalDue: Int = 0,
    val isLoading: Boolean = true
)

class GroupListViewModel(
    private val spaceId: Long,
    private val spaceRepository: SpaceRepository,
    private val cardGroupRepository: CardGroupRepository,
    private val cardRepository: CardRepository
) : ScreenModel {

    private val _space = MutableStateFlow<Space?>(null)
    val space: StateFlow<Space?> = _space.asStateFlow()

    val state: StateFlow<GroupListUiState> = combine(
        cardGroupRepository.observeBySpace(spaceId),
        cardRepository.getCardCountsByGroup(spaceId),
        cardRepository.getDueCountsByGroup(spaceId, Clock.System.now().toEpochMilliseconds()),
        cardRepository.getGardenStagesByGroup(spaceId),
        _space
    ) { groups, counts, dueCounts, stages, sp ->
        val items = groups.map { g ->
            GroupCardItem(
                group = g,
                cardsCount = counts[g.id] ?: 0,
                dueCount = dueCounts[g.id] ?: 0,
                stages = stages[g.id].orEmpty()
            )
        }
        GroupListUiState(
            space = sp,
            items = items,
            totalDue = dueCounts.values.sum(),
            isLoading = false
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), GroupListUiState())

    init {
        screenModelScope.launch {
            _space.value = spaceRepository.getById(spaceId)
        }
    }

    fun createGroup(name: String) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        screenModelScope.launch {
            cardGroupRepository.create(spaceId, trimmed)
        }
    }

    fun renameGroup(id: Long, name: String) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isEmpty()) return
        screenModelScope.launch {
            cardGroupRepository.rename(id, trimmed)
        }
    }

    fun deleteGroup(id: Long) {
        screenModelScope.launch {
            cardGroupRepository.delete(id)
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 50
    }
}
```

- [ ] **Step 2: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/presentation/viewmodel/GroupListViewModel.kt
git commit -m "feat: add GroupListViewModel"
```

---

### Task 11: `CardListViewModel` переключается на `groupId`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/transcard/presentation/viewmodel/CardListViewModel.kt`

- [ ] **Step 1: Переписать VM**

Заменить параметр `spaceId` на `groupId`, добавить загрузку `group` и `space`:
```kotlin
package com.transcard.presentation.viewmodel

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.transcard.domain.model.Card
import com.transcard.domain.model.CardGroup
import com.transcard.domain.model.Space
import com.transcard.domain.model.SuggestionSource
import com.transcard.domain.model.TranslationSuggestion
import com.transcard.domain.repository.CardGroupRepository
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
    private val groupId: Long,
    private val spaceRepository: SpaceRepository,
    private val cardGroupRepository: CardGroupRepository,
    private val cardRepository: CardRepository,
    private val translationRepository: TranslationRepository
) : ScreenModel {

    val cards: StateFlow<List<Card>> = cardRepository.getCardsByGroup(groupId)
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _space = MutableStateFlow<Space?>(null)
    val space: StateFlow<Space?> = _space.asStateFlow()

    private val _group = MutableStateFlow<CardGroup?>(null)
    val group: StateFlow<CardGroup?> = _group.asStateFlow()

    private val nativeQuery = MutableStateFlow("")
    private val _suggestions = MutableStateFlow(SuggestionsState())
    val suggestions: StateFlow<SuggestionsState> = _suggestions.asStateFlow()

    init {
        screenModelScope.launch {
            val g = cardGroupRepository.getById(groupId)
            _group.value = g
            if (g != null) _space.value = spaceRepository.getById(g.spaceId)
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
        val g = _group.value ?: return
        screenModelScope.launch {
            cardRepository.createCard(
                spaceId = g.spaceId,
                groupId = g.id,
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
```

- [ ] **Step 2: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/presentation/viewmodel/CardListViewModel.kt
git commit -m "refactor: scope CardListViewModel to group instead of space"
```

---

### Task 12: `StudyViewModel` принимает `StudyScope`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/transcard/presentation/viewmodel/StudyViewModel.kt`

- [ ] **Step 1: Переписать VM**

```kotlin
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
                StudyMode.SCHEDULED -> when (scope) {
                    is StudyScope.Space -> cardRepository.getDueCardsBySpace(scope.spaceId, now).first()
                    is StudyScope.Group -> cardRepository.getDueCardsByGroup(scope.groupId, now).first()
                }
                StudyMode.DRILL -> when (scope) {
                    is StudyScope.Space -> cardRepository.getCardsBySpace(scope.spaceId).first()
                    is StudyScope.Group -> cardRepository.getCardsByGroup(scope.groupId).first()
                }
            }.shuffled()
            val total = when (scope) {
                is StudyScope.Space -> cardRepository.countBySpace(scope.spaceId).first()
                is StudyScope.Group -> cardRepository.countByGroup(scope.groupId).first()
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
```

- [ ] **Step 2: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/presentation/viewmodel/StudyViewModel.kt
git commit -m "refactor: StudyViewModel accepts StudyScope (space or group)"
```

---

### Task 13: Koin `Modules.kt` — регистрация новых компонентов

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/transcard/di/Modules.kt`

- [ ] **Step 1: Переписать модуль**

```kotlin
package com.transcard.di

import com.transcard.data.api.YandexDictionaryApi
import com.transcard.data.api.createHttpClient
import com.transcard.data.db.createDatabase
import com.transcard.data.repository.CardGroupRepositoryImpl
import com.transcard.data.repository.CardRepositoryImpl
import com.transcard.data.repository.ProgressRepositoryImpl
import com.transcard.data.repository.SpaceRepositoryImpl
import com.transcard.data.repository.TranslationRepositoryImpl
import com.transcard.data.translation.LocalDictionary
import com.transcard.domain.model.StudyDirection
import com.transcard.domain.model.StudyMode
import com.transcard.domain.model.StudyScope
import com.transcard.domain.repository.CardGroupRepository
import com.transcard.domain.repository.CardRepository
import com.transcard.domain.repository.ProgressRepository
import com.transcard.domain.repository.SpaceRepository
import com.transcard.domain.repository.TranslationRepository
import com.transcard.domain.usecase.CheckAnswerUseCase
import com.transcard.domain.usecase.GetSpaceStatsUseCase
import com.transcard.domain.usecase.GetStudyCardsUseCase
import com.transcard.domain.usecase.ReviewCardUseCase
import com.transcard.presentation.viewmodel.CardListViewModel
import com.transcard.presentation.viewmodel.GardenViewModel
import com.transcard.presentation.viewmodel.GroupListViewModel
import com.transcard.presentation.viewmodel.SpaceListViewModel
import com.transcard.presentation.viewmodel.StudyViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val sharedModule = module {
    single { createDatabase(get()) }

    single<SpaceRepository> { SpaceRepositoryImpl(get()) }
    single<CardGroupRepository> { CardGroupRepositoryImpl(get()) }
    single<CardRepository> { CardRepositoryImpl(get()) }
    single<ProgressRepository> { ProgressRepositoryImpl(get()) }

    single { createHttpClient() }
    single { YandexDictionaryApi(get()) }

    single { LocalDictionary() }
    single<TranslationRepository> { TranslationRepositoryImpl(get(), get()) }

    factoryOf(::GetStudyCardsUseCase)
    factoryOf(::CheckAnswerUseCase)
    factoryOf(::GetSpaceStatsUseCase)
    factoryOf(::ReviewCardUseCase)

    factory { SpaceListViewModel(get(), get(), get()) }
    factory { params -> GroupListViewModel(params.get<Long>(), get(), get(), get()) }
    factory { params -> CardListViewModel(params.get<Long>(), get(), get(), get(), get()) }
    factory { params -> GardenViewModel(params.get<Long>(), get(), get()) }
    factory { params ->
        StudyViewModel(
            params.get<StudyScope>(),
            params.get<StudyDirection>(),
            params.get<StudyMode>(),
            get(), get(), get()
        )
    }
}

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(platformModule(), sharedModule)
    }
```

- [ ] **Step 2: Build**

```powershell
./gradlew :shared:compileKotlinMetadata
```
Expected: BUILD SUCCESSFUL для всего `commonMain`, кроме экранов (их правим в следующих тасках).

- [ ] **Step 3: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/di/Modules.kt
git commit -m "feat: register CardGroupRepository and GroupListViewModel in Koin"
```

---

## Phase 4 — Compose UI (Android + Desktop)

### Task 14: Создать `GroupListScreen`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/transcard/presentation/screen/GroupListScreen.kt`

- [ ] **Step 1: Написать экран**

```kotlin
package com.transcard.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.transcard.domain.model.StudyScope
import com.transcard.presentation.components.AppCard
import com.transcard.presentation.viewmodel.GroupCardItem
import com.transcard.presentation.viewmodel.GroupListViewModel
import org.koin.core.parameter.parametersOf

data class GroupListScreen(val spaceId: Long) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm: GroupListViewModel = koinScreenModel { parametersOf(spaceId) }
        val navigator = LocalNavigator.currentOrThrow
        val state by vm.state.collectAsState()

        var showCreate by remember { mutableStateOf(false) }
        var groupToRename by remember { mutableStateOf<GroupCardItem?>(null) }
        var groupToDelete by remember { mutableStateOf<GroupCardItem?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(state.space?.name ?: "") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Группа") },
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.totalDue > 0) {
                    item {
                        AppCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Изучать всё пространство",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "${state.totalDue} к повторению",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        navigator.push(
                                            StudySetupScreen(StudyScope.Space(spaceId))
                                        )
                                    }
                                ) {
                                    Icon(Icons.Filled.PlayArrow, "Начать")
                                }
                            }
                        }
                    }
                }

                items(state.items, key = { it.group.id }) { item ->
                    GroupRow(
                        item = item,
                        onClick = { navigator.push(CardListScreen(item.group.id)) },
                        onStudy = {
                            navigator.push(
                                StudySetupScreen(StudyScope.Group(item.group.id))
                            )
                        },
                        onRename = { groupToRename = item },
                        onDelete = { groupToDelete = item }
                    )
                }
            }
        }

        if (showCreate) {
            GroupNameDialog(
                title = "Новая группа",
                initial = "",
                onConfirm = {
                    vm.createGroup(it)
                    showCreate = false
                },
                onDismiss = { showCreate = false }
            )
        }

        groupToRename?.let { gi ->
            GroupNameDialog(
                title = "Переименовать группу",
                initial = gi.group.name,
                onConfirm = {
                    vm.renameGroup(gi.group.id, it)
                    groupToRename = null
                },
                onDismiss = { groupToRename = null }
            )
        }

        groupToDelete?.let { gi ->
            AlertDialog(
                onDismissRequest = { groupToDelete = null },
                title = { Text("Удалить группу?") },
                text = {
                    Text("«${gi.group.name}» и ${gi.cardsCount} карточек будут удалены.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteGroup(gi.group.id)
                        groupToDelete = null
                    }) { Text("Удалить") }
                },
                dismissButton = {
                    TextButton(onClick = { groupToDelete = null }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
private fun GroupRow(
    item: GroupCardItem,
    onClick: () -> Unit,
    onStudy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.group.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${item.cardsCount} карточек" +
                            (if (item.dueCount > 0) " · ${item.dueCount} к повторению" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (item.dueCount > 0) {
                    IconButton(onClick = onStudy) {
                        Icon(Icons.Filled.PlayArrow, "Начать")
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, "Меню")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Переименовать") },
                            onClick = { menuOpen = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить") },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClick) {
                    Text("Открыть карточки")
                }
            }
        }
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 50) name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotEmpty()
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
```

- [ ] **Step 2: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/presentation/screen/GroupListScreen.kt
git commit -m "feat: add GroupListScreen with create/rename/delete and study entries"
```

---

### Task 15: `CardListScreen` принимает `groupId` + заголовок «группа · пространство»

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/transcard/presentation/screen/CardListScreen.kt`

- [ ] **Step 1: Изменить data class, использование VM и навигацию study**

Найти в файле:
```kotlin
data class CardListScreen(val spaceId: Long) : Screen {
```
Заменить на:
```kotlin
data class CardListScreen(val groupId: Long) : Screen {
```

Найти строку с `koinScreenModel`:
```kotlin
val vm: CardListViewModel = koinScreenModel { parametersOf(spaceId) }
```
Заменить на:
```kotlin
val vm: CardListViewModel = koinScreenModel { parametersOf(groupId) }
```

Добавить collectAsState для `group`:
```kotlin
val group by vm.group.collectAsState()
val space by vm.space.collectAsState()
```

Найти `TopAppBar` с `space?.name` и заменить title на показ обоих:
```kotlin
TopAppBar(
    title = {
        Column {
            Text(group?.name ?: "")
            space?.let { sp ->
                Text(
                    sp.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    },
    ...
)
```

Найти кнопку study:
```kotlin
onClick = { navigator.push(StudySetupScreen(spaceId)) },
```
Заменить на:
```kotlin
onClick = { navigator.push(StudySetupScreen(StudyScope.Group(groupId))) },
```

И добавить в импорты `import com.transcard.domain.model.StudyScope`.

> **Примечание для исполнителя:** в файле есть другие места, где использовался `spaceId` (например, для построения текста заголовка). Прочти весь файл, замени все упоминания старого `space` на пары `group` + `space` где нужно. Не трогай логику добавления/редактирования карточек — она вся вызывает VM.

- [ ] **Step 2: Build**

```powershell
./gradlew :shared:compileKotlinAndroid
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/presentation/screen/CardListScreen.kt
git commit -m "refactor: CardListScreen scoped to group, shows group + space in title"
```

---

### Task 16: `SpaceListScreen` — переход на `GroupListScreen`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/transcard/presentation/screen/SpaceListScreen.kt`

- [ ] **Step 1: Заменить навигацию open**

Найти:
```kotlin
onOpen = { navigator.push(CardListScreen(item.space.id)) },
onStudy = { navigator.push(StudySetupScreen(item.space.id)) },
```
Заменить на:
```kotlin
onOpen = { navigator.push(GroupListScreen(item.space.id)) },
onStudy = { navigator.push(StudySetupScreen(StudyScope.Space(item.space.id))) },
```

Добавить импорт:
```kotlin
import com.transcard.domain.model.StudyScope
```

- [ ] **Step 2: Build**

```powershell
./gradlew :shared:compileKotlinAndroid
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/presentation/screen/SpaceListScreen.kt
git commit -m "feat: navigate from SpaceList into GroupList"
```

---

### Task 17: `StudySetupScreen`, `StudyScreen`, `StudyResultScreen` принимают `StudyScope`

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/transcard/presentation/screen/StudySetupScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/transcard/presentation/screen/StudyScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/transcard/presentation/screen/StudyResultScreen.kt`

- [ ] **Step 1: `StudySetupScreen` — изменить конструктор и передачу в StudyScreen**

В `StudySetupScreen.kt`:

Найти:
```kotlin
data class StudySetupScreen(val spaceId: Long) : Screen {
```
Заменить на:
```kotlin
data class StudySetupScreen(val scope: StudyScope) : Screen {
```

Добавить импорт:
```kotlin
import com.transcard.domain.model.StudyScope
```

Найти переход:
```kotlin
navigator.replace(StudyScreen(spaceId, direction, mode))
```
Заменить на:
```kotlin
navigator.replace(StudyScreen(scope, direction, mode))
```

- [ ] **Step 2: `StudyScreen` — изменить конструктор и параметризацию VM**

В `StudyScreen.kt`:

Найти:
```kotlin
data class StudyScreen(
    val spaceId: Long,
    val direction: StudyDirection,
    val mode: StudyMode
) : Screen {
```
Заменить на:
```kotlin
data class StudyScreen(
    val scope: StudyScope,
    val direction: StudyDirection,
    val mode: StudyMode
) : Screen {
```

Импорт:
```kotlin
import com.transcard.domain.model.StudyScope
```

Найти строку с `koinScreenModel`:
```kotlin
val vm: StudyViewModel = koinScreenModel { parametersOf(spaceId, direction, mode) }
```
Заменить на:
```kotlin
val vm: StudyViewModel = koinScreenModel { parametersOf(scope, direction, mode) }
```

Найти переходы на `StudyResultScreen`:
```kotlin
navigator.replace(StudyResultScreen(spaceId, direction, mode, correct, total))
```
(если такое есть; точная строка в файле). Заменить `spaceId` на `scope`.

- [ ] **Step 3: `StudyResultScreen` — изменить конструктор**

В `StudyResultScreen.kt`:

Найти:
```kotlin
data class StudyResultScreen(
    val spaceId: Long,
    val direction: StudyDirection,
    ...
```
Заменить `val spaceId: Long` на `val scope: StudyScope`. Импорт `import com.transcard.domain.model.StudyScope`.

Найти кнопку «Учить снова»:
```kotlin
onClick = { navigator.replace(StudyScreen(spaceId, direction, mode)) },
```
Заменить на:
```kotlin
onClick = { navigator.replace(StudyScreen(scope, direction, mode)) },
```

- [ ] **Step 4: Build**

```powershell
./gradlew :shared:compileKotlinAndroid :shared:compileKotlinDesktop
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add shared/src/commonMain/kotlin/com/transcard/presentation/screen/StudySetupScreen.kt shared/src/commonMain/kotlin/com/transcard/presentation/screen/StudyScreen.kt shared/src/commonMain/kotlin/com/transcard/presentation/screen/StudyResultScreen.kt
git commit -m "refactor: study screens accept StudyScope"
```

---

### Task 18: Полный билд Android + Desktop

**Files:** (никаких изменений — только верификация)

- [ ] **Step 1: Полная сборка Compose таргетов**

```powershell
./gradlew :shared:build :androidApp:assembleDebug :desktopApp:build
```
Expected: BUILD SUCCESSFUL.

Если падает — НЕ коммитим. Чинить ошибки прицельно (опечатка в импорте, забытый параметр и т.п.). Не лезть в iOS — иначе разрулим.

---

## Phase 5 — iOS

### Task 19: Расширить Koin-бридж в `KoinIOS.kt` и `KoinResolver.swift`

**Files:**
- Modify: `shared/src/iosMain/kotlin/com/transcard/ios/KoinIOS.kt`
- Modify: `iosApp/iosApp/Bridges/KoinResolver.swift`

- [ ] **Step 1: Обновить `KoinIOS.kt`**

```kotlin
package com.transcard.ios

import com.transcard.data.api.YandexDictionaryApi
import com.transcard.data.preferences.Preferences
import com.transcard.di.initKoin as initKoinShared
import com.transcard.domain.model.StudyDirection
import com.transcard.domain.model.StudyMode
import com.transcard.domain.model.StudyScope
import com.transcard.presentation.viewmodel.CardListViewModel
import com.transcard.presentation.viewmodel.GardenViewModel
import com.transcard.presentation.viewmodel.GroupListViewModel
import com.transcard.presentation.viewmodel.SpaceListViewModel
import com.transcard.presentation.viewmodel.StudyViewModel
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

object KoinHelper {
    val shared: KoinHelper get() = this

    private lateinit var koinInstance: Koin

    fun start() {
        koinInstance = initKoinShared().koin
    }

    val koin: Koin get() = koinInstance

    fun getSpaceListViewModel(): SpaceListViewModel = koin.get()

    fun getGroupListViewModel(spaceId: Long): GroupListViewModel =
        koin.get { parametersOf(spaceId) }

    fun getCardListViewModel(groupId: Long): CardListViewModel =
        koin.get { parametersOf(groupId) }

    fun getStudyViewModel(
        scope: StudyScope,
        direction: StudyDirection,
        mode: StudyMode
    ): StudyViewModel = koin.get { parametersOf(scope, direction, mode) }

    fun getGardenViewModel(spaceId: Long): GardenViewModel =
        koin.get { parametersOf(spaceId) }

    fun getPreferences(): Preferences = koin.get()

    fun isYandexConfigured(): Boolean = koin.get<YandexDictionaryApi>().isConfigured
}
```

- [ ] **Step 2: Обновить `KoinResolver.swift`**

```swift
import Foundation
import Shared

enum DI {
    static func spaceListViewModel() -> SpaceListViewModel {
        return KoinHelper.shared.getSpaceListViewModel()
    }

    static func groupListViewModel(spaceId: Int64) -> GroupListViewModel {
        return KoinHelper.shared.getGroupListViewModel(spaceId: spaceId)
    }

    static func cardListViewModel(groupId: Int64) -> CardListViewModel {
        return KoinHelper.shared.getCardListViewModel(groupId: groupId)
    }

    static func studyViewModel(
        scope: StudyScope,
        direction: StudyDirection,
        mode: StudyMode
    ) -> StudyViewModel {
        return KoinHelper.shared.getStudyViewModel(
            scope: scope,
            direction: direction,
            mode: mode
        )
    }

    static func gardenViewModel(spaceId: Int64) -> GardenViewModel {
        return KoinHelper.shared.getGardenViewModel(spaceId: spaceId)
    }

    static func preferences() -> Preferences {
        return KoinHelper.shared.getPreferences()
    }
}
```

- [ ] **Step 3: Build iOS framework**

```powershell
./gradlew :shared:compileKotlinIosX64
```
Expected: BUILD SUCCESSFUL (Swift код будет проверен на следующих тасках, когда обновим Swift-файлы).

- [ ] **Step 4: Commit**

```powershell
git add shared/src/iosMain/kotlin/com/transcard/ios/KoinIOS.kt iosApp/iosApp/Bridges/KoinResolver.swift
git commit -m "feat: iOS bridge exposes GroupListViewModel and StudyScope"
```

---

### Task 20: Создать `GroupListView.swift`

**Files:**
- Create: `iosApp/iosApp/Screens/GroupListView.swift`

- [ ] **Step 1: Написать SwiftUI экран**

```swift
import SwiftUI
import Shared

@MainActor
final class GroupListObservable: ObservableObject {
    @Published var state: GroupListUiState = GroupListUiState(
        space: nil, items: [], totalDue: 0, isLoading: true
    )
    let viewModel: GroupListViewModel
    private var subscription: FlowSubscription?

    init(spaceId: Int64) {
        self.viewModel = DI.groupListViewModel(spaceId: spaceId)
        subscription = FlowSubscription(flow: viewModel.state) { [weak self] (s: GroupListUiState) in
            self?.state = s
        }
    }
}

struct GroupListView: View {
    let spaceId: Int64
    @StateObject private var state: GroupListObservable
    @State private var showCreate = false
    @State private var groupToRename: GroupCardItem?
    @State private var groupToDelete: GroupCardItem?

    init(spaceId: Int64) {
        self.spaceId = spaceId
        _state = StateObject(wrappedValue: GroupListObservable(spaceId: spaceId))
    }

    var body: some View {
        ZStack {
            AppPalette.background.ignoresSafeArea()
            content
        }
        .navigationTitle(state.state.space?.name ?? "")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { showCreate = true } label: {
                    Label("Группа", systemImage: "plus")
                }
            }
        }
        .sheet(isPresented: $showCreate) {
            GroupNameSheet(title: "Новая группа", initial: "") { name in
                state.viewModel.createGroup(name: name)
                showCreate = false
            }
        }
        .sheet(item: $groupToRename) { gi in
            GroupNameSheet(title: "Переименовать", initial: gi.group.name) { name in
                state.viewModel.renameGroup(id: gi.group.id, name: name)
                groupToRename = nil
            }
        }
        .alert(item: $groupToDelete) { gi in
            Alert(
                title: Text("Удалить группу?"),
                message: Text("«\(gi.group.name)» и \(gi.cardsCount) карточек будут удалены."),
                primaryButton: .destructive(Text("Удалить")) {
                    state.viewModel.deleteGroup(id: gi.group.id)
                },
                secondaryButton: .cancel(Text("Отмена"))
            )
        }
    }

    @ViewBuilder
    private var content: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if state.state.totalDue > 0 {
                    StudyAllBanner(
                        due: Int(state.state.totalDue),
                        spaceId: spaceId
                    )
                }
                ForEach(state.state.items, id: \.group.id) { item in
                    GroupRow(
                        item: item,
                        onRename: { groupToRename = item },
                        onDelete: { groupToDelete = item }
                    )
                }
            }
            .padding(16)
        }
    }
}

private struct StudyAllBanner: View {
    let due: Int
    let spaceId: Int64

    var body: some View {
        NavigationLink(
            destination: StudySetupView(scope: StudyScope.Space(spaceId: spaceId))
        ) {
            HStack {
                VStack(alignment: .leading) {
                    Text("Изучать всё пространство").font(.headline)
                    Text("\(due) к повторению")
                        .font(.caption)
                        .foregroundColor(AppPalette.textSecondary)
                }
                Spacer()
                Image(systemName: "play.fill")
                    .padding(10)
                    .background(AppPalette.primary)
                    .foregroundColor(.white)
                    .clipShape(Circle())
            }
            .padding(16)
            .background(AppPalette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

private struct GroupRow: View {
    let item: GroupCardItem
    let onRename: () -> Void
    let onDelete: () -> Void

    var body: some View {
        NavigationLink(destination: CardListView(groupId: item.group.id, title: item.group.name)) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(item.group.name)
                        .font(.title3.weight(.semibold))
                        .foregroundColor(AppPalette.textPrimary)
                    Spacer()
                    Menu {
                        Button("Переименовать", action: onRename)
                        Button("Удалить", role: .destructive, action: onDelete)
                    } label: {
                        Image(systemName: "ellipsis")
                            .foregroundColor(AppPalette.textSecondary)
                            .padding(8)
                    }
                }
                Text("\(item.cardsCount) карточек" +
                     (item.dueCount > 0 ? " · \(item.dueCount) к повторению" : ""))
                    .font(.subheadline)
                    .foregroundColor(AppPalette.textSecondary)
                if item.dueCount > 0 {
                    NavigationLink(
                        destination: StudySetupView(scope: StudyScope.Group(groupId: item.group.id))
                    ) {
                        Label("Изучать", systemImage: "play.fill")
                            .font(.subheadline.weight(.medium))
                            .foregroundColor(AppPalette.primary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
            .background(AppPalette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

extension GroupCardItem: @retroactive Identifiable {
    public var id: Int64 { group.id }
}

private struct GroupNameSheet: View {
    let title: String
    let initial: String
    var onConfirm: (String) -> Void

    @State private var name: String
    @Environment(\.dismiss) private var dismiss

    init(title: String, initial: String, onConfirm: @escaping (String) -> Void) {
        self.title = title
        self.initial = initial
        self.onConfirm = onConfirm
        _name = State(initialValue: initial)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Название") {
                    TextField("Например, «Еда»", text: $name)
                }
            }
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Отмена") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Сохранить") {
                        onConfirm(name)
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```powershell
git add iosApp/iosApp/Screens/GroupListView.swift
git commit -m "feat(ios): add GroupListView"
```

> **Замечание:** файл `GroupListView.swift` нужно вручную добавить в Xcode project в iosApp.xcodeproj. Это делает пользователь при первой iOS-сборке (см. README).

---

### Task 21: `CardListView.swift` — `groupId` вместо `spaceId`

**Files:**
- Modify: `iosApp/iosApp/Screens/CardListView.swift`

- [ ] **Step 1: Заменить параметр**

В `CardListObservable`:
```swift
init(groupId: Int64) {
    self.viewModel = DI.cardListViewModel(groupId: groupId)
    ...
}
```

В `CardListView`:
```swift
let groupId: Int64
...
init(groupId: Int64, title: String) {
    self.groupId = groupId
    self.title = title
    _state = StateObject(wrappedValue: CardListObservable(groupId: groupId))
}
```

В переходе на StudySetup:
```swift
NavigationLink(destination: StudySetupView(scope: StudyScope.Group(groupId: groupId))) {
    ...
}
```

> **Замечание для исполнителя:** в файле могут быть и другие места, где `spaceId` использовался для построения текста или передачи в дочерние View — перепроверь весь файл. ViewModel теперь сам содержит `group` и `space` flows; если экран показывает имя пространства — можно подписаться через FlowSubscription по аналогии с `state`.

- [ ] **Step 2: Commit**

```powershell
git add iosApp/iosApp/Screens/CardListView.swift
git commit -m "refactor(ios): CardListView scoped to group"
```

---

### Task 22: `StudySetupView.swift`, `StudyView.swift`, `StudyResultView.swift` — `StudyScope`

**Files:**
- Modify: `iosApp/iosApp/Screens/StudySetupView.swift`
- Modify: `iosApp/iosApp/Screens/StudyView.swift`
- Modify: `iosApp/iosApp/Screens/StudyResultView.swift`

- [ ] **Step 1: `StudySetupView`**

Заменить `let spaceId: Int64` на `let scope: StudyScope`. Обновить инициализатор. В переходе на StudyView передавать `scope`.

- [ ] **Step 2: `StudyView`**

Заменить `let spaceId: Int64` на `let scope: StudyScope`. В резолвере VM: `DI.studyViewModel(scope: scope, direction: direction, mode: mode)`.

- [ ] **Step 3: `StudyResultView`**

Заменить `let spaceId: Int64` на `let scope: StudyScope`. В кнопке «Учить снова» передавать `scope` в StudyView.

> **Замечание:** Конкретные строки зависят от текущего состояния файла. Прочти каждый файл, найди упоминания `spaceId` в инициализаторах и переходах, замени на `scope: StudyScope`.

- [ ] **Step 4: Commit**

```powershell
git add iosApp/iosApp/Screens/StudySetupView.swift iosApp/iosApp/Screens/StudyView.swift iosApp/iosApp/Screens/StudyResultView.swift
git commit -m "refactor(ios): study screens accept StudyScope"
```

---

### Task 23: `SpaceListView.swift` — переход на `GroupListView`

**Files:**
- Modify: `iosApp/iosApp/Screens/SpaceListView.swift`

- [ ] **Step 1: Заменить NavigationLink**

Найти:
```swift
NavigationLink(destination: CardListView(spaceId: item.space.id, title: item.space.name)) {
```
Заменить на:
```swift
NavigationLink(destination: GroupListView(spaceId: item.space.id)) {
```

Найти:
```swift
NavigationLink(destination: StudySetupView(spaceId: item.space.id)) {
```
Заменить на:
```swift
NavigationLink(destination: StudySetupView(scope: StudyScope.Space(spaceId: item.space.id))) {
```

- [ ] **Step 2: Commit**

```powershell
git add iosApp/iosApp/Screens/SpaceListView.swift
git commit -m "feat(ios): SpaceList navigates into GroupList"
```

---

### Task 24: Build iOS framework

- [ ] **Step 1: Сборка framework**

```powershell
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

Expected: BUILD SUCCESSFUL. Swift-код проверяется только в Xcode — на этом этапе автоматическая верификация невозможна без Xcode. Указать пользователю: открыть iosApp.xcodeproj, убедиться, что `GroupListView.swift` добавлен в target, собрать на симуляторе.

> Если этап **только Windows** — пропустить ручную Xcode-проверку, ограничиться `./gradlew build` (он соберёт iosX64, iosArm64, iosSimulatorArm64 framework). Swift тогда верифицируется при следующем открытии в Xcode.

---

## Phase 6 — Финальная верификация

### Task 25: Полный билд всех таргетов

- [ ] **Step 1: Запустить `./gradlew build`**

```powershell
./gradlew build
```
Expected: BUILD SUCCESSFUL для `:shared` (Android, Desktop, iosX64, iosArm64, iosSimulatorArm64), `:androidApp`, `:desktopApp`.

- [ ] **Step 2: Если есть ошибки — фиксить, не двигаться дальше.**

---

### Task 26: Ручной smoke-тест

**Файлы:** (никаких изменений)

- [ ] **Step 1: Android — установить debug APK поверх предыдущей версии**

```powershell
./gradlew :androidApp:installDebug
```

Проверить вручную:
- [ ] Существующие пространства не пустые, карточки на месте.
- [ ] У каждого старого пространства видна одна группа «Общее» с всеми старыми карточками.
- [ ] Создание нового пространства: автоматически создаётся группа «Общее».
- [ ] Создание новой группы в пространстве → появляется в списке.
- [ ] Открыть группу → видны карточки только этой группы.
- [ ] Добавление новой карточки в группе → попадает в эту группу.
- [ ] Переименование группы → имя обновляется.
- [ ] Удаление группы с N карточками → диалог «Удалить «X» и N карточек?» → подтверждаем → группа и её карточки исчезают.
- [ ] Кнопка «Изучать всё пространство» с экрана групп → study микса всех групп.
- [ ] Кнопка «Изучать» на конкретной группе → study только её карточек.
- [ ] Удаление пространства → каскадно удаляет группы и карточки (без orphan'ов после переоткрытия).

- [ ] **Step 2: Desktop**

```powershell
./gradlew :desktopApp:run
```
Те же сценарии, что и на Android.

- [ ] **Step 3: iOS (если доступен macOS)**

Открыть iosApp.xcodeproj, добавить `GroupListView.swift` в target если ещё не добавлен, собрать на симуляторе, повторить сценарии.

- [ ] **Step 4: Финальный commit (если по итогам smoke-теста нашлись косметические правки)**

При необходимости — commit. Если всё работает — пропустить.

---

## Self-review (после написания плана)

Проверка покрытия спека:
- ✅ Модель данных: Tasks 2, 3, 5
- ✅ Миграция v1→v2: Task 4
- ✅ FK enforcement: Task 1
- ✅ Repository слой (CardGroup + extension CardRepository): Tasks 6, 7, 8
- ✅ Транзакционное создание дефолтной группы: Task 9
- ✅ ViewModels: Tasks 10, 11, 12
- ✅ DI: Task 13
- ✅ Compose GroupListScreen: Task 14
- ✅ Compose CardListScreen с groupId: Task 15
- ✅ Compose SpaceListScreen навигация: Task 16
- ✅ Compose Study screens с StudyScope: Task 17
- ✅ iOS bridge: Task 19
- ✅ iOS GroupListView: Task 20
- ✅ iOS CardListView с groupId: Task 21
- ✅ iOS Study views с StudyScope: Task 22
- ✅ iOS SpaceListView навигация: Task 23
- ✅ Финальный билд + smoke: Tasks 25, 26

Внимание: иконки группы / drag-reorder / move card / поиск групп — Out of scope (явно в спеке).
