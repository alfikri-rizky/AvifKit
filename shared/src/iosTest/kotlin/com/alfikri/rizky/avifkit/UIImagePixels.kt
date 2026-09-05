package com.alfikri.rizky.avifkit

import kotlinx.cinterop.*
import platform.CoreGraphics.*
import platform.UIKit.*

internal class RgbaPixels(val pixels: ByteArray, val width: Int, val height: Int) {

  /** RGBA of the pixel at ([x], [y]), each component 0..255. */
  fun at(x: Int, y: Int): IntArray {
    val offset = (y * width + x) * 4
    return IntArray(4) { pixels[offset + it].toInt() and 0xFF }
  }
}

/** Extract PREMULTIPLIED RGBA from a UIImage the same way the production encode path does. */
@OptIn(ExperimentalForeignApi::class)
internal fun uiImagePremultipliedRgba(image: UIImage): RgbaPixels {
  val cgImage = image.CGImage ?: error("UIImage has no CGImage")
  val w = CGImageGetWidth(cgImage).toInt()
  val h = CGImageGetHeight(cgImage).toInt()
  val out = ByteArray(w * h * 4)
  val colorSpace = CGColorSpaceCreateDeviceRGB()
  try {
    out.usePinned { pinned ->
      val ctx =
        CGBitmapContextCreate(
          data = pinned.addressOf(0),
          width = w.toULong(),
          height = h.toULong(),
          bitsPerComponent = 8u,
          bytesPerRow = (w * 4).toULong(),
          space = colorSpace,
          bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        )!!
      UIGraphicsPushContext(ctx)
      memScoped {
        val rect =
          alloc<CGRect>().apply {
            origin.x = 0.0
            origin.y = 0.0
            size.width = w.toDouble()
            size.height = h.toDouble()
          }
        CGContextTranslateCTM(ctx, 0.0, h.toDouble())
        CGContextScaleCTM(ctx, 1.0, -1.0)
        image.drawInRect(rect.readValue())
      }
      UIGraphicsPopContext()
    }
  } finally {
    CGColorSpaceRelease(colorSpace)
  }
  return RgbaPixels(out, w, h)
}
