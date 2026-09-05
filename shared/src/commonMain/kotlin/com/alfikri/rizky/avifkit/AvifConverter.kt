package com.alfikri.rizky.avifkit

/** Main AVIF converter interface for converting images to and from AVIF format */
expect class AvifConverter() {

  /**
   * Convert any supported image format to AVIF and return as Bitmap
   *
   * @param input Can be ByteArray, Bitmap, or file path
   * @param priority Quick preset for common scenarios (default: BALANCED)
   * @param options Custom encoding options (overrides priority if provided)
   * @return Platform-specific Bitmap (Android: android.graphics.Bitmap, iOS: UIImage)
   */
  @Throws(Exception::class)
  suspend fun convertToBitmap(
    input: ImageInput,
    priority: Priority = Priority.BALANCED,
    options: EncodingOptions? = null,
  ): PlatformBitmap

  /**
   * Convert any supported image format to AVIF and save to file
   *
   * @param input Can be ByteArray, Bitmap, or file path
   * @param outputPath Path where the AVIF file will be saved
   * @param priority Quick preset for common scenarios (default: BALANCED)
   * @param options Custom encoding options (overrides priority if provided)
   * @return Path to the saved file
   */
  @Throws(Exception::class)
  suspend fun convertToFile(
    input: ImageInput,
    outputPath: String,
    priority: Priority = Priority.BALANCED,
    options: EncodingOptions? = null,
  ): String

  /**
   * Convert any supported image format to AVIF and save to PlatformFile
   *
   * @param input Can be ByteArray, Bitmap, file path, or PlatformFile
   * @param output PlatformFile where the AVIF will be saved
   * @param priority Quick preset for common scenarios (default: BALANCED)
   * @param options Custom encoding options (overrides priority if provided)
   * @return PlatformFile pointing to the saved file
   */
  @Throws(Exception::class)
  suspend fun convertToFile(
    input: ImageInput,
    output: PlatformFile,
    priority: Priority = Priority.BALANCED,
    options: EncodingOptions? = null,
  ): PlatformFile

  /**
   * Encode image to AVIF format and return as ByteArray
   *
   * @param input Can be ByteArray, Bitmap, or file path
   * @param priority Quick preset for common scenarios (default: BALANCED)
   * @param options Custom encoding options (overrides priority if provided)
   * @return AVIF encoded data as ByteArray
   */
  @Throws(Exception::class)
  suspend fun encodeAvif(
    input: ImageInput,
    priority: Priority = Priority.BALANCED,
    options: EncodingOptions? = null,
  ): ByteArray

  /**
   * Decode AVIF data to platform bitmap
   *
   * @param input AVIF data as ByteArray or file path
   * @return Platform-specific Bitmap
   */
  @Throws(Exception::class) suspend fun decodeAvif(input: ImageInput): PlatformBitmap

  /**
   * Decode the frames of an AVIF image sequence, in playback order.
   *
   * A still AVIF yields a single frame with a zero duration, so callers do not need to branch on
   * [ImageInfo.isAnimated] first. Unlike [decodeAvif] this holds every frame at once — `width *
   * height * 4 * frameCount` bytes, which is 92 MB for a 48-frame 800x600 animation — so both
   * limits below exist to keep that bounded and are worth setting for anything on-screen.
   *
   * @param input AVIF data as ByteArray or file path
   * @param maxDimension Downscale each frame so its longest edge is at most this. Null keeps the
   *   encoded size.
   * @param maxFrames Stop after this many frames. The result is truncated, not resampled, so the
   *   animation it plays back is shorter rather than faster.
   * @return One entry per frame, each with the delay to show it for
   */
  @Throws(Exception::class)
  suspend fun decodeAvifFrames(
    input: ImageInput,
    maxDimension: Int? = null,
    maxFrames: Int = Int.MAX_VALUE,
  ): List<AvifFrame>

  /**
   * Check if AVIF encoding/decoding is supported on this platform
   *
   * @return true if AVIF is supported
   */
  fun isAvifSupported(): Boolean

  /**
   * Check if the input data is in AVIF format
   *
   * @param input Image data to check
   * @return true if input is AVIF format
   */
  fun isAvifFile(input: ImageInput): Boolean

  /**
   * Get image information without full decoding
   *
   * @param input Image data to inspect
   * @return ImageInfo containing dimensions and format
   */
  @Throws(Exception::class) suspend fun getImageInfo(input: ImageInput): ImageInfo
}
