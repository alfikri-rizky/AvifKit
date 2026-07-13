package com.alfikri.rizky.avifkit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import libavif.*
import platform.CoreGraphics.*
import platform.UIKit.*
import platform.posix.memcpy

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

  /** Build a solid-color, fully opaque RGBA UIImage to feed the encoder. */
  private fun makeTestImage(): UIImage =
    makeImageFromPremultipliedRgba(solidRgba(0x20, 0x80, 0xC0, 0xFF), width, height)

  @Test
  fun isAvifSupported_isTrue() {
    assertTrue(AvifConverter().isAvifSupported(), "codec is statically linked")
  }

  /**
   * C1 regression (encode direction): CoreGraphics hands AvifConverter PREMULTIPLIED RGBA, and the
   * encoder must declare that to libavif so the file stores STRAIGHT alpha. Verified by reading the
   * encoded file back through raw libavif (straight-alpha ground truth, no CoreGraphics involved).
   */
  @Test
  fun encode_storesStraightAlpha_notPremultiplied() = runBlocking {
    // Straight color (0x40, 0x80, 0xC0) at 50% alpha → premultiplied (0x20, 0x40, 0x60, 0x80).
    val premultiplied = solidRgba(0x20, 0x40, 0x60, 0x80)
    val image = makeImageFromPremultipliedRgba(premultiplied, width, height)

    val avif =
      AvifConverter()
        .encodeAvif(
          ImageInput.from(image),
          options =
            EncodingOptions(
              quality = 95,
              speed = 10,
              subsample = ChromaSubsample.YUV444,
              alphaQuality = 100,
            ),
        )

    val straight = decodeRawStraightRgba(avif)
    assertEquals(width, straight.width)
    assertEquals(height, straight.height)
    // The file must contain the STRAIGHT color. The pre-fix bug stored the premultiplied
    // values (0x20, 0x40, 0x60) — off by ~50%, far outside codec tolerance.
    assertCenterPixel(straight.pixels, straight.width, straight.height, 0x40, 0x80, 0xC0, 0x80)
  }

  /**
   * C1 regression (decode direction): libavif yields straight alpha, but the CGImage built by
   * AvifConverter is declared premultiplied — so the decoder must ask libavif to premultiply. The
   * input file is produced by raw libavif from straight-alpha ground truth.
   */
  @Test
  fun decode_premultipliesForCoreGraphics() = runBlocking {
    val straight = solidRgba(0x40, 0x80, 0xC0, 0x80)
    val avif = encodeRawStraightAvif(straight, width, height)

    val decoded = AvifConverter().decodeAvif(ImageInput.from(avif))
    val premultiplied = uiImagePremultipliedRgba(decoded)

    // Rendered (premultiplied) values must be color × alpha. The pre-fix bug produced the
    // straight values (0x40, 0x80, 0xC0) tagged as premultiplied — mathematically invalid
    // where color > alpha, and visibly washed out.
    assertCenterPixel(
      premultiplied.pixels,
      premultiplied.width,
      premultiplied.height,
      0x20,
      0x40,
      0x60,
      0x80,
    )
  }

  /**
   * C3 regression: lossless=true must round-trip pixel-exact. Requires the encoder to force
   * YUV444 + identity matrix coefficients — quality=100 alone still rounds through YUV. The default
   * subsample (YUV420) is deliberately left in place to prove it gets overridden.
   */
  @Test
  fun lossless_roundTripsPixelExact() = runBlocking {
    val src = ByteArray(width * height * 4)
    for (y in 0 until height) {
      for (x in 0 until width) {
        val i = (y * width + x) * 4
        src[i] = ((x * 8 + y) and 0xFF).toByte() // R
        src[i + 1] = ((x * 3 + y * 5) and 0xFF).toByte() // G
        src[i + 2] = ((x + y * 11) and 0xFF).toByte() // B
        src[i + 3] = 0xFF.toByte() // opaque: premultiplied == straight
      }
    }
    val image = makeImageFromPremultipliedRgba(src, width, height)

    val avif =
      AvifConverter()
        .encodeAvif(ImageInput.from(image), options = EncodingOptions(lossless = true, speed = 10))

    val decoded = decodeRawStraightRgba(avif)
    assertEquals(width, decoded.width)
    assertEquals(height, decoded.height)
    assertTrue(
      decoded.pixels.contentEquals(src),
      "lossless encode must round-trip pixel-exact; first diff at index " +
        decoded.pixels.indices.firstOrNull { decoded.pixels[it] != src[it] },
    )
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

  // ---------------------------------------------------------------------------
  // Test helpers
  // ---------------------------------------------------------------------------

  private class RgbaPixels(val pixels: ByteArray, val width: Int, val height: Int)

  private fun solidRgba(r: Int, g: Int, b: Int, a: Int): ByteArray {
    val out = ByteArray(width * height * 4)
    for (i in 0 until width * height) {
      out[i * 4] = r.toByte()
      out[i * 4 + 1] = g.toByte()
      out[i * 4 + 2] = b.toByte()
      out[i * 4 + 3] = a.toByte()
    }
    return out
  }

  /** Build a UIImage from PREMULTIPLIED RGBA bytes (the only alpha mode CoreGraphics supports). */
  private fun makeImageFromPremultipliedRgba(pixels: ByteArray, width: Int, height: Int): UIImage {
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
          UIImage.imageWithCGImage(cg)
        } finally {
          CGImageRelease(cg)
        }
      }
    } finally {
      CGColorSpaceRelease(colorSpace)
    }
  }

  /** Extract PREMULTIPLIED RGBA from a UIImage the same way the production encode path does. */
  private fun uiImagePremultipliedRgba(image: UIImage): RgbaPixels {
    val cgImage = image.CGImage ?: error("UIImage has no CGImage")
    val w = CGImageGetWidth(cgImage).toInt()
    val h = CGImageGetHeight(cgImage).toInt()
    val out = ByteArray(w * h * 4)
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    try {
      out.usePinned { pinned ->
        val ctx =
          CGBitmapContextCreate(
            data = pinned.addressOf(0),
            width = w.toULong(),
            height = h.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = (w * 4).toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
          )!!
        UIGraphicsPushContext(ctx)
        memScoped {
          val rect =
            alloc<CGRect>().apply {
              origin.x = 0.0
              origin.y = 0.0
              size.width = w.toDouble()
              size.height = h.toDouble()
            }
          CGContextTranslateCTM(ctx, 0.0, h.toDouble())
          CGContextScaleCTM(ctx, 1.0, -1.0)
          image.drawInRect(rect.readValue())
        }
        UIGraphicsPopContext()
      }
    } finally {
      CGColorSpaceRelease(colorSpace)
    }
    return RgbaPixels(out, w, h)
  }

  /**
   * Encode STRAIGHT-alpha RGBA to AVIF through raw libavif — ground truth that bypasses both
   * AvifConverter and CoreGraphics (alphaPremultiplied stays AVIF_FALSE, libavif's default).
   */
  private fun encodeRawStraightAvif(rgba: ByteArray, width: Int, height: Int): ByteArray =
    memScoped {
      val image =
        avifImageCreate(width.toUInt(), height.toUInt(), 8u, AVIF_PIXEL_FORMAT_YUV444)
          ?: error("avifImageCreate failed")
      val encoder = avifEncoderCreate() ?: error("avifEncoderCreate failed")
      try {
        encoder.pointed.quality = 95
        encoder.pointed.qualityAlpha = 100
        encoder.pointed.speed = 10

        rgba.usePinned { pinned ->
          val rgb = alloc<avifRGBImage>()
          avifRGBImageSetDefaults(rgb.ptr, image)
          rgb.pixels = pinned.addressOf(0).reinterpret()
          rgb.rowBytes = (width * 4).toUInt()
          rgb.format = AVIF_RGB_FORMAT_RGBA
          rgb.depth = 8u
          val res = avifImageRGBToYUV(image, rgb.ptr)
          check(res == AVIF_RESULT_OK) { "RGBToYUV: ${avifResultToString(res)?.toKString()}" }
        }

        val output = alloc<avifRWData>()
        try {
          val res = avifEncoderWrite(encoder, image, output.ptr)
          check(res == AVIF_RESULT_OK) { "encoderWrite: ${avifResultToString(res)?.toKString()}" }
          ByteArray(output.size.toInt()).also { bytes ->
            bytes.usePinned { pinnedOut ->
              memcpy(pinnedOut.addressOf(0), output.data, output.size)
            }
          }
        } finally {
          avifRWDataFree(output.ptr)
        }
      } finally {
        avifImageDestroy(image)
        avifEncoderDestroy(encoder)
      }
    }

  /**
   * Decode AVIF to STRAIGHT-alpha RGBA through raw libavif — ground truth that bypasses both
   * AvifConverter and CoreGraphics (alphaPremultiplied stays AVIF_FALSE, libavif's default).
   */
  private fun decodeRawStraightRgba(avif: ByteArray): RgbaPixels = memScoped {
    val decoder = avifDecoderCreate() ?: error("avifDecoderCreate failed")
    val rgb = alloc<avifRGBImage>()
    var rgbAllocated = false
    try {
      avif.usePinned { pinned ->
        var res =
          avifDecoderSetIOMemory(decoder, pinned.addressOf(0).reinterpret(), avif.size.toULong())
        check(res == AVIF_RESULT_OK) { "setIOMemory: ${avifResultToString(res)?.toKString()}" }
        res = avifDecoderParse(decoder)
        check(res == AVIF_RESULT_OK) { "parse: ${avifResultToString(res)?.toKString()}" }
        res = avifDecoderNextImage(decoder)
        check(res == AVIF_RESULT_OK) { "nextImage: ${avifResultToString(res)?.toKString()}" }

        val decoded = decoder.pointed.image ?: error("decoder produced no image")
        avifRGBImageSetDefaults(rgb.ptr, decoded)
        rgb.format = AVIF_RGB_FORMAT_RGBA
        rgb.depth = 8u
        res = avifRGBImageAllocatePixels(rgb.ptr)
        check(res == AVIF_RESULT_OK) { "allocatePixels: ${avifResultToString(res)?.toKString()}" }
        rgbAllocated = true
        res = avifImageYUVToRGB(decoded, rgb.ptr)
        check(res == AVIF_RESULT_OK) { "YUVToRGB: ${avifResultToString(res)?.toKString()}" }

        val w = rgb.width.toInt()
        val h = rgb.height.toInt()
        val rowBytes = rgb.rowBytes.toInt()
        val out = ByteArray(w * h * 4)
        out.usePinned { pinnedOut ->
          for (row in 0 until h) {
            memcpy(
              pinnedOut.addressOf(row * w * 4),
              rgb.pixels!! + row * rowBytes,
              (w * 4).toULong(),
            )
          }
        }
        RgbaPixels(out, w, h)
      }
    } finally {
      if (rgbAllocated) avifRGBImageFreePixels(rgb.ptr)
      avifDecoderDestroy(decoder)
    }
  }

  /**
   * Assert the center pixel matches within [tolerance] per channel — generous enough for YUV444
   * codec noise at quality 95, far tighter than the ~50% shift a premultiply mismatch causes.
   */
  private fun assertCenterPixel(
    pixels: ByteArray,
    w: Int,
    h: Int,
    r: Int,
    g: Int,
    b: Int,
    a: Int,
    tolerance: Int = 12,
  ) {
    val i = ((h / 2) * w + w / 2) * 4
    val actual =
      intArrayOf(
        pixels[i].toInt() and 0xFF,
        pixels[i + 1].toInt() and 0xFF,
        pixels[i + 2].toInt() and 0xFF,
        pixels[i + 3].toInt() and 0xFF,
      )
    val expected = intArrayOf(r, g, b, a)
    val channels = arrayOf("R", "G", "B", "A")
    for (c in 0..3) {
      assertTrue(
        abs(actual[c] - expected[c]) <= tolerance,
        "${channels[c]} at center: expected ~${expected[c]}, got ${actual[c]} " +
          "(full pixel: rgba(${actual[0]}, ${actual[1]}, ${actual[2]}, ${actual[3]}))",
      )
    }
  }
}
