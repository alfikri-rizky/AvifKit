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

  /** True when the encoder is aiming at a byte budget rather than a fixed quality. */
  val hasSizeTarget: Boolean
    get() = outputFormat == OutputFormat.AVIF && (targetSizeBytes ?: 0L) > 0L

  /** The effective longest-edge cap, or `null` to keep the original size. */
  val effectiveMaxDimension: Int?
    get() = maxDimension?.takeIf { it > 0 }

  /** The effective JPEG/PNG encoder quality, clamped to the 0..100 the platform encoders accept. */
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
      // AvifKit documents preserveMetadata as a not-yet-implemented no-op, so leaving it false
      // keeps the UI honest — there is no toggle for it.
      preserveMetadata = false,
      maxDimension = effectiveMaxDimension,
      maxSize = targetSizeBytes?.takeIf { it > 0 },
      compressionStrategy = strategy,
    )

  companion object {
    const val DEFAULT_QUALITY = 75
    const val DEFAULT_SPEED = 6
    const val DEFAULT_ALPHA_QUALITY = 90

    /** Longest-edge caps offered in the UI. `null` means "keep original size". */
    val DIMENSION_CHOICES: List<Int?> = listOf(null, 4096, 2560, 1920, 1280, 1024, 720)

    /** Byte budgets offered by [Recipe.FIT_SIZE_LIMIT]. */
    val SIZE_TARGET_CHOICES: List<Long> =
      listOf(100L * 1024, 200L * 1024, 500L * 1024, 1024L * 1024, 2048L * 1024)
  }
}
