package com.flowers.app.data.image

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Тримач тестового растру для перевірки розмірів на JVM.
 */
class TestDecodedBitmapHolder(
    override val width: Int,
    override val height: Int
) : DecodedBitmapHolder {
    var isClosed: Boolean = false
        private set

    override fun close() {
        isClosed = true
    }
}

/**
 * Генератор та парсер валідних JPEG-файлів за стандартом ITU-T T.81 з підтримкою EXIF APP1.
 * Працює на чистому JVM без залежностей від java.desktop / AWT.
 */
object MinimalJpeg {

    /**
     * Створює валідний сегмент EXIF APP1 (marker 0xFFE1) за специфікацією TIFF Little Endian.
     */
    fun createExifApp1(orientation: Int = 1, hasGps: Boolean = false): ByteArray {
        val tiffBaos = ByteArrayOutputStream()
        val tiff = DataOutputStream(tiffBaos)

        fun writeShortLE(v: Int) {
            tiff.writeByte(v and 0xFF)
            tiff.writeByte((v ushr 8) and 0xFF)
        }
        fun writeIntLE(v: Int) {
            tiff.writeByte(v and 0xFF)
            tiff.writeByte((v ushr 8) and 0xFF)
            tiff.writeByte((v ushr 16) and 0xFF)
            tiff.writeByte((v ushr 24) and 0xFF)
        }

        // 1. TIFF Header (8 bytes)
        tiff.writeByte(0x49); tiff.writeByte(0x49) // "II" (Intel Little Endian)
        writeShortLE(42) // Magic 42
        writeIntLE(8) // Offset to IFD0 is 8 bytes from TIFF start

        // 2. IFD0
        val numTags = if (hasGps) 2 else 1
        writeShortLE(numTags)

        // Tag: Orientation (0x0112), Type: SHORT (3), Count: 1
        writeShortLE(0x0112)
        writeShortLE(3)
        writeIntLE(1)
        writeShortLE(orientation)
        writeShortLE(0) // Padding to 4 bytes

        if (hasGps) {
            // Tag: GPSInfo (0x8825), Type: LONG (4), Count: 1
            writeShortLE(0x8825)
            writeShortLE(4)
            writeIntLE(1)
            val gpsOffset = 8 + 2 + numTags * 12 + 4
            writeIntLE(gpsOffset)
        }

        writeIntLE(0) // Next IFD offset = 0

        if (hasGps) {
            // GPS IFD
            writeShortLE(2) // 2 GPS tags

            // Tag 1: GPSLatitudeRef (0x0001), ASCII, Count 2 ("N\0")
            writeShortLE(0x0001)
            writeShortLE(2)
            writeIntLE(2)
            tiff.writeByte('N'.code); tiff.writeByte(0); tiff.writeByte(0); tiff.writeByte(0)

            // Tag 2: GPSLatitude (0x0002), RATIONAL (5), Count 3
            writeShortLE(0x0002)
            writeShortLE(5)
            writeIntLE(3)
            val valuesOffset = 38 + 2 + 2 * 12 + 4
            writeIntLE(valuesOffset)

            writeIntLE(0) // Next IFD

            // Values for 50° 27' 12.0" N
            writeIntLE(50); writeIntLE(1)
            writeIntLE(27); writeIntLE(1)
            writeIntLE(1200); writeIntLE(100)
        }

        val tiffBytes = tiffBaos.toByteArray()
        val app1Baos = ByteArrayOutputStream()
        val app1 = DataOutputStream(app1Baos)
        app1.writeShort(0xFFE1) // APP1 marker
        app1.writeShort(2 + 6 + tiffBytes.size) // Total APP1 length
        app1.write("Exif\u0000\u0000".toByteArray(Charsets.US_ASCII))
        app1.write(tiffBytes)

        return app1Baos.toByteArray()
    }

    /**
     * Генерує валідний бінарний потік JPEG із заданими розмірами SOF0 та опціональним EXIF APP1.
     */
    fun createBytes(width: Int, height: Int, orientation: Int = 1, hasGps: Boolean = false): ByteArray {
        val baos = ByteArrayOutputStream()
        val out = DataOutputStream(baos)

        // 1. SOI (Start of Image)
        out.writeShort(0xFFD8.toShort().toInt())

        // 2. APP0 (JFIF Header)
        out.writeShort(0xFFE0.toShort().toInt())
        out.writeShort(16) // Length
        out.write("JFIF\u0000".toByteArray(Charsets.US_ASCII))
        out.writeByte(1)
        out.writeByte(2) // Version 1.02
        out.writeByte(0) // Units
        out.writeShort(1) // Xdensity
        out.writeShort(1) // Ydensity
        out.writeByte(0) // Xthumbnail
        out.writeByte(0) // Ythumbnail

        // 3. EXIF APP1 (якщо задано орієнтацію або GPS)
        if (orientation != 1 || hasGps) {
            val app1Bytes = createExifApp1(orientation = orientation, hasGps = hasGps)
            out.write(app1Bytes)
        }

        // 4. SOF0 (Start of Frame - Baseline DCT)
        out.writeShort(0xFFC0.toShort().toInt())
        out.writeShort(17) // Length
        out.writeByte(8) // Precision (8 bits)
        out.writeShort(height) // Height
        out.writeShort(width) // Width
        out.writeByte(3) // 3 Components (Y, Cb, Cr)
        out.writeByte(1); out.writeByte(0x11); out.writeByte(0)
        out.writeByte(2); out.writeByte(0x11); out.writeByte(0)
        out.writeByte(3); out.writeByte(0x11); out.writeByte(0)

        // 5. EOI (End of Image)
        out.writeShort(0xFFD9.toShort().toInt())
        out.flush()

        return baos.toByteArray()
    }

    /**
     * Зчитує розміри JPEG із маркерів SOF0/SOF2 без декодування пікселів.
     */
    fun parseBounds(inputStream: InputStream): ImageDimensions? {
        val dis = DataInputStream(inputStream)
        return try {
            val soi = dis.readUnsignedShort()
            if (soi != 0xFFD8) return null // Не JPEG

            while (true) {
                val marker = dis.readUnsignedShort()
                if (marker == 0xFFD9 || marker == 0xFFDA) break // EOI або SOS (пікселі)

                val length = dis.readUnsignedShort()
                if (length < 2) return null

                // SOF0 (0xFFC0) або SOF2 (0xFFC2)
                if (marker == 0xFFC0 || marker == 0xFFC2) {
                    dis.readUnsignedByte() // Precision
                    val height = dis.readUnsignedShort()
                    val width = dis.readUnsignedShort()
                    return ImageDimensions(width, height)
                } else {
                    val bytesToSkip = length - 2
                    var skipped = 0L
                    while (skipped < bytesToSkip) {
                        val s = dis.skip((bytesToSkip - skipped).toLong())
                        if (s <= 0) {
                            if (dis.read() == -1) return null
                            skipped += 1
                        } else {
                            skipped += s
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Тестовий рушій ImageDecoderEngine для виконання юніт-тестів на будь-якій платформі (Linux/macOS/Windows, CI).
 */
class TestImageDecoderEngine : ImageDecoderEngine {

    override fun decodeBounds(streamOpener: () -> InputStream): ImageDimensions? = runCatching {
        streamOpener().use { stream -> MinimalJpeg.parseBounds(stream) }
    }.getOrNull()

    override fun readExifOrientation(streamOpener: () -> InputStream): Int = runCatching {
        streamOpener().use { stream ->
            val exif = ExifInterface(stream)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    override fun decodeSampledAndTransform(
        streamOpener: () -> InputStream,
        inSampleSize: Int,
        targetDimensions: ImageDimensions,
        orientation: Int
    ): DecodedBitmapHolder? = runCatching {
        val originalBounds = streamOpener().use { MinimalJpeg.parseBounds(it) } ?: return null

        val transform = ImageScaleCalculator.getExifTransform(orientation)
        val visualWidth = if (transform.swapsDimensions) originalBounds.height else originalBounds.width
        val visualHeight = if (transform.swapsDimensions) originalBounds.width else originalBounds.height

        val maxVisual = maxOf(visualWidth, visualHeight)
        val scale = if (maxVisual > targetDimensions.maxDimension && maxVisual > 0) {
            targetDimensions.maxDimension.toFloat() / maxVisual.toFloat()
        } else {
            1.0f
        }

        val targetWidth = (visualWidth * scale).toInt().coerceAtLeast(1)
        val targetHeight = (visualHeight * scale).toInt().coerceAtLeast(1)

        TestDecodedBitmapHolder(width = targetWidth, height = targetHeight)
    }.getOrNull()

    override fun compressToFile(
        bitmapHolder: DecodedBitmapHolder,
        format: ImageFormat,
        quality: Int,
        destinationFile: File
    ): Boolean = runCatching {
        destinationFile.parentFile?.mkdirs()
        // Зберігає чистий JPEG без вихідних EXIF-метаданих
        val cleanJpegBytes = MinimalJpeg.createBytes(bitmapHolder.width, bitmapHolder.height, orientation = 1, hasGps = false)
        FileOutputStream(destinationFile).use { fos ->
            fos.write(cleanJpegBytes)
            fos.flush()
        }
        destinationFile.exists() && destinationFile.length() > 0L
    }.getOrDefault(false)
}
