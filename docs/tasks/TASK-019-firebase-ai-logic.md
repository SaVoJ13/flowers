# TASK-019: Перехід на Firebase AI Logic + App Check

## 1. Загальні відомості
- **ID:** TASK-019 · **Тип:** Платна версія / безпека · **Дата створення:** 2026-11-19
- **Роль зараз:** Архітектор (лише план і документація; код у цій сесії НЕ змінюється)
- **Статус:** План підготовлено → очікує дій Власника в консолі Firebase (див. `TASK-019-owner-firebase-guide.md`), потім реалізація
- **Пов'язане:** ADR-008 (проблема), ADR-009 (рішення), TASK-018 (попередній hotfix)
- **Мета:** користувач більше не вводить і не зберігає Gemini-ключ у застосунку. Запити йдуть через Firebase AI Logic, а захищає їх App Check.

## 2. Поточна інтеграція (досліджено в коді, 2026-11-19)
| Що | Де |
|---|---|
| Ключ | `UserPreferences.geminiApiKey` (SharedPreferences `flowers_user_preferences`, ключ `key_gemini_api_key`) |
| Запит | `GeminiPlantService.generatePlantCareCard(plantQuery, apiKey)`: ручний `HttpURLConnection` POST на `geminiGenerateContentUrl()`, заголовок `x-goog-api-key`, тіло з `generationConfig {responseMimeType: application/json, temperature 0.2}` |
| Модель | `GEMINI_MODEL = "gemini-3.5-flash-lite"` у `data/remote/GeminiConfig.kt` |
| Виклик | `FlowersRepositoryImpl.generatePlantWithAi()` бере ключ з `userPreferences` |
| UI | `SettingsScreen.kt` (картка «Google Gemini API Ключ»: поле, кнопка «Зберегти», стан `isKeySaved`); `CatalogScreen.kt` (діалог генерації, тексти про Gemini); `CatalogViewModel` (`isPremium`) |
| Збереження результату | Room: `catalogDao.insertAll`, `carePodDao.insertAll` (не змінюється) |
| Тести | `GeminiPlantServiceTest` (парсинг відповіді), `GeminiConfigTest` |
| Збірка | `applicationId = com.savoj.flowers` (змінено з `com.flowers.app`, ADR-010; `namespace` і Kotlin-пакети лишаються `com.flowers.app`), minSdk 26, compileSdk/targetSdk 35, AGP 8.7.3, Kotlin 2.0.21, JDK 21 (L-004) |

## 3. Що кажуть чинні офіційні джерела (звірено 2026-11-19)
Джерела: firebase.google.com/docs/ai-logic (get-started, app-check, faq-and-troubleshooting, quotas, models), firebase.google.com/docs/app-check/android/play-integrity-provider, firebase.google.com/docs/android/setup, docs.cloud.google.com/billing/docs/how-to/budgets, aistudio.google.com/docs/rate-limits.

- **Android, Gemini Developer API:** потрібен minSdk ≥ 24 (у нас 26 ✔). Вхід у консолі: *AI Services → AI Logic → Get started → провайдер «Gemini Developer API»*. Майстер сам вмикає потрібні API і автоматично вмикає примусову перевірку App Check для AI Logic.
- **Залежності (BoM 35.2.0):** `platform("com.google.firebase:firebase-bom:35.2.0")`, `firebase-ai`, `firebase-appcheck-debug` (лише debug), `firebase-appcheck-playintegrity` (release). Без BoM: `firebase-ai:18.2.0`, `firebase-appcheck-debug:20.1.0`, `firebase-appcheck-playintegrity:20.1.0`.
- **Плагін:** `com.google.gms.google-services` версія 4.6.0 (root: `apply false`; app: без версії). Файл `google-services.json` кладеться в `app/`; не перейменовувати (без «(2)»).
- **Ініціалізація моделі:** `Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("<модель>")`; методи suspend (`generateContent(prompt)` → `response.text`). Приклад JSON/generationConfig у сторінці get-started відсутній — взяти з розділів «model parameters» і «structured output» під час реалізації.
- **API-ключ:** Firebase AI Logic більше не авторизує виклики Gemini Developer API стандартним Gemini-ключем, а використовує керований Google сервісний акаунт. Не класти Gemini-ключ у код застосунку; не додавати Gemini API до дозволів Firebase API-ключа. Автостворений ключ «Gemini Developer API key (auto created by Firebase)» не видаляти без перевірки Metrics.
- **App Check:** у консолі *Security → App Check → APIs → Firebase AI Logic → Set up*: Baseline = Enforced; Replay protection спершу Disabled. Debug-провайдер лише для debug-збірок: у Logcat рядок «Enter this debug secret into the allow list», токен реєструється в *Apps → Manage debug tokens*. Токен і debug-збірку не комітити та не випускати. Production: Play Integrity (потрібен SHA-256 підпису; у Play Console *App integrity → Link Cloud project* — лише Owner проєкту). TTL токена 30 хв – 7 діб (за замовчуванням 1 год).
- **Replay protection (етап 2):** `Firebase.ai(backend=..., useLimitedUseAppCheckTokens = true)`, BoM ≥ 34.14.0; у консолі спочатку «Monitoring only».
- **Тарифи:** FAQ: Gemini Developer API доступний і на безкоштовному **Spark**, і на **Blaze**. Сторінка цін Firebase цього прямо не підтверджує (клітинка Spark порожня). Розміри безкоштовних лімітів у документації Firebase **не наведено**; на сторінці лімітів AI Studio для `gemini-3.5-flash-lite` RPM/RPD також не вказано. **Не перевірено:** точні безкоштовні ліміти. Якщо проєкт на Blaze, усі виклики платні. Кредити $300 пробного періоду для Gemini Developer API не діють.
- **Ліміти/бюджет:** за замовчуванням Firebase AI Logic обмежує **100 запитів/хв на користувача** (змінюється в Google Cloud: *Firebase AI Logic API → Manage → Quotas & System Limits → «Generate content requests» → Edit quota*, право `serviceusage.quotas.update`). Бюджетні сповіщення: *Billing → Budgets & alerts → Create budget → Alerts only*; пороги за замовчуванням 50/90/100 %. **Alerts-only бюджет НЕ зупиняє витрати**, листи можуть приходити із запізненням у кілька годин.
- **Модель:** `gemini-3.5-flash-lite` у списку підтримуваних моделей Firebase AI Logic, стабільна, без дати вимкнення, не вимагає білінгу. Документація радить НЕ хардкодити назву моделі, а віддавати її через Firebase Remote Config (щоб міняти без релізу) і не використовувати `-latest`.
- **Не перевірено / ризики документації:** сумісність `firebase-ai` 18.2.0 з нашими AGP 8.7.3 / Kotlin 2.0.21 / compileSdk 35 (можуть знадобитися новіші; це виявиться під час збірки); поведінка Play Integrity для APK, встановленого через `adb` (документація прямо не каже).

## 4. План міграції
### 4.1. Залежності та збірка
1. `gradle/libs.versions.toml`: `firebaseBom = "35.2.0"`, `googleServices = "4.6.0"`; бібліотеки `firebase-bom`, `firebase-ai`, `firebase-appcheck-playintegrity`, `firebase-appcheck-debug`; плагін `google-services`.
2. Root `build.gradle.kts`: плагін `apply false`; `app/build.gradle.kts`: `implementation(platform(bom))`, `implementation(firebase-ai)`, `implementation(appcheck-playintegrity)`, `debugImplementation(appcheck-debug)`; плагін google-services.
3. `.gitignore`: додати `app/google-services.json` (див. §7, рішення про коміт).
4. Якщо збірка вимагатиме новіші AGP/Kotlin/compileSdk — окремий коміт з записом у DECISIONS; усі 19 тестів мають лишитися зеленими.

### 4.2. Код
1. `FlowersApplication.onCreate()`: `Firebase.initialize(this)` і встановлення App Check **до будь-якого іншого Firebase-виклику**. Release → `PlayIntegrityAppCheckProviderFactory`; debug → `DebugAppCheckProviderFactory` (через `src/debug` source set з власним класом-провайдером, щоб debug-код не потрапляв у release).
2. `GeminiPlantService`: прибрати `HttpURLConnection`, URL та параметр `apiKey`. Модель: `Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(GEMINI_MODEL, generationConfig = {responseMimeType = "application/json"; temperature = 0.2f})`. Промпт, `parseGeminiResponse`/розбір у `GeneratedPlantResponse`, збереження в Room, `coerceIn(3,28)` залишаються без змін. Виняткова ситуація → `GeneratePlantResult.Error` з людським текстом (мережа, 403 App Check, 429 ліміт).
3. `GeminiConfig.kt`: `GEMINI_MODEL` лишається єдиним джерелом; `GEMINI_BASE_URL` та `geminiGenerateContentUrl()` видалити разом із `GeminiConfigTest` (замінити тестом на сталість `GEMINI_MODEL`). Remote Config для назви моделі: окремий пізніший крок (не в цьому завданні).
4. `FlowersRepositoryImpl.generatePlantWithAi()`: прибрати передачу ключа.
5. `UserPreferences`: прибрати `geminiApiKey`; при першому запуску нової версії один раз видалити збережений ключ (`prefs.edit().remove("key_gemini_api_key")`), щоб старі ключі не лишалися на телефонах.
6. `SettingsScreen.kt`: картку «Google Gemini API Ключ» (поле, «Зберегти», `isKeySaved`) **видалити**; замість неї інформаційна картка «ШІ-генератор: працює через захищений сервіс Flowers, ключ не потрібен». `CatalogScreen.kt`: тексти про «Gemini» залишити, прибрати згадки про ключ.
7. Дозволи: `INTERNET` уже є; нових дозволів немає.

### 4.3. Документація приватності (обов'язково перед публікацією)
`docs/privacy/PRIVACY_POLICY.md`, `DATA_SAFETY.md`, `docs/architecture/PERMISSIONS.md`: назва рослини, яку вводить користувач, передається в Google (Firebase AI Logic / Gemini); App Check/Play Integrity передає токен пристрою; оновити суперечливий п. PRD «жодних викликів Gemini» (docs/product/PRD.md:121). Це розширення збору даних → за п. 3.2 конституції потребує затвердження Власника.

### 4.4. CI та `google-services.json`
У репозиторії **немає CI**: каталогу `.github/` нема, workflow-файлів GitHub Actions чи іншого CI немає. Збірка виконується локально (JDK 21, `./gradlew.bat`). Тому наразі `google-services.json` лежить лише в `app/` на комп'ютері Власника. **Якщо CI з'явиться** (наприклад, GitHub Actions), порядок такий:
1. Власник кодує файл у base64 (PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\google-services.json"))`) і додає результат як **GitHub Secret** `GOOGLE_SERVICES_JSON_BASE64` (репозиторій → Settings → Secrets and variables → Actions → New repository secret). Секрети репозиторію належать до п. 3.2 конституції, тож це робить Власник.
2. Крок workflow перед збіркою: `echo "$GOOGLE_SERVICES_JSON_BASE64" | base64 -d > app/google-services.json`.
3. Якщо CI лише компілює й проганяє юніт-тести, debug-токен App Check не потрібен; для інструментальних тестів із Firebase знадобиться окремий debug-токен у секреті.
4. Без секрету збірка падає на плагіні `google-services` (файл відсутній). На pull request із форків секрети недоступні, тому такі збірки треба робити без Firebase (окремий flavor або заглушка).

## 5. Тестування
1. Юніт: парсинг (`GeminiPlantServiceTest`) без змін; тест сталості `GEMINI_MODEL`; нових мережевих юніт-тестів не пишемо (мок Firebase SDK надлишковий).
2. Збірка: `assembleDebug` і `assembleRelease` (JDK 21) без помилок; `google-services.json` присутній лише локально.
3. На телефоні Власника (debug-збірка): перший запуск → Logcat → debug-токен App Check → Власник реєструє його в консолі (інструкція, крок 8) → генерація «Філодендрон Біркін» успішна.
4. Негативні перевірки: літак-режим → зрозуміла помилка; debug-токен не зареєстровано → помилка 403 без падіння застосунку; Settings більше не показує поле ключа.
5. Перевірка безпеки: `git grep -i "AIza\|api_key"` порожній; у релізному APK немає Gemini-ключа та debug-провайдера (`apkanalyzer`/пошук класу `DebugAppCheckProviderFactory`).
6. Метрики: після перших викликів у консолі Firebase → AI Logic → Metrics видно запити; App Check → Metrics показує «Verified».
7. QA виконує інший агент (п. 2.1); live-перевірку на пристрої виконує Власник (п. 3.2).

## 6. Відкат
- Перед злиттям реалізації створити тег `pre-task-019` на поточному `main` (код з ключем у Налаштуваннях, TASK-018).
- Відкат коду: `git revert` squash-коміта PR реалізації (нова гілка + PR). Старі застосунки з ключем у SharedPreferences працюватимуть далі без змін.
- Відкат у консолі: App Check → Firebase AI Logic → **Unenforced** (миттєво знімає блокування запитів); за потреби видалити зареєстровані debug-токени.
- Дані Room не змінюються, міграцій БД немає.
- Якщо модель зникне (404): змінити `GEMINI_MODEL` (або Remote Config, коли буде впроваджено).

## 7. Рішення та ризики
- **Коміт `google-services.json`:** документація Firebase називає вміст «унікальними, але не секретними» ідентифікаторами, але прямо не радить комітити чи ні. Рішення Архітектора (суворіше, за замовчуванням): **не комітити** (`.gitignore`), файл лежить лише на комп'ютері Власника/агента; ризик — без нього не збереться нова чиста копія (описано в README/інструкції). Власник може змінити це рішення.
- **Play Integrity і релізний APK, встановлений через adb:** не підтверджено; до публікації в Play (закрите тестування) Власник тестує **debug-збірку** з debug-токеном. Для Play Integrity потрібен застосунок у Play Console і звʼязаний Cloud-проєкт (роль Owner).
- **Витрати:** починати на Spark; якщо консоль вимагатиме Blaze — спершу бюджетні сповіщення. Alerts-only бюджет не обмежує витрат, тому захист — App Check (блокує чужі клієнти) + зниження квоти на користувача + моніторинг Metrics. Преміум-перемикач `isPremium` зараз завжди true (локальний), реальної перевірки підписки немає; до появи платежів ліміти витрат — лише через квоти.
- **Витік старого ключа:** після міграції Власнику варто видалити/перегенерувати ключ у Google AI Studio, яким користувався раніше.
- **Залежності:** потребує запису в `docs/THIRD_PARTY.md` (Firebase BoM, Apache 2.0 — перевірити першоджерело під час реалізації).

## 8. Підзавдання реалізації
- 019-A (Власник): консоль Firebase за інструкцією → надіслати `google-services.json` та підтвердити App Check/бюджет.
- 019-B (Агент): гілка `feat/task-019-firebase-ai-logic`, §4.1–4.2, тести, PR.
- 019-C (QA-агент): перевірка за §5.
- 019-D (Власник): live-перевірка на телефоні, реєстрація debug-токена.
- 019-E (Агент 0): оновлення приватності (§4.3), закриття.

## 9. Журнал виконання (Append-only)
- `2026-11-19`: Створено картку TASK-019. Власник вирівняв локальну `main` з `origin/main` (резервна гілка `backup/local-main-2026-11-19`). Вивчено поточну інтеграцію, звірено чинну офіційну документацію Firebase AI Logic, App Check, Play Integrity, білінгу. Написано план та інструкцію Власника. Код не змінювався.
- Наступний крок: Власник виконує інструкцію (`docs/tasks/TASK-019-owner-firebase-guide.md`) і надсилає `google-services.json`.
- `2026-11-19`: Блокер від Власника: `com.flowers.app` зайнятий у Google Play. Рішення Власника: новий applicationId `com.savoj.flowers` (ADR-010). Інструкцію Власника оновлено (крок 6.3 за security checklist), CI в репозиторії немає (§4.4). Реєстрацію в Firebase робити лише з новим ID.
- `2026-11-19`: Реалізовано підзавдання TASK-019-B (Розробник, Antigravity):
  1. Перевірено наявність `app/google-services.json` з `package_name = com.savoj.flowers`. Файл надійно додано до `.gitignore` першим комітом гілки `feat/task-019-firebase-ai-logic` (`125ab90`).
  2. Створено тег `pre-task-019` на актуальному `main` для безпечного відкату.
  3. Підключено плагін `google-services` 4.6.0, `Firebase BoM` 35.2.0, `firebase-ai` 18.2.0, `firebase-appcheck-playintegrity` та `firebase-appcheck-debug` (лише `debugImplementation`).
  4. Створено мультисорсове розділення `AppCheckHelper`: у `src/debug` — `DebugAppCheckProviderFactory`, у `src/release` — `PlayIntegrityAppCheckProviderFactory`. У релізному бінарнику налагоджувальний провайдер відсутній (верифіковано).
  5. Переведено `GeminiPlantService` на `Firebase.ai(backend = GenerativeBackend.googleAI())`. Збережено парсинг, валідацію та збереження в Room. Додано переклад помилок на зрозумілу українську мову.
  6. Вилучено введення та збереження API-ключа в `SettingsScreen` та `UserPreferences` (додано очищення старого ключа з `SharedPreferences`).
  7. Оновлено `GeminiConfigTest`, успішно пройдено юніт-тести (`./gradlew.bat test`), зібрано `assembleDebug` та `assembleRelease`.
  8. Оновлено `docs/THIRD_PARTY.md` з новими залежностями Firebase.
