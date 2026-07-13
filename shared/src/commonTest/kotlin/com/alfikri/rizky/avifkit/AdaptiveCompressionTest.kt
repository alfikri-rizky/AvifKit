package com.alfikri.rizky.avifkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AdaptiveCompressionTest {

  /**
   * Fake encoder: output size is a monotonic function of quality (and maxDimension), so the search
   * behaves like a real codec without one. Returns the quality it was asked for; [sizeOf] maps that
   * to bytes. Tracks how many times it ran.
   */
  private class FakeEncoder {
    var calls = 0
      private set

    val optionsSeen = mutableListOf<EncodingOptions>()

    suspend fun encode(options: EncodingOptions): Int {
      calls++
      optionsSeen += options
      return options.quality
    }

    // quality 40..100 -> 4000..10000 bytes; a lower maxDimension shrinks it further.
    fun sizeOf(quality: Int): Long {
      val dimFactor =
        optionsSeenFor(quality)?.maxDimension?.let { it.coerceAtMost(2048) / 2048.0 } ?: 1.0
      return (quality * 100 * dimFactor).toLong()
    }

    private fun optionsSeenFor(quality: Int) = optionsSeen.lastOrNull { it.quality == quality }
  }

  @Test
  fun smart_findsHighestQualityThatFits() = runTest {
    val enc = FakeEncoder()
    // size(q) = q*100; target 7050 => q<=70 fits (7000), q=71 (7100) does not.
    val result =
      AdaptiveCompression.compress(
        EncodingOptions(compressionStrategy = CompressionStrategy.SMART),
        targetSize = 7050,
        encode = enc::encode,
        sizeOf = { (it * 100).toLong() },
      )
    assertEquals(70, result, "should pick the highest quality whose 7000-byte output fits 7050")
    assertTrue(enc.calls <= AdaptiveCompression.SMART_MAX_ATTEMPTS, "must respect the attempt cap")
  }

  @Test
  fun smart_fallsBackWhenNothingFits() = runTest {
    val enc = FakeEncoder()
    val result =
      AdaptiveCompression.compress(
        EncodingOptions(compressionStrategy = CompressionStrategy.SMART),
        targetSize = 10, // smaller than any achievable size
        encode = enc::encode,
        sizeOf = { (it * 100).toLong() },
      )
    // Fallback options encode at quality 40.
    assertEquals(40, result)
    assertEquals(40, enc.optionsSeen.last().quality)
    assertEquals(10, enc.optionsSeen.last().speed, "fallback uses the aggressive speed preset")
  }

  @Test
  fun strict_returnsSmallestThatFits() = runTest {
    val enc = FakeEncoder()
    val result =
      AdaptiveCompression.compress(
        EncodingOptions(quality = 80, compressionStrategy = CompressionStrategy.STRICT),
        targetSize = 6200,
        encode = enc::encode,
        sizeOf = { (it * 100).toLong() },
      )
    // Progressive lowering lands on quality 60 (6000 bytes) — the smallest that fits 6200.
    assertEquals(60, result)
    assertTrue((result * 100).toLong() <= 6200)
  }

  @Test
  fun strict_stopsEarlyAtParameterFixedPoint() = runTest {
    val enc = FakeEncoder()
    AdaptiveCompression.compress(
      EncodingOptions(quality = 80, compressionStrategy = CompressionStrategy.STRICT),
      targetSize = 6200,
      encode = enc::encode,
      sizeOf = { (it * 100).toLong() },
    )
    // Once quality/alpha floor out, further attempts can't shrink the output, so the loop must
    // break well before the 10-attempt cap instead of burning them all.
    assertTrue(
      enc.calls < AdaptiveCompression.STRICT_MAX_ATTEMPTS,
      "expected early termination, but ran ${enc.calls} attempts",
    )
  }

  @Test
  fun adjust_flooringReachesFixedPoint() {
    // A tiny miss (else branch) repeatedly applied must converge so STRICT can terminate.
    var options = EncodingOptions(quality = 62, alphaQuality = 72)
    repeat(5) {
      val next =
        AdaptiveCompression.adjustCompressionParameters(options, currentSize = 100, targetSize = 99)
      if (next == options) return // fixed point reached
      options = next
    }
    assertEquals(60, options.quality)
    assertEquals(70, options.alphaQuality)
  }
}
