package com.alfikri.rizky.avifkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import libavif.*
import platform.posix.memcpy

/**
 * [EncodingOptions.preserveMetadata] through the real libavif cinterop path — the iOS counterpart
 * of `AvifMetadataAndroidTest`. Runs with `./gradlew :shared:iosSimulatorArm64Test`.
 *
 * Unlike the Android side, this can read the encoded file back through raw libavif, so the checks
 * are on the actual `avifImage` rather than on a byte search: the embedded Exif is parsed, and
 * `transformFlags` is inspected directly. That last one is the whole point —
 * `avifImageSetMetadataExif` turns an Exif orientation into an `irot`/`imir` box, and AvifKit has
 * already rotated the pixels, so the file must come out carrying no transform at all.
 */
@OptIn(ExperimentalForeignApi::class)
class AvifMetadataIosTest {

  private class Probe(
    val exif: ByteArray,
    val xmp: ByteArray,
    val transformFlags: UInt,
    val width: Int,
    val height: Int,
  )

  /** Parse [avif] with raw libavif and copy out everything the assertions need. */
  private fun probe(avif: ByteArray): Probe {
    val decoder = avifDecoderCreate() ?: error("libavif: failed to create decoder")
    try {
      // The production decoder ignores XMP; this one must not, or the check below can't fail.
      decoder.pointed.ignoreExif = AVIF_FALSE
      decoder.pointed.ignoreXMP = AVIF_FALSE
      return avif.usePinned { pinned ->
        val io =
          avifDecoderSetIOMemory(decoder, pinned.addressOf(0).reinterpret(), avif.size.convert())
        check(io == AVIF_RESULT_OK) { "setIOMemory: ${avifResultToString(io)?.toKString()}" }
        val parsed = avifDecoderParse(decoder)
        check(parsed == AVIF_RESULT_OK) { "parse: ${avifResultToString(parsed)?.toKString()}" }
        val image = decoder.pointed.image ?: error("libavif: parsed file has no image")
        Probe(
          exif = copyOut(image.pointed.exif.data, image.pointed.exif.size),
          xmp = copyOut(image.pointed.xmp.data, image.pointed.xmp.size),
          transformFlags = image.pointed.transformFlags,
          width = image.pointed.width.toInt(),
          height = image.pointed.height.toInt(),
        )
      }
    } finally {
      avifDecoderDestroy(decoder)
    }
  }

  private fun copyOut(data: CPointer<UByteVar>?, size: ULong): ByteArray {
    if (data == null || size == 0uL) return ByteArray(0)
    val out = ByteArray(size.toInt())
    out.usePinned { memcpy(it.addressOf(0), data, size) }
    return out
  }

  private fun ByteArray.containsAscii(text: String): Boolean {
    val needle = text.encodeToByteArray()
    if (needle.isEmpty() || needle.size > size) return false
    outer@ for (start in 0..size - needle.size) {
      for (i in needle.indices) if (this[start + i] != needle[i]) continue@outer
      return true
    }
    return false
  }

  private suspend fun encode(source: ByteArray, preserve: Boolean): ByteArray =
    AvifConverter()
      .encodeAvif(
        ImageInput.from(source),
        options =
          EncodingOptions(
            quality = 95,
            speed = 10,
            subsample = ChromaSubsample.YUV444,
            preserveMetadata = preserve,
          ),
      )

  @Test
  fun exifAndXmp_reachTheEncodedFile() = runBlocking {
    val result = probe(encode(MetadataFixtures.JPEG_ORIENTED, preserve = true))
    assertTrue(result.exif.isNotEmpty(), "libavif should report an Exif payload")
    assertTrue(result.exif.containsAscii(MetadataFixtures.SOFTWARE_MARKER), "Software tag survived")
    assertTrue(result.xmp.containsAscii(MetadataFixtures.XMP_MARKER), "XMP packet survived")
  }

  @Test
  fun metadataIsStrippedByDefault() = runBlocking {
    val result = probe(encode(MetadataFixtures.JPEG_ORIENTED, preserve = false))
    assertEquals(0, result.exif.size, "no Exif when preserveMetadata is off")
    assertEquals(0, result.xmp.size, "no XMP when preserveMetadata is off")
  }

  /**
   * The keystone. The fixture claims "rotate 90 CW"; AvifKit applies that to the pixels, so the
   * file it writes must claim orientation 1 and carry no irot/imir. Copying the 6 through would
   * leave `transformFlags` set and every reader would rotate the already-rotated image again.
   *
   * `transformFlags` is the direct proof; the dimensions are the same claim seen from outside, and
   * only became assertable here once the encode path started sizing its context from the displayed
   * size rather than the raster (see `AvifOrientationIosTest`).
   */
  @Test
  fun preservedExif_emitsNoRotationTransform() = runBlocking {
    val result = probe(encode(MetadataFixtures.JPEG_ORIENTED, preserve = true))

    assertEquals(1, ExifTiff.orientationOf(result.exif), "embedded Exif must read as upright")
    assertEquals(0u, result.transformFlags, "AVIF_TRANSFORM_NONE — no irot/imir may be emitted")
    assertEquals(MetadataFixtures.ORIENTED_DISPLAY_WIDTH, result.width)
    assertEquals(MetadataFixtures.ORIENTED_DISPLAY_HEIGHT, result.height)
  }

  /** And the pixels themselves must land the same way with metadata on or off. */
  @Test
  fun preservingMetadata_doesNotMoveThePixels() = runBlocking {
    val withMetadata =
      AvifConverter()
        .decodeAvif(ImageInput.from(encode(MetadataFixtures.JPEG_ORIENTED, preserve = true)))
    val without =
      AvifConverter()
        .decodeAvif(ImageInput.from(encode(MetadataFixtures.JPEG_ORIENTED, preserve = false)))
    val a = uiImagePremultipliedRgba(withMetadata)
    val b = uiImagePremultipliedRgba(without)
    assertEquals(b.width, a.width)
    assertEquals(b.height, a.height)
    assertTrue(a.pixels.contentEquals(b.pixels), "metadata must not change a single pixel")
  }

  @Test
  fun pngMetadata_reachesTheEncodedFile() = runBlocking {
    val result = probe(encode(MetadataFixtures.PNG_META, preserve = true))
    assertTrue(result.exif.containsAscii(MetadataFixtures.SOFTWARE_MARKER))
    assertTrue(result.xmp.containsAscii(MetadataFixtures.XMP_MARKER))
    assertEquals(1, ExifTiff.orientationOf(result.exif))
  }

  @Test
  fun webpMetadata_reachesTheEncodedFile() = runBlocking {
    val result = probe(encode(MetadataFixtures.WEBP_META, preserve = true))
    assertTrue(result.exif.containsAscii(MetadataFixtures.SOFTWARE_MARKER))
    assertTrue(result.xmp.containsAscii(MetadataFixtures.XMP_MARKER))
  }

  /** A source with no metadata must still encode rather than fail on an empty payload. */
  @Test
  fun sourceWithoutMetadata_encodesFine() = runBlocking {
    val result = probe(encode(MetadataFixtures.JPEG_PLAIN, preserve = true))
    assertEquals(0, result.exif.size)
    assertEquals(MetadataFixtures.STORED_WIDTH, result.width)
    assertEquals(MetadataFixtures.STORED_HEIGHT, result.height)
  }

  @Test
  fun resizedOutput_stillCarriesMetadata() = runBlocking {
    val avif =
      AvifConverter()
        .encodeAvif(
          ImageInput.from(MetadataFixtures.JPEG_ORIENTED),
          options =
            EncodingOptions(quality = 90, speed = 10, preserveMetadata = true, maxDimension = 16),
        )
    val result = probe(avif)
    assertTrue(result.exif.containsAscii(MetadataFixtures.SOFTWARE_MARKER))
    assertEquals(16, maxOf(result.width, result.height))
  }
}
