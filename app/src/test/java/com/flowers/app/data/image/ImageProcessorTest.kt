package com.flowers.app.data.image

import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.abs

class ImageProcessorTest {

    private lateinit var testWorkDir: File
    private lateinit var inputDir: File
    private lateinit var outputDir: File
    private lateinit var engine: ImageDecoderEngine
    private lateinit var processor: ImageProcessor

    @Before
    fun setUp() {
        testWorkDir = File(System.getProperty("java.io.tmpdir"), "flowers_img_test_${System.currentTimeMillis()}")
        inputDir = File(testWorkDir, "input").apply { mkdirs() }
        outputDir = File(testWorkDir, "output").apply { mkdirs() }
        engine = TestImageDecoderEngine()
        processor = ImageProcessor(
            context = null,
            outputDirectory = outputDir,
            engine = engine
        )
    }

    @After
    fun tearDown() {
        testWorkDir.deleteRecursively()
    }

    private fun createTestJpeg(
        file: File,
        width: Int,
        height: Int,
        orientation: Int = 1,
        hasGps: Boolean = false
    ): File {
        val bytes = MinimalJpeg.createBytes(width, height, orientation = orientation, hasGps = hasGps)
        FileOutputStream(file).use { it.write(bytes) }
        return file
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
    fun `SpeciesIdentification profile downsizes to max 1024px preserving aspect ratio`() = runBlocking {
        val originalFile = File(inputDir, "large_landscape.jpg")
        createTestJpeg(originalFile, 1920, 1080)

        val result = processor.processFile(originalFile, ImageProfile.SpeciesIdentification)
        assertTrue("Processing should succeed", result.isSuccess)

        val data = result.getOrThrow()
        assertTrue("Output file must exist", data.file.exists())
        assertEquals("image/jpeg", data.mimeType)
        assertEquals(1024, data.processedDimensions.width)
        assertEquals(576, data.processedDimensions.height)
        assertTrue(data.byteSize > 0)

        // Verify aspect ratio preservation
        val originalAspect = 1920f / 1080f
        val processedAspect = data.processedDimensions.width.toFloat() / data.processedDimensions.height.toFloat()
        assertTrue("Aspect ratio preserved", abs(originalAspect - processedAspect) < 0.01)
    }

    @Test
    fun `DiseaseDiagnosis profile downsizes to max 1536px`() = runBlocking {
        val originalFile = File(inputDir, "high_res_leaf.jpg")
        createTestJpeg(originalFile, 2560, 1440)

        val result = processor.processFile(originalFile, ImageProfile.DiseaseDiagnosis)
        assertTrue("Processing should succeed", result.isSuccess)

        val data = result.getOrThrow()
        assertEquals(1536, data.processedDimensions.width)
        assertEquals(864, data.processedDimensions.height)
        assertTrue(data.processedDimensions.maxDimension <= 1536)
    }

    @Test
    fun `test photos with 12, 50, and 200 MP are properly subsampled`() = runBlocking {
        // 12 MP (4000x3000)
        val file12 = File(inputDir, "photo_12mp.jpg")
        createTestJpeg(file12, 4000, 3000)
        val res12 = processor.processFile(file12, ImageProfile.SpeciesIdentification).getOrThrow()
        assertEquals(2, res12.inSampleSize)
        assertEquals(1024, res12.processedDimensions.width)
        assertEquals(768, res12.processedDimensions.height)

        // 50 MP (8192x6144)
        val file50 = File(inputDir, "photo_50mp.jpg")
        createTestJpeg(file50, 8192, 6144)
        val res50 = processor.processFile(file50, ImageProfile.SpeciesIdentification).getOrThrow()
        assertEquals(8, res50.inSampleSize)
        assertEquals(1024, res50.processedDimensions.width)
        assertEquals(768, res50.processedDimensions.height)

        // 200 MP (16384x12288 - ISOCELL HP2 full sensor mode)
        val file200 = File(inputDir, "photo_200mp.jpg")
        createTestJpeg(file200, 16384, 12288)
        val res200 = processor.processFile(file200, ImageProfile.SpeciesIdentification).getOrThrow()
        assertEquals(16, res200.inSampleSize)
        assertEquals(1024, res200.processedDimensions.width)
        assertEquals(768, res200.processedDimensions.height)
    }

    @Test
    fun `EXIF orientation with 90 degree rotation correctly swaps dimensions`() = runBlocking {
        val originalFile = File(inputDir, "rotated_90.jpg")
        createTestJpeg(originalFile, 1920, 1080, orientation = 6) // Orientation 6 = 90° CW

        // Verify source has orientation 6
        val srcExif = ExifInterface(originalFile.absolutePath)
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, srcExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1))

        val result = processor.processFile(originalFile, ImageProfile.SpeciesIdentification)
        assertTrue(result.isSuccess)

        val data = result.getOrThrow()
        // Visual image is portrait (1080x1920) -> downscaled to 576x1024
        assertEquals(576, data.processedDimensions.width)
        assertEquals(1024, data.processedDimensions.height)
    }

    @Test
    fun `original file in gallery or storage is never altered`() = runBlocking {
        val originalFile = File(inputDir, "gallery_photo.jpg")
        createTestJpeg(originalFile, 1200, 800)

        val initialSize = originalFile.length()
        val initialMd5 = calculateMd5(originalFile)

        val result = processor.processFile(originalFile, ImageProfile.SpeciesIdentification)
        assertTrue(result.isSuccess)

        // Verify original file remained strictly byte-identical
        assertEquals("Original size must not change", initialSize, originalFile.length())
        assertEquals("Original MD5 hash must not change", initialMd5, calculateMd5(originalFile))
    }

    @Test
    fun `GPS and camera metadata are completely removed from output file`() = runBlocking {
        val originalFile = File(inputDir, "geotagged_photo.jpg")
        createTestJpeg(originalFile, 800, 600, hasGps = true)

        // Verify source has GPS
        val checkSrcExif = ExifInterface(originalFile.absolutePath)
        assertNotNull(checkSrcExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))

        // Process through ImageProcessor
        val result = processor.processFile(originalFile, ImageProfile.SpeciesIdentification)
        assertTrue(result.isSuccess)

        val outputFile = result.getOrThrow().file
        val outExif = ExifInterface(outputFile.absolutePath)

        // Assert all GPS and camera metadata tags are null / stripped
        assertNull("GPS latitude must be stripped", outExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull("Camera make must be stripped", outExif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull("Camera model must be stripped", outExif.getAttribute(ExifInterface.TAG_MODEL))
        val orientationTag = outExif.getAttribute(ExifInterface.TAG_ORIENTATION)
        assertTrue("EXIF orientation tag must be null or undefined (0)", orientationTag == null || orientationTag == "0")
    }

    @Test
    fun `corrupt and non-image files return failure without creating corrupt files`() = runBlocking {
        val corruptFile = File(inputDir, "broken_image.jpg")
        corruptFile.writeText("THIS IS NOT A VALID JPEG OR IMAGE STREAM AT ALL")

        val result = processor.processFile(corruptFile, ImageProfile.SpeciesIdentification)
        assertTrue("Must fail gracefully for corrupt files", result.isFailure)
        assertTrue(result.exceptionOrNull() is ImageProcessingException)

        // Verify no leftover files in output directory
        val leftoverFiles = outputDir.listFiles() ?: emptyArray()
        assertEquals("Output directory must be empty after failed processing", 0, leftoverFiles.size)
    }

    @Test
    fun `failed compression cleans up temporary files atomically`() = runBlocking {
        val validFile = File(inputDir, "valid_for_failing_engine.jpg")
        createTestJpeg(validFile, 600, 400)

        // Mock engine that fails during compression
        val failingEngine = object : ImageDecoderEngine by engine {
            override fun compressToFile(
                bitmapHolder: DecodedBitmapHolder,
                format: ImageFormat,
                quality: Int,
                destinationFile: File
            ): Boolean {
                // Simulate partial write followed by failure
                destinationFile.writeText("partial garbage")
                return false
            }
        }

        val failingProcessor = ImageProcessor(outputDirectory = outputDir, engine = failingEngine)
        val result = failingProcessor.processFile(validFile, ImageProfile.SpeciesIdentification)

        assertTrue(result.isFailure)
        val files = outputDir.listFiles() ?: emptyArray()
        // Guarantee no incomplete/corrupt or .tmp files remain
        assertEquals("No temporary or partial files should be left behind", 0, files.size)
    }
}
