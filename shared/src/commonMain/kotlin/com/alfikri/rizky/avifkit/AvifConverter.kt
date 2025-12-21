package com.alfikri.rizky.avifkit

/**
 * Main AVIF converter interface for converting images to and from AVIF format
 */
expect class AvifConverter() {

    /**
     * Convert any supported image format to AVIF and return as Bitmap
     * @param input Can be ByteArray, Bitmap, or file path
     * @param priority Quick preset for common scenarios (default: BALANCED)
     * @param options Custom encoding options (overrides priority if provided)
     * @return Platform-specific Bitmap (Android: android.graphics.Bitmap, iOS: UIImage)
     */
    suspend fun convertToBitmap(
        input: ImageInput,
        priority: Priority = Priority.BALANCED,
        options: EncodingOptions? = null
    ): PlatformBitmap

    /**
     * Convert any supported image format to AVIF and save to file
     * @param input Can be ByteArray, Bitmap, or file path
     * @param outputPath Path where the AVIF file will be saved
     * @param priority Quick preset for common scenarios (default: BALANCED)
     * @param options Custom encoding options (overrides priority if provided)
     * @return Path to the saved file
     */
    suspend fun convertToFile(
        input: ImageInput,
        outputPath: String,
        priority: Priority = Priority.BALANCED,
        options: EncodingOptions? = null
    ): String

    /**
     * Encode image to AVIF format and return as ByteArray
     * @param input Can be ByteArray, Bitmap, or file path
     * @param priority Quick preset for common scenarios (default: BALANCED)
     * @param options Custom encoding options (overrides priority if provided)
     * @return AVIF encoded data as ByteArray
     */
    suspend fun encodeAvif(
        input: ImageInput,
        priority: Priority = Priority.BALANCED,
        options: EncodingOptions? = null
    ): ByteArray

    /**
     * Decode AVIF data to platform bitmap
     * @param input AVIF data as ByteArray or file path
     * @return Platform-specific Bitmap
     */
    suspend fun decodeAvif(
        input: ImageInput
    ): PlatformBitmap

    /**
     * Check if AVIF encoding/decoding is supported on this platform
     * @return true if AVIF is supported
     */
    fun isAvifSupported(): Boolean

    /**
     * Check if the input data is in AVIF format
     * @param input Image data to check
     * @return true if input is AVIF format
     */
    fun isAvifFile(input: ImageInput): Boolean

    /**
     * Get image information without full decoding
     * @param input Image data to inspect
     * @return ImageInfo containing dimensions and format
     */
    suspend fun getImageInfo(input: ImageInput): ImageInfo
}
