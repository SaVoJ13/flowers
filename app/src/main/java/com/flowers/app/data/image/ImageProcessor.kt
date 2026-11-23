package com.flowers.app.data.image

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Сервіс безпечної локальної обробки та оптимізації фотографій Flowers 2.0 (TASK-052).
 *
 * Особливості:
 * 1. Двоетапне читання без завантаження повного растру в пам'ять (inJustDecodeBounds + inSampleSize).
 * 2. Автоматична корекція всіх 8 EXIF-орієнтацій (поворот і віддзеркалення).
 * 3. 100% стирання GPS та інших приватних метаданих при новому кодуванні пікселів.
 * 4. Захист оригіналу: галерея лише читається, оптимізована копія зберігається у `filesDir/plants/`.
 * 5. Атомарне збереження через тимчасові файли без ризику залишити пошкоджені або неповні файли.
 * 6. Захист від OutOfMemoryError та некоректних вхідних даних.
 */
class ImageProcessor(
    private val context: Context? = null,
    private val outputDirectory: File? = null,
    private val engine: ImageDecoderEngine = AndroidImageDecoderEngine(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Обробляє зображення з Android Content URI (з камери або галереї).
     */
    suspend fun processImage(
        uri: Uri,
        profile: ImageProfile = ImageProfile.SpeciesIdentification,
        customFileName: String? = null
    ): Result<ImageProcessResult> {
        val appContext = context?.applicationContext
            ?: return Result.failure(IllegalStateException("Context необхідний для обробки Android Content URI"))
        val source = ImageSource.ContentUri(uri) { targetUri ->
            appContext.contentResolver.openInputStream(targetUri)
        }
        return process(source, profile, customFileName)
    }

    /**
     * Обробляє зображення з локального файлу.
     */
    suspend fun processFile(
        file: File,
        profile: ImageProfile = ImageProfile.SpeciesIdentification,
        customFileName: String? = null
    ): Result<ImageProcessResult> {
        return process(ImageSource.FromFile(file), profile, customFileName)
    }

    /**
     * Універсальна обробка джерела зображення.
     */
    suspend fun process(
        source: ImageSource,
        profile: ImageProfile = ImageProfile.SpeciesIdentification,
        customFileName: String? = null
    ): Result<ImageProcessResult> = withContext(dispatcher) {
        val targetDir = outputDirectory
            ?: context?.let { File(it.filesDir, "plants") }
            ?: File(System.getProperty("java.io.tmpdir"), "flowers_plants")

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return@withContext Result.failure(
                ImageProcessingException.IoFailure("Неможливо створити цільову папку: ${targetDir.absolutePath}")
            )
        }

        // 1. Зчитування меж без завантаження пікселів у RAM
        val originalBounds = try {
            engine.decodeBounds(source::openStream)
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

        // 2. Зчитування EXIF-орієнтації
        val orientation = try {
            engine.readExifOrientation(source::openStream)
        } catch (_: Throwable) {
            1 // За замовчуванням normal
        }

        // 3. Розрахунок коефіцієнта субсемплювання та цільових розмірів
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

        // 4. Декодування, поворот і масштабування
        val holder = try {
            engine.decodeSampledAndTransform(
                streamOpener = source::openStream,
                inSampleSize = inSampleSize,
                targetDimensions = targetDimensions,
                orientation = orientation
            )
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

        // 5. Безпечне збереження у тимчасовий файл з наступним атомарним перейменуванням
        val extension = profile.format.extension
        val prefix = if (profile == ImageProfile.SpeciesIdentification) "species" else "diagnosis"
        val fileName = customFileName ?: "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
        val targetFile = File(targetDir, fileName)
        val tempFile = File(targetDir, "${fileName}.tmp_${UUID.randomUUID().toString().take(6)}")

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
                    tempFile.delete()
                    return@withContext Result.failure(
                        ImageProcessingException.SaveFailed("Не вдалося зберегти стиснене зображення у файл")
                    )
                }
            }

            // Атомарне перейменування для гарантії цілісності
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: OutOfMemoryError) {
            tempFile.delete()
            if (targetFile.exists()) targetFile.delete()
            return@withContext Result.failure(
                ImageProcessingException.OutOfMemory("Недостатньо пам'яті під час компресії зображення", e)
            )
        } catch (t: Throwable) {
            tempFile.delete()
            if (targetFile.exists()) targetFile.delete()
            return@withContext Result.failure(
                ImageProcessingException.SaveFailed("Збій під час запису файлу: ${t.message}", t)
            )
        } finally {
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
