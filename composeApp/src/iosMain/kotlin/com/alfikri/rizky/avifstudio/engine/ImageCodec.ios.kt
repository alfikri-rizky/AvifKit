@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.alfikri.rizky.avifstudio.engine

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.alfikri.rizky.avifkit.PlatformBitmap
import com.alfikri.rizky.avifstudio.model.OutputFormat
import kotlin.math.max
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImageOrientation
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

actual class ImageCodec {

  actual suspend fun decode(bytes: ByteArray, maxDimension: Int?): PlatformBitmap =
    withContext(Dispatchers.Default) {
      val image =
        UIImage(data = bytes.toNSData())
          ?: throw IllegalArgumentException("Could not decode this image")
      // UIImage decodes lazily at full size; redrawing at the target size here is what actually
      // caps memory, and it bakes in the EXIF orientation at the same time.
      scale(image, maxDimension)
    }

  actual suspend fun readSize(bytes: ByteArray): PixelSize? =
    withContext(Dispatchers.Default) {
      val image = UIImage(data = bytes.toNSData()) ?: return@withContext null
      pixelSize(image)
    }

  actual suspend fun scale(bitmap: PlatformBitmap, maxDimension: Int?): PlatformBitmap =
    withContext(Dispatchers.Default) {
      val current = pixelSize(bitmap)
      if (current.width <= 0 || current.height <= 0) return@withContext bitmap

      // Redrawing is also what bakes the EXIF orientation into the pixels. A rotated photo that
      // happens to need no downscaling must still go through here, or `CGImage` (which is NOT
      // rotated) and `UIImage.size` (which IS) disagree, and the preview renders sideways.
      val needsScale =
        maxDimension != null &&
          maxDimension > 0 &&
          max(current.width, current.height) > maxDimension
      val needsRotation = bitmap.imageOrientation != UIImageOrientation.UIImageOrientationUp
      if (!needsScale && !needsRotation) return@withContext bitmap

      val factor =
        if (needsScale) maxDimension!!.toDouble() / max(current.width, current.height) else 1.0
      val width = (current.width * factor).toInt().coerceAtLeast(1)
      val height = (current.height * factor).toInt().coerceAtLeast(1)

      // scale = 1.0 so the context is sized in pixels, not points — otherwise a 3x Retina screen
      // silently produces an image three times the requested dimension.
      UIGraphicsBeginImageContextWithOptions(
        size = CGSizeMake(width.toDouble(), height.toDouble()),
        opaque = false,
        scale = 1.0,
      )
      bitmap.drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
      val scaled = UIGraphicsGetImageFromCurrentImageContext()
      UIGraphicsEndImageContext()
      scaled ?: bitmap
    }

  actual suspend fun encode(bitmap: PlatformBitmap, format: OutputFormat, quality: Int): ByteArray =
    withContext(Dispatchers.Default) {
      val data: NSData? =
        when (format) {
          // UIKit takes 0.0..1.0 where Android and our UI use 0..100.
          OutputFormat.JPEG -> UIImageJPEGRepresentation(bitmap, quality.coerceIn(0, 100) / 100.0)
          OutputFormat.PNG -> UIImagePNGRepresentation(bitmap)
          OutputFormat.AVIF ->
            throw IllegalArgumentException(
              "AVIF encoding goes through AvifKit, not the platform codec"
            )
        }
      (data ?: throw IllegalStateException("The system encoder rejected this image")).toByteArray()
    }

  actual fun sizeOf(bitmap: PlatformBitmap): PixelSize = pixelSize(bitmap)

  /**
   * Real pixel dimensions. `UIImage.size` is in points and already has the orientation applied, so
   * it is multiplied back by `scale` rather than read off the CGImage, which is not rotated.
   */
  private fun pixelSize(image: UIImage): PixelSize {
    val scale = image.scale
    return image.size.useContents { PixelSize((width * scale).toInt(), (height * scale).toInt()) }
  }
}

actual fun PlatformBitmap.toImageBitmap(): ImageBitmap {
  val cgImage = this.CGImage ?: throw IllegalStateException("This image has no drawable content")

  val width = CGImageGetWidth(cgImage).toInt()
  val height = CGImageGetHeight(cgImage).toInt()
  val bytesPerRow = width * 4
  val totalBytes = height * bytesPerRow

  val context =
    CGBitmapContextCreate(
      data = null,
      width = width.toULong(),
      height = height.toULong(),
      bitsPerComponent = 8u,
      bytesPerRow = bytesPerRow.toULong(),
      space = CGColorSpaceCreateDeviceRGB(),
      bitmapInfo =
        kCGBitmapByteOrder32Little or CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst.value,
    ) ?: throw IllegalStateException("Could not allocate a drawing context for this image")

  CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
  val data =
    CGBitmapContextGetData(context)
      ?: throw IllegalStateException("Could not read the drawn pixels")

  val pixels = ByteArray(totalBytes)
  pixels.usePinned { pinned -> memcpy(pinned.addressOf(0), data, totalBytes.toULong()) }

  val info =
    ImageInfo(
      width = width,
      height = height,
      colorType = ColorType.BGRA_8888,
      alphaType = ColorAlphaType.PREMUL,
    )
  return Image.makeRaster(info, pixels, bytesPerRow).toComposeImageBitmap()
}

internal fun ByteArray.toNSData(): NSData {
  if (isEmpty()) return NSData()
  return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}

internal fun NSData.toByteArray(): ByteArray {
  val size = length.toInt()
  if (size == 0) return ByteArray(0)
  val result = ByteArray(size)
  // Copy exactly what was allocated. `length` is NSUInteger and `size` is the Int the array was
  // sized from; handing memcpy the former would let the copy outrun the buffer if they ever
  // disagreed.
  result.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, size.toULong()) }
  return result
}
