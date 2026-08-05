package com.alfikri.rizky.avifstudio.model

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Human-readable byte size, e.g. `842 KB`, `1.4 MB`.
 *
 * Uses 1024-based units and one decimal place from MB up, because "1.4 MB" reads better than
 * "1,468,006 bytes" and a photo app shows this number on every row.
 */
fun formatBytes(bytes: Long): String {
  if (bytes < 0) return "—"
  if (bytes < 1024) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024) return "${kb.roundToLong()} KB"
  val mb = kb / 1024.0
  if (mb < 1024) return "${formatOneDecimal(mb)} MB"
  return "${formatOneDecimal(mb / 1024.0)} GB"
}

/** `12.3` — one decimal place, without relying on JVM-only String.format. */
internal fun formatOneDecimal(value: Double): String {
  val scaled = (value * 10).roundToLong()
  val whole = scaled / 10
  val tenth = abs(scaled % 10)
  return "$whole.$tenth"
}

/**
 * Percentage of the original size that was saved: `85` means the output is 15% of the input.
 *
 * Negative when a conversion made the file bigger — which really happens (re-encoding an
 * already-optimised JPEG at high quality, or PNG output from a photo), and hiding it would be lying
 * to the user.
 */
fun savingsPercent(originalBytes: Long, newBytes: Long): Int {
  if (originalBytes <= 0) return 0
  return (((originalBytes - newBytes).toDouble() / originalBytes) * 100).roundToInt()
}

/** `1.4 MB → 210 KB` with the sign of the change spelled out, for result rows. */
fun formatSavings(originalBytes: Long, newBytes: Long): String {
  val percent = savingsPercent(originalBytes, newBytes)
  return when {
    percent > 0 -> "$percent% smaller"
    percent < 0 -> "${-percent}% larger"
    else -> "same size"
  }
}

/** `4032 × 3024` for dimension labels. */
fun formatDimensions(width: Int, height: Int): String = "$width × $height"

/** `1.2 s` / `840 ms` — encode timings, where sub-second precision is the interesting part. */
fun formatDuration(millis: Long): String =
  if (millis < 1000) "$millis ms" else "${formatOneDecimal(millis / 1000.0)} s"

/**
 * Scales [width] × [height] so the longest edge is at most [maxDimension], matching what AvifKit's
 * `EncodingOptions.maxDimension` does, so the UI can predict output dimensions before encoding.
 * Never upscales.
 */
fun scaledDimensions(width: Int, height: Int, maxDimension: Int?): Pair<Int, Int> {
  if (maxDimension == null || maxDimension <= 0) return width to height
  val longest = maxOf(width, height)
  if (longest <= maxDimension) return width to height
  val scale = maxDimension.toDouble() / longest
  // Never round a non-empty image down to a zero-pixel edge.
  val scaledWidth = (width * scale).roundToInt().coerceAtLeast(1)
  val scaledHeight = (height * scale).roundToInt().coerceAtLeast(1)
  return scaledWidth to scaledHeight
}
