package io.ahxxm.ic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.ahxxm.ic.domain.ImageCompressionPreview

/**
 * Squoosh-style comparison: original and compressed share one layout box, and a vertical divider
 * at [fraction] clips the compressed image to its right side. The whole box is the drag target.
 */
@Composable
fun ComparisonSlider(
    preview: ImageCompressionPreview,
    modifier: Modifier = Modifier
) {
    var fraction by remember(preview) { mutableFloatStateOf(0.5f) }
    val dividerColor = MaterialTheme.colorScheme.primary
    val handleColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (size.width > 0) {
                        fraction = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    }
                    drag(down.id) { change ->
                        change.consume()
                        if (size.width > 0) {
                            fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                }
            }
            .semantics {
                contentDescription = "Comparison slider"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target ->
                    fraction = target.coerceIn(0f, 1f)
                    true
                }
            }
    ) {
        AsyncImage(
            model = preview.image.uri,
            contentDescription = "Original",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        AsyncImage(
            model = preview.tempFile,
            contentDescription = "Compressed",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(left = size.width * fraction) {
                        this@drawWithContent.drawContent()
                    }
                }
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val x = size.width * fraction
            val center = Offset(x, size.height / 2f)
            drawLine(
                color = dividerColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(color = dividerColor, radius = 14.dp.toPx(), center = center)
            drawCircle(color = handleColor, radius = 11.dp.toPx(), center = center)
        }
    }
}
