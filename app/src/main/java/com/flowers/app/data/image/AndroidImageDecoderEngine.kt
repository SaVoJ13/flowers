package com.flowers.app.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Android-реалізація обгортки над Bitmap для автоматичного звільнення пам'яті.
 */
class AndroidDecodedBitmap(val bitmap: Bitmap) : DecodedBitmapHolder {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun close() {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

/**
 * Продакшен-рушій обробки зображень на Android (TASK-052).
 * Використовує нативний BitmapFactory із двоетапним декодуванням та Matrix для трансформацій.
 */
class AndroidImageDecoderEngine : ImageDecoderEngine {

    override fun decodeBounds(streamOpener: () -> InputStream): ImageDimensions? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        return try {
            streamOpener().use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                ImageDimensions(options.outWidth, options.outHeight)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override fun readExifOrientation(streamOpener: () -> InputStream): Int {
        return try {
            streamOpener().use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    override fun decodeSampledAndTransform(
        streamOpener: () -> InputStream,
        inSampleSize: Int,
        targetDimensions: ImageDimensions,
        orientation: Int
    ): DecodedBitmapHolder? {
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var sampledBitmap: Bitmap? = null
        try {
            sampledBitmap = streamOpener().use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
            if (sampledBitmap == null) return null

            val transform = ImageScaleCalculator.getExifTransform(orientation)
            val matrix = Matrix()

            // 1. Поворот за годинниковою стрілкою
            if (transform.rotationDegrees != 0f) {
                matrix.postRotate(transform.rotationDegrees)
            }
            // 2. Дзеркальні відображення
            val sx = if (transform.flipHorizontal) -1f else 1f
            val sy = if (transform.flipVertical) -1f else 1f
            if (sx != 1f || sy != 1f) {
                matrix.postScale(sx, sy)
            }

            // 3. Масштабування до цільового розміру
            val visualWidth = if (transform.swapsDimensions) sampledBitmap.height else sampledBitmap.width
            val visualHeight = if (transform.swapsDimensions) sampledBitmap.width else sampledBitmap.height
            val maxVisual = maxOf(visualWidth, visualHeight)

            if (maxVisual > targetDimensions.maxDimension && maxVisual > 0) {
                val scale = targetDimensions.maxDimension.toFloat() / maxVisual.toFloat()
                matrix.postScale(scale, scale)
            }

            val finalBitmap = if (matrix.isIdentity) {
                sampledBitmap
            } else {
                try {
                    val transformed = Bitmap.createBitmap(
                        sampledBitmap,
                        0,
                        0,
                        sampledBitmap.width,
                        sampledBitmap.height,
                        matrix,
                        true
                    )
                    if (transformed != sampledBitmap && !sampledBitmap.isRecycled) {
                        sampledBitmap.recycle()
                    }
                    transformed
                } catch (e: OutOfMemoryError) {
                    if (!sampledBitmap.isRecycled) {
                        sampledBitmap.recycle()
                    }
                    throw e
                } catch (t: Throwable) {
                    if (!sampledBitmap.isRecycled) {
                        sampledBitmap.recycle()
                    }
                    throw t
                }
            }
            return AndroidDecodedBitmap(finalBitmap)
        } catch (e: CancellationException) {
            sampledBitmap?.let { if (!it.isRecycled) it.recycle() }
            throw e
        } catch (e: OutOfMemoryError) {
            sampledBitmap?.let { if (!it.isRecycled) it.recycle() }
            throw e
        } catch (_: Exception) {
            sampledBitmap?.let { if (!it.isRecycled) it.recycle() }
            return null
        }
    }

    override fun compressToFile(
        bitmapHolder: DecodedBitmapHolder,
        format: ImageFormat,
        quality: Int,
        destinationFile: File
    ): Boolean {
        val androidBitmap = (bitmapHolder as? AndroidDecodedBitmap)?.bitmap ?: return false
        destinationFile.parentFile?.mkdirs()

        val compressFormat = when (format) {
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }
        return try {
            FileOutputStream(destinationFile).use { out ->
                val success = androidBitmap.compress(compressFormat, quality.coerceIn(1, 100), out)
                out.flush()
                success
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}
