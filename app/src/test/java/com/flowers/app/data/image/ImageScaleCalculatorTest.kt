package com.flowers.app.data.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ImageScaleCalculatorTest {

    @Test
    fun `calculateInSampleSize calculates powers of 2 for 12, 50 and 200 MP`() {
        // 12 MP: 4000x3000
        val sample12_species = ImageScaleCalculator.calculateInSampleSize(4000, 3000, 1024)
        val sample12_diagnosis = ImageScaleCalculator.calculateInSampleSize(4000, 3000, 1536)
        assertEquals(2, sample12_species)
        assertEquals(2, sample12_diagnosis)

        // 50 MP: 8192x6144
        val sample50_species = ImageScaleCalculator.calculateInSampleSize(8192, 6144, 1024)
        val sample50_diagnosis = ImageScaleCalculator.calculateInSampleSize(8192, 6144, 1536)
        assertEquals(8, sample50_species)
        assertEquals(4, sample50_diagnosis)

        // 200 MP: 16384x12288 (Samsung S24 Ultra ISOCELL HP2 camera mode)
        val sample200_species = ImageScaleCalculator.calculateInSampleSize(16384, 12288, 1024)
        val sample200_diagnosis = ImageScaleCalculator.calculateInSampleSize(16384, 12288, 1536)
        assertEquals(16, sample200_species)
        assertEquals(8, sample200_diagnosis)

        // Small image (under target): inSampleSize must be 1
        val sampleSmall = ImageScaleCalculator.calculateInSampleSize(800, 600, 1024)
        assertEquals(1, sampleSmall)

        // Always a power of 2: (x & (x - 1)) == 0
        listOf(sample12_species, sample50_species, sample200_species, sampleSmall).forEach { sample ->
            assertTrue("Sample size $sample must be a power of 2", sample > 0 && (sample and (sample - 1)) == 0)
        }
    }

    @Test
    fun `memory estimation proves dramatic RAM reduction avoiding OOM`() {
        // 200 MP raw uncompressed: 16384 * 12288 * 4 bytes = 805,306,368 bytes (~805 MB RAM!)
        val raw200Bytes = ImageScaleCalculator.estimateMemoryUsageBytes(16384, 12288, inSampleSize = 1)
        assertTrue(raw200Bytes > 800_000_000L)

        // 200 MP with inSampleSize=16: 1024 * 768 * 4 bytes = 3,145,728 bytes (~3.1 MB RAM!)
        val sampled200Bytes = ImageScaleCalculator.estimateMemoryUsageBytes(16384, 12288, inSampleSize = 16)
        assertEquals(1024L * 768L * 4L, sampled200Bytes)
        assertTrue("RAM savings should exceed 99%", (raw200Bytes - sampled200Bytes).toDouble() / raw200Bytes > 0.99)

        // 50 MP raw: ~201 MB RAM. With inSampleSize=8: ~3.1 MB RAM (>98% savings)
        val raw50Bytes = ImageScaleCalculator.estimateMemoryUsageBytes(8192, 6144, inSampleSize = 1)
        val sampled50Bytes = ImageScaleCalculator.estimateMemoryUsageBytes(8192, 6144, inSampleSize = 8)
        assertTrue(raw50Bytes > 200_000_000L)
        assertEquals(1024L * 768L * 4L, sampled50Bytes)
        assertTrue("RAM savings should exceed 98%", (raw50Bytes - sampled50Bytes).toDouble() / raw50Bytes > 0.98)

        // 12 MP raw: ~48 MB RAM. With inSampleSize=2: ~12 MB RAM (75% savings)
        val raw12Bytes = ImageScaleCalculator.estimateMemoryUsageBytes(4000, 3000, inSampleSize = 1)
        val sampled12Bytes = ImageScaleCalculator.estimateMemoryUsageBytes(4000, 3000, inSampleSize = 2)
        assertEquals(4000L * 3000L * 4L, raw12Bytes)
        assertEquals(2000L * 1500L * 4L, sampled12Bytes)
        assertEquals(0.75, (raw12Bytes - sampled12Bytes).toDouble() / raw12Bytes, 0.001)
    }

    @Test
    fun `calculateTargetDimensions preserves aspect ratio precisely`() {
        // Landscape 16:9
        val dims16_9 = ImageScaleCalculator.calculateTargetDimensions(3840, 2160, 1024)
        assertEquals(1024, dims16_9.width)
        assertEquals(576, dims16_9.height)
        val originalRatio = 3840f / 2160f
        val scaledRatio = dims16_9.width.toFloat() / dims16_9.height.toFloat()
        assertTrue(abs(originalRatio - scaledRatio) < 0.01)

        // Portrait 3:4 (typical phone camera)
        val dims3_4 = ImageScaleCalculator.calculateTargetDimensions(3000, 4000, 1024)
        assertEquals(768, dims3_4.width)
        assertEquals(1024, dims3_4.height)
        val origPortraitRatio = 3000f / 4000f
        val scaledPortraitRatio = dims3_4.width.toFloat() / dims3_4.height.toFloat()
        assertTrue(abs(origPortraitRatio - scaledPortraitRatio) < 0.01)

        // Square 1:1
        val dimsSquare = ImageScaleCalculator.calculateTargetDimensions(2048, 2048, 1024)
        assertEquals(1024, dimsSquare.width)
        assertEquals(1024, dimsSquare.height)

        // Disease diagnosis target 1536
        val dimsDiagnosis = ImageScaleCalculator.calculateTargetDimensions(4000, 3000, 1536)
        assertEquals(1536, dimsDiagnosis.width)
        assertEquals(1152, dimsDiagnosis.height)
    }

    @Test
    fun `calculateTargetDimensions never upscales small images`() {
        val small = ImageScaleCalculator.calculateTargetDimensions(640, 480, 1024)
        assertEquals(640, small.width)
        assertEquals(480, small.height)
    }

    @Test
    fun `getExifTransform correctly handles all 8 orientations`() {
        // 1: Normal
        val t1 = ImageScaleCalculator.getExifTransform(1)
        assertEquals(0f, t1.rotationDegrees, 0.0f)
        assertFalse(t1.flipHorizontal)
        assertFalse(t1.flipVertical)
        assertFalse(t1.swapsDimensions)

        // 2: Flip Horizontal
        val t2 = ImageScaleCalculator.getExifTransform(2)
        assertEquals(0f, t2.rotationDegrees, 0.0f)
        assertTrue(t2.flipHorizontal)
        assertFalse(t2.flipVertical)
        assertFalse(t2.swapsDimensions)

        // 3: Rotate 180°
        val t3 = ImageScaleCalculator.getExifTransform(3)
        assertEquals(180f, t3.rotationDegrees, 0.0f)
        assertFalse(t3.flipHorizontal)
        assertFalse(t3.flipVertical)
        assertFalse(t3.swapsDimensions)

        // 4: Flip Vertical
        val t4 = ImageScaleCalculator.getExifTransform(4)
        assertEquals(0f, t4.rotationDegrees, 0.0f)
        assertFalse(t4.flipHorizontal)
        assertTrue(t4.flipVertical)
        assertFalse(t4.swapsDimensions)

        // 5: Transpose (90° + Flip Horizontal)
        val t5 = ImageScaleCalculator.getExifTransform(5)
        assertEquals(90f, t5.rotationDegrees, 0.0f)
        assertTrue(t5.flipHorizontal)
        assertFalse(t5.flipVertical)
        assertTrue(t5.swapsDimensions)

        // 6: Rotate 90° CW
        val t6 = ImageScaleCalculator.getExifTransform(6)
        assertEquals(90f, t6.rotationDegrees, 0.0f)
        assertFalse(t6.flipHorizontal)
        assertFalse(t6.flipVertical)
        assertTrue(t6.swapsDimensions)

        // 7: Transverse (270° + Flip Horizontal)
        val t7 = ImageScaleCalculator.getExifTransform(7)
        assertEquals(270f, t7.rotationDegrees, 0.0f)
        assertTrue(t7.flipHorizontal)
        assertFalse(t7.flipVertical)
        assertTrue(t7.swapsDimensions)

        // 8: Rotate 270° CW
        val t8 = ImageScaleCalculator.getExifTransform(8)
        assertEquals(270f, t8.rotationDegrees, 0.0f)
        assertFalse(t8.flipHorizontal)
        assertFalse(t8.flipVertical)
        assertTrue(t8.swapsDimensions)
    }

    @Test
    fun `calculateTargetDimensions accounts for EXIF orientation dimension swapping`() {
        // Landscape photo stored as 4000x3000, but with EXIF 6 (90° CW)
        // Visual image is portrait: width=3000, height=4000
        val dims90 = ImageScaleCalculator.calculateTargetDimensions(4000, 3000, 1024, orientation = 6)
        assertEquals(768, dims90.width)
        assertEquals(1024, dims90.height)

        // Transpose (orientation 5)
        val dimsTranspose = ImageScaleCalculator.calculateTargetDimensions(4000, 3000, 1024, orientation = 5)
        assertEquals(768, dimsTranspose.width)
        assertEquals(1024, dimsTranspose.height)
    }
}
