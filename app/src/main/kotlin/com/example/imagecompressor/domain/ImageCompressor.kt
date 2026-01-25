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
        private val EXIF_TAGS = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH
        )
    }
}
