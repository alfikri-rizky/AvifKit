package com.alfikri.rizky.avifstudio.model

import com.alfikri.rizky.avifkit.ChromaSubsample
import com.alfikri.rizky.avifkit.CompressionStrategy

/**
 * Presets named after the job the user is trying to get done, not after codec settings.
 *
 * "Quality 75, speed 6, YUV420" tells a stranger nothing; "Web-ready" does. [CUSTOM] is the escape
 * hatch that exposes every knob for people who do know what YUV420 means. Titles and descriptions
 * live in the `composeResources` string files so they can be translated.
 */
enum class Recipe(val glyph: String) {
  WEB_READY("\uD83C\uDF10"),
  FIT_SIZE_LIMIT("\uD83C\uDFAF"),
  SMALLEST("\uD83E\uDEB6"),
  ARCHIVE("\uD83D\uDC8E"),
  TO_JPEG("\uD83D\uDD04"),
  TO_PNG("\uD83E\uDDCA"),
  CUSTOM("\uD83C\uDF9B\uFE0F");

  /** The settings this recipe starts from. [CUSTOM] simply keeps whatever was there before. */
  fun defaultSettings(): ConversionSettings =
    when (this) {
      WEB_READY ->
        ConversionSettings(
          outputFormat = OutputFormat.AVIF,
          quality = 75,
          speed = 6,
          subsample = ChromaSubsample.YUV420,
          maxDimension = 1920,
        )
      FIT_SIZE_LIMIT ->
        ConversionSettings(
          outputFormat = OutputFormat.AVIF,
          quality = 85,
          speed = 6,
          subsample = ChromaSubsample.YUV420,
          maxDimension = 2560,
          targetSizeBytes = 200L * 1024,
          strategy = CompressionStrategy.SMART,
        )
      SMALLEST ->
        ConversionSettings(
          outputFormat = OutputFormat.AVIF,
          quality = 55,
          speed = 8,
          subsample = ChromaSubsample.YUV420,
          alphaQuality = 70,
          maxDimension = 1280,
        )
      ARCHIVE ->
        ConversionSettings(
          outputFormat = OutputFormat.AVIF,
          quality = 95,
          // Slower encode buys real bytes back at this quality, and "archive" is the one job
          // where the user has already said they care about the result more than the wait.
          speed = 4,
          subsample = ChromaSubsample.YUV444,
          alphaQuality = 98,
          maxDimension = null,
        )
      // The two format-changing recipes deliberately keep their output even when it is larger:
      // the user asked for a JPEG/PNG because something downstream cannot read AVIF, and handing
      // back the original "because it was smaller" would defeat the whole request.
      TO_JPEG ->
        ConversionSettings(
          outputFormat = OutputFormat.JPEG,
          quality = 90,
          maxDimension = null,
          skipIfLarger = false,
        )
      TO_PNG ->
        ConversionSettings(
          outputFormat = OutputFormat.PNG,
          maxDimension = null,
          skipIfLarger = false,
        )
      CUSTOM -> ConversionSettings()
    }

  /** Recipes whose whole point is a byte budget get the size picker; the rest do not. */
  val showsSizeTarget: Boolean
    get() = this == FIT_SIZE_LIMIT

  companion object {
    /** Order shown in the picker — the two most common jobs first. */
    val displayOrder: List<Recipe> =
      listOf(WEB_READY, FIT_SIZE_LIMIT, SMALLEST, ARCHIVE, TO_JPEG, TO_PNG, CUSTOM)
  }
}
