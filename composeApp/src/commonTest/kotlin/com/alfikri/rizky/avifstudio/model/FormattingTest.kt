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
  fun switchesDurationUnitAtOneSecond() {
    assertEquals("840 ms", formatDuration(840))
    assertEquals("1.0 s", formatDuration(1000))
    assertEquals("12.5 s", formatDuration(12_500))
  }

  @Test
  fun formatsDimensionsWithAMultiplicationSign() {
    assertEquals("4032 × 3024", formatDimensions(4032, 3024))
  }
}
