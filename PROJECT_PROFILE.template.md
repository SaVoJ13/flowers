# PROJECT PROFILE / ПРОФІЛЬ ПРОЄКТУ

> Fills the 🔧 parameters of the constitution (`CONSTITUTION.en.md` / `CONSTITUTION.uk.md`). May only fill in and **tighten** rules, never loosen them.
> Заповнює 🔧-параметри конституції. Може лише заповнювати й **посилювати** правила, а не послаблювати.
> Version / Версія: 1.0 · Date / Дата: <YYYY-MM-DD> · Constitution / Конституція: 2.0

## How to adopt in a new project / Як підключити до нового проєкту
1. Copy `CONSTITUTION.uk.md`, `CONSTITUTION.en.md` and this file into `docs/governance/` (or another folder; record the path in section 9).
   Скопіюйте їх у `docs/governance/` (або іншу папку; запишіть шлях у розділі 9).
2. Fill in every section below. Replace each `<…>` with a real value; a parameter that does not apply is marked "n/a" with a reason.
   Заповніть кожен розділ. Кожне `<…>` замініть реальним значенням; неактуальний параметр позначте «н/д» з причиною.
3. In `CLAUDE.md` add: "Read the constitution and PROJECT_PROFILE.md at the start of every task" (with the file paths).
   У `CLAUDE.md` додайте: «Читай конституцію й PROJECT_PROFILE.md на початку кожного завдання» (зі шляхами).
4. Create the memory files from section 5.1 of the constitution (empty, with headers).
   Створіть файли пам'яті з розділу 5.1 конституції (порожні, із заголовками).
5. Record the adoption in the decision log (decision number and date).
   Запишіть підключення в журнал рішень (номер рішення й дата).

---

## 1. Project / Проєкт
| Field / Поле | Value / Значення |
|---|---|
| Name / Назва | <…> |
| Short description / Короткий опис | <…> |
| Project licence / Ліцензія проєкту | <e.g. GPL-3.0-or-later, MIT, proprietary> |
| Main branch / Головна гілка | <main> |
| Repository / Репозиторій | <URL> |

## 2. Owner / Власник
| Field / Поле | Value / Значення |
|---|---|
| Time for the project / Час на проєкт | <e.g. 3–4 hours a day> |
| Technical level / Технічний рівень | <e.g. not a programmer> |
| Language of reports and chat / Мова звітів і спілкування | <Ukrainian / English / both> |
| Language of code, comments, commits / Мова коду, коментарів, комітів | <…> |
| Time zone / Часовий пояс | <…> |

## 3. Autonomy level (constitution 3.4) / Рівень автономії
- Level / Рівень: <default 3.1: agents merge after `QA Verified` + green CI | the Owner merges | the Owner approves every PR | the Owner approves releases only>
- Who may give `QA Verified` / Хто може ставити `QA Verified`: <QA agent(s) / the Owner>

## 4. Roles and areas / Ролі й зони
| Role / Роль | Folder / Папка | May touch / Може змінювати | May not touch / Не чіпає |
|---|---|---|---|
| Agent 0 (lead / керівник) | <whole repo> | <…> | <…> |
| <role> | <path> | <…> | <…> |
| QA | <tests/> | <tests only> | <product code> |

## 5. Technology and environment / Технології й середовище
- Stack / Стек: <…>
- Platforms / Платформи: <…>
- Owner's computer / Комп'ютер Власника: <OS, shell, limits (memory, protection)>
- Devices for testing / Пристрої для перевірки: <…>
- Enabled modules / Підключені модулі: <Android/Google Play, Unity, monetisation, GPL project, …>

## 6. Licences and third-party components / Ліцензії й сторонні компоненти
- Register file / Файл реєстру: <docs/THIRD_PARTY.md>
- Accepted licences / Прийнятні ліцензії: <…>
- Conditional (after separate check) / Умовно (після окремої перевірки): <…>
- Forbidden / Заборонені: <e.g. NC, ND, "personal use only", no licence>
- Rule of verification / Правило перевірки: <check against the author's original source; record in the register>

## 7. Owner-only list (constitution 3.2) / Лише Власник
- Accounts and consoles / Акаунти й консолі: <e.g. Google Play Console, AdMob, Firebase, repo secrets, payment profile>
- Paid services / Платні сервіси: <none unless approved>
- Publishing channels (beyond internal testing) / Канали публікації: <…>
- Approved in advance (not requiring a new request) / Затверджено наперед (не потребує нового запиту): <e.g. ads in the placements from MONETIZATION_STRATEGY; or "nothing">
- Phases and gates / Етапи й ворота: <none | list of phases and the gate definition>

## 8. Reports and commands / Звіти й команди
| Purpose / Призначення | Command or file / Команда або файл |
|---|---|
| One Owner action / Одна дія Власника | <`/owner-step` or equivalent> |
| Period report / Звіт за період | <`/daily-report` or equivalent> |
| Phase gate / Ворота етапу | <`/phase-gate` or n/a> |
| Session close / Закриття сесії | <`/close-session` or equivalent> |
| Report template / Шаблон звіту | <docs/templates/OWNER_REPORT.md> |

## 9. Paths and checks / Шляхи й перевірки
| What / Що | Path / Шлях |
|---|---|
| Constitution and profile / Конституція й профіль | <docs/governance/> |
| Decision log / Журнал рішень | <docs/governance/DECISIONS.md> |
| Current state, task board, session log, lessons, owner inbox | <docs/governance/…> |
| Task cards / Картки завдань | <docs/tasks/> |
| Product and plan documents / Документи продукту й плану | <concept, roadmap, strategy: paths> |
| Definition of Done | <docs/governance/DEFINITION_OF_DONE.md> |
| Agent working rules / Робочі правила агентів | <AGENTS.md or equivalent> |
| Key terms for the overwrite check (constitution 5.5) / Ключові терміни для перевірки перезапису | <list of unique terms and IDs that must never disappear from the docs> |
| Required CI checks / Обов'язкові перевірки CI | <build, tests, lint, …> |

## 10. Project-specific tightening and exceptions / Посилення й винятки проєкту
- <Any extra rule that tightens the constitution, e.g. "no INTERNET permission in the app", "never push from agents". Loosening is not allowed.>
- <Будь-яке додаткове правило, що посилює конституцію, напр. «жодного INTERNET у застосунку», «агенти ніколи не пушать». Послаблювати не можна.>
