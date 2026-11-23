package com.flowers.app.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Інструментальні тести реального Android-рушія AndroidImageDecoderEngine (TASK-052).
 * Виконуються на справжньому середовищі Android (нативний Skia BitmapFactory, Matrix, ExifInterface).
 */
@RunWith(AndroidJUnit4::class)
class AndroidImageProcessorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var testDir: File
    private lateinit var inputDir: File
    private lateinit var outputDir: File
    private lateinit var processor: ImageProcessor

    @Before
    fun setUp() {
        testDir = File(context.cacheDir, "android_img_test_${System.currentTimeMillis()}").apply { mkdirs() }
        inputDir = File(testDir, "input").apply { mkdirs() }
        outputDir = File(testDir, "output").apply { mkdirs() }
        processor = ImageProcessor(
            context = context,
            outputDirectory = outputDir,
            engine = AndroidImageDecoderEngine()
        )
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    /**
     * Створює 4-колірний тестовий растр:
     * - Top-Left (TL): RED
     * - Top-Right (TR): GREEN
     * - Bottom-Left (BL): BLUE
     * - Bottom-Right (BR): YELLOW
     */
    private fun createAsymmetricBitmap(width: Int = 400, height: Int = 200): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val midX = width / 2
        val midY = height / 2
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = when {
                    x < midX && y < midY -> Color.RED
                    x >= midX && y < midY -> Color.GREEN
                    x < midX && y >= midY -> Color.BLUE
                    else -> Color.YELLOW
                }
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }

    private fun saveBitmapAsJpeg(bitmap: Bitmap, file: File, orientation: Int = 1, hasGps: Boolean = false): File {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.flush()
        }
        val exif = ExifInterface(file.absolutePath)
        if (orientation != 1) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        }
        if (hasGps) {
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "50/1,27/1,1234/100")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "30/1,31/1,5678/100")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
            exif.setAttribute(ExifInterface.TAG_MAKE, "Samsung")
            exif.setAttribute(ExifInterface.TAG_MODEL, "Galaxy S24 Ultra")
        }
        exif.saveAttributes()
        return file
    }

    private fun isColorClose(actual: Int, expected: Int, tolerance: Int = 20): Boolean {
        return abs(Color.red(actual) - Color.red(expected)) <= tolerance &&
            abs(Color.green(actual) - Color.green(expected)) <= tolerance &&
            abs(Color.blue(actual) - Color.blue(expected)) <= tolerance
    }

    private fun calculateMd5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } > 0) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun real_android_decoding_and_scaling_preserves_aspect_ratio() = runBlocking {
        val srcBitmap = createAsymmetricBitmap(1600, 900)
        val srcFile = File(inputDir, "real_16_9.jpg")
        saveBitmapAsJpeg(srcBitmap, srcFile)

        val result = processor.processFile(srcFile, ImageProfile.SpeciesIdentification)
        assertTrue("Real Android processing must succeed", result.isSuccess)

        val data = result.getOrThrow()
        assertEquals(1024, data.processedDimensions.width)
        assertEquals(576, data.processedDimensions.height)

        val decodedResult = BitmapFactory.decodeFile(data.file.absolutePath)
        assertNotNull(decodedResult)
        assertEquals(1024, decodedResult.width)
        assertEquals(576, decodedResult.height)
        decodedResult.recycle()
    }

    @Test
    fun real_android_exif_orientations_verified_by_actual_pixel_coordinates() = runBlocking {
        // Тестуємо всі 8 орієнтацій EXIF за фактичним розташуванням пікселів
        for (orientation in 1..8) {
            val srcBitmap = createAsymmetricBitmap(200, 100)
            val srcFile = File(inputDir, "orient_$orientation.jpg")
            saveBitmapAsJpeg(srcBitmap, srcFile, orientation = orientation)

            val result = processor.processFile(srcFile, ImageProfile.SpeciesIdentification, customFileName = "out_$orientation.jpg")
            assertTrue("Orientation $orientation must process successfully", result.isSuccess)

            val outFile = result.getOrThrow().file
            val decoded = BitmapFactory.decodeFile(outFile.absolutePath)
            assertNotNull("Decoded bitmap for orientation $orientation must not be null", decoded)

            val w = decoded.width
            val h = decoded.height

            // Зчитуємо пікселі по кутах
            val pixelTL = decoded.getPixel(10, 10)
            val pixelTR = decoded.getPixel(w - 10, 10)
            val pixelBL = decoded.getPixel(10, h - 10)
            val pixelBR = decoded.getPixel(w - 10, h - 10)

            when (orientation) {
                // 1 (Normal): TL=RED, TR=GREEN, BL=BLUE, BR=YELLOW
                1 -> {
                    assertTrue("O1 TL Red", isColorClose(pixelTL, Color.RED))
                    assertTrue("O1 TR Green", isColorClose(pixelTR, Color.GREEN))
                    assertTrue("O1 BL Blue", isColorClose(pixelBL, Color.BLUE))
                    assertTrue("O1 BR Yellow", isColorClose(pixelBR, Color.YELLOW))
                }
                // 2 (Flip Horizontal): гориз. дзеркало
                2 -> {
                    assertTrue("O2 TL Green", isColorClose(pixelTL, Color.GREEN))
                    assertTrue("O2 TR Red", isColorClose(pixelTR, Color.RED))
                    assertTrue("O2 BL Yellow", isColorClose(pixelBL, Color.YELLOW))
                    assertTrue("O2 BR Blue", isColorClose(pixelBR, Color.BLUE))
                }
                // 3 (Rotate 180)
                3 -> {
                    assertTrue("O3 TL Yellow", isColorClose(pixelTL, Color.YELLOW))
                    assertTrue("O3 TR Blue", isColorClose(pixelTR, Color.BLUE))
                    assertTrue("O3 BL Green", isColorClose(pixelBL, Color.GREEN))
                    assertTrue("O3 BR Red", isColorClose(pixelBR, Color.RED))
                }
                // 4 (Flip Vertical)
                4 -> {
                    assertTrue("O4 TL Blue", isColorClose(pixelTL, Color.BLUE))
                    assertTrue("O4 TR Yellow", isColorClose(pixelTR, Color.YELLOW))
                    assertTrue("O4 BL Red", isColorClose(pixelBL, Color.RED))
                    assertTrue("O4 BR Green", isColorClose(pixelBR, Color.GREEN))
                }
                // 5 (Transpose: 90° CW + flip H)
                5 -> {
                    assertTrue("O5 TL Red", isColorClose(pixelTL, Color.RED))
                    assertTrue("O5 TR Blue", isColorClose(pixelTR, Color.BLUE))
                    assertTrue("O5 BL Green", isColorClose(pixelBL, Color.GREEN))
                    assertTrue("O5 BR Yellow", isColorClose(pixelBR, Color.YELLOW))
                }
                // 6 (Rotate 90° CW)
                6 -> {
                    assertTrue("O6 TL Blue", isColorClose(pixelTL, Color.BLUE))
                    assertTrue("O6 TR Red", isColorClose(pixelTR, Color.RED))
                    assertTrue("O6 BL Yellow", isColorClose(pixelBL, Color.YELLOW))
                    assertTrue("O6 BR Green", isColorClose(pixelBR, Color.GREEN))
                }
                // 7 (Transverse: 270° CW + flip H)
                7 -> {
                    assertTrue("O7 TL Yellow", isColorClose(pixelTL, Color.YELLOW))
                    assertTrue("O7 TR Green", isColorClose(pixelTR, Color.GREEN))
                    assertTrue("O7 BL Blue", isColorClose(pixelBL, Color.BLUE))
                    assertTrue("O7 BR Red", isColorClose(pixelBR, Color.RED))
                }
                // 8 (Rotate 270° CW)
                8 -> {
                    assertTrue("O8 TL Green", isColorClose(pixelTL, Color.GREEN))
                    assertTrue("O8 TR Yellow", isColorClose(pixelTR, Color.YELLOW))
                    assertTrue("O8 BL Red", isColorClose(pixelBL, Color.RED))
                    assertTrue("O8 BR Blue", isColorClose(pixelBR, Color.BLUE))
                }
            }

            decoded.recycle()
            srcBitmap.recycle()
        }
    }

    @Test
    fun real_android_gps_metadata_completely_stripped() = runBlocking {
        val srcBitmap = createAsymmetricBitmap(400, 300)
        val srcFile = File(inputDir, "photo_with_gps.jpg")
        saveBitmapAsJpeg(srcBitmap, srcFile, hasGps = true)

        val srcExif = ExifInterface(srcFile.absolutePath)
        assertNotNull(srcExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))

        val result = processor.processFile(srcFile, ImageProfile.SpeciesIdentification)
        assertTrue(result.isSuccess)

        val outFile = result.getOrThrow().file
        val outExif = ExifInterface(outFile.absolutePath)

        assertNull("GPS Latitude must be stripped", outExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull("GPS Longitude must be stripped", outExif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull("Device Make must be stripped", outExif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull("Device Model must be stripped", outExif.getAttribute(ExifInterface.TAG_MODEL))
        srcBitmap.recycle()
    }

    @Test
    fun real_android_original_file_strictly_unchanged() = runBlocking {
        val srcBitmap = createAsymmetricBitmap(500, 300)
        val srcFile = File(inputDir, "original_immutable.jpg")
        saveBitmapAsJpeg(srcBitmap, srcFile)

        val initialLen = srcFile.length()
        val initialMd5 = calculateMd5(srcFile)

        val result = processor.processFile(srcFile, ImageProfile.SpeciesIdentification)
        assertTrue(result.isSuccess)

        assertEquals("Original length must not change", initialLen, srcFile.length())
        assertEquals("Original MD5 must not change", initialMd5, calculateMd5(srcFile))
        srcBitmap.recycle()
    }
}
