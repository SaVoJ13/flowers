# CURRENT STATE (Поточний стан проєкту)

> Знімок стану проєкту на поточний момент. Оновлюється Агентом 0.

---

## 1. Загальний огляд
- **Проєкт:** Flowers (Android-застосунок для догляду за кімнатними рослинами)
- **Поточний етап:** **Етап 8 завершено (Freemium + Gemini AI Plant Generator).** Оновлений додаток v1.1.0 розгорнуто та верифіковано на фізичному пристрої.
- **Головна гілка:** `main`.
- **Остання зміна (2026-11-19):** Hotfix TASK-018: вимкнену Google модель `gemini-1.5-flash` замінено на `gemini-3.5-flash-lite`, назву моделі винесено в константу `GEMINI_MODEL` (`data/remote/GeminiConfig.kt`), каскад моделей прибрано, ключ передається заголовком `x-goog-api-key`. PR #1 злито в `main`. Власник перевірив генерацію картки на телефоні: працює, 404 немає. Тести: 19/19.
- **applicationId (2026-11-19):** змінено на `com.savoj.flowers` (ADR-010), бо `com.flowers.app` зайнятий у Google Play. Kotlin-пакети та namespace без змін. На телефоні Власника встановлено нову версію, стару видалено разом із даними. CI в репозиторії немає.
- **Планування та реалізація (2026-11-19):** TASK-019 — Власник виконав кроки 1–2 (створив проєкт Firebase «Flowers», надав `google-services.json`). Агент (Antigravity) реалізував підзавдання 019-B:
  - `google-services.json` першим комітом додано у `.gitignore` (`125ab90`), створено тег `pre-task-019` на `main`.
  - Підключено Firebase BoM 35.2.0, `firebase-ai` 18.2.0, `firebase-appcheck-playintegrity`, `firebase-appcheck-debug` (debug-only).
  - Налаштовано App Check з мультисорсовим розділенням (`src/debug` та `src/release`), у релізному APK debug-провайдер відсутній.
  - `GeminiPlantService` переведено на `Firebase.ai(backend = GenerativeBackend.googleAI())`.
  - З `SettingsScreen` та `UserPreferences` видалено введення та зберігання API-ключа, додано інформаційну картку про захист через App Check.
  - Тести пройдено (18/18), `assembleDebug` та `assembleRelease` успішні. Відкрито PR для незалежного QA.
- **Попередня зміна:** Реалізація моделі Freemium та інтеграція сервісу Google Gemini Flash для створення карток догляду за рослинами на льоту (TASK-017, QA Verified). Додано 1 новий тест (всього 17/17 тестів зелених).

---

## 2. Стан коду та артефактів
- **Код застосунку:** Повністю реалізовано в `app/src/main/java/com/flowers/app/` (Clean Architecture, Jetpack Compose, Material 3, Room, WorkManager, Coroutines, Gemini AI Service, UserPreferences).
- **Збірка:** Успішно зібрано `app/build/outputs/apk/debug/app-debug.apk` (v1.1.0).
- **Тести:** 19/19 юніт-тестів пройдено успішно (ReminderCalculator, SymptomCorrelationEngine, CatalogSearch, CatalogPrepopulation, GeminiPlantService, GeminiConfig).
- **Фізичний пристрій:** Застосунок оновлено через ADB (`adb install -r`) на смартфоні Власника Samsung Galaxy S24 Ultra (`SM-S928B`, `RZCX326349A`). Вікно ШІ-генератора рослин та блок Flowers Premium протестовані на пристрої. 2026-11-19: після TASK-018 Власник підтвердив на пристрої успішну генерацію картки з `gemini-3.5-flash-lite`.

---

## 3. Блокери та відкриті питання
- **Блокери:** Відсутні. MVP повністю функціональний.
- **Відома проблема / наступне завдання:** зараз користувач вводить власний API-ключ Gemini (зберігається локально в SharedPreferences на пристрої). Для платної версії потрібно перейти на Firebase AI Logic з App Check, щоб ключ не зберігався в застосунку. Див. ADR-008, ADR-009, TASK-019 (план готовий, очікує дій Власника в консолі Firebase; витрати, акаунт Firebase, приватність — п. 3.2 конституції).
- **Активні питання в `OWNER_INBOX.md`:** Відсутні (всі питання закрито).

---

## 4. Наступні кроки для майбутніх ітерацій (за потреби)
1. Експлуатація та збір особистого зворотного зв'язку Власника під час щоденного догляду за домашніми рослинами.
2. Підготовка релізного підписаного AAB-пакета (`./gradlew bundleRelease`) та публікація у Google Play Console для закритого тестування.
3. Розширення бази подів та додаткових видів рослин за запитом.
4. TASK-019: Власник виконує `docs/tasks/TASK-019-owner-firebase-guide.md` (пакет `com.savoj.flowers`; консоль Firebase, `google-services.json` у `app/`); після цього агент реалізує код (гілка `feat/task-019-firebase-ai-logic`), QA іншим агентом, live-перевірка Власником. Деталі: `docs/tasks/TASK-019-firebase-ai-logic.md`.
