package com.alfikri.rizky.avifkit

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device validation of the animated AVIF path: an animated GIF in, an AVIF image sequence out,
 * and the frames back again. Runs with `./gradlew :shared:connectedAndroidDeviceTest`.
 *
 * The AVIF here is written by the streaming JNI encoder, so these also cover the handle lifecycle —
 * a leaked or double-freed encoder shows up as a crash in this suite rather than in production.
 */
@RunWith(AndroidJUnit4::class)
class AvifAnimationAndroidTest {

  private val gif = GifFixtures.MOVING_SQUARE
  private val frameCount = 6
  private val options = EncodingOptions(quality = 80, speed = 10)

  @Test
  fun animatedGif_becomesAnAvifImageSequence() = runBlocking {
    val converter = AvifConverter()
    val avif = converter.encodeAvif(ImageInput.from(gif), options = options)

    assertTrue("output must be AVIF", converter.isAvifFile(ImageInput.from(avif)))
    val info = converter.getImageInfo(ImageInput.from(avif))
    assertEquals(64, info.width)
    assertEquals(48, info.height)
    assertEquals(frameCount, info.frameCount)
    assertTrue("frameCount > 1 means isAnimated", info.isAnimated)
    assertEquals(240L, info.durationMillis)
    assertEquals("GIF loops forever, so must the AVIF", 0, info.loopCount)
  }

  /**
   * The point of the whole feature: every GIF frame survives, in order, with its own delay. Testing
   * that consecutive frames DIFFER is what separates a real animation from six copies of frame 0,
   * which is exactly what a broken encoder loop produces.
   */
  @Test
  fun frames_roundTripInOrderWithTheirDelays() = runBlocking {
    val converter = AvifConverter()
    val avif = converter.encodeAvif(ImageInput.from(gif), options = options)

    val frames = converter.decodeAvifFrames(ImageInput.from(avif))
    assertEquals(frameCount, frames.size)

    frames.forEachIndexed { index, frame ->
      assertEquals("frame $index size", 64, frame.bitmap.width)
      assertEquals("frame $index delay", 40, frame.durationMillis)

      val x = GifFixtures.squareCenterX(index, frameCount)
      val pixel = frame.bitmap.getPixel(x, GifFixtures.SQUARE_CENTER_Y)
      val red = (pixel shr 16) and 0xFF
      val blue = pixel and 0xFF
      if (index % 2 == 0) {
        assertTrue(
          "frame $index should be red at ($x,18), was #${Integer.toHexString(pixel)}",
          red > 150 && blue < 90,
        )
      } else {
        assertTrue(
          "frame $index should be blue at ($x,18), was #${Integer.toHexString(pixel)}",
          blue > 150 && red < 90,
        )
      }
    }

    assertNotEquals(
      "consecutive frames must differ, or nothing is animating",
      frames[0].bitmap.getPixel(GifFixtures.squareCenterX(0, frameCount), 18),
      frames[1].bitmap.getPixel(GifFixtures.squareCenterX(0, frameCount), 18),
    )
  }

  @Test
  fun encodeAnimationFalse_keepsOnlyTheFirstFrame() = runBlocking {
    val converter = AvifConverter()

    val still =
      converter.encodeAvif(ImageInput.from(gif), options = options.copy(encodeAnimation = false))
    val animated = converter.encodeAvif(ImageInput.from(gif), options = options)

    val stillInfo = converter.getImageInfo(ImageInput.from(still))
    assertEquals(1, stillInfo.frameCount)
    assertFalse(stillInfo.isAnimated)
    assertEquals(0L, stillInfo.durationMillis)
    assertTrue(
      "a single frame must be smaller than six (${still.size} vs ${animated.size})",
      still.size < animated.size,
    )
  }

  @Test
  fun getImageInfo_onTheGifItself_reportsFramesAndDuration() = runBlocking {
    val info = AvifConverter().getImageInfo(ImageInput.from(gif))

    assertEquals(ImageFormat.GIF, info.format)
    assertEquals(64, info.width)
    assertEquals(48, info.height)
    assertEquals(frameCount, info.frameCount)
    assertEquals(240L, info.durationMillis)
  }

  /** A still image is a one-frame sequence, so callers never have to branch before decoding. */
  @Test
  fun decodeAvifFrames_onAStillImage_returnsOneFrame() = runBlocking {
    val converter = AvifConverter()
    val still =
      converter.encodeAvif(ImageInput.from(gif), options = options.copy(encodeAnimation = false))

    val frames = converter.decodeAvifFrames(ImageInput.from(still))
    assertEquals(1, frames.size)
    assertEquals("a still has no playback time", 0, frames[0].durationMillis)
    assertEquals(64, frames[0].bitmap.width)
  }

  /** decodeAvif keeps its single-bitmap contract on an animation: the first frame. */
  @Test
  fun decodeAvif_onAnAnimation_returnsTheFirstFrame() = runBlocking {
    val converter = AvifConverter()
    val avif = converter.encodeAvif(ImageInput.from(gif), options = options)

    val bitmap = converter.decodeAvif(ImageInput.from(avif))
    assertEquals(64, bitmap.width)
    assertEquals(48, bitmap.height)
    val pixel = bitmap.getPixel(GifFixtures.squareCenterX(0, frameCount), 18)
    assertTrue("first frame is red at its square", ((pixel shr 16) and 0xFF) > 150)
  }

  @Test
  fun maxDimension_scalesEveryFrame() = runBlocking {
    val converter = AvifConverter()
    val avif = converter.encodeAvif(ImageInput.from(gif), options = options.copy(maxDimension = 32))

    val info = converter.getImageInfo(ImageInput.from(avif))
    assertEquals(32, info.width)
    assertEquals(24, info.height)
    assertEquals(frameCount, info.frameCount)
  }

  /**
   * maxSize is documented as not applying to animated output — the animation is kept whole rather
   * than trimmed to a byte budget. This pins that behaviour so it cannot drift silently.
   */
  @Test
  fun maxSize_doesNotTrimAnAnimation() = runBlocking {
    val converter = AvifConverter()
    val avif = converter.encodeAvif(ImageInput.from(gif), options = options.copy(maxSize = 1024L))

    assertEquals(frameCount, converter.getImageInfo(ImageInput.from(avif)).frameCount)
  }

  @Test
  fun interlacedGif_encodesAllItsFrames() = runBlocking {
    val converter = AvifConverter()
    val avif = converter.encodeAvif(ImageInput.from(GifFixtures.INTERLACED), options = options)

    val info = converter.getImageInfo(ImageInput.from(avif))
    assertEquals(3, info.frameCount)
    assertEquals(150L, info.durationMillis)
    assertEquals("GIF plays 3 times, so must the AVIF", 3, info.loopCount)
  }
}
