package com.flowers.app.data.image

import java.io.File
import java.io.InputStream

/**
 * Тримач декодованого зображення в пам'яті, що гарантує звільнення пам'яті (AutoCloseable).
 */
interface DecodedBitmapHolder : AutoCloseable {
    val width: Int
    val height: Int
}

/**
 * Низькорівневий рушій декодування та компресії зображень (TASK-052).
 * Відокремлює системні графічні виклики від високорівневої логіки збереження та контролю цілісності.
 */
interface ImageDecoderEngine {

    /**
     * Зчитує вихідні розміри зображення без виділення пам'яті під пікселі.
     * Повертає null, якщо потік пошкоджений або не є підтримуваним зображенням.
     */
    fun decodeBounds(streamOpener: () -> InputStream): ImageDimensions?

    /**
     * Зчитує EXIF-орієнтацію (1..8) через ExifInterface. Повертає 1 (Normal) при відсутності тегу.
     */
    fun readExifOrientation(streamOpener: () -> InputStream): Int

    /**
     * Декодує субсемпльований растр і застосовує трансформацію (поворот, дзеркало, фінальне масштабування).
     */
    fun decodeSampledAndTransform(
        streamOpener: () -> InputStream,
        inSampleSize: Int,
        targetDimensions: ImageDimensions,
        orientation: Int
    ): DecodedBitmapHolder?

    /**
     * Стискає растр у файл без збереження вихідних метаданих (GPS, серійні номери камери тощо).
     */
    fun compressToFile(
        bitmapHolder: DecodedBitmapHolder,
        format: ImageFormat,
        quality: Int,
        destinationFile: File
    ): Boolean
}
