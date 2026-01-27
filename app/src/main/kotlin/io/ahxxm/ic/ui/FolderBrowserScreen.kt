package io.ahxxm.ic.ui

import android.Manifest
import android.content.ContentResolver
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.ahxxm.ic.domain.FolderSummary
import io.ahxxm.ic.domain.ImageRepository
import io.ahxxm.ic.domain.formatBytes
import kotlinx.coroutines.launch

private fun requiredPermissions(): List<String> = buildList {
    add(
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
    )
    if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private fun hasMediaPermission(context: android.content.Context): Boolean {
    val mediaPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, mediaPermission) == PermissionChecker.PERMISSION_GRANTED
}

private class ContentResolverFolderRepository(
    private val contentResolver: ContentResolver
) : FolderRepository {
    override suspend fun getFolders(): List<FolderSummary> {
        return ImageRepository(contentResolver).getFolders()
    }
}

@Composable
fun FolderBrowserScreen(
    onFolderSelected: (bucketId: Long, folderName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionGranted by remember { mutableStateOf(hasMediaPermission(context)) }

    val viewModel = remember {
        FolderBrowserViewModel(ContentResolverFolderRepository(context.contentResolver))
    }
    val state by viewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val mediaPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        permissionGranted = results[mediaPermission] == true
        viewModel.onPermissionChanged(permissionGranted)
    }

    DisposableEffect(Unit) {
        viewModel.onPermissionChanged(permissionGranted)
        onDispose { }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && permissionGranted) {
                scope.launch { viewModel.loadFolders() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !permissionGranted -> {
                PermissionRequest(
                    onRequestPermission = { permissionLauncher.launch(requiredPermissions().toTypedArray()) }
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select folder to compress",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = { scope.launch { viewModel.loadFolders() } }) {
                            Text("Refresh")
                        }
                    }
                    HorizontalDivider()
                    FolderList(
                        folders = state.folders,
                        onFolderClick = { folder -> onFolderSelected(folder.bucketId, folder.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRequest(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permissions required",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Image access: to read and compress your photos.\n\nNotifications (optional): shows compression progress when you switch apps.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(onClick = onRequestPermission) {
            Text("Grant Permissions")
        }
    }
}

@Composable
private fun FolderList(
    folders: List<FolderSummary>,
    onFolderClick: (FolderSummary) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(folders, key = { it.bucketId }) { folder ->
            FolderRow(folder = folder, onClick = { onFolderClick(folder) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun FolderRow(
    folder: FolderSummary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${folder.imageCount} images",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatBytes(folder.totalSizeBytes, false),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
