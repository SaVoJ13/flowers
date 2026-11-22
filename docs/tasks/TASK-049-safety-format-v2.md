# TASK-049 — Розширений формат безпеки каталогу (schema 2, ADR-020, затверджено Власником)

Джерело: Власник 2026-11-21 (вибірка *pachira_aquatica*: NC State каже низька тяжкість, лише коти, отруйне насіння; прапорець був `TOXIC`). **Історичний стан на початку TASK-049:** PR не зливався до затвердження формату Власником, партії B2+ були заблоковані. Формат затверджено дорученням Власника 2026-11-22; актуальний стан наведено нижче.

## Зроблено
- `docs/content/catalog/FORMAT.md`: розділ «Безпека, schema 2», `schema: 2`; ADR-020 (первісно запропоновано, затверджено 2026-11-22); INBOX-012.
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

### Незалежна QA 2026-11-22 — Needs changes → виправлено

- QA виявила, що джерело для *Dracaena reflexa* підтверджує низьку токсичність для котів і собак, але не доводить її для людей або дітей. `children` повернуто з `LOW` до `UNKNOWN`; чотири пояснення переписано без непідтвердженого твердження.
- У `safety.evidence.json` виправлено застарілі підсумки для `zamioculcas` і `ficus_elastica`, а непідтверджене свідчення для дітей у `dracaena_reflexa` замінено на `null`. Початковий блокувальний статус у заголовку завдання позначено як історичний.
- Повторна перевірка: `:app:testDebugUnitTest --rerun-tasks` з JDK 21 — `BUILD SUCCESSFUL`; 154 тести, 0 помилок. B0/B1 у `assets` і `docs` побайтово однакові; `git diff --check` без зауважень.

### Повторна незалежна QA 2026-11-22 — Needs changes → виправлено

- Виправлено незавершене українське речення для `fatsia_japonica`.
- Для `ficus_elastica` у чотирьох мовах прибрано кальку зі службового маркера NC State «Problem for Cats/Dogs»; пояснення тепер прямо описує можливу шкоду й окремо називає підтверджену Clemson дію соку.
- Повторний прогін `:app:testDebugUnitTest --rerun-tasks` з JDK 21: `BUILD SUCCESSFUL`, 154 тести, 0 помилок.

### Повна джерельна QA 2026-11-22 — Needs changes → виправлено

- Незалежний QA відкрив усі 109 записи джерел, використані 50 картками; 20 із 21 початково недоступних сторінок знайдено через актуальні офіційні адреси або офіційну індексацію. Clemson Fatsia лишилася недоступною, але вона є джерелом догляду, а не безпеки.
- Із 25 прапорців `NON_TOXIC` поточні офіційні сторінки явно підтвердили 7: коти/собаки для базиліку й розмарину, коти/собаки/діти для пілеї. Для дев’яти карток 18 прапорців не мали явного поточного підтвердження, тому консервативно змінені на `UNKNOWN`: `chlorophytum`, `peperomia`, `calathea`, `phalaenopsis_hybrid`, `hoya_carnosa`, `nephrolepis_exaltata`, `beaucarnea_recurvata`, `aspidistra_elatior`, `cissus_rhombifolia`. Пояснення, `sourceIds` і evidence синхронізовано.
- `fatsia_japonica`: знято `sourcesDisagree`, бо поточна NC State сторінка не містить явного твердження `NON_TOXIC`; рівні `TOXIC` і `FRUIT` лишаються за RHS, єдиним джерелом безпеки.
- `schefflera`: NC State одночасно вказує низьку тяжкість і серйозну токсичність усіх частин для котів і собак. `SEVERE/SEVERE/LOW`, `ALL_PARTS` і `sourcesDisagree` лишаються; пояснення чотирма мовами доповнено ризиком для людей/дітей та всіма частинами.
- Наступний крок: повний прогін тестів і фінальна незалежна QA виправленого HEAD.
- Повний прогін після виправлень: `:app:testDebugUnitTest --rerun-tasks` з JDK 21 — `BUILD SUCCESSFUL`, 154 тести, 0 помилок; копії `assets/docs` однакові, `git diff --check` чистий.
