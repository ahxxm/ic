package io.ahxxm.ic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.ahxxm.ic.domain.CompressionOptions
import io.ahxxm.ic.domain.ImageCompressionPreview
import kotlinx.serialization.Serializable

@Serializable data object FolderBrowser
@Serializable data class Options(val bucketId: Long, val folderName: String)
@Serializable data class SavingsPreview(val bucketId: Long)
@Serializable data object Comparison
@Serializable data object Progress
@Serializable data object Done

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    var currentOptions by remember { mutableStateOf(CompressionOptions()) }
    var selectedForComparison by remember { mutableStateOf<ImageCompressionPreview?>(null) }
    var selectedPreviews by remember { mutableStateOf<List<ImageCompressionPreview>>(emptyList()) }
    var completionCount by remember { mutableStateOf(0) }
    var completionSavedBytes by remember { mutableStateOf(0L) }

    NavHost(
        navController = navController,
        startDestination = FolderBrowser,
        modifier = modifier
    ) {
        composable<FolderBrowser> {
            FolderBrowserScreen(
                onFolderSelected = { bucketId, folderName ->
                    navController.navigate(Options(bucketId, folderName))
                }
            )
        }

        composable<Options> { backStackEntry ->
            val route = backStackEntry.toRoute<Options>()
            OptionsScreen(
                bucketId = route.bucketId,
                folderName = route.folderName,
                onConfirm = { options ->
                    currentOptions = options
                    navController.navigate(SavingsPreview(route.bucketId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<SavingsPreview> { backStackEntry ->
            val route = backStackEntry.toRoute<SavingsPreview>()
            SavingsPreviewScreen(
                bucketId = route.bucketId,
                options = currentOptions,
                onConfirm = { previews ->
                    selectedPreviews = previews
                    navController.navigate(Progress)
                },
                onBack = { navController.popBackStack() },
                onImageClick = { preview ->
                    selectedForComparison = preview
                    navController.navigate(Comparison)
                }
            )
        }

        composable<Comparison> {
            selectedForComparison?.let { preview ->
                ComparisonScreen(
                    preview = preview,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable<Progress> {
            ProgressScreen(
                selectedPreviews = selectedPreviews,
                onComplete = { count, savedBytes ->
                    completionCount = count
                    completionSavedBytes = savedBytes
                    navController.navigate(Done) {
                        popUpTo<FolderBrowser>()
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable<Done> {
            DoneScreen(
                count = completionCount,
                savedBytes = completionSavedBytes,
                onFinish = {
                    navController.navigate(FolderBrowser) {
                        popUpTo<FolderBrowser> { inclusive = true }
                    }
                }
            )
        }
    }
}
