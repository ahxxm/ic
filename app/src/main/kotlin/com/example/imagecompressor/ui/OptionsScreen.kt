package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.imagecompressor.domain.CompressionOptions
import com.example.imagecompressor.domain.Encoder
import kotlin.math.roundToInt

@Composable
fun OptionsScreen(
    folderName: String = "",
    onConfirm: (CompressionOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    var quality by rememberSaveable { mutableFloatStateOf(80f) }
    var preserveExif by rememberSaveable { mutableStateOf(true) }
    var convertPng by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Compression Options",
            style = MaterialTheme.typography.headlineMedium
        )
        if (folderName.isNotEmpty()) {
            Text(
                text = folderName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quality slider
        Text(
            text = "Quality: ${quality.roundToInt()}",
            style = MaterialTheme.typography.bodyLarge
        )
        Slider(
            value = quality,
            onValueChange = { quality = it },
            valueRange = 70f..95f,
            steps = 24,
            modifier = Modifier.fillMaxWidth()
        )

        // Encoder (disabled for now)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Encoder: mozjpeg",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = " (jpegli coming later)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // EXIF checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = preserveExif,
                onCheckedChange = { preserveExif = it }
            )
            Text(
                text = "Preserve EXIF metadata",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        // PNG convert checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = convertPng,
                onCheckedChange = { convertPng = it }
            )
            Text(
                text = "Convert PNG to JPEG",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                onConfirm(
                    CompressionOptions(
                        quality = quality.roundToInt(),
                        preserveExif = preserveExif,
                        convertPng = convertPng,
                        encoder = Encoder.MOZJPEG
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}
