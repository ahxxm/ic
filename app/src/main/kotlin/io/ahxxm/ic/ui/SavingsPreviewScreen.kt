package io.ahxxm.ic.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.ahxxm.ic.CompressionService
import io.ahxxm.ic.domain.CompressionOptions
import io.ahxxm.ic.domain.ImageCompressionPreview
import io.ahxxm.ic.domain.ImageRepository
import io.ahxxm.ic.domain.formatBytes

sealed class SavingsPreviewState {
    data class Compressing(val current: Int, val total: Int) : SavingsPreviewState()
    data class Ready(val previews: List<ImageCompressionPreview>) : SavingsPreviewState()
    data class Error(val message: String) : SavingsPreviewState()
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var confirmedPreviews by remember { mutableStateOf<List<ImageCompressionPreview>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val progress by CompressionService.compressionProgress.collectAsStateWithLifecycle()
    val results by CompressionService.compressionResults.collectAsStateWithLifecycle()

    // Selections computed synchronously when results changes, mutable for user toggles
    var selections by remember(results) {
        mutableStateOf(
            results?.map { !ImageCompressionPreview.shouldAutoDeselect(it.savingsPercent, it.savingsBytes) }
                ?: emptyList()
        )
    }

    val state: SavingsPreviewState = when {
        errorMessage != null -> SavingsPreviewState.Error(errorMessage!!)
        results != null -> SavingsPreviewState.Ready(results!!)
        progress != null -> SavingsPreviewState.Compressing(progress!!.first, progress!!.second)
        else -> SavingsPreviewState.Compressing(0, 0)
    }

    // Cleanup temp files when leaving screen (unless proceeding to compression)
    DisposableEffect(Unit) {
        onDispose {
            if (confirmedPreviews == null) {
                results?.forEach { it.tempFile?.delete() }
            }
        }
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Savings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            when (val s = state) {
                is SavingsPreviewState.Compressing -> CompressingContent(s.current, s.total)
                is SavingsPreviewState.Ready -> ReadyContent(
                    previews = s.previews,
                    selections = selections,
                    onSelectionChange = { index, selected ->
                        selections = selections.toMutableList().apply { set(index, selected) }
                    },
                    onSelectAll = { selections = List(s.previews.size) { true } },
                    onDeselectAll = { selections = List(s.previews.size) { false } },
                    onSmartSelect = {
                        selections = s.previews.map { preview ->
                            !ImageCompressionPreview.shouldAutoDeselect(preview.savingsPercent, preview.savingsBytes)
                        }
                    },
                    onImageClick = { index -> onImageClick(s.previews[index]) },
                    onConfirm = {
                        val selected = s.previews.filterIndexed { i, _ -> selections[i] }
                        confirmedPreviews = selected
                        onConfirm(selected)
                    }
                )
                is SavingsPreviewState.Error -> ErrorContent(s.message, onBack)
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
private fun ReadyContent(
    previews: List<ImageCompressionPreview>,
    selections: List<Boolean>,
    onSelectionChange: (Int, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSmartSelect: () -> Unit,
    onImageClick: (Int) -> Unit,
    onConfirm: () -> Unit
) {
    val selectedCount = selections.count { it }
    val totalSavings = previews.filterIndexed { i, _ -> selections[i] }
        .sumOf { it.savingsBytes }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "$selectedCount of ${previews.size} selected | ${formatBytes(totalSavings)} savings",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Selection buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onSelectAll) { Text("All") }
            TextButton(onClick = onDeselectAll) { Text("None") }
            TextButton(onClick = onSmartSelect) { Text("Smart") }
        }

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

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Originals will be replaced",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onConfirm,
            enabled = selectedCount > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Apply to $selectedCount Images")
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
