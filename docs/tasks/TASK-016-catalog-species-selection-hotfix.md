# TASK-016 [Hotfix]: Виправлення вибору ботанічного виду з каталогу та завантаження каталогу

## 1. Загальні відомості
- **ID завдання:** TASK-016
- **Назва:** Виправлення вибору ботанічного виду з каталогу рослин та відновлення завантаження даних (Gson `@SerializedName`, pre-population fix, dropdown UX)
- **Дата створення:** 2026-11-18
- **Автор:** Агент 0 (Керівник проєкту)
- **Виконавець:** Агент 0
- **Статус:** Завершено (Done, QA Verified)
- **Пріоритет:** Критичний (Hotfix після тестування Власником на смартфоні)

---

## 2. Опис дефекту та першопричина (Root Cause Analysis)
- **Симптом:** 
  1. При додаванні/редагуванні рослини вибір ботанічного виду з каталогу не працює («ботанічний вид з каталогу теж не працює»). Список порожній або не підтягується.
  2. У довіднику раніше відображалось 0 рослин.
- **Першопричина:**
  1. **Невідповідність ключів JSON та полів сутностей (Snake_case vs CamelCase):**
     - У файлі `assets/content/PLANT_CATALOG.json` ключі записані у стилі `snake_case` (`name_uk`, `name_latin`, `popular_name`, `default_check_interval_days`, `soil_type`, `toxic_to_pets`).
     - У сутності `CatalogPlantEntity` поля були оголошені у `camelCase` (`nameUk`, `nameLatin`, `popularName`, тощо) **без анотацій `@SerializedName`**.
     - Аналогічно у `CARE_PODS.json`: ключі `plant_id`, `title_uk`, `content_uk`, а в `CarePodEntity` — `plantSpeciesId`, `titleUk`, `contentUk` без `@SerializedName`.
     - За замовчуванням Gson встановлював значення `null` у поля з `NOT NULL` обмеженням у базі даних SQLite (`NOT NULL constraint failed`).
  2. **Мовчазне падіння pre-population callback:**
     - Під час створення бази даних `RoomDatabase.Callback.onCreate` кидав виняток `SQLiteConstraintException`, який перехоплювався `catch (e: Exception)` без відновлення, тому таблиці `catalog_plants` та `care_pods` залишалися **порожніми** (0 записів).
     - Більше того, `onCreate` викликається лише один раз під час створення файлу SQLite. На вже створеній базі даних додатка `onCreate` більше не спрацьовував, через що таблиці залишалися пустими навіть після наступних запусків.
  3. **UX вибору в `AddEditPlantScreen.kt`:**
     - `OutlinedTextField` мав статичне значення замість плейсхолдера при порожньому виборі.
     - `onExpandedChange` використовував `!isExpanded` замість `it`.
     - Було відсутнє завантаження існуючої рослини при `plantId != null`.

---

## 3. План виправлення
1. Додати анотації `@SerializedName` до всіх полів у `CatalogPlantEntity.kt` та `CarePodEntity.kt`.
2. Реалізувати надійний механізм автоматичного наповнення (`ensurePopulated`) у `FlowersRepositoryImpl` та `FlowersDatabase` при `count() == 0`, який гарантовано наповнює дані як при новому створенні, так і на існуючих базах даних.
3. Оновити `AddEditPlantScreen.kt`:
   - Належне відображення плейсхолдера та обраного ботанічного виду.
   - Коректна обробка `onExpandedChange = { isExpanded = it }`.
   - Завантаження параметрів існуючої рослини через `LaunchedEffect(plantId, catalogPlants)`.
4. Створити вичерпний юніт-тест `CatalogPrepopulationTest.kt`, що перевіряє десеріалізацію та валідність усіх 20 видів каталогу та 18 подів догляду безпосередньо з ассетів.
5. Зібрати APK, прогнати тести, оновити додаток на телефоні Samsung Galaxy S24 Ultra (`RZCX326349A`).
6. Залучити незалежного QA-субагента для верифікації виправлення (Червона лінія №9).

---

## 4. Журнал виконання (Append-only)
- `2026-11-18 13:08`: Створено картку завдання TASK-016, ідентифіковано першопричину з `@SerializedName`.
- `2026-11-18 13:09`: Додано `@SerializedName` до всіх полів `CatalogPlantEntity` та `CarePodEntity`.
- `2026-11-18 13:10`: Додано механізм самовідновлення `ensurePopulated()` у `FlowersRepositoryImpl` та `FlowersDatabase` при `count() == 0`.
- `2026-11-18 13:11`: Оновлено `AddEditPlantScreen.kt`: `onExpandedChange = { isExpanded = it }`, `MenuAnchorType.PrimaryNotEditable`, плейсхолдер та `LaunchedEffect(plantId)`.
- `2026-11-18 13:12`: Написано `CatalogPrepopulationTest.kt` (2 тести, всі 16 тестів проєкту пройдено успішно).
- `2026-11-18 13:13`: Зібрано debug APK, встановлено на Samsung Galaxy S24 Ultra (`RZCX326349A`).
- `2026-11-18 13:19`: **QA Verified** від Lead Independent QA Inspector. Перевірено роботу на пристрої, вибір ботанічного виду протестовано зі скриншотами. Завдання завершено.
