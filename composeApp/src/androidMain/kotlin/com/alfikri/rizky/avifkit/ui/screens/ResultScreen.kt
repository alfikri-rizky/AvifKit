package com.alfikri.rizky.avifkit.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alfikri.rizky.avifkit.ui.models.ConversionResult
import com.alfikri.rizky.avifkit.ui.models.ImageData
import com.alfikri.rizky.avifkit.utils.FileDownloadHelper
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ResultScreen(
    result: ConversionResult,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetMaxSize: Long? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isDownloading by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Header with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Conversion Results",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // Compression Stats Card
        CompressionStatsCard(result = result, targetMaxSize = targetMaxSize)

        // Tabs for Before/After
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Before") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("After (AVIF)") }
            )
        }

        // Image Preview Card
        val currentImageUri = if (selectedTab == 0) result.originalImageUri else "file://${result.convertedImagePath}"
        val currentData = if (selectedTab == 0) result.originalData else result.convertedData
        val imageLabel = if (selectedTab == 0) "Original Image" else "AVIF Image (Decoded via decodeAvif())"

        ImagePreviewCard(
            imageUri = currentImageUri,
            imageLabel = imageLabel,
            scale = scale,
            offset = offset,
            onTransform = { newScale, newOffset ->
                scale = newScale
                offset = newOffset
            },
            onResetZoom = {
                scale = 1f
                offset = Offset.Zero
            },
            // Use decoded bitmap for AVIF tab to verify decodeAvif works
            decodedBitmap = if (selectedTab == 1) result.decodedBitmap as? android.graphics.Bitmap else null
        )

        // Image Details Card
        ImageDetailsCard(imageData = currentData)

        // Download Button
        Button(
            onClick = {
                isDownloading = true
                scope.launch {
                    val sourceFile = File(result.convertedImagePath)
                    val fileName = FileDownloadHelper.generateAvifFileName(
                        sourceFile.nameWithoutExtension
                    )

                    val downloadResult = FileDownloadHelper.saveToDownloads(
                        context = context,
                        sourceFile = sourceFile,
                        displayName = fileName
                    )

                    downloadResult.fold(
                        onSuccess = { message ->
                            snackbarHostState.showSnackbar(
                                message = message,
                                duration = SnackbarDuration.Short
                            )
                        },
                        onFailure = { error ->
                            snackbarHostState.showSnackbar(
                                message = "Download failed: ${error.message}",
                                duration = SnackbarDuration.Long
                            )
                        }
                    )

                    isDownloading = false
                }
            },
            enabled = !isDownloading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isDownloading) "Downloading..." else "Download AVIF")
        }

        Text(
            text = if (!isDownloading) "Download to your device's Downloads folder" else "Saving file...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        }
    }
}

@Composable
private fun CompressionStatsCard(
    result: ConversionResult,
    modifier: Modifier = Modifier,
    targetMaxSize: Long? = null
) {
    val originalSize = result.originalData.fileSize
    val convertedSize = result.convertedData.fileSize
    val compressionRatio = if (originalSize > 0) {
        ((originalSize - convertedSize).toFloat() / originalSize * 100).toInt()
    } else 0
    val savedBytes = originalSize - convertedSize
    val targetAchieved = targetMaxSize?.let { convertedSize <= it } ?: true

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (targetMaxSize != null && !targetAchieved) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "File Size Reduced",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                    Text(
                        text = "$compressionRatio% smaller",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Saved",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                    Text(
                        text = formatFileSize(savedBytes),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Show target max size status if set
            if (targetMaxSize != null) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Target Size: ${formatFileSize(targetMaxSize)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                    Text(
                        text = if (targetAchieved) "✓ Achieved" else "✗ Not Reached",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (targetAchieved) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewCard(
    imageUri: String,
    imageLabel: String,
    scale: Float,
    offset: Offset,
    onTransform: (Float, Offset) -> Unit,
    onResetZoom: () -> Unit,
    modifier: Modifier = Modifier,
    decodedBitmap: android.graphics.Bitmap? = null
) {
    // Create transformable state for proper gesture handling
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)

        // Calculate new offset with proper bounds
        val newOffset = if (newScale > 1f) {
            offset + panChange
        } else {
            Offset.Zero
        }

        onTransform(newScale, newOffset)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(8.dp)
                    )
                    // Apply transformable modifier for smooth pinch-to-zoom
                    .transformable(state = state)
                    // Add double-tap to zoom
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1f) {
                                    onResetZoom()
                                } else {
                                    onTransform(2f, Offset.Zero)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    // Use decoded bitmap if available, otherwise use URI
                    model = decodedBitmap ?: imageUri,
                    contentDescription = imageLabel,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = imageLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onResetZoom,
                    enabled = scale > 1f
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Reset Zoom",
                        modifier = Modifier.size(20.dp),
                        tint = if (scale > 1f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageDetailsCard(
    imageData: ImageData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Image Details",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            DetailRow(label = "Quality", value = "${imageData.quality}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DetailRow(label = "Encoding Speed", value = "${imageData.speed}/10")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DetailRow(label = "Chroma Subsampling", value = imageData.subsample.name)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DetailRow(
                label = "Metadata Preserved",
                value = if (imageData.metadata) "Yes" else "No"
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DetailRow(label = "Dimensions", value = imageData.dimension)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            DetailRow(label = "File Size", value = formatFileSize(imageData.fileSize))
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${String.format("%.2f", bytes / 1024.0)} KB"
        else -> "${String.format("%.2f", bytes / (1024.0 * 1024.0))} MB"
    }
}