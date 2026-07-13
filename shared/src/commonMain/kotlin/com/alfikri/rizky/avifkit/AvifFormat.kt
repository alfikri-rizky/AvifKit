package com.alfikri.rizky.avifkit

/**
 * AVIF container (ISO-BMFF `ftyp` box) detection, shared by both platforms.
 *
 * A file is AVIF when its `ftyp` box lists `avif` or `avis` (image sequence) as the major brand OR
 * among the compatible brands — encoders commonly emit `mif1`/`miaf` as the major brand with `avif`
 * only in the compatible list, which a plain `ftypavif` prefix check misses.
 */
internal object AvifFormat {

  /**
   * How many leading bytes callers should read to make [isAvif] reliable: enough for the box
   * header, major brand, minor version, and a healthy number of compatible brands.
   */
  const val HEADER_CHECK_SIZE: Int = 64

  private const val FTYP = 0x66747970 // "ftyp"
  private const val BRAND_AVIF = 0x61766966 // "avif"
  private const val BRAND_AVIS = 0x61766973 // "avis"

  /**
   * Check whether [header] (the leading bytes of a file — a truncated prefix is fine) is an AVIF
   * container.
   */
  fun isAvif(header: ByteArray): Boolean {
    // Minimum: size(4) + "ftyp"(4) + major brand(4).
    if (header.size < 12) return false
    if (readBe32(header, 4) != FTYP) return false

    val majorBrand = readBe32(header, 8)
    if (majorBrand == BRAND_AVIF || majorBrand == BRAND_AVIS) return true

    // Compatible brands run from byte 16 (after the minor version) to the end of the box.
    // boxSize == 0 means "extends to end of file"; boxSize == 1 means a 64-bit size follows
    // (never seen for ftyp) — in both cases just scan the header bytes we were given.
    val boxSize = readBe32(header, 0)
    val end = if (boxSize in 16..header.size) boxSize else header.size
    var offset = 16
    while (offset + 4 <= end) {
      val brand = readBe32(header, offset)
      if (brand == BRAND_AVIF || brand == BRAND_AVIS) return true
      offset += 4
    }
    return false
  }

  private fun readBe32(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xFF) shl 24) or
      ((data[offset + 1].toInt() and 0xFF) shl 16) or
      ((data[offset + 2].toInt() and 0xFF) shl 8) or
      (data[offset + 3].toInt() and 0xFF)
}
