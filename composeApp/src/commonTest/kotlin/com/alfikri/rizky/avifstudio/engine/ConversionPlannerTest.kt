package com.alfikri.rizky.avifstudio.engine

import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.OutputFormat
import com.alfikri.rizky.avifstudio.model.SourceImage
import io.github.vinceglb.filekit.PlatformFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversionPlannerTest {

  @Test
  fun namesOutputsAfterTheirSources() {
    val names =
      ConversionPlanner.outputNames(
        listOf(source("beach.jpg"), source("mountain.png")),
        OutputFormat.AVIF,
      )
    assertEquals(listOf("beach.avif", "mountain.avif"), names)
  }

  /**
   * The reason this function exists: picking `IMG_0042.jpg` from two folders is completely routine,
   * and without deduplication the second output silently overwrites the first.
   */
  @Test
  fun neverLetsTwoOutputsClaimTheSameName() {
    val names =
      ConversionPlanner.outputNames(
        listOf(source("IMG_0042.jpg"), source("IMG_0042.png"), source("IMG_0042.heic")),
        OutputFormat.AVIF,
      )
    assertEquals(listOf("IMG_0042.avif", "IMG_0042 (2).avif", "IMG_0042 (3).avif"), names)
    assertEquals(names.size, names.toSet().size)
  }

  @Test
  fun producesOneNamePerSourceInOrder() {
    val sources = List(5) { source("photo$it.jpg") }
    val names = ConversionPlanner.outputNames(sources, OutputFormat.JPEG)
    assertEquals(5, names.size)
    assertTrue(names.all { it.endsWith(".jpg") })
  }

  @Test
  fun handlesAnEmptyBatch() {
    assertEquals(emptyList(), ConversionPlanner.outputNames(emptyList(), OutputFormat.AVIF))
  }

  @Test
  fun keepsTheOriginalWhenTheConversionCameOutNoSmaller() {
    val settings = ConversionSettings(skipIfLarger = true)
    assertTrue(ConversionPlanner.shouldKeepOriginal(1000, 1200, settings))
    assertTrue(ConversionPlanner.shouldKeepOriginal(1000, 1000, settings))
    assertFalse(ConversionPlanner.shouldKeepOriginal(1000, 999, settings))
  }

  @Test
  fun respectsTheOptOutForFormatChangingRecipes() {
    val settings = ConversionSettings(skipIfLarger = false)
    assertFalse(ConversionPlanner.shouldKeepOriginal(1000, 5000, settings))
  }

  /** An unknown source size would otherwise make every conversion look like a regression. */
  @Test
  fun doesNotKeepTheOriginalWhenItsSizeIsUnknown() {
    assertFalse(
      ConversionPlanner.shouldKeepOriginal(0, 500, ConversionSettings(skipIfLarger = true))
    )
  }

  private fun source(name: String) =
    SourceImage(id = name, file = PlatformFile("/tmp/$name"), displayName = name, sizeBytes = 1000)
}
