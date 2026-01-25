package com.example.imagecompressor.domain

import android.content.Context
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.awxkee.aire.Aire
import io.github.awxkee.jpegli.coder.JpegliCoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class ImageCompressor(private val context: Context) {

    suspend fun compressImage(
        image: ImageItem,
        options: CompressionOptions
    ): CompressionResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(image.uri)
                ?: return@withContext CompressionResult(
                    originalSize = image.sizeBytes,
                    compressedSize = 0,
                    tempFile = null,
                    success = false,
                    error = "Cannot open image"
                )

            val bitmap = inputStream.use { BitmapFactory.decodeStream(it) }
                ?: return@withContext CompressionResult(
                    originalSize = image.sizeBytes,
                    compressedSize = 0,
                    tempFile = null,
                    success = false,
                    error = "Cannot decode image"
                )

            val tempFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
            val compressed = when (options.encoder) {
                Encoder.MOZJPEG -> Aire.mozjpeg(bitmap, options.quality)
                Encoder.JPEGLI -> ByteArrayOutputStream().use { out ->
                    JpegliCoder.compress(
                        bitmap = bitmap,
                        quality = options.quality,
                        progressive = true,
                        outputStream = out
                    )
                    out.toByteArray()
                }
            }
            tempFile.writeBytes(compressed)
            bitmap.recycle()

            if (options.preserveExif && image.isJpeg) {
                copyExifData(image, tempFile)
            }

            CompressionResult(
                originalSize = image.sizeBytes,
                compressedSize = tempFile.length(),
                tempFile = tempFile,
                success = true
            )
        } catch (e: Exception) {
            CompressionResult(
                originalSize = image.sizeBytes,
                compressedSize = 0,
                tempFile = null,
                success = false,
                error = e.message
            )
        }
    }

    private fun copyExifData(image: ImageItem, destFile: File) {
        try {
            val inputStream = context.contentResolver.openInputStream(image.uri) ?: return
            val sourceExif = inputStream.use { ExifInterface(it) }
            val destExif = ExifInterface(destFile)

            EXIF_TAGS.forEach { tag ->
                sourceExif.getAttribute(tag)?.let { value ->
                    destExif.setAttribute(tag, value)
                }
            }
            destExif.saveAttributes()
        } catch (_: Exception) {
            // EXIF copy is best-effort
        }
    }

    companion object {
        // Excluded: thumbnail (stale after recompression), maker notes (proprietary blobs)
        private val EXCLUDED_PREFIXES = listOf("TAG_THUMBNAIL_", "TAG_MAKER_NOTE")

        private val EXIF_TAGS: List<String> by lazy {
            ExifInterface::class.java.fields
                .filter { field ->
                    field.name.startsWith("TAG_") &&
                    field.type == String::class.java &&
                    EXCLUDED_PREFIXES.none { prefix -> field.name.startsWith(prefix) }
                }
                .mapNotNull { runCatching { it.get(null) as? String }.getOrNull() }
        }
    }
}
