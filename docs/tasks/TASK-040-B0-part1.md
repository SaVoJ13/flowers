# TASK-040-B0, частина 1: 20 наявних рослин у новому форматі (реліз 1.1, хвиля 0)

Рішення Власника (автономний режим 2026-11-20): 20 наявних ID переписуються без погодження списку; 5 нових рослин B0 НЕ додано (чекають погодження списку, PR #15). Дані ще не підключені до застосунку (імпортер TASK-037 не існує).

## Конвеєр (ADR-017)
1. Збір фактів: 4 субагенти (Sonnet), по 5 рослин, ≥2 прочитані джерела догляду (NC State Extension Plant Toolbox, Clemson HGIC, Penn State, Iowa State, UW-Madison, Illinois, RHS), безпека лише з не-ASPCA джерел. Обмеження: інструмент веб-читання повертає стислі зведення, тому уривки (`drafts/b00.excerpts.json`) НЕ гарантовано дослівні, частина коротша за 80 символів; перевірка оригінальності на них слабша.
2. Написання: 4 субагенти (Sonnet), uk/en/ru/pl окремо за фактами. Частина псевдонімів (aliases) і англомовних назв узята авторами із загальних знань, не з фактів (напр. «Baby rubber plant», «Devil's ivy», «Lilia pokoju», «Ten commandments plant»): QA перевіряє.
3. Складання скриптом: `app/src/main/assets/catalog/{b00,sources,manifest}.json` (+ копії в `docs/content/catalog/`), `contentVersion: 1`.
4. Валідатор + `OriginalityValidator`: 54/54 тести (включно з файлами в assets).

## Рішення щодо безпеки (початкові; ЗАСТАРІЛО в частині sansevieria/dracaena_marginata, див. розділ QA нижче: після QA обидві TOXIC)
- `dracaena_marginata`: pets/children = UNKNOWN (джерело NC State описує *D. reflexa* var. *angustifolia*).
- `sansevieria`, `maranta`: UNKNOWN (джерела не дали явного твердження).
- `NON_TOXIC` (небезпечніший напрямок помилки) лише для `chlorophytum`, `peperomia`, `calathea` і лише для котів/собак із тегів NC State (діти UNKNOWN): QA перевіряє 100 % прапорців.
- Для дітей TOXIC лише там, де джерела кажуть про людей/дітей; решта UNKNOWN.

## Чекає
Незалежна QA іншою моделлю: вибірка ≥ 30 % карток за URL, 100 % прапорців безпеки, оригінальність, природність ru/pl. Вибірка Власника: 3–5 карток; польську перевіряє носій (Q-1.1-5). `nativeReviewed` усюди `false`.

## QA (Opus) → Needs changes → виправлено
Перевірено: 42 з 44 URL відкриваються; догляд 10 з 20 карток (50 %); 100 % прапорців безпеки (40); 11 карток на 4 мовах повністю. Виправлено: `sansevieria` і `dracaena_marginata` → TOXIC (джерела підтверджують, моє «UNKNOWN» було хибним послабленням), `syngonium` children → TOXIC, `syngonium` lightMin → MEDIUM, `begonia` humidity → HIGH; прибрано кальки й оманливі псевдоніми (monstera, spathiphyllum, crassula, schefflera, syngonium, aglaonema, sansevieria, epipremnum, begonia), виправлено польські назви (*Grubosz jajowaty*, *Zroślicha*, *Epipremnum złociste*), мовні правки (русизми, «кліщі та комахи-шкідники», «раз на два місяці»), прибрано непідтверджені джерелами твердження (торф, випалювання добривом).
Лишилося (низька серйозність, прийнято): англійські фрази begonia/monstera близькі до RHS/NC State за змістом (валідатор: < 8 слів поспіль), деякі твердження про пересадку (aloe, peperomia) не знайдено у прочитаних сторінках, дублікати й зведення в excerpts.
