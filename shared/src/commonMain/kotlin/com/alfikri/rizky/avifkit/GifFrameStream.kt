package com.alfikri.rizky.avifkit

/**
 * Feeds an animated GIF to a platform encoder one frame at a time, applying [EncodingOptions]
 * `maxDimension` on the way through.
 *
 * The Android (JNI) and iOS (cinterop) encoders differ only in how they receive a frame, so
 * everything else — sizing, buffer reuse, the AVIF timing model — lives here and is exercised by
 * the same common tests on both platforms.
 */
internal class GifFrameStream(private val gif: GifDecoder, maxDimension: Int?) {

  private val scaled: Pair<Int, Int> = RgbaScale.fit(gif.width, gif.height, maxDimension)

  val width: Int = scaled.first
  val height: Int = scaled.second
  val frameCount: Int = gif.frameCount
  val hasAlpha: Boolean = gif.hasTransparency
  val durationMillis: Long = gif.durationMillis

  /** Number of playbacks, in libavif's `repetitionCount` terms: -1 is infinite, `n` plays n+1. */
  val repetitionCount: Int =
    if (gif.loopCount <= 0) REPETITION_INFINITE else (gif.loopCount - 1).coerceAtLeast(0)

  private val needsScaling = width != gif.width || height != gif.height

  /**
   * Calls [action] once per frame with an RGBA8888 buffer of exactly `width * height * 4` bytes.
   * The buffer is reused, so [action] must consume it before returning.
   */
  fun forEachFrame(action: (rgba: ByteArray, durationMillis: Int) -> Unit) {
    val resized = if (needsScaling) ByteArray(width * height * 4) else null
    gif.decodeFrames { _, rgba, delayMillis ->
      if (resized == null) {
        action(rgba, delayMillis)
      } else {
        RgbaScale.downscale(rgba, gif.width, gif.height, resized, width, height)
        action(resized, delayMillis)
      }
    }
  }

  companion object {
    /**
     * AVIF frame durations are integers in `timescale` units. GIF delays are whole milliseconds
     * after the decoder's clamp, so a 1 kHz timescale carries them across exactly — no rounding, no
     * drift accumulated over hundreds of frames.
     */
    const val TIMESCALE = 1000

    const val REPETITION_INFINITE = -1
  }
}
