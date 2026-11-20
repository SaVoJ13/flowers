# TASK-038 — Формат каталогу, схема, валідатор (реліз 1.1, хвиля 0)

Рішення: INBOX-008 Q-1.1-1 (формат, Room v3, «ID не видаляються») = ТАК (ADR-018). Лише документи, дані й тест; код застосунку не змінено.

## Зроблено
- `docs/content/catalog/FORMAT.md`: схема `schema: 1`, enum-и, межі, правила партій, поріг оригінальності (< 8 слів поспіль).
- `app/src/main/assets/catalog/{manifest,sources}.json` (порожній каталог, `contentVersion: 0`) і копія в `docs/content/catalog/`.
- Валідатор `CatalogValidator` + `OverlapChecker` (test source set), `CatalogValidatorTest`: 33 тести (позитивні, 26 негативних, файли в assets валідні, копія docs = assets, список 20 ID збігається з `PLANT_CATALOG.json`).
- Валідатор у `testDebugUnitTest`, отже виконується в CI/локальних тестах.

## Не зроблено (навмисно)
Імпортер і Room v3 (TASK-037), партії карток (TASK-040), список рослин (TASK-045). Коли з'явиться B0, `contentVersion` ≥ 1 вмикає перевірку присутності всіх 20 наявних ID.
