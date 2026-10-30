package io.ahxxm.ic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ahxxm.ic.CompressionService
import io.ahxxm.ic.domain.CompressionOptions
import io.ahxxm.ic.domain.ImageCompressionPreview
import io.ahxxm.ic.domain.ImageRepository

@Composable
fun SavingsPreviewScreen(
    bucketId: Long,
    options: CompressionOptions,
    onConfirm: (List<ImageCompressionPreview>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val progress by CompressionService.compressionProgress.collectAsStateWithLifecycle()
    val results by CompressionService.compressionResults.collectAsStateWithLifecycle()

    // Start compression service
    LaunchedEffect(bucketId, options) {
        val repository = ImageRepository(context.contentResolver)
        val allImages = repository.getImagesInFolder(bucketId)
        val images = allImages.filter { it.isJpeg || (it.isPng && options.convertPng) }

        if (images.isEmpty()) {
            val hasPngs = allImages.any { it.isPng }
            errorMessage = if (hasPngs && !options.convertPng) {
                "No JPEG images found.\nEnable 'Convert PNG to JPEG' in options to include ${allImages.count { it.isPng }} PNG files."
            } else {
                "No processable images in this folder."
            }
            return@LaunchedEffect
        }

        CompressionService.startCompression(context, images, options)
    }

    val previews = results
    if (previews != null && errorMessage == null) {
        SavingsReviewScreen(
            previews = previews,
            onConfirm = onConfirm,
            onBack = onBack,
            modifier = modifier
        )
    } else {
        Scaffold(
            topBar = {
                BackTopAppBar(title = "Review Savings", onBack = onBack)
            },
            modifier = modifier
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                val message = errorMessage
                if (message != null) {
                    ErrorContent(message, onBack)
                } else {
                    CompressingContent(progress?.first ?: 0, progress?.second ?: 0)
                }
            }
        }
    }
}

@Composable
private fun CompressingContent(current: Int, total: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Compressing...", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) current.toFloat() / total else 0f },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("$current / $total", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorContent(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Error", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Text(message)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Back") }
    }
}
