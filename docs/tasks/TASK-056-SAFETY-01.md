# TASK-056-SAFETY-01 — Захист історії рослин від каскадного видалення при повторній вставці

> **Статус:** РЕАЛІЗОВАНО (Очікує незалежної QA та погодження Власника)  
> **Етап:** Flowers 2.0 MVP (ADR-021, `FLOWERS_2_0_MVP_PLAN.md`)  
> **Пріоритет:** Критична безпека даних (Data Safety)  
> **Відповідальний:** Агент 0 (Архітектор / Керівник)

---

## 1. Контекст та суть проблеми

У процесі реалізації Room v4 (TASK-056 / Крок 1) було створено таблицю `chat_messages` із зовнішнім ключем:
```sql
FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
```
Аналогічний каскадний зв'язок діє для таблиці `care_events`:
```sql
FOREIGN KEY(`plantId`) REFERENCES `plants`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
```

В інтерфейсі `PlantDao.kt` метод `insertPlant` мав анотацію:
```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
abstract suspend fun insertPlant(plant: PlantEntity): Long
```

### Механізм виникнення ризику в SQLite:
1. За стандартом SQLite операція `INSERT OR REPLACE` (або `OnConflictStrategy.REPLACE` у Room) виконується як атомарна послідовність: `DELETE` існуючого рядка за первинним ключем, після чого `INSERT` нового рядка.
2. Оскільки Room вмикає підтримку зовнішніх ключів (`PRAGMA foreign_keys = ON`), виконання `DELETE` батьківського рядка в таблиці `plants` негайно запускає тригер `ON DELETE CASCADE`.
3. У результаті каскаду **безповоротно видалялися всі пов'язані дочірні записи** (`care_events` та `chat_messages`) цієї рослини.
4. Новий рядок вставлявся, але його повна історія догляду та чату ставала порожньою.

У production-коді наразі виклики `insertPlant` відбуваються лише для нових рослин (`id == 0L`), а оновлення виконується через `updatePlant()` (який не викликає DELETE і не видаляє каскадно). Проте за наявності `OnConflictStrategy.REPLACE` будь-який майбутній виклик (наприклад, імпорт, синхронізація або логічна помилка) міг призвести до безповоротної втрати історії користувача.

---

## 2. Реалізоване рішення

### 2.1. Зміна стратегії конфлікту в `PlantDao.kt`
У `com/flowers/app/data/local/dao/PlantDao.kt`:
```kotlin
@Insert(onConflict = OnConflictStrategy.ABORT)
abstract suspend fun insertPlant(plant: PlantEntity): Long
```
- Схема Room v4, хеші схеми та міграції `MIGRATION_3_4` залишаються на 100% незмінними.
- Нові рослини з автоінкрементним ID додаються у звичайному режимі.
- Спроба вставити рослину з наявним ID завершується стандартним `SQLiteConstraintException` (операція абортується, жодні дані не видаляються).
- Штатне оновлення рослини через `@Update abstract suspend fun updatePlant(plant: PlantEntity)` працює без змін і зберігає всі дочірні дані.

---

## 3. Регресійні та компонентні тести

Оновлено тест `abortOnDuplicateInsertPlant_preservesCascadeData_andUpdatePlantIsSafe` у `ChatMessageDaoTest.kt`:
1. **Створення нової рослини:** успішне генерування ID.
2. **Контроль конфлікту:** спроба повторної вставки рослини з наявним ID викликає `SQLiteConstraintException`.
3. **Цілісність батьківського запису:** назва, локація та дата перевірки існуючої рослини залишаються незмінними.
4. **Збереження журналу догляду:** усі типи подій (`WATER`, `DELAY`, `HEALTH_CHECK`) залишаються збереженими разом із нотатками, днями затримки та `photoUri`.
5. **Збереження історії чату:** усі повідомлення `USER` та `MODEL` залишаються збереженими.
6. **Штатне редагування:** виклик `updatePlant()` оновлює поля рослини, повністю зберігаючи дочірні події та повідомлення.
7. **Ізоляція:** контрольна рослина B та її історія залишаються на 100% недоторканими.
