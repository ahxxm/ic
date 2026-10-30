package io.ahxxm.ic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.ahxxm.ic.domain.ImageCompressionPreview
import io.ahxxm.ic.domain.formatBytes

/**
 * The whole Review visit: the batch result list and, when [ReviewSession.comparisonIndex] is set,
 * a comparison shown in place. Both modes read from the same session, so selections and the list's
 * scroll position survive the switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsReviewScreen(
    session: ReviewSession,
    onConfirm: (List<ImageCompressionPreview>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    val leaveReview: () -> Unit = {
        session.discard()
        onBack()
    }
    val back: () -> Unit = {
        if (session.isComparing) session.closeComparison() else leaveReview()
    }

    BackHandler(onBack = back)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Review Savings") },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val preview = session.comparisonIndex?.let { session.previews.getOrNull(it) }
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp)

        if (preview != null) {
            ComparisonContent(preview = preview, modifier = contentModifier)
        } else {
            ResultList(
                previews = session.previews,
                selections = session.selections,
                listState = listState,
                onSelectionChange = session::setSelected,
                onSelectAll = session::selectAll,
                onDeselectAll = session::deselectAll,
                onSmartSelect = session::smartSelect,
                onOpenComparison = session::openComparison,
                onConfirm = { onConfirm(session.confirm()) },
                modifier = contentModifier
            )
        }
    }
}

@Composable
private fun ComparisonContent(
    preview: ImageCompressionPreview,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = preview.image.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        ComparisonSlider(
            preview = preview,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
        )

        ComparisonSummary(preview)
    }
}

@Composable
private fun ComparisonSummary(preview: ImageCompressionPreview) {
    val savingsPct = (preview.savingsPercent * 100).toInt()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Original ${formatBytes(preview.originalSize, false)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Compressed ${formatBytes(preview.compressedSize, false)}",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Savings:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${formatBytes(preview.savingsBytes, false)} ($savingsPct%)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ResultList(
    previews: List<ImageCompressionPreview>,
    selections: List<Boolean>,
    listState: LazyListState,
    onSelectionChange: (Int, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSmartSelect: () -> Unit,
    onOpenComparison: (Int) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCount = selections.count { it }
    val sizeDelta = previews.filterIndexed { index, _ -> selections[index] }
        .sumOf { it.compressedSize - it.originalSize }

    Column(modifier = modifier) {
        Text(
            text = "$selectedCount of ${previews.size} selected | ${formatBytes(sizeDelta, true)}",
            style = MaterialTheme.typography.titleMedium,
            color = if (sizeDelta <= 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
        )

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
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(previews) { index, preview ->
                PreviewRow(
                    preview = preview,
                    selected = selections[index],
                    onSelectedChange = { onSelectionChange(index, it) },
                    onClick = { onOpenComparison(index) }
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
    val sizeDelta = preview.compressedSize - preview.originalSize
    val changePct = if (preview.originalSize > 0) (sizeDelta * 100 / preview.originalSize).toInt() else 0
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
            text = "${formatBytes(sizeDelta, true)} (%+d%%)".format(changePct),
            style = MaterialTheme.typography.bodyMedium,
            color = if (lowSavings) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
        )
    }
}
