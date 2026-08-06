package com.alfikri.rizky.avifstudio.model

import kotlin.math.abs
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
 * Percentage of the original size that was saved: `85.0` means the output is 15% of the input.
 *
 * Negative when a conversion made the file bigger — which really happens (re-encoding an
 * already-optimised JPEG at high quality, or PNG output from a photo), and hiding it would be lying
 * to the user.
 *
 * Capped just below 100 whenever a file was actually written, because nothing that still exists is
 * 100% smaller. Without the cap a 2.3 MB photo landing at 11 KB (99.5%) displayed as "100%
 * smaller".
 */
fun savingsPercent(originalBytes: Long, newBytes: Long): Double {
  if (originalBytes <= 0) return 0.0
  val saved = ((originalBytes - newBytes).toDouble() / originalBytes) * 100
  return if (newBytes > 0) saved.coerceAtMost(MAX_SAVINGS_PERCENT) else saved
}

/**
 * `99`, `99.5` — one decimal place, dropped when it is `.0`.
 *
 * The decimal earns its place at the top of the range, where whole numbers stop distinguishing a
 * good result from an impossible one. Below that it is noise, so `99.0` prints as `99`.
 */
fun formatPercent(value: Double): String {
  val scaled = (value * 10).roundToLong()
  return if (scaled % 10 == 0L) "${scaled / 10}" else formatOneDecimal(value)
}

/** The most any surviving file can claim. See [savingsPercent]. */
private const val MAX_SAVINGS_PERCENT = 99.9

/** `4032 × 3024` for dimension labels. */
fun formatDimensions(width: Int, height: Int): String = "$width × $height"

/** `1.2 s` / `840 ms` — encode timings, where sub-second precision is the interesting part. */
fun formatDuration(millis: Long): String =
  if (millis < 1000) "$millis ms" else "${formatOneDecimal(millis / 1000.0)} s"
