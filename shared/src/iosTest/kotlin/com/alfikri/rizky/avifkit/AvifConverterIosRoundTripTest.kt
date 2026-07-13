package com.alfikri.rizky.avifkit

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

  /**
   * H4 regression: AVIF stores orientation as irot/imir properties that libavif reports but does
   * not apply — the decoder must rotate/mirror the pixels itself. The fixture is written by raw
   * libavif with irot angle 1 (90° anti-clockwise).
   */
  @Test
  fun decode_appliesIrotOrientation() = runBlocking {
    // Black canvas with a red 8×8 marker block in the top-left corner (straight alpha, opaque).
    val src = solidRgba(0x00, 0x00, 0x00, 0xFF)
    for (y in 0 until 8) {
      for (x in 0 until 8) {
        src[(y * width + x) * 4] = 0xFF.toByte() // R
      }
    }
    val avif = encodeRawStraightAvif(src, width, height, irotAngle = 1)

    val decoded = AvifConverter().decodeAvif(ImageInput.from(avif))
    val out = uiImagePremultipliedRgba(decoded)

    // 90° CCW: dimensions swap and the top-left marker lands at the bottom-left.
    assertEquals(height, out.width, "rotated width")
    assertEquals(width, out.height, "rotated height")
    fun red(x: Int, y: Int): Int = out.pixels[(y * out.width + x) * 4].toInt() and 0xFF
    assertTrue(red(4, out.height - 4) > 0x80, "marker should move to the bottom-left corner")
    assertTrue(red(4, 4) < 0x40, "top-left corner should now be background")
  }

  /**
   * H1 regression: maxSize with an already-AVIF input must re-encode when the input exceeds the
   * target instead of silently returning the oversized original.
   */
  @Test
  fun maxSize_reencodesOversizedAvifInput() = runBlocking {
    // Random noise compresses poorly → a near-lossless encode is comfortably large.
    val rnd = kotlin.random.Random(42)
    val noise = ByteArray(width * height * 4)
    rnd.nextBytes(noise)
    for (i in 0 until width * height) noise[i * 4 + 3] = 0xFF.toByte()
    val image = makeImageFromPremultipliedRgba(noise, width, height)

    val converter = AvifConverter()
    val original =
      converter.encodeAvif(
        ImageInput.from(image),
        options = EncodingOptions(quality = 100, speed = 10, subsample = ChromaSubsample.YUV444),
      )
    val target = original.size / 2L

    val compressed =
      converter.encodeAvif(
        ImageInput.from(original),
        options = EncodingOptions(maxSize = target, speed = 10),
      )

    assertTrue(
      compressed.size <= target,
      "expected re-encode to ≤$target bytes, got ${compressed.size} (input ${original.size})",
    )
    assertTrue(converter.isAvifFile(ImageInput.from(compressed)), "output must still be AVIF")
  }

  /** Companion to the above: input that already fits the target passes through unchanged. */
  @Test
  fun maxSize_passesThroughAvifInputWithinTarget() = runBlocking {
    val converter = AvifConverter()
    val avif = converter.encodeAvif(ImageInput.from(makeTestImage()), Priority.BALANCED)

    val result =
      converter.encodeAvif(
        ImageInput.from(avif),
        options = EncodingOptions(maxSize = avif.size + 1000L),
      )

    assertTrue(result.contentEquals(avif), "fitting AVIF input must be returned byte-identical")
  }

  /**
   * M6 regression: an opaque source must NOT get an alpha channel in the output, while a
   * transparent source must. Verified via libavif's parse-time alphaPresent flag.
   */
  @Test
  fun opaqueEncode_omitsAlphaChannel_transparentKeepsIt() = runBlocking {
    val converter = AvifConverter()

    val opaque =
      converter.encodeAvif(
        ImageInput.from(
          makeImageFromPremultipliedRgba(solidRgba(0x30, 0x60, 0x90, 0xFF), width, height)
        ),
        options = EncodingOptions(quality = 90, speed = 10),
      )
    assertFalse(rawAvifAlphaPresent(opaque), "opaque source must not encode an alpha channel")

    val transparent =
      converter.encodeAvif(
        ImageInput.from(
          makeImageFromPremultipliedRgba(solidRgba(0x18, 0x30, 0x48, 0x80), width, height)
        ),
        options = EncodingOptions(quality = 90, speed = 10, alphaQuality = 90),
      )
    assertTrue(rawAvifAlphaPresent(transparent), "transparent source must encode an alpha channel")
  }

  /**
   * M2 regression: getImageInfo on AVIF bytes must report real dimensions and alpha via libavif
   * (the old path decoded through UIImage, which returns nil for AVIF on iOS 15).
   */
  @Test
  fun getImageInfo_onAvif_reportsDimensionsAndAlpha() = runBlocking {
    val converter = AvifConverter()
    val avif = encodeRawStraightAvif(solidRgba(0x40, 0x80, 0xC0, 0x80), width, height)

    val info = converter.getImageInfo(ImageInput.from(avif))
    assertEquals(width, info.width)
    assertEquals(height, info.height)
    assertEquals(ImageFormat.AVIF, info.format)
    assertTrue(info.hasAlpha, "alpha-bearing AVIF must report hasAlpha")
  }

  /**
   * M8 regression: resize is driven by pixel dimensions and clamps to >= 1px. A 200x100 source
   * capped at 50 must come out with its longest side at 50.
   */
  @Test
  fun maxDimension_downscalesByPixels() = runBlocking {
    val w = 200
    val h = 100
    val px = ByteArray(w * h * 4)
    for (i in 0 until w * h) {
      px[i * 4] = 0x40
      px[i * 4 + 1] = 0x80.toByte()
      px[i * 4 + 2] = 0xC0.toByte()
      px[i * 4 + 3] = 0xFF.toByte()
    }
    val image = makeImageFromPremultipliedRgba(px, w, h)

    val avif =
      AvifConverter()
        .encodeAvif(
          ImageInput.from(image),
          options = EncodingOptions(maxDimension = 50, speed = 10),
        )

    val info = AvifConverter().getImageInfo(ImageInput.from(avif))
    assertEquals(50, info.width, "longest side should be scaled to the 50px cap")
    assertEquals(25, info.height)
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
   * [irotAngle]/[imirAxis] write the corresponding orientation properties into the file.
   */
  private fun encodeRawStraightAvif(
    rgba: ByteArray,
    width: Int,
    height: Int,
    irotAngle: Int = 0,
    imirAxis: Int = -1,
  ): ByteArray = memScoped {
    val image =
      avifImageCreate(width.toUInt(), height.toUInt(), 8u, AVIF_PIXEL_FORMAT_YUV444)
        ?: error("avifImageCreate failed")
    if (irotAngle != 0) {
      image.pointed.transformFlags = image.pointed.transformFlags or AVIF_TRANSFORM_IROT.toUInt()
      image.pointed.irot.angle = irotAngle.toUByte()
    }
    if (imirAxis >= 0) {
      image.pointed.transformFlags = image.pointed.transformFlags or AVIF_TRANSFORM_IMIR.toUInt()
      image.pointed.imir.axis = imirAxis.toUByte()
    }
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
          bytes.usePinned { pinnedOut -> memcpy(pinnedOut.addressOf(0), output.data, output.size) }
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

  /** Whether an AVIF file carries an alpha channel, via libavif's parse-time alphaPresent flag. */
  private fun rawAvifAlphaPresent(avif: ByteArray): Boolean = memScoped {
    val decoder = avifDecoderCreate() ?: error("avifDecoderCreate failed")
    try {
      avif.usePinned { pinned ->
        val res =
          avifDecoderSetIOMemory(decoder, pinned.addressOf(0).reinterpret(), avif.size.toULong())
        check(res == AVIF_RESULT_OK) { "setIOMemory: ${avifResultToString(res)?.toKString()}" }
        val parseRes = avifDecoderParse(decoder)
        check(parseRes == AVIF_RESULT_OK) { "parse: ${avifResultToString(parseRes)?.toKString()}" }
        decoder.pointed.alphaPresent != AVIF_FALSE
      }
    } finally {
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
