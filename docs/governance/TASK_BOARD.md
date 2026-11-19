# TASK BOARD (Дошка завдань)

> Статуси завдань проєкту за етапами. Оновлюється Агентом 0.

---

## Беклог / Заплановано
- [ ] `TASK-020` [Тех. борг, QA] Юніт-тести для мапінгу помилок ШІ на українські тексти (`mapExceptionToHumanReadableMessage`, зауваження QA PR #5).
- [ ] `TASK-021` [Перед публікацією] Додати SHA-1 ключа підпису Google Play (Play Console → App signing → App signing key certificate) в обмеження Android-ключа, у Firebase і в App Check (Play Integrity), інакше ШІ не працюватиме в опублікованій версії; оновити PRIVACY_POLICY / DATA_SAFETY / PERMISSIONS (колишнє 019-E). Блокується акаунтом Play Console (Власник).
- [ ] `TASK-022` [Платна версія, ПРОПОНОВАНО] ШІ-шлюз (Cloud Functions), анонімний вхід, квоти, дисклеймер і кнопка скарги. Картка: `docs/tasks/TASK-022-ai-gateway-quotas.md`. Чекає рішення Власника (ADR-012), Blaze і кроку 7 (бюджет).
- [ ] `TASK-023` [Платна версія, ПРОПОНОВАНО] Play Billing + серверна перевірка підписки + RTDN (після TASK-022).
- [ ] `TASK-024` [Платна версія, ПРОПОНОВАНО] «Лікар рослин» (флагман підписки, після TASK-022/023).
- [ ] `TASK-025` [Політика Play] Токсичність лише з перевірених джерел: прибрати `toxic_to_pets` із ШІ-генерації, позначати «не перевірено».
- Дорожня карта: `docs/product/AI_ROADMAP.md`.

---

## В роботі
- *(Немає активних завдань; усі пропоновані завдання в Беклозі чекають рішення Власника.)*

---

## На перевірці (QA / Review)
- *(Усі поточні завдання перевірено, PR #5 злито в main)*

---

## Завершено (Done)
- [x] `TASK-019` [Платна версія] Перехід на Firebase AI Logic + App Check: 019-A..D виконано; 2026-11-19 Власник виконав крок 6 (ключ Android key обмежено до `com.savoj.flowers` + debug SHA-1; Generative Language API немає у списку), генерація на телефоні працює. Крок 7 (бюджет) відкладено до переходу на Blaze. 019-E перенесено в TASK-021.
- [x] `TASK-019-B` [Платна версія] Реалізація міграції на Firebase AI Logic + App Check (QA Verified, PR #5 злито, Live Tested Власником на Samsung S24 Ultra 2026-11-19)
- [x] `TASK-018` [Hotfix] Заміна вимкненої моделі Gemini на gemini-3.5-flash-lite, константа GEMINI_MODEL (QA Verified; перевірено Власником на пристрої 2026-11-19, PR #1 злито)
- [x] `TASK-017` [Етап 8] Інтеграція ШІ-генератора догляду (Gemini Flash), Freemium-архітектури та розширення каталогу (QA Verified)
- [x] `TASK-016` [Hotfix] Виправлення вибору ботанічного виду та завантаження каталогу (Gson `@SerializedName`, pre-population) (QA Verified)
- [x] `TASK-015` [Hotfix] Виправлення пошуку рослин у вкладці Довідник (кирилиця, case-insensitivity, UI-фільтрація) (QA Verified)
- [x] `TASK-000` [Етап 0] Аудит репозиторію, середовища та розгортання Governance-контуру v2.0 (Агент 0, Owner Approved)
- [x] `TASK-001` [Етап 1] Дослідження ринку, аналіз відгуків Google Play, формування PRD (QA Verified, Owner Approved)
- [x] `TASK-003` [Етап 2] Проєктування User Flows, навігації та макетів екранів MVP (QA Verified, Owner Approved)
- [x] `TASK-004` [Етап 2] Складання базового каталогу рослин та структури подів-підказок (QA Verified, Owner Approved)
- [x] `TASK-005` [Етап 2] Розробка детермінованого дерева симптомів та причин для офлайн-аналізу стану (QA Verified, Owner Approved)
- [x] `TASK-006` [Етап 3] Проєктування архітектури Android-додатку, схеми Room та сповіщень WorkManager (QA Verified, Owner Approved)
- [x] `TASK-007` [Етап 3] Розробка стратегії реклами та чернеток політики приватності (QA Verified, Owner Approved)
- [x] `TASK-008` [Етап 4] Ініціалізація Android-проєкту та базової дизайн-системи Compose (QA Verified, ADR-006)
- [x] `TASK-009` [Етап 4] Реалізація бази даних Room, репозиторіїв та локальних ассетів (QA Verified, ADR-006)
- [x] `TASK-010` [Етап 4] Реалізація екранів MVP на Jetpack Compose (Сьогодні, Рослини, Деталі, Діагностика, Каталог, Налаштування) (QA Verified, ADR-006)
- [x] `TASK-011` [Етап 4] Реалізація WorkManager сповіщень та кореляції симптомів (QA Verified, ADR-006)
- [x] `TASK-012` [Етап 5] Комплексне незалежне тестування функціоналу MVP, 100% автотести (QA Verified)
- [x] `TASK-013` [Етап 6] Підготовка матеріалів для Google Play та вимог закритого тестування (QA Verified)
- [x] `TASK-014` [Етап 7] Збірка debug APK, успішне встановлення та запуск на фізичному телефоні Samsung Galaxy S24 Ultra (QA Verified, Live Tested)
