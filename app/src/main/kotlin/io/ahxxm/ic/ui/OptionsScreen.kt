package io.ahxxm.ic.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.ahxxm.ic.domain.CompressionOptions
import io.ahxxm.ic.domain.CompressionResult
import io.ahxxm.ic.domain.Encoder
import io.ahxxm.ic.domain.ImageCompressor
import io.ahxxm.ic.domain.ImageItem
import io.ahxxm.ic.domain.ImageRepository
import io.ahxxm.ic.domain.formatBytes
import kotlinx.coroutines.delay
import java.io.File
private enum class QualityLevel(val label: String) {
    OK("ok"),
    GOOD("good"),
    GREAT("great");

    fun qualityFor(encoder: Encoder): Int = when (this) {
        OK -> if (encoder == Encoder.MOZJPEG) 75 else 65
        GOOD -> if (encoder == Encoder.MOZJPEG) 85 else 75
        GREAT -> if (encoder == Encoder.MOZJPEG) 90 else 82
    }
}

private sealed class SampleState {
    data object Loading : SampleState()
    data class Ready(val image: ImageItem, val result: CompressionResult) : SampleState()
    data class NoImages(val message: String) : SampleState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(
    bucketId: Long,
    folderName: String = "",
    onConfirm: (CompressionOptions) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var qualityLevel by rememberSaveable { mutableStateOf(QualityLevel.GOOD) }
    var preserveExif by rememberSaveable { mutableStateOf(true) }
    var convertPng by rememberSaveable { mutableStateOf(false) }
    var encoder by rememberSaveable { mutableStateOf(Encoder.MOZJPEG) }

    var sampleImage by remember { mutableStateOf<ImageItem?>(null) }
    var sampleState by remember { mutableStateOf<SampleState>(SampleState.Loading) }
    var currentTempFile by remember { mutableStateOf<File?>(null) }

    // Select sample image based on convertPng setting
    LaunchedEffect(bucketId, convertPng) {
        val repository = ImageRepository(context.contentResolver)
        val allImages = repository.getImagesInFolder(bucketId)
        val candidates = if (convertPng) {
            allImages.filter { it.isJpeg || it.isPng }
        } else {
            allImages.filter { it.isJpeg }
        }

        val current = sampleImage
        // Keep current sample if still valid for this mode
        if (current != null && candidates.any { it.uri == current.uri }) {
            return@LaunchedEffect
        }

        sampleImage = candidates.randomOrNull()
        if (sampleImage == null) {
            val msg = if (!convertPng && allImages.any { it.isPng }) {
                "No JPEG images. Enable 'Include PNG' to preview."
            } else {
                "No images in this folder"
            }
            sampleState = SampleState.NoImages(msg)
        }
    }

    // Recompress when compression options change (debounced)
    val quality = qualityLevel.qualityFor(encoder)
    LaunchedEffect(sampleImage, quality, encoder, preserveExif) {
        val sample = sampleImage ?: return@LaunchedEffect

        sampleState = SampleState.Loading
        delay(300) // debounce

        currentTempFile?.delete()
        val compressor = ImageCompressor(context)
        val options = CompressionOptions(
            quality = quality,
            preserveExif = preserveExif,
            convertPng = convertPng,
            encoder = encoder
        )
        val result = compressor.compressImage(sample, options)
        currentTempFile = result.tempFile

        sampleState = if (result.success) {
            SampleState.Ready(sample, result)
        } else {
            SampleState.NoImages(result.error ?: "Compression failed")
        }
    }

    // Cleanup temp file on dispose
    DisposableEffect(Unit) {
        onDispose { currentTempFile?.delete() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folderName.ifEmpty { "Options" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Live preview at top
            when (val state = sampleState) {
                is SampleState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Compressing...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is SampleState.Ready -> SamplePreview(state.image, state.result)
                is SampleState.NoImages -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Quality level
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Quality", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                QualityLevel.entries.forEach { level ->
                    FilterChip(
                        selected = qualityLevel == level,
                        onClick = { qualityLevel = level },
                        label = { Text("${level.label} q${level.qualityFor(encoder)}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Encoder selection
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Encoder", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                FilterChip(
                    selected = encoder == Encoder.MOZJPEG,
                    onClick = { encoder = Encoder.MOZJPEG },
                    label = { Text("mozjpeg") }
                )
                FilterChip(
                    selected = encoder == Encoder.JPEGLI,
                    onClick = { encoder = Encoder.JPEGLI },
                    label = { Text("jpegli") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Checkboxes row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = preserveExif, onCheckedChange = { preserveExif = it })
                    Text("Keep EXIF", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = convertPng, onCheckedChange = { convertPng = it })
                    Text("Include PNG", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Originals won't be modified yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onConfirm(
                        CompressionOptions(
                            quality = quality,
                            preserveExif = preserveExif,
                            convertPng = convertPng,
                            encoder = encoder
                        )
                    )
                },
                enabled = sampleState is SampleState.Ready,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Compress")
            }
        }
    }
}

@Composable
private fun SamplePreview(image: ImageItem, result: CompressionResult) {
    val savingsPct = (result.savingsPercent * 100).toInt()
    val color = if (savingsPct >= 10) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = image.uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = image.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${formatBytes(result.originalSize)} → ${formatBytes(result.compressedSize)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "-$savingsPct%",
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
        }
    }
}
