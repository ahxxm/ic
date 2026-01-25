package io.ahxxm.ic.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.ahxxm.ic.domain.CompressionOptions
import io.ahxxm.ic.domain.CompressionResult
import io.ahxxm.ic.domain.Encoder
import io.ahxxm.ic.domain.ImageItem
import io.ahxxm.ic.domain.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsScreen(
    bucketId: Long,
    onConfirm: (CompressionOptions) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    folderName: String = "",
    viewModel: OptionsViewModel = viewModel(),
) {
    val sampleState by viewModel.sampleState.collectAsState()
    val qualityLevel by viewModel.qualityLevel.collectAsState()
    val preserveExif by viewModel.preserveExif.collectAsState()
    val convertPng by viewModel.convertPng.collectAsState()
    val encoder by viewModel.encoder.collectAsState()

    LaunchedEffect(bucketId) {
        viewModel.loadSampleImage(bucketId)
    }

    LaunchedEffect(Unit) {
        viewModel.navigateBack.collect { onBack() }
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
            SampleStateContent(sampleState)

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Quality
            LabeledChipRow(label = "Quality") {
                QualityLevel.entries.forEach { level ->
                    FilterChip(
                        selected = qualityLevel == level,
                        onClick = { viewModel.qualityLevel.value = level },
                        label = { Text("${level.label} q${level.qualityFor(encoder)}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Encoder
            LabeledChipRow(label = "Encoder") {
                FilterChip(
                    selected = encoder == Encoder.MOZJPEG,
                    onClick = { viewModel.encoder.value = Encoder.MOZJPEG },
                    label = { Text("mozjpeg") }
                )
                FilterChip(
                    selected = encoder == Encoder.JPEGLI,
                    onClick = { viewModel.encoder.value = Encoder.JPEGLI },
                    label = { Text("jpegli") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Checkboxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = preserveExif,
                        onCheckedChange = { viewModel.preserveExif.value = it }
                    )
                    Text("Keep EXIF", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = convertPng,
                        onCheckedChange = { viewModel.setConvertPng(bucketId, it) }
                    )
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
                onClick = { onConfirm(viewModel.buildOptions()) },
                enabled = sampleState is SampleState.Ready,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Compress")
            }
        }
    }
}

@Composable
private fun LabeledChipRow(label: String, chips: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        chips()
    }
}

@Composable
private fun SampleStateContent(state: SampleState) {
    when (state) {
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
        is SampleState.Error -> {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
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
                text = "${formatBytes(result.originalSize)} -> ${formatBytes(result.compressedSize)}",
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
