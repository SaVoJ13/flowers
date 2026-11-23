# TASK-052 — Локальний безпечний фотопроцесор (ImageProcessor)

## Мета

Створити надійний, безпечний сервіс обробки та підготовки фотографій перед відправкою до ШІ та збереженням у картці рослини в межах переходу на Flowers 2.0 (ADR-021, FLOWERS_2_0_MVP_PLAN.md).

## Вимоги та реалізація

1. **Читання без надмірного декодування в RAM:**
   - Двоетапне читання: спочатку `inJustDecodeBounds = true` для отримання розмірів без виділення пам'яті під пікселі.
   - Динамічний розрахунок `inSampleSize` як степеня двійки (`1, 2, 4, 8, 16...`) через `ImageScaleCalculator`.
   - Зменшення пікового споживання RAM для великих сенсорів (12 МП ~75% економії, 50 МП >98% економії, 200 МП >99.6% економії пам'яті).
   - Розділено теоретичні оцінки сирого растру від фактичного пікового сліду (`estimateMemoryFootprint`), який враховує проміжний растр `Bitmap.createBitmap` при роботі `Matrix` та буфери стиснення.
2. **Корекція всіх 8 EXIF-орієнтацій:**
   - Зчитування орієнтації через `androidx.exifinterface.media.ExifInterface`.
   - Застосування поворотів (0°, 90°, 180°, 270°) та дзеркальних відображень (горизонтальне, вертикальне, транспонування, поперечне).
   - Точний перерахунок цільових розмірів при зміні осей (орієнтації 5, 6, 7, 8).
   - Верифіковано на піксельному рівні (Pixel-level color coordinates) в інструментальних тестах.
3. **Профілі оптимізації:**
   - `SpeciesIdentification`: макс. 1024 px по довшій стороні, якість 80%, JPEG.
   - `DiseaseDiagnosis`: макс. 1536 px по довшій стороні, якість 85%, JPEG.
   - Збереження пропорцій (aspect ratio); відсутність штучного збільшення малих фото.
4. **Видалення GPS та приватних метаданих:**
   - Нове кодування растру через `compress` створює чистий JPEG без вихідних блоків EXIF APP1 (GPS-координати, серійні номери, модель пристрою повністю вилучено).
5. **Захист фотографій та безпечне збереження:**
   - Оригінал у галереї використовується виключно в режимі читання вхідного потоку і ніколи не модифікується.
   - Сувора заборона видалення або перезаписування існуючих цільових файлів (`FileAlreadyExists`).
   - Повний захист від Path Traversal: метод `sanitizeAndValidateFileName` блокує `..`, `/`, `\`, null-байти та дозволяє лише безпечні символи `[a-zA-Z0-9._-]`.
   - Заборона колізії джерела та цілі (`source == target`).
   - Внутрішньопроцесна синхронізація через `ConcurrentHashMap<String, Mutex>` гарантує атомарну перевірку та збереження між конкурентними корутинами.
   - `StandardCopyOption.ATOMIC_MOVE` свідомо вилучено: на POSIX/Linux (зокрема Android bionic) він безумовно замінює цільовий файл через `rename(2)` навіть без `REPLACE_EXISTING`.
   - Переміщення файлу виконується через `Files.move` без прапорця `REPLACE_EXISTING`, що за стандартом Java NIO гарантує викидання `FileAlreadyExistsException`.
   - При збоях видаляється виключно власний тимчасовий `.tmp` файл; цільовий файл ніколи не видаляється.
6. **Надійність і корутини:**
   - `CancellationException` ніколи не поглинається і завжди ретранслюється вгору.
   - Специфічний перехоплення `OutOfMemoryError` зі звільненням виділених Bitmap.
   - Глобальний `unitTests.isReturnDefaultValues = true` видалено з конфігурації Gradle, щоб уникнути маскування викликів Android SDK. Для `ExifInterface` створено локальний заглушковий `Log.java` у test scope.

## Залежності

- `androidx.exifinterface:exifinterface` (1.3.7, Apache-2.0) — зафіксовано в `libs.versions.toml`, `app/build.gradle.kts` та `docs/THIRD_PARTY.md`.

## Результати тестів

- `ImageScaleCalculatorTest`: 6 тестів (степені двійки для 12/50/200 МП, оцінка пікової RAM з урахуванням проміжних Bitmap, збереження пропорцій, 8 орієнтацій).
- `ImageProcessorTest`: 15 тестів (Species profile, Disease profile, subsampling 12/50/200 МП, поворот 90°, незмінність оригіналу, видалення GPS, Path Traversal захист, захист від перезапису існуючих файлів, захист source == target, очищення тимчасових файлів, поширення CancellationException, конкурентні паралельні записи з єдиним переможцем, захист від дублювання імен, конкурентні автозгенеровані імена).
- `AndroidImageProcessorTest`: 5 інструментальних тестів виконано на ізольованому емуляторі `small_phone` (AVD API 36, Android 16.0 x86_64, `emulator-5554`) — 5/5 PASSED:
   1. `real_android_decoding_and_scaling_preserves_aspect_ratio`: passed (5.441s)
   2. `real_android_exif_orientations_verified_by_actual_pixel_coordinates`: passed (3.680s, усі 8 EXIF-орієнтацій)
   3. `real_android_existing_file_is_never_overwritten`: passed (1.298s, перевірка захисту на реальній ФС Android)
   4. `real_android_gps_metadata_completely_stripped`: passed (1.668s)
   5. `real_android_original_file_strictly_unchanged`: passed (0.391s)
- Усі 178 юніт-тестів проєкту зелені (`testDebugUnitTest` PASS).
- `assembleDebugAndroidTest` збирає тестовий APK успішно.
- `bundleRelease` на Adoptium JDK 21 збирається успішно (`BUILD SUCCESSFUL`).
