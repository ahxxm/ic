package io.ahxxm.ic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.ahxxm.ic.domain.CompressionOptions
import io.ahxxm.ic.domain.ImageCompressionPreview
import io.ahxxm.ic.domain.ImageCompressor
import io.ahxxm.ic.domain.ImageItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CompressionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_SAVE

        when (mode) {
            MODE_COMPRESS -> handleCompress()
            MODE_SAVE -> handleSave()
            else -> stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun handleCompress() {
        val images = pendingImages ?: run { stopSelf(); return }
        val options = pendingOptions ?: run { stopSelf(); return }

        startForeground(NOTIFICATION_ID, buildNotification("Compressing...", 0, images.size))

        scope.launch {
            val compressor = ImageCompressor(this@CompressionService)
            val results = mutableListOf<ImageCompressionPreview>()

            images.forEachIndexed { index, image ->
                _compressionProgress.value = (index + 1) to images.size
                updateNotification("Compressing...", index + 1, images.size)
                val result = compressor.compressImage(image, options)
                if (result.success) {
                    results.add(
                        ImageCompressionPreview(
                            image = image,
                            originalSize = result.originalSize,
                            compressedSize = result.compressedSize,
                            tempFile = result.tempFile
                        )
                    )
                }
            }

            _compressionResults.value = results.sortedByDescending { it.savingsBytes }
            pendingImages = null
            pendingOptions = null

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun handleSave() {
        val items = pendingSaveItems ?: run { stopSelf(); return }

        startForeground(NOTIFICATION_ID, buildNotification("Saving...", 0, items.size))

        scope.launch {
            var successCount = 0
            var totalSaved = 0L

            items.forEachIndexed { index, preview ->
                val success = overwriteImage(preview)
                if (success) {
                    successCount++
                    totalSaved += preview.savingsBytes
                }
                preview.tempFile?.delete()
                updateNotification("Saving...", index + 1, items.size)
            }

            _saveResult.value = SaveResult(successCount, totalSaved)
            pendingSaveItems = null

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun overwriteImage(preview: ImageCompressionPreview): Boolean {
        val tempFile = preview.tempFile ?: return false
        return try {
            contentResolver.openOutputStream(preview.image.uri, "wt")?.use { out ->
                tempFile.inputStream().use { it.copyTo(out) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Compression Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows image compression progress"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, current: Int, total: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$current / $total")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setProgress(total, current, false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, current: Int, total: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, current, total))
    }

    data class SaveResult(val count: Int, val savedBytes: Long)

    companion object {
        private const val CHANNEL_ID = "compression_progress"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_MODE = "mode"
        private const val MODE_COMPRESS = "compress"
        private const val MODE_SAVE = "save"

        var pendingImages: List<ImageItem>? = null
        var pendingOptions: CompressionOptions? = null
        var pendingSaveItems: List<ImageCompressionPreview>? = null

        private val _compressionProgress = MutableStateFlow<Pair<Int, Int>?>(null)
        val compressionProgress: StateFlow<Pair<Int, Int>?> = _compressionProgress.asStateFlow()

        private val _compressionResults = MutableStateFlow<List<ImageCompressionPreview>?>(null)
        val compressionResults: StateFlow<List<ImageCompressionPreview>?> = _compressionResults.asStateFlow()

        private val _saveResult = MutableStateFlow<SaveResult?>(null)
        val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

        fun startCompression(context: Context, images: List<ImageItem>, options: CompressionOptions) {
            pendingImages = images
            pendingOptions = options
            _compressionProgress.value = 0 to images.size
            _compressionResults.value = null
            context.startForegroundService(
                Intent(context, CompressionService::class.java).putExtra(EXTRA_MODE, MODE_COMPRESS)
            )
        }

        fun startSave(context: Context, items: List<ImageCompressionPreview>) {
            pendingSaveItems = items
            _saveResult.value = null
            context.startForegroundService(
                Intent(context, CompressionService::class.java).putExtra(EXTRA_MODE, MODE_SAVE)
            )
        }
    }
}
