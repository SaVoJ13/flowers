package com.flowers.app.data.image

import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val srcExif = ExifInterface(originalFile.absolutePath)
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, srcExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1))

        val result = processor.processFile(originalFile, ImageProfile.SpeciesIdentification)
        assertTrue(result.isSuccess)

        val data = result.getOrThrow()
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

        assertEquals("Original size must not change", initialSize, originalFile.length())
        assertEquals("Original MD5 hash must not change", initialMd5, calculateMd5(originalFile))
    }

    @Test
    fun `GPS and camera metadata are completely removed from output file`() = runBlocking {
        val originalFile = File(inputDir, "geotagged_photo.jpg")
        createTestJpeg(originalFile, 800, 600, hasGps = true)

        val checkSrcExif = ExifInterface(originalFile.absolutePath)
        assertNotNull(checkSrcExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))

        val result = processor.processFile(originalFile, ImageProfile.SpeciesIdentification)
        assertTrue(result.isSuccess)

        val outputFile = result.getOrThrow().file
        val outExif = ExifInterface(outputFile.absolutePath)

        assertNull("GPS latitude must be stripped", outExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull("Camera make must be stripped", outExif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull("Camera model must be stripped", outExif.getAttribute(ExifInterface.TAG_MODEL))
        val orientationTag = outExif.getAttribute(ExifInterface.TAG_ORIENTATION)
        assertTrue("EXIF orientation tag must be null or undefined (0)", orientationTag == null || orientationTag == "0")
    }

    @Test
    fun `path traversal attempts are detected and rejected`() = runBlocking {
        val originalFile = File(inputDir, "normal.jpg")
        createTestJpeg(originalFile, 600, 400)

        // Attempt 1: Relative navigation ../../evil.jpg
        val res1 = processor.processFile(originalFile, customFileName = "../../evil.jpg")
        assertTrue(res1.isFailure)
        assertTrue(res1.exceptionOrNull() is ImageProcessingException.InvalidFileName)

        // Attempt 2: Subdirectory slash
        val res2 = processor.processFile(originalFile, customFileName = "subdir/photo.jpg")
        assertTrue(res2.isFailure)
        assertTrue(res2.exceptionOrNull() is ImageProcessingException.InvalidFileName)

        // Attempt 3: Backslash
        val res3 = processor.processFile(originalFile, customFileName = "test\\escape.jpg")
        assertTrue(res3.isFailure)
        assertTrue(res3.exceptionOrNull() is ImageProcessingException.InvalidFileName)
    }

    @Test
    fun `forbids overwriting existing target file and preserves existing file untouched`() = runBlocking {
        val originalFile = File(inputDir, "new_photo.jpg")
        createTestJpeg(originalFile, 800, 600)

        val existingFile = File(outputDir, "existing_card.jpg")
        existingFile.writeText("PRE-EXISTING PHOTO CONTENT THAT MUST NOT BE DELETED")
        val existingSize = existingFile.length()

        val result = processor.processFile(originalFile, customFileName = "existing_card.jpg")
        assertTrue("Must fail when target file already exists", result.isFailure)
        assertTrue(result.exceptionOrNull() is ImageProcessingException.FileAlreadyExists)

        // Assert existing file was NOT modified or deleted
        assertTrue(existingFile.exists())
        assertEquals("Existing file size must not change", existingSize, existingFile.length())
        assertEquals("PRE-EXISTING PHOTO CONTENT THAT MUST NOT BE DELETED", existingFile.readText())
    }

    @Test
    fun `forbids processing when source and target point to the same file`() = runBlocking {
        val sameFile = File(outputDir, "same_file.jpg")
        createTestJpeg(sameFile, 800, 600)

        // Pass sameFile as both source and target in outputDir
        val result = processor.processFile(sameFile, customFileName = "same_file.jpg")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ImageProcessingException.InvalidImage)
    }

    @Test
    fun `corrupt and non-image files return failure without creating corrupt files`() = runBlocking {
        val corruptFile = File(inputDir, "broken_image.jpg")
        corruptFile.writeText("THIS IS NOT A VALID JPEG OR IMAGE STREAM AT ALL")

        val result = processor.processFile(corruptFile, ImageProfile.SpeciesIdentification)
        assertTrue("Must fail gracefully for corrupt files", result.isFailure)
        assertTrue(result.exceptionOrNull() is ImageProcessingException)

        val leftoverFiles = outputDir.listFiles() ?: emptyArray()
        assertEquals("Output directory must be empty after failed processing", 0, leftoverFiles.size)
    }

    @Test
    fun `failed compression cleans up only temporary files and leaves output directory intact`() = runBlocking {
        val validFile = File(inputDir, "valid_for_failing_engine.jpg")
        createTestJpeg(validFile, 600, 400)

        // Create an existing photo in output directory to ensure it is not touched
        val existingPreciousPhoto = File(outputDir, "precious_plant.jpg")
        existingPreciousPhoto.writeText("UNTOUCHED PLANT PHOTO")

        val failingEngine = object : ImageDecoderEngine by engine {
            override fun compressToFile(
                bitmapHolder: DecodedBitmapHolder,
                format: ImageFormat,
                quality: Int,
                destinationFile: File
            ): Boolean {
                destinationFile.writeText("partial garbage")
                return false
            }
        }

        val failingProcessor = ImageProcessor(outputDirectory = outputDir, engine = failingEngine)
        val result = failingProcessor.processFile(validFile, ImageProfile.SpeciesIdentification, customFileName = "failed_task.jpg")

        assertTrue(result.isFailure)

        // Assert only the existing photo remains; temporary file is cleaned up, failed target is not created
        val files = outputDir.listFiles()?.map { it.name } ?: emptyList()
        assertEquals(listOf("precious_plant.jpg"), files)
        assertEquals("UNTOUCHED PLANT PHOTO", existingPreciousPhoto.readText())
    }

    @Test
    fun `coroutine cancellation propagates without being swallowed`() = runBlocking {
        val originalFile = File(inputDir, "cancel_test.jpg")
        createTestJpeg(originalFile, 1200, 800)

        var cancelledCaught = false
        val job = launch {
            try {
                // Cancel current scope before/during execution
                cancel("User left the screen")
                processor.processFile(originalFile)
            } catch (e: CancellationException) {
                cancelledCaught = true
                throw e
            }
        }
        job.join()
        assertTrue("CancellationException must be rethrown and propagate", cancelledCaught)
    }

    @Test
    fun `concurrent parallel saves to same custom file name guarantee zero overwrites and exactly one winner`() = runBlocking {
        val originalFile = File(inputDir, "parallel_source.jpg")
        createTestJpeg(originalFile, 800, 600)

        val concurrentCount = 20
        val targetName = "parallel_target.jpg"

        // Launch concurrent attempts on Dispatchers.IO
        val deferreds = (1..concurrentCount).map { index ->
            async(Dispatchers.IO) {
                processor.processFile(originalFile, customFileName = targetName)
            }
        }

        val results = deferreds.map { it.await() }
        val successes = results.filter { it.isSuccess }
        val failures = results.filter { it.isFailure }

        assertEquals("Exactly one concurrent operation must succeed", 1, successes.size)
        assertEquals("All other concurrent operations must fail", concurrentCount - 1, failures.size)

        failures.forEach { failure ->
            val ex = failure.exceptionOrNull()
            assertTrue(
                "Failure must be FileAlreadyExists but was $ex",
                ex is ImageProcessingException.FileAlreadyExists
            )
        }

        val targetFile = File(outputDir, targetName)
        assertTrue("Target file must exist", targetFile.exists())
        assertTrue("Target file must not be empty", targetFile.length() > 0)

        // Verify no leftover .tmp files
        val tmpFiles = outputDir.listFiles()?.filter { it.name.contains(".tmp") } ?: emptyList()
        assertEquals("No temporary files should be left over", 0, tmpFiles.size)
    }

    @Test
    fun `existing photo is never overwritten when save attempted with duplicate custom name`() = runBlocking {
        val originalFile = File(inputDir, "source_plant.jpg")
        createTestJpeg(originalFile, 500, 500)

        val existingTarget = File(outputDir, "untouchable_plant.jpg")
        val originalContent = "CRITICAL_ORIGINAL_PHOTO_BYTES_NEVER_OVERWRITE"
        existingTarget.writeText(originalContent)
        val originalLastModified = existingTarget.lastModified()

        val result = processor.processFile(originalFile, customFileName = "untouchable_plant.jpg")

        assertTrue("Must fail when target already exists", result.isFailure)
        assertTrue(result.exceptionOrNull() is ImageProcessingException.FileAlreadyExists)

        // Verify the original file content and integrity remain 100% untouched
        assertEquals(originalContent, existingTarget.readText())
        assertEquals(originalLastModified, existingTarget.lastModified())
    }

    @Test
    fun `concurrent auto-generated names never collide and all succeed`() = runBlocking {
        val originalFile = File(inputDir, "auto_source.jpg")
        createTestJpeg(originalFile, 600, 400)

        val count = 15
        val deferreds = (1..count).map {
            async(Dispatchers.IO) {
                processor.processFile(originalFile, customFileName = null)
            }
        }

        val results = deferreds.map { it.await() }
        val successes = results.filter { it.isSuccess }

        assertEquals("All auto-generated saves must succeed", count, successes.size)

        val uniqueFiles = successes.map { it.getOrThrow().file.name }.toSet()
        assertEquals("Each save must produce a distinct unique file", count, uniqueFiles.size)
    }
}
