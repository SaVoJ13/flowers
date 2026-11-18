# ТЕХНІЧНА АРХІТЕКТУРА — FLOWERS (MVP)

- **Проєкт:** Flowers (Android)
- **Етап:** Етап 3 — Архітектура та дозволи (`TASK-006`)
- **Версія:** 1.0 (MVP)
- **Автор:** Android-агент під координацією Агента 0

---

## 1. Загальний огляд та архітектурний патерн

Застосунок Flowers побудований за принципами **Clean Architecture** та **Modern Android Architecture (UDF / MVVM)** із фокусом на 100% офлайн-роботу та нульову вартість серверної інфраструктури ($0):

```
┌─────────────────────────────────────────────────────────────┐
│                       UI LAYER                              │
│   Jetpack Compose Screens + Navigation Compose + M3 Themes  │
│                               ▲                             │
│                               │ UI State (StateFlow)        │
│                               ▼ User Actions                │
│                        ViewModels                           │
└───────────────────────────────┬─────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────┐
│                     DOMAIN LAYER                            │
│   Domain Models · SymptomCorrelationEngine · ReminderEngine │
└───────────────────────────────┬─────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────┐
│                      DATA LAYER                             │
│   FlowersRepositoryImpl · Local File Storage (Images)       │
│                               ▲                             │
│                               │                             │
│             ┌─────────────────┴─────────────────┐           │
│             ▼                                   ▼           │
│     Room Database (SQLite)              Asset Pre-populator │
│   Plants / CareEvents / Pods / Catalog    (Initial JSONs)   │
└─────────────────────────────────────────────────────────────┘
                                ▲
┌───────────────────────────────┴─────────────────────────────┐
│                   BACKGROUND & SYSTEM                       │
│    WorkManager (DailyReminderWorker) · BootReceiver         │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Схема бази даних Room (SQLite)

База даних названа `flowers_database.db`. Включає 4 ключові сутності:

### 2.1. `PlantEntity` (Таблиця `plants`)
Зберігає індивідуальні рослини користувача.
```kotlin
@Entity(tableName = "plants")
data class PlantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customName: String,               // Власна назва, напр. "Монстера біля вікна"
    val speciesId: String?,               // Посилання на catalog_plants.id або null
    val roomLocation: String,             // Кімната: "Вітальня", "Спальня" тощо
    val lightLevel: String,               // "SHADE", "INDIRECT", "DIRECT"
    val checkIntervalDays: Int,           // Інтервал перевірки (днів), напр. 7
    val lastWateredDate: Long?,           // Timestamp останнього фактичного поливу
    val nextCheckDate: Long,              // Timestamp наступної запланованої перевірки
    val photoUri: String?,                // Локальний шлях до файлу фотографії
    val isArchived: Boolean = false,      // Прапорець архівації
    val createdAt: Long = System.currentTimeMillis()
)
```

### 2.2. `CareEventEntity` (Таблиця `care_events`)
Зберігає хронологію всіх дій користувача та записів журналу.
```kotlin
@Entity(
    tableName = "care_events",
    foreignKeys = [
        ForeignKey(
            entity = PlantEntity::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["plantId"]), Index(value = ["timestamp"])]
)
data class CareEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val eventType: String,                // "WATER", "DELAY", "SKIP", "CHECK_STATE", "REPOT", "FERTILIZE"
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String? = null,            // Текстова нотатка користувача
    val photoUri: String? = null,         // Фото стану або динаміки "було/стало"
    val daysDelayed: Int? = null          // На скільки днів було відкладено перевірку
)
```

### 2.3. `CatalogPlantEntity` (Таблиця `catalog_plants`)
Локальний довідник 20 видів кімнатних рослин, що постачається разом із застосунком.
```kotlin
@Entity(tableName = "catalog_plants")
data class CatalogPlantEntity(
    @PrimaryKey val id: String,           // напр. "zamioculcas", "monstera"
    val nameUk: String,
    val nameLatin: String,
    val popularName: String,
    val difficulty: String,
    val light: String,
    val defaultCheckIntervalDays: Int,
    val soilType: String,
    val toxicToPets: Boolean
)
```

### 2.4. `CarePodEntity` (Таблиця `care_pods`)
Картки-поди знань (загальні та специфічні для видів).
```kotlin
@Entity(tableName = "care_pods")
data class CarePodEntity(
    @PrimaryKey val id: String,           // напр. "pod_zamio_moisture"
    val plantSpeciesId: String,           // "general_beginner" або id рослини
    val category: String,                 // "moisture_check", "light_placement", "common_mistake"
    val titleUk: String,
    val contentUk: String,
    val icon: String
)
```

---

## 3. Стратегія попереднього заповнення (Asset Pre-population)

- При першому створенні бази даних (`RoomDatabase.Callback.onCreate`) фонова корутина зчитує файли `assets/content/PLANT_CATALOG.json` та `assets/content/CARE_PODS.json`.
- Дані розпаршуються за допомогою `kotlinx.serialization` (або `Gson`) і вставляються транзакцією в таблиці `catalog_plants` та `care_pods`.
- Завдяки цьому каталог та поди доступні миттєво з першої секунди запуску без жодних інтернет-запитів.

---

## 4. Логіка сповіщень та планувальник (WorkManager)

1. **`DailyReminderWorker`:**
   - Щоранку (за замовчуванням о 09:00 або в час, налаштований у `Settings`) `PeriodicWorkRequest` запускає легку перевірку.
   - Працівник зчитує з Room усі рослини, у яких `nextCheckDate <= System.currentTimeMillis()` та нагадування увімкнені.
   - Якщо такі рослини знайдено:
     - Для однієї рослини: *«Час перевірити [Назва]: огляньте ґрунт і вологість»*.
     - Для кількох: *«Час ранкового огляду: 3 рослини очікують на перевірку»*.
2. **Стійкість до перезавантаження (`BootReceiver`):**
   - Клас `BootReceiver` реагує на `ACTION_BOOT_COMPLETED` та `ACTION_MY_PACKAGE_REPLACED`, відновлюючи періодичний розклад у `WorkManager`.
3. **Обробка дій «Відкласти» та «Виконано»:**
   - При виборі «Відкласти на N днів»: `nextCheckDate = System.currentTimeMillis() + N * 86400000L`. Запис події `DELAY` фіксується в журналі.
   - При виборі «Виконано»: `lastWateredDate = now`, `nextCheckDate = now + checkIntervalDays * 86400000L`. Запис `WATER` додається в журнал.

---

## 5. Збереження фотографій та медіа

- Фотографії зберігаються у захищеній внутрішній папці застосунку: `context.filesDir/plant_photos/<uuid>.jpg`.
- Для вибору фото використовується сучасний нативний **Android Photo Picker** (`ActivityResultContracts.PickVisualMedia`), який:
  - **НЕ потребує** дозволів на читання сховища (`READ_EXTERNAL_STORAGE` чи `READ_MEDIA_IMAGES`);
  - Забезпечує повну конфіденційність: додаток отримує доступ лише до конкретно обраного користувачем файлу.
- При видаленні рослини пов'язані файли зображень автоматично видаляються з диску для економії пам'яті.

---

## 6. Двигун кореляції симптомів (`SymptomCorrelationEngine`)

- Файл `SYMPTOM_DECISION_TREE.json` завантажується в пам'ять як детермінований граф.
- Коли користувач обирає симптом втрати тургору листя (`leaves_drooping_wilting`), рушій робить SQL-запит:
  ```sql
  SELECT MAX(timestamp) FROM care_events 
  WHERE plantId = :plantId AND eventType = 'WATER'
  ```
- Якщо різниця між поточним часом та останнім поливом становить $\le 3$ днів, алгоритм повертає підозру на перелив та план дій щодо порятунку коренів.
- Якщо різниця $> 7$ днів — діагностується нестача вологи.
