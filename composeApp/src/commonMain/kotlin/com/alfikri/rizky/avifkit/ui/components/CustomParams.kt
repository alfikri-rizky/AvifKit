package com.alfikri.rizky.avifkit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alfikri.rizky.avifkit.ChromaSubsample
import com.alfikri.rizky.avifkit.CompressionStrategy
import com.alfikri.rizky.avifkit.ui.models.CustomParameters

@Composable
fun CustomParams(
    params: CustomParameters,
    onParamsChange: (CustomParameters) -> Unit,
    modifier: Modifier = Modifier,
    currentFileSize: Long? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Custom Parameters",
                style = MaterialTheme.typography.titleMedium
            )

            // Quality Slider
            SliderWithLabel(
                label = "Quality",
                value = params.quality.toFloat(),
                valueRange = 0f..100f,
                onValueChange = {
                    onParamsChange(params.copy(quality = it.toInt()))
                },
                valueLabel = "${params.quality}"
            )

            // Speed Slider
            SliderWithLabel(
                label = "Encoding Speed",
                value = params.speed.toFloat(),
                valueRange = 0f..10f,
                onValueChange = {
                    onParamsChange(params.copy(speed = it.toInt()))
                },
                valueLabel = "${params.speed}/10"
            )

            // Chroma Subsampling Dropdown
            var expandedSubsample by remember { mutableStateOf(false) }
            Column {
                Text(
                    text = "Chroma Subsampling",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedButton(
                    onClick = { expandedSubsample = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(params.subsample.name)
                }
                DropdownMenu(
                    expanded = expandedSubsample,
                    onDismissRequest = { expandedSubsample = false }
                ) {
                    ChromaSubsample.entries.forEach { subsample ->
                        DropdownMenuItem(
                            text = { Text(subsample.name) },
                            onClick = {
                                onParamsChange(params.copy(subsample = subsample))
                                expandedSubsample = false
                            }
                        )
                    }
                }
            }

            // Alpha Quality Slider
            SliderWithLabel(
                label = "Alpha Quality",
                value = params.alphaQuality.toFloat(),
                valueRange = 0f..100f,
                onValueChange = {
                    onParamsChange(params.copy(alphaQuality = it.toInt()))
                },
                valueLabel = "${params.alphaQuality}"
            )

            // Lossless Toggle
            SwitchWithLabel(
                label = "Lossless",
                checked = params.lossless,
                onCheckedChange = {
                    onParamsChange(params.copy(lossless = it))
                }
            )

            // Preserve Metadata Toggle
            SwitchWithLabel(
                label = "Preserve Metadata",
                checked = params.preserveMetadata,
                onCheckedChange = {
                    onParamsChange(params.copy(preserveMetadata = it))
                }
            )

            // Max Dimension (Optional)
            var maxDimEnabled by remember { mutableStateOf(params.maxDimension != null) }
            var maxDimValue by remember { mutableStateOf((params.maxDimension ?: 2048).toFloat()) }

            Column {
                SwitchWithLabel(
                    label = "Limit Max Dimension",
                    checked = maxDimEnabled,
                    onCheckedChange = {
                        maxDimEnabled = it
                        onParamsChange(
                            params.copy(maxDimension = if (it) maxDimValue.toInt() else null)
                        )
                    }
                )

                if (maxDimEnabled) {
                    SliderWithLabel(
                        label = "Max Dimension",
                        value = maxDimValue,
                        valueRange = 512f..4096f,
                        steps = 7, // 512, 720, 1024, 1280, 1920, 2048, 4096
                        onValueChange = {
                            maxDimValue = it
                            onParamsChange(params.copy(maxDimension = it.toInt()))
                        },
                        valueLabel = "${maxDimValue.toInt()}px"
                    )
                }
            }

            // Max File Size (Optional)
            var maxSizeEnabled by remember { mutableStateOf(params.maxSize != null) }
            var maxSizeValue by remember {
                mutableStateOf(params.maxSize?.toFloat() ?: (currentFileSize?.toFloat() ?: 1048576f))
            }
            var maxSizeError by remember { mutableStateOf<String?>(null) }

            // Update maxSizeValue when currentFileSize changes
            LaunchedEffect(currentFileSize) {
                if (currentFileSize != null && maxSizeValue > currentFileSize.toFloat()) {
                    maxSizeValue = currentFileSize.toFloat()
                    if (maxSizeEnabled) {
                        onParamsChange(params.copy(maxSize = maxSizeValue.toLong()))
                    }
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SwitchWithLabel(
                        label = "Limit Max File Size",
                        checked = maxSizeEnabled,
                        onCheckedChange = { enabled ->
                            maxSizeEnabled = enabled
                            if (enabled) {
                                // Validate before enabling
                                maxSizeError = validateMaxSize(maxSizeValue.toLong(), currentFileSize)
                                if (maxSizeError == null) {
                                    onParamsChange(params.copy(maxSize = maxSizeValue.toLong()))
                                }
                            } else {
                                maxSizeError = null
                                onParamsChange(params.copy(maxSize = null))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (currentFileSize == null && maxSizeEnabled) {
                    Text(
                        text = "Please select a file first",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (maxSizeEnabled && currentFileSize != null) {
                    SliderWithLabel(
                        label = "Max File Size",
                        value = maxSizeValue,
                        valueRange = 1024f..currentFileSize.toFloat(),
                        onValueChange = { newValue ->
                            maxSizeValue = newValue
                            maxSizeError = validateMaxSize(newValue.toLong(), currentFileSize)
                            if (maxSizeError == null) {
                                onParamsChange(params.copy(maxSize = newValue.toLong()))
                            }
                        },
                        valueLabel = formatFileSize(maxSizeValue.toLong())
                    )

                    if (maxSizeError != null) {
                        Text(
                            text = maxSizeError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Warning for very small sizes
                    if (maxSizeValue < 50 * 1024) { // Less than 50 KB
                        Text(
                            text = "⚠️ Very small target size. Result may not reach target due to AVIF format overhead and image complexity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Text(
                        text = "Original size: ${formatFileSize(currentFileSize)} • The converter will try up to 10 attempts to reach the target by adjusting quality and dimensions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Compression Strategy (Only visible when maxSize is set)
            if (maxSizeEnabled && currentFileSize != null) {
                var expandedStrategy by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Compression Strategy",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedButton(
                            onClick = { expandedStrategy = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when (params.compressionStrategy) {
                                            CompressionStrategy.SMART -> "SMART (Recommended)"
                                            CompressionStrategy.STRICT -> "STRICT"
                                        },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = expandedStrategy,
                            onDismissRequest = { expandedStrategy = false }
                        ) {
                            CompressionStrategy.entries.forEach { strategy ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = when (strategy) {
                                                    CompressionStrategy.SMART -> "SMART (Recommended)"
                                                    CompressionStrategy.STRICT -> "STRICT"
                                                },
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = when (strategy) {
                                                    CompressionStrategy.SMART -> "Best quality within limit"
                                                    CompressionStrategy.STRICT -> "Smallest possible size"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        onParamsChange(params.copy(compressionStrategy = strategy))
                                        expandedStrategy = false
                                    }
                                )
                            }
                        }

                        // Strategy explanation
                        Text(
                            text = when (params.compressionStrategy) {
                                CompressionStrategy.SMART -> "🎯 SMART: Finds the highest quality image that meets your target size using binary search. Faster (6-8 attempts) and produces better-looking images."
                                CompressionStrategy.STRICT -> "🗜️ STRICT: Continues compressing even after meeting target to find the smallest possible size. May take longer (up to 10 attempts)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun validateMaxSize(maxSize: Long, currentFileSize: Long?): String? {
    return when {
        maxSize <= 0 -> "Max size must be greater than 0"
        currentFileSize != null && maxSize > currentFileSize -> "Max size cannot exceed original file size"
        else -> null
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${String.format("%.2f", bytes / 1024.0)} KB"
        else -> "${String.format("%.2f", bytes / (1024.0 * 1024.0))} MB"
    }
}

@Composable
private fun SliderWithLabel(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueLabel: String,
    modifier: Modifier = Modifier,
    steps: Int = 0
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SwitchWithLabel(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}