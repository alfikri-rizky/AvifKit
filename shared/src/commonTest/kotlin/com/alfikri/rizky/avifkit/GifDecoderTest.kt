package com.alfikri.rizky.avifkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GifDecoderTest {

  private val white = intArrayOf(255, 255, 255, 255)
  private val red = intArrayOf(255, 0, 0, 255)
  private val blue = intArrayOf(0, 0, 255, 255)
  private val green = intArrayOf(0, 255, 0, 255)
  private val transparent = intArrayOf(0, 0, 0, 0)

  @Test
  fun parse_readsStructureWithoutDecodingPixels() {
    val gif = assertNotNull(GifDecoder.parse(GifFixtures.MOVING_SQUARE))

    assertEquals(64, gif.width)
    assertEquals(48, gif.height)
    assertEquals(6, gif.frameCount)
    assertEquals(240, gif.durationMillis)
    assertEquals(0, gif.loopCount, "NETSCAPE loop 0 means forever")
    assertTrue(gif.delaysMillis.all { it == 40 })
  }

  @Test
  fun parse_rejectsNonGif() {
    assertNull(GifDecoder.parse(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
    assertNull(GifDecoder.parse(ByteArray(0)))
    // A GIF header with no image block is not an animation and has nothing to encode.
    assertNull(GifDecoder.parse(GifFixtures.MOVING_SQUARE.copyOfRange(0, 13)))
  }

  @Test
  fun isAnimated_isFalseForNonGifBytes() {
    assertFalse(GifDecoder.isAnimated(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    assertTrue(GifDecoder.isAnimated(GifFixtures.MOVING_SQUARE))
  }

  /**
   * The frames after the first are partial rectangles, so getting this right means the LZW decode,
   * the frame offsets and the disposal handling all agree — a decoder that ignored compositing
   * would leave the background wrong or the previous square still painted.
   */
  @Test
  fun decodeFrames_compositesEveryFrameOntoTheCanvas() {
    val gif = assertNotNull(GifDecoder.parse(GifFixtures.MOVING_SQUARE))
    var seen = 0

    gif.decodeFrames { index, rgba, delayMillis ->
      assertEquals(64 * 48 * 4, rgba.size)
      assertEquals(40, delayMillis)

      val centerX = GifFixtures.squareCenterX(index, gif.frameCount)
      assertPixel(
        expected = if (index % 2 == 0) red else blue,
        rgba = rgba,
        width = gif.width,
        x = centerX,
        y = GifFixtures.SQUARE_CENTER_Y,
        label = "frame $index square",
      )
      assertPixel(white, rgba, gif.width, x = 2, y = 45, label = "frame $index background")
      seen++
    }

    assertEquals(6, seen)
  }

  /**
   * Every row of the fixture is a flat colour keyed to its own y, so the four interlace passes have
   * to be unwound to land any row on the right colour. Row 0 alone would pass either way — it is
   * the first row of pass 1 — which is why this walks all 24.
   */
  @Test
  fun decodeFrames_handlesInterlacedRows() {
    val gif = assertNotNull(GifDecoder.parse(GifFixtures.INTERLACED))
    assertEquals(32, gif.width)
    assertEquals(24, gif.height)
    assertEquals(3, gif.frameCount)
    assertEquals(3, gif.loopCount)

    val palette = listOf(red, blue, green, white)
    var seen = 0
    gif.decodeFrames { index, rgba, delayMillis ->
      assertEquals(50, delayMillis)
      for (y in 0 until gif.height) {
        assertPixel(palette[(y + index) % 4], rgba, gif.width, x = 5, y = y, "frame $index row $y")
      }
      seen++
    }
    assertEquals(3, seen)
  }

  @Test
  fun decodeFrames_keepsTransparencyAndClearsToBackground() {
    val gif = assertNotNull(GifDecoder.parse(GifFixtures.TRANSPARENT))
    assertTrue(gif.hasTransparency)
    assertEquals(intArrayOf(60, 70).toList(), gif.delaysMillis.toList())

    val frames = mutableListOf<ByteArray>()
    gif.decodeFrames { _, rgba, _ -> frames += rgba.copyOf() }

    assertPixel(red, frames[0], gif.width, 0, 0, "frame 0 painted")
    assertPixel(transparent, frames[0], gif.width, 4, 0, "frame 0 untouched")
    // Frame 0 disposes to background, so its red block must be gone by frame 1.
    assertPixel(transparent, frames[1], gif.width, 0, 0, "frame 1 after dispose-to-background")
    assertPixel(blue, frames[1], gif.width, 4, 0, "frame 1 painted")
  }

  @Test
  fun decodeFrames_survivesTruncatedPixelData() {
    val truncated = GifFixtures.MOVING_SQUARE.copyOfRange(0, GifFixtures.MOVING_SQUARE.size / 2)
    val gif = assertNotNull(GifDecoder.parse(truncated))
    // Whatever survived the truncation must still decode without throwing; a partly-readable GIF
    // is what arrives from a cancelled download, and it should degrade rather than crash.
    gif.decodeFrames { _, rgba, _ -> assertEquals(64 * 48 * 4, rgba.size) }
  }

  private fun assertPixel(
    expected: IntArray,
    rgba: ByteArray,
    width: Int,
    x: Int,
    y: Int,
    label: String,
  ) {
    val offset = (y * width + x) * 4
    val actual = IntArray(4) { rgba[offset + it].toInt() and 0xFF }
    assertEquals(
      expected.toList(),
      actual.toList(),
      "$label at ($x, $y): expected ${expected.toList()}, was ${actual.toList()}",
    )
  }
}
