package com.example.imagecompressor.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.imagecompressor.domain.CompressionOptions
import com.example.imagecompressor.domain.CompressionResult
import com.example.imagecompressor.domain.ImageCompressor
import com.example.imagecompressor.domain.ImageItem
import com.example.imagecompressor.domain.ImageRepository

sealed class PreviewState {
    data object Loading : PreviewState()
    data class Success(val image: ImageItem, val result: CompressionResult) : PreviewState()
    data class Error(val message: String) : PreviewState()
}

@Composable
fun PreviewScreen(
    bucketId: Long,
    options: CompressionOptions,
    onProceed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<PreviewState>(PreviewState.Loading) }

    LaunchedEffect(bucketId, options) {
        val repository = ImageRepository(context.contentResolver)
        val compressor = ImageCompressor(context)

        val images = repository.getImagesInFolder(bucketId)
            .filter { it.isJpeg || (it.isPng && options.convertPng) }

        if (images.isEmpty()) {
            state = PreviewState.Error("No processable images in folder")
            return@LaunchedEffect
        }

        val sample = images.random()
        val result = compressor.compressImage(sample, options)

        state = if (result.success) {
            PreviewState.Success(sample, result)
        } else {
            PreviewState.Error(result.error ?: "Compression failed")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        when (val s = state) {
            is PreviewState.Loading -> LoadingContent()
            is PreviewState.Success -> SuccessContent(s.image, s.result, onProceed, onBack)
            is PreviewState.Error -> ErrorContent(s.message, onBack)
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Compressing sample image...")
        }
    }
}

@Composable
private fun SuccessContent(
    image: ImageItem,
    result: CompressionResult,
    onProceed: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Sample Preview",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = image.name,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        SizeRow("Original", result.originalSize)
        SizeRow("Compressed", result.compressedSize)

        Spacer(modifier = Modifier.height(16.dp))

        val savingsKb = result.savingsBytes / 1024
        val savingsPct = (result.savingsPercent * 100).toInt()
        val savingsColor = if (savingsPct >= 10) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }

        Text(
            text = "Savings: ${savingsKb}KB ($savingsPct%)",
            style = MaterialTheme.typography.titleLarge,
            color = savingsColor
        )

        if (savingsPct < 10) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Low savings - image may already be well-compressed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            Button(
                onClick = onProceed,
                modifier = Modifier.weight(1f)
            ) {
                Text("Compress All")
            }
        }
    }
}

@Composable
private fun SizeRow(label: String, bytes: Long) {
    val kb = bytes / 1024
    val mb = bytes / (1024 * 1024)
    val display = if (mb > 0) "${mb}MB" else "${kb}KB"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(display, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorContent(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(message)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}
