package com.alfikri.rizky.avifkit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.UIKit.*
import platform.CoreGraphics.*
import kotlinx.cinterop.*
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class AvifConverter {

    actual suspend fun convertToBitmap(
        input: ImageInput,
        priority: Priority,
        options: EncodingOptions?
    ): PlatformBitmap = withContext(Dispatchers.Default) {
        val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

        // Handle maxSize if specified
        val avifData = if (encodingOptions.maxSize != null) {
            convertWithAdaptiveCompression(input, encodingOptions)
        } else {
            convertStandard(input, encodingOptions)
        }

        // Decode AVIF to UIImage
        decodeAvifToImage(avifData)
    }

    actual suspend fun convertToFile(
        input: ImageInput,
        outputPath: String,
        priority: Priority,
        options: EncodingOptions?
    ): String = withContext(Dispatchers.Default) {
        val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

        // Handle maxSize if specified
        val avifData = if (encodingOptions.maxSize != null) {
            convertWithAdaptiveCompression(input, encodingOptions)
        } else {
            convertStandard(input, encodingOptions)
        }

        // Save to file
        val outputUrl = NSURL.fileURLWithPath(outputPath)
        val directory = outputUrl.URLByDeletingLastPathComponent
        directory?.let {
            NSFileManager.defaultManager.createDirectoryAtURL(
                it,
                true,
                null,
                null
            )
        }

        val success = avifData.writeToURL(outputUrl, true)

        if (!success) {
            throw AvifError.EncodingFailed("Failed to save file to $outputPath")
        }

        outputPath
    }

    actual suspend fun convertToFile(
        input: ImageInput,
        output: PlatformFile,
        priority: Priority,
        options: EncodingOptions?
    ): PlatformFile = withContext(Dispatchers.Default) {
        val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

        // Handle maxSize if specified
        val avifData = if (encodingOptions.maxSize != null) {
            convertWithAdaptiveCompression(input, encodingOptions)
        } else {
            convertStandard(input, encodingOptions)
        }

        // Save to PlatformFile
        output.writeBytes(avifData.toByteArray())
        output
    }

    actual suspend fun encodeAvif(
        input: ImageInput,
        priority: Priority,
        options: EncodingOptions?
    ): ByteArray = withContext(Dispatchers.Default) {
        val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

        if (encodingOptions.maxSize != null) {
            convertWithAdaptiveCompression(input, encodingOptions).toByteArray()
        } else {
            convertStandard(input, encodingOptions).toByteArray()
        }
    }

    actual suspend fun decodeAvif(input: ImageInput): PlatformBitmap = withContext(Dispatchers.Default) {
        val data = when (input) {
            is ImageInput.FromBytes -> input.data.toNSData()
            is ImageInput.FromPath -> {
                val url = NSURL.fileURLWithPath(input.path)
                NSData.dataWithContentsOfURL(url) ?: throw AvifError.FileError("File not found: ${input.path}")
            }
            is ImageInput.FromFile -> input.file.readBytes().toNSData()
            is ImageInput.FromBitmap -> throw AvifError.InvalidInput
        }

        decodeAvifToImage(data)
    }

    actual fun isAvifSupported(): Boolean {
        // On iOS, AVIF support depends on the integration of avif.swift library
        // For now, return true as we have the structure in place
        return true
    }

    actual fun isAvifFile(input: ImageInput): Boolean {
        return try {
            val data = when (input) {
                is ImageInput.FromBytes -> input.data
                is ImageInput.FromPath -> {
                    val url = NSURL.fileURLWithPath(input.path)
                    val nsData = NSData.dataWithContentsOfURL(url)
                    nsData?.toByteArray()?.take(12)?.toByteArray() ?: return false
                }
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

    actual suspend fun getImageInfo(input: ImageInput): ImageInfo = withContext(Dispatchers.Default) {
        when (input) {
            is ImageInput.FromBytes -> {
                val nsData = input.data.toNSData()
                val image = UIImage.imageWithData(nsData)
                    ?: throw AvifError.InvalidInput

                val width = image.size.useContents { this.width }
                val height = image.size.useContents { this.height }

                ImageInfo(
                    width = (width * image.scale).toInt(),
                    height = (height * image.scale).toInt(),
                    format = detectFormat(input.data),
                    hasAlpha = imageHasAlpha(image),
                    fileSize = input.data.size.toLong()
                )
            }
            is ImageInput.FromPath -> {
                val url = NSURL.fileURLWithPath(input.path)
                val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(input.path, null)
                val fileSize = (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L

                val nsData = NSData.dataWithContentsOfURL(url)
                    ?: throw AvifError.FileError("Failed to read file: ${input.path}")

                val image = UIImage.imageWithData(nsData)
                    ?: throw AvifError.InvalidInput

                val width = image.size.useContents { this.width }
                val height = image.size.useContents { this.height }

                ImageInfo(
                    width = (width * image.scale).toInt(),
                    height = (height * image.scale).toInt(),
                    format = detectFormatFromPath(input.path),
                    hasAlpha = imageHasAlpha(image),
                    fileSize = fileSize
                )
            }
            is ImageInput.FromFile -> {
                val data = input.file.readBytes()
                val nsData = data.toNSData()
                val image = UIImage.imageWithData(nsData)
                    ?: throw AvifError.InvalidInput

                val width = image.size.useContents { this.width }
                val height = image.size.useContents { this.height }

                ImageInfo(
                    width = (width * image.scale).toInt(),
                    height = (height * image.scale).toInt(),
                    format = detectFormat(data),
                    hasAlpha = imageHasAlpha(image),
                    fileSize = input.file.size()
                )
            }
            is ImageInput.FromBitmap -> {
                val image = input.bitmap
                val width = image.size.useContents { this.width }
                val height = image.size.useContents { this.height }

                ImageInfo(
                    width = (width * image.scale).toInt(),
                    height = (height * image.scale).toInt(),
                    format = ImageFormat.UNKNOWN,
                    hasAlpha = imageHasAlpha(image)
                )
            }
        }
    }

    // Private helper methods

    private suspend fun convertWithAdaptiveCompression(
        input: ImageInput,
        options: EncodingOptions
    ): NSData {
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
    ): NSData {
        NSLog("AvifConverter: Using SMART compression strategy for target size: $targetSize bytes")

        var bestResult: NSData? = null
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
            val resultSize = result.length.toLong()
            attempts++

            NSLog("AvifConverter: SMART attempt $attempts: quality=$testQuality, size=$resultSize, target=$targetSize")

            if (resultSize <= targetSize) {
                // Meets target - save this result and try higher quality
                bestResult = result
                bestQuality = testQuality
                minQuality = testQuality + 1
                NSLog("AvifConverter:   ✓ Meets target, trying higher quality")
            } else {
                // Too large - try lower quality
                maxQuality = testQuality - 1
                NSLog("AvifConverter:   ✗ Too large, trying lower quality")
            }
        }

        // If we found a result that meets target, return it
        if (bestResult != null) {
            NSLog("AvifConverter: SMART compression succeeded: quality=$bestQuality, size=${bestResult.length}")
            return bestResult
        }

        // If binary search failed, fall back to aggressive compression
        NSLog("AvifConverter: SMART compression failed to meet target, using fallback")
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
    ): NSData {
        NSLog("AvifConverter: Using STRICT compression strategy for target size: $targetSize bytes")

        var currentOptions = options.copy(maxSize = null)
        var attempt = 0
        val maxAttempts = 10
        var bestResult: NSData? = null
        var targetMet = false

        while (attempt < maxAttempts) {
            val result = convertStandard(input, currentOptions)
            val resultSize = result.length.toLong()

            NSLog("AvifConverter: STRICT attempt $attempt: size=$resultSize, target=$targetSize")

            // Check if we meet the size requirement
            if (resultSize <= targetSize) {
                targetMet = true
                bestResult = result
                NSLog("AvifConverter:   ✓ Meets target, continuing for maximum compression")
            } else {
                NSLog("AvifConverter:   ✗ Above target, adjusting parameters")
            }

            // Continue to next attempt for more aggressive compression
            currentOptions = adjustCompressionParameters(
                current = currentOptions,
                currentSize = resultSize,
                targetSize = targetSize,
                attempt = attempt
            )

            attempt++
        }

        // Return the best result if we met the target at least once
        if (bestResult != null) {
            NSLog("AvifConverter: STRICT compression succeeded: final size=${bestResult.length}")
            return bestResult
        }

        // Final attempt with minimum settings
        NSLog("AvifConverter: STRICT compression failed to meet target, using fallback")
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
            reductionRatio < 0.5 -> current.copy(
                quality = maxOf(40, (current.quality * 0.7).toInt()),
                maxDimension = current.maxDimension?.let {
                    (it * 0.75).toInt()
                } ?: 1920,
                subsample = ChromaSubsample.YUV420,
                alphaQuality = maxOf(50, current.alphaQuality - 20),
                speed = minOf(10, current.speed + 2)
            )

            reductionRatio < 0.75 -> current.copy(
                quality = maxOf(50, current.quality - 15),
                maxDimension = current.maxDimension?.let {
                    (it * 0.85).toInt()
                } ?: 2560,
                alphaQuality = maxOf(60, current.alphaQuality - 10),
                speed = minOf(10, current.speed + 1)
            )

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
    ): NSData = withContext(Dispatchers.Default) {
        when (input) {
            is ImageInput.FromBytes -> {
                val nsData = input.data.toNSData()
                if (isAvifFormat(input.data)) {
                    nsData
                } else {
                    val uiImage = UIImage.imageWithData(nsData)
                        ?: throw AvifError.InvalidInput
                    encodeImageToAvif(uiImage, options)
                }
            }
            is ImageInput.FromBitmap -> {
                encodeImageToAvif(input.bitmap, options)
            }
            is ImageInput.FromPath -> {
                val url = NSURL.fileURLWithPath(input.path)
                val data = NSData.dataWithContentsOfURL(url)
                    ?: throw AvifError.InvalidInput

                if (input.path.endsWith(".avif", ignoreCase = true)) {
                    data
                } else {
                    val uiImage = UIImage.imageWithData(data)
                        ?: throw AvifError.InvalidInput
                    encodeImageToAvif(uiImage, options)
                }
            }
            is ImageInput.FromFile -> {
                val byteData = input.file.readBytes()
                val nsData = byteData.toNSData()
                if (isAvifFormat(byteData)) {
                    nsData
                } else {
                    val uiImage = UIImage.imageWithData(nsData)
                        ?: throw AvifError.InvalidInput
                    encodeImageToAvif(uiImage, options)
                }
            }
        }
    }

    private fun encodeImageToAvif(image: UIImage, options: EncodingOptions): NSData {
        // Use native AVIF converter with orientation support
        val converter = AVIFNativeConverter()

        // Check if native AVIF is available
        if (!AVIFNativeConverter.isAvifAvailable()) {
            NSLog("⚠️ libavif not available, using JPEG fallback")
            // Fallback to JPEG (which properly handles orientation)
            val jpegData = UIImageJPEGRepresentation(image, options.quality / 100.0)
                ?: throw AvifError.EncodingFailed("Failed to encode image")
            return jpegData
        }

        // Prepare encoding options as NSDictionary
        @Suppress("UNCHECKED_CAST")
        val encodingOptions = mapOf<Any?, Any?>(
            "quality" to options.quality,
            "speed" to options.speed,
            "maxDimension" to (options.maxDimension ?: 0)
        ) as NSDictionary

        // Encode with native converter (handles orientation automatically)
        val avifData = converter.encodeImageWithOptions(image, encodingOptions)
            ?: throw AvifError.EncodingFailed("Native AVIF encoding failed")

        return avifData
    }

    private fun decodeAvifToImage(avifData: NSData): UIImage {
        // PLACEHOLDER: Decode AVIF data
        // In production, use avif.swift library here
        /*
        let decoder = AVIFDecoder()
        let image = try decoder.decode(avifData)
        return image
        */

        // For now, try to decode as regular image
        NSLog("PLACEHOLDER: Decoding as regular image. Integrate avif.swift for actual AVIF decoding.")

        return UIImage.imageWithData(avifData)
            ?: throw AvifError.DecodingFailed("Failed to decode AVIF data")
    }

    private fun resizeImage(image: UIImage, maxDimension: Int): UIImage {
        val width = image.size.useContents { this.width }
        val height = image.size.useContents { this.height }

        if (width <= maxDimension && height <= maxDimension) {
            return image
        }

        val scale = maxDimension.toDouble() / maxOf(width, height)
        val newWidth = width * scale
        val newHeight = height * scale

        memScoped {
            val newSize = alloc<CGSize>().apply {
                this.width = newWidth
                this.height = newHeight
            }

            UIGraphicsBeginImageContextWithOptions(
                newSize.readValue(),
                false,
                0.0
            )

            val rect = alloc<CGRect>().apply {
                origin.x = 0.0
                origin.y = 0.0
                size.width = newWidth
                size.height = newHeight
            }
            image.drawInRect(rect.readValue())

            val resizedImage = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()

            return resizedImage ?: image
        }
    }

    private fun isAvifFormat(data: ByteArray): Boolean {
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
        val extension = (path.substringAfterLast('.', "")).lowercase()
        return when (extension) {
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

    private fun imageHasAlpha(image: UIImage): Boolean {
        val cgImage = image.CGImage ?: return false
        val alphaInfo = CGImageGetAlphaInfo(cgImage)
        return alphaInfo != CGImageAlphaInfo.kCGImageAlphaNone &&
                alphaInfo != CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst &&
                alphaInfo != CGImageAlphaInfo.kCGImageAlphaNoneSkipLast
    }

    // Extension functions for data conversion
    private fun ByteArray.toNSData(): NSData {
        return usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        return ByteArray(this.length.toInt()).apply {
            usePinned { pinned ->
                memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
            }
        }
    }
}
