package com.alfikri.rizky.avifkit

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [EncodingOptions.preserveMetadata] through the real JNI codec — the Android counterpart of
 * `AvifMetadataIosTest`. Runs with `./gradlew :shared:connectedAndroidDeviceTest`.
 *
 * The parsing itself is covered by `SourceMetadataTest` on the host. What can only be proven here
 * is that the blobs survive `avifEncoderWrite`, and that the orientation AvifKit baked into the
 * pixels is not *also* handed to the reader.
 */
@RunWith(AndroidJUnit4::class)
class AvifMetadataAndroidTest {

  private fun ByteArray.containsAscii(text: String): Boolean {
    val needle = text.encodeToByteArray()
    if (needle.isEmpty() || needle.size > size) return false
    outer@ for (start in 0..size - needle.size) {
      for (i in needle.indices) if (this[start + i] != needle[i]) continue@outer
      return true
    }
    return false
  }

  private fun encode(source: ByteArray, preserve: Boolean): ByteArray = runBlocking {
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
  }

  @Test
  fun exifAndXmp_reachTheEncodedFile() {
    val avif = encode(MetadataFixtures.JPEG_ORIENTED, preserve = true)
    assertTrue(
      "Exif should be embedded in the AVIF",
      avif.containsAscii(MetadataFixtures.SOFTWARE_MARKER),
    )
    assertTrue(
      "XMP should be embedded in the AVIF",
      avif.containsAscii(MetadataFixtures.XMP_MARKER),
    )
  }

  @Test
  fun metadataIsStrippedByDefault() {
    val avif = encode(MetadataFixtures.JPEG_ORIENTED, preserve = false)
    assertFalse(
      "no Exif may be embedded when preserveMetadata is off",
      avif.containsAscii(MetadataFixtures.SOFTWARE_MARKER),
    )
    assertFalse(
      "no XMP may be embedded when preserveMetadata is off",
      avif.containsAscii(MetadataFixtures.XMP_MARKER),
    )
  }

  /**
   * The regression that motivated the orientation rewrite.
   *
   * AvifKit rotates the source's pixels itself, so the fixture's "rotate 90 CW" ends up as a 16x32
   * image. `avifImageSetMetadataExif` derives `irot`/`imir` from whatever orientation the payload
   * claims, so copying the original 6 through would make the decoder rotate a second time and hand
   * back 32x16. Both dimensions and pixels are checked: a 180 degree error keeps the dimensions and
   * only moves colour.
   */
  @Test
  fun preservedExif_doesNotRotateTheImageTwice() = runBlocking {
    val avif = encode(MetadataFixtures.JPEG_ORIENTED, preserve = true)
    val decoded = AvifConverter().decodeAvif(ImageInput.from(avif))

    assertEquals(
      "orientation must not be applied twice",
      MetadataFixtures.ORIENTED_DISPLAY_WIDTH,
      decoded.width,
    )
    assertEquals(MetadataFixtures.ORIENTED_DISPLAY_HEIGHT, decoded.height)
    assertChannelsNear(0xE0, 0x1F, 0x20, decoded.getPixel(2, 2), "top-left stays red")
    assertChannelsNear(
      0x20,
      0x40,
      0xE0,
      decoded.getPixel(2, decoded.height - 3),
      "bottom-left stays blue",
    )
  }

  /** Turning metadata off must not change the pixels — only what travels alongside them. */
  @Test
  fun strippingMetadata_leavesOrientationHandlingAlone() = runBlocking {
    val decoded =
      AvifConverter()
        .decodeAvif(ImageInput.from(encode(MetadataFixtures.JPEG_ORIENTED, preserve = false)))
    assertEquals(MetadataFixtures.ORIENTED_DISPLAY_WIDTH, decoded.width)
    assertEquals(MetadataFixtures.ORIENTED_DISPLAY_HEIGHT, decoded.height)
  }

  @Test
  fun pngMetadata_reachesTheEncodedFile() {
    val avif = encode(MetadataFixtures.PNG_META, preserve = true)
    assertTrue(avif.containsAscii(MetadataFixtures.SOFTWARE_MARKER))
    assertTrue(avif.containsAscii(MetadataFixtures.XMP_MARKER))
  }

  @Test
  fun webpMetadata_reachesTheEncodedFile() {
    val avif = encode(MetadataFixtures.WEBP_META, preserve = true)
    assertTrue(avif.containsAscii(MetadataFixtures.SOFTWARE_MARKER))
    assertTrue(avif.containsAscii(MetadataFixtures.XMP_MARKER))
  }

  /** A source with no metadata must still encode, not fail on the empty payload. */
  @Test
  fun sourceWithoutMetadata_encodesFine() = runBlocking {
    val avif = encode(MetadataFixtures.JPEG_PLAIN, preserve = true)
    assertTrue(AvifConverter().isAvifFile(ImageInput.from(avif)))
    val decoded = AvifConverter().decodeAvif(ImageInput.from(avif))
    assertEquals(MetadataFixtures.STORED_WIDTH, decoded.width)
    assertEquals(MetadataFixtures.STORED_HEIGHT, decoded.height)
  }

  /** The Exif pixel-dimension tags follow a resize, so the payload keeps describing the output. */
  @Test
  fun resizedOutput_stillCarriesMetadata() = runBlocking {
    val avif =
      AvifConverter()
        .encodeAvif(
          ImageInput.from(MetadataFixtures.JPEG_ORIENTED),
          options =
            EncodingOptions(quality = 90, speed = 10, preserveMetadata = true, maxDimension = 16),
        )
    assertTrue(avif.containsAscii(MetadataFixtures.SOFTWARE_MARKER))
    val decoded = AvifConverter().decodeAvif(ImageInput.from(avif))
    assertEquals(16, maxOf(decoded.width, decoded.height))
  }

  private fun assertChannelsNear(r: Int, g: Int, b: Int, actual: Int, message: String) {
    val actualR = actual shr 16 and 0xFF
    val actualG = actual shr 8 and 0xFF
    val actualB = actual and 0xFF
    // JPEG decode plus AVIF encode both shift colour; the bound only has to separate red from blue.
    val tolerance = 32
    assertTrue(
      "$message — expected ~($r, $g, $b) but was ($actualR, $actualG, $actualB)",
      abs(actualR - r) <= tolerance &&
        abs(actualG - g) <= tolerance &&
        abs(actualB - b) <= tolerance,
    )
  }
}
