package com.flowers.app.data.image

import kotlin.math.roundToInt

/**
 * Опис геометричної трансформації EXIF-орієнтації (поворот і віддзеркалення).
 *
 * @property rotationDegrees Кут повороту за годинниковою стрілкою (0, 90, 180, 270).
 * @property flipHorizontal Чи потрібно виконати горизонтальне дзеркальне відображення.
 * @property flipVertical Чи потрібно виконати вертикальне дзеркальне відображення.
 * @property swapsDimensions Чи міняються місцями ширина та висота (для поворотів на 90° та 270°).
 */
data class ExifTransform(
    val rotationDegrees: Float,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val swapsDimensions: Boolean
)

/**
 * Калькулятор масштабування, пам'яті та EXIF-геометрії (TASK-052).
 * Чисті детерміновані функції без прив'язки до Android framework для надійного тестування.
 */
object ImageScaleCalculator {

    /**
     * Розраховує безпечний коефіцієнт субсемплювання inSampleSize як степінь двійки (1, 2, 4, 8, 16...).
     * Гарантує, що декодоване в пам'ять зображення не буде меншим за цільовий розмір,
     * але зменшить витрати оперативної пам'яті в (inSampleSize^2) разів.
     */
    fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, targetMaxDimension: Int): Int {
        if (rawWidth <= 0 || rawHeight <= 0 || targetMaxDimension <= 0) return 1
        var inSampleSize = 1
        val maxRaw = maxOf(rawWidth, rawHeight)
        while (maxRaw / (inSampleSize * 2) >= targetMaxDimension) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    /**
     * Повертає параметри трансформації для всіх 8 стандартних EXIF-орієнтацій (TIFF/EXIF tag 0x0112).
     */
    fun getExifTransform(orientation: Int): ExifTransform = when (orientation) {
        // 1: Normal (0° rotation)
        1 -> ExifTransform(rotationDegrees = 0f, flipHorizontal = false, flipVertical = false, swapsDimensions = false)
        // 2: Flip Horizontal (дзеркало по горизонталі)
        2 -> ExifTransform(rotationDegrees = 0f, flipHorizontal = true, flipVertical = false, swapsDimensions = false)
        // 3: Rotate 180°
        3 -> ExifTransform(rotationDegrees = 180f, flipHorizontal = false, flipVertical = false, swapsDimensions = false)
        // 4: Flip Vertical (дзеркало по вертикалі)
        4 -> ExifTransform(rotationDegrees = 0f, flipHorizontal = false, flipVertical = true, swapsDimensions = false)
        // 5: Transpose (поворот на 90° CW + горизонтальне дзеркало)
        5 -> ExifTransform(rotationDegrees = 90f, flipHorizontal = true, flipVertical = false, swapsDimensions = true)
        // 6: Rotate 90° CW
        6 -> ExifTransform(rotationDegrees = 90f, flipHorizontal = false, flipVertical = false, swapsDimensions = true)
        // 7: Transverse (поворот на 270° CW + горизонтальне дзеркало)
        7 -> ExifTransform(rotationDegrees = 270f, flipHorizontal = true, flipVertical = false, swapsDimensions = true)
        // 8: Rotate 270° CW (або 90° CCW)
        8 -> ExifTransform(rotationDegrees = 270f, flipHorizontal = false, flipVertical = false, swapsDimensions = true)
        // Undefined або некоректні значення розглядаються як звичайна орієнтація
        else -> ExifTransform(rotationDegrees = 0f, flipHorizontal = false, flipVertical = false, swapsDimensions = false)
    }

    /**
     * Розраховує кінцеві розміри зображення зі збереженням пропорцій (aspect ratio)
     * та урахуванням орієнтації EXIF. Зображення, менші за цільовий розмір, не збільшуються.
     */
    fun calculateTargetDimensions(
        rawWidth: Int,
        rawHeight: Int,
        targetMaxDimension: Int,
        orientation: Int = 1
    ): ImageDimensions {
        if (rawWidth <= 0 || rawHeight <= 0) return ImageDimensions(0, 0)
        val transform = getExifTransform(orientation)
        val visualWidth = if (transform.swapsDimensions) rawHeight else rawWidth
        val visualHeight = if (transform.swapsDimensions) rawWidth else rawHeight
        val maxVisual = maxOf(visualWidth, visualHeight)

        if (maxVisual <= targetMaxDimension) {
            return ImageDimensions(visualWidth, visualHeight)
        }

        val scale = targetMaxDimension.toFloat() / maxVisual.toFloat()
        val targetWidth = (visualWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (visualHeight * scale).roundToInt().coerceAtLeast(1)
        return ImageDimensions(targetWidth, targetHeight)
    }

    /**
     * Оцінює обсяг оперативної пам'яті (у байтах), необхідний для зберігання пікселів у форматі ARGB_8888 (4 байти на піксель).
     */
    fun estimateMemoryUsageBytes(width: Int, height: Int, inSampleSize: Int = 1, bytesPerPixel: Int = 4): Long {
        if (width <= 0 || height <= 0 || inSampleSize <= 0) return 0L
        val sampledWidth = (width / inSampleSize).coerceAtLeast(1).toLong()
        val sampledHeight = (height / inSampleSize).coerceAtLeast(1).toLong()
        return sampledWidth * sampledHeight * bytesPerPixel.toLong()
    }
}
