# Дизайн: Аккаунты и облачная синхронизация (бэкап-режим)

**Дата:** 2026-05-12
**Статус:** утверждено (пользователем) к реализации
**Объём:** общий design-doc для **6 подпроектов**, у каждого будет отдельная детальная спека + план реализации. Здесь зафиксированы только сквозные решения, протокол синка, общая схема и декомпозиция.

## Цель

Дать пользователю возможность создать аккаунт и хранить копию своих данных (Spaces, CardGroups, Cards, StudyResults) на сервере, чтобы:

- при потере/смене устройства восстановить всё, войдя в аккаунт;
- иметь защиту от случайных удалений (история снапшотов на сервере);
- продолжать работать **полностью офлайн** — аккаунт опционален, без интернета приложение работает как раньше.

**Это бэкап-режим**, а не полноценная multi-device live-синхронизация. Один аккаунт может быть открыт на нескольких устройствах, но при конкурентной работе побеждает последний снапшот (last-snapshot-wins). Конфликты не разрешаются построчно, tombstones и vector clocks не вводятся.

## Принятые решения (зафиксированы из брейншторма)

1. **Модель синка:** бэкап + восстановление. Не multi-device live-sync.
2. **Протокол:** полные snapshot'ы. Сервер хранит историю последних N снапшотов на пользователя (даёт откат).
3. **Аутентификация:** email + пароль. Без SMS, без OAuth, без magic-link. Восстановление пароля по email-ссылке (вторая итерация — допускается заглушка в MVP).
4. **Стек бэкенда:** Node.js + TypeScript + Express + Prisma ORM + PostgreSQL.
5. **Хостинг:** один VPS (Timeweb Cloud или Selectel) с Docker Compose: контейнер Node + контейнер PostgreSQL + Caddy (TLS).
6. **Триггер синка:** клиент пушит полный snapshot после каждого изменения с debounce 5 сек. При старте приложения — pull (если у сервера версия новее).
7. **Первый вход после регистрации:** локальные данные становятся первым снапшотом на сервере (автоматически, без диалога).
8. **Вход в существующий аккаунт при наличии локальных данных:** диалог-предупреждение «локальные данные будут заменены данными с сервера. Продолжить?». Без слияния — это бэкап-режим.
9. **Локальная схема почти не меняется.** Не вводим UUID, не переходим на soft delete, не добавляем `updatedAt` к каждой таблице. Достаточно одной новой таблицы `SyncState`.
10. **Сервер — глупый.** Хранит snapshot как JSON blob + метаданные. Не парсит структуру карточек, не считает SRS, не валидирует семантику.
11. **Размер снапшота:** в первой версии лимит 5 МБ. Этого хватает на десятки тысяч карточек (карточка ≈ 100 байт в JSON).
12. **Токены:** JWT access (15 мин) + refresh token (30 дней). Refresh хранится на сервере и может быть отозван.

## Архитектура

```
┌─────────────────────────────────┐         ┌────────────────────────────────┐
│  Клиент (KMP, 3 платформы)      │         │   Сервер (Node.js / TS)        │
│                                 │         │                                │
│  ┌───────────────────────────┐  │  HTTPS  │  ┌──────────────────────────┐  │
│  │ ViewModels                │  │ ◄─────► │  │ Express + middlewares    │  │
│  └────────────┬──────────────┘  │  JSON   │  │ (auth, rate limit, CORS) │  │
│               │                 │         │  └────────────┬─────────────┘  │
│  ┌────────────▼──────────────┐  │         │               │                │
│  │ AuthService               │  │         │  ┌────────────▼─────────────┐  │
│  │  - login / register       │  │         │  │ Routes: /auth, /sync     │  │
│  │  - token storage          │  │         │  └────────────┬─────────────┘  │
│  └────────────┬──────────────┘  │         │               │                │
│               │                 │         │  ┌────────────▼─────────────┐  │
│  ┌────────────▼──────────────┐  │         │  │ Prisma ORM               │  │
│  │ SyncManager               │  │         │  └────────────┬─────────────┘  │
│  │  - debounced push         │  │         │               │                │
│  │  - pull on app start      │  │         │  ┌────────────▼─────────────┐  │
│  │  - retry queue            │  │         │  │ PostgreSQL               │  │
│  │  - status flow            │  │         │  │  - users                 │  │
│  └────────────┬──────────────┘  │         │  │  - refresh_tokens        │  │
│               │                 │         │  │  - snapshots             │  │
│  ┌────────────▼──────────────┐  │         │  └──────────────────────────┘  │
│  │ Repositories              │  │         │                                │
│  │  (Space, Card, ...)       │  │         │  Docker Compose на одном VPS:  │
│  └────────────┬──────────────┘  │         │    - node:20-alpine            │
│               │                 │         │    - postgres:16-alpine        │
│  ┌────────────▼──────────────┐  │         │    - caddy:2 (TLS + reverse)   │
│  │ SQLDelight (local DB)     │  │         │                                │
│  └───────────────────────────┘  │         │                                │
└─────────────────────────────────┘         └────────────────────────────────┘
```

### Принципы

- **Сервер ничего не знает про SRS/изучение/группы.** Он принимает JSON-blob, проверяет схему версии и размер, сохраняет.
- **Клиент — единственный источник логики.** Сервер только хранит и отдаёт.
- **Snapshot атомарен.** Никаких частичных обновлений отдельных строк на серверной стороне.
- **Локальная БД остаётся самодостаточной.** Если сервер недоступен — приложение работает как раньше; SyncManager ставит изменения в очередь и пушит при появлении сети.

## Декомпозиция на подпроекты

Каждый подпроект получит **отдельную спеку** в `docs/superpowers/specs/` и отдельный план реализации. Здесь только список и порядок.

| # | Подпроект | Зависит от | Краткое описание |
|---|-----------|-----------|------------------|
| 1 | **backend-skeleton** | — | Express + Prisma + PostgreSQL + Docker Compose. Только `/healthz`, без бизнес-логики. Выкат на VPS, домен, TLS через Caddy. |
| 2 | **backend-auth** | 1 | Регистрация, логин, JWT access + refresh, middleware `requireAuth`. |
| 3 | **backend-sync** | 2 | `POST /sync` (push snapshot), `GET /sync/latest` (pull), `GET /sync/history`, `POST /sync/restore/{id}`. |
| 4 | **kmp-http-client** | — (можно параллельно с 1-3) | Ktor Client в `shared`, expect/actual для secure-token-storage (Android Keystore, iOS Keychain, Desktop OS keyring или зашифрованный файл). |
| 5 | **kmp-sync-manager** | 3, 4 | Локальный SyncManager: debounced push, pull, retry, status `Flow<SyncStatus>`. Новая таблица `SyncState`. |
| 6 | **ui-account** | 5 | Экраны Login / Register / AccountSettings на Compose (Android+Desktop) и SwiftUI (iOS). Индикатор синхронизации в Settings/AppBar. |

**Порядок реализации:** 1 → 2 → 3 → 4 → 5 → 6. Подпроект 4 можно делать параллельно с 1-3, так как он не зависит от API (только от его контракта, который зафиксирован в этой спеке).

## Серверная модель данных (PostgreSQL через Prisma)

```prisma
// schema.prisma — финальный набор моделей для MVP

model User {
  id            String   @id @default(uuid())
  email         String   @unique
  passwordHash  String
  createdAt     DateTime @default(now())
  refreshTokens RefreshToken[]
  snapshots     Snapshot[]
}

model RefreshToken {
  id         String   @id @default(uuid())
  userId     String
  user       User     @relation(fields: [userId], references: [id], onDelete: Cascade)
  tokenHash  String   @unique  // храним хеш, не сам токен
  expiresAt  DateTime
  revokedAt  DateTime?
  createdAt  DateTime @default(now())
  @@index([userId])
}

model Snapshot {
  id           String   @id @default(uuid())
  userId       String
  user         User     @relation(fields: [userId], references: [id], onDelete: Cascade)
  schemaVersion Int                       // версия схемы локальной БД клиента (см. ниже)
  payload      Json                       // тело snapshot
  sizeBytes    Int
  clientInfo   String?                    // "android-1.2.3" / "ios-1.2.3" / "desktop-1.2.3"
  createdAt    DateTime @default(now())
  @@index([userId, createdAt(sort: Desc)])
}
```

**Ретенция снапшотов:** хранить последние **10** на пользователя. При создании 11-го — удаляется самый старый (cron job или прямо в логике `POST /sync`, простой `DELETE` с подзапросом).

## Формат snapshot.payload

```json
{
  "schemaVersion": 2,
  "exportedAt": 1715520000000,
  "spaces": [
    { "id": 1, "name": "Английский", "nativeLang": "ru", "targetLang": "en", "createdAt": 1700000000000 }
  ],
  "cardGroups": [
    { "id": 1, "spaceId": 1, "name": "Общее", "createdAt": 1700000000001 }
  ],
  "cards": [
    {
      "id": 1, "spaceId": 1, "groupId": 1,
      "nativeWord": "яблоко", "targetWord": "apple", "hint": null,
      "intervalDays": 0.0, "easiness": 2.5, "repetitions": 0, "nextReviewAt": 0
    }
  ],
  "studyResults": [
    { "cardId": 1, "correct": true, "timestamp": 1715000000000 }
  ]
}
```

- `schemaVersion` — совпадает с версией SQLDelight-схемы клиента. Сервер не валидирует семантику, но сохраняет версию, чтобы при будущих миграциях понимать формат payload'а.
- ID остаются `Long` (как в локальной БД). Они уникальны **внутри одного аккаунта** — этого достаточно, потому что snapshot принадлежит конкретному `userId`. Коллизий с чужими данными нет.

## REST API

Все эндпоинты под `/api/v1`. JSON, UTF-8. Авторизация — `Authorization: Bearer <accessToken>`.

### Auth

| Метод | Путь | Тело запроса | Ответ |
|-------|------|--------------|-------|
| `POST` | `/auth/register` | `{ email, password }` | `201 { userId, accessToken, refreshToken }` |
| `POST` | `/auth/login` | `{ email, password }` | `200 { userId, accessToken, refreshToken }` |
| `POST` | `/auth/refresh` | `{ refreshToken }` | `200 { accessToken, refreshToken }` (rotation) |
| `POST` | `/auth/logout` | `{ refreshToken }` | `204` (revokes refresh token) |

### Sync

| Метод | Путь | Тело / параметры | Ответ |
|-------|------|------------------|-------|
| `POST` | `/sync` | `{ schemaVersion, payload, clientInfo }` | `201 { snapshotId, createdAt }` |
| `GET` | `/sync/latest` | `?sinceTimestamp=<ms>` (опционально) | `200 { snapshot } \| 304 Not Modified` |
| `GET` | `/sync/history` | — | `200 [{ id, createdAt, sizeBytes, clientInfo }]` (последние 10) |
| `POST` | `/sync/restore/:snapshotId` | — | `200 { snapshot }` (отдаёт payload указанного снапшота; клиент сам применяет) |

### Коды ошибок

| Код | Когда |
|-----|-------|
| `400` | Невалидное тело (zod-схема), email не email, пароль < 8 символов, snapshot > 5 МБ |
| `401` | Нет токена / токен истёк / неверный пароль при логине |
| `403` | Refresh token revoked |
| `404` | Snapshot c таким id не найден или принадлежит другому юзеру |
| `409` | Email уже занят |
| `413` | Snapshot превышает лимит (5 МБ) |
| `429` | Rate limit (см. ниже) |

### Rate limiting

`express-rate-limit` на `/auth/*`: 10 запросов / минута / IP. На `/sync`: 60 запросов / минута / пользователь.

## Аутентификация (детали)

- **Пароли:** хешируются `argon2id` (пакет `argon2`). Параметры по умолчанию из библиотеки — этого достаточно.
- **Access token:** JWT (HS256), payload `{ userId, exp }`, время жизни 15 минут. Секрет — env-переменная `JWT_SECRET` (32+ байта рандома).
- **Refresh token:** не JWT, а cryptographically random 32 байта (base64url). На сервере хранится `tokenHash` (SHA-256 от токена). Время жизни 30 дней. При каждом `POST /auth/refresh` — старый refresh отзывается (`revokedAt = now`), выдаётся новый (rotation).
- **На клиенте:** оба токена хранятся в **secure storage** платформы:
  - Android: `EncryptedSharedPreferences` через `androidx.security:security-crypto`.
  - iOS: Keychain (через KMP-обёртку или прямой вызов `SecItemAdd` в `iosMain`).
  - Desktop: OS keyring через библиотеку `com.github.javakeyring:java-keyring` или, как фолбэк, зашифрованный файл (`~/.transcard/secrets.bin`, ключ выводится из имени пользователя ОС). **Решение по Desktop откладывается в подпроект 4** — там будет отдельный мини-брейншторм.

## Изменения локальной схемы (минимальные)

### Новая таблица `SyncState`

Файл: `shared/src/commonMain/sqldelight/com/transcard/db/SyncState.sq`

В таблице хранятся **только метаданные синка**. Access/refresh-токены сюда **не пишутся** — SQLite не зашифрована (на рутованном Android / jailbroken iOS читается без пароля), поэтому секреты живут в secure storage платформы (`EncryptedSharedPreferences` / Keychain / OS keyring). В `SyncState` остаются `userId` и `email` — этого хватает для отображения "Вы вошли как user@example.com" и проверки факта логина без обращения к secure storage.

```sql
CREATE TABLE SyncState (
    id INTEGER PRIMARY KEY CHECK (id = 1),   -- singleton row
    userId TEXT,                              -- null = не залогинен
    email TEXT,
    lastPushedAt INTEGER NOT NULL DEFAULT 0,
    lastPulledAt INTEGER NOT NULL DEFAULT 0,
    lastServerSnapshotAt INTEGER NOT NULL DEFAULT 0,
    pendingPush INTEGER NOT NULL DEFAULT 0    -- 1 = есть несинхронизированные изменения
);

INSERT OR IGNORE INTO SyncState(id) VALUES (1);
```

### Миграция SQLDelight (v2 → v3)

После миграции групп карточек (v1 → v2 уже описана в `2026-05-12-card-groups-design.md`) добавится миграция v2 → v3:

Файл: `shared/src/commonMain/sqldelight/com/transcard/db/migrations/2.sqm`

```sql
CREATE TABLE SyncState (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    userId TEXT,
    email TEXT,
    lastPushedAt INTEGER NOT NULL DEFAULT 0,
    lastPulledAt INTEGER NOT NULL DEFAULT 0,
    lastServerSnapshotAt INTEGER NOT NULL DEFAULT 0,
    pendingPush INTEGER NOT NULL DEFAULT 0
);

INSERT OR IGNORE INTO SyncState(id) VALUES (1);
```

`Space`, `Card`, `CardGroup`, `StudyResult` — без изменений.

## Логика синхронизации (на клиенте)

### Состояния

```kotlin
sealed class SyncStatus {
    object Idle : SyncStatus()           // ничего не делает
    object Pushing : SyncStatus()
    object Pulling : SyncStatus()
    data class Error(val message: String, val recoverable: Boolean) : SyncStatus()
    object NotAuthenticated : SyncStatus() // в аккаунт не вошёл
}
```

### Push (после каждого изменения)

1. Любой `insert/update/delete` в Space/CardGroup/Card/StudyResult ставит `SyncState.pendingPush = 1`.
2. SyncManager слушает изменения и через **debounce 5 секунд** вызывает `push()`.
3. `push()` собирает полный снапшот из всех таблиц → `POST /sync` → при успехе ставит `pendingPush = 0`, `lastPushedAt = now`.
4. При сетевой ошибке — `pendingPush` остаётся `1`, retry с экспоненциальной задержкой (5 сек → 30 сек → 5 мин → стоп до следующего изменения или старта приложения).

### Pull (при старте приложения / при логине)

1. При запуске приложения, если есть токен: `GET /sync/latest?sinceTimestamp=<lastServerSnapshotAt>`.
2. Если `200 OK` — пришёл более новый снапшот → **полностью заменить** локальные данные.
3. Если `304 Not Modified` — ничего не делаем.
4. **Перед** заменой: если `pendingPush = 1` (есть локальные несинхронизированные изменения), **сначала push**, потом pull. Это критично, иначе локальные правки потеряются. Если push фейлится — pull блокируется до успешного push (показываем юзеру варнинг "у вас есть несохранённые изменения, нет связи").

### Псевдокод push

```kotlin
suspend fun push() {
    if (!isAuthenticated()) return
    if (syncState.pendingPush == 0L) return
    statusFlow.emit(SyncStatus.Pushing)
    val payload = buildSnapshot()  // selectAll из 4 таблиц
    try {
        val resp = api.postSync(SnapshotRequest(SCHEMA_VERSION, payload, clientInfo()))
        syncStateDao.markPushed(now(), resp.snapshotId)
        statusFlow.emit(SyncStatus.Idle)
    } catch (e: Throwable) {
        statusFlow.emit(SyncStatus.Error(e.message ?: "push failed", recoverable = true))
        scheduleRetry()
    }
}
```

### Псевдокод pull

```kotlin
suspend fun pullOnStart() {
    if (!isAuthenticated()) return
    if (syncState.pendingPush == 1L) {
        val ok = pushNow(); if (!ok) return // не перетираем локальные
    }
    statusFlow.emit(SyncStatus.Pulling)
    val resp = api.getLatest(sinceTimestamp = syncState.lastServerSnapshotAt)
    when (resp) {
        is NotModified -> statusFlow.emit(SyncStatus.Idle)
        is Snapshot -> {
            applySnapshotLocally(resp)
            syncStateDao.markPulled(now(), resp.createdAt)
            statusFlow.emit(SyncStatus.Idle)
        }
    }
}
```

### Применение снапшота (replace local data)

В одной транзакции SQLDelight:
1. `DELETE FROM StudyResult;`
2. `DELETE FROM Card;`
3. `DELETE FROM CardGroup;`
4. `DELETE FROM Space;`
5. `INSERT` по очереди: Spaces → CardGroups → Cards → StudyResults (порядок важен — FK).

`AUTOINCREMENT`-id сохраняются «как есть» (вставка с явным id) — это поддерживается SQLite, см. SQLDelight docs.

## Изменения слоёв на клиенте

### `domain`

- Новый модуль `domain/sync/`:
  - `SyncRepository` интерфейс: `observeStatus(): Flow<SyncStatus>`, `pushNow(): Result<Unit>`, `pullNow(): Result<Unit>`.
  - `AuthRepository` интерфейс: `register(email, password)`, `login(email, password)`, `logout()`, `observeAuthState(): Flow<AuthState>`.
  - Модели: `AuthState(userId: String?, email: String?)`, `SyncStatus` (см. выше).

### `data`

- `data/remote/ApiClient` — Ktor Client, base URL из BuildConfig (Android) / Bundle (iOS) / системного properties (Desktop). Конфигурация:
  - JSON через `kotlinx.serialization`.
  - `Auth` plugin Ktor для автоматической подстановки `Authorization` header и refresh при `401`.
  - Logging plugin только в debug.
- `data/remote/dto/` — DTO для `/auth/*` и `/sync/*`. Отдельно от domain-моделей.
- `data/repository/AuthRepositoryImpl` и `SyncRepositoryImpl`.
- `data/storage/SecureTokenStorage` (expect/actual):
  - `androidMain`: `EncryptedSharedPreferences`.
  - `iosMain`: Keychain через CoreFoundation.
  - `desktopMain`: см. подпроект 4 (решение откладывается).

### `presentation`

- Новые ViewModels:
  - `LoginViewModel`, `RegisterViewModel` — состояния формы, валидация, вызов `AuthRepository`.
  - `AccountViewModel` — отображает текущий статус аккаунта и `SyncStatus`, кнопки «Выйти», «Синхронизировать сейчас», «История снапшотов».
- В существующие репозитории (`SpaceRepositoryImpl`, `CardRepositoryImpl`, `CardGroupRepositoryImpl`) добавить вызов `syncTrigger.markDirty()` в каждом `insert/update/delete`. Можно вынести в декоратор, но проще — явные вызовы (~10 строк суммарно).

### DI

В `shared/src/commonMain/kotlin/com/transcard/di/Modules.kt`:
```kotlin
single { ApiClient(BASE_URL) }
single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
single<SyncRepository> { SyncRepositoryImpl(get(), get(), get(), get()) }
single { SyncTrigger(get()) }  // debounce-источник
factory { LoginViewModel(get()) }
factory { RegisterViewModel(get()) }
factory { AccountViewModel(get(), get()) }
```

Платформенные модули (`platformModule()`) регистрируют `SecureTokenStorage`.

## UI / Навигация

### Compose (Android + Desktop)

Точка входа в аккаунт — новый пункт в текущем `SettingsScreen` (если его нет — добавляется в `TopAppBar` `SpaceListScreen` иконка ⚙).

```
SpaceListScreen
    └─ Settings / AppBar icon → AccountScreen
                                  │
                  ┌───────────────┴───────────────┐
                  │ Не залогинен                  │ Залогинен
                  ▼                               ▼
            кнопки                        ┌── Email пользователя
            ┌──────────────┐              │   Статус: "Синхронизировано
            │ Войти        │              │            5 минут назад"
            │ Создать      │              │   ┌─────────────────────┐
            │ аккаунт      │              │   │ Синхронизировать    │
            └──────────────┘              │   │ сейчас              │
                                          │   └─────────────────────┘
                                          │   ┌─────────────────────┐
                                          │   │ История снапшотов   │
                                          │   └─────────────────────┘
                                          │   ┌─────────────────────┐
                                          │   │ Выйти               │
                                          │   └─────────────────────┘
```

Новые экраны:
- `presentation/screen/LoginScreen.kt`
- `presentation/screen/RegisterScreen.kt`
- `presentation/screen/AccountScreen.kt`
- `presentation/screen/SnapshotHistoryScreen.kt`

Индикатор синхронизации (опционально для MVP): маленькая иконка-облако в `TopAppBar` главного экрана с тремя состояниями:
- 🟢 — синхронизировано (Idle, pendingPush=0)
- 🟡 — пушит/пулит
- 🔴 — ошибка

### SwiftUI (iOS)

- `iosApp/iosApp/Screens/AccountScreen.swift`
- `iosApp/iosApp/Screens/LoginScreen.swift`
- `iosApp/iosApp/Screens/RegisterScreen.swift`
- `iosApp/iosApp/Screens/SnapshotHistoryScreen.swift`
- `iosApp/iosApp/Bridges/KoinResolver.swift` — добавить `func loginViewModel()`, `func registerViewModel()`, `func accountViewModel()`.
- Через `FlowObserver` — `Flow<SyncStatus>` и `Flow<AuthState>`. Эти типы — sealed классы, поэтому `as!`-каст потребует аккуратности (паттерн `KStudyDirection` уже применяется для sealed-классов в проекте).

Тексты на русском.

## Хостинг и деплой (детали)

### Провайдер

**Timeweb Cloud** (на старте). Тариф: самый дешёвый VPS (~300 ₽/мес, 1 vCPU / 2 ГБ RAM / 30 ГБ NVMe) для одного пользователя. На вырост — Selectel.

Альтернативы: REG.RU Cloud, Beget Cloud — аналогичная цена. **Vendor lock-in отсутствует** благодаря Docker.

### Структура репозитория

Бэкенд живёт в **отдельном Git-репозитории** `transcard-server` (не в KMP-репо). Связь между ними — только через JSON-контракты API и фиксированный `schemaVersion`. Так чище для CI/CD и проще для деплоя.

```
transcard-server/
├── docker-compose.yml
├── Caddyfile
├── .env.example
├── prisma/
│   └── schema.prisma
├── src/
│   ├── index.ts
│   ├── routes/
│   │   ├── auth.ts
│   │   └── sync.ts
│   ├── middleware/
│   │   ├── auth.ts
│   │   └── rateLimit.ts
│   └── lib/
│       ├── jwt.ts
│       └── passwords.ts
└── package.json
```

### docker-compose.yml (скелет)

```yaml
services:
  app:
    build: .
    environment:
      DATABASE_URL: postgres://transcard:${DB_PASSWORD}@db:5432/transcard
      JWT_SECRET: ${JWT_SECRET}
    depends_on: [db]

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: transcard
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: transcard
    volumes:
      - pgdata:/var/lib/postgresql/data

  caddy:
    image: caddy:2-alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
    depends_on: [app]

volumes:
  pgdata:
  caddy_data:
```

### Деплой

В первой итерации — **ручной**: `git pull && docker compose up -d --build` по SSH. CI/CD (GitHub Actions / Gitea Actions) — после стабилизации MVP, в отдельный подпроект.

### Бэкапы PostgreSQL

`pg_dump` по cron-расписанию (нативный systemd-таймер на хосте, не в контейнере), архив раз в сутки в локальный каталог `/var/backups/transcard`. Загрузка в S3-совместимое хранилище (Timeweb Cloud Storage / Selectel Object Storage) — допускается отложить до второго подпроекта или сделать ручным.

## Обработка ошибок

### Сетевые

- **Нет интернета:** SyncManager ставит `pendingPush=1`, статус `Error(recoverable=true)`. Приложение продолжает работать офлайн. При появлении сети — авто-retry.
- **5xx от сервера:** retry с экспоненциальной задержкой, максимум 3 раза, потом стоп до следующего изменения / старта.
- **401 Unauthorized:** Ktor Auth-plugin автоматически делает refresh. Если refresh тоже фейлится с 401/403 — логаут (очистка `SyncState.userId` и токенов из secure storage), переход на `LoginScreen`.

### Размер snapshot'а

Если на клиенте payload > 5 МБ — это означает, что у юзера ~50 000+ карточек. На MVP считаем это нереалистичным сценарием. SyncManager при превышении лимита показывает в Settings предупреждение "слишком много данных для облачной синхронизации" и **прекращает push**. На сервере 413 — повторный сигнал тому же UI.

### Валидация на сервере

- `zod` для всех тел запросов. Невалидное → 400 с понятным сообщением.
- На уровне Express — `body-parser` с лимитом 6 МБ (чтобы 5-мегабайтные snapshot'ы проходили + overhead).

### Безопасность

- HTTPS-only (Caddy выдаёт сертификаты Let's Encrypt автоматически).
- Пароль не логируется ни на клиенте, ни на сервере.
- JWT_SECRET в env, не в коде.
- В Prisma включить `previewFeatures = ["fullTextSearch"]` не нужно. Никаких raw SQL не делаем.
- `helmet` middleware на сервере.

## Тестирование

В проекте тестов сейчас нет (KISS). Не добавляем юнит-тестов в рамках этого этапа. Верификация — ручная по подпроектам.

**Что обязательно проверить вручную:**

- Регистрация → автозалив локальных данных → выход → удаление приложения → установка → логин → данные восстановились.
- Создание карточки → ожидание 5 сек → проверка `GET /sync/latest` через curl/Postman.
- Отключить сеть → создать карточку → включить сеть → push прошёл.
- Создать карточку на устройстве A → войти на устройстве B → данные с A видны на B (после pull).
- Откат: создать карточку → ждать sync → удалить карточку → ждать sync → восстановить предыдущий snapshot через `POST /sync/restore/{id}`.
- Миграция SQLDelight v2 → v3 на реальном устройстве с существующими данными.

## Вне scope

- **Multi-device live sync** (одновременная работа на 2+ устройствах с авто-мержем) — это другой проект, потребует UUID, soft delete, vector clocks.
- **Шаринг колод** между пользователями.
- **OAuth** (VK ID, Yandex ID, Google).
- **SMS-верификация**.
- **2FA**.
- **Push-уведомления** (например, "не забудьте позаниматься").
- **Веб-клиент** на claude.ai/code-стиле или иной.
- **End-to-end encryption** snapshot'ов (сейчас сервер видит plain JSON). Рассмотреть в будущем — нужен ключ, выводимый из пароля + соль (zero-knowledge).
- **Сжатие** snapshot'а (gzip над JSON). Не критично при 5 МБ, добавим если упрёмся.
- **CI/CD** для бэкенда. Сначала — ручной деплой.
- **Метрики и мониторинг** (Prometheus, Grafana). Сначала — `docker logs`.

## Точки риска

1. **iOS Keychain через KMP** — самый ломкий бридж. Может потребоваться написать Swift-обёртку в `iosApp/Bridges/` и вызывать её из `iosMain` через `interop`. Рассмотреть библиотеку `multiplatform-settings-no-arg` / `multiplatform-settings` от Russhwolf, у неё есть `KeychainSettings` для iOS и `EncryptedSettings` для Android — это упростит подпроект 4.

2. **Размер snapshot'а растёт линейно с числом карточек.** При 100 000 карточек payload ≈ 10 МБ. Это удар по трафику мобильника. **Митигация:** для MVP лимит 5 МБ, для будущего — переход на инкрементальный sync (подход B из брейншторма) или сжатие gzip.

3. **AUTOINCREMENT ID + восстановление snapshot.** Если на новом устройстве локальная БД пустая и приходит snapshot с id=5,7,9 — нужно вставлять с явными id. SQLite позволяет (`INSERT INTO ... (id, ...) VALUES (5, ...)`), и AUTOINCREMENT сам обновит `sqlite_sequence`. Это уже учтено в логике applySnapshot, но точку проверить вручную.

4. **`pendingPush=1` после установки приложения.** Если юзер ставит обновление, локальная БД мигрирует на v3, `SyncState` создаётся пустой (`userId=null`, `pendingPush=0`). Это правильно — без аккаунта пушить некуда. После логина выставляется `pendingPush=1` принудительно, чтобы первый push отправил всё на сервер.

5. **Конфликт push и pull при старте.** Если на устройстве A локально есть несинхронизированные изменения (`pendingPush=1`), а на сервере уже более новый snapshot (с устройства B) — простая «push потом pull» вызовет ситуацию: push A перетирает B, потом pull возвращает то, что только что запушили. **Решение:** при `pendingPush=1` и `lastServerSnapshotAt > lastPushedAt` показываем юзеру выбор: «На сервере более свежие данные. Использовать серверные (локальные изменения потеряются) или локальные (серверные данные будут перезаписаны)?». Это последний рубеж защиты от потери данных в бэкап-режиме.

6. **Email-провайдер для восстановления пароля.** В MVP можно отложить (показывать просто "восстановление пока не работает, напишите автору"). Когда нужно — SMTP через Yandex.Почту (бесплатно для своего домена) или Mailgun.

7. **Российская специфика.** Платёжные карты для оплаты VPS — РФ-карты Мир/Visa/Mastercard работают у Timeweb, Selectel, Beget. Домены `.ru`/`.рф` — через REG.RU. SSL — Let's Encrypt работает на РФ-IP без проблем (Caddy сам всё делает).

## Что писать дальше

После утверждения этой общей спеки — **6 детальных спек по подпроектам** (в порядке реализации):

1. `2026-05-12-backend-skeleton-design.md`
2. `2026-05-12-backend-auth-design.md`
3. `2026-05-12-backend-sync-design.md`
4. `2026-05-12-kmp-http-client-design.md`
5. `2026-05-12-kmp-sync-manager-design.md`
6. `2026-05-12-ui-account-design.md`

И только потом — implementation plans для каждой.
