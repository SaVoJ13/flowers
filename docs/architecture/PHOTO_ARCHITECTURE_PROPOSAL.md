# FLOWERS 2.0 — АРХІТЕКТУРНИЙ ПРОЄКТ: МУЛЬТИМОДАЛЬНИЙ ШІ-ЧАТ РОСЛИНИ (TASK-056 / PHOTO ARCHITECTURE)

> **Статус документа:** ОЧІКУЄ ПОГОДЖЕННЯ ВЛАСНИКА (Architecture Proposal)
> **Дата складання:** 2026-11-28
> **Версія:** 1.2 (Узгоджено з вимогами Власника: backup, безпека ФС, конкурентність withChatSending, емпіричні квоти)
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
5. **Локальне зберігання та політика резервного копіювання:** усі надіслані фотографії зберігаються у захищеному внутрішньому сховищі (`filesDir`). Фото доступні для перегляду в історії чату без інтернету на поточному пристрої.
6. **Мультимодальний контекст Gemini:** фото передається до `gemini-3.5-flash-lite` разом із паспортом рослини, історією догляду та останніми повідомленнями.
7. **Незмінність вкладення при Retry:** у разі збою мережі користувач може повторити надсилання («Спробувати знову») без втрати прикріпленого знімка та без повторного вибору файлу (за умови фізичної наявності файлу на диску).
8. **Безпечне очищення ресурсів:** очищення історії чату або видалення рослини гарантовано видаляє всі пов'язані графічні файли з диска під контролем м'ютексу, запобігаючи витокам пам'яті накопичувача та Path Traversal.
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

### Варіант B: Окрема таблиця `chat_attachments` із зовнішнім ключем 1-до-багатьох (ПОГОДЖЕНО ВЛАСНИКОМ)
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

## 4. Життєвий цикл фотографії та безпека файлової системи

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
        └─ 5. Атомарний запис: спочатку .tmp файл, потім перейменування у target
        │
        ▼ (Двофазний запис: файл на диску -> транзакція Room)
[ Вставка у Room в одній транзакції database.withTransaction ]
        ├─ 1. ChatMessageEntity (role=USER, status=SENDING)
        └─ 2. ChatAttachmentEntity (messageId=..., filePath=...)
        │
        ▼ (Мережевий виклик поза м'ютексом!)
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
- **Галерея:** Використовує офіційний системний контракт `ActivityResultContracts.PickVisualMedia` з фільтром `ImageOnly`. Не потребує небезпечних дозволів на читання сховища у маніфесті.
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

#### 4. Політика резервного копіювання (Android Auto Backup) та перенесення на інший пристрій
- **Фактичний стан конфігурації:** Чинні правила резервного копіювання проєкту (`app/src/main/res/xml/backup_rules.xml` та `data_extraction_rules.xml`) суворо обмежують бекап доменами `database` (`flowers_database.db*`) та `sharedpref` (`flowers_user_preferences.xml`). Каталог внутрішнього сховища `filesDir/chat_photos/` **НЕ копіюється** у хмару Google Drive та не передається при міграції пристрою для економії трафіку й квоти сховища.
- **Поведінка після відновлення з бекапу:**
  - При відновленні застосунку на новому смартфоні таблиці `chat_messages` та `chat_attachments` будуть відновлені (текстова історія діалогів зберігається 100%), але фізичні файли фотографій у `filesDir/chat_photos/` будуть відсутні.
  - **Вимога до UI:** Інтерфейс чату повинен бути готовий до відсутності локальних файлів. Якщо файл за шляхом `filePath` не існує, Compose UI відображає охайний інформативний плейсхолдер: *«Фото недоступне (файл не входить до резервної копії)»*. Застосунок не падає з `FileNotFoundException`.
  - Застосунок **не обіцяє** користувачеві відновлення графічних файлів, які не резервувалися.

#### 5. Двофазний протокол запису (Two-Phase Protocol) та атомарність файлових операцій
Файлова система та SQLite не мають спільного координатора транзакцій (2PC). Для запобігання втратам та появі файлів-сиріт діє суворий протокол:
1. **Атомарний запис через тимчасовий файл:**
   - Стиснений JPEG спочатку повністю записується у тимчасовий файл з унікальним іменем:
     `filesDir/chat_photos/plant_<plantId>/temp_<uuid>.tmp`.
   - Після успішного скидання потоку (`flush()`) та закриття файлу виконується атомарне перейменування/переміщення файлу (`Files.move` або `File.renameTo`) у цільове ім'я `chat_<timestamp>_<uuid>.jpg`.
   - Це гарантує, що цільовий файл ніколи не з'явиться на диску наполовину записаним або пошкодженим через раптове переривання.
2. **Перевірка Canonical Path (Захист від Path Traversal):**
   - Будь-який шлях до файлу перед відкриттям або видаленням проходить сувору перевірку:
     ```kotlin
     val baseDir = File(context.filesDir, "chat_photos").canonicalFile.toPath()
     val targetPath = File(filePath).canonicalFile.toPath()
     require(targetPath.startsWith(baseDir)) {
         "Безпекова помилка: виявлено спробу виходу за межі дозволеного каталогу: $filePath"
     }
     ```
   - Заборонено читати чи видаляти довільні шляхи з бази даних без попередньої верифікації відносно кореневого каталогу фотографій чату.
3. **Ізоляція рослин:**
   - Знімки кожної рослини зберігаються у власній піддиректорії `filesDir/chat_photos/plant_<plantId>/`. Операції для однієї рослини ніколи не торкаються каталогів інших рослин.
4. **Обробка крашу між записом файлу та транзакцією Room:**
   - Якщо процес Android буде вбито ОС рівно в інтервалі між створенням файлу на диску та успішним виконанням транзакції `database.withTransaction`:
   - У Room запис не створюється. Файл на диску залишається осиротілим.
   - Служба Orphan Cleanup під час наступного сканування виявить файл, відсутній у базі понад 24 години, і безпечно видалить його.
5. **Повторне очищення після невдалого фізичного видалення:**
   - Якщо виклик `File.delete()` завершується невдачею (блокування дескриптора ОС Windows/Android, тимчасова помилка I/O), операція видалення в базі даних не блокується.
   - Файл залишається на диску і гарантовано видаляється під час наступного прогону служби очищення сиріт.
6. **Служба повного очищення сиріт (Orphan Cleanup Service):**
   - Сканує **весь каталог `filesDir/chat_photos/`** (усі підпапки `plant_*`, включно з папками вже видалених рослин).
   - Отримує повний набір актуальних шляхів із таблиці `chat_attachments`.
   - Будь-який файл на диску, створений понад 24 години тому, який відсутній у базі даних, безпечно видаляється разом із порожніми підкаталогами.

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
  *(Примітка: для моделі `gemini-3.5-flash-lite` параметри `temperature`, `top_p` та `top_k` є застарілими (deprecated) згідно з офіційним Gemini API guide і повинні бути вилучені з `chatGenerationConfig` у PR №45).*
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
  В історії діалогу (попередні репліки `chatHistory`) передається **виключно текст попередніх запитань та відповідей**. Фотографії з минулих повідомлень повторно до Gemini НЕ надсилаються. Якщо користувач надсилав лише фото без власного тексту, у промпті історії передається маркер `[Користувач надіслав фото: Оціни, будь ласка, стан моєї рослини за цим фото]`.

### 5.2. Реалістичний аналіз токенів та квот
- **Використовувана модель:** `gemini-3.5-flash-lite` (через Firebase AI Logic SDK, Google AI backend).
- **Підтримка мультимодальності:** Підтверджена офіційною документацією Google Gemini API. Моделі Flash підтримують одночасне передавання тексту та зображень.
- **Оцінка токенів зображення (gemini-api-guides/tokens.md):**
  - За офіційною документацією Gemini API, зображення розбиваються на тайли 768×768 px по 258 токенів кожен (зображення ≤ 384 px = 258 токенів).
  - Для зображення розміром 1536 px кількість тайлів становить до 4, що дає теоретичну оцінку **~1 032 токени** (або ~1 120 токенів за стандартного media resolution).
  - Разом із текстовим контекстом запит орієнтовно становить **~2 000–2 120 токенів**.
  - **Застереження:** Ця оцінка є розрахунковою і **НЕ є жорстко гарантованою** для конкретної версії моделі.
- **Емпіричне вимірювання токенів на Free Tier:**
  - Під час дозволеного тестування передбачено пряме емпіричне вимірювання фактичної кількості токенів засобами Firebase AI Logic:
    `val tokenCount = model.countTokens(inputContent).totalTokens` або читання метаданих відповіді `response.usageMetadata?.promptTokenCount`.
  - Усі вимірювання виконуються виключно в межах безкоштовного тарифу (Free Tier), без підключення платного білінгу (Blaze plan) та без змін у Firebase/Google Cloud Console.
- **Статус квот Free Tier (Дія O-1):**
  - Згідно з `rate-limits.md`, ліміти безкоштовного тарифу є орієнтовними та залежать від проєкту (*«Specified rate limits are not guaranteed and actual capacity may vary»*).
  - У проєкті залишається відкритою дія **O-1** (`TASK_BOARD.md:25`, `OWNER_INBOX.md:103`) щодо перевірки фактичних квот Власником під час внутрішнього тестування.

---

## 6. Конкурентність, узгодження з `withChatSending` та матриця відмовостійкості

### 6.1. Принцип неблокуючого виконання (Non-blocking Network I/O)
Чинна архітектура `withChatSending(plantId)` (`FlowersRepositoryImpl.kt:167–185`) побудована на суворому правилі:
1. М'ютекс `getPlantMutex(plantId)` захоплюється **лише на мілісекунди** для атомарної перевірки та інкременту лічильника `activeSendingCounters[plantId]`.
2. Мережевий виклик Gemini (`generateContent`), який може тривати від 2 до 20 секунд, виконується **ЗА МЕЖАМИ М'ЮТЕКСУ**.
3. У блоці `finally` під `withContext(NonCancellable)` м'ютекс знову захоплюється на мілісекунди для декременту лічильника.
**М'ютекс ніколи не утримується протягом мережевого запиту!** Це запобігає зависанню UI та блокуванню паралельних операцій.

### 6.2. Взаємодія конкурентних операцій під час активного надсилання

1. **Очищення чату під час активного запиту (`clearChatHistory` під час `isPlantChatSending == true`):**
   - Користувач натискає «Очистити історію чату».
   - У `FlowersRepositoryImpl.clearChatHistory(plantId)` під короткочасним захопленням `getPlantMutex(plantId)`:
     - Отримуються актуальні шляхи файлів з `chat_attachments`.
     - Виконується транзакція Room `chatMessageDao.deleteAllForPlant(plantId)`, що видаляє повідомлення та каскадно очищує `chat_attachments`.
     - Видаляються фізичні файли та каталог `chat_photos/plant_<plantId>/`.
   - Мережевий виклик Gemini, що триває у фоні, повертає відповідь.
   - Викликається `saveAssistantResponse(userMessageId, modelMessage)`.
   - У транзакції Room метод перевіряє `getById(userMessageId)`. Оскільки повідомлення вже видалено очищенням, метод повертає `-1L` і скидає відповідь асистента без запису в базу.
2. **Видалення рослини під час активного запиту (`deletePlant` під час `isPlantChatSending == true`):**
   - `PlantsViewModel.deletePlant(plant)` викликає `FlowersRepository.deletePlant(plant)`.
   - Під короткочасним захопленням `getPlantMutex(plant.id)` рослина видаляється з таблиці `plants`. SQLite через `ON DELETE CASCADE` атомарно видаляє всі пов'язані `chat_messages` та `chat_attachments`. Каталог фотографій рослини видаляється з диска.
   - При завершенні мережевого виклику `saveAssistantResponse` бачить відсутність вихідного повідомлення (або відсутність рослини) і безпечно повертає `-1L`.
3. **Завершення Gemini після скасування (Job Cancellation):**
   - Якщо `viewModelScope` скасовує корутину запиту (наприклад, користувач вийшов з екрана чату):
   - Корутина переривається з `CancellationException`.
   - Блок `finally` у `withChatSending` гарантовано виконується під `withContext(NonCancellable)` і коректно зменшує лічильник `activeSendingCounters`. Витоків стану не виникає.
4. **Retry із тим самим фото:**
   - Якщо попередня відправка завершилась помилкою (`ERROR`), користувач натискає «Повторити».
   - Викликається `beginChatMessageRetry(messageId, plantId)`, який змінює статус репліки `ERROR -> SENDING`.
   - Репозиторій перевіряє наявність запису в `chat_attachments`. За шляхом `filePath` перевіряється `file.exists() && file.canRead()`.
   - Якщо файл на місці — він повторно передається до Gemini без повторного копіювання.
   - Якщо файл відсутній на диску (наприклад, видалений користувачем через файловий менеджер або не відновлений з бекапу) — система **НЕ надсилає запит мовчки як текстовий**, а повертає чітку помилку `PlantChatResult.ServiceError("Прикріплений файл фото не знайдено на пристрої. Будь ласка, зробіть нове фото")`.

### 6.3. Матриця надійності та відмовостійкості

| № | Сценарій / Потенційний збій | Механізм захисту та поведінка системи |
| :- | :--- | :--- |
| **1** | Втрата інтернету після вибору фото | `AndroidNetworkChecker` перехоплює офлайн до виклику ШІ. Репліка переходить у `ERROR`. Фото на диску зберігається для Retry. |
| **2** | HTTP 429 (Rate Limit від Gemini) | `AiErrorMapper` перехоплює 429. Репліка отримує статус `ERROR`. Фото зберігається, дублікатів не створюється. |
| **3** | Подвійний клік «Надіслати» (Double Tap) | Синхронний дебаунс у `ChatViewModel` (`isSending = true` у першому кадрі) блокує повторний вхід. |
| **4** | Crash процесу посеред запиту | На наступному старті `recoverStaleSendingMessages(plantId)` переводить підвислі `SENDING` у `ERROR`. Фото на місці. |
| **5** | Очищення чату під час запиту | Репліки видаляються з Room; запізніла відповідь Gemini відкидається в `saveAssistantResponse` (повертає -1L). |
| **6** | Видалення рослини під час запиту | Каскадне видалення очищує повідомлення; відповідь Gemini відкидається. Каталог фото видаляється. |
| **7** | Пошкоджений або битий JPEG | Результат `BitmapFactory.decodeFile` перевіряється на `null`. Повертається контрольована помилка без крашу. |
| **8** | Відновлення з Google Auto Backup | UI показує плейсхолдер *«Фото недоступне (файл не входить до резервної копії)»*. Відсутні файли не крашать чат. |
| **9** | Спроба Path Traversal у шляху файлу | Канонічна перевірка `canonicalPath.startsWith(baseDir)` блокує несанкціонований доступ з винятком безпеки. |
| **10** | Збій видалення файлу (`delete()` = false) | Транзакція БД не переривається; файл залишається для очищення службою Orphan Cleanup при наступному скануванні. |

### 6.4. Обов'язкові регресійні та Crash-Recovery тести
Архітектура не стверджує абсолютної відсутності ризиків без практичного підтвердження. Для доведення надійності обов'язковими є наступні автоматичні тести:
1. **Concurrency Regression Test:** виклик `clearChatHistory` під час активного мок-запиту Gemini (перевірка безпечного відкидання відповіді та відсутності гонок).
2. **Plant Deletion Concurrency Test:** виклик `deletePlant` під час активного запиту чату (перевірка коректного завершення корутини та каскаду).
3. **Crash Recovery Test (Orphan File):** створення файлу `.tmp` / цільового файлу без запису в Room → перевірка успішного виявлення та видалення службою Orphan Cleanup.
4. **Crash Recovery Test (Stale SENDING):** емуляція перезапуску застосунку з реплікою в стані `SENDING` та вкладенням → перевірка переведення в `ERROR` та збереження зв'язку з файлом.
5. **Missing File Retry Test:** наявність запису `chat_attachments` при фізично видаленому файлі → перевірка відсутності тихого фолбеку та повернення зрозумілої помилки.
6. **Path Traversal Security Test:** спроба передати `../../` у шляху вкладення → перевірка спрацьовування захисту canonical path.
7. **Backup Simulation Test:** ініціалізація `ChatMessageWithAttachments` із шляхом до неіснуючого файлу → перевірка стабільності Compose UI та відображення плейсхолдера.

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

Для дотримання Конституції та безпечної розробки впровадження розділено на 5 невеликих, суворо верифікованих PR (діапазон номерів після поточного документаційного PR #42):

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
  - `ChatPhotoManager.kt`: атомарний запис через `.tmp`, санітизація імен, перевірка canonical path, видалення, пошук сиріт.
  - Оновлення `ImageProcessor.kt` / `ImageProfile.kt` (додавання профілю консультації).
  - Фонова служба Orphan Cleanup при старті застосунку.
  - Оновлення `FlowersRepositoryImpl` для роботи з файлами при очищенні чату та видаленні рослини під `plantMutex`.
- **Тестування:**
  - Юніт-тести обробки файлів, перевірки блокування path traversal, очищення сиріт, повтору видалення при блокуванні.

### Етап 3: PR №45 — Мультимодальний клієнт Gemini та оновлення контексту
- **Склад змін:**
  - Оновлення `GeminiPlantService`: мультимодальний виклик `sendPlantChatMessage` з передачею `Bitmap` та ізольованою `systemInstruction` (очищення застарілих параметрів temperature).
  - Підтримка вимірювання токенів через `countTokens` у тестах Free Tier.
  - Оновлення `PlantChatContextBuilder.kt`: безпечна серіалізація повідомлень без тексту (з маркером фото).
  - Оновлення `FlowersRepository.sendPlantChatMessage`.
- **Тестування:**
  - Юніт-тести з мок-відповідями Gemini (із зображенням, без зображення, битий JPEG, помилки, таймаути, перевірка `PlantSafetyGuardrail`).

### Етап 4: PR №46 — Мультимодальний `ChatViewModel`
- **Склад змін:**
  - Оновлення `ChatViewModel`: методи `attachPhoto(file)`, `removeAttachedPhoto()`, обробка порожнього тексту з дефолтним питанням.
  - Робота з `selectedPhotoFile`, Retry зі збереженим вкладенням, скасування операцій.
- **Тестування:**
  - 25+ юніт-тестів на всі стани ViewModel, дебаунс, помилки, перевірка відсутності файлу при Retry, тести конкурентності з `clearChatHistory` та `deletePlant`.

### Етап 5: PR №47 (STEP 4) — Користувацький інтерфейс та навігація
- **Склад змін:**
  - Створення `PlantChatScreen.kt` у Jetpack Compose.
  - Поле введення з кнопками камери/галереї, плашка попереднього перегляду фото перед надсиланням.
  - Відображення бульбашок повідомлень з фото через Coil (`AsyncImage`) та плейсхолдерами для відсутніх файлів після відновлення з бекапу.
  - Діалог повноекранного перегляду фотографії.
  - Точка входу в чат із `PlantDetailScreen.kt` та маршрут у навігаційному графі (`Screen.PlantChat`).
- **Тестування:**
  - Compose UI тести, ручна перевірка на емуляторі, підготовка до перевірки Власником на телефоні.

---

## 9. Питання, погоджені Власником ([INBOX-015])

Власник умовно погодив 3 ключові рішення (2026-11-28):
1. **Модель зберігання:** Затверджено **Варіант B** (окрема таблиця `chat_attachments` із каскадним видаленням і готовністю до порівняння фото).
2. **Параметри стиснення:** Затверджено **1536 px**, **85% JPEG**, нативне видалення EXIF/GPS, оціночний розмір ~200–500 КБ.
3. **План поетапних PR:** Затверджено реалізацію п'ятьма окремими PR (**PR №43 → PR №44 → PR №45 → PR №46 → PR №47 / STEP 4**).

Всі 4 зауваження Власника (Backup-правила, безпека ФС та атомарність, неблокуюча конкурентність `withChatSending`, емпіричні квоти Gemini без платного тарифу) повністю інтегровано у версію 1.2 цього документа.

---
*Документ підготовлено Агентом 0 і винесено на фінальне затвердження Власника.*
