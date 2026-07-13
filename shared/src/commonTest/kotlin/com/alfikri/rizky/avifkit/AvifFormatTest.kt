package com.alfikri.rizky.avifkit

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvifFormatTest {

  /** Build an ISO-BMFF `ftyp` box: size + "ftyp" + major brand + minor version + compat brands. */
  private fun ftyp(majorBrand: String, vararg compatibleBrands: String): ByteArray {
    val boxSize = 16 + compatibleBrands.size * 4
    val out = ByteArray(boxSize)
    out[0] = (boxSize ushr 24).toByte()
    out[1] = (boxSize ushr 16).toByte()
    out[2] = (boxSize ushr 8).toByte()
    out[3] = boxSize.toByte()
    writeAscii(out, 4, "ftyp")
    writeAscii(out, 8, majorBrand)
    // bytes 12..15: minor version, left zero
    compatibleBrands.forEachIndexed { i, brand -> writeAscii(out, 16 + i * 4, brand) }
    return out
  }

  private fun writeAscii(target: ByteArray, offset: Int, text: String) {
    text.forEachIndexed { i, c -> target[offset + i] = c.code.toByte() }
  }

  @Test
  fun majorBrandAvif_isDetected() {
    assertTrue(AvifFormat.isAvif(ftyp("avif", "mif1", "miaf")))
  }

  @Test
  fun majorBrandAvis_imageSequence_isDetected() {
    assertTrue(AvifFormat.isAvif(ftyp("avis", "msf1", "miaf")))
  }

  @Test
  fun compatibleBrandAvif_withMif1Major_isDetected() {
    // Several encoders emit mif1 as the major brand with avif only in the compatible list;
    // the old `ftypavif` prefix check missed these.
    assertTrue(AvifFormat.isAvif(ftyp("mif1", "avif", "miaf")))
    assertTrue(AvifFormat.isAvif(ftyp("mif1", "miaf", "avif")))
  }

  @Test
  fun heic_isNotDetected() {
    assertFalse(AvifFormat.isAvif(ftyp("heic", "mif1", "miaf")))
    assertFalse(AvifFormat.isAvif(ftyp("mif1", "miaf", "heic")))
  }

  @Test
  fun nonIsobmffMagicBytes_areNotDetected() {
    assertFalse(AvifFormat.isAvif(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))) // JPEG
    val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)
    assertFalse(AvifFormat.isAvif(png))
    assertFalse(AvifFormat.isAvif(ByteArray(0)))
  }

  @Test
  fun truncatedHeaders_areHandled() {
    val full = ftyp("avif", "mif1")
    // Exactly 12 bytes (size + ftyp + major brand) is enough for a major-brand match.
    assertTrue(AvifFormat.isAvif(full.copyOfRange(0, 12)))
    assertFalse(AvifFormat.isAvif(full.copyOfRange(0, 11)))

    // Compatible-brand match with the header cut mid-box must not crash or overread.
    val compat = ftyp("mif1", "miaf", "avif")
    assertTrue(AvifFormat.isAvif(compat))
    assertFalse(AvifFormat.isAvif(compat.copyOfRange(0, 18))) // cut before any full brand
  }

  @Test
  fun boxSizeZero_meaningExtendsToEof_isScanned() {
    val header = ftyp("mif1", "avif")
    header[0] = 0
    header[1] = 0
    header[2] = 0
    header[3] = 0
    assertTrue(AvifFormat.isAvif(header))
  }
}
