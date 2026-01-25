package io.ahxxm.ic.domain

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageRepository(private val contentResolver: ContentResolver) {

    suspend fun getFolders(): List<FolderSummary> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<Long, MutableFolderData>()

        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE
        )

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val bucketId = cursor.getLong(bucketIdCol)
                val bucketName = cursor.getString(bucketNameCol) ?: "Unknown"
                val size = cursor.getLong(sizeCol)

                val data = folders.getOrPut(bucketId) { MutableFolderData(bucketName) }
                data.imageCount++
                data.totalSize += size
            }
        }

        folders.map { (id, data) ->
            FolderSummary(
                bucketId = id,
                name = data.name,
                imageCount = data.imageCount,
                totalSizeBytes = data.totalSize
            )
        }.sortedByDescending { it.totalSizeBytes }
    }

    suspend fun getImagesInFolder(bucketId: Long): List<ImageItem> = withContext(Dispatchers.IO) {
        val images = mutableListOf<ImageItem>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId.toString())

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(mimeCol) ?: continue
                val name = cursor.getString(nameCol) ?: continue
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                images.add(
                    ImageItem(
                        uri = uri,
                        name = name,
                        sizeBytes = cursor.getLong(sizeCol),
                        mimeType = mimeType
                    )
                )
            }
        }

        images
    }

    private class MutableFolderData(val name: String) {
        var imageCount: Int = 0
        var totalSize: Long = 0L
    }
}
