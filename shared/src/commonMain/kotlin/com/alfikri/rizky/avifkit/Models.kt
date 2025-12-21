package com.alfikri.rizky.avifkit

/**
 * Priority presets for common conversion scenarios
 */
enum class Priority {
    SPEED,      // Fastest encoding, lower quality
    QUALITY,    // Best quality, slower encoding
    STORAGE,    // Minimum file size, aggressive compression
    BALANCED    // Default - good balance of all factors
}

/**
 * Sealed class representing different input types
 */
sealed class ImageInput {
    data class FromBytes(val data: ByteArray) : ImageInput() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as FromBytes
            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }

    data class FromBitmap(val bitmap: PlatformBitmap) : ImageInput()
    data class FromPath(val path: String) : ImageInput()

    companion object {
        fun from(data: ByteArray) = FromBytes(data)
        fun from(bitmap: PlatformBitmap) = FromBitmap(bitmap)
        fun from(path: String) = FromPath(path)
    }
}

/**
 * Encoding options for AVIF conversion
 *
 * @param quality Base quality (0-100). May be auto-adjusted if maxSize is set.
 * @param speed Encoding speed (0-10). 0=slowest/best, 10=fastest
 * @param subsample Chroma subsampling mode
 * @param alphaQuality Quality for alpha channel (0-100)
 * @param lossless Enable lossless compression
 * @param preserveMetadata Keep EXIF and other metadata
 * @param maxDimension Auto-resize if larger. May be auto-adjusted if maxSize is set.
 * @param maxSize Target maximum file size in bytes. If set, will override other params
 *                to achieve this size through adaptive compression.
 * @param compressionStrategy Strategy for adaptive compression when maxSize is set.
 *                           SMART (default) finds highest quality within target size.
 *                           STRICT finds smallest possible size.
 */
data class EncodingOptions(
    val quality: Int = 75,
    val speed: Int = 6,
    val subsample: ChromaSubsample = ChromaSubsample.YUV420,
    val alphaQuality: Int = 90,
    val lossless: Boolean = false,
    val preserveMetadata: Boolean = false,
    val maxDimension: Int? = null,
    val maxSize: Long? = null,
    val compressionStrategy: CompressionStrategy = CompressionStrategy.SMART
) {
    init {
        require(quality in 0..100) { "Quality must be between 0 and 100" }
        require(speed in 0..10) { "Speed must be between 0 and 10" }
        require(alphaQuality in 0..100) { "Alpha quality must be between 0 and 100" }
        maxSize?.let { require(it > 0) { "Max size must be positive" } }
        maxDimension?.let { require(it > 0) { "Max dimension must be positive" } }
    }

    companion object {
        /**
         * Create EncodingOptions from Priority preset
         */
        fun fromPriority(priority: Priority): EncodingOptions {
            return when (priority) {
                Priority.SPEED -> EncodingOptions(
                    quality = 70,
                    speed = 10,
                    subsample = ChromaSubsample.YUV420,
                    alphaQuality = 75,
                    preserveMetadata = false,
                    maxDimension = 1920
                )
                Priority.QUALITY -> EncodingOptions(
                    quality = 95,
                    speed = 2,
                    subsample = ChromaSubsample.YUV444,
                    alphaQuality = 98,
                    preserveMetadata = true,
                    maxDimension = null
                )
                Priority.STORAGE -> EncodingOptions(
                    quality = 65,
                    speed = 8,
                    subsample = ChromaSubsample.YUV420,
                    alphaQuality = 70,
                    preserveMetadata = false,
                    maxDimension = 1280
                )
                Priority.BALANCED -> EncodingOptions(
                    quality = 80,
                    speed = 6,
                    subsample = ChromaSubsample.YUV420,
                    alphaQuality = 85,
                    preserveMetadata = false,
                    maxDimension = 2048
                )
            }
        }
    }
}

enum class ImageFormat {
    JPEG, PNG, WEBP, AVIF, BMP, GIF, HEIF, UNKNOWN
}

enum class ChromaSubsample {
    YUV444, YUV422, YUV420
}

/**
 * Compression strategy for adaptive compression when maxSize is specified
 *
 * This determines how the library tries to meet the target file size:
 *
 * SMART (Recommended):
 * - Finds the HIGHEST QUALITY image that still meets the target size
 * - Uses binary search to find optimal quality setting
 * - Faster and produces better-looking images
 * - Example: If 200KB is the target, tries to find quality 85 that produces 198KB
 *   instead of stopping at quality 70 that produces 180KB
 * - Best for: General use, when you want the best possible quality within size limit
 *
 * STRICT (Maximum Compression):
 * - Finds the SMALLEST POSSIBLE image by trying all compression options
 * - Continues compressing even after meeting target size
 * - May take longer as it exhaustively tries more aggressive settings
 * - Example: If 200KB is the target, might compress down to 120KB
 * - Best for: Storage-critical scenarios, batch processing, when smallest size matters most
 */
enum class CompressionStrategy {
    /**
     * Smart compression: Find highest quality that meets target size (Recommended)
     */
    SMART,

    /**
     * Strict compression: Find smallest possible size (Maximum compression)
     */
    STRICT
}

data class ImageInfo(
    val width: Int,
    val height: Int,
    val format: ImageFormat = ImageFormat.UNKNOWN,
    val hasAlpha: Boolean = false,
    val fileSize: Long? = null
)

/**
 * Internal data class for decoded image data
 */
data class DecodedImage(
    val pixels: IntArray,
    val width: Int,
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as DecodedImage
        if (!pixels.contentEquals(other.pixels)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        return true
    }

    override fun hashCode(): Int {
        var result = pixels.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}
