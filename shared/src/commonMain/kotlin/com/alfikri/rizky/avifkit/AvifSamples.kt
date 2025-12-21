package com.alfikri.rizky.avifkit

/**
 * Sample usage examples for the AVIF Converter library
 * These examples demonstrate common use cases
 */
object AvifSamples {

    /**
     * Example 1: Convert image file to AVIF with balanced quality
     */
    suspend fun example1_ConvertFileToAvif(inputPath: String, outputPath: String): String {
        val converter = AvifConverter()

        return converter.convertToFile(
            input = ImageInput.from(inputPath),
            outputPath = outputPath,
            priority = Priority.BALANCED
        )
    }

    /**
     * Example 2: Convert with custom quality settings
     */
    suspend fun example2_CustomQuality(inputPath: String, outputPath: String): String {
        val converter = AvifConverter()

        val customOptions = EncodingOptions(
            quality = 90,
            speed = 4,
            maxDimension = 2048
        )

        return converter.convertToFile(
            input = ImageInput.from(inputPath),
            outputPath = outputPath,
            options = customOptions
        )
    }

    /**
     * Example 3: Convert with file size limit (adaptive compression)
     */
    suspend fun example3_WithSizeLimit(inputPath: String, outputPath: String): String {
        val converter = AvifConverter()

        val options = EncodingOptions.fromPriority(Priority.QUALITY).copy(
            maxSize = 500 * 1024  // Max 500KB
        )

        return converter.convertToFile(
            input = ImageInput.from(inputPath),
            outputPath = outputPath,
            options = options
        )
    }

    /**
     * Example 4: Convert ByteArray to AVIF
     */
    suspend fun example4_ConvertByteArray(imageData: ByteArray): ByteArray {
        val converter = AvifConverter()

        return converter.encodeAvif(
            input = ImageInput.from(imageData),
            priority = Priority.BALANCED
        )
    }

    /**
     * Example 5: Convert bitmap/UIImage to AVIF
     */
    suspend fun example5_ConvertBitmap(bitmap: PlatformBitmap, outputPath: String): String {
        val converter = AvifConverter()

        return converter.convertToFile(
            input = ImageInput.from(bitmap),
            outputPath = outputPath,
            priority = Priority.BALANCED
        )
    }

    /**
     * Example 6: Decode AVIF to bitmap/UIImage
     */
    suspend fun example6_DecodeAvif(avifPath: String): PlatformBitmap {
        val converter = AvifConverter()

        return converter.decodeAvif(
            input = ImageInput.from(avifPath)
        )
    }

    /**
     * Example 7: Get image information without full decode
     */
    suspend fun example7_GetImageInfo(imagePath: String): ImageInfo {
        val converter = AvifConverter()

        return converter.getImageInfo(
            input = ImageInput.from(imagePath)
        )
    }

    /**
     * Example 8: Check if file is AVIF format
     */
    fun example8_CheckIfAvif(imagePath: String): Boolean {
        val converter = AvifConverter()

        return converter.isAvifFile(
            input = ImageInput.from(imagePath)
        )
    }

    /**
     * Example 9: Social media optimization (Instagram)
     */
    suspend fun example9_ForInstagram(imagePath: String, outputPath: String): String {
        val converter = AvifConverter()

        val instagramOptions = EncodingOptions(
            quality = 85,
            maxDimension = 1080,
            maxSize = 1024 * 1024  // 1MB limit
        )

        return converter.convertToFile(
            input = ImageInput.from(imagePath),
            outputPath = outputPath,
            options = instagramOptions
        )
    }

    /**
     * Example 10: Batch conversion with different priorities
     */
    suspend fun example10_BatchConversion(
        inputPaths: List<String>,
        outputDir: String
    ): List<String> {
        val converter = AvifConverter()
        val results = mutableListOf<String>()

        inputPaths.forEachIndexed { index, inputPath ->
            val outputPath = "$outputDir/image_$index.avif"

            val savedPath = converter.convertToFile(
                input = ImageInput.from(inputPath),
                outputPath = outputPath,
                priority = when (index % 3) {
                    0 -> Priority.QUALITY
                    1 -> Priority.BALANCED
                    else -> Priority.SPEED
                }
            )

            results.add(savedPath)
        }

        return results
    }

    /**
     * Example 11: Progressive quality reduction to meet size requirement
     */
    suspend fun example11_ProgressiveCompression(
        imagePath: String,
        targetSizeKB: Int
    ): ByteArray {
        val converter = AvifConverter()
        val qualityLevels = listOf(90, 80, 70, 60, 50, 40)

        for (quality in qualityLevels) {
            val result = converter.encodeAvif(
                input = ImageInput.from(imagePath),
                options = EncodingOptions(
                    quality = quality,
                    maxSize = targetSizeKB * 1024L
                )
            )

            if (result.size <= targetSizeKB * 1024) {
                return result
            }
        }

        // Final attempt with minimum quality
        return converter.encodeAvif(
            input = ImageInput.from(imagePath),
            options = EncodingOptions(
                quality = 40,
                maxDimension = 1024,
                maxSize = targetSizeKB * 1024L
            )
        )
    }

    /**
     * Example 12: Safe conversion with error handling
     */
    suspend fun example12_SafeConversion(
        imagePath: String,
        outputPath: String
    ): Result<String> {
        return try {
            val converter = AvifConverter()

            // Check if input exists and is valid
            val info = converter.getImageInfo(ImageInput.from(imagePath))
            println("Converting ${info.width}x${info.height} ${info.format} image")

            // Convert
            val result = converter.convertToFile(
                input = ImageInput.from(imagePath),
                outputPath = outputPath,
                priority = Priority.BALANCED
            )

            Result.success(result)
        } catch (e: AvifError.EncodingFailed) {
            Result.failure(Exception("Encoding failed: ${e.message}"))
        } catch (e: AvifError.DecodingFailed) {
            Result.failure(Exception("Decoding failed: ${e.message}"))
        } catch (e: AvifError.FileError) {
            Result.failure(Exception("File error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
