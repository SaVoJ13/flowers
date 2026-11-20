# TASK-020 — Юніт-тести мапінгу помилок ШІ на українські тексти

- **Статус:** Реалізовано, очікує незалежної QA (PR `feat/task-020-ai-error-mapping-tests`).
- **Джерело:** `docs/release/RELEASE_1_0_PLAN.md` §2.1; зауваження QA PR #5.
- **Обсяг:** `mapExceptionToHumanReadableMessage` винесено з `GeminiPlantService` у `object AiErrorMapper` (`data/remote/AiErrorMapper.kt`); тексти не змінено; додано `AiErrorMapperTest` (16 тестів: немає інтернету ×3, 403/App Check ×4, 429/ліміт ×2, 5xx, невідома, пріоритети ×3, унікальність текстів).

## Журнал

### 2026-11-19 — Розробник (Агент, сесія 015)
- Зроблено: рефакторинг у `AiErrorMapper` (поведінка та тексти без змін), 16 нових тестів. `testDebugUnitTest`: 34/34 зелені (18 + 16); `assembleDebug`, `assembleRelease` успішні.
- Стан: гілка `feat/task-020-ai-error-mapping-tests`.
- Зупинився на: перевірка на телефоні RZCX326349A (пристрій не був підключений до adb на момент збірки; див. SESSION_LOG), незалежна QA.
- Наступний крок: QA іншою моделлю → злиття; після нього TASK-026 оновить текст 429 і відповідний тест.
- Увага / пастки: пріоритет гілок зафіксовано тестами: мережа > App Check/403 > 429/quota > 5xx. «403 quota» дає App Check.

### 2026-11-20 — Розробник (Агент, виправлення дефекту після перевірки Власника)
- Дефект (знайшов Власник на Samsung RZCX326349A, Wi-Fi і дані вимкнено, ШІ-пошук «тюльпан»): замість «немає мережі» показувалось загальне «Не вдалося згенерувати картку…». Юніт-тести були зеленими, бо використовували «чисті» винятки.
- Причина (звірено з байткодом firebase-ai 18.2.0, `FirebaseAIException.from`): будь-який невідомий виняток, зокрема `UnknownHostException` від Ktor, SDK загортає в `UnknownException("Something unexpected happened.", cause)`, а таймаут Ktor у `RequestTimeoutException`. Мапер дивився лише на верхній виняток і його message.
- Виправлено: `AiErrorMapper.map(Throwable)` обходить весь ланцюжок cause і suppressed (ширина, без циклів, глибина ≤16); мережа: `UnknownHostException`, `ConnectException`, `NoRouteToHostException`, `SocketException`, будь-який `IOException`; таймаут (клас з «Timeout» у назві) має окреме повідомлення; `QuotaExceededException`/`ServerException` SDK розпізнаються за типом. Перед запитом сервіс питає `NetworkChecker` (INTERNET + VALIDATED); без мережі одразу `MSG_NO_INTERNET`, модель не викликається.
- Нові тексти: «Немає підключення до інтернету. Перевірте Wi-Fi або мобільні дані і спробуйте ще раз.»; загальне «Не вдалося згенерувати картку догляду. Перевірте назву рослини та підключення до інтернету і спробуйте ще раз.»; **новий** (не в доручені Власника) текст таймауту «Запит до ШІ триває надто довго. Перевірте підключення до інтернету і спробуйте ще раз.» (Власнику вирішити, чи залишати).
- Зміна поведінки: тест «plain io exception without network hint is unknown» замінено на «plain io exception is a network error» (будь-який IOException тепер = мережа).
- Нове в маніфесті: normal-дозвіл `ACCESS_NETWORK_STATE` (потрібен для `getNetworkCapabilities`; без запиту користувачу, дані не збираються).
- Тести: +17 (13 у `AiErrorMapperTest` з РЕАЛЬНИМИ класами SDK через рефлексію `SdkExceptions`, вкладеність 1–3 рівні, suppressed, цикл; 4 у `GeminiPlantServiceNetworkTest`: офлайн без виклику моделі, онлайн викликає, обгорнутий UnknownHost через сервіс, порожній запит). Усього 66/66.
- Статус: потрібна незалежна QA іншою моделлю (автор виправлення не верифікує власну роботу, конституція п. 2.1).

