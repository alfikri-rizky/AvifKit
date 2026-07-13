package com.alfikri.rizky.avifkit

/**
 * Applies AVIF orientation transforms (`irot`/`imir`) to decoded pixel buffers.
 *
 * AVIF stores orientation as transformative item properties instead of rotating pixels: `irot`
 * rotates anti-clockwise in 90° steps, `imir` mirrors. libavif reports them but does NOT apply them
 * — decoders must. Per ISO/IEC 23008-12, rotation is applied first, then mirroring.
 *
 * Shared by both platforms so the (easy to get backwards) index math exists exactly once:
 * - iOS uses [apply] on the RGBA byte buffer from libavif.
 * - Android uses [applyToPixels] on the ARGB int array from the JNI wrapper.
 */
internal object RgbaTransform {

  class Result(val pixels: ByteArray, val width: Int, val height: Int)

  /** True when the transform is a no-op ([irotAngle] 0 rotations, no mirror axis). */
  fun isIdentity(irotAngle: Int, imirAxis: Int): Boolean = irotAngle and 3 == 0 && imirAxis < 0

  fun outputWidth(width: Int, height: Int, irotAngle: Int): Int =
    if (irotAngle and 1 == 0) width else height

  fun outputHeight(width: Int, height: Int, irotAngle: Int): Int =
    if (irotAngle and 1 == 0) height else width

  /**
   * Transform an RGBA8888 byte buffer ([rowBytes] source stride; output is tightly packed).
   *
   * @param irotAngle anti-clockwise rotation in 90° steps (0–3)
   * @param imirAxis mirror axis after rotation: 0 = top/bottom exchanged, 1 = left/right exchanged,
   *   negative = no mirror
   */
  fun apply(
    src: ByteArray,
    width: Int,
    height: Int,
    rowBytes: Int,
    irotAngle: Int,
    imirAxis: Int,
  ): Result {
    val angle = irotAngle and 3
    val outW = outputWidth(width, height, angle)
    val outH = outputHeight(width, height, angle)
    val out = ByteArray(outW * outH * 4)
    for (sy in 0 until height) {
      val srcRow = sy * rowBytes
      for (sx in 0 until width) {
        val s = srcRow + sx * 4
        val d = destPixelIndex(sx, sy, width, height, angle, imirAxis, outW, outH) * 4
        out[d] = src[s]
        out[d + 1] = src[s + 1]
        out[d + 2] = src[s + 2]
        out[d + 3] = src[s + 3]
      }
    }
    return Result(out, outW, outH)
  }

  /** Transform a tightly-packed pixel int array (e.g. ARGB_8888). Same semantics as [apply]. */
  fun applyToPixels(
    src: IntArray,
    width: Int,
    height: Int,
    irotAngle: Int,
    imirAxis: Int,
  ): IntArray {
    val angle = irotAngle and 3
    val outW = outputWidth(width, height, angle)
    val outH = outputHeight(width, height, angle)
    val out = IntArray(outW * outH)
    for (sy in 0 until height) {
      val srcRow = sy * width
      for (sx in 0 until width) {
        out[destPixelIndex(sx, sy, width, height, angle, imirAxis, outW, outH)] = src[srcRow + sx]
      }
    }
    return out
  }

  /** Where source pixel (sx, sy) lands after rotating anti-clockwise then mirroring. */
  private fun destPixelIndex(
    sx: Int,
    sy: Int,
    width: Int,
    height: Int,
    angle: Int,
    imirAxis: Int,
    outW: Int,
    outH: Int,
  ): Int {
    var dx: Int
    var dy: Int
    when (angle) {
      1 -> { // 90° anti-clockwise: top-right corner becomes top-left
        dx = sy
        dy = width - 1 - sx
      }
      2 -> { // 180°
        dx = width - 1 - sx
        dy = height - 1 - sy
      }
      3 -> { // 270° anti-clockwise (= 90° clockwise): top-left corner becomes top-right
        dx = height - 1 - sy
        dy = sx
      }
      else -> {
        dx = sx
        dy = sy
      }
    }
    when (imirAxis) {
      0 -> dy = outH - 1 - dy // top and bottom parts exchanged
      1 -> dx = outW - 1 - dx // left and right parts exchanged
    }
    return dy * outW + dx
  }
}
