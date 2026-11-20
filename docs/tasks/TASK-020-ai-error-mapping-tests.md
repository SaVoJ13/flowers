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
