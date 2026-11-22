# TASK-021-C — релізний AAB і локальна конфігурація підпису

## Мета

Підготувати підпис release AAB upload-ключем Власника без зберігання ключа або паролів у Git чи чаті.

## Реалізація

- `app/build.gradle.kts` читає `keystore.properties` лише якщо файл існує. За наявності файлу всі чотири значення обов'язкові й release автоматично використовує signing config.
- `.gitignore` виключає `keystore.properties`, `*.jks`, `*.keystore`.
- `keystore.properties.example` містить лише заповнювачі.
- Інструкція Власнику: `docs/release/UPLOAD_KEY_GUIDE.uk.md`.
- Версія релізу вже правильна: `versionCode=1`, `versionName=1.0.0`.

## Критерії перевірки

1. Без локального `keystore.properties` тести й `bundleRelease` збираються; AAB вважається лише технічним і не завантажується.
2. Із локальним файлом Власника AAB підписаний; `jarsigner -verify` завершується `jar verified`.
3. У release AAB немає `DebugAppCheckProviderFactory`; release використовує Play Integrity.
4. У відстежуваних файлах немає API-ключів, ключа підпису або паролів.
5. Незалежна QA іншою моделлю; як видима/релізна зміна, остаточне приймання після перевірки збірки з Play Власником.

## Перевірка 2026-11-22

- JDK 21: `:app:testDebugUnitTest :app:bundleRelease --rerun-tasks` — `BUILD SUCCESSFUL`, 154 тести, 0 помилок; release AAB створено.
- Без `keystore.properties` `jarsigner` повідомляє `jar is unsigned`, як і очікується. Цей технічний AAB не завантажується в Play Console.
- Перевірено всі чотири `classes*.dex` у release AAB: `DebugAppCheckProviderFactory` відсутній; `PlayIntegrityAppCheckProviderFactory` присутній.
- Пошук шаблону реального Google API-ключа `git grep -nP 'AIza[0-9A-Za-z_-]{30,}'` у відстежуваних файлах порожній. Простий пошук слова `AIza` непридатний, бо документація сама згадує цей маркер. Ключі й паролі не створювались.
- Наступний крок: незалежна QA гілки; після неї Власник створює upload-ключ і локальний `keystore.properties`, агент повторно збирає та перевіряє підписаний AAB.
