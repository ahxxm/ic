package com.example.imagecompressor.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCompressionPreviewTest {

    @Test
    fun `shouldAutoDeselect returns true when both below threshold`() {
        // 5% savings, 40KB - both below 10% and 50KB thresholds
        assertTrue(ImageCompressionPreview.shouldAutoDeselect(0.05f, 40_000))
    }

    @Test
    fun `shouldAutoDeselect returns false when percent above threshold`() {
        // 15% savings, 40KB - percent above 10% threshold
        assertFalse(ImageCompressionPreview.shouldAutoDeselect(0.15f, 40_000))
    }

    @Test
    fun `shouldAutoDeselect returns false when bytes above threshold`() {
        // 5% savings, 60KB - bytes above 50KB threshold
        assertFalse(ImageCompressionPreview.shouldAutoDeselect(0.05f, 60_000))
    }

    @Test
    fun `shouldAutoDeselect returns false when both above threshold`() {
        // 15% savings, 60KB - both above thresholds
        assertFalse(ImageCompressionPreview.shouldAutoDeselect(0.15f, 60_000))
    }

    @Test
    fun `shouldAutoDeselect at exact percent threshold`() {
        // Exactly 10% - not less than, so should NOT auto-deselect
        assertFalse(ImageCompressionPreview.shouldAutoDeselect(0.10f, 40_000))
    }

    @Test
    fun `shouldAutoDeselect at exact bytes threshold`() {
        // Exactly 50KB - not less than, so should NOT auto-deselect
        assertFalse(ImageCompressionPreview.shouldAutoDeselect(0.05f, 50_000))
    }

    @Test
    fun `shouldAutoDeselect just below both thresholds`() {
        // 9.99% and 49,999 bytes - both just under
        assertTrue(ImageCompressionPreview.shouldAutoDeselect(0.0999f, 49_999))
    }
}

class CompressionResultTest {

    @Test
    fun `savingsBytes calculates correctly`() {
        val result = CompressionResult(
            originalSize = 100_000,
            compressedSize = 75_000,
            tempFile = null,
            success = true
        )
        assertEquals(25_000, result.savingsBytes)
    }

    @Test
    fun `savingsBytes handles negative savings`() {
        // Compressed larger than original (rare but possible)
        val result = CompressionResult(
            originalSize = 100_000,
            compressedSize = 110_000,
            tempFile = null,
            success = true
        )
        assertEquals(-10_000, result.savingsBytes)
    }

    @Test
    fun `savingsPercent calculates correctly`() {
        val result = CompressionResult(
            originalSize = 100_000,
            compressedSize = 80_000,
            tempFile = null,
            success = true
        )
        assertEquals(0.20f, result.savingsPercent, 0.001f)
    }

    @Test
    fun `savingsPercent handles zero original size`() {
        val result = CompressionResult(
            originalSize = 0,
            compressedSize = 0,
            tempFile = null,
            success = true
        )
        assertEquals(0f, result.savingsPercent, 0.001f)
    }

    @Test
    fun `savingsPercent handles negative savings`() {
        val result = CompressionResult(
            originalSize = 100_000,
            compressedSize = 120_000,
            tempFile = null,
            success = true
        )
        assertEquals(-0.20f, result.savingsPercent, 0.001f)
    }
}
