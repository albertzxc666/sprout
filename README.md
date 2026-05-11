# TransCard

Кросс-платформенное приложение для изучения иностранных слов через карточки.
Kotlin Multiplatform + Compose Multiplatform (Android, Desktop) + SwiftUI (iOS).

## Архитектура

```
TransCard/
├── shared/                    ← KMP shared (вся бизнес-логика + Compose UI)
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/transcard/
│       │   │   ├── data/          ← репозитории + Database
│       │   │   ├── domain/        ← модели, интерфейсы, use cases
│       │   │   ├── presentation/  ← ViewModels, theme, screens, components
│       │   │   ├── di/            ← Koin модули
│       │   │   └── App.kt         ← корневой Composable
│       │   └── sqldelight/com/transcard/db/   ← .sq схемы
│       ├── androidMain/   ← AndroidSqliteDriver + Koin Android module
│       ├── desktopMain/   ← JdbcSqliteDriver
│       └── iosMain/       ← NativeSqliteDriver + Flow→Swift bridge
├── androidApp/                ← Android entry point (Application + Activity)
├── desktopApp/                ← Compose Desktop entry point
└── iosApp/iosApp/             ← SwiftUI screens (5 шт.) + bridges
```

## Технологии

| Слой           | Технология                              | Версия       |
|----------------|-----------------------------------------|--------------|
| Язык           | Kotlin Multiplatform                    | 2.1.0        |
| UI (Android/Desktop) | Compose Multiplatform             | 1.7.3        |
| UI (iOS)       | SwiftUI                                 | iOS 15+      |
| БД             | SQLDelight                              | 2.0.2        |
| DI             | Koin                                    | 4.0.0        |
| Навигация      | Voyager                                 | 1.1.0-beta02 |
| Async          | Coroutines + StateFlow                  | 1.9.0        |

## Сборка

### Требования
- JDK 17+
- Android SDK (API 35) + AGP 8.7
- Xcode 15+ (для iOS)
- Gradle wrapper подтянется автоматически при первом запуске

### Подготовка Gradle wrapper
```bash
gradle wrapper --gradle-version 8.10
```

### Android
```bash
./gradlew :androidApp:installDebug
```
Или открой проект в Android Studio (Hedgehog+) и запусти конфигурацию `androidApp`.

### Desktop (JVM)
```bash
./gradlew :desktopApp:run
```
Сборка нативного дистрибутива:
```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
```

### iOS
1. В терминале собери XCFramework для shared:
   ```bash
   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
   ```
   (или `assembleSharedXCFramework` после доконфигурирования)
2. Создай Xcode-проект в `iosApp/iosApp.xcodeproj`:
   - File → New → Project → iOS App, имя `iosApp`, путь `iosApp/`.
   - Удали стандартные `ContentView.swift` / `iosAppApp.swift`.
   - Добавь существующие файлы из `iosApp/iosApp/` (Add Files to "iosApp").
3. Подключи фреймворк `Shared.framework`:
   - Build Phases → Link Binary With Libraries → `+` → Add Other → Add Files…
   - Укажи путь `shared/build/xcode-frameworks/Debug/iphonesimulator17.0/Shared.framework`
   - Либо добавь Run Script Phase, который собирает shared перед билдом:
     ```bash
     cd "$SRCROOT/.."
     ./gradlew :shared:embedAndSignAppleFrameworkForXcode
     ```
4. Запусти из Xcode. Минимальная iOS — 15.

> **Примечание:** Скрипт `embedAndSignAppleFrameworkForXcode` появляется в задачах Gradle после первого открытия Xcode-проекта с правильной конфигурацией. Альтернатива — использовать `KMMBridge` или `SKIE` для автоматизации.

## Где хранятся данные

- **Android:** `/data/data/com.transcard.android/databases/transcard.db`
- **Desktop:** `~/.transcard/transcard.db`
- **iOS:** Application Support directory (`transcard.db`)

Всё локально. Сеть не нужна.

## Дизайн

Палитра подобрана под долгое чтение — приглушённые тона, без резких акцентов:

| Назначение        | Light     | Dark      |
|-------------------|-----------|-----------|
| Background        | `#F5F2ED` | `#1E1C1A` |
| Surface           | `#FFFFFF` | `#2A2825` |
| Primary (шалфей)  | `#5C8C6A` | `#7AAA88` |
| Error (терракот)  | `#B85C44` | `#D17968` |
| Text primary      | `#2C2A26` | `#EDE9E3` |
| Text secondary    | `#6B6860` | `#9C9890` |

Тема переключается автоматически по системной (`isSystemInDarkTheme`).

## Экраны

1. **SpaceListScreen** — список пространств, FAB для создания, диалог выбора языковой пары.
2. **CardListScreen(spaceId)** — карточки внутри пространства, добавление/редактирование/удаление, кнопка «Начать обучение».
3. **StudySetupScreen(spaceId)** — выбор направления (родной↔изучаемый).
4. **StudyScreen(spaceId, direction)** — основной режим: прогресс-бар, карточка, поле ввода, проверка с цветной обводкой и иконкой, переход к следующей.
5. **StudyResultScreen** — итоги (счёт + процент), кнопки «Учить снова» / «На главную».

## Известные ограничения текущего скаффолда

- **Gradle wrapper не сгенерирован** — выполни `gradle wrapper --gradle-version 8.10` после клона.
- **iOS Xcode-проект не создан** — Xcode не умеет в автогенерацию `.xcodeproj` из CLI без шаблонов; см. инструкцию выше. Все Swift-файлы готовы и лежат в `iosApp/iosApp/`.
- **iOS bridging без SKIE** — `FlowSubscription` использует `as!`-касты (List<T> ↔ NSArray, StateFlow → Flow). Для production-проекта рекомендуется подключить [SKIE](https://skie.touchlab.co/) — это уберёт ручные касты и даст нативные Combine publishers.
- **Шрифт Nunito/Inter не подключён** — используется системный шрифт. Чтобы добавить:
  - В `shared/src/commonMain/composeResources/font/` положи `Nunito-Regular.ttf` и `Nunito-SemiBold.ttf`
  - В `Typography.kt` подключи через `FontFamily` + ресурсы Compose Multiplatform.
- **Иконки в пустых состояниях** — иконки Material; чтобы заменить на иллюстрации, добавь SVG/PNG в `composeResources/drawable/` и используй `painterResource`.
- **Тесты не написаны** — пропущены по запросу.

## Как добавить функцию

1. Модель → `shared/.../domain/model/Models.kt`
2. SQL → новый `.sq`-файл в `shared/src/commonMain/sqldelight/com/transcard/db/`
3. Repository → интерфейс в `domain/repository`, имплементация в `data/repository`
4. UseCase (если бизнес-логика нетривиальна) → `domain/usecase/UseCases.kt`
5. ViewModel → `presentation/viewmodel/`
6. Screen → `presentation/screen/` (+ зарегистрировать в DI и в навигации)
7. Для iOS — добавить SwiftUI-экран в `iosApp/iosApp/Screens/` и observable-обёртку.

## Что точно проверить после первого `./gradlew build`

- Версия Compose Compiler соответствует Kotlin 2.1 (плагин `kotlin.plugin.compose` сам подтянет совместимую — но проверь).
- На iOS таргетах `iosX64`, `iosArm64`, `iosSimulatorArm64` — все три должны собираться, иначе фреймворк не подключится в симулятор/девайс.
- SQLDelight генерация — после первой сборки в `shared/build/generated/sqldelight/` появятся `TransCardDatabase`, `SpaceQueries`, `CardQueries`, `StudyResultQueries`.

---

Лицензия: MIT (по умолчанию, можешь сменить).
