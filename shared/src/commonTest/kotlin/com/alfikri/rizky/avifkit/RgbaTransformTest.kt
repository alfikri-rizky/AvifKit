package com.alfikri.rizky.avifkit

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RgbaTransformTest {

  // 2 wide × 3 tall source grid:
  //   1 2
  //   3 4
  //   5 6
  private val src = intArrayOf(1, 2, 3, 4, 5, 6)
  private val w = 2
  private val h = 3

  @Test
  fun identity_isRecognized() {
    assertTrue(RgbaTransform.isIdentity(irotAngle = 0, imirAxis = -1))
    assertFalse(RgbaTransform.isIdentity(irotAngle = 1, imirAxis = -1))
    assertFalse(RgbaTransform.isIdentity(irotAngle = 0, imirAxis = 0))
  }

  @Test
  fun rotate90AntiClockwise() {
    // The right column becomes the top row:
    //   2 4 6
    //   1 3 5
    val out = RgbaTransform.applyToPixels(src, w, h, irotAngle = 1, imirAxis = -1)
    assertContentEquals(intArrayOf(2, 4, 6, 1, 3, 5), out)
  }

  @Test
  fun rotate180() {
    //   6 5
    //   4 3
    //   2 1
    val out = RgbaTransform.applyToPixels(src, w, h, irotAngle = 2, imirAxis = -1)
    assertContentEquals(intArrayOf(6, 5, 4, 3, 2, 1), out)
  }

  @Test
  fun rotate270AntiClockwise() {
    // 90° clockwise — the left column becomes the top row, reversed:
    //   5 3 1
    //   6 4 2
    val out = RgbaTransform.applyToPixels(src, w, h, irotAngle = 3, imirAxis = -1)
    assertContentEquals(intArrayOf(5, 3, 1, 6, 4, 2), out)
  }

  @Test
  fun mirrorTopBottom() {
    //   5 6
    //   3 4
    //   1 2
    val out = RgbaTransform.applyToPixels(src, w, h, irotAngle = 0, imirAxis = 0)
    assertContentEquals(intArrayOf(5, 6, 3, 4, 1, 2), out)
  }

  @Test
  fun mirrorLeftRight() {
    //   2 1
    //   4 3
    //   6 5
    val out = RgbaTransform.applyToPixels(src, w, h, irotAngle = 0, imirAxis = 1)
    assertContentEquals(intArrayOf(2, 1, 4, 3, 6, 5), out)
  }

  @Test
  fun rotateThenMirror_appliesRotationFirst() {
    // Per ISO/IEC 23008-12, irot is applied before imir. 90° CCW gives
    //   2 4 6
    //   1 3 5
    // then a top/bottom mirror gives
    //   1 3 5
    //   2 4 6
    val out = RgbaTransform.applyToPixels(src, w, h, irotAngle = 1, imirAxis = 0)
    assertContentEquals(intArrayOf(1, 3, 5, 2, 4, 6), out)
  }

  @Test
  fun byteBuffer_respectsRowStrideAndRotates() {
    // 2×2 RGBA pixels A(1,2,3,4) B(5,6,7,8) / C(9,10,11,12) D(13,14,15,16),
    // stored with a 12-byte row stride (4 bytes of padding per row).
    val rowBytes = 12
    val padded = ByteArray(rowBytes * 2)
    byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8).copyInto(padded, 0)
    byteArrayOf(9, 10, 11, 12, 13, 14, 15, 16).copyInto(padded, rowBytes)

    // 180° rotation: D C / B A, tightly packed.
    val out = RgbaTransform.apply(padded, 2, 2, rowBytes, irotAngle = 2, imirAxis = -1)
    assertContentEquals(
      byteArrayOf(13, 14, 15, 16, 9, 10, 11, 12, 5, 6, 7, 8, 1, 2, 3, 4),
      out.pixels,
    )
    assertTrue(out.width == 2 && out.height == 2)
  }

  @Test
  fun rotatedDimensionsSwap() {
    val out = RgbaTransform.applyToPixels(src, w, h, irotAngle = 1, imirAxis = -1)
    assertTrue(out.size == 6)
    assertTrue(RgbaTransform.outputWidth(w, h, 1) == 3)
    assertTrue(RgbaTransform.outputHeight(w, h, 1) == 2)
    assertTrue(RgbaTransform.outputWidth(w, h, 2) == 2)
    assertTrue(RgbaTransform.outputHeight(w, h, 2) == 3)
  }
}
