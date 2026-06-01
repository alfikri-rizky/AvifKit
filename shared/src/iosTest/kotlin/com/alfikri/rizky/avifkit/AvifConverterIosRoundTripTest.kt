package com.alfikri.rizky.avifkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import platform.CoreGraphics.*
import platform.UIKit.UIImage

/**
 * End-to-end validation of the iOS cinterop path: encode a synthetic image to AVIF via libavif,
 * confirm the `ftypavif` signature, then decode it back and check the dimensions survive. Runs with
 * `./gradlew :shared:iosSimulatorArm64Test`. Requires the codec static libs to be present
 * (scripts/build-ios-libavif.sh) — the same libs the framework links.
 */
@OptIn(ExperimentalForeignApi::class)
class AvifConverterIosRoundTripTest {

  private val width = 64
  private val height = 48

  /** Build a solid-color RGBA UIImage to feed the encoder. */
  private fun makeTestImage(): UIImage {
    val bytesPerRow = width * 4
    val pixels = ByteArray(bytesPerRow * height)
    for (i in 0 until width * height) {
      pixels[i * 4 + 0] = 0x20 // R
      pixels[i * 4 + 1] = 0x80.toByte() // G
      pixels[i * 4 + 2] = 0xC0.toByte() // B
      pixels[i * 4 + 3] = 0xFF.toByte() // A
    }
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    try {
      return pixels.usePinned { pinned ->
        val ctx =
          CGBitmapContextCreate(
            data = pinned.addressOf(0),
            width = width.toULong(),
            height = height.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = bytesPerRow.toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
          )!!
        val cg = CGBitmapContextCreateImage(ctx)!!
        try {
          UIImage.imageWithCGImage(cg)
        } finally {
          CGImageRelease(cg)
        }
      }
    } finally {
      CGColorSpaceRelease(colorSpace)
    }
  }

  @Test
  fun isAvifSupported_isTrue() {
    assertTrue(AvifConverter().isAvifSupported(), "codec is statically linked")
  }

  @Test
  fun encodeThenDecode_roundTrips() = runBlocking {
    val converter = AvifConverter()
    val image = makeTestImage()

    val avif = converter.encodeAvif(ImageInput.from(image), Priority.BALANCED)
    assertTrue(avif.size > 12, "AVIF output should be non-trivial, was ${avif.size} bytes")
    assertTrue(
      converter.isAvifFile(ImageInput.from(avif)),
      "encoded bytes should carry the ftypavif signature",
    )

    val decoded = converter.decodeAvif(ImageInput.from(avif))
    val w = decoded.size.useContents { this.width } * decoded.scale
    val h = decoded.size.useContents { this.height } * decoded.scale
    assertEquals(width, w.toInt(), "decoded width should match")
    assertEquals(height, h.toInt(), "decoded height should match")
  }
}
