package com.alfikri.rizky.avifstudio.model

/**
 * Cheap magic-byte format detection from the head of a file.
 *
 * AvifKit has its own detector but it is `internal` to the library, and `getImageInfo` decodes.
 * Listing 40 picked files must not decode 40 images, so the picker sniffs the first [HEADER_BYTES]
 * bytes instead.
 */
object ImageSniffer {

  /** Enough for an ISO-BMFF `ftyp` box with a healthy list of compatible brands. */
  const val HEADER_BYTES = 64

  enum class Kind(val label: String) {
    AVIF("AVIF"),
    JPEG("JPEG"),
    PNG("PNG"),
    WEBP("WebP"),
    HEIF("HEIF"),
    GIF("GIF"),
    BMP("BMP"),
    UNKNOWN("Image"),
  }

  fun detect(header: ByteArray): Kind =
    when {
      isAvif(header) -> Kind.AVIF
      startsWith(header, 0, 0xFF, 0xD8, 0xFF) -> Kind.JPEG
      startsWith(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> Kind.PNG
      startsWith(header, 0, 0x52, 0x49, 0x46, 0x46) &&
        startsWith(header, 8, 0x57, 0x45, 0x42, 0x50) -> Kind.WEBP
      isFtyp(header) && startsWith(header, 8, 0x68, 0x65) -> Kind.HEIF
      startsWith(header, 0, 0x47, 0x49, 0x46, 0x38) -> Kind.GIF
      startsWith(header, 0, 0x42, 0x4D) -> Kind.BMP
      else -> Kind.UNKNOWN
    }

  /**
   * AVIF containers do not always put `avif` in the major brand — encoders routinely emit `mif1` or
   * `miaf` there and list `avif` only among the compatible brands, so a plain `ftypavif` prefix
   * check misses real AVIF files. Mirrors `AvifFormat.isAvif` in the library.
   */
  fun isAvif(header: ByteArray): Boolean {
    if (!isFtyp(header)) return false
    if (isAvifBrand(header, 8)) return true

    val boxSize = readBe32(header, 0)
    val end = if (boxSize in 16..header.size) boxSize else header.size
    var offset = 16 // skip box size, "ftyp", major brand and minor version
    while (offset + 4 <= end) {
      if (isAvifBrand(header, offset)) return true
      offset += 4
    }
    return false
  }

  private fun isFtyp(header: ByteArray): Boolean =
    header.size >= 12 && startsWith(header, 4, 0x66, 0x74, 0x79, 0x70)

  // "avif" (still image) or "avis" (image sequence).
  private fun isAvifBrand(header: ByteArray, offset: Int): Boolean =
    startsWith(header, offset, 0x61, 0x76, 0x69, 0x66) ||
      startsWith(header, offset, 0x61, 0x76, 0x69, 0x73)

  private fun readBe32(data: ByteArray, offset: Int): Int {
    if (offset + 4 > data.size) return 0
    return ((data[offset].toInt() and 0xFF) shl 24) or
      ((data[offset + 1].toInt() and 0xFF) shl 16) or
      ((data[offset + 2].toInt() and 0xFF) shl 8) or
      (data[offset + 3].toInt() and 0xFF)
  }

  private fun startsWith(data: ByteArray, offset: Int, vararg bytes: Int): Boolean {
    if (offset < 0 || offset + bytes.size > data.size) return false
    for (i in bytes.indices) {
      if ((data[offset + i].toInt() and 0xFF) != bytes[i]) return false
    }
    return true
  }
}
