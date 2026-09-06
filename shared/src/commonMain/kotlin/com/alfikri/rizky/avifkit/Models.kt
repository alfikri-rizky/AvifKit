package com.alfikri.rizky.avifkit

/** Priority presets for common conversion scenarios */
enum class Priority {
  SPEED, // Fastest encoding, lower quality
  QUALITY, // Best quality, slower encoding
  STORAGE, // Minimum file size, aggressive compression
  BALANCED, // Default - good balance of all factors
}

/** Sealed class representing different input types */
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

  data class FromFile(val file: PlatformFile) : ImageInput()

  companion object {
    fun from(data: ByteArray) = FromBytes(data)

    fun from(bitmap: PlatformBitmap) = FromBitmap(bitmap)

    fun from(path: String) = FromPath(path)

    fun from(file: PlatformFile) = FromFile(file)
  }
}

/**
 * Encoding options for AVIF conversion
 *
 * @param quality Base quality (0-100). May be auto-adjusted if maxSize is set. Ignored when
 *   [lossless] is true.
 * @param speed Encoding speed (0-10). 0=slowest/best, 10=fastest
 * @param subsample Chroma subsampling mode. Ignored when [lossless] is true (lossless forces
 *   YUV444, since chroma subsampling is inherently lossy).
 * @param alphaQuality Quality for alpha channel (0-100). Ignored when [lossless] is true.
 * @param lossless Enable true lossless compression. Overrides [quality], [alphaQuality], and
 *   [subsample]: encodes at quality 100 with YUV444 and identity matrix coefficients. Output files
 *   are significantly larger than lossy output.
 * @param preserveMetadata Copy the source file's EXIF and XMP into the AVIF — capture date, camera
 *   and lens, GPS, ratings, edit history. Off by default, because EXIF routinely carries the
 *   photographer's location and silently republishing it is the worse mistake. Only JPEG, PNG and
 *   WebP sources are read: HEIC keeps its metadata in ISOBMFF items this library does not parse, a
 *   GIF has nowhere to put EXIF (so animated output carries none), and an AVIF source is either
 *   returned byte for byte — metadata and all — or, when [maxSize] forces a re-encode, comes out
 *   stripped. Two values are rewritten rather than copied, because they describe the input:
 *   orientation becomes 1 (the pixels handed to the encoder are already upright, so copying "rotate
 *   90" through would make every reader rotate them a second time), and the EXIF pixel-dimension
 *   tags follow [maxDimension]. An EXIF block that cannot be parsed is dropped rather than embedded
 *   — libavif rejects a malformed payload at `avifEncoderWrite`, and losing metadata beats failing
 *   the conversion. ICC profiles are NOT carried: both platforms draw the source through an sRGB
 *   context on the way to the encoder, so the source's profile would misdescribe the pixels it was
 *   attached to. Metadata counts toward [maxSize].
 * @param maxDimension Auto-resize if larger. May be auto-adjusted if maxSize is set.
 * @param maxSize Target maximum file size in bytes. If set, will override other params to achieve
 *   this size through adaptive compression. Best-effort: if even the most aggressive settings
 *   cannot reach the target, the smallest achieved result is returned rather than throwing.
 *   Already-AVIF input is returned unchanged when it fits the target, and decoded + re-encoded when
 *   it does not.
 * @param compressionStrategy Strategy for adaptive compression when maxSize is set. SMART (default)
 *   finds highest quality within target size. STRICT finds smallest possible size.
 * @param encodeAnimation Keep the animation when the input is an animated GIF, writing an AVIF
 *   image sequence instead of a still. Set false to encode only the first frame. Has no effect on
 *   single-frame input. [maxSize] is NOT applied to animated output — hitting a byte budget would
 *   mean re-encoding every frame once per probe, so quality/[maxDimension] are the knobs here.
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
  val compressionStrategy: CompressionStrategy = CompressionStrategy.SMART,
  val encodeAnimation: Boolean = true,
) {
  init {
    require(quality in 0..100) { "Quality must be between 0 and 100" }
    require(speed in 0..10) { "Speed must be between 0 and 10" }
    require(alphaQuality in 0..100) { "Alpha quality must be between 0 and 100" }
    maxSize?.let { require(it > 0) { "Max size must be positive" } }
    maxDimension?.let { require(it > 0) { "Max dimension must be positive" } }
  }

  companion object {
    /** Create EncodingOptions from Priority preset */
    fun fromPriority(priority: Priority): EncodingOptions {
      return when (priority) {
        Priority.SPEED ->
          EncodingOptions(
            quality = 70,
            speed = 10,
            subsample = ChromaSubsample.YUV420,
            alphaQuality = 75,
            preserveMetadata = false,
            maxDimension = 1920,
          )
        Priority.QUALITY ->
          EncodingOptions(
            quality = 95,
            speed = 5,
            subsample = ChromaSubsample.YUV444,
            alphaQuality = 98,
            // The one preset that keeps it: "quality" here means an archival copy, and a photo
            // stripped of its capture date and camera is not that. The other presets exist to make
            // files small, where metadata is just bytes.
            preserveMetadata = true,
            maxDimension = 4096,
          )
        Priority.STORAGE ->
          EncodingOptions(
            quality = 65,
            speed = 8,
            subsample = ChromaSubsample.YUV420,
            alphaQuality = 70,
            preserveMetadata = false,
            maxDimension = 1280,
          )
        Priority.BALANCED ->
          EncodingOptions(
            quality = 80,
            speed = 6,
            subsample = ChromaSubsample.YUV420,
            alphaQuality = 85,
            preserveMetadata = false,
            maxDimension = 2048,
          )
      }
    }
  }
}

enum class ImageFormat {
  JPEG,
  PNG,
  WEBP,
  AVIF,
  BMP,
  GIF,
  HEIF,
  UNKNOWN,
}

enum class ChromaSubsample {
  YUV444,
  YUV422,
  YUV420,
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
 * - Example: If 200KB is the target, tries to find quality 85 that produces 198KB instead of
 *   stopping at quality 70 that produces 180KB
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
  /** Smart compression: Find highest quality that meets target size (Recommended) */
  SMART,

  /** Strict compression: Find smallest possible size (Maximum compression) */
  STRICT,
}

/**
 * @param frameCount Number of frames; 1 for a still image. Populated for animated GIF input and for
 *   AVIF image sequences (`avis`).
 * @param durationMillis Playback time of one loop. 0 when the image is not animated.
 * @param loopCount How many times playback repeats, where 0 means forever. Mirrors the GIF
 *   NETSCAPE2.0 loop count and AVIF's repetition count.
 */
data class ImageInfo(
  val width: Int,
  val height: Int,
  val format: ImageFormat = ImageFormat.UNKNOWN,
  val hasAlpha: Boolean = false,
  val fileSize: Long? = null,
  val frameCount: Int = 1,
  val durationMillis: Long = 0,
  val loopCount: Int = 0,
) {
  val isAnimated: Boolean
    get() = frameCount > 1
}

/** One frame of an animation, as returned by [AvifConverter.decodeAvifFrames]. */
data class AvifFrame(val bitmap: PlatformBitmap, val durationMillis: Int)

/**
 * Internal data class for decoded image data.
 *
 * [irotAngle]/[imirAxis] carry the AVIF `irot`/`imir` orientation properties (see [RgbaTransform]);
 * the Android JNI wrapper constructs this class reflectively, so the primary constructor signature
 * must stay in sync with `avif_jni_wrapper.cpp` (`([IIIIII)V`).
 *
 * [durationMillis] is only meaningful for frames pulled out of an image sequence.
 */
data class DecodedImage(
  val pixels: IntArray,
  val width: Int,
  val height: Int,
  val irotAngle: Int = 0,
  val imirAxis: Int = -1,
  val durationMillis: Int = 0,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false
    other as DecodedImage
    if (!pixels.contentEquals(other.pixels)) return false
    if (width != other.width) return false
    if (height != other.height) return false
    if (irotAngle != other.irotAngle) return false
    if (imirAxis != other.imirAxis) return false
    if (durationMillis != other.durationMillis) return false
    return true
  }

  override fun hashCode(): Int {
    var result = pixels.contentHashCode()
    result = 31 * result + width
    result = 31 * result + height
    result = 31 * result + irotAngle
    result = 31 * result + imirAxis
    result = 31 * result + durationMillis
    return result
  }
}
