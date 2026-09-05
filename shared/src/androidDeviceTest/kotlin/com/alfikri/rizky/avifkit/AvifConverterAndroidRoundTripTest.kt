package com.alfikri.rizky.avifkit

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device validation of the Android JNI codec path — the Android counterpart of
 * `AvifConverterIosRoundTripTest`. Runs with `./gradlew :shared:connectedAndroidDeviceTest` against
 * a connected device/emulator (also wired into CI).
 */
@RunWith(AndroidJUnit4::class)
class AvifConverterAndroidRoundTripTest {

  private val width = 64
  private val height = 48

  private fun solidBitmap(color: Int): Bitmap =
    Bitmap.createBitmap(IntArray(width * height) { color }, width, height, Bitmap.Config.ARGB_8888)

  @Test
  fun isAvifSupported_isTrue() {
    assertTrue("codec .so must load with libavif compiled in", AvifConverter().isAvifSupported())
  }

  @Test
  fun encodeThenDecode_roundTrips() {
    runBlocking {
      val converter = AvifConverter()
      val bitmap = solidBitmap(Color.argb(0xFF, 0x20, 0x80, 0xC0))

      val avif = converter.encodeAvif(ImageInput.from(bitmap), Priority.BALANCED)
      assertTrue("AVIF output should be non-trivial, was ${avif.size} bytes", avif.size > 12)
      assertTrue(
        "encoded bytes must carry an AVIF signature",
        converter.isAvifFile(ImageInput.from(avif)),
      )

      val decoded = converter.decodeAvif(ImageInput.from(avif))
      assertEquals(width, decoded.width)
      assertEquals(height, decoded.height)
      assertPixelNear(Color.argb(0xFF, 0x20, 0x80, 0xC0), decoded.getPixel(width / 2, height / 2))
    }
  }

  /** C2 regression: alphaQuality now reaches the encoder and alpha survives a round trip. */
  @Test
  fun alpha_roundTripsStraightAlpha() {
    runBlocking {
      val converter = AvifConverter()
      val original = Color.argb(0x80, 0x40, 0x80, 0xC0)
      val bitmap = solidBitmap(original)

      val avif =
        converter.encodeAvif(
          ImageInput.from(bitmap),
          options =
            EncodingOptions(
              quality = 95,
              speed = 10,
              subsample = ChromaSubsample.YUV444,
              alphaQuality = 100,
            ),
        )
      val decoded = converter.decodeAvif(ImageInput.from(avif))

      assertPixelNear(original, decoded.getPixel(width / 2, height / 2))
    }
  }

  /** C3 regression: lossless=true must round-trip pixel-exact (YUV444 + identity matrix). */
  @Test
  fun lossless_roundTripsPixelExact() {
    runBlocking {
      val src = IntArray(width * height)
      for (y in 0 until height) {
        for (x in 0 until width) {
          src[y * width + x] =
            Color.argb(0xFF, (x * 8 + y) and 0xFF, (x * 3 + y * 5) and 0xFF, (x + y * 11) and 0xFF)
        }
      }
      val bitmap = Bitmap.createBitmap(src, width, height, Bitmap.Config.ARGB_8888)

      val converter = AvifConverter()
      val avif =
        converter.encodeAvif(
          ImageInput.from(bitmap),
          options = EncodingOptions(lossless = true, speed = 10),
        )
      val decoded = converter.decodeAvif(ImageInput.from(avif))

      val out = IntArray(width * height)
      decoded.getPixels(out, 0, width, 0, 0, width, height)
      assertTrue(
        "lossless encode must round-trip pixel-exact; first diff at index " +
          out.indices.firstOrNull { out[it] != src[it] },
        out.contentEquals(src),
      )
    }
  }

  /** H1 regression: maxSize with oversized AVIF input must re-encode, not pass through. */
  @Test
  fun maxSize_reencodesOversizedAvifInput() {
    runBlocking {
      val rnd = Random(42)
      val noise = IntArray(width * height) { rnd.nextInt() or (0xFF shl 24) }
      val bitmap = Bitmap.createBitmap(noise, width, height, Bitmap.Config.ARGB_8888)

      val converter = AvifConverter()
      val original =
        converter.encodeAvif(
          ImageInput.from(bitmap),
          options = EncodingOptions(quality = 100, speed = 10, subsample = ChromaSubsample.YUV444),
        )
      val target = original.size / 2L

      val compressed =
        converter.encodeAvif(
          ImageInput.from(original),
          options = EncodingOptions(maxSize = target, speed = 10),
        )

      assertTrue(
        "expected re-encode to <=$target bytes, got ${compressed.size} (input ${original.size})",
        compressed.size <= target,
      )
      assertTrue(converter.isAvifFile(ImageInput.from(compressed)))
    }
  }

  /** Companion to the above: input that already fits the target passes through unchanged. */
  @Test
  fun maxSize_passesThroughAvifInputWithinTarget() {
    runBlocking {
      val converter = AvifConverter()
      val avif =
        converter.encodeAvif(
          ImageInput.from(solidBitmap(Color.argb(0xFF, 0x20, 0x80, 0xC0))),
          Priority.BALANCED,
        )

      val result =
        converter.encodeAvif(
          ImageInput.from(avif),
          options = EncodingOptions(maxSize = avif.size + 1000L),
        )

      assertTrue("fitting AVIF input must pass through unchanged", result.contentEquals(avif))
    }
  }

  /** M1 regression: HARDWARE bitmaps (Coil/Glide default) must encode, not throw. */
  @Test
  fun hardwareBitmap_encodes() {
    runBlocking {
      // Bitmap.Config.HARDWARE is API 26; touching the field at all on API 24 is a
      // NoSuchFieldError, so this has nothing to exercise below O.
      assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

      // Build a software bitmap, then round-trip through HARDWARE config to simulate what image
      // loaders hand back. (HARDWARE bitmaps have no getPixels access.)
      val software = solidBitmap(Color.argb(0xFF, 0x20, 0x80, 0xC0))
      val hardware =
        software.copy(Bitmap.Config.HARDWARE, false)
          ?: return@runBlocking // device without HW bitmap support: nothing to test

      val avif = AvifConverter().encodeAvif(ImageInput.from(hardware), Priority.SPEED)
      assertTrue("hardware bitmap should encode to a real AVIF", avif.size > 12)
      assertTrue(AvifConverter().isAvifFile(ImageInput.from(avif)))
    }
  }

  /** M6 regression: opaque source omits the alpha channel; transparent keeps it. */
  @Test
  fun opaqueEncode_omitsAlpha_transparentKeepsIt() {
    runBlocking {
      val converter = AvifConverter()

      val opaque = solidBitmap(Color.argb(0xFF, 0x30, 0x60, 0x90))
      val opaqueAvif = converter.encodeAvif(ImageInput.from(opaque), Priority.SPEED)
      val opaqueInfo = converter.getImageInfo(ImageInput.from(opaqueAvif))
      assertTrue("opaque source must not encode an alpha channel", !opaqueInfo.hasAlpha)

      val transparent = solidBitmap(Color.argb(0x80, 0x18, 0x30, 0x48))
      val transparentAvif =
        converter.encodeAvif(
          ImageInput.from(transparent),
          options = EncodingOptions(quality = 90, speed = 10, alphaQuality = 90),
        )
      val transparentInfo = converter.getImageInfo(ImageInput.from(transparentAvif))
      assertTrue("transparent source must encode an alpha channel", transparentInfo.hasAlpha)
    }
  }

  /** M2 regression: getImageInfo on AVIF reports real dims + alpha (works below API 31). */
  @Test
  fun getImageInfo_onAvif_reportsDimensionsAndAlpha() {
    runBlocking {
      val converter = AvifConverter()
      val avif =
        converter.encodeAvif(
          ImageInput.from(solidBitmap(Color.argb(0x80, 0x40, 0x80, 0xC0))),
          options = EncodingOptions(quality = 90, speed = 10, alphaQuality = 90),
        )
      val info = converter.getImageInfo(ImageInput.from(avif))
      assertEquals(width, info.width)
      assertEquals(height, info.height)
      assertEquals(ImageFormat.AVIF, info.format)
      assertTrue("alpha-bearing AVIF must report hasAlpha", info.hasAlpha)
    }
  }

  /** M8 regression: an extreme aspect ratio must not crash resize (short side clamps to >= 1). */
  @Test
  fun extremeAspectRatio_resizesWithoutCrashing() {
    runBlocking {
      val wide = Bitmap.createBitmap(4000, 2, Bitmap.Config.ARGB_8888)
      wide.eraseColor(Color.argb(0xFF, 0x10, 0x20, 0x30))

      val avif =
        AvifConverter()
          .encodeAvif(
            ImageInput.from(wide),
            options = EncodingOptions(maxDimension = 1024, speed = 10),
          )
      val info = AvifConverter().getImageInfo(ImageInput.from(avif))
      assertEquals(1024, info.width)
      assertEquals("short side must clamp to 1px, not 0", 1, info.height)
    }
  }

  private fun assertPixelNear(expected: Int, actual: Int, tolerance: Int = 12) {
    val channels = listOf("A" to 24, "R" to 16, "G" to 8, "B" to 0)
    for ((name, shift) in channels) {
      val e = (expected shr shift) and 0xFF
      val a = (actual shr shift) and 0xFF
      assertTrue(
        "$name: expected ~$e, got $a (expected #${Integer.toHexString(expected)}, " +
          "actual #${Integer.toHexString(actual)})",
        abs(e - a) <= tolerance,
      )
    }
  }
}
