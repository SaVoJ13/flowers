# THIRD PARTY COMPONENTS & LICENCES (Реєстр сторонніх компонентів)

> Відповідно до розділу 3.1 та 6 Конституції, будь-яка бібліотека чи компонент додається лише після перевірки першоджерела, підтвердження сумісності ліцензії та внесення до цього реєстру.

| Назва компонента / Library | Ліцензія / Licence | Першоджерело / Source | Призначення / Purpose | Статус перевірки |
|---|---|---|---|---|
| **Kotlin Standard Library (`org.jetbrains.kotlin:kotlin-stdlib`)** | Apache-2.0 | [github.com/JetBrains/kotlin](https://github.com/JetBrains/kotlin) | Базова мовна бібліотека Kotlin | ✅ Перевірено |
| **AndroidX Core KTX (`androidx.core:core-ktx`)** | Apache-2.0 | [android.googlesource.com](https://android.googlesource.com/platform/frameworks/support) | Системні розширення Android | ✅ Перевірено |
| **Jetpack Compose UI & Material 3 (`androidx.compose.material3:material3`)** | Apache-2.0 | [android.googlesource.com](https://android.googlesource.com/platform/frameworks/support) | Декларативний інтерфейс Material Design 3 | ✅ Перевірено |
| **Compose Navigation (`androidx.navigation:navigation-compose`)** | Apache-2.0 | [android.googlesource.com](https://android.googlesource.com/platform/frameworks/support) | Маршрутизація та навігація між екранами | ✅ Перевірено |
| **Room Database (`androidx.room:room-runtime`, `room-ktx`)** | Apache-2.0 | [android.googlesource.com](https://android.googlesource.com/platform/frameworks/support) | Локальна SQLite-база даних | ✅ Перевірено |
| **WorkManager (`androidx.work:work-runtime-ktx`)** | Apache-2.0 | [android.googlesource.com](https://android.googlesource.com/platform/frameworks/support) | Фонове планування локальних сповіщень | ✅ Перевірено |
| **Kotlinx Coroutines (`org.jetbrains.kotlinx:kotlinx-coroutines-android`)** | Apache-2.0 | [github.com/Kotlin/kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Асинхронне виконання та потік даних | ✅ Перевірено |
| **Coil Compose (`io.coil-kt:coil-compose`)** | Apache-2.0 | [github.com/coil-kt/coil](https://github.com/coil-kt/coil) | Локальне завантаження та кешування фото | ✅ Перевірено |
| **Gson (`com.google.code.gson:gson`)** | Apache-2.0 | [github.com/google/gson](https://github.com/google/gson) | Локальний парсинг JSON-каталогів з assets | ✅ Перевірено |
| **JUnit 4 (`junit:junit`)** | EPL-1.0 | [github.com/junit-team/junit4](https://github.com/junit-team/junit4) | Модульне юніт-тестування (test scope only) | ✅ Перевірено |
| **Firebase Android BoM (`com.google.firebase:firebase-bom`)** | Apache-2.0 | [github.com/firebase/firebase-android-sdk](https://github.com/firebase/firebase-android-sdk) | Управління узгодженими версіями бібліотек Firebase | ✅ Перевірено |
| **Firebase AI Logic (`com.google.firebase:firebase-ai`)** | Apache-2.0 | [github.com/firebase/firebase-android-sdk](https://github.com/firebase/firebase-android-sdk) | SDK генерації вмісту через Gemini Developer API | ✅ Перевірено |
| **Cloud Firestore (`com.google.firebase:firebase-firestore`)** | Apache-2.0 | [github.com/firebase/firebase-android-sdk](https://github.com/firebase/firebase-android-sdk) | Запис скарг на відповіді ШІ (лише create, TASK-027; схвалено Власником, O-Q3/ADR-014) | ✅ Перевірено (версія з BoM) |
| **Firebase App Check Play Integrity (`com.google.firebase:firebase-appcheck-playintegrity`)** | Apache-2.0 | [github.com/firebase/firebase-android-sdk](https://github.com/firebase/firebase-android-sdk) | Захист викликів ШІ через атестацію пристрою Google Play Integrity | ✅ Перевірено |
| **Firebase App Check Debug (`com.google.firebase:firebase-appcheck-debug`)** | Apache-2.0 | [github.com/firebase/firebase-android-sdk](https://github.com/firebase/firebase-android-sdk) | Тестування App Check у debug-збірках (debugImplementation) | ✅ Перевірено |
| **Google Services Plugin (`com.google.gms:google-services`)** | Apache-2.0 | [developers.google.com](https://developers.google.com/android/guides/google-services-plugin) | Плагін обробки google-services.json під час збірки | ✅ Перевірено |
| **SQLite JDBC (`org.xerial:sqlite-jdbc`)** | Apache-2.0 | [github.com/xerial/sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) | Тест міграції Room на JVM (test scope only, у застосунок не потрапляє) | ✅ Ліцензію перевірено в POM |
