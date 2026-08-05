package com.alfikri.rizky.avifstudio.engine

import androidx.compose.ui.graphics.ImageBitmap
import com.alfikri.rizky.avifkit.PlatformBitmap
import com.alfikri.rizky.avifstudio.model.OutputFormat

/** Pixel dimensions of a decoded image. */
data class PixelSize(val width: Int, val height: Int)

/**
 * The non-AVIF half of the codec work: decoding JPEG/PNG/WebP/HEIF input and encoding JPEG/PNG
 * output. AvifKit owns everything AVIF; this owns everything else, using each platform's own
 * imaging stack (`BitmapFactory`/`Bitmap.compress` on Android, `UIImage` on iOS).
 */
expect class ImageCodec() {

  /**
   * Decodes [bytes] into a platform bitmap, downscaling during decode when [maxDimension] is set so
   * a 50 MP photo never has to exist in memory at full size.
   */
  suspend fun decode(bytes: ByteArray, maxDimension: Int? = null): PlatformBitmap

  /** Dimensions from the header alone — no full decode, no large allocation. */
  suspend fun readSize(bytes: ByteArray): PixelSize?

  /**
   * Downscales so the longest edge is at most [maxDimension]. Returns the input when it already
   * fits.
   */
  suspend fun scale(bitmap: PlatformBitmap, maxDimension: Int?): PlatformBitmap

  /** Encodes to [format]. [quality] is 0..100 and ignored by PNG. Throws if the format is AVIF. */
  suspend fun encode(bitmap: PlatformBitmap, format: OutputFormat, quality: Int): ByteArray

  fun sizeOf(bitmap: PlatformBitmap): PixelSize
}

/**
 * Bridges AvifKit's platform bitmap into something Compose can draw. Named `toImageBitmap` rather
 * than `toComposeImageBitmap` so it cannot collide with Skia's own extension of that name, which is
 * already in scope in iosMain.
 */
expect fun PlatformBitmap.toImageBitmap(): ImageBitmap
