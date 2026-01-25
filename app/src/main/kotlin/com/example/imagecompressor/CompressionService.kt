package com.example.imagecompressor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.imagecompressor.domain.ImageCompressionPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CompressionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val items = pendingItems ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(0, items.size))

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
                updateNotification(index + 1, items.size)
            }

            completionResult = CompletionResult(successCount, totalSaved)
            pendingItems = null

            stopForeground(STOP_FOREGROUND_REMOVE)
            sendBroadcast(Intent(ACTION_COMPRESSION_COMPLETE))
            stopSelf()
        }

        return START_NOT_STICKY
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

    private fun buildNotification(current: Int, total: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Compressing images")
            .setContentText("$current / $total")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setProgress(total, current, false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(current: Int, total: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(current, total))
    }

    data class CompletionResult(val count: Int, val savedBytes: Long)

    companion object {
        private const val CHANNEL_ID = "compression_progress"
        private const val NOTIFICATION_ID = 1

        const val ACTION_COMPRESSION_COMPLETE = "com.example.imagecompressor.COMPRESSION_COMPLETE"

        var pendingItems: List<ImageCompressionPreview>? = null
        var completionResult: CompletionResult? = null

        fun start(context: Context, items: List<ImageCompressionPreview>) {
            pendingItems = items
            completionResult = null
            context.startForegroundService(Intent(context, CompressionService::class.java))
        }
    }
}
