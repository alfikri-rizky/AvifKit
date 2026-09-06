package com.alfikri.rizky.avifkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking

/**
 * The iOS half of the animated AVIF validation, mirroring `AvifAnimationAndroidTest` assertion for
 * assertion so a platform-specific regression shows up as one suite failing and not the other. Runs
 * with `./gradlew :shared:iosSimulatorArm64Test`.
 */
@OptIn(ExperimentalForeignApi::class)
class AvifAnimationIosTest {

  private val gif = GifFixtures.MOVING_SQUARE
  private val frameCount = 6
  private val options = EncodingOptions(quality = 80, speed = 10)

  @Test
  fun animatedGif_becomesAnAvifImageSequence() = runBlocking {
    val converter = AvifConverter()
    val avif = converter.encodeAvif(ImageInput.from(gif), options = options)

    assertTrue(converter.isAvifFile(ImageInput.from(avif)), "output must be AVIF")
    val info = converter.getImageInfo(ImageInput.from(avif))
    assertEquals(64, info.width)
    assertEquals(48, info.height)
    assertEquals(frameCount, info.frameCount)
    assertTrue(info.isAnimated)
    assertEquals(240L, info.durationMillis)
    assertEquals(0, info.loopCount, "GIF loops forever, so must the AVIF")
  }

  /**
   * The point of the whole feature: every GIF frame survives, in order, with its own delay. The
   * per-frame colour check is what separates a real animation from six copies of frame 0, which is
   * exactly what a broken encoder loop produces.
   */
  @Test
  fun frames_roundTripInOrderWithTheirDelays() = runBlocking {
    val converter = AvifConverter()
    val avif = converter.encodeAvif(ImageInput.from(gif), options = options)

    val frames = converter.decodeAvifFrames(ImageInput.from(avif))
    assertEquals(frameCount, frames.size)

    frames.forEachIndexed { index, frame ->
      assertEquals(40, frame.durationMillis, "frame $index delay")
      val pixels = uiImagePremultipliedRgba(frame.bitmap)
      assertEquals(64, pixels.width, "frame $index width")

      val (r, g, b) =
        pixels.at(GifFixtures.squareCenterX(index, frameCount), GifFixtures.SQUARE_CENTER_Y)
      if (index % 2 == 0) {
        assertTrue(r > 150 && b < 90, "frame $index should be red at its square, was ($r,$g,$b)")
      } else {
        assertTrue(b > 150 && r < 90, "frame $index should be blue at its square, was ($r,$g,$b)")
      }
    }
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
      still.size < animated.size,
      "a single frame must be smaller than six (${still.size} vs ${animated.size})",
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
    assertEquals(0, frames[0].durationMillis, "a still has no playback time")
    assertEquals(64, uiImagePremultipliedRgba(frames[0].bitmap).width)
  }

  /** decodeAvif keeps its single-bitmap contract on an animation: the first frame. */
  @Test
  fun decodeAvif_onAnAnimation_returnsTheFirstFrame() = runBlocking {
    val converter = AvifConverter()
    val avif = converter.encodeAvif(ImageInput.from(gif), options = options)

    val pixels = uiImagePremultipliedRgba(converter.decodeAvif(ImageInput.from(avif)))
    assertEquals(64, pixels.width)
    assertEquals(48, pixels.height)
    val (r, _, _) = pixels.at(GifFixtures.squareCenterX(0, frameCount), GifFixtures.SQUARE_CENTER_Y)
    assertTrue(r > 150, "first frame is red at its square, was r=$r")
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
    assertEquals(3, info.loopCount, "GIF plays 3 times, so must the AVIF")
  }
}
