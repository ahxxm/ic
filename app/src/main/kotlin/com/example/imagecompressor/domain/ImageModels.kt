package com.example.imagecompressor.domain

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
