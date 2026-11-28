# FLOWERS 2.0 — АРХІТЕКТУРНИЙ ПРОЄКТ: МУЛЬТИМОДАЛЬНИЙ ШІ-ЧАТ РОСЛИНИ (TASK-056 / PHOTO ARCHITECTURE)

> **Статус документа:** ОЧІКУЄ ПОГОДЖЕННЯ ВЛАСНИКА (Architecture Proposal)
> **Дата складання:** 2026-11-28
> **Версія:** 1.1 (Оновлено за результатами незалежного QA-аудиту)
> **Проєкт:** Flowers 2.0 MVP (ADR-021)
> **Відповідальний:** Агент 0 (Архітектор / Керівник)

---

## 1. Загальний огляд та стратегічні цілі

Під час реалізації **TASK-056 / STEP 3** Власником було затверджено стратегічну зміну продуктового плану:
> **Рішення Власника (2026-11-27):** Додавання фотографій безпосередньо під час персональної консультації є обов'язковою функцією Flowers 2.0 MVP, а не другорядним розширенням.

Цей документ визначає цілісну архітектуру підтримки фотографій у персональному чаті рослини перед стартом інтерфейсного етапу (STEP 4).

### 1.1. Базові продуктові вимоги
1. **Одне фото на повідомлення:** користувач може додати одне фото до свого запитання в межах початкового MVP.
2. **Гнучкість введення та контракти запитів:**
   - **Текст + фото:** користувач ставить конкретне запитання («Що це за білі цятки на листі?») і додає фото.
   - **Тільки фото:** користувач надсилає знімок без тексту. У `ChatViewModel` та `FlowersRepository` при порожньому рядку запитання автоматично підставляється стандартний локалізований запит: *«Оціни, будь ласка, стан моєї рослини за цим фото»*. У базі даних та історії промпту це формує валідну репліку, запобігаючи передачі порожнього тексту.
   - **Тільки текст:** чинний текстовий чат (STEP 2 та STEP 3) працює без змін і без зайвого споживання ресурсів.
3. **Джерела фото:** вибір з системної галереї (Android Photo Picker / `ActivityResultContracts.PickVisualMedia`) або швидкий знімок із системної камери через `CameraCaptureManager` та `FileProvider`.
4. **Попередній перегляд (Preview):** користувач бачить мініатюру прикріпленого фото до надсилання з можливістю видалити або перезняти його.
5. **Надійне постійне локальне зберігання:** усі надіслані фотографії зберігаються у захищеному внутрішньому сховищі (`filesDir`) та доступні для миттєвого перегляду в історії чату без інтернету.
6. **Мультимодальний контекст Gemini:** фото передається до `gemini-3.5-flash-lite` разом із паспортом рослини, історією догляду та останніми повідомленнями.
7. **Незмінність вкладення при Retry:** у разі збою мережі користувач може повторити надсилання («Спробувати знову») без втрати прикріпленого знімка та без повторного вибору файлу.
8. **Безпечне очищення ресурсів:** очищення історії чату або видалення рослини гарантовано видаляє всі пов'язані графічні файли з диска, запобігаючи витокам пам'яті накопичувача.
9. **Готовність до майбутнього порівняння фото (Photo Comparison Ready):** архітектура проєктується так, щоб у майбутньому без деструктивних міграцій підтримати надсилання кількох фото для порівняльного аналізу динаміки одужання рослини («до і після»).

---

## 2. Порівняльний аналіз архітектур локального зберігання

Для зв'язку реплік чату та графічних вкладень розглянуто два ключові варіанти:

### Варіант A: Розширення `ChatMessageEntity` додатковими колонками вкладення
У таблицю `chat_messages` додаються поля:
```kotlin
val imageUri: String? = null,
val imageWidth: Int = 0,
val imageHeight: Int = 0,
val imageSizeBytes: Long = 0L
```

### Варіант B: Окрема таблиця `chat_attachments` із зовнішнім ключем 1-до-багатьох
Створюється окрема сутність `ChatAttachmentEntity`, прив'язана до `chat_messages(id)` через `ForeignKey(onDelete = CASCADE)`:
```kotlin
@Entity(
    tableName = "chat_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class ChatAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val filePath: String,
    val mimeType: String = "image/jpeg",
    val fileSizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
```

### Порівняльна таблиця альтернатив

| Критерій | Варіант A (Поля в `ChatMessageEntity`) | Варіант B (Окрема таблиця `chat_attachments`) |
| :--- | :--- | :--- |
| **Складність запитів Room** | **Мінімальна.** `SELECT * FROM chat_messages` повертає готову репліку разом зі шляхом до фото. Жодних JOIN чи `@Relation`. | **Помірна.** Потребує обгортки `@Relation` (`ChatMessageWithAttachments`) для Flow у UI або складеного запиту. |
| **Масштабованість на N фото (порівняння "до/після")** | **Низька.** Обмежено одним фото на повідомлення. Для підтримки 2 фото потрібна нова міграція схеми або парсинг рядка. | **Відмінна (10/10).** Схема спочатку готова до зв'язку 1:N (1, 2 або більше фото на повідомлення) без зміни БД у майбутньому. |
| **Безпека міграції Room v4 → v5** | **Висока.** Прості `ALTER TABLE chat_messages ADD COLUMN imageUri TEXT DEFAULT NULL`. | **Висока.** Суто адитивна операція: `CREATE TABLE IF NOT EXISTS chat_attachments`. Таблиця `chat_messages` не змінюється взагалі! |
| **Сумісність із кодом STEP 3** | **Висока (100%).** Чинні методи DAO (`saveAssistantResponse`, `getLastSentMessages`) повертають `ChatMessageEntity` без жодних змін. | **Висока.** Чинний `ChatMessageDao` залишається працездатним; для UI додається спостереження `observeMessagesWithAttachments`. |
| **Каскадне видалення зв'язків** | Працює автоматично (поле в тому самому рядку). | Працює автоматично на рівні SQLite завдяки `FOREIGN KEY ... ON DELETE CASCADE`. |
| **Збереження чистих реплік ШІ (MODEL)** | Стовпчики `imageUri` у репліках асистента завжди `NULL` (витрата пам'яті SQLite мізерна, але таблиця денормалізована). | Повна реляційна чистота: вкладення існують лише для тих реплік, де вони реально є. |
| **Обсяг автоматичних тестів** | Менший: ~15 нових юніт-тестів. | Середній: ~25–30 нових юніт-тестів та інструментальних тестів зв'язків. |

### Рекомендація Архітектора: **Варіант B (Окрема таблиця з обмеженням MVP до 1 фото)**
Чому рекомендується саме **Варіант B**:
1. **Вимога Власника щодо порівняння фото:** Власник прямо вказав обов'язкову вимогу: *«Підготовлена можливість майбутнього порівняння старих і нових фотографій»*. Спроєктувати це у Варіанті A без повторної міграції Room у майбутньому неможливо. Варіант B вирішує це завдання раз і назавжди: у MVP ми обмежуємо на рівні `ViewModel/Repository` кількість вкладень до 1, але сама база даних уже підтримує довільну кількість фотографій.
2. **Абсолютна безпека міграції:** Таблиця `chat_messages` (яку щойно верифікували у STEP 3) **взагалі не зазнає змін (`ALTER TABLE` не потрібен)**. Додається лише нова ізольована таблиця `chat_attachments`. Будь-який ризик пошкодити збережені текстові репліки дорівнює нулю.
3. **Елегантний доступ:** Для відображення в UI створюється POJO `ChatMessageWithAttachments` із `@Relation`, а для операцій збереження — атомарні транзакції Room.

---

## 3. Схема даних Room v5 та план міграції

### 3.1. Структура сутностей

```kotlin
// Нова сутність: вкладення репліки персонального чату
@Entity(
    tableName = "chat_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["messageId"])
    ]
)
data class ChatAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val filePath: String,
    val mimeType: String = "image/jpeg",
    val fileSizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

// Агрегована модель для UI
data class ChatMessageWithAttachments(
    @Embedded val message: ChatMessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "messageId"
    )
    val attachments: List<ChatAttachmentEntity> = emptyList()
) {
    /** Допоміжна властивість для MVP: перше фото повідомлення (якщо є) */
    val primaryPhoto: ChatAttachmentEntity? get() = attachments.firstOrNull()
}
```

### 3.2. SQL-скрипт адитивної міграції `MIGRATION_4_5`

```sql
-- Створення нової таблиці вкладень чату (Room v5)
CREATE TABLE IF NOT EXISTS `chat_attachments` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `messageId` INTEGER NOT NULL,
    `filePath` TEXT NOT NULL,
    `mimeType` TEXT NOT NULL,
    `fileSizeBytes` INTEGER NOT NULL,
    `width` INTEGER NOT NULL,
    `height` INTEGER NOT NULL,
    `createdAt` INTEGER NOT NULL,
    FOREIGN KEY(`messageId`) REFERENCES `chat_messages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);

-- Індекс для миттєвих вибірок вкладень за messageId
CREATE INDEX IF NOT EXISTS `index_chat_attachments_messageId` ON `chat_attachments` (`messageId`);
```

### 3.3. Гарантії збереження даних
- Таблиці `plants`, `care_events`, `catalog_plants`, `care_pods` та `chat_messages` **не зазнають жодних модифікацій**.
- Жодних операцій `DROP TABLE`, `ALTER TABLE` чи `DELETE`.
- Усі наявні рослини, налаштування поливу, історія догляду та всі текстові повідомлення попередніх кроків зберігаються на 100%.

---

## 4. Життєвий цикл фотографії (End-to-End Photo Lifecycle)

```
[ Камера / Галерея ]
        │
        ▼ (Тимчасовий URI / кеш камери)
[ Попередній перегляд у UI ] ──── (Користувач скасував) ────► [ Видалення тимчасового файлу камери ]
        │
        ▼ (Користувач натиснув «Надіслати»)
[ Обробка через ImageProcessor ]
        ├─ 1. Двоетапне декодування (inSampleSize, захист від OOM)
        ├─ 2. Масштабування до макс. 1536 px (збереження деталей листя/шкідників)
        ├─ 3. Нормалізація орієнтації (Matrix за EXIF-тегом)
        ├─ 4. Стиснення у JPEG 85% (НАТИВНЕ ВИДАЛЕННЯ EXIF/GPS)
        └─ 5. Безпечне збереження у filesDir/chat_photos/plant_<id>/
        │
        ▼ (Двофазний протокол під getPlantMutex(plantId))
[ Вставка у Room в одній транзакції database.withTransaction ]
        ├─ 1. ChatMessageEntity (role=USER, status=SENDING)
        └─ 2. ChatAttachmentEntity (messageId=..., filePath=...)
        │
        ▼
[ Мультимодальний виклик GeminiPlantService ]
        ├─ Читання файлу фото з диска у Bitmap (перевірка на null/битий файл)
        ├─ Формування PlantChatPrompt (паспорт + догляд + історія + питання)
        └─ Відправка: systemInstruction + content { image(bitmap); text(prompt) }
        │
   ┌────┴────────────────────────┐
   ▼ (Успіх)                     ▼ (Помилка / Офлайн / Таймаут)
[ saveAssistantResponse ]    [ updateStatus(id, ERROR) ]
   ├─ userMsg: SENT             ├─ userMsg: ERROR
   └─ modelMsg: SENT            ├─ вкладення залишається у БД і на диску!
                                └─ При натисканні Retry: той самий файл надсилається повторно
```

### 4.1. Детальний опис етапів

#### 1. Джерела знімків
- **Галерея:** Використовує офіційний системний контракт `ActivityResultContracts.PickVisualMedia` з фільтром `ImageOnly`. Не потребує небезпечних дозволів на читання сховища у маніфесті (Android 13+ Photo Picker / зворотна сумісність через Google Play Services).
- **Камера:** Використовує `CameraCaptureManager` та `FileProvider`. Тимчасовий файл створюється в `cacheDir/camera/`. Якщо користувач скасовує зйомку або закриває діалог, тимчасовий файл негайно видаляється. Префікс імені тимчасового файлу уніфікується (`chat_cam_...`).

#### 2. Очищення приватних метаданих (EXIF/GPS Stripping)
- На етапі обробки `AndroidImageDecoderEngine` зчитує лише тег `TAG_ORIENTATION` для коректного повороту через `Matrix`.
- При виклику `bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)` створюється новий чистий потік байтів JPEG.
- **Усі геомітки (GPS), дата зйомки, серійні номери камери та пристрою повністю видаляються.** До моделі Gemini та у внутрішнє сховище потрапляє лише чисте піксельне зображення.

#### 3. Обмеження розміру та захист оперативної пам'яті
- Для персонального чату використовується профіль якості `ImageProfile.DiseaseDiagnosis` (або виділений профіль `ImageProfile.ChatConsultation`):
  - Максимальний вимір: `1536 px` по довшій стороні (оптимально для виявлення павутинного кліща, щитівки та дрібних плям септоріозу).
  - Якість: `85%`.
  - Формат: `JPEG`.
  - Оціночний розмір файлу на диску: орієнтовно `200–500 КБ` (фактичний розмір залежить від складності сцени).
- `ImageProcessor` виконує двоетапне декодування (`inJustDecodeBounds = true`), вираховує ступінь зсуву `inSampleSize` як ступінь 2, тому оперативна пам'ять при декодуванні мінімізується навіть для знімків 48–108 МП. Точні витрати пам'яті та час обробки підлягають вимірюванню на фізичному пристрої.

#### 4. Локація та збереження після перезапуску Android
- Зображення зберігаються у **внутрішньому постійному сховищі застосунку**:
  `Context.filesDir/chat_photos/plant_<plantId>/chat_<timestamp>_<uuid>.jpg`.
- Каталог `filesDir` захищений пісочницею Android (інші застосунки не мають доступу), не очищується системою при браку пам'яті (на відміну від `cacheDir`) і зберігається після перезавантаження смартфона.
- **Політика резервного копіювання:** За поточними `backup_rules.xml` та `data_extraction_rules.xml` бекапуються лише бази даних SQLite та налаштування; каталог `filesDir/chat_photos/` виключено з хмарного автобекапу для економії квоти користувача Google Drive. У разі відновлення на новому пристрої UI чату коректно відображає плейсхолдер *«Фото недоступне (локальний файл відсутній)»* без збоїв.

#### 5. Вирішення проблеми відсутності спільної транзакції FS + SQLite (Two-Phase Protocol)
Файлова система та SQLite не мають спільного координатора транзакцій (2PC). Для запобігання втратам та появі файлів-сиріт діє суворий протокол під координацією м'ютексу рослини `getPlantMutex(plantId)`:
1. **Спочатку файл, потім БД:** спочатку `ImageProcessor` оптимізує та зберігає файл у `filesDir/chat_photos/plant_<plantId>/`.
2. Якщо запис файлу зазнав невдачі (диск переповнений, I/O помилка) — операція негайно переривається. У Room нічого не вставляється, користувач бачить зрозумілу помилку. «Порожніх» повідомлень без файлу не виникає.
3. Якщо файл збережено — у транзакції `database.withTransaction` атомарно додається репліка користувача у статусі `SENDING` та запис про вкладення в `chat_attachments`.
4. **Служба повного очищення сиріт (Orphan Cleanup Service):**
   - Під час старту застосунку (у фоновому `CoroutineScope`) або при очищенні чату викликається підпрограма очищення.
   - Сканується **весь каталог `filesDir/chat_photos/`** (усі підпапки `plant_*`, включно з папками вже видалених рослин).
   - Отримується повний набір актуальних шляхів із таблиці `chat_attachments`.
   - Будь-який файл на диску, створений понад 24 години тому, який відсутній у базі даних (наслідок раптового збою живлення/крашу процесу до вставки в Room), безпечно видаляється. Порожні каталоги рослин також видаляються.

#### 6. Синхронізація очищення при видаленні чату та рослини
Для усунення race condition між файловими операціями та новими повідомленнями:
- **Очищення історії чату (`clearChatHistory(plantId)`):**
  Виконується під блокуванням `getPlantMutex(plantId)`:
  1. Отримуються всі шляхи файлів для `plantId` з `chat_attachments`.
  2. У транзакції Room виконується `chatMessageDao.deleteAllForPlant(plantId)` (каскадно видаляє репліки та вкладення).
  3. Фізичні файли та каталог `chat_photos/plant_<plantId>/` видаляються з диска.
- **Видалення рослини (`deletePlant(plant)`):**
  Виконується під блокуванням `getPlantMutex(plant.id)`:
  1. Скасовується активна робота надсилання повідомлень, якщо вона тривала.
  2. У базі даних видаляється рослина (`plantDao.deletePlant(plant)`), що через каскад SQLite очищує повідомлення та вкладення.
  3. Каталог фотографій рослини `chat_photos/plant_<plant.id>/` видаляється з диска.

---

## 5. Інтеграція з Gemini (Firebase AI Logic)

### 5.1. Контракт сервісу та відокремлення інструкцій

Сервіс `GeminiPlantService` розширюється мультимодальним викликом із чітким дотриманням архітектури STEP 2:

```kotlin
suspend fun sendPlantChatMessage(
    prompt: PlantChatPrompt,
    imageFile: File? = null,
    plantId: Long = 0L
): PlantChatResult
```

- **Суворе відокремлення системних інструкцій:**
  `prompt.systemInstruction` передається виключно через офіційний параметр конфігурації моделі:
  ```kotlin
  val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
      modelName = GEMINI_MODEL, // "gemini-3.5-flash-lite"
      generationConfig = chatGenerationConfig,
      systemInstruction = content { text(prompt.systemInstruction) }
  )
  ```
  *(Примітка: для моделі `gemini-3.5-flash-lite` параметри `temperature`, `top_p` та `top_k` є застарілими (deprecated) згідно з офіційним Gemini API guide і не повинні використовуватись у нових конфігураціях).*
- **Формування тіла запиту (`generateContent`):**
  - Якщо `imageFile == null`: викликається чисто текстовий запит `model.generateContent(prompt.userPrompt).text`. Текстові чати працюють із нульовим оверхедом!
  - Якщо `imageFile != null`: файл декодується в локальний `Bitmap` (із захистом від битих файлів: перевірка на null після декодування) і надсилається єдиним вмістом:
    ```kotlin
    val inputContent = content {
        image(bitmap)
        text(prompt.userPrompt)
    }
    model.generateContent(inputContent).text
    ```
- **Захист від надсилання попередніх фото з історії:**
  В історії діалогу (попередні репліки `chatHistory`) передається **виключно текст попередніх запитань та відповідей**. Фотографії з минулих повідомлень повторно до Gemini НЕ надсилаються. Якщо користувач надсилав лише фото без власного тексту, у промпті історії передається маркер `[Користувач надіслав фото: Оціни, будь ласка, стан моєї рослини за цим фото]`. Це критично для:
  1. Збереження квоти токенів.
  2. Запобігання перевантаженню моделі застарілим візуальним контекстом.
  3. Стабільної та передбачуваної швидкості відповіді моделі.

### 5.2. Реалістичний аналіз токенів та квот
- **Використовувана модель:** `gemini-3.5-flash-lite` (через Firebase AI Logic SDK, Google AI backend).
- **Підтримка мультимодальності:** Підтверджена офіційною документацією Google Gemini API. Моделі Flash підтримують одночасне передавання тексту та зображень.
- **Офіційна математика токенів зображення (gemini-api-guides/tokens.md):**
  - Зображення розміром ≤ 384 px в обох вимірах тарифікується як **258 токенів**.
  - Зображення більшого розміру розбиваються на тайли 768×768 px, де кожен тайл оцінюється у **258 токенів**.
  - Для зображення розміром 1536 px максимальна кількість тайлів становить **4** (2×2 по 768 px), що дає **4 × 258 = 1 032 токени**.
  - За замовчуванням (media resolution) для моделей нових поколінь виділяється **~1 032–1 120 токенів на одне зображення**.
  - Текстовий промпт (паспорт + історія + питання): до 1 000 токенів (~3 500 символів згідно з лімітами `PlantChatContextBuilder`).
  - **Разом на один мультимодальний запит:** орієнтовно **~2 032–2 120 токенів**.
- **Статус квот Free Tier (Дія O-1):**
  - Офіційна документація Gemini зазначає: *«Specified rate limits are not guaranteed and actual capacity may vary»*. Ліміти залежать від налаштувань проєкту в Google AI Studio та Firebase Console.
  - У проєкті залишається відкритою дія **O-1** (`TASK_BOARD.md:25`, `OWNER_INBOX.md:103`) щодо перевірки фактичних квот Власником під час внутрішнього тестування.
  - Архітектура чату проєктується з повною готовністю до лімітів: перехоплення HTTP 429 (`PlantChatResult.RateLimited`), блокування паралельних відправок через м'ютекси та збереження локального стану репліки при перевищенні ліміту.

### 5.3. Архітектурне забезпечення майбутнього порівняння двох фото
Для майбутнього функціоналу порівняння динаміки рослини («до» і «після»):
- Таблиця `chat_attachments` уже містить зв'язок 1:N. Користувач зможе прикріпити 2 фото до одного повідомлення або система підтягне фото двотижневої давнини.
- Виклик `Firebase AI Logic` підтримує кілька зображень у `content`:
  ```kotlin
  val inputContent = content {
      image(beforeBitmap) // Фото 2 тижні тому
      image(afterBitmap)  // Фото сьогодні
      text("Порівняй стан рослини на цих двох фото...")
  }
  ```
- Жодної зміни схеми бази даних у майбутньому не знадобиться!

---

## 6. Матриця надійності, відмовостійкості та безпеки

| № | Потенційний сценарій / Збій | Поведінка системи та захисні механізми | Статус у схемі |
| :- | :--- | :--- | :- |
| **1** | **Втрата інтернету після прикріплення фото** | `AndroidNetworkChecker` фіксує офлайн до відправки. Повідомлення зберігається локально зі статусом `ERROR` («Немає підключення до інтернету»). Фото залишається збереженим на диску, користувач може натиснути «Повторити», коли з'явиться зв'язок. | Захищено |
| **2** | **HTTP 429 (Rate Limit від Gemini)** | Перехоплюється `AiErrorMapper.map(e)`. Повідомлення переходить у статус `ERROR` із текстом «Перевищено ліміт запитів. Зачекайте хвилину». Фото зберігається, дублікатів репліки не створюється. | Захищено |
| **3** | **Подвійне натискання «Надіслати» (Double Tap)** | Синхронний дебаунс у `ChatViewModel` (`_uiState.value.isSending` блокує вхід у першому ж кадрі) + м'ютекс `withChatSending(plantId)` у репозиторії повністю виключають дублювання операцій. | Захищено |
| **4** | **Process Death посеред обробки запиту** | При наступному запуску екрана чату спрацьовує `recoverStaleSendingMessages(plantId)` (перевірено у STEP 3). Усі репліки `SENDING` переходять у `ERROR`. Прикріплене фото залишається на місці, кнопка Retry відновлює відправку. | Захищено |
| **5** | **Користувач очистив чат під час запиту** | Серіалізація через `clearJob`: якщо виконується очищення, новий запит чекає або скасовується. Якщо запит повернувся після очищення, `saveAssistantResponse` бачить, що вихідного `userMessageId` більше немає в базі, і безпечно скидає запис (повертає -1L). Файли очищуються під м'ютексом рослини. | Захищено |
| **6** | **Рослину видалено під час активного запиту** | `deletePlant` під `getPlantMutex(plantId)` скасовує активне надсилання. `saveAssistantResponse` повертає -1L, бо запису `plants` більше не існує. Каскадне видалення Room очищує таблиці, а репозиторій видаляє каталог фото рослини. | Захищено |
| **7** | **Файл фото пошкоджено, битий JPEG або видалено ззовні** | Перед декодуванням у `Bitmap` сервіс перевіряє `file.exists() && file.canRead()`, а результат `BitmapFactory.decodeFile` перевіряється на `null`. Якщо декодування не вдалося — повертається контрольована помилка `PlantChatResult.ServiceError("Не вдалося завантажити прикріплене фото")`. Застосунок не падає з `NullPointerException`. | Захищено |
| **8** | **Повтор відправки (Retry) після помилки з перевіркою файлу** | Викликається `beginChatMessageRetry(id, plantId)`, який змінює статус репліки `ERROR -> SENDING`. Якщо для повідомлення в базі існує `chat_attachments`, але фізичний файл на диску відсутній, репозиторій НЕ відправляє запит мовчки як текстовий, а повертає помилку `PlantChatResult.ServiceError("Прикріплений файл фото не знайдено на пристрої. Будь ласка, зробіть нове фото")`. | Захищено |
| **9** | **Одночасно відкриті чати двох різних рослин** | Повна ізоляція: каталоги на диску розділені (`plant_<id>`), м'ютекси `ConcurrentHashMap<Long, Mutex>()` розділені за `plantId`, транзакції в базі даних строго ізольовані. | Захищено |
| **10** | **Спроба прикріпити надвелике фото (48–108 МП)** | `AndroidImageDecoderEngine` вираховує `inSampleSize` за межами зображення без виділення пам'яті під пікселі, запобігаючи `OutOfMemoryError`. | Захищено |

---

## 7. Контракти інтерфейсів та компонентів

### 7.1. DAO: `ChatAttachmentDao`

```kotlin
@Dao
abstract class ChatAttachmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAttachment(attachment: ChatAttachmentEntity): Long

    @Query("SELECT * FROM chat_attachments WHERE messageId = :messageId LIMIT 1")
    abstract suspend fun getAttachmentForMessage(messageId: Long): ChatAttachmentEntity?

    @Query("SELECT * FROM chat_attachments WHERE messageId IN (:messageIds)")
    abstract suspend fun getAttachmentsForMessages(messageIds: List<Long>): List<ChatAttachmentEntity>

    @Query("SELECT filePath FROM chat_attachments WHERE messageId IN (SELECT id FROM chat_messages WHERE plantId = :plantId)")
    abstract suspend fun getAllFilePathsForPlant(plantId: Long): List<String>

    @Query("SELECT filePath FROM chat_attachments")
    abstract suspend fun getAllStoredAttachmentPaths(): List<String>

    @Query("DELETE FROM chat_attachments WHERE messageId = :messageId")
    abstract suspend fun deleteByMessageId(messageId: Long): Int
}
```

### 7.2. Оновлення `ChatMessageDao` та збереження в репозиторії

```kotlin
// Новий метод спостереження за повідомленнями разом із вкладеннями
@Transaction
@Query("SELECT * FROM chat_messages WHERE plantId = :plantId ORDER BY timestamp ASC, id ASC")
abstract fun observeMessagesWithAttachments(plantId: Long): Flow<List<ChatMessageWithAttachments>>
```

Атомарне збереження репліки з вкладенням реалізується у `FlowersRepositoryImpl` через `database.withTransaction`:
```kotlin
suspend fun insertUserMessageWithAttachment(
    message: ChatMessageEntity,
    attachmentFile: File?,
    width: Int = 0,
    height: Int = 0,
    sizeBytes: Long = 0L
): Long = database.withTransaction {
    val messageId = chatMessageDao.insertMessage(message)
    if (messageId > 0 && attachmentFile != null) {
        val attachment = ChatAttachmentEntity(
            messageId = messageId,
            filePath = attachmentFile.absolutePath,
            width = width,
            height = height,
            fileSizeBytes = sizeBytes
        )
        chatAttachmentDao.insertAttachment(attachment)
    }
    messageId
}
```

### 7.3. Контракт `FlowersRepository`

```kotlin
interface FlowersRepository {
    // ... чинні методи ...

    /** Спостереження за повною історією повідомлень разом із вкладеннями */
    fun getChatMessagesWithAttachments(plantId: Long): Flow<List<ChatMessageWithAttachments>>

    /**
     * Надсилає репліку (текст, фото або текст+фото) до помічника.
     */
    suspend fun sendPlantChatMessage(
        plantId: Long,
        userQuestion: String,
        imageFile: File? = null,
        language: String = "uk",
        currentMessageId: Long? = null
    ): PlantChatResult

    /** Очищення файлів та історії чату рослини */
    suspend fun clearChatHistory(plantId: Long): Int
}
```

### 7.4. Стан `ChatUiState` у `ChatViewModel`

```kotlin
data class ChatUiState(
    val plant: PlantEntity? = null,
    val messages: List<ChatMessageWithAttachments> = emptyList(),
    val selectedPhotoFile: File? = null, // Тимчасове фото до надсилання (Preview)
    val isSending: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
```

---

## 8. План поетапного впровадження (PR Staging Plan)

Для дотримання Конституції та безпечної розробки впровадження розділено на 5 невеликих, суворо верифікованих PR (діапазон номерів після поточного PR #42):

```
[ PR №43: Room v5 Schema & Migration ] ────► [ PR №44: Image Storage & Lifecycle ]
                     │                                           │
                     ▼                                           ▼
[ PR №45: Gemini Multimodal Service ]  ◄─────────────────────────┘
                     │
                     ▼
[ PR №46: ChatViewModel Integration ]
                     │
                     ▼
[ PR №47: STEP 4 — UI & Navigation (PlantChatScreen) ]
```

### Етап 1: PR №43 — База даних та міграція (Room v4 → v5)
- **Склад змін:**
  - `ChatAttachmentEntity.kt`
  - `ChatMessageWithAttachments.kt`
  - `ChatAttachmentDao.kt`
  - `Migrations.MIGRATION_4_5`
  - Оновлення `FlowersDatabase` (версія 5, реєстрація сутності та DAO).
- **Тестування:**
  - JVM-тести міграції у `app/src/test/java/com/flowers/app/data/local/MigrationTest.kt` (`migrate4To5`).
  - Тести каскадного видалення у `ChatMessageDaoTest` та `ChatAttachmentDaoTest`.

### Етап 2: PR №44 — Файлове сховище та менеджер фотографій чату
- **Склад змін:**
  - `ChatPhotoManager.kt`: збереження у `filesDir/chat_photos/plant_<id>/`, санітизація імен, видалення, пошук сиріт.
  - Оновлення `ImageProcessor.kt` / `ImageProfile.kt` (додавання профілю консультації).
  - Фонова служба Orphan Cleanup при старті застосунку.
  - Оновлення `FlowersRepositoryImpl` для роботи з файлами при очищенні чату та видаленні рослини під `plantMutex`.
- **Тестування:**
  - Юніт-тести обробки файлів, перевірки відсутності path traversal, очищення сиріт.

### Етап 3: PR №45 — Мультимодальний клієнт Gemini та оновлення контексту
- **Склад змін:**
  - Оновлення `GeminiPlantService`: мультимодальний виклик `sendPlantChatMessage` з передачею `Bitmap` та ізольованою `systemInstruction` (очищення застарілих параметрів temperature).
  - Оновлення `PlantChatContextBuilder.kt`: безпечна серіалізація повідомлень без тексту (з маркером фото).
  - Оновлення `FlowersRepository.sendPlantChatMessage`.
- **Тестування:**
  - Юніт-тести з мок-відповідями Gemini (із зображенням, без зображення, битий JPEG, помилки, таймаути, перевірка `PlantSafetyGuardrail`).

### Етап 4: PR №46 — Мультимодальний `ChatViewModel`
- **Склад змін:**
  - Оновлення `ChatViewModel`: методи `attachPhoto(file)`, `removeAttachedPhoto()`, обробка порожнього тексту з дефолтним питанням.
  - Робота з `selectedPhotoFile`, Retry зі збереженим вкладенням, скасування операцій.
- **Тестування:**
  - 25+ юніт-тестів на всі стани ViewModel, дебаунс, помилки, перевірка відсутності файлу при Retry.

### Етап 5: PR №47 (STEP 4) — Користувацький інтерфейс та навігація
- **Склад змін:**
  - Створення `PlantChatScreen.kt` у Jetpack Compose.
  - Поле введення з кнопками камери/галереї, плашка попереднього перегляду фото перед надсиланням.
  - Відображення бульбашок повідомлень з фото через Coil (`AsyncImage`).
  - Діалог повноекранного перегляду фотографії.
  - Точка входу в чат із `PlantDetailScreen.kt` та маршрут у навігаційному графі (`Screen.PlantChat`).
- **Тестування:**
  - Compose UI тести, ручна перевірка на емуляторі, підготовка до перевірки Власником на телефоні.

---

## 9. Питання, винесені на рішення Власника

Питання також офіційно зафіксовані у журналі `docs/governance/OWNER_INBOX.md` ([INBOX-015]):

1. **Вибір архітектури таблиці вкладень:**
   - **Рекомендація архітектора:** Затвердити **Варіант B** (окрема таблиця `chat_attachments`). Це забезпечує чисту міграцію без зміни `chat_messages` та гарантує повну готовність до майбутнього порівняння фотографій.
   - *Питання до Власника:* Чи погоджуєте Ви використання Варіанту B?
2. **Розмір знімка та якість стиснення:**
   - **Рекомендація архітектора:** Використовувати максимальний вимір `1536 px` та якість `85% JPEG` (аналогічно діагностиці). Це дає розмір ~200–500 КБ, відмінну деталізацію для ШІ та швидке завантаження.
   - *Питання до Власника:* Чи підходить зазначений баланс якості та розміру?
3. **План поетапних PR:**
   - **Рекомендація архітектора:** Реалізувати мультимодальність невеликими кроками (PR №43 -> PR №44 -> PR №45 -> PR №46 -> PR №47 / STEP 4).
   - *Питання до Власника:* Чи затверджуєте Ви запропонований порядок PR?

---
*Документ підготовлено Агентом 0 і винесено на розгляд Власника.*
