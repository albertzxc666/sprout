# Дизайн: Группы карточек внутри пространств

**Дата:** 2026-05-12
**Статус:** утверждено (пользователем) к реализации
**Объём:** только фича групп, offline. Аккаунты/синхронизация/бэкенд — отдельный спек.

## Цель

Добавить в TransCard слой группировки карточек внутри пространства. Пользователь создаёт пространство (например, «Английский»), внутри — группы по темам («Еда», «Одежда», «Глаголы»). Каждая карточка принадлежит ровно одной группе. Изучение возможно как на уровне всего пространства (микс всех групп), так и на уровне одной группы.

## Принятые решения (зафиксированы из брейншторма)

1. **Отношение карточки и группы:** 1 карточка → ровно 1 группа. `Card.groupId NOT NULL`.
2. **Иерархия групп:** плоский список (без подгрупп).
3. **Область учёбы:** доступна и из пространства (все карточки пространства), и из конкретной группы.
4. **Удаление группы:** каскадное удаление карточек с подтверждением (диалог: «Удалить «X» и N карточек?»). Совпадает с поведением удаления пространства.
5. **Дефолтная группа:** при создании нового пространства транзакционно создаётся одна группа `«Общее»`. Она ничем не отличается от пользовательских — её можно переименовать и удалить. Никакого `isSystem`‑флага.
6. **Навигация:** отдельный экран `GroupListScreen` между `SpaceListScreen` и `CardListScreen`. `CardListScreen` начинает работать в контексте группы (получает `groupId` вместо `spaceId`).

## Модель данных

### Новая таблица `CardGroup`

Файл: `shared/src/commonMain/sqldelight/com/transcard/db/CardGroup.sq`

```sql
CREATE TABLE CardGroup (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    spaceId INTEGER NOT NULL,
    name TEXT NOT NULL,
    createdAt INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (spaceId) REFERENCES Space(id) ON DELETE CASCADE
);
CREATE INDEX cardgroup_space_idx ON CardGroup(spaceId);
```

Запросы:
- `selectBySpace(spaceId)` — список групп пространства, упорядочен по `createdAt ASC, id ASC`.
- `selectById(id)`
- `insert(spaceId, name, createdAt)`
- `update(name, id)` — переименование
- `deleteById(id)` — каскад удалит карточки группы
- `countBySpace(spaceId)`

### Изменения в `Card.sq`

Добавляется колонка `groupId INTEGER NOT NULL` с внешним ключом и индексом. Старая колонка `spaceId` сохраняется (так быстрее фильтровать на уровне пространства без JOIN'ов и проще делать инвариант «карточка ↔ пространство»).

Новые запросы:
- `selectByGroup(groupId)`
- `selectDueByGroup(groupId, now)`
- `countByGroup(groupId)`
- `countDueByGroup(groupId, now)`
- `countAllByGroup` — для агрегатов на `GroupListScreen` (по аналогии с `countAllBySpace`)
- `countDueAllByGroup`
- `selectGardenStagesByGroup` — для мини‑прогресса в `GroupListScreen`

Существующие `*BySpace` запросы сохраняются — нужны для study на уровне пространства.

`insert` обновляется:
```sql
insert:
INSERT INTO Card(spaceId, groupId, nativeWord, targetWord, hint)
VALUES (?, ?, ?, ?, ?);
```

### Domain‑модели

`shared/src/commonMain/kotlin/com/transcard/domain/model/Models.kt`:

```kotlin
data class CardGroup(
    val id: Long,
    val spaceId: Long,
    val name: String,
    val createdAt: Long
)
```

В `data class Card` добавляется `val groupId: Long`.

## Миграция (схема v1 → v2)

SQLite не позволяет добавить `NOT NULL` колонку без default к существующей таблице. Делаем пересоздание таблицы `Card` в миграции.

Файл: `shared/src/commonMain/sqldelight/com/transcard/db/migrations/1.sqm`

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
SELECT id, 'Общее', strftime('%s','now') * 1000 FROM Space;

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

В `shared/build.gradle.kts` для SQLDelight включить:
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

## Архитектура слоёв

### Repository

Новый интерфейс в `domain/repository/Repositories.kt`:

```kotlin
interface CardGroupRepository {
    fun observeBySpace(spaceId: Long): Flow<List<CardGroup>>
    suspend fun getById(id: Long): CardGroup?
    suspend fun create(spaceId: Long, name: String): Long
    suspend fun rename(id: Long, name: String)
    suspend fun delete(id: Long)
}
```

Имплементация: `data/repository/CardGroupRepositoryImpl` — обёртка над сгенерированным `CardGroupQueries`. `observeBySpace` использует `asFlow().mapToList(dispatcher)` как в существующих репозиториях.

### Атомарное создание пространства

В существующий `SpaceRepository.create` добавляется создание дефолтной группы в той же транзакции SQLDelight:

```kotlin
override suspend fun create(name: String, ...): Long = withContext(Dispatchers.IO) {
    db.transactionWithResult {
        spaceQueries.insert(name, ...)
        val spaceId = spaceQueries.lastInsertedId().executeAsOne()
        cardGroupQueries.insert(spaceId, "Общее", nowMillis())
        spaceId
    }
}
```

`SpaceRepository.create` остаётся с прежней сигнатурой (возвращает `spaceId`) — вызывающие коды не меняются. Дефолтный `groupId` запрашивается через `CardGroupRepository.observeBySpace(spaceId).first()` там, где он нужен (например, на пустом `GroupListScreen` сразу после создания).

### `CardRepository`

Новые методы:
- `observeByGroup(groupId): Flow<List<Card>>`
- `countByGroup(groupId): Flow<Long>`
- `countDueByGroup(groupId, now): Flow<Long>`
- `gardenStagesByGroup(groupId): Flow<List<GroupGardenRow>>` (id группы + stage + count) — для агрегатов в списке групп.

`insert(spaceId, groupId, nativeWord, targetWord, hint)` — расширяет существующий метод. Все существующие `*BySpace` методы остаются (нужны для study на уровне пространства).

### Use cases

- `CreateGroupUseCase(spaceId, name) -> Long` — валидация имени (не пустое, trim, лимит 50 символов), создание.
- `RenameGroupUseCase(groupId, name)` — та же валидация.
- `DeleteGroupUseCase(groupId)` — просто прокси на репозиторий; UI отвечает за подтверждение.

### ViewModels

- **Новый `GroupListViewModel(spaceId: Long)`** в `presentation/viewmodel/`:
  - State: `GroupListUiState(space: Space?, items: List<GroupCardItem>, isLoading, totalDue: Long)`.
  - `GroupCardItem(group: CardGroup, cardsCount: Long, dueCount: Long, gardenStages: Map<GardenStage, Int>)`.
  - Actions: `createGroup(name)`, `renameGroup(id, name)`, `deleteGroup(id)`.
  - Источник: `combine(observeBySpace, countAllByGroup, countDueAllByGroup, gardenStagesByGroup)`.
- **`CardListViewModel`** меняет конструктор: вместо `spaceId: Long` принимает `groupId: Long`. Все запросы — `*ByGroup`. `addCard` берёт `groupId` из конструктора. Для отображения родительского пространства догружает `space` через `SpaceRepository.getById`.
- **`StudySetupViewModel` / `StudyViewModel`** расширяются: вместо `spaceId` принимают `scope`:
  ```kotlin
  sealed class StudyScope {
      data class Space(val spaceId: Long) : StudyScope()
      data class Group(val groupId: Long) : StudyScope()
  }
  ```
  Внутри VM по типу скоупа выбирается соответствующий запрос (`selectDueBySpace` vs `selectDueByGroup`).

### DI

В `shared/src/commonMain/kotlin/com/transcard/di/Modules.kt`:
- `singleOf(::CardGroupRepositoryImpl) { bind<CardGroupRepository>() }`
- `factory { params -> GroupListViewModel(params.get(), get(), get()) }`
- `CardListViewModel` factory правится: первый параметр теперь `groupId`.
- `StudySetupViewModel` / `StudyViewModel` factories принимают `StudyScope`.

## UI / Навигация

### Compose (Android + Desktop)

Новый файл: `shared/src/commonMain/kotlin/com/transcard/presentation/screen/GroupListScreen.kt`.

```
SpaceListScreen
    └─ tap space → GroupListScreen(spaceId)
                    ├─ TopAppBar: title = space.name, back button
                    ├─ Header‑секция: "Изучать всё пространство"
                    │    (видна только если totalDue > 0)
                    ├─ LazyColumn:
                    │    └─ AppCard на каждую группу:
                    │         ├─ name (Text)
                    │         ├─ "<cardsCount> карточек · <dueCount> к повторению"
                    │         ├─ компактная garden‑полоса (как на SpaceList)
                    │         └─ overflow menu (⋮): Переименовать, Удалить
                    ├─ EmptyState если групп нет (не должно случаться благодаря "Общее", но на всякий)
                    ├─ FAB "+" → AlertDialog с OutlinedTextField (name)
                    └─ Диалог удаления: "Удалить «X» и N карточек?" (с подсчётом)

    tap group → CardListScreen(groupId)
                    ├─ TopAppBar: title = group.name,
                    │    subtitle = space.name, back button
                    ├─ (всё содержимое как сейчас, фильтр по groupId)
                    └─ "Изучать эту группу" — существующая кнопка study,
                         но скоуп = StudyScope.Group(groupId)
```

Навигация Voyager: `GroupListScreen(spaceId: Long) : Screen` — Voyager поддерживает параметры через конструктор. `CardListScreen` правится — теперь `CardListScreen(groupId: Long)`. Переход из `SpaceListScreen`: `navigator.push(GroupListScreen(space.id))`.

`StudySetupScreen` / `StudyScreen` принимают `StudyScope` через конструктор.

### iOS (SwiftUI)

- Новый `iosApp/iosApp/Screens/GroupListScreen.swift` + `GroupListObservableObject` (обёртка через `FlowObserver` — паттерн из существующих экранов).
- `iosApp/iosApp/Screens/CardListScreen.swift` — конструктор меняется на `init(groupId: Int64)`.
- Бридж `iosApp/iosApp/Bridges/KoinResolver.swift`: добавляется `func cardGroupRepository() -> CardGroupRepository`, `func groupListViewModel(spaceId: Int64) -> GroupListViewModel`. Параметры в Koin передаются через `parametersOf(spaceId)` — паттерн уже есть в проекте.
- `FlowObserver`: добавить `as!`‑каст для `Flow<List<CardGroup>>` → `NSArray` → `[CardGroup]`. Внимательно — типизация generics через iOS bridge ломкая, может потребовать тестового прогона.
- Навигация iOS: вставить `GroupListScreen` между `SpaceListScreen` и `CardListScreen` в `NavigationStack`/`NavigationLink`.

## Обработка ошибок

- Валидация имени группы: trim, непустое, ≤ 50 символов. При пустом — кнопка «Создать» неактивна (как сейчас в диалоге создания пространства).
- Дубликаты имён внутри пространства не запрещаются (соответствует поведению пространств).
- Удаление последней группы пространства разрешено — пространство просто остаётся пустым. (Альтернатива «нельзя удалить последнюю» отклонена ради простоты UX и согласованности с правилом «дефолтная группа = обычная группа».)

## Тестирование

В проекте сейчас тестов нет (KISS, см. `.claude/CLAUDE.md`). Не добавляем юнит‑тестов в рамках этой фичи. Верификация — ручная:
- `./gradlew build` собирает все таргеты (Android, Desktop, iosX64/Arm64/SimulatorArm64).
- Прогон на Android: создание группы, переименование, удаление с подсчётом, learn space vs learn group, миграция из v1 (поставить APK поверх v1‑данных).
- Прогон на Desktop: те же сценарии.
- Прогон на iOS: проверка типизации `Flow<List<CardGroup>>` через FlowObserver, навигация.

## Вне scope

- Drag‑to‑reorder групп.
- Move card между группами через UI (в первой версии — нет; технически достижимо через update `Card.groupId`, но UX откладываем).
- Импорт/экспорт групп, шаринг.
- Аккаунты, синхронизация, бэкенд — отдельный спек.
- Поиск/фильтрация групп.
- Цвета/иконки групп.

## Точки риска

1. **`PRAGMA foreign_keys=ON` не выставлен на Android и iOS драйверах** (только на Desktop). Текущее удаление `Space.deleteById(...)` опирается на `ON DELETE CASCADE`, но FK на Android/iOS могут не enforced'иться, оставляя orphan‑карточки. С добавлением `CardGroup` (двойной каскад Space → CardGroup → Card → StudyResult) риск растёт. **Действие:** включить `foreign_keys` в `DatabaseDriverFactory.android.kt` (через `AndroidSqliteDriver.Callback`) и `DatabaseDriverFactory.ios.kt` (через `NativeSqliteDriver(... onConfiguration = ...)` или `extendedConfig`). План должен это учесть.
2. **iOS bridge типизация** для `List<CardGroup>` — самая ломкая часть. Если `as!`‑касты падают на iOS, может потребоваться явная оборачивающая Swift‑функция в `iosMain` (паттерн уже применяется к `Card` и `Space`).
3. **Миграция при больших объёмах данных** — `INSERT ... SELECT` на тысячах карточек на старом Android может быть медленным. Для пользователей с < 1000 карточек неактуально.
4. **Параметризация Voyager Screen** — если паттерн Voyager `koinScreenModel { parametersOf(...) }` в проекте ещё не использовался, надо проверить, что параметры корректно прокидываются. (В коде `CardListScreen` это уже есть — см. import `org.koin.core.parameter.parametersOf`.)
