package com.flowers.app.data.image

import java.io.File

/**
 * Формати стиснення для оптимізованих зображень (TASK-052).
 */
enum class ImageFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    WEBP("webp", "image/webp")
}

/**
 * Профілі оптимізації фотографій Flowers 2.0 (TASK-052, ADR-021).
 *
 * @property maxDimension Максимальний розмір по довшій стороні в пікселях.
 * @property quality Якість стиснення (1..100).
 * @property format Цільовий формат файлу (JPEG або WebP).
 */
enum class ImageProfile(
    val maxDimension: Int,
    val quality: Int,
    val format: ImageFormat = ImageFormat.JPEG
) {
    /**
        * Профіль для визначення виду рослини: до 1024 px по довшій стороні, якість ~80%.
     * Мінімізує споживання трафіку й оперативної пам'яті для швидкого виклику Gemini AI.
     */
    SpeciesIdentification(maxDimension = 1024, quality = 80, format = ImageFormat.JPEG),

    /**
        * Профіль для фото-діагностики захворювань: до 1536 px по довшій стороні, якість ~85%.
     * Зберігає високу чіткість мікротекстур листя, плям і шкідників.
     */
    DiseaseDiagnosis(maxDimension = 1536, quality = 85, format = ImageFormat.JPEG),

    /**
     * Профіль для фотографій персонального чату рослини (TASK-056 / PR №44):
     * до 1536 px по довшій стороні, JPEG якість 85%.
     */
    ChatConsultation(maxDimension = 1536, quality = 85, format = ImageFormat.JPEG)
}

/**
 * Геометричні розміри зображення.
 */
data class ImageDimensions(val width: Int, val height: Int) {
    val maxDimension: Int get() = maxOf(width, height)
    val minDimension: Int get() = minOf(width, height)
    val aspectRatio: Float get() = if (height > 0) width.toFloat() / height.toFloat() else 1.0f
    val megaPixels: Double get() = (width.toLong() * height.toLong()) / 1_000_000.0
}

/**
 * Результат обробки фотографії.
 */
data class ImageProcessResult(
    val file: File,
    val originalDimensions: ImageDimensions,
    val processedDimensions: ImageDimensions,
    val inSampleSize: Int,
    val byteSize: Long,
    val mimeType: String
)

/**
 * Типізовані винятки обробки зображень Flowers 2.0.
 */
sealed class ImageProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidImage(message: String, cause: Throwable? = null) : ImageProcessingException(message, cause)
    class UnsupportedFormat(message: String) : ImageProcessingException(message)
    class OutOfMemory(message: String, cause: Throwable? = null) : ImageProcessingException(message, cause)
    class IoFailure(message: String, cause: Throwable? = null) : ImageProcessingException(message, cause)
    class SaveFailed(message: String, cause: Throwable? = null) : ImageProcessingException(message, cause)
    class AtomicMoveNotSupported(message: String, cause: Throwable? = null) : ImageProcessingException(message, cause)
    class FileAlreadyExists(message: String) : ImageProcessingException(message)
    class InvalidFileName(message: String) : ImageProcessingException(message)
}
