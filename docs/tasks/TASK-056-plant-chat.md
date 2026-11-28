# TASK-056 — Персональний ШІ-чат для кожної рослини з локальною історією

> **Статус:** В РОБОТІ (Кроки 1, 2, 3 та PR #42 завершено й злито в main; PR №43 Room v5 реалізовано й передано на незалежний QA-аудит)  
> **Етап:** Flowers 2.0 MVP (ADR-021, `FLOWERS_2_0_MVP_PLAN.md`)  
> **Відповідальний:** Агент 0 (Архітектор / Керівник)

---

## 1. Мета та бізнес-вимоги

Надати користувачеві можливість вести повноцінний контекстний діалог про конкретну рослину з урахуванням її унікального паспорта та реальної історії догляду:
1. **Ізоляція чатів:** у кожної рослини власна незалежна історія; контексти різних рослин ніколи не змішуються.
2. **Offline-first збереження:** уся історія реплік зберігається локально на пристрої в базі даних Room і доступна для читання без інтернету.
3. **Економний та безпечний контекст ШІ:** при надсиланні повідомлення формується суворий контекст:
   - паспорт конкретної рослини (назва, вид, кімната, рівень світла, базовий графік перевірки ґрунту);
   - до 10 останніх записів журналу догляду (`care_events`);
   - слайдинг-вікно з максимум 6 попередніх повідомлень діалогу (захист від переповнення токенів та витрат квоти).
4. **Надійність при помилках мережі:** репліка користувача зберігається локально зі статусом `ERROR` при відсутності інтернету або збої API; доступна дія «Повторити» (Retry) без дублювання записів.
5. **Безпека та конфіденційність:**
   - інформаційне попередження не надсилати чутливі особисті дані;
   - захист від токсичних хімічних порад (`PlantSafetyGuardrail`);
   - чат має виключно рекомендаційний характер і не змінює розклад поливу чи базу даних без явних ручних дій користувача;
   - відсутність логінів, платних підписок чи тарифів (використання безкоштовного Spark-плану Firebase AI Logic, $0).

---

## 2. Архітектура бази даних Room: Міграція v3 → v4

### 2.1. Необхідність міграції
Для надійного локального збереження діалогів окремо для кожної рослини необхідна нова таблиця `chat_messages`. Додавання сутності вимагає підвищення версії Room з 3 до 4.

### 2.2. Схема `ChatMessageEntity`
```kotlin
package com.flowers.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageRole {
    USER,
    MODEL
}

enum class MessageStatus {
    SENDING,
    SENT,
    ERROR
}

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = PlantEntity::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["plantId"]),
        Index(value = ["plantId", "timestamp"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus = MessageStatus.SENT,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
```

### 2.3. Правила каскадності та індексації
- **Зв'язок із `plants`:** `FOREIGN KEY(plantId) REFERENCES plants(id) ON DELETE CASCADE`. При видаленні рослини користувачем її історія чату автоматично й чисто видаляється базою SQLite без сирітських записів.
- **Індекси:**
  - `index_chat_messages_plantId`: для миттєвої фільтрації та каскадного очищення;
  - `index_chat_messages_plantId_timestamp`: композитний індекс для вибірки слайдинг-вікна повідомлень за `O(log N)` без повного сканування таблиці.
- **Сортування:** для інтерфейсу вибірка виконується за `ORDER BY timestamp ASC`, для слайдинг-вікна Gemini — `ORDER BY timestamp DESC LIMIT 6`.

### 2.4. Безпека даних при міграції `MIGRATION_3_4`
Міграція є суто **адитивною**. Вона лише створює нову таблицю та індекси, не змінюючи й не перетворюючи наявні таблиці `plants`, `care_events`, `catalog_plants`, `care_pods`.
```sql
CREATE TABLE IF NOT EXISTS `chat_messages` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `plantId` INTEGER NOT NULL,
    `role` TEXT NOT NULL,
    `content` TEXT NOT NULL,
    `status` TEXT NOT NULL,
    `errorMessage` TEXT DEFAULT NULL,
    `timestamp` INTEGER NOT NULL,
    FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_chat_messages_plantId` ON `chat_messages` (`plantId`);
CREATE INDEX IF NOT EXISTS `index_chat_messages_plantId_timestamp` ON `chat_messages` (`plantId`, `timestamp`);
```

### 2.5. План автоматичного тестування міграції
1. Інструментальний тест `RoomMigrationV3ToV4Test` за допомогою `MigrationTestHelper`:
   - Створення бази даних версії 3.
   - Вставка тестових рослин та подій догляду `CareEventEntity`.
   - Запуск міграції `MIGRATION_3_4` до версії 4.
   - Верифікація збереження 100% даних рослин та подій.
   - Перевірка вставки, читання та каскадного видалення в `chat_messages`.

---

## 3. Контекстний сервіс Gemini: `PlantChatContextBuilder`

### 3.1. Формування контексту
Промпт будується суворо на основі актуальних даних із репозиторію:
1. **Паспорт рослини (System Context):**
   - Назва: `customName`
   - Ботанічний вид: `speciesName` / `botanicalName`
   - Розташування: `roomLocation`
   - Рівень світла: `lightLevel`
   - Інтервал перевірки ґрунту: `checkIntervalDays`
   - Специфіка поливу: `wateringAdvice`
   - Субстрат: `soilType`
   - Опис: `description`
2. **Останні події догляду (Care History Window):**
   - Максимум 10 останніх записів із `care_events` (дата, тип: `WATER`, `DELAY`, `SKIP`, `HEALTH_CHECK`, нотатки).
3. **Діалогове слайдинг-вікно (Conversation Window):**
   - Максимум 6 останніх повідомлень діалогу (3 пари "користувач — асистент"), відсортованих хронологічно.

### 3.2. Захист від галюцинацій та промпт-ін'єкцій
- **Розділення ролей:** системні інструкції передаються як незмінний преамбул або через API `systemInstruction`.
- **Екранування введення:** запит користувача обрамляється роздільниками `<<<USER_QUERY>>> ... <<<END_USER_QUERY>>>`.
- **Правило відсутності фактів:** якщо в паспорті чи історії немає потрібної інформації (наприклад, вік рослини чи точна вологість), ШІ зобов'язаний запитати користувача або зазначити, що даних немає, а не придумувати їх.
- **Санітизація безпеки:** відповідь перевіряється через `PlantSafetyGuardrail` на відсутність токсичних пестицидів та небезпечних порад.

### 3.3. Бюджет токенів та безкоштовні ліміти ($0, Spark)
- Паспорт рослини: ~120 токенів.
- 10 подій догляду: ~150 токенів.
- 6 повідомлень історії: ~300 токенів.
- Новий запит: ~50 токенів.
- **Разом вхідний контекст:** ~620 токенів.
- **Обмеження відповіді:** `maxOutputTokens = 350` (стисла ботанічна відповідь у 2–3 абзаци).
- Це гарантує стабільну роботу в межах безкоштовної квоти моделі `gemini-3.5-flash-lite` на Firebase AI Logic без ризику перевищення лімітів (429).

---

## 4. Інтерфейс користувача та User Flow (UI/UX)

### 4.1. Точка входу
- У `PlantDetailScreen.kt` додається кнопка «Запитати про рослину» (`FilledTonalButton` з піктограмою чату) поруч із кнопками «Полив» та «Діагностика».
- Навігаційний маршрут: `Screen.PlantChat("plant_chat/{plantId}")`.

### 4.2. Складові екрана `PlantChatScreen.kt`
1. **TopAppBar:**
   - Кнопка «Назад».
   - Мініатюра фото рослини (якщо є) або кругла іконка листка.
   - Заголовок: ім'я рослини (напр. *«Монстера»*).
   - Підзаголовок: ботанічний вид або підпис *«ШІ-помічник»*.
   - Меню дій: опція «Очистити історію чату».
2. **Дисклеймер конфіденційності (угорі чату):**
   - Лаконічна плашка: *«ШІ допомагає порадами щодо догляду. Будь ласка, не надсилайте особисті конфіденційні дані.»*
3. **Стрічка повідомлень (`LazyColumn`):**
   - Бабли повідомлень користувача (вирівнювання праворуч, `primaryContainer`).
   - Бабли відповідей ШІ (вирівнювання ліворуч, `surfaceVariant`).
   - Індикація статусів:
     - `SENDING`: делікатна пульсація або годинничок.
     - `ERROR`: червоний напис помилки та кнопка «Повторити» 🔄.
     - `SENT`: час репліки.
   - Індикатор набору тексту (Thinking animation), коли триває запит до Gemini.
4. **Панель введення (Bottom Bar):**
   - `OutlinedTextField` з плейсхолдером *«Запитайте про полив, ґрунт, світло...»*.
   - Кнопка надсилання (активна лише при непорожньому тексті та відсутності активного запиту).
   - Автоматичний відступ при появі клавіатури (`Modifier.imePadding()`).
5. **Доступність та теми:**
   - Повна підтримка динамічних кольорів Material 3 (світла та темна теми).
   - Підтримка скрінрідерів (TalkBack) з описом стану кожного повідомлення.
   - Тексти виключно українською мовою.

---

## 5. Обробка помилок та повтор відправки без дублювання

1. Користувач вводить текст і натискає «Надіслати»:
   - Створюється `ChatMessageEntity(role = USER, status = SENDING)`.
   - Репліка негайно з'являється в списку повідомлень.
2. Перевірка мережі (`NetworkChecker.isOnline()`):
   - **Якщо немає зв'язку:** статус повідомлення перемикається на `ERROR`, `errorMessage = "Немає підключення до інтернету"`. Запит до Gemini не надсилається. Повідомлення зберігається в базі, біля нього з'являється кнопка повтору 🔄.
3. Якщо зв'язок є:
   - Відправляється запит до `GeminiPlantService.sendChatMessage(...)`.
   - **Успіх:** статус повідомлення користувача змінюється на `SENT`; створюється нове повідомлення асистента `ChatMessageEntity(role = MODEL, status = SENT, content = reply)`.
   - **Збій (таймаут, квота 429, помилка сервера):** повідомлення користувача отримує статус `ERROR` зі зрозумілим українським текстом (`AiErrorMapper`).
4. **Повтор відправки (Retry):**
   - Користувач натискає 🔄 на помилковому повідомленні:
   - Статус цього ж існуючого запису оновлюється на `SENDING`, і запит надсилається знову.
   - **Жодних дублікатів повідомлення користувача в чаті не створюється.**

---

## 6. План тестування (Testing Strategy)

1. **Модульні тести (JVM Unit Tests):**
   - `PlantChatContextBuilderTest`:
     - перевірка включення паспорта рослини без вигадування відсутніх полів;
     - обмеження історії догляду рівно 10 останніми записами;
     - обмеження діалогу рівно 6 повідомленнями (слайдинг-вікно);
     - ізоляція: фільтрація записів виключно за вказаним `plantId`.
   - `ChatViewModelTest`:
     - збереження локального повідомлення перед запитом;
     - коректний перехід статусів: `SENDING` -> `SENT` або `ERROR`;
     - повтор повідомлення без створення дублів;
     - блокування відправки під час активного запиту (дебаунс);
     - обробка офлайн-режиму без виклику Gemini;
     - очищення історії чату для конкретної рослини.
   - `GeminiPlantServiceChatTest`:
     - обробка нормальної текстової відповіді, таймауту, вичерпання квоти (429) та помилок Firebase AI Logic.
2. **Інструментальні тести на емуляторі (`small_phone` AVD):**
   - `RoomMigrationV3ToV4Test`: перевірка безпечної міграції БД на реальній SQLite.
   - `PlantChatComposeTest`:
     - відкриття чату з картки рослини;
     - відображення наявної історії без інтернету;
     - введення повідомлення, поява в списку, відображення відповіді ШІ;
     - поведінка при помилці зв'язку та натисканні «Повторити»;
     - ізоляція: перевірка, що повідомлення рослини A не видно у чаті рослини B.
3. **Релізна збірка:**
   - Перевірка `./gradlew.bat bundleRelease` на Adoptium JDK 21 з увімкненим R8/ProGuard.

---

## 7. Послідовність реалізації після затвердження

1. **Крок 1: База даних та міграція Room v3 → v4:**
   - створення `ChatMessageEntity`, `MessageRole`, `MessageStatus`;
   - створення `ChatMessageDao`;
   - оновлення `FlowersDatabase` (version 4);
   - реалізація `Migrations.MIGRATION_3_4`;
   - інструментальний тест міграції `RoomMigrationV3ToV4Test`.
2. **Крок 2: Контекст та сервіс ШІ:**
   - реалізація `PlantChatContextBuilder`;
   - додавання методу `sendPlantChatMessage` у `GeminiPlantService` та `FlowersRepository`;
   - unit-тести контексту та сервісу.
3. **Крок 3: ViewModel та логіка чату:**
   - реалізація `ChatViewModel` (дебаунс, ретраї, офлайн-перевірка, оновлення статусів);
   - unit-тести ViewModel.
4. **Крок 4: Інтерфейс користувача та навігація:**
   - створення `PlantChatScreen.kt`;
   - оновлення `PlantDetailScreen.kt` (кнопка «Запитати про рослину»);
   - оновлення `FlowersNavHost.kt` та `Screen.kt`.
5. **Крок 5: Комплексна верифікація:**
   - запуск усіх unit-тестів;
   - запуск Compose UI та інструментальних тестів на емуляторі `small_phone`;
   - релізна збірка `bundleRelease`;
   - створення PR, проходження GitHub Actions CI та незалежний аудит `QA Verified`.

---

## 8. Рішення, які виносяться на затвердження Власника

1. **Затвердження міграції Room v3 → v4:**
   - Підтвердження створення нової таблиці `chat_messages` з `ON DELETE CASCADE` та збереженням усіх наявних таблиць.
2. **Затвердження лімітів контексту Gemini:**
   - Підтвердження розміру слайдинг-вікна: максимум 6 повідомлень діалогу + до 10 останніх записів догляду (~650 токенів на запит, $0 витрат на тарифі Spark).
3. **Розташування точки входу в UI:**
   - Розміщення кнопки «Запитати про рослину» в `PlantDetailScreen` поруч із кнопками «Полив» та «Діагностика».


---

## Журнал виконання: Крок 1 (Room v4) — 2026-11-26

**Статус кроку 1:** ЗАВЕРШЕНО. PR #38 успішно злито в `main` (коміт `d931f26`). Gemini-чат, ContextBuilder, ChatViewModel, UI і навігація не створювались і очікують окремого погодження Власника на наступні кроки. Зареєстровано завдання безпеки `TASK-056-SAFETY-01`.

### Реалізовано
- `ChatMessageEntity` (+ `MessageRole` USER/MODEL, `MessageStatus` SENDING/SENT/ERROR), FK `plantId → plants.id ON DELETE CASCADE`.
- `ChatConverters`: enum зберігаються рядками `name` (стійко до зміни порядку констант).
- `ChatMessageDao`: `observeMessages`/`getMessages` (повна історія, `ORDER BY timestamp ASC, id ASC`), `getLastMessages(plantId, N)` (останні N у хронологічному порядку), `insertMessage` (**ABORT**, не REPLACE), `updateStatus`, `beginRetry` (ERROR→SENDING за тим самим id, ідемпотентно), `markInterruptedSendingAsError` (відновлення після перезапуску), `deleteAllForPlant`.
- Room v4, `MIGRATION_3_4`, `exportSchema = true`, експортовано `schemas/.../4.json`. Один індекс `(plantId, timestamp)` замість двох (префікс plantId покриває FK; дублів немає).

### SQL міграції 3 → 4 (лише CREATE)
```sql
CREATE TABLE IF NOT EXISTS `chat_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `plantId` INTEGER NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `status` TEXT NOT NULL, `errorMessage` TEXT, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );
CREATE INDEX IF NOT EXISTS `index_chat_messages_plantId_timestamp` ON `chat_messages` (`plantId`, `timestamp`);
```
Збігається з експортованою `4.json`; DROP/ALTER/DELETE/UPDATE відсутні (перевіряється тестом `migrationSql_isPurelyAdditive`).

### ⚠ ВИЯВЛЕНИЙ РИЗИК: `PlantDao.insertPlant` + `OnConflictStrategy.REPLACE`
SQLite виконує REPLACE як DELETE + INSERT; Room вмикає foreign keys, тому повторна вставка рослини з **наявним id** каскадно видаляє її `care_events` і `chat_messages`. **Підтверджено тестом** `ChatMessageDaoTest.replaceOnInsertPlant_documentsCascadeDataLoss_andUpdatePlantIsSafe` (після REPLACE дочірніх записів 0; `updatePlant` їх зберігає).
- Поточний production-код викликає `insertPlant` лише для НОВИХ рослин (id = 0, `PlantsViewModel`, гілка створення); редагування йде через `updatePlant`. Тож втрата зараз не відтворюється в застосунку — ризик латентний (майбутні виклики, імпорт/відновлення).
- `PlantDao` у цьому кроці НЕ змінювався (за вимогою без рефакторингу без погодження).
- **Пропозиція безпечного рішення (потрібне погодження):** змінити `insertPlant` на `OnConflictStrategy.ABORT` (або `@Upsert`, який виконує UPDATE при конфлікті і не каскадить) окремим малим завданням з тестом; тест характеризації тоді перетвориться на тест безпеки.

### Верифікація
- JVM unit: 254/254 (0 failures/errors/skipped).
- Instrumented (емулятор `small_phone`, API 36, `emulator-5584`): 46/46 (0 failed/skipped), у т.ч. 3 тести `RoomMigrationV3ToV4Test` і 10 тестів `ChatMessageDaoTest`. Під час роботи виправлено дві помилки самих тестів: хибнопозитивна перевірка слів `DELETE`/`UPDATE` усередині `ON DELETE CASCADE`/`ON UPDATE NO ACTION`; `RoomMigrationV2ToV3Test` мав зареєструвати `MIGRATION_3_4`, бо тепер відкриває БД v4.
- `bundleRelease` (Adoptium JDK 21): BUILD SUCCESSFUL, AAB 17 629 075 байт.
- Збереження старих даних (v3→v4, ізольована тестова БД): 2 рослини, шляхи фото, усі події WATER/DELAY/HEALTH_CHECK (з `daysDelayed`, `notes`, `photoUri`), нова таблиця спочатку порожня.

---

## Журнал виконання: Крок 2 (ContextBuilder та Gemini-сервіс) — 2026-11-27

**Статус кроку 2:** РЕАЛІЗОВАНО. Оформлено PR, проведено повне тестування, очікує QA та рішення Власника.

### Реалізовані компоненти:
1. **`PlantChatContextBuilder` (`com.flowers.app.domain`):**
   - Формує суворо ізольований контекст для конкретної рослини.
   - Детерміновано відбирає до 10 останніх подій догляду (`timestamp DESC, id DESC`).
   - Відбирає до 6 останніх завершених реплік (`status == MessageStatus.SENT`, `timestamp ASC, id ASC`).
   - Виключає повідомлення зі статусами `SENDING` та `ERROR`.
   - Запобігає дублюванню поточного питання користувача в попередньому діалозі.
   - Не включає `photoUri`, файлові шляхи чи локальні системні шляхи.
   - Не вигадує дати поливу (якщо `lastWateredDate == null`, пише «Дані про полив відсутні»).
   - Санітизує всі ненадійні текстові дані (заміна `<`, `>` на `[`, `]`, ліміти символів: питання ≤ 500, репліки ≤ 300, нотатки ≤ 150, ім'я ≤ 80).
   - Динамічний контроль бюджету контексту (загальний обсяг ≤ 3500 символів, відкидання найстаріших реплік при перевищенні).
   - Підтримка 5 мов: `uk`, `en`, `de`, `ru`, `es` (фолбек на `uk`).

2. **Захист системних інструкцій:**
   - Системні правила чітко відокремлені від даних рослини.
   - Системний промпт забороняє виконання команд, jailbreak, виведення інструкцій або зміну ролі, якщо вони містяться у ненадійних блоках користувача.
   - Заборона токсичних агрохімікатів на рівні системного промпту + додаткова верифікація через `PlantSafetyGuardrail`.

3. **`GeminiPlantService.sendPlantChatMessage`:**
   - Конфігурація моделі: `GEMINI_MODEL` (`gemini-3.5-flash-lite`), `temperature = 0.4f`, `maxOutputTokens = 350`, `responseMimeType = "text/plain"`.
   - Не впливає на JSON-конфігурацію карток та фото-діагностики.
   - Таймаут 20 секунд (`withTimeoutOrNull(20_000L)`).
   - Захист від повторних одночасних викликів для однієї рослини через `Mutex` per `plantId`.
   - Типізований результат `PlantChatResult`: `Success` (з прапорцем `isSafetySanitized`), `Offline`, `RateLimited`, `Timeout`, `InvalidResponse`, `ServiceError`.

4. **`FlowersRepository` та `FlowersRepositoryImpl`:**
   - Додано метод `sendPlantChatMessage(plantId, userQuestion, language)`.
   - Збирає паспорт рослини, події догляду та надіслані репліки, будує промпт через `PlantChatContextBuilder` та викликає `GeminiPlantService`.
   - Повідомлення в Room у цьому кроці НЕ записуються (збереження та UI будуть на кроках 3 і 4).

5. **Тестування:**
   - `PlantChatContextBuilderTest`: 11 тестів (ізоляція двох рослин, обмеження 10 подій, 6 повідомлень, сортування при однакових timestamp, виключення SENDING/ERROR, дедуплікація питання, санітизація ін'єкцій, обрізання довгих записів, 5 мов, відсутність шляхів фото).
   - `GeminiPlantServiceChatTest`: 9 тестів (успіх, офлайн без виклику API, порожня відповідь, таймаут, 429 ліміт, 500 сервер, санітизація токсичних хімікатів, блокування одночасних викликів, відсутність регресій).
   - Усі 274 JVM unit-тести пройшли успішно (100% pass).
   - Усі 46 Android інструментальних тестів пройшли успішно на `emulator-5554`.
### Усунення зауважень незалежного QA-аудиту (2026-11-27):
1. **Прокидання `CancellationException`:** у `GeminiPlantService.sendPlantChatMessage` виправлено обробку виключень — `CancellationException` більше не перехоплюється як `ServiceError`, а коректно прокидається далі для штатного скасування корутин при виході користувача з екрана.
2. **Розмежування `Timeout` та `InvalidResponse`:** замінено `withTimeoutOrNull` на `withTimeout` із перехопленням `TimeoutCancellationException` для `Timeout`, тоді як порожня або null-відповідь моделі повертає `InvalidResponse`.
3. **Санітизація `PlantSafetyGuardrail` без хибних спрацювань:**
   - Виключено ботанічні назви та симптоми («хлорофітум», «хлороз», «хлорофіл», «хлоропласт») із тригерів отрутохімікатів.
   - Використано межі слів для українського слова «гас» (керасин), щоб уникнути хибного спрацювання на звичайні дієслова («згасає», «погасне»).
   - Додано російські отрутохімікати та враховано відмінювання основ («белизн», «окроп», «кип'ятк», «кипятк»).
   - Створено вичерпний окремий тест-сьют `PlantSafetyGuardrailTest` (6 тестів).
   - Враховано відмінювання токсичних речовин («мідним купоросом», «актарою», «хлоркою», «хлорною водою»), додано «ядохимикат», «формалин», ізольовано «окроп» від безпечного дієслова «окропити».
   - Додано російські та англійські безпечні ботанічні терміни («хлорофит», «хлорофил», «chlorot»), щоб уникнути хибного спрацювання санітизації на «Хлорофитум».
4. **Покращення `PlantChatContextBuilder` та `FlowersRepositoryImpl`:**
   - Парсинг мовних кодів із суфіксами регіону (`en-US` → `en`, `uk_UA` → `uk`).
   - Дворівневий бюджет символів: точний розрахунок із заголовком блоку, скорочення діалогу, а за необхідності — старіших подій догляду (загальний промпт суворо ≤ 3500 символів).
   - Подвійна регістронезалежна дедуплікація поточного питання в діалозі (за сирим та санітизованим вмістом).
   - Санітизація `lightLevel` та `eventType`.
   - В інтерфейсі `FlowersRepository` метод `sendPlantChatMessage` зроблено суто абстрактним (вилучено тестову реалізацію за замовчуванням).
   - `userPreferences` у `FlowersRepositoryImpl` зроблено `lazy`.
   - Додано юніт-тести репозиторію `FlowersRepositoryChatTest` (валідація порожнього питання, невідомий plantId, успішна делегація).
5. **Підсумкові тести:** 287/287 JVM unit-тестів успішно пройдено (100%), `bundleRelease` зібрано успішно (17.6 МБ).

### Архітектурні виправлення за вказівкою Власника (2026-11-27):
1. **Справжнє розділення системних інструкцій:**
   - `PlantChatPrompt.systemInstruction` передається через офіційний параметр Firebase AI Logic `systemInstruction = content { text(sysInstruction) }`.
   - `PlantChatPrompt.userPrompt` передається окремо через `model.generateContent(userPrompt)`.
   - Модель отримує динамічну мову (`systemInstructionText`) індивідуально для кожного запиту без зміни глобальної конфігурації інших моделей.
   - Тестовий генератор `generateChatText: suspend (systemInstruction: String, userPrompt: String) -> String?` верифікує повне розділення преамбули та контексту рослини.
2. **Виправлення дедуплікації діалогу:**
   - Вилучено видалення повідомлень за збігом тексту.
   - Поточна репліка відсікається виключно за станом (`status != MessageStatus.SENT`) або за стабільним ID (`currentMessageId`).
   - Справжні повторення того самого запитання в різні моменти часу та попередні відповіді ШІ залишаються у діалоговому контексті.
3. **Захист локальних файлових шляхів у вільному тексті:**
   - У `PlantChatContextBuilder.sanitizeText()` додано фільтрацію локальних шляхів (`file://`, `content://`, `/storage/emulated/`, `/data/user/`, `/data/data/`, `/sdcard/`, Windows-шляхи на кшталт `C:\...`).
   - Будь-які шляхи у назвах, описах, нотатках догляду, старих репліках чи новому питанні автоматично замінюються на нейтральний маркер `[локальний_шлях]`, залишаючи решту тексту неушкодженою.
4. **Підсумкова верифікація:**
   - JVM Unit Tests: **290 / 290 PASSED** (0 failures, 0 errors, 0 skipped).
   - Android Instrumented Tests: **46 / 46 PASSED** на емуляторі `small_phone` (`emulator-5554`).
   - `bundleRelease` на JDK 21: BUILD SUCCESSFUL (AAB 17 642 725 байт).

### Безпекове виправлення PlantSafetyGuardrail (2026-11-27):
1. **Захист від маскування небезпечних речовин ботанічним вайтлістом:**
   - У `PlantSafetyGuardrail` замінено частковий збіг підрядків (`SAFE_BOTANICAL_ROOTS`) на точні регулярні вирази з межами слів для цілих слів або точних допустимих словоформ (`SAFE_BOTANICAL_PATTERNS`).
   - Усунуто корінь `chlorot`, який частково збігався з назвою небезпечного фунгіциду `chlorothalonil` і маскував його від перевірки на токсичність.
   - Забезпечено збереження проходження безпечних ботанічних термінів (`chlorotic`, `chlorosis`, `chlorophyll`, `Chlorophytum`, `chloroplast`, українських «хлороз», «хлоротичний», «хлорофіл», «хлорофітум», «хлоропласт» та російських еквівалентів).
   - `chlorothalonil` (та варіанти «хлороталоніл», «хлороталонил») гарантовано блокується як самостійно, так і в складі рекомендацій з одночасною наявністю безпечних ботанічних термінів.
   - Додано захисний дубль: пряме включення `chlorothalonil`, `хлороталоніл`, `хлороталонил` до `UNSAFE_KEYWORDS`.
2. **Регресійне тестування:**
   - Додано тест `chlorothalonilIsNotMaskedByBotanicalAllowlist` у `PlantSafetyGuardrailTest` (окрема перевірка безпечних термінів, назви хлороталонілу окремо та складних речень, де одночасно присутні ботанічний термін і хлороталоніл трьома мовами).
   - Усі 7 тестів `PlantSafetyGuardrailTest` проходять успішно.
3. **Підсумкова верифікація:**
   - JVM Unit Tests: **291 / 291 PASSED** (0 failures, 0 errors, 0 skipped, 26 тест-сьютів).
   - Android Instrumented Tests: **46 / 46 PASSED** на емуляторі `small_phone` (`emulator-5554`).
   - `bundleRelease` на JDK 21: BUILD SUCCESSFUL (AAB 17 643 155 байт).

### Злиття PR #40 та завершення Кроку 2 (2026-11-27):
- **Схвалення Власника:** Власник затвердив результати перевірки PR #40 та дозволив стандартне злиття в `main`.
- **Злиття:** PR #40 злито в `main` звичайним merge комітом `6f35c8f`.
- **Синхронізація:** локальний `main` синхронізовано з `origin/main` (`Fast-forward` до `6f35c8f`).
- **Підтверджені метрики якості:**
  - JVM модульні тести: 291 / 291 PASSED.
  - Android інструментальні тести: 46 / 46 PASSED на емуляторі `small_phone` (`emulator-5554`).
  - Релізна збірка: `bundleRelease` на Adoptium JDK 21 збирається успішно (AAB 17 643 155 байт).
  - GitHub Actions CI: run `46033756931` — SUCCESS.
  - Незалежний QA-аудит: `QA Verified`.
- **Поточний стан:**
  - `TASK-056 / STEP 2` — ЗАВЕРШЕНО.
  - `TASK-056 / STEP 3` — В РОБОТІ (дозволено Власником).

---

## Журнал виконання: Крок 3 (ChatViewModel та керування життєвим циклом чату) — 2026-11-27

**Статус кроку 3:** РЕАЛІЗОВАНО. Оформлено PR, проведено повне тестування, очікує QA та рішення Власника.

### Реалізовані компоненти:
1. **`ChatMessageDao` (атомарність та безпека):**
   - Перетворено з `interface` на `abstract class ChatMessageDao` для підтримки транзакційного методу `@Transaction open suspend fun saveAssistantResponse(userMessageId: Long, modelMessage: ChatMessageEntity): Long`.
   - В `saveAssistantResponse`:
     - Перевіряється наявність та статус повідомлення користувача (`userMsg != null && userMsg.status == SENDING`).
     - Якщо повідомлення було скасовано, видалено або чат очищено — оновлення не відбувається, модельне повідомлення не записується (запобігання «повідомленням-привидам»).
     - Атомарно в одній SQLite-транзакції: статус користувача перемикається на `SENT`, очищається `errorMessage`, вставляється повідомлення моделі.
   - Додано `beginRetryForPlant(id: Long, plantId: Long): Int`: безпечний ретрай лише в межах відповідної рослини зі статусу `ERROR` у `SENDING`.
   - Додано `markSendingAsErrorForPlant(plantId: Long, errorMessage: String): Int`: маркування незавершених запитів при відновленні після перезапуску процесу суворо для конкретної рослини.

2. **`FlowersRepository` та `FlowersRepositoryImpl`:**
   - Додано методи:
     - `getChatMessages(plantId: Long): Flow<List<ChatMessageEntity>>`
     - `getChatMessageById(messageId: Long): ChatMessageEntity?`
     - `insertChatMessage(message: ChatMessageEntity): Long`
     - `updateChatMessageStatus(messageId: Long, status: MessageStatus, errorMessage: String?): Boolean`
     - `beginChatMessageRetry(messageId: Long, plantId: Long): Boolean`
     - `clearChatHistory(plantId: Long): Int`
     - `saveAssistantResponse(userMessageId: Long, modelMessage: ChatMessageEntity): Long`
     - `recoverStaleSendingMessages(plantId: Long): Int`
     - `isPlantChatSending(plantId: Long): Boolean`
     - `withChatSending(plantId: Long, block: suspend () -> T): T`
   - Координація життєвого циклу повідомлень та усунення TOCTOU-гонки між `sendMessage()`/`retryMessage()` та `recoverStaleSendingMessages()`:
     - Впроваджено ізольовану по рослинах синхронізацію через `plantMutexes: ConcurrentHashMap<Long, Mutex>()`.
     - `withChatSending(plantId)` під м'ютексом рослини реєструє початок відправки в `activeSendingCounters` ще ДО запису репліки у статусі `SENDING` у SQLite і звільняє стан у `finally` під `withContext(NonCancellable)` лише тоді, коли репліка вже досягла фінального статусу (`SENT` або `ERROR`).
     - `recoverStaleSendingMessages(plantId)` перевіряє `isPlantChatSending(plantId)` під тим самим м'ютексом рослини: якщо відправка активна, відновлення повертає 0 і ніколи не зачіпає активні репліки.
     - Різні рослини мають незалежні м'ютекси і лічильники, тому чати різних рослин ніколи не блокують один одного.
     - Після реального збою процесу (`process death`) пам'ять скидається, лічильник дорівнює нулю, і `recoverStaleSendingMessages` коректно переводить осиротілі `SENDING` повідомлення в `ERROR`.

3. **`ChatViewModel` (`com.flowers.app.ui.chat`):**
   - Створено `ChatUiState(plant, messages, isSending, isLoading, errorMessage)`.
   - Синхронний дебаунс: прапорець `_uiState.value.isSending` блокує повторні виклики `sendMessage` та `retryMessage` синхронно в тому самому кадрі ще до запуску та перемикання корутин.
   - Координація з репозиторієм: усі операції `sendMessage` та `retryMessage` обгорнуто у `repository.withChatSending(plantId)`, що унеможливлює помилкове переведення активного повідомлення у статус `ERROR` іншим екземпляром ViewModel чи процесом відновлення.
   - Серіалізація та усунення гонки в `clearChat()`: гарантоване виконання `clearChatHistory` після скасування `activeJob` та `previousClearJob`, очікування `clearJob` у `sendMessage()`/`retryMessage()`, прив'язка очищення статусу `isSending` до монотонного `activeRequestToken`.
   - Реактивне оновлення: стан підписується на `getChatMessages(plantId)` та `getPlantFlowById(plantId)`.
   - Перевірка існування рослини перед відправкою (захист від `SQLiteConstraintException` при каскаді).
   - Захист від падінь при винятках SQLite/Room через перехоплення `Exception` із переведенням `USER` у `ERROR` ще всередині `withChatSending`.
   - Автоматичне відновлення після `process death` для поточної рослини при створенні ViewModel з очікуванням завершення відновлення перед новими запитами.
   - Валідація введення (ігнорування порожніх рядків, ліміт `MAX_QUESTION_LENGTH = 500`).
   - Обробка типів результатів `PlantChatResult` (Success, Offline, RateLimited, Timeout, InvalidResponse, ServiceError).
   - Безпечне прокидання `CancellationException`.
   - Фабрика `ChatViewModel.provideFactory(plantId, repository, language)`.

4. **Тестування:**
   - `ChatViewModelTest`: 25 вичерпних сценаріїв (синхронний дебаунс `sendMessage` та `retryMessage`, блокування подвійного кліку в одному кадрі, збереження моделі, ретраї, очищення із серіалізацією та очікуванням `clearJob`, негайний send/retry після `clearChat`, відновлення після збою процесу, паралельні одночасні чати двох рослин, перевірка null-рослини, ліміт символів 500, безпечне прокидання `CancellationException`, обробка винятків БД, детермінований тест усунення гонки між `sendMessage` та `recoverStaleSendingMessages`, детермінований тест усунення гонки між `retryMessage` та `recoverStaleSendingMessages`).
   - `ChatMessageDaoTest`: 3 розширених інструментальних тести (атомарне збереження, відхилення не-USER та не-SENDING реплік, безпечний ретрай з ізоляцією за `plantId`, відновлення незавершених повідомлень).
   - `FlowersRepositoryChatTest`: 8 детальних тестів для всіх методів чату в репозиторії, включно з `activeSendingCounters`, перехресними викликами, координацією `withChatSending` з відновленням на одній рослині та повною ізоляцією різних рослин.
   - Fakes оновлено у `DiagnosisViewModelTest`, `PlantsViewModelTest`, `TodayViewModelTest`.
   - Усі 321 JVM unit-тестів пройшли успішно (100% pass).
   - Інструментальні тести `ChatMessageDaoTest`: 13/13 PASSED на емуляторі `small_phone` (ізольована тестова SQLite БД), загальний набір Room v4 / Chat: 49/49 PASSED.

---

## 7. Завершення STEP 3 (PR #41 злито в main)

- **Результати Кроку 3:**
  - Реалізовано `ChatViewModel` та реактивний стан `ChatUiState`.
  - Синхронний дебаунс: `isSending` блокує подвійні кліки в одному кадрі.
  - Повна серіалізація `clearJob` та `recoveryJob`.
  - Захист від гонок через монотонний лічильник `activeRequestToken`.
  - Координація життєвого циклу через `withChatSending(plantId)` та ізольовані м'ютекси `plantMutexes: ConcurrentHashMap<Long, Mutex>()`.
  - Усунено TOCTOU-гонку при паралельному виклику відновлення підвислих реплік.
  - Атомарне збереження відповідей моделі в `ChatMessageDao.saveAssistantResponse`.
  - 321/321 JVM юніт-тестів та 13/13 інструментальних тестів на емуляторі пройдено.
  - PR #41 злито в `main` комітом `cfedf3a`.

---

## 8. Наступний етап: TASK-056 / PHOTO ARCHITECTURE (ПРОЄКТ ПІДГОТОВЛЕНО, ОЧІКУЄ ПОГОДЖЕННЯ)

> **Статус:** ПРОЄКТ ПІДГОТОВЛЕНО (v1.2), ОЧІКУЄ ДОЗВОЛУ ВЛАСНИКА НА ЗЛИТТЯ PR #42 (2026-11-28)
> **Детальний архітектурний документ:** [PHOTO_ARCHITECTURE_PROPOSAL.md](../architecture/PHOTO_ARCHITECTURE_PROPOSAL.md)
> **Мета:** Проєктування архітектури вкладень (фотографій) для персонального мультимодального чату.

### Обов'язкові продуктові вимоги Власника:
1. **Додавання одного фото до повідомлення:** користувач може прикріпити одне зображення до свого запитання.
2. **Джерело фото:** підтримка як системної камери, так і вибору з галереї.
3. **Локальне збереження:** надійне збереження файлу фотографії у внутрішнє сховище (`filesDir`) та прив'язка до локальної історії `ChatMessageEntity`.
4. **Мультимодальний контекст Gemini:** передавання зображення разом із текстовим промптом та паспортом рослини до моделі `gemini-3.5-flash-lite`.
5. **Надійність (Retry):** можливість повторити невдалу відправку (`retryMessage`) без втрати прикріпленого зображення.
6. **Порівняльний аналіз:** архітектурна можливість у майбутньому порівнювати нові та попередні фотографії рослини в динаміці.

### Результати архітектурного проєктування:
- Розроблено та задокументовано порівняльний аналіз: Варіант A (поля в `ChatMessageEntity`) проти Варіанту B (окрема таблиця `chat_attachments`).
- Рекомендовано та погоджено Варіант B як найбільш масштабований та чистий для збереження даних без повторних міграцій у майбутньому.
- Спроєктовано безпечну адитивну міграцію Room v4 → v5 (`MIGRATION_4_5`).
- Визначено двофазний протокол запису файлу та бази даних (Two-Phase Protocol): атомарний запис через `.tmp`, захист canonical path, повний Orphan Cleanup, синхронізація очищення при видаленні під `plantMutex`.
- Зафіксовано правила Backup: виключення `chat_photos` з Android Auto Backup (`backup_rules.xml`), безпечний плейсхолдер для відсутніх файлів у UI.
- Узгоджено неблокуючу конкурентність `withChatSending`: м'ютекс не блокує мережевий виклик Gemini; визначено поведінку при скасуванні, очищенні та видаленні рослини; сформовано список обов'язкових регресійних тестів.
- Зафіксовано емпіричне вимірювання токенів через `countTokens` у тестах Free Tier без платного тарифу та без змін у Firebase/Billing.
- Сформовано план реалізації у вигляді 5 невеликих PR після поточного PR #42: PR №43 (Room v5), PR №44 (ChatPhotoManager), PR №45 (Gemini), PR №46 (ChatViewModel), PR №47 (STEP 4: UI/Навігація).
- Рішення зафіксовано у `OWNER_INBOX.md` ([INBOX-015]).
- PR #42 успішно злито в `main` комітом `595601d`.

## 8. Хід робіт: PR №43 — Room v5 Schema & Migration (2026-11-28)

> ⚠️ **Обмеження:** Реалізація PR №43 обмежена виключно базою даних Room v5 (таблиця `chat_attachments`, `MIGRATION_4_5`, схема `5.json`, DAO та тести міграції/каскаду). До окремих рішень Власника заборонено реалізовувати `ChatPhotoManager` (PR №44), змінювати `GeminiPlantService` (PR №45), змінювати `ChatViewModel` (PR №46) чи створювати `PlantChatScreen` (PR №47).

- **Гілка:** `feat/task-056-photo-room-v5`, створена від `main` (`595601d` після злиття PR #42).
- **Схема бази даних:**
  - `ChatAttachmentEntity`: сутність таблиці `chat_attachments` (id, messageId, filePath, mimeType, fileSizeBytes, width, height, createdAt; FK на `chat_messages(id)` ON DELETE CASCADE; індекс `index_chat_attachments_messageId`).
  - `ChatMessageWithAttachments`: агрегована модель із `@Relation` та властивістю `primaryPhoto`.
  - `ChatAttachmentDao`: методи вставки (ABORT), вибірки по одному (з детермінованим `ORDER BY id ASC LIMIT 1`) та групі повідомлень, вибірки для конкретної рослини, запити шляхів файлів для очищення дисків (`getAllFilePathsForPlant`, `getAllStoredAttachmentPaths`), видалення `deleteByMessageId`, спостереження.
  - `ChatMessageDao`: додано `@Transaction observeMessagesWithAttachments(plantId)` та `getMessagesWithAttachments(plantId)` із збереженням хронологічного сортування (`ORDER BY timestamp ASC, id ASC`).
  - `MIGRATION_4_5`: суто адитивна міграція (CREATE TABLE / CREATE INDEX), зареєстрована у `FlowersDatabase` v5. Експортовано Room schema `app/schemas/com.flowers.app.data.local.FlowersDatabase/5.json`.
- **Тестування та верифікація:**
  - JVM Unit Tests: **325 / 325 PASSED** (100% успішно) включно з `MigrationTest` (міграція 4->5, відповідність схемі `5.json`, перевірка foreign keys та індексів, перевірка каскадного видалення при видаленні повідомлення та при видаленні рослини).
  - Android Instrumented Tests: **62 / 62 PASSED** на емуляторі `small_phone` (Android 16 API 36, `emulator-5554`, 0 failed, 0 skipped) включно з `RoomMigrationV4ToV5Test` (збереження plants, care_events, catalog_plants, care_pods, chat_messages; каскадне видалення окремого повідомлення та видалення рослини) та `ChatAttachmentDaoTest` (відхилення некоректних FK, batch-вставка, plant isolation, живий Flow collector).
  - Релізний бандл: `bundleRelease` на Adoptium JDK 21 — BUILD SUCCESSFUL (AAB 17.7 МБ).
- **Статус:** PR №43 підготовлено та передано на незалежний QA-аудит.

### Запис журналу сесії 028 (за §5.3 Конституції):
- **Зроблено:** реалізовано схему Room v5 (`ChatAttachmentEntity`, `ChatMessageWithAttachments`, `ChatAttachmentDao`, `ChatMessageDao`, `MIGRATION_4_5`, схема `5.json`), розширено тести міграції (JVM та Android instrumented на `small_phone`), відкрито PR #43.
- **Стан:** тести 100% зелені (325 JVM, 60 instrumented), release bundle зібрано успішно (17.7 МБ), GitHub Actions CI run `46107085480` — SUCCESS.
- **Зупинився на:** усунення зауважень незалежного QA аудиту для PR #43.
- **Наступний крок:** отримання фінального вердикту `QA Verified` від незалежного субагента.
- **Увага:** не зливати PR №43 без `QA Verified` та явного схвалення Власника; наступні етапи (PR №44..47) залишаються суворо заблокованими.






