package com.alfikri.rizky.avifstudio.model

import com.alfikri.rizky.avifkit.ChromaSubsample
import com.alfikri.rizky.avifkit.CompressionStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversionSettingsTest {

  @Test
  fun passesEverySettingThroughToTheEncoder() {
    val options =
      ConversionSettings(
          quality = 70,
          speed = 3,
          subsample = ChromaSubsample.YUV444,
          alphaQuality = 88,
          lossless = true,
          maxDimension = 1600,
          targetSizeBytes = 300_000,
          strategy = CompressionStrategy.STRICT,
        )
        .toEncodingOptions()

    assertEquals(70, options.quality)
    assertEquals(3, options.speed)
    assertEquals(ChromaSubsample.YUV444, options.subsample)
    assertEquals(88, options.alphaQuality)
    assertTrue(options.lossless)
    assertEquals(1600, options.maxDimension)
    assertEquals(300_000, options.maxSize)
    assertEquals(CompressionStrategy.STRICT, options.compressionStrategy)
  }

  /**
   * EncodingOptions throws on out-of-range values. A slider that can crash the app is worse than
   * one that saturates, so the clamp has to happen here.
   */
  @Test
  fun clampsOutOfRangeValuesInsteadOfThrowing() {
    val tooHigh =
      ConversionSettings(quality = 500, speed = 99, alphaQuality = -20).toEncodingOptions()
    assertEquals(100, tooHigh.quality)
    assertEquals(10, tooHigh.speed)
    assertEquals(0, tooHigh.alphaQuality)

    val tooLow = ConversionSettings(quality = -5, speed = -1).toEncodingOptions()
    assertEquals(0, tooLow.quality)
    assertEquals(0, tooLow.speed)
  }

  /** EncodingOptions rejects zero and negative values outright, so they must become null. */
  @Test
  fun treatsZeroAsNoLimitRatherThanAnInvalidLimit() {
    val options = ConversionSettings(maxDimension = 0, targetSizeBytes = 0).toEncodingOptions()
    assertNull(options.maxDimension)
    assertNull(options.maxSize)
  }

  @Test
  fun reportsASizeTargetOnlyForAvifOutput() {
    val avif = ConversionSettings(outputFormat = OutputFormat.AVIF, targetSizeBytes = 100_000)
    val jpeg = ConversionSettings(outputFormat = OutputFormat.JPEG, targetSizeBytes = 100_000)
    assertTrue(avif.hasSizeTarget)
    assertTrue(!jpeg.hasSizeTarget)
  }

  @Test
  fun neverAsksTheLibraryToPreserveMetadataItDoesNotYetSupport() {
    assertTrue(!ConversionSettings().toEncodingOptions().preserveMetadata)
  }

  @Test
  fun everyRecipeProducesEncoderOptionsTheLibraryAccepts() {
    // The EncodingOptions constructor validates in an init block; building one for each recipe is
    // what proves no preset ships an out-of-range value.
    Recipe.entries.forEach { recipe ->
      val settings = recipe.defaultSettings()
      val options = settings.toEncodingOptions()
      assertTrue(options.quality in 0..100, "${recipe.name} quality")
      assertTrue(options.speed in 0..10, "${recipe.name} speed")
    }
  }

  @Test
  fun formatChangingRecipesKeepTheirOutputEvenWhenItIsLarger() {
    assertTrue(!Recipe.TO_JPEG.defaultSettings().skipIfLarger)
    assertTrue(!Recipe.TO_PNG.defaultSettings().skipIfLarger)
    assertTrue(Recipe.WEB_READY.defaultSettings().skipIfLarger)
  }

  @Test
  fun recipesTargetTheFormatTheirNamePromises() {
    assertEquals(OutputFormat.JPEG, Recipe.TO_JPEG.defaultSettings().outputFormat)
    assertEquals(OutputFormat.PNG, Recipe.TO_PNG.defaultSettings().outputFormat)
    assertEquals(OutputFormat.AVIF, Recipe.WEB_READY.defaultSettings().outputFormat)
  }

  @Test
  fun onlyTheSizeLimitRecipeShowsTheSizePicker() {
    assertTrue(Recipe.FIT_SIZE_LIMIT.showsSizeTarget)
    assertEquals(listOf(Recipe.FIT_SIZE_LIMIT), Recipe.entries.filter { it.showsSizeTarget })
  }

  @Test
  fun everyRecipeIsReachableFromTheUi() {
    assertEquals(Recipe.entries.toSet(), Recipe.displayOrder.toSet())
    assertEquals(Recipe.entries.size, Recipe.displayOrder.size)
  }

  /**
   * The format-changing presets ask for the original size, which on a device with a 48 MB heap is a
   * guaranteed OOM on any modern photo rather than a faithful conversion.
   */
  @Test
  fun fillsInADimensionCapForPresetsThatAskedForOriginalSize() {
    val capped = Recipe.TO_WEBP.defaultSettings().withDeviceLimit(1280)
    assertEquals(1280, capped.maxDimension)
  }

  @Test
  fun leavesAPresetThatAlreadyNamesASizeAlone() {
    val webReady = Recipe.WEB_READY.defaultSettings()
    assertEquals(1920, webReady.maxDimension)
    assertEquals(webReady, webReady.withDeviceLimit(1280))
  }

  /** Devices with room to spare keep converting at full resolution. */
  @Test
  fun changesNothingWhereThereIsNoDeviceLimit() {
    val settings = Recipe.TO_JPEG.defaultSettings()
    assertEquals(settings, settings.withDeviceLimit(null))
    assertNull(settings.withDeviceLimit(null).maxDimension)
  }
}
