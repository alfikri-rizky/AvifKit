package com.alfikri.rizky.avifkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GifFrameStreamTest {

  @Test
  fun infiniteGifLoopBecomesInfiniteAvifRepetition() {
    val stream = GifFrameStream(assertNotNull(GifDecoder.parse(GifFixtures.MOVING_SQUARE)), null)

    assertEquals(GifFrameStream.REPETITION_INFINITE, stream.repetitionCount)
    assertEquals(6, stream.frameCount)
    assertEquals(240, stream.durationMillis)
  }

  /** libavif counts repeats, GIF counts plays: 3 plays is 2 repeats. */
  @Test
  fun finiteGifLoopLosesOneToBecomeARepeatCount() {
    val stream = GifFrameStream(assertNotNull(GifDecoder.parse(GifFixtures.INTERLACED)), null)

    assertEquals(2, stream.repetitionCount)
    assertEquals(3, AvifFormat.loopCountOf(stream.repetitionCount), "and back again")
  }

  @Test
  fun loopCountOf_mapsLibavifSentinelsToForever() {
    assertEquals(0, AvifFormat.loopCountOf(-1), "AVIF_REPETITION_COUNT_INFINITE")
    assertEquals(0, AvifFormat.loopCountOf(-2), "AVIF_REPETITION_COUNT_UNKNOWN")
    assertEquals(1, AvifFormat.loopCountOf(0), "0 repeats is one play")
  }

  @Test
  fun withoutMaxDimension_framesArriveAtSourceSize() {
    val stream = GifFrameStream(assertNotNull(GifDecoder.parse(GifFixtures.MOVING_SQUARE)), null)
    assertEquals(64, stream.width)
    assertEquals(48, stream.height)

    var frames = 0
    stream.forEachFrame { rgba, durationMillis ->
      assertEquals(64 * 48 * 4, rgba.size)
      assertEquals(40, durationMillis)
      frames++
    }
    assertEquals(6, frames)
  }

  @Test
  fun maxDimensionScalesEveryFrame() {
    val stream = GifFrameStream(assertNotNull(GifDecoder.parse(GifFixtures.MOVING_SQUARE)), 32)
    assertEquals(32, stream.width)
    assertEquals(24, stream.height)

    var frames = 0
    stream.forEachFrame { rgba, _ ->
      assertEquals(32 * 24 * 4, rgba.size)
      frames++
    }
    assertEquals(6, frames)
  }

  @Test
  fun maxDimensionLargerThanTheGifIsANoOp() {
    val stream = GifFrameStream(assertNotNull(GifDecoder.parse(GifFixtures.MOVING_SQUARE)), 4096)
    assertEquals(64, stream.width)
    assertEquals(48, stream.height)
  }

  /**
   * The square is the only non-white thing in the frame, so after a 2:1 downscale it has to still
   * be there and still be red — an averaging bug that reads the wrong rows washes it out to white.
   */
  @Test
  fun downscaledFramesKeepTheirContent() {
    val stream = GifFrameStream(assertNotNull(GifDecoder.parse(GifFixtures.MOVING_SQUARE)), 32)
    var checked = false

    stream.forEachFrame { rgba, _ ->
      if (!checked) {
        val offset = (GifFixtures.SQUARE_CENTER_Y / 2 * 32 + 4) * 4
        assertTrue(
          (rgba[offset].toInt() and 0xFF) > 200 && (rgba[offset + 2].toInt() and 0xFF) < 60,
          "downscaled frame 0 should still be red at its square",
        )
        checked = true
      }
    }
    assertTrue(checked)
  }
}
