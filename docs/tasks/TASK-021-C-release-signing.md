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

### Незалежна QA 2026-11-22 — Needs changes → виправлено

- Офіційна інструкція Android вимагає однаковий пароль для сховища й ключа через відому проблему інструментів підпису. Інструкцію та `keystore.properties.example` виправлено: Власник обирає один пароль і записує його в обидва поля.
- `keystore.properties` тепер читається через UTF-8 `Reader`, тому кириличний шлях не спотворюється. Інструкція вимагає прямі риски `/` у шляху та пояснює екранування `\` як `\\` у паролі.

### Повторна незалежна QA 2026-11-22 — QA Verified

- Інша модель (`gpt-6-luna`) перевірила diff після виправлень: проблем у Gradle signing config, UTF-8 читанні properties, захисті секретів та інструкції не знайдено.
- Після припинення паралельних Gradle-процесів виконано чистий послідовний прогін на JDK 21: `clean :app:testDebugUnitTest :app:bundleRelease --no-parallel` — `BUILD SUCCESSFUL`; 154/154 тести, 0 failures/errors/skipped.
- Технічний AAB без `keystore.properties` очікувано непідписаний (`jar is unsigned`). У чотирьох dex немає `DebugAppCheckProviderFactory`, є `PlayIntegrityAppCheckProviderFactory`; шаблон реального Google API-ключа у відстежуваних файлах не знайдено.
- Зупинився на: потрібен локальний upload-ключ Власника і `keystore.properties` за `docs/release/UPLOAD_KEY_GUIDE.uk.md`.
- Наступний крок: після повідомлення Власника «upload-ключ створено, `keystore.properties` заповнено» повторно зібрати AAB і підтвердити `jar verified`, не читаючи й не показуючи секрети.

### Підписаний AAB 2026-11-22

- Власник повідомив, що upload-ключ і локальний `keystore.properties` створено; агент не читав і не показував їхній вміст.
- `:app:bundleRelease --rerun-tasks --no-parallel` на JDK 21 — `BUILD SUCCESSFUL`; `validateSigningRelease` і `signReleaseBundle` виконано.
- `jarsigner -verify` завершився з кодом 0 і результатом `jar verified`.
- AAB: `app/build/outputs/bundle/release/app-release.aab`, 17 461 972 bytes, SHA-256 `9D9EDD8B752747A5A04993B84CE4D6A1BB773FCC947EE882FEB1A804FAEC322D`.
- `keystore.properties` підтверджено як ignored; AAB із каталогу Android Studio `app/release/` також виключено з Git. Ключ, паролі й `keystore.properties` не комітяться.
- TASK-021-C технічно виконано; PR #29 не зливається, доки не вирішено долю незакомічених автоматичних оновлень Android Studio (AGP 8.7.3 → 8.13.2, Gradle 8.12.1 → 8.13, Foojay resolver і daemon JVM properties).

### Фінальна збірка на затвердженому інструментарії 2026-11-22

- Власник підтвердив, що оновлення Android Studio було ненавмисним. Три tracked-файли відновлено до стану гілки; згенерований `gradle/gradle-daemon-jvm.properties` видалено. Ключ, `keystore.properties` і AAB не чіпали.
- На затверджених AGP 8.7.3 / Gradle 8.12.1 повторено `:app:bundleRelease --rerun-tasks --no-parallel`: `BUILD SUCCESSFUL`; `jarsigner -verify` = код 0, `jar verified`.
- **Канонічний фінальний AAB:** `app/build/outputs/bundle/release/app-release.aab`, 17 510 432 bytes, SHA-256 `69A09BCAF6DB87608D1FEC9E7F900F940628AE025FAFF073C210BACDC56DC375`. Цей хеш замінює попередній, отриманий ненавмисно оновленим інструментарієм; попередній запис збережено як історію перевірки.
- Конфігурація AGP/Gradle/settings повернута до HEAD; поза цими append-only доповненнями ненавмисних змін немає. TASK-021-C готове до повторної QA-звірки й злиття PR #29.
