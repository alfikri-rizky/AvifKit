package com.alfikri.rizky.avifkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncodingOptionsTest {

  @Test
  fun defaults_areValid() {
    val options = EncodingOptions()
    assertEquals(75, options.quality)
    assertEquals(6, options.speed)
    assertEquals(CompressionStrategy.SMART, options.compressionStrategy)
  }

  @Test
  fun outOfRangeValues_throw() {
    assertFailsWith<IllegalArgumentException> { EncodingOptions(quality = 101) }
    assertFailsWith<IllegalArgumentException> { EncodingOptions(quality = -1) }
    assertFailsWith<IllegalArgumentException> { EncodingOptions(speed = 11) }
    assertFailsWith<IllegalArgumentException> { EncodingOptions(alphaQuality = 101) }
    assertFailsWith<IllegalArgumentException> { EncodingOptions(maxSize = 0) }
    assertFailsWith<IllegalArgumentException> { EncodingOptions(maxDimension = 0) }
  }

  @Test
  fun onlyTheQualityPreset_keepsMetadata() {
    // Exif carries GPS, so every preset whose job is small files strips it; QUALITY is the archival
    // one and keeps it. The default stays off (see below) so an upgrade can't start republishing
    // locations on its own.
    assertTrue(EncodingOptions.fromPriority(Priority.QUALITY).preserveMetadata)
    listOf(Priority.SPEED, Priority.STORAGE, Priority.BALANCED).forEach { priority ->
      assertFalse(
        EncodingOptions.fromPriority(priority).preserveMetadata,
        "preset $priority optimises for size and must not carry metadata",
      )
    }
  }

  @Test
  fun defaults_stripMetadata() {
    assertFalse(EncodingOptions().preserveMetadata)
  }

  @Test
  fun presets_mapToExpectedTradeoffs() {
    val speed = EncodingOptions.fromPriority(Priority.SPEED)
    val quality = EncodingOptions.fromPriority(Priority.QUALITY)
    val storage = EncodingOptions.fromPriority(Priority.STORAGE)
    assertEquals(10, speed.speed)
    assertEquals(ChromaSubsample.YUV444, quality.subsample)
    assertEquals(95, quality.quality)
    assertEquals(1280, storage.maxDimension)
  }
}
