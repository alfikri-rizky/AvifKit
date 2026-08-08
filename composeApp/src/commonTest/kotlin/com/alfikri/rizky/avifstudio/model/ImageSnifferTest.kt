package com.alfikri.rizky.avifstudio.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `isAvif` decides whether a file goes to libavif or to the platform decoder. Getting it wrong in
 * either direction is a failed conversion, not a cosmetic issue — the platform decoder cannot read
 * AVIF below Android 12, and libavif cannot read a JPEG.
 */
class ImageSnifferTest {

  @Test
  fun recognisesAvifFromTheMajorBrand() {
    assertTrue(ImageSniffer.isAvif(ftyp("avif", "mif1", "miaf")))
  }

  /**
   * The case a naive `ftypavif` prefix check gets wrong: plenty of encoders write `mif1` as the
   * major brand and list `avif` only among the compatible brands.
   */
  @Test
  fun recognisesAvifListedOnlyAsACompatibleBrand() {
    assertTrue(ImageSniffer.isAvif(ftyp("mif1", "miaf", "MA1B", "avif")))
    assertTrue(ImageSniffer.isAvif(ftyp("mif1", "miaf", "avif")))
  }

  @Test
  fun recognisesAvifImageSequences() {
    assertTrue(ImageSniffer.isAvif(ftyp("avis", "avif", "msf1")))
  }

  /** HEIF shares the ISO-BMFF container; routing one to libavif would fail the conversion. */
  @Test
  fun doesNotMistakeHeicForAvif() {
    assertFalse(ImageSniffer.isAvif(ftyp("heic", "mif1", "miaf")))
  }

  @Test
  fun survivesTruncatedAndEmptyInput() {
    assertFalse(ImageSniffer.isAvif(ByteArray(0)))
    assertFalse(ImageSniffer.isAvif(bytes(0x00, 0x00, 0x00, 0x20, 0x66)))
    assertFalse(ImageSniffer.isAvif(ByteArray(64)))
    // A JPEG must never be routed to the AVIF decoder.
    assertFalse(ImageSniffer.isAvif(bytes(0xFF, 0xD8, 0xFF, 0xE0) + ByteArray(16)))
  }

  /**
   * A partial download declares a box size larger than the bytes on hand.
   *
   * The previous version of this test cut the buffer to exactly its own length, so it truncated
   * nothing at all. This one genuinely truncates — but note it still cannot distinguish the scan
   * bound in `isAvif` being present from it being absent, because `startsWith` range-checks every
   * read and both variants return the same answer. What it does pin down is the behaviour a caller
   * depends on: a brand that did not arrive is not reported as present.
   */
  @Test
  fun doesNotReadPastTheEndWhenTheBoxSizeLies() {
    val full = ftyp("mif1", "miaf", "MA1B", "avif")
    // The header still claims a 32-byte box, but only 24 bytes arrived — and the `avif` brand
    // lives in the part that did not.
    val truncated = full.copyOf(24)
    assertTrue(full.size > truncated.size, "the fixture must actually be truncated")
    assertFalse(ImageSniffer.isAvif(truncated))
    assertTrue(ImageSniffer.isAvif(full))
  }

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
