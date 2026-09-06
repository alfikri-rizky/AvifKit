package com.alfikri.rizky.avifkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import platform.CoreGraphics.*
import platform.UIKit.*

/**
 * Encode-side orientation on iOS: a UIImage carries a raster plus an `imageOrientation`, and every
 * UIKit draw call applies the orientation. The encode path therefore has to size its bitmap context
 * from the DISPLAYED pixel size, not the raster — otherwise a portrait photo (stored landscape with
 * orientation `.right`, which is how essentially every phone camera writes one) is drawn rotated
 * into a landscape canvas and comes out squashed.
 *
 * The decode direction — libavif's `irot`/`imir`, which it reports but does not apply — is covered
 * by `AvifConverterIosRoundTripTest.decode_appliesIrotOrientation`.
 */
@OptIn(ExperimentalForeignApi::class)
class AvifOrientationIosTest {

  private val rasterWidth = 32
  private val rasterHeight = 16

  private val red = Triple(0xE0, 0x20, 0x20)
  private val blue = Triple(0x20, 0x40, 0xE0)

  /** Landscape raster: left half red, right half blue, so a rotation visibly moves colour. */
  private fun leftRedRightBlue(width: Int, height: Int): ByteArray {
    val out = ByteArray(width * height * 4)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val (r, g, b) = if (x < width / 2) red else blue
        val i = (y * width + x) * 4
        out[i] = r.toByte()
        out[i + 1] = g.toByte()
        out[i + 2] = b.toByte()
        out[i + 3] = 0xFF.toByte()
      }
    }
    return out
  }

  /** A UIImage whose raster is [pixels] but which DISPLAYS with [orientation] applied. */
  private fun orientedImage(
    pixels: ByteArray,
    width: Int,
    height: Int,
    orientation: UIImageOrientation,
  ): UIImage {
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    try {
      return pixels.usePinned { pinned ->
        val ctx =
          CGBitmapContextCreate(
            data = pinned.addressOf(0),
            width = width.toULong(),
            height = height.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = (width * 4).toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
          )!!
        val cg = CGBitmapContextCreateImage(ctx)!!
        try {
          UIImage.imageWithCGImage(cg, 1.0, orientation)
        } finally {
          CGImageRelease(cg)
        }
      }
    } finally {
      CGColorSpaceRelease(colorSpace)
    }
  }

  private suspend fun encodeThenDecode(image: UIImage, maxDimension: Int? = null): RgbaPixels {
    val avif =
      AvifConverter()
        .encodeAvif(
          ImageInput.from(image),
          options = EncodingOptions(lossless = true, speed = 10, maxDimension = maxDimension),
        )
    return uiImagePremultipliedRgba(AvifConverter().decodeAvif(ImageInput.from(avif)))
  }

  private fun assertNear(expected: Triple<Int, Int, Int>, actual: IntArray, message: String) {
    val (r, g, b) = expected
    val tolerance = 12
    assertTrue(
      kotlin.math.abs(actual[0] - r) <= tolerance &&
        kotlin.math.abs(actual[1] - g) <= tolerance &&
        kotlin.math.abs(actual[2] - b) <= tolerance,
      "$message — expected ~($r, $g, $b) but was (${actual[0]}, ${actual[1]}, ${actual[2]})",
    )
  }

  /**
   * The regression. `.right` is EXIF orientation 6 — what a phone writes for a portrait photo — so
   * a 32x16 raster must encode as a 16x32 image with the red half on TOP. Before the fix the
   * context was sized 32x16 and the rotated image was squashed into it.
   */
  @Test
  fun quarterTurn_encodesAtTheDisplayedSize() = runBlocking {
    val image =
      orientedImage(
        leftRedRightBlue(rasterWidth, rasterHeight),
        rasterWidth,
        rasterHeight,
        UIImageOrientation.UIImageOrientationRight,
      )

    val decoded = encodeThenDecode(image)

    assertEquals(rasterHeight, decoded.width, "a quarter turn swaps the encoded width")
    assertEquals(rasterWidth, decoded.height, "a quarter turn swaps the encoded height")
    assertNear(red, decoded.at(decoded.width / 2, 3), "the left half rotates to the top")
    assertNear(blue, decoded.at(decoded.width / 2, decoded.height - 4), "and the right half down")
  }

  /** Every orientation, so the swap is applied to exactly the four that turn a quarter. */
  @Test
  fun onlyQuarterTurnsSwapTheDimensions() = runBlocking {
    val quarterTurns =
      listOf(
        UIImageOrientation.UIImageOrientationLeft,
        UIImageOrientation.UIImageOrientationRight,
        UIImageOrientation.UIImageOrientationLeftMirrored,
        UIImageOrientation.UIImageOrientationRightMirrored,
      )
    val uprights =
      listOf(
        UIImageOrientation.UIImageOrientationUp,
        UIImageOrientation.UIImageOrientationDown,
        UIImageOrientation.UIImageOrientationUpMirrored,
        UIImageOrientation.UIImageOrientationDownMirrored,
      )
    val raster = leftRedRightBlue(rasterWidth, rasterHeight)

    for (orientation in quarterTurns) {
      val decoded = encodeThenDecode(orientedImage(raster, rasterWidth, rasterHeight, orientation))
      assertEquals(rasterHeight, decoded.width, "$orientation should encode as portrait")
      assertEquals(rasterWidth, decoded.height, "$orientation should encode as portrait")
    }
    for (orientation in uprights) {
      val decoded = encodeThenDecode(orientedImage(raster, rasterWidth, rasterHeight, orientation))
      assertEquals(rasterWidth, decoded.width, "$orientation should keep the raster shape")
      assertEquals(rasterHeight, decoded.height, "$orientation should keep the raster shape")
    }
  }

  /**
   * `maxDimension` measures the displayed size too. A 64x32 raster shown as 32x64, capped at 16,
   * must come out 8x16 — sizing from the raster instead caps the wrong side and yields 16x8.
   */
  @Test
  fun resize_measuresTheDisplayedSide() = runBlocking {
    val image =
      orientedImage(leftRedRightBlue(64, 32), 64, 32, UIImageOrientation.UIImageOrientationRight)

    val decoded = encodeThenDecode(image, maxDimension = 16)

    assertEquals(8, decoded.width)
    assertEquals(16, decoded.height)
    assertNear(red, decoded.at(4, 2), "still red on top after the downscale")
    assertNear(blue, decoded.at(4, 13), "still blue on the bottom")
  }

  /**
   * `getImageInfo` has always reported the DISPLAYED size (`image.size * scale`). The encode path
   * reported the raster, so the library answered one size and produced another for the same file.
   */
  @Test
  fun encodedSize_agreesWithGetImageInfo() = runBlocking {
    val input = ImageInput.from(MetadataFixtures.JPEG_ORIENTED)
    val info = AvifConverter().getImageInfo(input)

    val avif =
      AvifConverter().encodeAvif(input, options = EncodingOptions(quality = 90, speed = 10))
    val decoded = AvifConverter().decodeAvif(ImageInput.from(avif))
    val pixels = uiImagePremultipliedRgba(decoded)

    assertEquals(
      MetadataFixtures.ORIENTED_DISPLAY_WIDTH,
      info.width,
      "getImageInfo is orientation-aware",
    )
    assertEquals(MetadataFixtures.ORIENTED_DISPLAY_HEIGHT, info.height)
    assertEquals(info.width, pixels.width, "what the library reports is what it encodes")
    assertEquals(info.height, pixels.height)
  }
}
