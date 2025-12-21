package com.alfikri.rizky.avifkit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alfikri.rizky.avifkit.ui.components.CustomParams
import com.alfikri.rizky.avifkit.ui.components.QualitySelector
import com.alfikri.rizky.avifkit.ui.models.CustomParameters
import com.alfikri.rizky.avifkit.ui.models.QualityPreset
import com.alfikri.rizky.avifkit.ui.models.UploadUiState

@Composable
fun UploadScreen(
    uiState: UploadUiState,
    qualityPreset: QualityPreset,
    customParams: CustomParameters,
    onImageSelected: (Uri) -> Unit,
    onQualityPresetChanged: (QualityPreset) -> Unit,
    onCustomParamsChanged: (CustomParameters) -> Unit,
    onConvertClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AVIF Converter",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Convert your images to AVIF format",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Image Upload Section
        ImageUploadCard(
            uiState = uiState,
            onImageSelected = onImageSelected
        )

        // Show quality selector and convert button when image is selected
        if (uiState is UploadUiState.ImageSelected || uiState is UploadUiState.Converting) {
            QualitySelector(
                selectedPreset = qualityPreset,
                onPresetChange = onQualityPresetChanged
            )

            if (qualityPreset == QualityPreset.CUSTOM) {
                val currentFileSize = when (uiState) {
                    is UploadUiState.ImageSelected -> uiState.fileSize
                    is UploadUiState.Converting -> uiState.fileSize
                    else -> null
                }

                CustomParams(
                    params = customParams,
                    onParamsChange = onCustomParamsChanged,
                    currentFileSize = currentFileSize
                )
            }

            Button(
                onClick = onConvertClicked,
                enabled = uiState is UploadUiState.ImageSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (uiState is UploadUiState.Converting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Converting...")
                } else {
                    Text("Convert to AVIF")
                }
            }
        }

        // Error message
        if (uiState is UploadUiState.Error) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = uiState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ImageUploadCard(
    uiState: UploadUiState,
    onImageSelected: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Create a URI for camera to save the photo
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { onImageSelected(it) }
    }

    // Camera picker
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { uri ->
                onImageSelected(uri)
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Upload Image",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedCard(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = "Gallery",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Gallery")
                    }
                }

                OutlinedCard(
                    onClick = {
                        // Create a temporary file for the camera to save the photo
                        val photoFile = File(
                            context.cacheDir,
                            "camera_photo_${System.currentTimeMillis()}.jpg"
                        )

                        // Create a content URI for the file using FileProvider
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )

                        cameraImageUri = uri
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = "Camera",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Camera")
                    }
                }
            }

            // Image Preview
            when (uiState) {
                is UploadUiState.ImageSelected, is UploadUiState.Converting -> {
                    val imageUri = when (uiState) {
                        is UploadUiState.ImageSelected -> uiState.imageUri
                        is UploadUiState.Converting -> uiState.imageUri
                        else -> return@Column
                    }
                    val fileName = when (uiState) {
                        is UploadUiState.ImageSelected -> uiState.fileName
                        is UploadUiState.Converting -> uiState.fileName
                        else -> return@Column
                    }
                    val fileSize = when (uiState) {
                        is UploadUiState.ImageSelected -> uiState.fileSize
                        is UploadUiState.Converting -> uiState.fileSize
                        else -> return@Column
                    }

                    Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                Text(
                                    text = formatFileSize(fileSize),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = "Selected image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                }
                else -> {}
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${String.format("%.2f", bytes / 1024.0)} KB"
        else -> "${String.format("%.2f", bytes / (1024.0 * 1024.0))} MB"
    }
}
