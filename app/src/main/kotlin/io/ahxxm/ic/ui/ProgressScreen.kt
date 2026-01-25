package io.ahxxm.ic.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.ahxxm.ic.CompressionService
import io.ahxxm.ic.domain.ImageCompressionPreview

sealed class ProgressState {
    data object RequestingNotificationPermission : ProgressState()
    data object RequestingWriteAccess : ProgressState()
    data object WaitingForApproval : ProgressState()
    data object Compressing : ProgressState()
    data object Complete : ProgressState()
    data class Error(val message: String) : ProgressState()
}

@Composable
fun ProgressScreen(
    selectedPreviews: List<ImageCompressionPreview>,
    onComplete: (count: Int, savedBytes: Long) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<ProgressState>(ProgressState.RequestingNotificationPermission) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed regardless of result - notification is nice-to-have
        state = ProgressState.RequestingWriteAccess
    }

    val writeRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            state = ProgressState.Compressing
            CompressionService.startSave(context, selectedPreviews)
        } else {
            state = ProgressState.Error("Write access denied")
        }
    }

    // Listen for service completion via broadcast
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                state = ProgressState.Complete
            }
        }
        val filter = IntentFilter(CompressionService.ACTION_SAVE_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Fallback polling in case broadcast is missed
    LaunchedEffect(state) {
        if (state == ProgressState.Compressing) {
            while (state == ProgressState.Compressing) {
                kotlinx.coroutines.delay(500)
                if (CompressionService.saveResult != null) {
                    state = ProgressState.Complete
                }
            }
        }
    }

    // Handle state transitions
    LaunchedEffect(state) {
        when (state) {
            ProgressState.RequestingNotificationPermission -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        state = ProgressState.RequestingWriteAccess
                    } else {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    state = ProgressState.RequestingWriteAccess
                }
            }
            ProgressState.RequestingWriteAccess -> {
                val uris = selectedPreviews.map { it.image.uri }
                val writeRequest = MediaStore.createWriteRequest(context.contentResolver, uris)
                state = ProgressState.WaitingForApproval
                writeRequestLauncher.launch(IntentSenderRequest.Builder(writeRequest).build())
            }
            ProgressState.Complete -> {
                val result = CompressionService.saveResult
                if (result != null) {
                    onComplete(result.count, result.savedBytes)
                } else {
                    onComplete(selectedPreviews.size, selectedPreviews.sumOf { it.savingsBytes })
                }
            }
            else -> {}
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val s = state) {
            ProgressState.RequestingNotificationPermission,
            ProgressState.RequestingWriteAccess,
            ProgressState.WaitingForApproval -> {
                Text("Preparing...", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
            }
            ProgressState.Compressing -> {
                Text("Replacing originals...", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Check notification for progress",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ProgressState.Complete -> {
                Text("Complete!", style = MaterialTheme.typography.titleMedium)
            }
            is ProgressState.Error -> {
                Text("Error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Text(s.message)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onCancel) { Text("Back") }
            }
        }
    }
}
