package com.example.imagecompressor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.imagecompressor.domain.CompressionOptions
import com.example.imagecompressor.domain.ImageCompressionPreview
import java.net.URLDecoder
import java.net.URLEncoder

object NavArgs {
    const val BUCKET_ID = "bucketId"
    const val FOLDER_NAME = "folderName"
}

sealed class Screen(val route: String) {
    data object FolderBrowser : Screen("folders")
    data object Options : Screen("options/{${NavArgs.BUCKET_ID}}/{${NavArgs.FOLDER_NAME}}") {
        fun createRoute(bucketId: Long, folderName: String): String =
            "options/$bucketId/${URLEncoder.encode(folderName, "UTF-8")}"
    }
    data object Preview : Screen("preview/{${NavArgs.BUCKET_ID}}") {
        fun createRoute(bucketId: Long): String = "preview/$bucketId"
    }
    data object SavingsPreview : Screen("savings/{${NavArgs.BUCKET_ID}}") {
        fun createRoute(bucketId: Long): String = "savings/$bucketId"
    }
    data object Comparison : Screen("comparison")
    data object Progress : Screen("progress")
    data object Done : Screen("done")
}

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
        startDestination = Screen.FolderBrowser.route,
        modifier = modifier
    ) {
        composable(Screen.FolderBrowser.route) {
            FolderBrowserScreen(
                onFolderSelected = { bucketId, folderName ->
                    navController.navigate(Screen.Options.createRoute(bucketId, folderName))
                }
            )
        }

        composable(
            route = Screen.Options.route,
            arguments = listOf(
                navArgument(NavArgs.BUCKET_ID) { type = NavType.LongType },
                navArgument(NavArgs.FOLDER_NAME) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bucketId = backStackEntry.arguments?.getLong(NavArgs.BUCKET_ID) ?: 0L
            val folderName = backStackEntry.arguments?.getString(NavArgs.FOLDER_NAME)?.let {
                URLDecoder.decode(it, "UTF-8")
            } ?: ""

            OptionsScreen(
                folderName = folderName,
                onConfirm = { options ->
                    currentOptions = options
                    navController.navigate(Screen.Preview.createRoute(bucketId))
                }
            )
        }

        composable(
            route = Screen.Preview.route,
            arguments = listOf(
                navArgument(NavArgs.BUCKET_ID) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val bucketId = backStackEntry.arguments?.getLong(NavArgs.BUCKET_ID) ?: 0L
            PreviewScreen(
                bucketId = bucketId,
                options = currentOptions,
                onProceed = { navController.navigate(Screen.SavingsPreview.createRoute(bucketId)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SavingsPreview.route,
            arguments = listOf(
                navArgument(NavArgs.BUCKET_ID) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val bucketId = backStackEntry.arguments?.getLong(NavArgs.BUCKET_ID) ?: 0L
            SavingsPreviewScreen(
                bucketId = bucketId,
                options = currentOptions,
                onConfirm = { previews ->
                    selectedPreviews = previews
                    navController.navigate(Screen.Progress.route)
                },
                onBack = { navController.popBackStack() },
                onImageClick = { preview ->
                    selectedForComparison = preview
                    navController.navigate(Screen.Comparison.route)
                }
            )
        }

        composable(Screen.Comparison.route) {
            selectedForComparison?.let { preview ->
                ComparisonScreen(
                    preview = preview,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Screen.Progress.route) {
            ProgressScreen(
                selectedPreviews = selectedPreviews,
                onComplete = { count, savedBytes ->
                    completionCount = count
                    completionSavedBytes = savedBytes
                    navController.navigate(Screen.Done.route) {
                        popUpTo(Screen.FolderBrowser.route)
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Screen.Done.route) {
            DoneScreen(
                count = completionCount,
                savedBytes = completionSavedBytes,
                onFinish = {
                    navController.navigate(Screen.FolderBrowser.route) {
                        popUpTo(Screen.FolderBrowser.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
