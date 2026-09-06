package com.alfikri.rizky.avifkit

/**
 * Box-filter downscaling for the RGBA8888 buffers the animation path works in.
 *
 * Still images are resized by the platform (`Bitmap.createScaledBitmap`, `UIGraphicsImageRenderer`)
 * before they ever become RGBA bytes. Animation frames never become a platform bitmap — they go
 * straight from [GifDecoder] to the encoder — so `maxDimension` has to be honoured here or it would
 * silently do nothing for animated input.
 */
internal object RgbaScale {

  /** The size [width]x[height] becomes when its longest edge is capped at [maxDimension]. */
  fun fit(width: Int, height: Int, maxDimension: Int?): Pair<Int, Int> {
    if (maxDimension == null || maxDimension <= 0) return width to height
    val longest = maxOf(width, height)
    if (longest <= maxDimension) return width to height
    val scale = maxDimension.toDouble() / longest
    return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
  }

  /**
   * Averages each destination pixel over the source rectangle it covers. Writes into [destination]
   * rather than allocating, because the animation path reuses one buffer for every frame.
   */
  fun downscale(
    source: ByteArray,
    sourceWidth: Int,
    sourceHeight: Int,
    destination: ByteArray,
    destWidth: Int,
    destHeight: Int,
  ) {
    val xRatio = sourceWidth.toDouble() / destWidth
    val yRatio = sourceHeight.toDouble() / destHeight

    for (y in 0 until destHeight) {
      val yStart = (y * yRatio).toInt()
      val yEnd = ((y + 1) * yRatio).toInt().coerceAtLeast(yStart + 1).coerceAtMost(sourceHeight)
      for (x in 0 until destWidth) {
        val xStart = (x * xRatio).toInt()
        val xEnd = ((x + 1) * xRatio).toInt().coerceAtLeast(xStart + 1).coerceAtMost(sourceWidth)

        var r = 0
        var g = 0
        var b = 0
        var a = 0
        var count = 0
        for (sy in yStart until yEnd) {
          var index = (sy * sourceWidth + xStart) * 4
          for (sx in xStart until xEnd) {
            r += source[index].toInt() and 0xFF
            g += source[index + 1].toInt() and 0xFF
            b += source[index + 2].toInt() and 0xFF
            a += source[index + 3].toInt() and 0xFF
            index += 4
            count++
          }
        }
        if (count == 0) count = 1

        val target = (y * destWidth + x) * 4
        destination[target] = (r / count).toByte()
        destination[target + 1] = (g / count).toByte()
        destination[target + 2] = (b / count).toByte()
        destination[target + 3] = (a / count).toByte()
      }
    }
  }
}
