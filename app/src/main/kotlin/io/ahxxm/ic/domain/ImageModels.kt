package io.ahxxm.ic.domain

import android.net.Uri

data class FolderSummary(
    val bucketId: Long,
    val name: String,
    val imageCount: Int,
    val totalSizeBytes: Long
)

data class ImageItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String
) {
    val isJpeg: Boolean get() = mimeType == "image/jpeg"
    val isPng: Boolean get() = mimeType == "image/png"
    val isProcessable: Boolean get() = isJpeg || isPng
}

data class CompressionResult(
    val originalSize: Long,
    val compressedSize: Long,
    val tempFile: java.io.File?,
    val success: Boolean,
    val error: String? = null
) {
    val savingsBytes: Long get() = originalSize - compressedSize
    val savingsPercent: Float get() = if (originalSize > 0) savingsBytes.toFloat() / originalSize else 0f
}

data class ImageCompressionPreview(
    val image: ImageItem,
    val originalSize: Long,
    val compressedSize: Long,
    val tempFile: java.io.File?,
    val selected: Boolean
) {
    val savingsBytes: Long get() = originalSize - compressedSize
    val savingsPercent: Float get() = if (originalSize > 0) savingsBytes.toFloat() / originalSize else 0f

    companion object {
        private const val MIN_SAVINGS_PERCENT = 0.10f
        private const val MIN_SAVINGS_BYTES = 50_000L

        fun shouldAutoDeselect(savingsPercent: Float, savingsBytes: Long): Boolean =
            savingsPercent < MIN_SAVINGS_PERCENT && savingsBytes < MIN_SAVINGS_BYTES
    }
}
