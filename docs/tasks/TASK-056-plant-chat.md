# TASK-056 — Персональний ШІ-чат для кожної рослини з локальною історією

> **Статус:** КРОК 2 РЕАЛІЗОВАНО (ContextBuilder, GeminiPlantService, Repository, тести; очікує QA та рішення Власника)  
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
   - `bundleRelease` на JDK 21 зібрано успішно (17.6 МБ).

