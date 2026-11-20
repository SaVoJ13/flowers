# TASK-040: партія B0 ч.2 (5 нових) і партія B1 (25) (реліз 1.1, хвиля 0)

Рішення Власника: ADR-019 (список погоджено, автономне виконання B0 ч.2 і B1). Дані ще не підключені до застосунку (імпортер TASK-037 не існує); `contentVersion: 2`, `batches: [b00, b01]`.

## Конвеєр (ADR-017)
Збір фактів: 6 субагентів (Sonnet), ≥2 прочитані джерела на рослину (NC State, Clemson, RHS, Iowa State, UW-Madison, UConn, Illinois, UMN тощо), безпека лише з не-ASPCA джерел. Написання: 6 субагентів (Sonnet), 4 мови окремо. Складання скриптом, валідатор і `OriginalityValidator` (перша перевірка знайшла 2 збіги ≥ 8 слів: `phalaenopsis_hybrid` en light, `philodendron_bipinnatifidum` en light, переписано). Уривки джерел НЕ гарантовано дослівні (веб-інструмент повертає зведення). Усе в `docs/content/catalog/` і `app/src/main/assets/catalog/`.

## Безпека (прапорці з джерел)
Тварини: TOXIC / NON_TOXIC / UNKNOWN за явними твердженнями; `NON_TOXIC` лише з тегів NC State для: ocimum_basilicum, salvia_rosmarinus, cissus_rhombifolia (сторінка про синонім), fatsia_japonica, phalaenopsis_hybrid (рівень роду), hoya_carnosa, nephrolepis_exaltata, beaucarnea_recurvata, aspidistra_elatior; `pilea_peperomioides` NON_TOXIC/NON_TOXIC за прозовим твердженням NC State. `citrus_limon`, `mentha`, `cyperus_alternifolius`, `rhaphidophora_tetrasperma` UNKNOWN. Діти TOXIC лише з тверджень про людей/дітей. QA перевіряє 100 % прапорців.

## Чекає
Незалежна QA (Opus): вибірка ≥ 30 % карток за URL, 100 % прапорців безпеки, оригінальність, мови (русизми, калька, aliases зі знань субагентів). Вибірка Власника (3–5 карток uk/ru) і носій pl: пізніше; `nativeReviewed` усюди `false`.
