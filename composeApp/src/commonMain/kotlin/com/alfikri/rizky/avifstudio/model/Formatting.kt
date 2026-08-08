package com.alfikri.rizky.avifstudio.model

import kotlin.math.abs
import kotlin.math.roundToLong

/** 1024-based, one decimal from MB up: `842 KB`, `1.4 MB`. Shown on every result row. */
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
 * `85.0` means the output is 15% of the input. Goes negative when a conversion made the file
 * bigger, which is shown rather than hidden.
 *
 * Capped below 100 whenever a file was written: a 2.3 MB photo landing at 11 KB used to display as
 * "100% smaller".
 */
fun savingsPercent(originalBytes: Long, newBytes: Long): Double {
  if (originalBytes <= 0) return 0.0
  val saved = ((originalBytes - newBytes).toDouble() / originalBytes) * 100
  return if (newBytes > 0) saved.coerceAtMost(MAX_SAVINGS_PERCENT) else saved
}

/** `99`, `99.5` — the decimal is dropped when it is `.0`, where it would only be noise. */
fun formatPercent(value: Double): String {
  val scaled = (value * 10).roundToLong()
  return if (scaled % 10 == 0L) "${scaled / 10}" else formatOneDecimal(value)
}

private const val MAX_SAVINGS_PERCENT = 99.9

fun formatDimensions(width: Int, height: Int): String = "$width × $height"

fun formatDuration(millis: Long): String =
  if (millis < 1000) "$millis ms" else "${formatOneDecimal(millis / 1000.0)} s"
