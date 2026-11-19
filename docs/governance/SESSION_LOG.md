# SESSION LOG (Журнал сесій)

> Короткий запис про кожну робочу сесію: дата, учасники, мета, виконані дії, підсумковий стан.
> Журнал лише доповнюється (append-only, п. 5.5).

---

## Сесія 001 — 2026-11-18 — Агент 0
- **Мета:** Початковий аудит репозиторію, створення проєктного governance-контуру та підготовка до Етапу 1.
- **Учасники:** Агент 0.
- **Результат:** Етап 0 завершено та затверджено Власником (`Owner Approved`, ADR-001..ADR-004).

---

## Сесія 002 — 2026-11-18 — Агент 0, Агент дослідження, QA Documentation Inspector
- **Мета:** Етап 1 — Дослідження ринку та формування PRD.
- **Результати:**
  - Проведено глибокий аналіз 5 конкурентів у Google Play (Planta, PictureThis, Blossom, Greg, Vera).
  - Складено `COMPETITIVE_ANALYSIS.md` та `PRD.md`.
  - Проведено незалежну верифікацію QA (`QA Verified`).
  - Отримано `Owner Approved` Власника (ADR-005).

---

## Сесія 003 — 2026-11-18 — Агент 0, Агент UX/UI, Редактор ботанічного контенту, QA Inspector
- **Мета:** Етап 2 — UX та ботанічний контент.
- **Результати:**
  - Створено `USER_FLOWS.md` та `SCREEN_SPECS.md`.
  - Складено `PLANT_CATALOG.json` (20 видів), `CARE_PODS.json` (18 карток), `SYMPTOM_DECISION_TREE.json` (11 вузлів з кореляцією поливу) та `SOURCES.md`.
  - Отримано незалежний `QA Verified`.
  - Власник надав мандат автономної розробки та встановлення на телефон (ADR-006).

---

## Сесія 004 — 2026-11-18 — Агент 0, Android-агент, Агент приватності, Lead QA Inspector
- **Мета:** Етапи 3, 4, 5, 6, 7 — Архітектура, розробка MVP, тестування, матеріали магазину та встановлення на смартфон.
- **Результати:**
  - **Етап 3:** Спроєктовано Clean Architecture, схему Room, WorkManager, стратегію приватності (Zero Data Collection) та оновлено `THIRD_PARTY.md` (усі Apache-2.0).
  - **Етап 4:** Повністю реалізовано кодову базу Android на Jetpack Compose, Material 3, Room, WorkManager, Coroutines (всі 7 екранів, поди знань, кореляція симптомів).
  - **Етап 5:** Написано та успішно пройдено 6/6 юніт-тестів (ReminderCalculator, SymptomCorrelationEngine). Зібрано `app-debug.apk` (18.4 MB).
  - **Етап 6:** Створено `STORE_LISTING.md` та `CLOSED_TESTING_GUIDE.md`.
  - **Етап 7:** Виявлено підключений через USB смартфон Власника Samsung Galaxy S24 Ultra (`SM-S928B`). Застосунок успішно встановлено через ADB (`Success`), запущено активне вікно та знято скриншот `screenshot_phone_flowers.png`.
  - **Незалежний QA-аудит:** Lead QA Inspector підтвердив `QA Verified` для Етапів 4–6.
- **Підсумок:** Проєкт Flowers MVP повністю розроблено, протестовано та встановлено на телефон Власника!
- **Зворотний зв'язок Власника:** Роботу прийнято («ну в цілому норм, якщо будуть якісь допрацювання в майбутньому то пізніше відкорегуємо»). Проєкт готовий до повсякденного використання.

---

## Сесія 005 — 2026-11-18 — Агент 0, Lead QA Inspector
- **Мета:** Виправлення дефекту пошуку рослин у вкладці «Довідник» (TASK-015 Hotfix).
- **Зворотний зв'язок Власника:** «в закладці довідник не проходить пошук рослин».
- **Результати:**
  - Здійснено Root Cause Analysis: SQLite `LIKE` не підтримував Unicode case-folding для кирилиці.
  - Оновлено `CatalogViewModel.kt` та `FlowersRepositoryImpl.kt` на реактивну фільтрацію в пам'яті через `contains(..., ignoreCase = true)`.
  - Покращено інтерфейс `CatalogScreen.kt`: додано кнопку скидання пошуку (`Clear`), порожній стан (`🔍 Нічого не знайдено`) та динамічний лічильник.
  - Додано 8 модульних тестів у `CatalogSearchTest.kt`. Усі 14 тестів проєкту пройдено (`BUILD SUCCESSFUL`).
  - Оновлений APK зібрано та встановлено на підключений Samsung Galaxy S24 Ultra (`RZCX326349A`).
  - Отримано незалежне підтвердження якості `QA Verified` від Lead QA Inspector.
- **Підсумок:** Дефект повністю усунуто, оновлення працює на телефоні Власника.

---

## Сесія 006 — 2026-11-18 — Агент 0, Lead QA Inspector
- **Мета:** Виправлення дефекту вибору ботанічного виду з каталогу та завантаження каталогу (TASK-016 Hotfix).
- **Зворотний зв'язок Власника:** «ботанічний вид з каталогу теж не прецює».
- **Результати:**
  - Виявлено першопричину: розбіжність snake_case ключів у `PLANT_CATALOG.json` та `CARE_PODS.json` із camelCase полями без `@SerializedName`, через що Room отримував `null` і кидав виняток при `NOT NULL` обмеженні, залишаючи БД з 0 рослин.
  - Додано явні анотації `@SerializedName` на всі поля `CatalogPlantEntity` та `CarePodEntity`.
  - Реалізовано автонаповнення бази `ensurePopulated()` у `FlowersRepositoryImpl` та `FlowersDatabase` при `count() == 0` для існуючих та нових установок.
  - Покращено `AddEditPlantScreen.kt`: `onExpandedChange = { isExpanded = it }`, `MenuAnchorType.PrimaryNotEditable`, плейсхолдер та завантаження існуючих рослин через `LaunchedEffect(plantId)`.
  - Створено юніт-тести `CatalogPrepopulationTest.kt` (перевірка 20 видів та 18 подів). Усі 16 тестів проєкту зелені.
  - Зібрано APK, розгорнуто на Samsung Galaxy S24 Ultra (`RZCX326349A`).
  - Отримано незалежний `QA Verified` від Lead QA Inspector із підтвердженням на живому пристрої та скриншотами.
- **Підсумок:** Ботанічний вид із каталогу працює бездоганно: випадаюче меню містить усі 20 видів, автозаповнює назву та базовий графік поливу.

---

## Сесія 007 — 2026-11-18 — Агент 0, Lead QA Inspector
- **Мета:** Етап 8 — Впровадження моделі Freemium та інтеграція ШІ-генератора рослин (Google Gemini Flash) за рішенням Власника ADR-007.
- **Результати:**
  - Додано дозвіл `android.permission.INTERNET` у маніфест.
  - Реалізовано `UserPreferences.kt` (статус Premium увімкнено, конфігурація Gemini API ключа).
  - Реалізовано `GeminiPlantService.kt` зі структурованим JSON-промптом для вилучення всіх ботанічних атрибутів рослини, каскадним fallback моделей (2.5 → 2.0 → 1.5) та автоматичним кешуванням у Room.
  - Оновлено `CatalogScreen.kt`: додано кнопку ШІ-генератора у TopAppBar, модальний діалог пошуку, стан прогресу та пропозицію генерації при порожньому пошуку.
  - Оновлено `SettingsScreen.kt`: додано картку «👑 Flowers Premium: АКТИВОВАНО» та поле налаштування API-ключа з посиланням на Google AI Studio.
  - Написано юніт-тест `GeminiPlantServiceTest.kt` (усі 17 тестів проєкту green).
  - Додаток v1.1.0 зібрано та встановлено на Samsung Galaxy S24 Ultra (`RZCX326349A`).
  - Отримано офіційний `QA Verified` від Lead QA Inspector із підтвердженням на реальному пристрої та скриншотами.
- **Підсумок:** Етап 8 успішно завершено! Застосунок отримав повноцінну Freemium-архітектуру та розумний ШІ-генератор догляду.

---

## Сесія 008 — 2026-11-19 — Агент 0, незалежний QA-агент
- **Мета:** Hotfix TASK-018 — генерація картки догляду повертала 404 («models/gemini-1.5-flash is not found for API version v1beta»), бо Google вимкнула модель.
- **Результати:**
  - Модель замінено на `gemini-3.5-flash-lite`; назву винесено в єдину константу `GEMINI_MODEL` (`GeminiConfig.kt`), прибрано каскад застарілих моделей, ключ переведено з URL у заголовок `x-goog-api-key`.
  - Перевірено: ключів у git та історії немає, `local.properties` в `.gitignore`.
  - Додано `GeminiConfigTest` (усього 19/19 тестів зелені; збірка з JDK 21 за L-004).
  - Гілка `fix/task-018-gemini-model-config`, PR #1 злито (squash) у `main` після `QA Verified` від незалежного QA-агента.
  - APK встановлено на Samsung Galaxy S24 Ultra (`RZCX326349A`); Власник підтвердив на пристрої: генерація працює, помилки 404 немає.
  - Зафіксовано відому проблему (ADR-008): користувач вводить власний ключ Gemini; для платної версії потрібен Firebase AI Logic + App Check.
- **Підсумок:** Hotfix закрито, перевірку Власником пройдено. Наступне завдання — TASK-019 (потребує рішення Власника).
- **Примітка:** локальна `main` розійшлася з `origin/main` (на GitHub squash-коміт, локально 13 непушнутих комітів); дерева файлів збігаються.

---

## Сесія 009 — 2026-11-19 — Агент 0 (роль Архітектора)
- **Мета:** вирівняти локальну `main`; спланувати TASK-019 (Firebase AI Logic + App Check). Код не змінювався.
- **Результати:**
  - За дозволом Власника: `git status` чистий → резервна гілка `backup/local-main-2026-11-19` (коміт `7b0f0cb`) → `git fetch` → `git reset --hard origin/main`; `main` = `origin/main` (`c111a83`).
  - Вивчено поточну інтеграцію (ключ у `UserPreferences`, запит у `GeminiPlantService`, `GEMINI_MODEL`, картка ключа в `SettingsScreen`).
  - Звірено чинну офіційну документацію Firebase AI Logic, App Check, Play Integrity, налаштування Android та Cloud Billing. Не підтверджено документацією: точні безкоштовні ліміти, Blaze для конкретного проєкту, Play Integrity для adb-встановлень.
  - Створено `docs/tasks/TASK-019-firebase-ai-logic.md` (план, тести, відкат) та `docs/tasks/TASK-019-owner-firebase-guide.md` (інструкція Власника зі значеннями `com.flowers.app` і SHA debug-ключа).
  - Додано ADR-009, оновлено TASK_BOARD і CURRENT_STATE.
- **Підсумок:** план готовий; блокер для реалізації — дії Власника в консолі Firebase та `google-services.json`.

---

## Сесія 010 — 2026-11-19 — Агент 0
- **Мета:** усунути блокер TASK-019: `com.flowers.app` зайнятий у Google Play.
- **Результати:** `applicationId` → `com.savoj.flowers` (ADR-010); namespace і пакети без змін. Debug-збірка успішна, 19/19 тестів. На Samsung `RZCX326349A` видалено `com.flowers.app`, встановлено `com.savoj.flowers`, застосунок запущено (фокус вікна на `com.savoj.flowers/com.flowers.app.MainActivity`, збоїв у crash-буфері немає). Дані старої версії (рослини, налаштування, ключ) не перенесено. Оновлено інструкцію Власника (ID, крок 6.3), план TASK-019 (розділ CI: CI немає), store-документи.
- **Підсумок:** можна реєструвати застосунок у Firebase з `com.savoj.flowers`.

---

## Сесія 011 — 2026-11-19 — Antigravity (Розробник)
- **Мета:** реалізація підзавдання TASK-019-B за планом `docs/tasks/TASK-019-firebase-ai-logic.md` (Firebase AI Logic + Firebase App Check).
- **Результати:**
  - Перевірено наявність `app/google-services.json` з `package_name = com.savoj.flowers`.
  - Створено гілку `feat/task-019-firebase-ai-logic` від актуального `origin/main`.
  - Першим комітом (`125ab90`) додано `google-services.json` до `.gitignore`, перевірено через `git check-ignore`.
  - Поставлено тег `pre-task-019` на `main` для можливості відкату.
  - Оновлено `gradle/libs.versions.toml`: додано `firebaseBom = "35.2.0"`, `googleServices = "4.6.0"`, бібліотеки `firebase-bom`, `firebase-ai` (18.2.0), `firebase-appcheck-playintegrity`, `firebase-appcheck-debug`, плагін `google-services`.
  - Оновлено `build.gradle.kts` та `app/build.gradle.kts`.
  - Створено класи `AppCheckHelper` у `app/src/debug` (DebugAppCheckProviderFactory) та `app/src/release` (PlayIntegrityAppCheckProviderFactory). Перевірено, що релізний APK не містить debug-провайдера.
  - Ініціалізовано Firebase та App Check у `FlowersApplication.kt`.
  - `GeminiPlantService.kt` переведено на `Firebase.ai(backend = GenerativeBackend.googleAI())`. Збережено парсинг відповіді, валідацію `coerceIn(3, 28)` та Room-збереження. Додано обробку винятків з виведенням зрозумілих україномовних помилок.
  - Оновлено `FlowersRepositoryImpl.kt` (прибрано передачу ключа).
  - У `UserPreferences.kt` прибрано властивість `geminiApiKey`, додано автоматичне одноразове видалення старого ключа з `SharedPreferences`.
  - У `SettingsScreen.kt` вилучено блок введення ключа та збереження, додано картку «ШІ-генератор догляду: Захищено Firebase App Check ✅».
  - Оновлено `GeminiConfigTest.kt` (перевірка константи моделі `gemini-3.5-flash-lite`).
  - Успішно пройдено юніт-тести (`./gradlew.bat test`), зібрано `assembleDebug` та `assembleRelease`.
  - Оновлено `docs/THIRD_PARTY.md` з новими залежностями Firebase (Apache 2.0).
  - Застосунок встановлено на телефон Samsung Galaxy S24 Ultra (`RZCX326349A`).
  - Отримано App Check debug secret з Logcat для подальшої реєстрації Власником у консолі Firebase.
  - Створено коміт та відкрито PR на GitHub (без злиття, очікує QA та підтвердження Власника).
- **Підсумок:** підзавдання TASK-019-B виконано. PR відкрито для незалежної QA-перевірки.

---

## Сесія 012 — 2026-11-19 — Antigravity (незалежний QA-агент, модель Antigravity / Gemini)
- **Мета:** незалежний аудит якості PR #5 (гілка `feat/task-019-firebase-ai-logic`, TASK-019-B) за чеклістом QA.
- **Результати:**
  - Робоча копія перевірена — чиста; гілку PR синхронізовано.
  - Проведено повну перевірку збірки й тестів: `cleanTestDebugUnitTest`, `testDebugUnitTest` (18/18 зелені), `assembleDebug`, `assembleRelease` — BUILD SUCCESSFUL.
  - З'ясовано різницю 19 → 18 тестів: видалено тест прямої URL-побудови `url is built from the single model constant and carries no key`, оскільки допоміжна функція `geminiGenerateContentUrl()` видалена разом із переходом на Firebase AI Logic SDK; константу моделі закріплено тестом `gemini model is constant and uses supported flash lite family`. Оформлено зауваження QA щодо необхідності покрити юніт-тестами метод `mapExceptionToHumanReadableMessage` у наступній технічній задачі.
  - Секрети перевірено: `google-services.json` у `.gitignore` першим комітом (`125ab90`) і відсутній у всій історії git; debug-секрет App Check відсутній у коді, документації та git; нових ключів `AIza`, `apiKey`, `key=` у diff немає.
  - App Check: debug-провайдер ізольовано в `src/debug` та `debugImplementation`; у dex релізного APK сліди `DebugAppCheckProviderFactory` відсутні. Ініціалізація в `FlowersApplication.onCreate()` виконується до звернень до Firebase.
  - Код та UI: `GeminiPlantService` використовує `Firebase.ai` з `GenerativeBackend.googleAI()` та `GEMINI_MODEL`; парсинг і Room-збереження не зламано; помилки показуються українською без сирого JSON; у Налаштуваннях немає поля ключа; збережений ключ стирається з `SharedPreferences`.
  - Документація: `docs/THIRD_PARTY.md` містить залежності Firebase SDK (Apache 2.0); зміни в `docs/governance/` дотримуються принципу append-only.
  - Live-перевірка: враховано фактичне підтвердження Власника від 2026-11-19 11:52 (генерація картки працює на Samsung Galaxy S24 Ultra).
  - Опубліковано коментар «QA Verified» у PR #5.
  - PR #5 злито (squash) у гілку `main`, локальну робочу копію переключено на `main` та синхронізовано (`git pull origin main`).
  - Оновлено `CURRENT_STATE.md` та `TASK_BOARD.md`.
- **Підсумок:** підзавдання TASK-019-B успішно верифіковано (`QA Verified`) та злито в `main`.
