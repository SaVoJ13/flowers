# Звірка Data safety з посібником Firebase (INBOX-010 п.7, рішення Власника 2026-11-20)

> Джерело: https://firebase.google.com/docs/android/play-data-disclosure і визначення Google Play: https://support.google.com/googleplay/android-developer/answer/10787469. Сторінки прочитано субагентом через WebFetch (текст пропущено через малу модель, цитати можуть бути скорочені): **перед відправкою форми Власник звіряє ключові цитати зі сторінкою**. Форму Data safety заповнює Власник.

## Що збирає Firebase автоматично (за посібником)
| SDK у застосунку | Автоматично збирає | Мета (за посібником) |
|---|---|---|
| Firebase AI Logic (`firebase-ai`) | назва моделі, версія SDK, версія Kotlin runtime, Firebase App ID і версія застосунку (якщо збір увімкнено); User ID лише якщо підключено Firebase Auth (у нас немає); промпти й відповіді лише якщо ввімкнено AI monitoring (у нас ні) | виклик бекенд-сервісу у відповідь на запити застосунку |
| App Check (core, Play Integrity; debug лише в debug-збірках) | Firebase user agent; токен Play Integrity (при цьому провайдері) | валідація атестації застосунку, захист бекенду від зловживань |
| Cloud Firestore | Firebase user agent (User ID лише з Auth, якого немає); дані, які користувач зберігає (скарги), на відповідальності розробника | робота бази |
| Firebase Installations | FID: ідентифікатор встановлення, що «не ідентифікує однозначно користувача чи фізичний пристрій» | ідентифікація встановлень |
| Спільне, транзитивне | Firebase user agent (версія ОС, назва/модель/бренд пристрою, form factor, джерело встановлення, список SDK); Firebase sessions SDK: package name, ОС, версія SDK, тип мережі, виробник/модель пристрою (підтягується транзитивно: **у нас перевірити фактичну наявність у залежностях**) | сервісні |
Посібник не позначає дані як «required/optional» і не розділяє «collected/shared» по типах; про Location для цих SDK нічого немає; окремого розділу «Firebase Common/Core» немає.

## Визначення Google Play
«Collect» = передача даних із пристрою. «Sharing» = передача зібраних даних третій стороні. Передача **постачальнику послуг, що обробляє дані від імені розробника, не є sharing**, але лишається **collected**. Інші винятки з sharing: юридичні вимоги, дія, ініційована користувачем, помітне розкриття із згодою, повністю анонімізовані дані.

## Висновки для DATA_SAFETY.md (пропозиція; рішення Власника)
1. **Device or other IDs: збирається = Так** (Firebase Installation ID, App ID, токен Play Integrity; user agent з моделлю пристрою). Мета: App functionality, Fraud prevention/security. Не пов'язується з особою. Shared: **Ні** (за посібником Firebase не передає дані третім сторонам, окрім субпідрядників і за вказівкою розробника; за визначенням Google Play така передача не є sharing); обов'язково: ні, збирається лише в необов'язкових функціях ШІ та скарги.
2. **Назва рослини → Gemini (безкоштовний рівень):** лишаємо **shared = Так** за рішенням Власника O-Q4 (Google на безкоштовному рівні може використовувати запити для покращення продуктів і люди можуть їх читати, тобто це не лише обробка від імені розробника).
3. **Скарги у Firestore:** collected = Так (Other user-generated content), shared = Ні (власна база, Firebase як постачальник послуг), мета App functionality.
4. **App info and performance:** окремо «Diagnostics» не декларуємо за відсутності власних SDK; якщо Власник вважає user agent/sessions діагностикою, декларувати як collected для App functionality. **Не підтверджено**, що Play вимагає цього; обережний варіант: декларувати.
5. Перевірено 2026-11-20 (`./gradlew :app:dependencies --configuration releaseRuntimeClasspath`): `firebase-sessions` у залежностях **відсутній**; у релізному classpath: firebase-ai 18.2.0, firebase-appcheck 20.x, firebase-appcheck-playintegrity 20.1.0, firebase-firestore 27.2.0, firebase-common, firebase-components (окремий `firebase-installations` у виводі не знайдено; FID у залежностях прямо не підтверджено; **FID розкрито свідомо як обережне розкриття** (Firebase SDK створюють ідентифікатор встановлення; зайве розкриття не порушує правил Play)). Отже пункт про sessions SDK не застосовується.
