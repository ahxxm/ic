package com.example.imagecompressor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.imagecompressor.domain.CompressionOptions
import com.example.imagecompressor.domain.ImageCompressor
import com.example.imagecompressor.domain.ImageCompressionPreview
import com.example.imagecompressor.domain.ImageRepository

sealed class SavingsPreviewState {
    data class Compressing(val current: Int, val total: Int) : SavingsPreviewState()
    data class Ready(val previews: List<ImageCompressionPreview>) : SavingsPreviewState()
    data class Error(val message: String) : SavingsPreviewState()
}

@Composable
fun SavingsPreviewScreen(
    bucketId: Long,
    options: CompressionOptions,
    onConfirm: (List<ImageCompressionPreview>) -> Unit,
    onBack: () -> Unit,
    onImageClick: (ImageCompressionPreview) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<SavingsPreviewState>(SavingsPreviewState.Compressing(0, 0)) }
    var selections by remember { mutableStateOf<List<Boolean>>(emptyList()) }

    LaunchedEffect(bucketId, options) {
        val repository = ImageRepository(context.contentResolver)
        val compressor = ImageCompressor(context)

        val images = repository.getImagesInFolder(bucketId)
            .filter { it.isJpeg || (it.isPng && options.convertPng) }

        if (images.isEmpty()) {
            state = SavingsPreviewState.Error("No processable images")
            return@LaunchedEffect
        }

        state = SavingsPreviewState.Compressing(0, images.size)

        val results = mutableListOf<ImageCompressionPreview>()
        images.forEachIndexed { index, image ->
            state = SavingsPreviewState.Compressing(index + 1, images.size)
            val result = compressor.compressImage(image, options)
            if (result.success) {
                val preview = ImageCompressionPreview(
                    image = image,
                    originalSize = result.originalSize,
                    compressedSize = result.compressedSize,
                    tempFile = result.tempFile,
                    selected = true
                )
                results.add(preview)
            }
        }

        val sorted = results.sortedByDescending { it.savingsBytes }
        selections = sorted.map { preview ->
            !ImageCompressionPreview.shouldAutoDeselect(preview.savingsPercent, preview.savingsBytes)
        }
        state = SavingsPreviewState.Ready(sorted)
    }

    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        when (val s = state) {
            is SavingsPreviewState.Compressing -> CompressingContent(s.current, s.total)
            is SavingsPreviewState.Ready -> ReadyContent(
                previews = s.previews,
                selections = selections,
                onSelectionChange = { index, selected ->
                    selections = selections.toMutableList().apply { set(index, selected) }
                },
                onImageClick = { index -> onImageClick(s.previews[index]) },
                onConfirm = {
                    val selected = s.previews.filterIndexed { i, _ -> selections[i] }
                    onConfirm(selected)
                },
                onBack = onBack
            )
            is SavingsPreviewState.Error -> ErrorContent(s.message, onBack)
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
        Text("Analyzing images...", style = MaterialTheme.typography.titleMedium)
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
private fun ReadyContent(
    previews: List<ImageCompressionPreview>,
    selections: List<Boolean>,
    onSelectionChange: (Int, Boolean) -> Unit,
    onImageClick: (Int) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val selectedCount = selections.count { it }
    val totalSavings = previews.filterIndexed { i, _ -> selections[i] }
        .sumOf { it.savingsBytes }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Savings Preview", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        val savingsMb = totalSavings / (1024 * 1024)
        val savingsKb = (totalSavings % (1024 * 1024)) / 1024
        val savingsDisplay = if (savingsMb > 0) "${savingsMb}.${savingsKb / 100}MB" else "${totalSavings / 1024}KB"

        Text(
            text = "$selectedCount selected | $savingsDisplay savings",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(previews) { index, preview ->
                PreviewRow(
                    preview = preview,
                    selected = selections[index],
                    onSelectedChange = { onSelectionChange(index, it) },
                    onClick = { onImageClick(index) }
                )
                if (index < previews.lastIndex) {
                    HorizontalDivider()
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
            Button(
                onClick = onConfirm,
                enabled = selectedCount > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text("Confirm")
            }
        }
    }
}

@Composable
private fun PreviewRow(
    preview: ImageCompressionPreview,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val savingsKb = preview.savingsBytes / 1024
    val savingsPct = (preview.savingsPercent * 100).toInt()
    val lowSavings = ImageCompressionPreview.shouldAutoDeselect(preview.savingsPercent, preview.savingsBytes)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = onSelectedChange)

        AsyncImage(
            model = preview.image.uri,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preview.image.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (lowSavings) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${preview.originalSize / 1024}KB → ${preview.compressedSize / 1024}KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "-${savingsKb}KB ($savingsPct%)",
            style = MaterialTheme.typography.bodyMedium,
            color = if (lowSavings) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
        )
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
