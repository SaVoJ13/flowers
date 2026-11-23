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
 * Деталізована оцінка використання оперативної пам'яті під час обробки.
 * Відокремлює теоретичний обсяг сирого растру від фактичного пікового сліду з урахуванням
 * проміжних Bitmap, матричних перетворень та буферів стиснення.
 *
 * @property theoreticalRawBytes Теоретичний розмір растру повного оригіналу (w * h * 4).
 * @property sampledBitmapBytes Розмір первинного субсемпльованого растру після BitmapFactory.
 * @property intermediateTransformedBytes Розмір другого проміжного растру під час трансформації Matrix (createBitmap).
 * @property compressionBufferBytes Орієнтовний обсяг системного буфера потоку кодування (1–2 МБ).
 * @property estimatedPeakBytes Оцінка пікового споживання пам'яті в момент одночасного існування двох растрових копій.
 */
data class MemoryEstimate(
    val theoreticalRawBytes: Long,
    val sampledBitmapBytes: Long,
    val intermediateTransformedBytes: Long,
    val compressionBufferBytes: Long,
    val estimatedPeakBytes: Long
) {
    val estimatedSavingsPercentage: Double
        get() = if (theoreticalRawBytes > 0) {
            ((theoreticalRawBytes - estimatedPeakBytes).coerceAtLeast(0).toDouble() / theoreticalRawBytes.toDouble()) * 100.0
        } else 0.0
}

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
     * Розраховує повну аналітичну оцінку споживання пам'яті під час конвеєра обробки.
     * Враховує:
     * 1. Первинний семпльований Bitmap.
     * 2. Другий проміжний Bitmap, який виділяється методом Bitmap.createBitmap під час застосування Matrix.
     * 3. Буфер стиснення потоку виведення (~1.5 МБ).
     */
    fun estimateMemoryFootprint(
        rawWidth: Int,
        rawHeight: Int,
        targetMaxDimension: Int,
        orientation: Int = 1,
        bytesPerPixel: Int = 4
    ): MemoryEstimate {
        val theoreticalRaw = rawWidth.toLong() * rawHeight.toLong() * bytesPerPixel.toLong()
        val inSampleSize = calculateInSampleSize(rawWidth, rawHeight, targetMaxDimension)
        val sampledW = (rawWidth / inSampleSize).coerceAtLeast(1).toLong()
        val sampledH = (rawHeight / inSampleSize).coerceAtLeast(1).toLong()
        val sampledBytes = sampledW * sampledH * bytesPerPixel.toLong()

        val targetDims = calculateTargetDimensions(rawWidth, rawHeight, targetMaxDimension, orientation)
        val transform = getExifTransform(orientation)
        val needsTransform = transform.rotationDegrees != 0f ||
            transform.flipHorizontal ||
            transform.flipVertical ||
            maxOf(sampledW, sampledH) > targetMaxDimension

        val intermediateBytes = if (needsTransform) {
            targetDims.width.toLong() * targetDims.height.toLong() * bytesPerPixel.toLong()
        } else {
            0L
        }

        val compressionBufferBytes = 1_500_000L // ~1.5 МБ для буферів потоку кодера
        val peakBytes = sampledBytes + intermediateBytes + compressionBufferBytes

        return MemoryEstimate(
            theoreticalRawBytes = theoreticalRaw,
            sampledBitmapBytes = sampledBytes,
            intermediateTransformedBytes = intermediateBytes,
            compressionBufferBytes = compressionBufferBytes,
            estimatedPeakBytes = peakBytes
        )
    }

    /**
     * Оцінює теоретичний обсяг пам'яті (у байтах) для зберігання растру в форматі ARGB_8888.
     */
    fun estimateRawMemoryBytes(width: Int, height: Int, inSampleSize: Int = 1, bytesPerPixel: Int = 4): Long {
        if (width <= 0 || height <= 0 || inSampleSize <= 0) return 0L
        val sampledWidth = (width / inSampleSize).coerceAtLeast(1).toLong()
        val sampledHeight = (height / inSampleSize).coerceAtLeast(1).toLong()
        return sampledWidth * sampledHeight * bytesPerPixel.toLong()
    }
}
