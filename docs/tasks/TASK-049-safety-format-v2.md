# TASK-049 — Розширений формат безпеки каталогу (schema 2, ADR-020, на затвердження Власника)

Джерело: Власник 2026-11-21 (вибірка *pachira_aquatica*: NC State каже низька тяжкість, лише коти, отруйне насіння; прапорець був `TOXIC`). **PR не зливається до затвердження формату Власником (INBOX-012); партії B2+ заблоковано.**

## Зроблено
- `docs/content/catalog/FORMAT.md`: розділ «Безпека, schema 2», `schema: 2`; ADR-020 (Пропоновано); INBOX-012.
- Валідатор (`CatalogValidator`): `safety.cats/dogs/children` (рівні), `toxicParts`, `sourcesDisagree`, `explanation` 4 мовами (маркер «джерела розходяться» обов'язковий при розбіжності), `sourceIds`; старі `pets/children` відхиляються; `manifest.schema` = 2. 10 нових тестів (рівні, частини, дублі, суперечність, пояснення, джерела, схема 1 відхиляється). 69/69 тести каталогу.
- Міграція всіх 50 карток B0–B1 (`migrate_safety`): 5 агентів Opus перечитали ≈ 110 сторінок джерел (ASPCA не використовували); `contentVersion: 3`; цитати-свідчення в `docs/content/catalog/drafts/safety.evidence.json`; застарілі примітки про безпеку прибрано; розбіжності в `notes` (англійською).

## Результат міграції (30 із 50 карток змінили оцінку)
- Уточнення тяжкості `TOXIC` → `LOW/MODERATE/SEVERE`; `dieffenbachia_seguine` і `schefflera` → `SEVERE` (schefflera: `sourcesDisagree`, суперечність усередині NC State: «Low» проти «seriously toxic»).
- `pachira_aquatica`: коти `LOW`, собаки `UNKNOWN`, діти `LOW`, лише `SEEDS`.
- `syngonium`: діти `TOXIC` → `UNKNOWN` (джерела не підтверджують); `fatsia_japonica`: `UNKNOWN` → `TOXIC` з `sourcesDisagree` (RHS «Potentially harmful» проти тегів NC State).
- Без змін: `NON_TOXIC` для котів і собак лишається у chlorophytum, peperomia, calathea, phalaenopsis_hybrid, hoya_carnosa, nephrolepis_exaltata, ocimum_basilicum, salvia_rosmarinus, beaucarnea_recurvata, aspidistra_elatior, cissus_rhombifolia; pilea_peperomioides NON_TOXIC для всіх трьох; `UNKNOWN` лишається в maranta, citrus_limon, cyperus_alternifolius.
- Судження агентів, які варто перевірити QA: `dracaena_marginata` (NC State описує *D. reflexa*, агент не знайшов в ній *D. marginata*; суперечить попередній примітці), `epipremnum` `sourcesDisagree` (NC State Medium проти Clemson «mildly»), правило «TOXIC без тяжкості + low severity = LOW».

## Не зроблено
Імпортер і UI (TASK-037); вибірка Власника; затвердження формату.
