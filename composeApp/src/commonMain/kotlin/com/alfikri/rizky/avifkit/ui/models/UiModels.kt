package com.alfikri.rizky.avifkit.ui.models

import com.alfikri.rizky.avifkit.ChromaSubsample
import com.alfikri.rizky.avifkit.CompressionStrategy
import com.alfikri.rizky.avifkit.Priority

/**
 * Quality preset options matching the Figma design
 */
enum class QualityPreset {
    SPEED,
    QUALITY,
    STORAGE,
    BALANCED,
    CUSTOM;

    fun toPriority(): Priority? = when (this) {
        SPEED -> Priority.SPEED
        QUALITY -> Priority.QUALITY
        STORAGE -> Priority.STORAGE
        BALANCED -> Priority.BALANCED
        CUSTOM -> null
    }

    fun getDisplayName(): String = when (this) {
        SPEED -> "Speed"
        QUALITY -> "Quality"
        STORAGE -> "Storage"
        BALANCED -> "Balanced"
        CUSTOM -> "Custom"
    }

    fun getDescription(): String = when (this) {
        SPEED -> "Fastest encoding, lower quality"
        QUALITY -> "Best quality, slower encoding"
        STORAGE -> "Minimum file size"
        BALANCED -> "Good balance of all factors"
        CUSTOM -> "Custom parameters"
    }
}

/**
 * Custom parameters for AVIF conversion
 */
data class CustomParameters(
    val quality: Int = 80,
    val speed: Int = 5,
    val subsample: ChromaSubsample = ChromaSubsample.YUV420,
    val alphaQuality: Int = 100,
    val lossless: Boolean = false,
    val preserveMetadata: Boolean = false,
    val maxDimension: Int? = null,
    val maxSize: Long? = null,
    val compressionStrategy: CompressionStrategy = CompressionStrategy.SMART
)

/**
 * Image data for display in UI
 */
data class ImageData(
    val quality: Int,
    val speed: Int,
    val subsample: ChromaSubsample,
    val metadata: Boolean,
    val dimension: String,
    val fileSize: Long
)

/**
 * Conversion result for display in Result Screen
 */
data class ConversionResult(
    val originalImageUri: String,
    val convertedImagePath: String,
    val originalData: ImageData,
    val convertedData: ImageData,
    val decodedBitmap: Any? = null // Platform-specific bitmap (Android: Bitmap, iOS: UIImage)
)

/**
 * UI State for Upload Screen
 */
sealed class UploadUiState {
    data object Idle : UploadUiState()
    data class ImageSelected(
        val imageUri: String,
        val fileName: String,
        val fileSize: Long
    ) : UploadUiState()
    data class Converting(
        val imageUri: String,
        val fileName: String,
        val fileSize: Long,
        val progress: Float = 0f
    ) : UploadUiState()
    data class Success(val result: ConversionResult) : UploadUiState()
    data class Error(val message: String) : UploadUiState()
}
