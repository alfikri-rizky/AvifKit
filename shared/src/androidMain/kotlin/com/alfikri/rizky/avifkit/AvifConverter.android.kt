package com.alfikri.rizky.avifkit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayInputStream
// Import FileKit extension functions
import io.github.vinceglb.filekit.*

actual class AvifConverter {

    // Native methods - implemented in C++ via JNI
    // These work with or without libavif (conditional compilation)
    private external fun nativeEncode(
        pixels: ByteArray,
        width: Int,
        height: Int,
        quality: Int,
        speed: Int,
        subsample: Int
    ): ByteArray?

    private external fun nativeDecode(
        avifData: ByteArray
    ): DecodedImage?

    private external fun nativeIsAvif(
        data: ByteArray
    ): Boolean

    private external fun nativeGetVersion(): String

    companion object {
        private const val TAG = "AvifConverter"
        private var nativeLibraryLoaded = false

        init {
            try {
                System.loadLibrary("avif-android-wrapper")
                nativeLibraryLoaded = true
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library not available. Some features may be limited.", e)
                nativeLibraryLoaded = false
            }
        }

        fun isNativeLibraryLoaded(): Boolean = nativeLibraryLoaded
    }

    actual suspend fun convertToBitmap(
        input: ImageInput,
        priority: Priority,
        options: EncodingOptions?
    ): PlatformBitmap = withContext(Dispatchers.IO) {
        val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

        // Handle maxSize if specified
        val avifData = if (encodingOptions.maxSize != null) {
            convertWithAdaptiveCompression(input, encodingOptions)
        } else {
            convertStandard(input, encodingOptions)
        }

        // Decode AVIF to Bitmap
        decodeAvifToBitmap(avifData)
    }

    actual suspend fun convertToFile(
        input: ImageInput,
        outputPath: String,
        priority: Priority,
        options: EncodingOptions?
    ): String = withContext(Dispatchers.IO) {
        val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

        // Handle maxSize if specified
        val avifData = if (encodingOptions.maxSize != null) {
            convertWithAdaptiveCompression(input, encodingOptions)
        } else {
            convertStandard(input, encodingOptions)
        }

        // Save to file
        File(outputPath).apply {
            parentFile?.mkdirs()
            writeBytes(avifData)
        }

        outputPath
    }

    actual suspend fun convertToFile(
        input: ImageInput,
        output: PlatformFile,
        priority: Priority,
        options: EncodingOptions?
    ): PlatformFile = withContext(Dispatchers.IO) {
        val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

        // Handle maxSize if specified
        val avifData = if (encodingOptions.maxSize != null) {
            convertWithAdaptiveCompression(input, encodingOptions)
        } else {
            convertStandard(input, encodingOptions)
        }

        // Save to PlatformFile
        output.write(avifData)
        output
    }

    actual suspend fun encodeAvif(
        input: ImageInput,
        priority: Priority,
        options: EncodingOptions?
    ): ByteArray = withContext(Dispatchers.IO) {
        val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

        if (encodingOptions.maxSize != null) {
            convertWithAdaptiveCompression(input, encodingOptions)
        } else {
            convertStandard(input, encodingOptions)
        }
    }

    actual suspend fun decodeAvif(input: ImageInput): PlatformBitmap = withContext(Dispatchers.IO) {
        val data = when (input) {
            is ImageInput.FromBytes -> input.data
            is ImageInput.FromPath -> File(input.path).readBytes()
            is ImageInput.FromFile -> input.file.readBytes()
            is ImageInput.FromBitmap -> throw AvifError.InvalidInput
        }

        decodeAvifToBitmap(data)
    }

    actual fun isAvifSupported(): Boolean {
        // Return true if native library is loaded
        // Currently returns true with placeholder implementation
        return true
    }

    actual fun isAvifFile(input: ImageInput): Boolean {
        return try {
            val data = when (input) {
                is ImageInput.FromBytes -> input.data
                is ImageInput.FromPath -> File(input.path).readBytes().take(12).toByteArray()
                is ImageInput.FromFile -> kotlinx.coroutines.runBlocking {
                    input.file.readBytes().take(12).toByteArray()
                }
                is ImageInput.FromBitmap -> return false
            }
            isAvifFormat(data)
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun getImageInfo(input: ImageInput): ImageInfo = withContext(Dispatchers.IO) {
        when (input) {
            is ImageInput.FromBytes -> {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(input.data, 0, input.data.size, options)
                ImageInfo(
                    width = options.outWidth,
                    height = options.outHeight,
                    format = detectFormat(input.data),
                    hasAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        options.outConfig == Bitmap.Config.ARGB_8888
                    } else false,
                    fileSize = input.data.size.toLong()
                )
            }

            is ImageInput.FromPath -> {
                val file = File(input.path)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(input.path, options)
                ImageInfo(
                    width = options.outWidth,
                    height = options.outHeight,
                    format = detectFormatFromPath(input.path),
                    hasAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        options.outConfig == Bitmap.Config.ARGB_8888
                    } else false,
                    fileSize = file.length()
                )
            }

            is ImageInput.FromFile -> {
                val data = input.file.readBytes()
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)
                ImageInfo(
                    width = options.outWidth,
                    height = options.outHeight,
                    format = detectFormat(data),
                    hasAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        options.outConfig == Bitmap.Config.ARGB_8888
                    } else false,
                    fileSize = input.file.size()
                )
            }

            is ImageInput.FromBitmap -> {
                ImageInfo(
                    width = input.bitmap.width,
                    height = input.bitmap.height,
                    format = ImageFormat.UNKNOWN,
                    hasAlpha = input.bitmap.hasAlpha()
                )
            }
        }
    }

    // Private helper methods

    private suspend fun convertWithAdaptiveCompression(
        input: ImageInput,
        options: EncodingOptions
    ): ByteArray {
        val targetSize = options.maxSize!!

        return when (options.compressionStrategy) {
            CompressionStrategy.SMART -> convertWithSmartCompression(input, options, targetSize)
            CompressionStrategy.STRICT -> convertWithStrictCompression(input, options, targetSize)
        }
    }

    /**
     * SMART compression: Find the highest quality image that still meets the target size
     * Uses binary search for optimal quality setting
     */
    private suspend fun convertWithSmartCompression(
        input: ImageInput,
        options: EncodingOptions,
        targetSize: Long
    ): ByteArray {
        Log.d(TAG, "Using SMART compression strategy for target size: $targetSize bytes")

        var bestResult: ByteArray? = null
        var bestQuality = 0

        // Binary search for optimal quality (40-100 range)
        var minQuality = 40
        var maxQuality = 100
        var attempts = 0
        val maxAttempts = 8 // Binary search typically needs log2(60) ≈ 6-8 attempts

        while (minQuality <= maxQuality && attempts < maxAttempts) {
            val testQuality = (minQuality + maxQuality) / 2
            val testOptions = options.copy(
                quality = testQuality,
                maxSize = null
            )

            val result = convertStandard(input, testOptions)
            attempts++

            Log.d(TAG, "SMART attempt $attempts: quality=$testQuality, size=${result.size}, target=$targetSize")

            if (result.size <= targetSize) {
                // Meets target - save this result and try higher quality
                bestResult = result
                bestQuality = testQuality
                minQuality = testQuality + 1
                Log.d(TAG, "  ✓ Meets target, trying higher quality")
            } else {
                // Too large - try lower quality
                maxQuality = testQuality - 1
                Log.d(TAG, "  ✗ Too large, trying lower quality")
            }
        }

        // If we found a result that meets target, return it
        if (bestResult != null) {
            Log.d(TAG, "SMART compression succeeded: quality=$bestQuality, size=${bestResult.size}")
            return bestResult
        }

        // If binary search failed, fall back to aggressive compression
        Log.w(TAG, "SMART compression failed to meet target, using fallback")
        return convertStandard(input, getFallbackOptions())
    }

    /**
     * STRICT compression: Find the smallest possible image by trying all compression options
     * Continues even after meeting target to maximize compression
     */
    private suspend fun convertWithStrictCompression(
        input: ImageInput,
        options: EncodingOptions,
        targetSize: Long
    ): ByteArray {
        Log.d(TAG, "Using STRICT compression strategy for target size: $targetSize bytes")

        var currentOptions = options.copy(maxSize = null)
        var attempt = 0
        val maxAttempts = 10
        var bestResult: ByteArray? = null
        var targetMet = false

        while (attempt < maxAttempts) {
            val result = convertStandard(input, currentOptions)

            Log.d(TAG, "STRICT attempt $attempt: size=${result.size}, target=$targetSize")

            // Check if we meet the size requirement
            if (result.size <= targetSize) {
                targetMet = true
                bestResult = result
                Log.d(TAG, "  ✓ Meets target, continuing for maximum compression")
            } else {
                Log.d(TAG, "  ✗ Above target, adjusting parameters")
            }

            // Continue to next attempt for more aggressive compression
            currentOptions = adjustCompressionParameters(
                current = currentOptions,
                currentSize = result.size.toLong(),
                targetSize = targetSize,
                attempt = attempt
            )

            attempt++
        }

        // Return the best result if we met the target at least once
        if (bestResult != null) {
            Log.d(TAG, "STRICT compression succeeded: final size=${bestResult.size}")
            return bestResult
        }

        // Final attempt with minimum settings
        Log.w(TAG, "STRICT compression failed to meet target, using fallback")
        return convertStandard(input, getFallbackOptions())
    }

    private fun adjustCompressionParameters(
        current: EncodingOptions,
        currentSize: Long,
        targetSize: Long,
        attempt: Int
    ): EncodingOptions {
        val reductionRatio = targetSize.toFloat() / currentSize

        return when {
            // Need >50% reduction
            reductionRatio < 0.5 -> current.copy(
                quality = maxOf(40, (current.quality * 0.7).toInt()),
                maxDimension = current.maxDimension?.let {
                    (it * 0.75).toInt()
                } ?: 1920,
                subsample = ChromaSubsample.YUV420,
                alphaQuality = maxOf(50, current.alphaQuality - 20),
                speed = minOf(10, current.speed + 2)
            )

            // Need 25-50% reduction
            reductionRatio < 0.75 -> current.copy(
                quality = maxOf(50, current.quality - 15),
                maxDimension = current.maxDimension?.let {
                    (it * 0.85).toInt()
                } ?: 2560,
                alphaQuality = maxOf(60, current.alphaQuality - 10),
                speed = minOf(10, current.speed + 1)
            )

            // Need <25% reduction
            else -> current.copy(
                quality = maxOf(60, current.quality - 8),
                alphaQuality = maxOf(70, current.alphaQuality - 5)
            )
        }
    }

    private fun getFallbackOptions() = EncodingOptions(
        quality = 40,
        speed = 10,
        subsample = ChromaSubsample.YUV420,
        alphaQuality = 50,
        maxDimension = 1024,
        preserveMetadata = false
    )

    private suspend fun convertStandard(
        input: ImageInput,
        options: EncodingOptions
    ): ByteArray = withContext(Dispatchers.IO) {
        when (input) {
            is ImageInput.FromBytes -> {
                if (isAvifFormat(input.data)) {
                    input.data
                } else {
                    val bitmap = BitmapFactory.decodeByteArray(input.data, 0, input.data.size)
                        ?: throw AvifError.DecodingFailed("Failed to decode input image")
                    // Apply EXIF orientation if present
                    val orientedBitmap = applyExifOrientation(bitmap, input.data)
                    encodeBitmapToAvif(orientedBitmap, options)
                }
            }

            is ImageInput.FromBitmap -> {
                // Bitmap is already in memory, use as-is
                encodeBitmapToAvif(input.bitmap, options)
            }

            is ImageInput.FromPath -> {
                val file = File(input.path)
                if (!file.exists()) {
                    throw AvifError.FileError("File not found: ${input.path}")
                }
                if (file.extension.lowercase() == "avif") {
                    file.readBytes()
                } else {
                    val bitmap = BitmapFactory.decodeFile(input.path)
                        ?: throw AvifError.DecodingFailed("Failed to decode file: ${input.path}")
                    // Apply EXIF orientation from file
                    val orientedBitmap = applyExifOrientationFromFile(bitmap, input.path)
                    encodeBitmapToAvif(orientedBitmap, options)
                }
            }

            is ImageInput.FromFile -> {
                val data = input.file.readBytes()
                if (isAvifFormat(data)) {
                    data
                } else {
                    val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                        ?: throw AvifError.DecodingFailed("Failed to decode file: ${input.file.name}")
                    // Apply EXIF orientation if present
                    val orientedBitmap = applyExifOrientation(bitmap, data)
                    encodeBitmapToAvif(orientedBitmap, options)
                }
            }
        }
    }

    private fun encodeBitmapToAvif(bitmap: Bitmap, options: EncodingOptions): ByteArray {
        try {
            // Resize if needed
            val resizedBitmap = options.maxDimension?.let { maxDim ->
                resizeBitmap(bitmap, maxDim)
            } ?: bitmap

            if (!nativeLibraryLoaded) {
                // Fallback: encode as JPEG if native library not loaded
                Log.w(TAG, "Native library not loaded, using JPEG fallback")
                val stream = java.io.ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, options.quality, stream)
                return stream.toByteArray()
            }

            // Convert bitmap to byte array
            val pixels = bitmapToByteArray(resizedBitmap)

            // Get subsample value (0=444, 1=422, 2=420)
            val subsampleValue = when (options.subsample) {
                ChromaSubsample.YUV444 -> 0
                ChromaSubsample.YUV422 -> 1
                ChromaSubsample.YUV420 -> 2
            }

            // Encode using native method (works with or without libavif)
            return nativeEncode(
                pixels,
                resizedBitmap.width,
                resizedBitmap.height,
                options.quality,
                options.speed,
                subsampleValue
            ) ?: throw AvifError.EncodingFailed("Native encoding failed")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError during AVIF encoding", e)
            throw AvifError.OutOfMemory
        } catch (e: AvifError) {
            // Re-throw AvifError as-is
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during encoding", e)
            throw AvifError.EncodingFailed("Encoding failed: ${e.message}")
        }
    }

    private fun decodeAvifToBitmap(avifData: ByteArray): Bitmap {
        try {
            if (!nativeLibraryLoaded) {
                // Fallback: try to decode as standard image format
                Log.w(TAG, "Native library not loaded, using standard image decoding")
                return BitmapFactory.decodeByteArray(avifData, 0, avifData.size)
                    ?: throw AvifError.DecodingFailed("Failed to decode image data")
            }

            // Decode using native method (works with or without libavif)
            val decoded = try {
                Log.d(TAG, "Calling nativeDecode with ${avifData.size} bytes")
                val result = nativeDecode(avifData)
                if (result == null) {
                    Log.e(TAG, "nativeDecode returned null")
                    throw AvifError.DecodingFailed("Native decoding returned null")
                }
                Log.d(TAG, "nativeDecode succeeded: ${result.width}x${result.height}")
                result
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OutOfMemoryError during native decode", e)
                throw AvifError.OutOfMemory
            } catch (e: AvifError) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Exception during native decode", e)
                throw AvifError.DecodingFailed("Native decoding failed: ${e.message}")
            }

            return Bitmap.createBitmap(
                decoded.pixels,
                decoded.width,
                decoded.height,
                Bitmap.Config.ARGB_8888
            )
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError during AVIF decoding", e)
            throw AvifError.OutOfMemory
        } catch (e: AvifError) {
            // Re-throw AvifError as-is
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during decoding", e)
            throw AvifError.DecodingFailed("Decoding failed: ${e.message}")
        }
    }

    /**
     * Get the version of the native library
     * Useful for debugging whether libavif is integrated
     */
    fun getLibraryVersion(): String {
        return if (nativeLibraryLoaded) {
            try {
                nativeGetVersion()
            } catch (e: Exception) {
                "Unknown (error getting version)"
            }
        } else {
            "Native library not loaded"
        }
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // Convert to byte array (RGBA format)
        return ByteArray(pixels.size * 4).apply {
            pixels.forEachIndexed { i, pixel ->
                this[i * 4] = (pixel shr 16 and 0xFF).toByte()     // R
                this[i * 4 + 1] = (pixel shr 8 and 0xFF).toByte()  // G
                this[i * 4 + 2] = (pixel and 0xFF).toByte()        // B
                this[i * 4 + 3] = (pixel shr 24 and 0xFF).toByte() // A
            }
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun isAvifFormat(data: ByteArray): Boolean {
        // Check AVIF file signature
        return data.size > 12 &&
                data.sliceArray(4..11).contentEquals(
                    byteArrayOf(0x66, 0x74, 0x79, 0x70, 0x61, 0x76, 0x69, 0x66)
                )
    }

    private fun detectFormat(data: ByteArray): ImageFormat {
        if (data.size < 12) return ImageFormat.UNKNOWN

        return when {
            isAvifFormat(data) -> ImageFormat.AVIF
            data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() -> ImageFormat.JPEG
            data[0] == 0x89.toByte() && data[1] == 0x50.toByte() -> ImageFormat.PNG
            data[8] == 0x57.toByte() && data[9] == 0x45.toByte() -> ImageFormat.WEBP
            else -> ImageFormat.UNKNOWN
        }
    }

    private fun detectFormatFromPath(path: String): ImageFormat {
        return when (File(path).extension.lowercase()) {
            "avif" -> ImageFormat.AVIF
            "jpg", "jpeg" -> ImageFormat.JPEG
            "png" -> ImageFormat.PNG
            "webp" -> ImageFormat.WEBP
            "bmp" -> ImageFormat.BMP
            "gif" -> ImageFormat.GIF
            "heif", "heic" -> ImageFormat.HEIF
            else -> ImageFormat.UNKNOWN
        }
    }

    /**
     * Apply EXIF orientation transformation to bitmap from byte array
     * This ensures portrait photos display correctly after conversion
     */
    private fun applyExifOrientation(bitmap: Bitmap, imageData: ByteArray): Bitmap {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(imageData))
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            rotateImageIfRequired(bitmap, orientation)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF orientation from byte array", e)
            bitmap // Return original bitmap if EXIF reading fails
        }
    }

    /**
     * Apply EXIF orientation transformation to bitmap from file path
     */
    private fun applyExifOrientationFromFile(bitmap: Bitmap, filePath: String): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            rotateImageIfRequired(bitmap, orientation)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF orientation from file: $filePath", e)
            bitmap // Return original bitmap if EXIF reading fails
        }
    }

    /**
     * Rotate bitmap based on EXIF orientation value
     * This "bakes in" the orientation so the output image displays correctly
     */
    private fun rotateImageIfRequired(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.postRotate(90f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.postRotate(180f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postRotate(270f)
            }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postScale(1f, -1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_UNDEFINED -> {
                // No rotation needed
                return bitmap
            }
        }

        return try {
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            // Recycle original bitmap if a new one was created
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            rotatedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rotate bitmap", e)
            bitmap
        }
    }
}
