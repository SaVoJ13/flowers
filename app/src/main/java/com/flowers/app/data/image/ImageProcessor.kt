package com.flowers.app.data.image

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Сервіс безпечної локальної обробки та оптимізації фотографій Flowers 2.0 (TASK-052).
 *
 * Принципи безпеки даних:
 * 1. Захист оригіналу: галерея та будь-які вхідні файли використовуються лише для читання.
 * 2. Заборона перезапису існуючих файлів: існуючі цільові файли ніколи не видаляються і не перезаписуються.
 * 3. Захист від Path Traversal: імена файлів жорстко санітизуються та валідуються.
 * 4. Безпечне збереження: створення файлу відбувається через унікальний тимчасовий файл (.tmp)
 *    з наступним переміщенням без перезапису (Files.move без REPLACE_EXISTING) та синхронізацією шляхів (Mutex).
 * 5. Ізоляція очищення: у разі будь-якого збою видаляється виключно власний тимчасовий файл.
 * 6. Коректне поводження з корутинами: CancellationException ніколи не поглинається.
 */
class ImageProcessor(
    private val context: Context? = null,
    private val outputDirectory: File? = null,
    private val engine: ImageDecoderEngine = AndroidImageDecoderEngine(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val fileMover: (Path, Path) -> Unit = { src, dst -> Files.move(src, dst) }
) {

    companion object {
        // Синхронізація для запобігання гонкам (race condition) при конкурентному збереженні до однакового шляху
        private val pathLocks = ConcurrentHashMap<String, Mutex>()
    }

    /**
     * Валідує та формує безпечне ім'я файлу, усуваючи будь-який ризик Path Traversal.
     */
    fun sanitizeAndValidateFileName(rawName: String?, prefix: String, format: ImageFormat): String {
        if (rawName == null) {
            val randomToken = UUID.randomUUID().toString().replace("-", "").take(12)
            return "${prefix}_${clock()}_${randomToken}.${format.extension}"
        }
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) {
            throw ImageProcessingException.InvalidFileName("Ім'я файлу не може бути порожнім")
        }
        // Захист від path traversal: заборона слешів, зворотних слешів, .. та нульових байтів
        if (trimmed.contains('/') || trimmed.contains('\\') || trimmed.contains("..") || trimmed.contains("\u0000")) {
            throw ImageProcessingException.InvalidFileName("Виявлено спробу path traversal у імені файлу: $trimmed")
        }
        // Дозволяються виключно безпечні символи: латиниця, цифри, крапка, підкреслення, дефіс
        if (!trimmed.matches(Regex("^[a-zA-Z0-9._-]+$"))) {
            throw ImageProcessingException.InvalidFileName("Ім'я файлу містить неприпустимі символи: $trimmed")
        }
        val ext = format.extension
        return if (trimmed.endsWith(".$ext", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed.$ext"
        }
    }

    /**
     * Обробляє зображення з Android Content URI (з камери або галереї).
     */
    suspend fun processImage(
        uri: Uri,
        profile: ImageProfile = ImageProfile.SpeciesIdentification,
        customFileName: String? = null,
        targetDirectory: File? = null
    ): Result<ImageProcessResult> {
        val appContext = context?.applicationContext
            ?: return Result.failure(IllegalStateException("Context необхідний для обробки Android Content URI"))
        val source = ImageSource.ContentUri(uri) { targetUri ->
            appContext.contentResolver.openInputStream(targetUri)
        }
        return process(source, profile, customFileName, targetDirectory)
    }

    /**
     * Обробляє зображення з локального файлу.
     */
    suspend fun processFile(
        file: File,
        profile: ImageProfile = ImageProfile.SpeciesIdentification,
        customFileName: String? = null,
        targetDirectory: File? = null
    ): Result<ImageProcessResult> {
        return process(ImageSource.FromFile(file), profile, customFileName, targetDirectory)
    }

    /**
     * Універсальна обробка джерела зображення з максимальним захистом файлів і пам'яті.
     */
    suspend fun process(
        source: ImageSource,
        profile: ImageProfile = ImageProfile.SpeciesIdentification,
        customFileName: String? = null,
        targetDirectory: File? = null
    ): Result<ImageProcessResult> = withContext(dispatcher) {
        val targetDir = targetDirectory
            ?: outputDirectory
            ?: context?.let { File(it.filesDir, "plants") }
            ?: File(System.getProperty("java.io.tmpdir"), "flowers_plants")

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return@withContext Result.failure(
                ImageProcessingException.IoFailure("Неможливо створити цільову папку: ${targetDir.absolutePath}")
            )
        }

        // 1. Валідація та захист імені файлу від Path Traversal
        val prefix = when (profile) {
            ImageProfile.SpeciesIdentification -> "species"
            ImageProfile.DiseaseDiagnosis -> "diagnosis"
            ImageProfile.ChatConsultation -> "chat"
        }
        val safeFileName = try {
            sanitizeAndValidateFileName(customFileName, prefix, profile.format)
        } catch (e: ImageProcessingException.InvalidFileName) {
            return@withContext Result.failure(e)
        }

        val targetFile = File(targetDir, safeFileName)

        // 2. Захист від збігу джерела та цілі
        if (source is ImageSource.FromFile) {
            val sourceCanonical = runCatching { source.file.canonicalFile.toPath() }.getOrNull()
            val targetCanonical = runCatching { targetFile.canonicalFile.toPath() }.getOrNull()
            if (sourceCanonical != null && sourceCanonical == targetCanonical) {
                return@withContext Result.failure(
                    ImageProcessingException.InvalidImage("Цільовий файл не може збігатися з вхідним файлом-джерелом")
                )
            }
        }

        val targetCanonicalPath = runCatching { targetFile.canonicalFile.absolutePath }.getOrDefault(targetFile.absolutePath)
        val fileMutex = pathLocks.computeIfAbsent(targetCanonicalPath) { Mutex() }

        fileMutex.withLock {
            // 3. Сувора заборона перезапису або видалення існуючих файлів
            if (targetFile.exists()) {
                return@withContext Result.failure(
                    ImageProcessingException.FileAlreadyExists("Цільовий файл уже існує та захищений від перезапису: ${targetFile.name}")
                )
            }

            // 4. Зчитування меж без виділення пам'яті під пікселі
            val originalBounds = try {
                engine.decodeBounds(source::openStream)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                return@withContext Result.failure(
                    ImageProcessingException.IoFailure("Помилка читання джерела зображення: ${t.message}", t)
                )
            } ?: return@withContext Result.failure(
                ImageProcessingException.InvalidImage("Неможливо декодувати розміри зображення: ${source.displayName}")
            )

            if (originalBounds.width <= 0 || originalBounds.height <= 0) {
                return@withContext Result.failure(
                    ImageProcessingException.InvalidImage("Некоректні розміри зображення (${originalBounds.width}x${originalBounds.height})")
                )
            }

            // 5. Зчитування EXIF-орієнтації
            val orientation = try {
                engine.readExifOrientation(source::openStream)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                1 // За замовчуванням normal
            }

            // 6. Розрахунок степеня двійки для inSampleSize та цільових пропорцій
            val inSampleSize = ImageScaleCalculator.calculateInSampleSize(
                rawWidth = originalBounds.width,
                rawHeight = originalBounds.height,
                targetMaxDimension = profile.maxDimension
            )
            val targetDimensions = ImageScaleCalculator.calculateTargetDimensions(
                rawWidth = originalBounds.width,
                rawHeight = originalBounds.height,
                targetMaxDimension = profile.maxDimension,
                orientation = orientation
            )

            // 7. Декодування та геометрична трансформація растру
            val holder = try {
                engine.decodeSampledAndTransform(
                    streamOpener = source::openStream,
                    inSampleSize = inSampleSize,
                    targetDimensions = targetDimensions,
                    orientation = orientation
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                return@withContext Result.failure(
                    ImageProcessingException.OutOfMemory("Недостатньо оперативної пам'яті для декодування зображення", e)
                )
            } catch (t: Throwable) {
                return@withContext Result.failure(
                    ImageProcessingException.InvalidImage("Збій обробки растру зображення: ${t.message}", t)
                )
            } ?: return@withContext Result.failure(
                ImageProcessingException.InvalidImage("Не вдалося декодувати зображення (пошкоджені дані)")
            )

            // 8. Безпечне збереження: запис у тимчасовий файл і переміщення без перезапису
            val tempToken = UUID.randomUUID().toString().replace("-", "").take(8)
            val tempFile = File(targetDir, "${safeFileName}.tmp_$tempToken")

            var processedWidth = holder.width
            var processedHeight = holder.height

            try {
                holder.use { bitmapHolder ->
                    processedWidth = bitmapHolder.width
                    processedHeight = bitmapHolder.height

                    val compressSuccess = engine.compressToFile(
                        bitmapHolder = bitmapHolder,
                        format = profile.format,
                        quality = profile.quality,
                        destinationFile = tempFile
                    )

                    if (!compressSuccess || !tempFile.exists() || tempFile.length() == 0L) {
                        return@withContext Result.failure(
                            ImageProcessingException.SaveFailed("Не вдалося записати стиснене зображення у тимчасовий файл")
                        )
                    }

                    // Перевірка цілісності записаного тимчасового файла перед атомарним переміщенням
                    val verifiedBounds = runCatching { engine.decodeBounds { tempFile.inputStream() } }.getOrNull()
                    if (verifiedBounds == null || verifiedBounds.width <= 0 || verifiedBounds.height <= 0) {
                        return@withContext Result.failure(
                            ImageProcessingException.SaveFailed("Записаний тимчасовий файл пошкоджений або неповний")
                        )
                    }
                }

                // Захист від перезаписування: перевірка перед переміщенням
                if (targetFile.exists()) {
                    return@withContext Result.failure(
                        ImageProcessingException.FileAlreadyExists("Цільовий файл уже існує: ${targetFile.name}")
                    )
                }

                // Стандартний Files.move без REPLACE_EXISTING гарантує викидання FileAlreadyExistsException
                // у разі наявності цільового файлу. StandardCopyOption.ATOMIC_MOVE не використовується,
                // оскільки на POSIX/Linux (зокрема Android) він безумовно замінює цільовий файл через rename(2).
                try {
                    fileMover(tempFile.toPath(), targetFile.toPath())
                } catch (e: FileAlreadyExistsException) {
                    return@withContext Result.failure(
                        ImageProcessingException.FileAlreadyExists("Цільовий файл уже існує: ${targetFile.name}")
                    )
                } catch (e: AtomicMoveNotSupportedException) {
                    return@withContext Result.failure(
                        ImageProcessingException.AtomicMoveNotSupported(
                            "Атомарне переміщення файлу не підтримується файловою системою: ${e.message}",
                            e
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                return@withContext Result.failure(
                    ImageProcessingException.OutOfMemory("Недостатньо пам'яті під час компресії зображення", e)
                )
            } catch (e: IOException) {
                return@withContext Result.failure(
                    ImageProcessingException.SaveFailed("Помилка файлової системи під час збереження: ${e.message}", e)
                )
            } catch (t: Throwable) {
                return@withContext Result.failure(
                    ImageProcessingException.SaveFailed("Збій під час збереження файлу: ${t.message}", t)
                )
            } finally {
                // Видаляємо ВИКЛЮЧНО власний тимчасовий файл; цільовий файл ніколи не видаляється
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }

            Result.success(
                ImageProcessResult(
                    file = targetFile,
                    originalDimensions = originalBounds,
                    processedDimensions = ImageDimensions(processedWidth, processedHeight),
                    inSampleSize = inSampleSize,
                    byteSize = targetFile.length(),
                    mimeType = profile.format.mimeType
                )
            )
        }
    }
}
