package com.alfikri.rizky.avifkit

/**
 * Content-based image-format detection shared by both platforms (was duplicated, and only
 * recognized JPEG/PNG/AVIF plus a too-loose WEBP check that matched bytes 8–9 without verifying the
 * `RIFF` container).
 */
internal object ImageFormats {

  fun detect(data: ByteArray): ImageFormat {
    if (data.size < 12) return ImageFormat.UNKNOWN

    // AVIF/AVIS via the proper ftyp-box parse.
    if (AvifFormat.isAvif(data)) return ImageFormat.AVIF

    return when {
      // JPEG: SOI marker FF D8 FF
      matches(data, 0, 0xFF, 0xD8, 0xFF) -> ImageFormat.JPEG
      // PNG: 89 50 4E 47 0D 0A 1A 0A
      matches(data, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> ImageFormat.PNG
      // GIF: "GIF8" (covers GIF87a and GIF89a)
      matches(data, 0, 0x47, 0x49, 0x46, 0x38) -> ImageFormat.GIF
      // BMP: "BM"
      matches(data, 0, 0x42, 0x4D) -> ImageFormat.BMP
      // WEBP: RIFF <size> WEBP — require BOTH the RIFF container and the WEBP form.
      matches(data, 0, 0x52, 0x49, 0x46, 0x46) && matches(data, 8, 0x57, 0x45, 0x42, 0x50) ->
        ImageFormat.WEBP
      // HEIF/HEIC: ISO-BMFF ftyp with an "he.." major brand (heic/heix/heif/hevc/...).
      isHeif(data) -> ImageFormat.HEIF
      else -> ImageFormat.UNKNOWN
    }
  }

  private fun isHeif(data: ByteArray): Boolean {
    // bytes 4..7 == "ftyp", major brand at 8..9 == "he" (heic, heix, heif, hevc, heim, heis, ...).
    return matches(data, 4, 0x66, 0x74, 0x79, 0x70) && matches(data, 8, 0x68, 0x65)
  }

  /** True if [data] starts, at [offset], with the given unsigned byte sequence. */
  private fun matches(data: ByteArray, offset: Int, vararg bytes: Int): Boolean {
    if (offset + bytes.size > data.size) return false
    for (i in bytes.indices) {
      if ((data[offset + i].toInt() and 0xFF) != bytes[i]) return false
    }
    return true
  }
}
