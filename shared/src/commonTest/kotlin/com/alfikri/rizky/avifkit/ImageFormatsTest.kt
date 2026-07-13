package com.alfikri.rizky.avifkit

import kotlin.test.Test
import kotlin.test.assertEquals

class ImageFormatsTest {

  private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

  private fun padded(header: ByteArray, size: Int = 16): ByteArray =
    ByteArray(size).also { header.copyInto(it) }

  @Test
  fun jpeg() {
    assertEquals(ImageFormat.JPEG, ImageFormats.detect(padded(bytes(0xFF, 0xD8, 0xFF, 0xE0))))
  }

  @Test
  fun png() {
    assertEquals(
      ImageFormat.PNG,
      ImageFormats.detect(padded(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))),
    )
  }

  @Test
  fun gif() {
    assertEquals(
      ImageFormat.GIF,
      ImageFormats.detect(padded(bytes(0x47, 0x49, 0x46, 0x38, 0x39, 0x61))),
    )
  }

  @Test
  fun bmp() {
    assertEquals(ImageFormat.BMP, ImageFormats.detect(padded(bytes(0x42, 0x4D, 0x00, 0x00))))
  }

  @Test
  fun webp_requiresRiffAndWebp() {
    // RIFF <4 size bytes> WEBP
    val webp = bytes(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50)
    assertEquals(ImageFormat.WEBP, ImageFormats.detect(webp))
    // "WE" at 8-9 but no RIFF container must NOT match (the old too-loose check did).
    val notWebp = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x00, 0x00)
    assertEquals(ImageFormat.UNKNOWN, ImageFormats.detect(notWebp))
  }

  @Test
  fun heif() {
    // ....ftypheic....
    val heic = bytes(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63)
    assertEquals(ImageFormat.HEIF, ImageFormats.detect(heic))
  }

  @Test
  fun avif_takesPrecedenceOverGenericFtyp() {
    val avif = bytes(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x61, 0x76, 0x69, 0x66)
    assertEquals(ImageFormat.AVIF, ImageFormats.detect(avif))
  }

  @Test
  fun tooShortOrUnknown() {
    assertEquals(ImageFormat.UNKNOWN, ImageFormats.detect(bytes(0xFF, 0xD8)))
    assertEquals(ImageFormat.UNKNOWN, ImageFormats.detect(padded(bytes(0x01, 0x02, 0x03, 0x04))))
  }
}
