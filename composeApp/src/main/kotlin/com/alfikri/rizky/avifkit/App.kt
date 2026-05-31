package com.alfikri.rizky.avifkit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alfikri.rizky.avifkit.ui.models.UploadUiState
import com.alfikri.rizky.avifkit.ui.screens.ResultScreen
import com.alfikri.rizky.avifkit.ui.screens.UploadScreen
import com.alfikri.rizky.avifkit.ui.viewmodel.AvifConverterViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

sealed class Screen(val route: String) {
    data object Upload : Screen("upload")
    data object Result : Screen("result")
}

@Composable
@Preview
fun App() {
    val context = LocalContext.current
    val viewModel: AvifConverterViewModel = viewModel { AvifConverterViewModel(context) }

    val uiState by viewModel.uiState.collectAsState()
    val qualityPreset by viewModel.qualityPreset.collectAsState()
    val customParams by viewModel.customParams.collectAsState()

    val navController = rememberNavController()

    // Navigate to result screen when conversion succeeds
    LaunchedEffect(uiState) {
        if (uiState is UploadUiState.Success) {
            navController.navigate(Screen.Result.route) {
                // Don't add upload screen to back stack multiple times
                launchSingleTop = true
            }
        }
    }

    MaterialTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Upload.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(Screen.Upload.route) {
                    UploadScreen(
                        uiState = uiState,
                        qualityPreset = qualityPreset,
                        customParams = customParams,
                        onImageSelected = viewModel::onImageSelected,
                        onQualityPresetChanged = viewModel::onQualityPresetChanged,
                        onCustomParamsChanged = viewModel::onCustomParamsChanged,
                        onConvertClicked = viewModel::convertToAvif
                    )
                }

                composable(Screen.Result.route) {
                    val result = (uiState as? UploadUiState.Success)?.result
                    if (result != null) {
                        ResultScreen(
                            result = result,
                            onBack = {
                                viewModel.resetState()
                                navController.popBackStack()
                            },
                            targetMaxSize = customParams.maxSize
                        )
                    }
                }
            }
        }
    }
}