# TASK-049 — Розширений формат безпеки каталогу (schema 2, ADR-020, на затвердження Власника)

Джерело: Власник 2026-11-21 (вибірка *pachira_aquatica*: NC State каже низька тяжкість, лише коти, отруйне насіння; прапорець був `TOXIC`). **PR не зливається до затвердження формату Власником (INBOX-012); партії B2+ заблоковано.**

## Зроблено
- `docs/content/catalog/FORMAT.md`: розділ «Безпека, schema 2», `schema: 2`; ADR-020 (Пропоновано); INBOX-012.
- Валідатор (`CatalogValidator`): `safety.cats/dogs/children` (рівні), `toxicParts`, `sourcesDisagree`, `explanation` 4 мовами (маркер «джерела розходяться» обов'язковий при розбіжності), `sourceIds`; старі `pets/children` відхиляються; `manifest.schema` = 2. 10 нових тестів (рівні, частини, дублі, суперечність, пояснення, джерела, схема 1 відхиляється). 69/69 тести каталогу.
- Міграція всіх 50 карток B0–B1 (`migrate_safety`): 5 агентів Opus перечитали ≈ 110 сторінок джерел (ASPCA не використовували); `contentVersion: 3`; цитати-свідчення в `docs/content/catalog/drafts/safety.evidence.json`; застарілі примітки про безпеку прибрано; розбіжності в `notes` (англійською).

## Результат міграції (30 із 50 карток змінили оцінку)
- Уточнення тяжкості `TOXIC` → `LOW/MODERATE/SEVERE`; `dieffenbachia_seguine` і `schefflera` → `SEVERE` (schefflera: `sourcesDisagree`, суперечність усередині NC State: «Low» проти «seriously toxic»).
- `pachira_aquatica`: коти `LOW`, собаки `UNKNOWN`, діти `LOW`, лише `SEEDS`.
- `syngonium`: діти `MODERATE` (NC State «Poisonous to Humans», Medium; початкова міграція помилково мала `UNKNOWN`, виправлено за QA); `fatsia_japonica`: `UNKNOWN` → `TOXIC` з `sourcesDisagree` (RHS «Potentially harmful» проти тегів NC State).
- Без змін: `NON_TOXIC` для котів і собак лишається у chlorophytum, peperomia, calathea, phalaenopsis_hybrid, hoya_carnosa, nephrolepis_exaltata, ocimum_basilicum, salvia_rosmarinus, beaucarnea_recurvata, aspidistra_elatior, cissus_rhombifolia; pilea_peperomioides NON_TOXIC для всіх трьох; `UNKNOWN` лишається в maranta, citrus_limon, cyperus_alternifolius.
- Судження агентів, які варто перевірити QA: `dracaena_marginata` (NC State описує *D. reflexa*, агент не знайшов в ній *D. marginata*; суперечить попередній примітці), `epipremnum` `sourcesDisagree` (NC State Medium проти Clemson «mildly»), правило «TOXIC без тяжкості + low severity = LOW».

## Не зроблено
Імпортер і UI (TASK-037); вибірка Власника; затвердження формату.

## QA (Opus, 20 карток глибоко + 48 сторінок NC State скриптом) → Needs changes → виправлено
Виправлено: `syngonium` діти → `MODERATE`; `dracaena_marginata` використовувала хибне джерело (var. reflexa), тепер `ncsu_dracaena_reflexa_angustifolia`: діти `LOW`, отруйні частини з джерела; `ficus_elastica` діти `LOW` → `TOXIC` (джерело описує шкоду без названої тяжкості); `zamioculcas` діти `MODERATE` → `TOXIC`; `dracaena_reflexa` діти `UNKNOWN` → `LOW`; правило для дітей записано у FORMAT.md; `notes` перебудовано (без обрізання й англійської суміші, небезпекові примітки повернуто); тексти (`pachira` uk, `mentha` uk «колосиста», `schefflera` ×4, `epipremnum` ×4 узгоджено з `toxicParts`, diarrhea); валідатор: заборона ы э ё ъ / і ї є ґ, переважно кирилиця, вужчі маркери розбіжності, зворотна перевірка, нейтральний текст для всіх UNKNOWN; FORMAT.md: порядок рівнів, що перевіряє QA. Правило суперечностей лишається (виняток QA: джерело без тяжкості з сильнішим формулюванням, напр. «seriously», «fatal», є суперечністю: у наявних картках таких немає).

## 2026-11-22 — Codex, передача роботи

- Власник затвердив формат schema 2 у дорученні; статус ADR-020 доповнено. Пункти INBOX-012 про правило суперечностей і ASPCA ще не отримали окремої відповіді.
- Переглянуто пояснення всіх 50 карток чотирма мовами. Прибрано внутрішній жаргон і непотрібне твердження про те, що кімнатна пахіра не дає насіння; переписано пояснення для дев’яти карток чотирма мовами, решту формулювань про джерела очищено від службового слова «теги». Копії в `assets` і `docs` синхронні.
- До `FORMAT.md` додано обов’язкову QA-перевірку кожного пояснення на зрозумілість і підтвердження всіх тверджень джерелами.
- Стан: PR #26, локальні правки; чекають автоматичні тести, незалежна QA та злиття.
- Наступний крок: запустити валідатор і тести каталогу, виправити виявлене, передати PR незалежній QA іншою моделлю.

### Перевірка 2026-11-22

- `:app:testDebugUnitTest --rerun-tasks` з JDK 21: `BUILD SUCCESSFUL`, 154 тести, 0 помилок; `git diff --check` без зауважень.
- Файли B0/B1 у `assets` і `docs` побайтово однакові; 50 карток, 200 пояснень. У поясненнях не лишилося фраз «лише теги» або твердження про відсутність насіння в кімнатної пахіри.
