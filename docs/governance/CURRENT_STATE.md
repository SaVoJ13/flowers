# CURRENT STATE (Поточний стан проєкту)

> Знімок стану проєкту на поточний момент. Оновлюється Агентом 0.

---

## 1. Загальний огляд
- **Проєкт:** Flowers (Android-застосунок для догляду за кімнатними рослинами)
- **Поточний етап:** **Етап 8 завершено (Freemium + Gemini AI Plant Generator).** Оновлений додаток v1.1.0 розгорнуто та верифіковано на фізичному пристрої.
- **Головна гілка:** `main`.
- **Остання зміна (2026-11-19, сесія 013):** TASK-019 закрито: Власник обмежив Android-ключ (`com.savoj.flowers` + debug SHA-1; Generative Language API немає у списку), генерація на телефоні працює; крок 7 (бюджет) відкладено, проєкт на Spark без платіжного акаунта. Складено `docs/product/AI_ROADMAP.md` і ADR-012 (Пропоновано). Раніше: TASK-019 — успішна міграція на Firebase AI Logic та Firebase App Check (ADR-009..011). PR #5 пройшов незалежний аудит (`QA Verified`) і злито в `main`. Ключ Gemini повністю усунено з клієнтського застосунку. Власник підтвердив працездатність на Samsung Galaxy S24 Ultra (`RZCX326349A`). Тести: 18/18 зелені.
- **applicationId (2026-11-19):** `com.savoj.flowers` (ADR-010). Застосунок зареєстровано у Firebase з цим ID.
- **Попередня зміна:** Hotfix TASK-018: вимкнену Google модель `gemini-1.5-flash` замінено на `gemini-3.5-flash-lite`.

---

## 2. Стан коду та артефактів
- **Код застосунку:** Повністю реалізовано в `app/src/main/java/com/flowers/app/` (Clean Architecture, Jetpack Compose, Material 3, Room, WorkManager, Coroutines, Gemini AI Service, UserPreferences).
- **Збірка:** Успішно зібрано `app/build/outputs/apk/debug/app-debug.apk` (v1.1.0).
- **Тести:** 18/18 юніт-тестів пройдено успішно (ReminderCalculator, SymptomCorrelationEngine, CatalogSearch, CatalogPrepopulation, GeminiPlantService, GeminiConfig).
- **Фізичний пристрій:** Застосунок оновлено через ADB (`adb install -r`) на смартфоні Власника Samsung Galaxy S24 Ultra (`SM-S928B`, `RZCX326349A`). 2026-11-19: Власник підтвердив успішну генерацію картки рослини через Firebase AI Logic із захистом App Check.

---

## 3. Блокери та відкриті питання
- **Блокери:** Відсутні. MVP та захищена хмарна ШІ-генерація повністю функціональні. Проблему збереження API-ключа в застосунку (ADR-008) вирішено (ADR-009, ADR-011).
- **Активні питання в `OWNER_INBOX.md`:** Відсутні (всі питання закрито).

---

## 4. Наступні кроки
1. **Рішення Власника** за ADR-012 та питаннями в `OWNER_INBOX.md` (монетизація, Blaze + бюджет, ціни, вік 18+, облік користувачів).
2. Після рішення: TASK-022 (ШІ-шлюз і квоти), далі TASK-023 (Play Billing), TASK-024 (Лікар рослин).
3. Беклог: TASK-020 (тести мапінгу помилок), TASK-025 (токсичність лише з перевірених джерел).
4. Перед будь-якою публікацією: TASK-021 (SHA-1 ключа підпису Google Play в обмеження ключа, Firebase та App Check; документи приватності) та крок 7 (бюджет) до переходу на Blaze.
