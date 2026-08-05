package com.alfikri.rizky.avifstudio.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import com.alfikri.rizky.avifkit.PlatformBitmap
import com.alfikri.rizky.avifstudio.model.OutputFormat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class ImageCodec {

  actual suspend fun decode(bytes: ByteArray, maxDimension: Int?): PlatformBitmap =
    withContext(Dispatchers.Default) {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        throw IllegalArgumentException("Not a readable image")
      }

      val options =
        BitmapFactory.Options().apply {
          // Sub-sampling during decode is the difference between allocating 8 MB and 200 MB for a
          // 50 MP photo — it has to happen here, not after the fact.
          inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
      val decoded =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
          ?: throw IllegalArgumentException("Could not decode this image")

      // AvifKit bakes EXIF orientation in on the AVIF path; the JPEG/PNG path is ours, so without
      // this every portrait photo shot on a phone comes out sideways.
      applyExifOrientation(bytes, decoded)
    }

  actual suspend fun readSize(bytes: ByteArray): PixelSize? =
    withContext(Dispatchers.Default) {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        PixelSize(bounds.outWidth, bounds.outHeight)
      } else {
        null
      }
    }

  actual suspend fun scale(bitmap: PlatformBitmap, maxDimension: Int?): PlatformBitmap =
    withContext(Dispatchers.Default) {
      if (maxDimension == null || maxDimension <= 0) return@withContext bitmap
      val longest = max(bitmap.width, bitmap.height)
      if (longest <= maxDimension) return@withContext bitmap
      val scale = maxDimension.toFloat() / longest
      val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
      val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
      Bitmap.createScaledBitmap(bitmap, width, height, /* filter= */ true)
    }

  actual suspend fun encode(bitmap: PlatformBitmap, format: OutputFormat, quality: Int): ByteArray =
    withContext(Dispatchers.Default) {
      val compressFormat =
        when (format) {
          OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
          OutputFormat.PNG -> Bitmap.CompressFormat.PNG
          OutputFormat.AVIF ->
            throw IllegalArgumentException(
              "AVIF encoding goes through AvifKit, not the platform codec"
            )
        }
      val output = ByteArrayOutputStream(bitmap.width * bitmap.height / 4)
      if (!bitmap.compress(compressFormat, quality.coerceIn(0, 100), output)) {
        throw IllegalStateException("The system encoder rejected this image")
      }
      output.toByteArray()
    }

  actual fun sizeOf(bitmap: PlatformBitmap): PixelSize = PixelSize(bitmap.width, bitmap.height)

  /** Largest power-of-two sub-sample that still leaves the longest edge above [maxDimension]. */
  private fun sampleSizeFor(width: Int, height: Int, maxDimension: Int?): Int {
    if (maxDimension == null || maxDimension <= 0) return 1
    var sample = 1
    var longest = max(width, height)
    while (longest / 2 >= maxDimension) {
      longest /= 2
      sample *= 2
    }
    return sample
  }

  private fun applyExifOrientation(bytes: ByteArray, bitmap: Bitmap): Bitmap {
    val orientation =
      try {
        ExifInterface(ByteArrayInputStream(bytes))
          .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
      } catch (_: Exception) {
        // No EXIF, or a format ExifInterface refuses to parse — the pixels are what they are.
        ExifInterface.ORIENTATION_NORMAL
      }

    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(270f)
        matrix.postScale(-1f, 1f)
      }
      else -> return bitmap
    }

    return try {
      val rotated =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, /* filter= */ true)
      if (rotated != bitmap) bitmap.recycle()
      rotated
    } catch (_: OutOfMemoryError) {
      // A sideways photo beats a crash.
      bitmap
    }
  }
}

actual fun PlatformBitmap.toImageBitmap(): ImageBitmap = asImageBitmap()
