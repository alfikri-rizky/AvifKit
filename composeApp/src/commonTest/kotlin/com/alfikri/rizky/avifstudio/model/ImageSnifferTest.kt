package com.alfikri.rizky.avifstudio.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageSnifferTest {

  @Test
  fun recognisesAvifFromTheMajorBrand() {
    assertEquals(ImageSniffer.Kind.AVIF, ImageSniffer.detect(ftyp("avif", "mif1", "miaf")))
  }

  /**
   * The case a naive `ftypavif` prefix check gets wrong: plenty of encoders write `mif1` as the
   * major brand and only list `avif` among the compatible brands.
   */
  @Test
  fun recognisesAvifListedOnlyAsACompatibleBrand() {
    assertTrue(ImageSniffer.isAvif(ftyp("mif1", "miaf", "MA1B", "avif")))
    assertEquals(ImageSniffer.Kind.AVIF, ImageSniffer.detect(ftyp("mif1", "miaf", "avif")))
  }

  @Test
  fun recognisesAvifImageSequences() {
    assertEquals(ImageSniffer.Kind.AVIF, ImageSniffer.detect(ftyp("avis", "avif", "msf1")))
  }

  @Test
  fun doesNotMistakeHeicForAvif() {
    val heic = ftyp("heic", "mif1", "miaf")
    assertFalse(ImageSniffer.isAvif(heic))
    assertEquals(ImageSniffer.Kind.HEIF, ImageSniffer.detect(heic))
  }

  @Test
  fun recognisesTheOrdinaryFormats() {
    assertEquals(
      ImageSniffer.Kind.JPEG,
      ImageSniffer.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0) + ByteArray(16)),
    )
    assertEquals(
      ImageSniffer.Kind.PNG,
      ImageSniffer.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(16)),
    )
    assertEquals(
      ImageSniffer.Kind.GIF,
      ImageSniffer.detect("GIF89a".encodeToByteArray() + ByteArray(16)),
    )
    assertEquals(
      ImageSniffer.Kind.BMP,
      ImageSniffer.detect("BM".encodeToByteArray() + ByteArray(16)),
    )
  }

  /** A bare `RIFF` header is not WebP; the form type at byte 8 has to say so too. */
  @Test
  fun requiresBothTheRiffContainerAndTheWebpForm() {
    val webp = "RIFF".encodeToByteArray() + ByteArray(4) + "WEBP".encodeToByteArray() + ByteArray(8)
    assertEquals(ImageSniffer.Kind.WEBP, ImageSniffer.detect(webp))

    val wave = "RIFF".encodeToByteArray() + ByteArray(4) + "WAVE".encodeToByteArray() + ByteArray(8)
    assertEquals(ImageSniffer.Kind.UNKNOWN, ImageSniffer.detect(wave))
  }

  @Test
  fun survivesTruncatedAndEmptyInput() {
    assertFalse(ImageSniffer.isAvif(ByteArray(0)))
    assertFalse(ImageSniffer.isAvif(bytes(0x00, 0x00, 0x00, 0x20, 0x66)))
    assertEquals(ImageSniffer.Kind.UNKNOWN, ImageSniffer.detect(ByteArray(0)))
    assertEquals(ImageSniffer.Kind.UNKNOWN, ImageSniffer.detect(ByteArray(64)))
  }

  /**
   * A box size larger than the buffer must not make the brand scan read past the end — that is the
   * difference between a wrong answer and a crash on a truncated download.
   */
  @Test
  fun doesNotReadPastTheEndWhenTheBoxSizeLies() {
    val header = ftyp("mif1", "avif")
    val truncated = header.copyOf(20)
    // Still finds the brand that is actually present, and does not throw on the missing tail.
    assertTrue(ImageSniffer.isAvif(truncated))
  }

  /** Builds a real `ftyp` box: size, "ftyp", major brand, minor version, compatible brands. */
  private fun ftyp(majorBrand: String, vararg compatibleBrands: String): ByteArray {
    val size = 16 + compatibleBrands.size * 4
    val out = ByteArray(size)
    out[0] = ((size shr 24) and 0xFF).toByte()
    out[1] = ((size shr 16) and 0xFF).toByte()
    out[2] = ((size shr 8) and 0xFF).toByte()
    out[3] = (size and 0xFF).toByte()
    "ftyp".encodeToByteArray().copyInto(out, 4)
    majorBrand.encodeToByteArray().copyInto(out, 8)
    // bytes 12..15 are the minor version, left as zero
    compatibleBrands.forEachIndexed { index, brand ->
      brand.encodeToByteArray().copyInto(out, 16 + index * 4)
    }
    return out
  }

  private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
