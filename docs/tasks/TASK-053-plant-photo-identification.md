# TASK-053 — Мультимодальне розпізнавання та додавання рослини за фото з підтвердженням

## Мета

Реалізувати повноцінний флоу створення персональної картки рослини Flowers 2.0:
Користувач робить фото або обирає з галереї $\rightarrow$ `ImageProcessor` оптимізує фото (1024px, без GPS, захищене локальне збереження) $\rightarrow$ `GeminiPlantService` виконує мультимодальне розпізнавання виду $\rightarrow$ користувач бачить екран підтвердження з попереднім переглядом фото, назвою, ступенем впевненості та рекомендаціями догляду $\rightarrow$ користувач підтверджує (або коригує) $\rightarrow$ створюється картка в Room v3.
За відсутності інтернету або помилках ШІ доступне збереження вручну (Offline-first).

## Складові реалізації

1. **Room v3 та міграція 2 → 3:**
   - Оновлення `PlantEntity`: додавання полів `speciesName`, `botanicalName`, `description`, `wateringAdvice`, `soilType`, `toxicityInfo`, `aiConfidence`, `isAiGenerated`.
   - Підвищення версії `FlowersDatabase` до `3`.
   - `MIGRATION_2_3`: адитивні `ALTER TABLE plants ADD COLUMN ...` зі значеннями за замовчуванням (`DEFAULT NULL` та `DEFAULT 0`), що гарантує 100% збереження всіх наявних рослин, історії та нагадувань.
2. **Мультимодальне розпізнавання у `GeminiPlantService`:**
   - Інтеграція моделі `gemini-3.5-flash-lite` з передачею `content { image(bitmap); text(prompt) }`.
   - Структурований JSON-вивід (`responseMimeType = "application/json"`, низька температура 0.2f).
   - Обробка результату: прапорець `is_plant`, назва українською, ботанічна назва, рівень впевненості (`HIGH`, `MEDIUM`, `LOW`), поради щодо поливу, ґрунту, токсичності та альтернативні види.
   - Обробка помилок: не рослина, низька впевненість, таймаут, квота (429), відсутність зв'язку (`NetworkChecker`).
3. **Екран підтвердження та створення (AddPlantScreen / PlantsViewModel):**
   - Перегляд фото, бейдж впевненості ШІ, альтернативи при невизначеності.
   - Редаговані поля (назва вазона, вид, кімната, світло, інтервал перевірки ґрунту, поради).
   - Очищення тимчасового фото при скасуванні.
   - Офлайн-фолбек.
4. **Тестування:**
   - Модульні тести міграції Room 2 → 3.
   - Модульні тести мапінгу мультимодальної відповіді (успіх, не рослина, низька впевненість, помилка мережі, помилка квоти).
   - Збірка на Adoptium JDK 21.

## Журнал виконання

- **Крок 1: Схема бази даних Room v3 та безпечна міграція 2 → 3:**
  - Розширено `PlantEntity` додатковими полями Flowers 2.0: `speciesName`, `botanicalName`, `description`, `wateringAdvice`, `soilType`, `toxicityInfo`, `aiConfidence`, `isAiGenerated`.
  - Реалізовано адитивну міграцію `MIGRATION_2_3` (`ALTER TABLE plants ADD COLUMN ...`).
  - Оновлено `MigrationTest.kt` із верифікацією збереження 100% рядків таблиці `plants`, нагадувань і подій догляду при переході 2 → 3.
- **Крок 2: Мультимодальний ШІ-промпт та відповідь:**
  - У `AiPrompt.kt` додано `identifyPlantFromPhotoPrompt()` із жорсткою схемою полів (`is_plant`, `name_uk`, `name_latin`, `confidence`, `description`, `light_level`, `default_check_interval_days`, `watering_advice`, `soil_type`, `toxicity_info`, `alternative_species`).
  - Додано тести у `AiPromptTest.kt`.
- **Крок 3: Інтеграція мультимодального розпізнавання у `GeminiPlantService`:**
  - Додано моделі `PlantIdentificationResponse` та `IdentifyPlantResult`.
  - Реалізовано методи `identifyPlantFromPhoto(bitmap)` та `identifyPlantFromPhoto(imageFile, decoder)`.
  - Реалізовано `parseIdentificationResponse` із санітизацією рівнів впевненості, діапазону інтервалів 3..21 та дефолтом `UNKNOWN` для токсичності.
  - Оновлено `GeminiPlantServiceTest.kt` із тестами на успіх, розпізнавання «не рослина», санітизацію, офлайн-помилку, помилку квоти та відсутній файл.
- **Крок 4: Екран підтвердження та керування станом (AddEditPlantScreen & PlantsViewModel):**
  - У `PlantsViewModel` реалізовано потік `AddPlantUiState` (`Idle`, `ProcessingPhoto`, `AnalyzingWithAi`, `Confirmation`, `NotAPlantError`, `ErrorFallback`), метод `onPhotoSelected` із викликом `ImageProcessor`, метод `resetAddPlantFlow` із видаленням тимчасового фото, метод `savePlant` із підтримкою полів Flowers 2.0.
  - У `AddEditPlantScreen` створено Compose-інтерфейс зйомки/вибору фото, індикатори завантаження, відображення бейджа впевненості (High/Medium/Low), чіпів схожих видів, редагованих полів та порад щодо догляду.
  - Налаштовано `FileProvider` у `AndroidManifest.xml` та `res/xml/file_paths.xml` (без розширення дозволів).
  - Створено повний набір юніт-тестів `PlantsViewModelTest.kt`.
- **Крок 5: Верифікація збірки:**
  - `testDebugUnitTest`: BUILD SUCCESSFUL.
  - `assembleDebug`: BUILD SUCCESSFUL.
  - `bundleRelease`: BUILD SUCCESSFUL.

- **Крок 6: Усунення блокувальних зауважень архітектурного аудиту:**
  - 1. **Скасування та очищення тимчасових фото:** `activeAnalysisJob?.cancel()`, відстеження `pendingPhotoFile`, гарантоване видалення залишкових файлів при виклику `resetAddPlantFlow(discardPhoto = true)` та захист від запізнілих результатів ШІ.
  - 2. **Правдива історія поливу:** вилучено створення штучної події `WATER` при створенні картки рослини; `lastWateredDate` для нових рослин залишається `null`; `ReminderCalculator.calculateInitialCheckDate(now)` призначає перевірку ґрунту на день додавання.
  - 3. **Безпека альтернативних видів:** при виборі чіпа альтернативного виду скидаються залежні поля (`botanicalName`, `wateringAdvice`, `soilType`), токсичність переводиться в `UNKNOWN`, рівень впевненості — в `LOW`, користувач отримує попереджувальний банер.
  - 4. **Захист від дублювання збереження:** блокування повторних натискань кнопки «Зберегти» через `_isSaving` у `PlantsViewModel` та `AddEditPlantScreen`.
  - 5. **Android Instrumented тест міграції Room v2 → v3 (`RoomMigrationV2ToV3Test`):** створення реальної бази даних SQLite v2 на ізольованому емуляторі `emulator-5554`, виконання `MIGRATION_2_3`, відкриття через Room v3, верифікація збереження 100% рослин і подій догляду та підтримки нових колонок v3. Результат: 1/1 PASSED.
  - 6. **Наскрізний інструментальний тест користувацького сценарію (`AddPlantUserScenarioTest`):** прогін повного шляху (фото $\rightarrow$ `ImageProcessor` $\rightarrow$ ШІ $\rightarrow$ екран підтвердження $\rightarrow$ збереження в Room v3 $\rightarrow$ повторне відкриття), скасування з очищенням фото, офлайн-фолбек. Результат: 3/3 PASSED.
  - 7. **EXIF та растр (`AndroidImageProcessorTest`):** верифікація 8 EXIF-орієнтацій на рівні пікселів на `emulator-5554`. Результат: 5/5 PASSED.


