package com.example.imagecompressor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

sealed class Screen(val route: String) {
    data object FolderBrowser : Screen("folders")
    data object Options : Screen("options")
    data object Preview : Screen("preview")
    data object Progress : Screen("progress")
    data object Done : Screen("done")
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.FolderBrowser.route,
        modifier = modifier
    ) {
        composable(Screen.FolderBrowser.route) {
            PlaceholderScreen("Folder Browser")
        }
        composable(Screen.Options.route) {
            PlaceholderScreen("Compression Options")
        }
        composable(Screen.Preview.route) {
            PlaceholderScreen("Savings Preview")
        }
        composable(Screen.Progress.route) {
            PlaceholderScreen("Compression Progress")
        }
        composable(Screen.Done.route) {
            PlaceholderScreen("Compression Complete")
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title)
    }
}
