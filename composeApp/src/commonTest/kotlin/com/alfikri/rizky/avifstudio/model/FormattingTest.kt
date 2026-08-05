package com.alfikri.rizky.avifstudio.model

import kotlin.test.Test
import kotlin.test.assertEquals

class FormattingTest {

  @Test
  fun formatsBytesAcrossUnitBoundaries() {
    assertEquals("0 B", formatBytes(0))
    assertEquals("512 B", formatBytes(512))
    assertEquals("1023 B", formatBytes(1023))
    assertEquals("1 KB", formatBytes(1024))
    assertEquals("200 KB", formatBytes(200 * 1024))
    assertEquals("1.0 MB", formatBytes(1024L * 1024))
    assertEquals("1.5 MB", formatBytes(1024L * 1024 * 3 / 2))
    assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024))
  }

  /** A negative size means "not measured yet", not a file of negative length. */
  @Test
  fun rendersNegativeSizeAsUnknown() {
    assertEquals("—", formatBytes(-1))
  }

  @Test
  fun reportsSavingsPositiveWhenSmallerAndNegativeWhenLarger() {
    assertEquals(80, savingsPercent(originalBytes = 1000, newBytes = 200))
    assertEquals(0, savingsPercent(originalBytes = 1000, newBytes = 1000))
    assertEquals(-50, savingsPercent(originalBytes = 1000, newBytes = 1500))
  }

  /** Guards the division: an unreadable source can legitimately report zero bytes. */
  @Test
  fun survivesZeroByteOriginal() {
    assertEquals(0, savingsPercent(originalBytes = 0, newBytes = 500))
  }

  @Test
  fun wordsSavingsInTheDirectionOfTheChange() {
    assertEquals("80% smaller", formatSavings(1000, 200))
    assertEquals("50% larger", formatSavings(1000, 1500))
    assertEquals("same size", formatSavings(1000, 1000))
  }

  @Test
  fun switchesDurationUnitAtOneSecond() {
    assertEquals("840 ms", formatDuration(840))
    assertEquals("1.0 s", formatDuration(1000))
    assertEquals("12.5 s", formatDuration(12_500))
  }

  @Test
  fun capsLongestEdgeAndKeepsAspectRatio() {
    assertEquals(1920 to 1440, scaledDimensions(4032, 3024, 1920))
    assertEquals(1440 to 1920, scaledDimensions(3024, 4032, 1920))
  }

  @Test
  fun neverUpscalesAndTreatsNoCapAsKeepOriginal() {
    assertEquals(800 to 600, scaledDimensions(800, 600, 1920))
    assertEquals(4032 to 3024, scaledDimensions(4032, 3024, null))
    assertEquals(4032 to 3024, scaledDimensions(4032, 3024, 0))
  }

  /** A 500:1 panorama would round its short edge to 0 px without the coerce. */
  @Test
  fun neverScalesAnEdgeDownToZeroPixels() {
    val (width, height) = scaledDimensions(10_000, 20, 100)
    assertEquals(100, width)
    assertEquals(1, height)
  }

  @Test
  fun formatsDimensionsWithAMultiplicationSign() {
    assertEquals("4032 × 3024", formatDimensions(4032, 3024))
  }
}
