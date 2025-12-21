package com.alfikri.rizky.avifkit.ui.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfikri.rizky.avifkit.AvifConverter
import com.alfikri.rizky.avifkit.ChromaSubsample
import com.alfikri.rizky.avifkit.EncodingOptions
import com.alfikri.rizky.avifkit.ImageInput
import com.alfikri.rizky.avifkit.ui.models.ConversionResult
import com.alfikri.rizky.avifkit.ui.models.CustomParameters
import com.alfikri.rizky.avifkit.ui.models.ImageData
import com.alfikri.rizky.avifkit.ui.models.QualityPreset
import com.alfikri.rizky.avifkit.ui.models.UploadUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AvifConverterViewModel(private val context: Context) : ViewModel() {

    private val avifConverter = AvifConverter()

    private val _uiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    private val _qualityPreset = MutableStateFlow(QualityPreset.BALANCED)
    val qualityPreset: StateFlow<QualityPreset> = _qualityPreset.asStateFlow()

    private val _customParams = MutableStateFlow(CustomParameters())
    val customParams: StateFlow<CustomParameters> = _customParams.asStateFlow()

    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                val fileSize = inputStream?.available()?.toLong() ?: 0L
                val fileName = getFileName(uri)

                inputStream?.close()

                _uiState.value = UploadUiState.ImageSelected(
                    imageUri = uri.toString(),
                    fileName = fileName,
                    fileSize = fileSize
                )
            } catch (e: Exception) {
                _uiState.value = UploadUiState.Error("Failed to load image: ${e.message}")
            }
        }
    }

    fun onQualityPresetChanged(preset: QualityPreset) {
        _qualityPreset.value = preset
    }

    fun onCustomParamsChanged(params: CustomParameters) {
        _customParams.value = params
    }

    fun convertToAvif() {
        val currentState = _uiState.value
        if (currentState !is UploadUiState.ImageSelected) return

        viewModelScope.launch {
            try {
                _uiState.value = UploadUiState.Converting(
                    imageUri = currentState.imageUri,
                    fileName = currentState.fileName,
                    fileSize = currentState.fileSize
                )

                val uri = Uri.parse(currentState.imageUri)
                val imageBytes = context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                } ?: throw Exception("Failed to read image data")

                // Get original image info
                val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                val originalDimension = "${originalBitmap.width}x${originalBitmap.height}"

                // Get encoding options based on preset or custom params
                val encodingOptions = if (_qualityPreset.value == QualityPreset.CUSTOM) {
                    val params = _customParams.value
                    EncodingOptions(
                        quality = params.quality,
                        speed = params.speed,
                        subsample = params.subsample,
                        alphaQuality = params.alphaQuality,
                        lossless = params.lossless,
                        preserveMetadata = params.preserveMetadata,
                        maxDimension = params.maxDimension,
                        maxSize = params.maxSize,
                        compressionStrategy = params.compressionStrategy
                    )
                } else {
                    EncodingOptions.fromPriority(_qualityPreset.value.toPriority()!!)
                }

                // Convert to AVIF
                val outputPath = File(context.cacheDir, "converted_${System.currentTimeMillis()}.avif").absolutePath
                val convertedPath = avifConverter.convertToFile(
                    input = ImageInput.from(imageBytes),
                    outputPath = outputPath,
                    options = encodingOptions
                )

                // Get converted file info
                val convertedFile = File(convertedPath)
                val convertedSize = convertedFile.length()

                // Calculate converted dimensions
                var convertedWidth = originalBitmap.width
                var convertedHeight = originalBitmap.height

                encodingOptions.maxDimension?.let { maxDim ->
                    val maxOriginal = maxOf(originalBitmap.width, originalBitmap.height)
                    if (maxOriginal > maxDim) {
                        val scale = maxDim.toFloat() / maxOriginal
                        convertedWidth = (originalBitmap.width * scale).toInt()
                        convertedHeight = (originalBitmap.height * scale).toInt()
                    }
                }

                val convertedDimension = "${convertedWidth}x${convertedHeight}"

                val originalData = ImageData(
                    quality = 100,
                    speed = 0,
                    subsample = ChromaSubsample.YUV444,
                    metadata = true,
                    dimension = originalDimension,
                    fileSize = currentState.fileSize
                )

                val convertedData = ImageData(
                    quality = encodingOptions.quality,
                    speed = encodingOptions.speed,
                    subsample = encodingOptions.subsample,
                    metadata = encodingOptions.preserveMetadata,
                    dimension = convertedDimension,
                    fileSize = convertedSize
                )

                val result = ConversionResult(
                    originalImageUri = currentState.imageUri,
                    convertedImagePath = convertedPath,
                    originalData = originalData,
                    convertedData = convertedData
                )

                _uiState.value = UploadUiState.Success(result)

            } catch (e: Exception) {
                _uiState.value = UploadUiState.Error("Conversion failed: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = UploadUiState.Idle
        _qualityPreset.value = QualityPreset.BALANCED
        _customParams.value = CustomParameters()
    }

    private fun getFileName(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            if (nameIndex >= 0) it.getString(nameIndex) else "unknown.jpg"
        } ?: "unknown.jpg"
    }
}
