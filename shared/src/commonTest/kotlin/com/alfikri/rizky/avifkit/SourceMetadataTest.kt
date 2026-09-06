package com.alfikri.rizky.avifkit

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Container parsing and Exif rewriting for [EncodingOptions.preserveMetadata].
 *
 * This is where the feature's risky half lives: the codec tests prove bytes reach the file, but
 * only these prove the *right* bytes do — in particular that the orientation which AvifKit already
 * baked into the pixels is not also handed to the reader.
 */
class SourceMetadataTest {

  private fun ByteArray.containsAscii(text: String): Boolean {
    val needle = text.encodeToByteArray()
    if (needle.isEmpty() || needle.size > size) return false
    outer@ for (start in 0..size - needle.size) {
      for (i in needle.indices) if (this[start + i] != needle[i]) continue@outer
      return true
    }
    return false
  }

  // ---- container extraction -------------------------------------------------------------------

  @Test
  fun jpeg_carriesExifAndXmp() {
    val found = assertNotNull(MetadataExtractor.extract(MetadataFixtures.JPEG_ORIENTED))
    val exif = assertNotNull(found.exif)
    val xmp = assertNotNull(found.xmp)
    assertTrue(exif.containsAscii(MetadataFixtures.SOFTWARE_MARKER), "Exif kept its Software tag")
    assertTrue(xmp.containsAscii(MetadataFixtures.XMP_MARKER), "XMP packet came through")
    // The APP1 "Exif\u0000\u0000" prefix must be stripped: libavif wants the TIFF blob
    // itself. Pillow writes big-endian TIFF, so these fixtures cover the "MM" branch end to
    // end; the little-endian one is covered by the hand-built payload further down.
    assertEquals("MM", exif.copyOfRange(0, 2).decodeToString(), "payload starts at the TIFF header")
  }

  @Test
  fun png_carriesExifAndXmp() {
    val found = assertNotNull(MetadataExtractor.extract(MetadataFixtures.PNG_META))
    assertTrue(assertNotNull(found.exif).containsAscii(MetadataFixtures.SOFTWARE_MARKER))
    assertTrue(assertNotNull(found.xmp).containsAscii(MetadataFixtures.XMP_MARKER))
  }

  @Test
  fun webp_carriesExifAndXmp() {
    val found = assertNotNull(MetadataExtractor.extract(MetadataFixtures.WEBP_META))
    assertTrue(assertNotNull(found.exif).containsAscii(MetadataFixtures.SOFTWARE_MARKER))
    assertTrue(assertNotNull(found.xmp).containsAscii(MetadataFixtures.XMP_MARKER))
  }

  @Test
  fun imageWithoutMetadata_yieldsNothing() {
    assertNull(MetadataExtractor.extract(MetadataFixtures.JPEG_PLAIN))
  }

  @Test
  fun gif_hasNowhereToPutExif() {
    assertNull(MetadataExtractor.extract(GifFixtures.MOVING_SQUARE))
  }

  @Test
  fun truncatedFile_doesNotThrow() {
    // A half-downloaded JPEG must degrade to "no metadata", not take the conversion down with it.
    for (length in listOf(2, 8, 20, 64, 200)) {
      MetadataExtractor.extract(MetadataFixtures.JPEG_ORIENTED.copyOf(length))
    }
    MetadataExtractor.extract(MetadataFixtures.PNG_META.copyOf(30))
    MetadataExtractor.extract(MetadataFixtures.WEBP_META.copyOf(30))
  }

  // ---- Exif rewriting -------------------------------------------------------------------------

  @Test
  fun sourceOrientation_isReadBeforeRewriting() {
    val exif = assertNotNull(MetadataExtractor.extract(MetadataFixtures.JPEG_ORIENTED)?.exif)
    assertEquals(6, ExifTiff.orientationOf(exif), "fixture really is 'rotate 90 CW'")
  }

  /**
   * The keystone. AvifKit rotates the pixels itself, so the Exif it embeds has to claim the image
   * is already upright — otherwise libavif derives an irot box from it and every reader rotates
   * twice.
   */
  @Test
  fun normalize_resetsOrientationToUpright() {
    val exif = assertNotNull(MetadataExtractor.extract(MetadataFixtures.JPEG_ORIENTED)?.exif)
    val normalized = assertNotNull(ExifTiff.normalize(exif))

    assertEquals(1, ExifTiff.orientationOf(normalized))
    assertEquals(exif.size, normalized.size, "rewrite must not relayout the payload")
    val changed = exif.indices.count { exif[it] != normalized[it] }
    assertEquals(1, changed, "only the orientation byte may differ")
  }

  @Test
  fun normalize_isIdempotentOnAnAlreadyUprightPayload() {
    val exif = assertNotNull(MetadataExtractor.extract(MetadataFixtures.PNG_META)?.exif)
    assertEquals(1, ExifTiff.orientationOf(exif), "fixture starts upright")
    assertContentEquals(exif, ExifTiff.normalize(exif))
  }

  @Test
  fun normalize_rejectsPayloadWithoutTiffHeader() {
    // libavif's encoder fails the whole write on one of these, so it must never be embedded.
    assertNull(ExifTiff.normalize(ByteArray(64) { 0x7A }))
    assertNull(ExifTiff.normalize(ByteArray(0)))
  }

  @Test
  fun normalize_survivesTruncatedPayloads() {
    val exif = assertNotNull(MetadataExtractor.extract(MetadataFixtures.JPEG_ORIENTED)?.exif)
    for (length in 5..exif.size step 7) ExifTiff.normalize(exif.copyOf(length))
  }

  // ---- pixel-dimension tags, in both byte orders ----------------------------------------------

  @Test
  fun normalize_updatesPixelDimensionsLittleEndian() = assertPixelDimensionsRewritten(le = true)

  @Test fun normalize_updatesPixelDimensionsBigEndian() = assertPixelDimensionsRewritten(le = false)

  private fun assertPixelDimensionsRewritten(le: Boolean) {
    val exif = tiffWithPixelDimensions(le, orientation = 8, pixelX = 4000, pixelY = 3000)

    val normalized = assertNotNull(ExifTiff.normalize(exif, width = 640, height = 480))

    assertEquals(1, ExifTiff.orientationOf(normalized), "orientation still reset")
    assertEquals(
      640,
      readU32(normalized, PIXEL_X_VALUE_AT, le),
      "PixelXDimension follows the output",
    )
    assertEquals(
      480,
      readU16(normalized, PIXEL_Y_VALUE_AT, le),
      "PixelYDimension follows the output",
    )
    assertEquals(exif.size, normalized.size)
  }

  @Test
  fun normalize_leavesPixelDimensionsAloneWhenSizeIsUnknown() {
    val exif = tiffWithPixelDimensions(le = true, orientation = 3, pixelX = 4000, pixelY = 3000)
    val normalized = assertNotNull(ExifTiff.normalize(exif))
    assertEquals(4000, readU32(normalized, PIXEL_X_VALUE_AT, le = true))
    assertEquals(3000, readU16(normalized, PIXEL_Y_VALUE_AT, le = true))
  }

  // ---- XMP ------------------------------------------------------------------------------------

  @Test
  fun xmp_orientationIsResetToo() {
    // Adobe mirrors orientation into XMP; a reader preferring it would undo the Exif fix.
    val xmp = """<rdf:Description tiff:Orientation="6" tiff:Make="X"/>""".encodeToByteArray()
    val normalized = XmpOrientation.normalize(xmp)
    assertEquals(
      """<rdf:Description tiff:Orientation="1" tiff:Make="X"/>""",
      normalized.decodeToString(),
    )
    assertEquals(xmp.size, normalized.size, "packet length must not move")
  }

  @Test
  fun xmp_elementFormIsResetToo() {
    val xmp = "<tiff:Orientation>8</tiff:Orientation>".encodeToByteArray()
    assertEquals(
      "<tiff:Orientation>1</tiff:Orientation>",
      XmpOrientation.normalize(xmp).decodeToString(),
    )
  }

  @Test
  fun xmp_withoutOrientation_isUntouched() {
    val xmp = MetadataFixtures.PNG_META // any bytes without the tag
    assertContentEquals(xmp, XmpOrientation.normalize(xmp))
  }

  // ---- end to end -----------------------------------------------------------------------------

  @Test
  fun forSource_producesUprightMetadata() {
    val metadata =
      assertNotNull(
        EncodedMetadata.forSource(
          MetadataFixtures.JPEG_ORIENTED,
          MetadataFixtures.ORIENTED_DISPLAY_WIDTH,
          MetadataFixtures.ORIENTED_DISPLAY_HEIGHT,
        )
      )
    assertEquals(1, ExifTiff.orientationOf(assertNotNull(metadata.exif)))
    assertTrue(assertNotNull(metadata.xmp).containsAscii(MetadataFixtures.XMP_MARKER))
  }

  @Test
  fun forSource_isNullWithoutASourceFile() {
    // The FromBitmap input path: there is no file, so there is nothing to preserve.
    assertNull(EncodedMetadata.forSource(null, 16, 16))
    assertNull(EncodedMetadata.forSource(MetadataFixtures.JPEG_PLAIN, 32, 16))
  }

  // ---- a hand-built TIFF, so the Exif-IFD pointer walk is actually exercised -------------------

  // Layout built by [tiffWithPixelDimensions]; the value slot is the last 4 bytes of a 12-byte
  // field entry, and the Exif IFD starts at byte 38.
  private companion object {
    const val EXIF_IFD_AT = 38
    const val PIXEL_X_VALUE_AT = EXIF_IFD_AT + 2 + 8
    const val PIXEL_Y_VALUE_AT = EXIF_IFD_AT + 2 + 12 + 8
  }

  /**
   * A minimal but structurally real Exif payload: IFD0 holding Orientation and a pointer to an Exif
   * IFD, which in turn holds PixelXDimension (LONG) and PixelYDimension (SHORT). The two differing
   * types are deliberate — both are legal, and the inline write has to respect the width of each.
   */
  private fun tiffWithPixelDimensions(
    le: Boolean,
    orientation: Int,
    pixelX: Int,
    pixelY: Int,
  ): ByteArray {
    val out = ByteArray(68)
    var at = 0
    fun u16(value: Int) {
      if (le) {
        out[at] = (value and 0xFF).toByte()
        out[at + 1] = (value ushr 8 and 0xFF).toByte()
      } else {
        out[at] = (value ushr 8 and 0xFF).toByte()
        out[at + 1] = (value and 0xFF).toByte()
      }
      at += 2
    }
    fun u32(value: Int) {
      for (i in 0 until 4) {
        val shift = if (le) i * 8 else (3 - i) * 8
        out[at + i] = (value ushr shift and 0xFF).toByte()
      }
      at += 4
    }
    fun shortField(tag: Int, value: Int) {
      u16(tag)
      u16(3)
      u32(1)
      u16(value)
      at += 2 // SHORT values are left-justified in the 4-byte slot; the rest is padding
    }
    fun longField(tag: Int, value: Int) {
      u16(tag)
      u16(4)
      u32(1)
      u32(value)
    }

    if (le) {
      out[0] = 'I'.code.toByte()
      out[1] = 'I'.code.toByte()
      out[2] = 42
      out[3] = 0
    } else {
      out[0] = 'M'.code.toByte()
      out[1] = 'M'.code.toByte()
      out[2] = 0
      out[3] = 42
    }
    at = 4
    u32(8) // offset of IFD0

    u16(2) // IFD0 field count
    shortField(0x0112, orientation)
    longField(0x8769, EXIF_IFD_AT) // Exif IFD pointer
    u32(0) // no next IFD

    check(at == EXIF_IFD_AT) { "Exif IFD landed at $at, not $EXIF_IFD_AT" }
    u16(2) // Exif IFD field count
    longField(0xA002, pixelX)
    shortField(0xA003, pixelY)
    u32(0)
    return out
  }

  private fun readU16(data: ByteArray, at: Int, le: Boolean): Int {
    val b0 = data[at].toInt() and 0xFF
    val b1 = data[at + 1].toInt() and 0xFF
    return if (le) (b1 shl 8) or b0 else (b0 shl 8) or b1
  }

  private fun readU32(data: ByteArray, at: Int, le: Boolean): Int {
    var value = 0
    for (i in 0 until 4) {
      val byte = data[at + i].toInt() and 0xFF
      value = value or (byte shl (if (le) i * 8 else (3 - i) * 8))
    }
    return value
  }
}
