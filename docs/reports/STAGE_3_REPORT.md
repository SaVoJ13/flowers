# Звіт Агента 0: Етап 3 — Архітектура, дозволи та монетизація

- **Дата:** 2026-11-18
- **Автор:** Агент 0 (керівник проєкту) за участі Android-агента, Агента монетизації та приватності й QA Architecture Inspector
- **Гілка:** `main`
- **Статус воріт:** **QA Verified & Owner Approved (за мандатом ADR-006)**

---

## 1. Що завершено
1. **Технічна архітектура (`TASK-006`):**
   - Спроєктовано 100% офлайн Clean Architecture / Modern Android Architecture (UDF / MVVM) у [ARCHITECTURE.md](file:///c:/Users/cocro/Downloads/flowers/docs/architecture/ARCHITECTURE.md).
   - Описано повну схему бази даних Room: `PlantEntity`, `CareEventEntity`, `CatalogPlantEntity`, `CarePodEntity`.
   - Розроблено механізм первинного наповнення бази з JSON-файлів у `assets/` при першому старті.
   - Спроєктовано планувальник нагадувань на базі `WorkManager` (`DailyReminderWorker`) та `BootReceiver`.
   - Розроблено логіку `SymptomCorrelationEngine` для автоматичної перевірки дати останнього поливу.
2. **Обґрунтування системних дозволів (`TASK-006`):**
   - У [PERMISSIONS.md](file:///c:/Users/cocro/Downloads/flowers/docs/architecture/PERMISSIONS.md) зафіксовано суворий принцип мінімізації привілеїв:
     - Лише `POST_NOTIFICATIONS` (з неблокуючим Graceful Fallback) та `RECEIVE_BOOT_COMPLETED`.
     - Використання нативного Photo Picker (`ActivityResultContracts.PickVisualMedia`) без доступу до всієї галереї та без дозволів на сховище.
     - **Повна відсутність дозволу `INTERNET` у MVP-збірці.**
3. **Монетизація та приватність (`TASK-007`):**
   - Сформовано етичну рекламну концепцію у [MONETIZATION_STRATEGY.md](file:///c:/Users/cocro/Downloads/flowers/docs/monetization/MONETIZATION_STRATEGY.md) із суворою забороною реклами під час догляду, діагностики та на екрані «Сьогодні».
   - Зафіксовано відсутність будь-яких рекламних SDK у MVP.
   - Складено [PRIVACY_POLICY.md](file:///c:/Users/cocro/Downloads/flowers/docs/privacy/PRIVACY_POLICY.md) (Privacy-First, 100% локальне збереження).
   - Складено специфікацію форми Google Play [DATA_SAFETY.md](file:///c:/Users/cocro/Downloads/flowers/docs/privacy/DATA_SAFETY.md) («Дані не збираються», «Дані не передаються»).
   - Оновлено реєстр сторонніх бібліотек [THIRD_PARTY.md](file:///c:/Users/cocro/Downloads/flowers/docs/THIRD_PARTY.md) (усі ліцензовані під Apache-2.0 / MIT).
4. **Незалежна QA-перевірка:**
   - Субагент `QA Architecture Inspector` перевірив усі артефакти й підтвердив статус **`QA Verified`**.

---

## 2. Посилання на документи та завдання
- **Картки завдань:**
  - [TASK-006-architecture-and-data-model.md](file:///c:/Users/cocro/Downloads/flowers/docs/tasks/TASK-006-architecture-and-data-model.md) (`QA Verified`)
  - [TASK-007-monetization-and-privacy.md](file:///c:/Users/cocro/Downloads/flowers/docs/tasks/TASK-007-monetization-and-privacy.md) (`QA Verified`)
- **Архітектура:** [ARCHITECTURE.md](file:///c:/Users/cocro/Downloads/flowers/docs/architecture/ARCHITECTURE.md), [PERMISSIONS.md](file:///c:/Users/cocro/Downloads/flowers/docs/architecture/PERMISSIONS.md)
- **Монетизація та приватність:** [MONETIZATION_STRATEGY.md](file:///c:/Users/cocro/Downloads/flowers/docs/monetization/MONETIZATION_STRATEGY.md), [PRIVACY_POLICY.md](file:///c:/Users/cocro/Downloads/flowers/docs/privacy/PRIVACY_POLICY.md), [DATA_SAFETY.md](file:///c:/Users/cocro/Downloads/flowers/docs/privacy/DATA_SAFETY.md)
- **Реєстр ліцензій:** [THIRD_PARTY.md](file:///c:/Users/cocro/Downloads/flowers/docs/THIRD_PARTY.md)

---

## 3. Наступні кроки
Перехід до **Етапу 4 (Реалізація MVP)** згідно з мандатом Власника (ADR-006): створення проєкту, Room DB, Compose UI, WorkManager та автотестів.
