package com.alfikri.rizky.avifkit

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Platform-agnostic adaptive-compression strategies for [EncodingOptions.maxSize].
 *
 * Both platforms previously duplicated this ~150-line binary-search / progressive logic verbatim in
 * their `actual` classes, so bugs (like the maxSize/AVIF passthrough, H1) had to be fixed twice.
 * The logic is pure aside from the caller-supplied [encode] function, which the platform wires to a
 * single already-decoded source image — so, unlike before, the source is decoded ONCE and only the
 * encode step repeats across attempts.
 *
 * [encode] is generic over the platform result type (`ByteArray` on Android, `NSData` on iOS);
 * [sizeOf] reports its byte size.
 */
internal object AdaptiveCompression {

  const val SMART_MAX_ATTEMPTS = 8 // binary search over quality 40..100 needs log2(60) ≈ 6
  const val STRICT_MAX_ATTEMPTS = 10

  suspend fun <R> compress(
    options: EncodingOptions,
    targetSize: Long,
    encode: suspend (EncodingOptions) -> R,
    sizeOf: (R) -> Long,
  ): R =
    when (options.compressionStrategy) {
      CompressionStrategy.SMART -> smart(options, targetSize, encode, sizeOf)
      CompressionStrategy.STRICT -> strict(options, targetSize, encode, sizeOf)
    }

  /** Highest quality whose output still fits [targetSize], via binary search over quality. */
  private suspend fun <R> smart(
    options: EncodingOptions,
    targetSize: Long,
    encode: suspend (EncodingOptions) -> R,
    sizeOf: (R) -> Long,
  ): R {
    var bestResult: R? = null
    var minQuality = 40
    var maxQuality = 100
    var attempts = 0

    while (minQuality <= maxQuality && attempts < SMART_MAX_ATTEMPTS) {
      coroutineContext
        .ensureActive() // honor cancellation between (potentially multi-second) encodes
      val testQuality = (minQuality + maxQuality) / 2
      val result = encode(options.copy(quality = testQuality, maxSize = null))
      attempts++

      if (sizeOf(result) <= targetSize) {
        bestResult = result // fits — try to do better
        minQuality = testQuality + 1
      } else {
        maxQuality = testQuality - 1
      }
    }

    return bestResult ?: encode(fallbackOptions(options.preserveMetadata))
  }

  /**
   * Smallest output that still fits [targetSize], pushing settings more aggressive each attempt.
   */
  private suspend fun <R> strict(
    options: EncodingOptions,
    targetSize: Long,
    encode: suspend (EncodingOptions) -> R,
    sizeOf: (R) -> Long,
  ): R {
    var currentOptions = options.copy(maxSize = null)
    var bestResult: R? = null
    var bestSize = Long.MAX_VALUE
    var attempt = 0

    while (attempt < STRICT_MAX_ATTEMPTS) {
      coroutineContext.ensureActive()
      val result = encode(currentOptions)
      val size = sizeOf(result)
      if (size <= targetSize && size < bestSize) {
        bestResult = result
        bestSize = size
      }

      val next = adjustCompressionParameters(currentOptions, size, targetSize)
      attempt++
      // Once the parameters floor out, further attempts reproduce the same output — stop early
      // instead of burning the remaining attempts (they can't get any smaller).
      if (next == currentOptions) break
      currentOptions = next
    }

    return bestResult ?: encode(fallbackOptions(options.preserveMetadata))
  }

  /**
   * Progressively more aggressive settings, scaled by how far the last result missed the target.
   */
  fun adjustCompressionParameters(
    current: EncodingOptions,
    currentSize: Long,
    targetSize: Long,
  ): EncodingOptions {
    val reductionRatio = if (currentSize <= 0L) 1f else targetSize.toFloat() / currentSize

    return when {
      // Need >50% reduction
      reductionRatio < 0.5 ->
        current.copy(
          quality = maxOf(40, (current.quality * 0.7).toInt()),
          maxDimension = current.maxDimension?.let { (it * 0.75).toInt() } ?: 1920,
          subsample = ChromaSubsample.YUV420,
          alphaQuality = maxOf(50, current.alphaQuality - 20),
          speed = minOf(10, current.speed + 2),
        )

      // Need 25-50% reduction
      reductionRatio < 0.75 ->
        current.copy(
          quality = maxOf(50, current.quality - 15),
          maxDimension = current.maxDimension?.let { (it * 0.85).toInt() } ?: 2560,
          alphaQuality = maxOf(60, current.alphaQuality - 10),
          speed = minOf(10, current.speed + 1),
        )

      // Need <25% reduction
      else ->
        current.copy(
          quality = maxOf(60, current.quality - 8),
          alphaQuality = maxOf(70, current.alphaQuality - 5),
        )
    }
  }

  /**
   * Last-resort aggressive settings when no attempt met the target.
   *
   * [preserveMetadata] is carried through from the caller's options rather than forced off: the
   * other attempts all keep it, and a fallback that silently strips metadata would make whether a
   * photo keeps its capture date depend on how hard the encoder had to work.
   */
  fun fallbackOptions(preserveMetadata: Boolean = false): EncodingOptions =
    EncodingOptions(
      quality = 40,
      speed = 10,
      subsample = ChromaSubsample.YUV420,
      alphaQuality = 50,
      maxDimension = 1024,
      preserveMetadata = preserveMetadata,
    )
}
