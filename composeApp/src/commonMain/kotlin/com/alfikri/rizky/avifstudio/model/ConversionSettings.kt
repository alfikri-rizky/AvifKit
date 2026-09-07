package com.alfikri.rizky.avifstudio.model

import com.alfikri.rizky.avifkit.ChromaSubsample
import com.alfikri.rizky.avifkit.CompressionStrategy
import com.alfikri.rizky.avifkit.EncodingOptions

/**
 * Everything a conversion needs, in one value the UI can edit directly.
 *
 * Deliberately a superset of [EncodingOptions]: it also covers the JPEG/PNG output paths, which
 * AvifKit has no opinion about. [toEncodingOptions] clamps every field, because `EncodingOptions`
 * throws on out-of-range input and a slider that can crash the app is worse than one that
 * saturates.
 */
data class ConversionSettings(
  val outputFormat: OutputFormat = OutputFormat.AVIF,
  val quality: Int = DEFAULT_QUALITY,
  val speed: Int = DEFAULT_SPEED,
  val subsample: ChromaSubsample = ChromaSubsample.YUV420,
  val alphaQuality: Int = DEFAULT_ALPHA_QUALITY,
  val lossless: Boolean = false,
  /**
   * Carry the source's EXIF and XMP into the output.
   *
   * Off by default, and deliberately not part of any preset: EXIF routinely carries the GPS
   * coordinates the photo was taken at, and an app that quietly republished those would be doing
   * something the user never asked for. AVIF-only — the JPEG/PNG/WebP output paths here do not
   * write metadata.
   *
   * GPS is the one tag this cannot promise. Android's system photo picker hands back Exif with the
   * GPS values zeroed unless the app holds ACCESS_MEDIA_LOCATION, which on API 33+ only means
   * anything alongside READ_MEDIA_IMAGES — the "Photos and videos" permission this app deliberately
   * does without (see the comment in AndroidManifest.xml). Files chosen through "Add files" (SAF)
   * arrive intact, which is what the toggle's hint tells the user.
   */
  val preserveMetadata: Boolean = false,
  val maxDimension: Int? = null,
  val targetSizeBytes: Long? = null,
  val strategy: CompressionStrategy = CompressionStrategy.SMART,
  /**
   * Keep the original file when the conversion came out no smaller.
   *
   * An app whose promise is "smaller files" must not quietly hand back a bigger one. Re-encoding an
   * already-optimised JPEG, or pushing a flat-colour PNG through AVIF, really does grow the file.
   * Format-changing recipes turn this off, because there the format *is* the point.
   */
  val skipIfLarger: Boolean = true,
) {

  /** AVIF only — no other encoder here can target a byte budget. */
  val hasSizeTarget: Boolean
    get() = outputFormat == OutputFormat.AVIF && (targetSizeBytes ?: 0L) > 0L

  val effectiveMaxDimension: Int?
    get() = maxDimension?.takeIf { it > 0 }

  val effectiveQuality: Int
    get() = quality.coerceIn(0, 100)

  /**
   * Translates to AvifKit's [EncodingOptions]. Only meaningful when [outputFormat] is
   * [OutputFormat.AVIF]; the JPEG/PNG paths read [effectiveQuality] and [effectiveMaxDimension].
   */
  fun toEncodingOptions(): EncodingOptions =
    EncodingOptions(
      quality = effectiveQuality,
      speed = speed.coerceIn(0, 10),
      subsample = subsample,
      alphaQuality = alphaQuality.coerceIn(0, 100),
      lossless = lossless,
      preserveMetadata = preserveMetadata,
      maxDimension = effectiveMaxDimension,
      maxSize = targetSizeBytes?.takeIf { it > 0 },
      compressionStrategy = strategy,
    )

  /**
   * "Original size" is the honest default for the format-changing recipes, but on a device whose
   * whole heap is 48 MB, decoding a 12 MP photo at full resolution fails rather than converts. A
   * preset that already names a size, and any size the user picked, are left alone.
   */
  fun withDeviceLimit(cap: Int?): ConversionSettings =
    if (cap == null || maxDimension != null) this else copy(maxDimension = cap)

  companion object {
    const val DEFAULT_QUALITY = 75
    const val DEFAULT_SPEED = 6
    const val DEFAULT_ALPHA_QUALITY = 90

    /** Longest-edge caps offered in the UI. `null` means "keep original size". */
    val DIMENSION_CHOICES: List<Int?> = listOf(null, 4096, 2560, 1920, 1280, 1024, 720)

    val SIZE_TARGET_CHOICES: List<Long> =
      listOf(100L * 1024, 200L * 1024, 500L * 1024, 1024L * 1024, 2048L * 1024)
  }
}
