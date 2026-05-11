# TransCard — заметки для Claude Code

Кросс-платформенное приложение для изучения иностранных слов через карточки.
Kotlin Multiplatform + Compose Multiplatform (Android, Desktop) + SwiftUI (iOS). Всё работает локально, без сети. Подсказки перевода — из bundled-словарей в `shared/src/commonMain/composeResources/files/dictionaries/`.

## Стек

- Kotlin 2.1.0 / JDK 17 / Gradle 8.10 (wrapper)
- Compose Multiplatform 1.7.3 (Android + Desktop)
- SwiftUI на iOS (минимум iOS 15)
- SQLDelight 2.0.2 (SQLite на всех платформах)
- Koin 3.5.6 (DI), Voyager 1.1.0-beta02 (навигация)
- Coroutines 1.9.0 + StateFlow
- kotlinx.serialization 1.7.3 (парсинг словарных JSON-файлов)

Версии в `gradle/libs.versions.toml` — менять только там, не хардкодить в `build.gradle.kts`.

## Структура модулей

```
shared/        ← KMP: вся бизнес-логика + Compose UI
  commonMain/kotlin/com/transcard/
    data/         репозитории, фабрика драйвера БД
    domain/       модели, интерфейсы репозиториев, use cases
    presentation/ ViewModels, theme, components, screen/
    di/           Koin: sharedModule + expect platformModule()
    App.kt        корневой Composable (Navigator → SpaceListScreen)
  commonMain/sqldelight/com/transcard/db/   .sq схемы (Space, Card, StudyResult)
  androidMain/  AndroidSqliteDriver + Koin Android module
  desktopMain/  JdbcSqliteDriver (~/.transcard/transcard.db)
  iosMain/      NativeSqliteDriver + Flow→Swift bridge

androidApp/    Application + ComponentActivity, вызывает shared App()
desktopApp/    Compose Desktop entry, вызывает shared App()
iosApp/iosApp/ SwiftUI: 5 экранов + Bridges/ (FlowObserver, KoinResolver)
```

Архитектура — clean: `domain` ничего не знает о `data` и `presentation`. ViewModel → UseCase/Repository (интерфейс из `domain`) → SQLDelight Queries.

## Правила работы с кодом

- **Не рефакторить чужое.** Меняй только то, что просят. Стиль по `.editorconfig`/Kotlin official.
- **expect/actual** — если добавляешь платформозависимость, объяви `expect` в `commonMain`, реализуй во всех трёх таргетах (`androidMain`, `desktopMain`, `iosMain`). Не оставляй пустые actual'ы.
- **DI:** новые ViewModel/Repository/UseCase регистрируй в `shared/.../di/Modules.kt`. Платформенные зависимости — в `expect fun platformModule()`.
- **БД:** новая таблица = новый `.sq` файл в `commonMain/sqldelight/com/transcard/db/`. После сборки в `shared/build/generated/sqldelight/` появляются `*Queries`-классы.
- **iOS:** SwiftUI-экраны не дублируют логику — оборачивают shared ViewModel через `FlowObserver`. Любые изменения `domain`/`presentation` проверяй на iOS bridge — там используются `as!`-касты (List<T> ↔ NSArray, StateFlow → Flow).
- **Тема:** все цвета — через `MaterialTheme.colorScheme`/`AppPalette` (iOS). Hex-литералов в экранах не пиши, они только в `presentation/theme/Color.kt` и `iosApp/iosApp/Theme/AppPalette.swift`.
- **Тексты UI** — на русском (приложение русскоязычное).

## Как добавить экран/фичу

1. Модель → `domain/model/Models.kt`
2. SQL → новый `.sq` в `commonMain/sqldelight/com/transcard/db/`
3. Repo: интерфейс в `domain/repository/Repositories.kt`, имплементация в `data/repository/`
4. UseCase (если логика нетривиальна) → `domain/usecase/UseCases.kt`
5. ViewModel → `presentation/viewmodel/`
6. Screen → `presentation/screen/`, регистрация в Koin (`di/Modules.kt`) и в навигации
7. iOS: SwiftUI-экран в `iosApp/iosApp/Screens/` + observable-обёртка через `FlowObserver`

## Сборка

```powershell
# Android
./gradlew :androidApp:installDebug

# Desktop
./gradlew :desktopApp:run
./gradlew :desktopApp:packageDistributionForCurrentOS

# iOS framework для Xcode
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

Перед первой сборкой: `gradle wrapper --gradle-version 8.10` (wrapper в репо не закоммичен).

## Где лежат данные

- Android: `/data/data/com.transcard.android/databases/transcard.db`
- Desktop: `~/.transcard/transcard.db`
- iOS: Application Support (`transcard.db`)

## Текущие ограничения (важно знать перед изменениями)

- **Gradle wrapper не сгенерирован** — см. выше.
- **Xcode-проект `iosApp.xcodeproj` не создан** — Swift-файлы готовы, но `.xcodeproj` создаётся вручную (инструкция в README).
- **iOS bridge без SKIE** — ручные `as!`-касты в `FlowObserver`. Если ломается типизация на iOS — смотреть туда.
- **Тестов нет.** Если пишешь новую логику и пользователь не просил тесты — не добавляй (KISS), но скажи об этом.
- **Шрифт Nunito не подключён** — системный.

## Проверка после изменений

- `./gradlew build` собирает все таргеты включая iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`).
- При изменении `.sq` — пересобирать (генерация SQLDelight).
- При изменении `domain`/`presentation` — проверить, что iOS-обёртки не сломались (особенно дженерики в Flow/List).

## Прочее

- README.md содержит более подробную инструкцию по iOS-сборке и палитру цветов — туда смотреть, если нужны детали.
- Язык общения с пользователем — русский.
