package com.alfikri.rizky.avifkit

// Import FileKit extension functions
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.write
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class AvifConverter {

  // Native methods - implemented in C++ via JNI
  // These work with or without libavif (conditional compilation)
  private external fun nativeEncode(
    pixels: ByteArray,
    width: Int,
    height: Int,
    quality: Int,
    alphaQuality: Int,
    speed: Int,
    subsample: Int,
    lossless: Boolean,
    hasAlpha: Boolean,
  ): ByteArray?

  private external fun nativeDecode(avifData: ByteArray): DecodedImage?

  // Parse-only metadata (no pixel decode): [width, height, hasAlpha(0|1), depth], or null on
  // failure. Used by getImageInfo so AVIF inspection works below API 31 (where BitmapFactory
  // can't decode AVIF) and reports alpha accurately.
  private external fun nativeGetAvifInfo(avifData: ByteArray): IntArray?

  private external fun nativeGetVersion(): String

  companion object {
    private const val TAG = "AvifConverter"
    private var nativeLibraryLoaded = false

    init {
      try {
        System.loadLibrary("avif-android-wrapper")
        nativeLibraryLoaded = true
        Log.i(TAG, "Native library loaded successfully")
      } catch (e: UnsatisfiedLinkError) {
        Log.w(TAG, "Native library not available. Some features may be limited.", e)
        nativeLibraryLoaded = false
      }
    }

    fun isNativeLibraryLoaded(): Boolean = nativeLibraryLoaded

    // True only when the .so is loaded AND was compiled against libavif (a wrapper
    // built in placeholder mode reports a non-libavif version string).
    private val avifCodecAvailable: Boolean by lazy {
      if (!nativeLibraryLoaded) {
        false
      } else {
        try {
          AvifConverter().nativeGetVersion().startsWith("libavif")
        } catch (e: Throwable) {
          Log.w(TAG, "Failed to query native library version", e)
          false
        }
      }
    }
  }

  actual suspend fun convertToBitmap(
    input: ImageInput,
    priority: Priority,
    options: EncodingOptions?,
  ): PlatformBitmap =
    withContext(Dispatchers.IO) {
      val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

      // Handle maxSize if specified
      val avifData =
        if (encodingOptions.maxSize != null) {
          convertWithAdaptiveCompression(input, encodingOptions)
        } else {
          convertStandard(input, encodingOptions)
        }

      // Decode AVIF to Bitmap
      decodeAvifToBitmap(avifData)
    }

  actual suspend fun convertToFile(
    input: ImageInput,
    outputPath: String,
    priority: Priority,
    options: EncodingOptions?,
  ): String =
    withContext(Dispatchers.IO) {
      val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

      // Handle maxSize if specified
      val avifData =
        if (encodingOptions.maxSize != null) {
          convertWithAdaptiveCompression(input, encodingOptions)
        } else {
          convertStandard(input, encodingOptions)
        }

      // Save to file
      File(outputPath).apply {
        parentFile?.mkdirs()
        writeBytes(avifData)
      }

      outputPath
    }

  actual suspend fun convertToFile(
    input: ImageInput,
    output: PlatformFile,
    priority: Priority,
    options: EncodingOptions?,
  ): PlatformFile =
    withContext(Dispatchers.IO) {
      val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

      // Handle maxSize if specified
      val avifData =
        if (encodingOptions.maxSize != null) {
          convertWithAdaptiveCompression(input, encodingOptions)
        } else {
          convertStandard(input, encodingOptions)
        }

      // Save to PlatformFile
      output.write(avifData)
      output
    }

  actual suspend fun encodeAvif(
    input: ImageInput,
    priority: Priority,
    options: EncodingOptions?,
  ): ByteArray =
    withContext(Dispatchers.IO) {
      val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

      if (encodingOptions.maxSize != null) {
        convertWithAdaptiveCompression(input, encodingOptions)
      } else {
        convertStandard(input, encodingOptions)
      }
    }

  actual suspend fun decodeAvif(input: ImageInput): PlatformBitmap =
    withContext(Dispatchers.IO) {
      val data =
        when (input) {
          is ImageInput.FromBytes -> input.data
          is ImageInput.FromPath -> File(input.path).readBytes()
          is ImageInput.FromFile -> input.file.readBytes()
          is ImageInput.FromBitmap -> throw AvifError.InvalidInput
        }

      decodeAvifToBitmap(data)
    }

  actual fun isAvifSupported(): Boolean {
    // Honest feature detection: false when the native library failed to load (or was
    // built without libavif), so callers can branch instead of hitting AvifError later.
    return avifCodecAvailable
  }

  actual fun isAvifFile(input: ImageInput): Boolean {
    return try {
      val data =
        when (input) {
          is ImageInput.FromBytes -> input.data
          is ImageInput.FromPath ->
            File(input.path).readBytes().take(AvifFormat.HEADER_CHECK_SIZE).toByteArray()
          is ImageInput.FromFile ->
            kotlinx.coroutines.runBlocking {
              input.file.readBytes().take(AvifFormat.HEADER_CHECK_SIZE).toByteArray()
            }

          is ImageInput.FromBitmap -> return false
        }
      AvifFormat.isAvif(data)
    } catch (e: Exception) {
      false
    }
  }

  actual suspend fun getImageInfo(input: ImageInput): ImageInfo =
    withContext(Dispatchers.IO) {
      when (input) {
        is ImageInput.FromBytes -> imageInfoFromBytes(input.data, input.data.size.toLong())
        is ImageInput.FromPath -> {
          val file = File(input.path)
          if (!file.exists()) throw AvifError.FileError("File not found: ${input.path}")
          imageInfoFromBytes(file.readBytes(), file.length())
        }
        is ImageInput.FromFile -> {
          val data = input.file.readBytes()
          imageInfoFromBytes(data, input.file.size())
        }
        is ImageInput.FromBitmap -> {
          ImageInfo(
            width = input.bitmap.width,
            height = input.bitmap.height,
            format = ImageFormat.UNKNOWN,
            hasAlpha = input.bitmap.hasAlpha(),
          )
        }
      }
    }

  private fun imageInfoFromBytes(data: ByteArray, fileSize: Long): ImageInfo {
    // AVIF: inspect via libavif (parse only, no pixel decode). This is exact for alpha and works
    // below API 31, where BitmapFactory can't decode AVIF and would return -1 dimensions.
    if (AvifFormat.isAvif(data)) {
      nativeGetAvifInfo(data)?.let { info ->
        return ImageInfo(
          width = info[0],
          height = info[1],
          format = ImageFormat.AVIF,
          hasAlpha = info[2] == 1,
          fileSize = fileSize,
        )
      }
    }

    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
    val format = detectFormat(data)
    return ImageInfo(
      width = options.outWidth,
      height = options.outHeight,
      format = format,
      // Without a full decode we can't know per-pixel alpha, so report the format's capability.
      // (The old outConfig==ARGB_8888 test was BitmapFactory's default config and reported alpha
      // for every JPEG.) AVIF above reports exact alpha via libavif.
      hasAlpha = formatSupportsAlpha(format),
      fileSize = fileSize,
    )
  }

  /**
   * Whether the container format can carry an alpha channel (best-effort without a full decode).
   */
  private fun formatSupportsAlpha(format: ImageFormat): Boolean =
    when (format) {
      ImageFormat.PNG,
      ImageFormat.WEBP,
      ImageFormat.GIF,
      ImageFormat.HEIF,
      ImageFormat.AVIF -> true
      ImageFormat.JPEG,
      ImageFormat.BMP,
      ImageFormat.UNKNOWN -> false
    }

  // Private helper methods

  private suspend fun convertWithAdaptiveCompression(
    input: ImageInput,
    options: EncodingOptions,
  ): ByteArray {
    val targetSize = options.maxSize!!

    // Already-AVIF input: return it untouched only when it fits the target. Otherwise decode it
    // (once) and re-encode — the passthrough would hand back the same oversized bytes forever.
    val avifBytes = readInputAvifBytes(input)
    val source: Bitmap =
      if (avifBytes != null) {
        if (avifBytes.size <= targetSize) return avifBytes
        decodeAvifToBitmap(avifBytes)
      } else {
        // Decode + orient the source ONCE; the loop only re-encodes it (M5 — previously each
        // attempt re-read and re-decoded the input).
        decodeSourceToBitmap(input)
      }

    return AdaptiveCompression.compress(
      options = options,
      targetSize = targetSize,
      encode = { opts -> encodeBitmapToAvif(source, opts) },
      sizeOf = { it.size.toLong() },
    )
  }

  /** The input's raw bytes when they already contain AVIF data, else null. */
  private suspend fun readInputAvifBytes(input: ImageInput): ByteArray? {
    val bytes =
      when (input) {
        is ImageInput.FromBytes -> input.data
        is ImageInput.FromPath ->
          File(input.path).takeIf { it.exists() }?.readBytes() ?: return null
        is ImageInput.FromFile -> input.file.readBytes()
        is ImageInput.FromBitmap -> return null
      }
    return if (AvifFormat.isAvif(bytes)) bytes else null
  }

  private suspend fun convertStandard(input: ImageInput, options: EncodingOptions): ByteArray =
    withContext(Dispatchers.IO) {
      when (input) {
        is ImageInput.FromBytes ->
          if (AvifFormat.isAvif(input.data)) input.data
          else encodeBitmapToAvif(decodeBytesToOrientedBitmap(input.data), options)

        is ImageInput.FromBitmap -> encodeBitmapToAvif(input.bitmap, options)

        is ImageInput.FromPath -> {
          val file = File(input.path)
          if (!file.exists()) throw AvifError.FileError("File not found: ${input.path}")
          // Sniff by content, not by ".avif" extension (M4): a mislabeled file must not pass
          // through unconverted, and a real AVIF with the wrong name must not go to BitmapFactory.
          val data = file.readBytes()
          if (AvifFormat.isAvif(data)) data
          else encodeBitmapToAvif(decodeBytesToOrientedBitmap(data), options)
        }

        is ImageInput.FromFile -> {
          val data = input.file.readBytes()
          if (AvifFormat.isAvif(data)) data
          else encodeBitmapToAvif(decodeBytesToOrientedBitmap(data), options)
        }
      }
    }

  /** Decode any non-AVIF input to an EXIF-oriented bitmap. Reads the source exactly once. */
  private suspend fun decodeSourceToBitmap(input: ImageInput): Bitmap =
    when (input) {
      is ImageInput.FromBitmap -> input.bitmap
      is ImageInput.FromBytes -> decodeBytesToOrientedBitmap(input.data)
      is ImageInput.FromFile -> decodeBytesToOrientedBitmap(input.file.readBytes())
      is ImageInput.FromPath -> {
        val file = File(input.path)
        if (!file.exists()) throw AvifError.FileError("File not found: ${input.path}")
        decodeBytesToOrientedBitmap(file.readBytes())
      }
    }

  private fun decodeBytesToOrientedBitmap(data: ByteArray): Bitmap {
    val bitmap =
      BitmapFactory.decodeByteArray(data, 0, data.size)
        ?: throw AvifError.DecodingFailed("Failed to decode input image")
    return applyExifOrientation(bitmap, data)
  }

  private fun encodeBitmapToAvif(bitmap: Bitmap, options: EncodingOptions): ByteArray {
    try {
      // HARDWARE bitmaps (Coil/Glide default on API 26+) have no accessible pixels; copy to a
      // readable config first, else getPixels() throws (M1). Must happen before resize/getPixels.
      val readable = ensureReadableBitmap(bitmap)

      // Resize if needed
      val resizedBitmap =
        options.maxDimension?.let { maxDim -> resizeBitmap(readable, maxDim) } ?: readable

      if (!nativeLibraryLoaded) {
        throw AvifError.EncodingFailed(
          "Native AVIF library not loaded. " +
            "Ensure the 'avif-android-wrapper' native library is included in the AAR."
        )
      }

      // Convert bitmap to byte array
      val pixels = bitmapToByteArray(resizedBitmap)

      // Get subsample value (0=444, 1=422, 2=420)
      val subsampleValue =
        when (options.subsample) {
          ChromaSubsample.YUV444 -> 0
          ChromaSubsample.YUV422 -> 1
          ChromaSubsample.YUV420 -> 2
        }

      // Skip the alpha plane for opaque sources (M6): smaller output, and no phantom alpha on
      // decode. A JPEG-backed bitmap reports hasAlpha()==false.
      val hasAlpha = resizedBitmap.hasAlpha()

      // Encode using native method (works with or without libavif)
      return nativeEncode(
        pixels,
        resizedBitmap.width,
        resizedBitmap.height,
        options.quality,
        options.alphaQuality,
        options.speed,
        subsampleValue,
        options.lossless,
        hasAlpha,
      ) ?: throw AvifError.EncodingFailed("Native encoding failed")
    } catch (e: OutOfMemoryError) {
      Log.e(TAG, "OutOfMemoryError during AVIF encoding", e)
      throw AvifError.OutOfMemory
    } catch (e: AvifError) {
      // Re-throw AvifError as-is
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Unexpected error during encoding", e)
      throw AvifError.EncodingFailed("Encoding failed: ${e.message}")
    }
  }

  private fun decodeAvifToBitmap(avifData: ByteArray): Bitmap {
    try {
      if (!nativeLibraryLoaded) {
        throw AvifError.DecodingFailed(
          "Native AVIF library not loaded. " +
            "Ensure the 'avif-android-wrapper' native library is included in the AAR."
        )
      }

      // Decode using native method (works with or without libavif)
      val decoded =
        try {
          Log.d(TAG, "Calling nativeDecode with ${avifData.size} bytes")
          val result = nativeDecode(avifData)
          if (result == null) {
            Log.e(TAG, "nativeDecode returned null")
            throw AvifError.DecodingFailed("Native decoding returned null")
          }
          Log.d(TAG, "nativeDecode succeeded: ${result.width}x${result.height}")
          result
        } catch (e: OutOfMemoryError) {
          Log.e(TAG, "OutOfMemoryError during native decode", e)
          throw AvifError.OutOfMemory
        } catch (e: AvifError) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Exception during native decode", e)
          throw AvifError.DecodingFailed("Native decoding failed: ${e.message}")
        }

      // Apply AVIF irot/imir orientation (libavif reports but does not apply it).
      val oriented =
        if (RgbaTransform.isIdentity(decoded.irotAngle, decoded.imirAxis)) {
          decoded
        } else {
          DecodedImage(
            pixels =
              RgbaTransform.applyToPixels(
                decoded.pixels,
                decoded.width,
                decoded.height,
                decoded.irotAngle,
                decoded.imirAxis,
              ),
            width = RgbaTransform.outputWidth(decoded.width, decoded.height, decoded.irotAngle),
            height = RgbaTransform.outputHeight(decoded.width, decoded.height, decoded.irotAngle),
          )
        }

      return Bitmap.createBitmap(
        oriented.pixels,
        oriented.width,
        oriented.height,
        Bitmap.Config.ARGB_8888,
      )
    } catch (e: OutOfMemoryError) {
      Log.e(TAG, "OutOfMemoryError during AVIF decoding", e)
      throw AvifError.OutOfMemory
    } catch (e: AvifError) {
      // Re-throw AvifError as-is
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Unexpected error during decoding", e)
      throw AvifError.DecodingFailed("Decoding failed: ${e.message}")
    }
  }

  /** Get the version of the native library Useful for debugging whether libavif is integrated */
  fun getLibraryVersion(): String {
    return if (nativeLibraryLoaded) {
      try {
        nativeGetVersion()
      } catch (e: Exception) {
        "Unknown (error getting version)"
      }
    } else {
      "Native library not loaded"
    }
  }

  /** Ensure a bitmap's pixels are readable via getPixels() (HARDWARE bitmaps are not). */
  private fun ensureReadableBitmap(bitmap: Bitmap): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
      return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        ?: throw AvifError.EncodingFailed("Failed to convert hardware bitmap to a readable format")
    }
    return bitmap
  }

  private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    // Convert to byte array (RGBA format)
    return ByteArray(pixels.size * 4).apply {
      pixels.forEachIndexed { i, pixel ->
        this[i * 4] = (pixel shr 16 and 0xFF).toByte() // R
        this[i * 4 + 1] = (pixel shr 8 and 0xFF).toByte() // G
        this[i * 4 + 2] = (pixel and 0xFF).toByte() // B
        this[i * 4 + 3] = (pixel shr 24 and 0xFF).toByte() // A
      }
    }
  }

  private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height

    if (width <= maxDimension && height <= maxDimension) {
      return bitmap
    }

    val scale = maxDimension.toFloat() / maxOf(width, height)
    // Clamp to >= 1: an extreme aspect ratio (e.g. a 20000x1 line) would otherwise round the short
    // side to 0 and crash createScaledBitmap (M8).
    val newWidth = (width * scale).toInt().coerceAtLeast(1)
    val newHeight = (height * scale).toInt().coerceAtLeast(1)

    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
  }

  private fun detectFormat(data: ByteArray): ImageFormat {
    if (data.size < 12) return ImageFormat.UNKNOWN

    return when {
      AvifFormat.isAvif(data) -> ImageFormat.AVIF
      data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() -> ImageFormat.JPEG
      data[0] == 0x89.toByte() && data[1] == 0x50.toByte() -> ImageFormat.PNG
      data[8] == 0x57.toByte() && data[9] == 0x45.toByte() -> ImageFormat.WEBP
      else -> ImageFormat.UNKNOWN
    }
  }

  private fun detectFormatFromPath(path: String): ImageFormat {
    return when (File(path).extension.lowercase()) {
      "avif" -> ImageFormat.AVIF
      "jpg",
      "jpeg" -> ImageFormat.JPEG
      "png" -> ImageFormat.PNG
      "webp" -> ImageFormat.WEBP
      "bmp" -> ImageFormat.BMP
      "gif" -> ImageFormat.GIF
      "heif",
      "heic" -> ImageFormat.HEIF
      else -> ImageFormat.UNKNOWN
    }
  }

  /**
   * Apply EXIF orientation transformation to bitmap from byte array This ensures portrait photos
   * display correctly after conversion
   */
  private fun applyExifOrientation(bitmap: Bitmap, imageData: ByteArray): Bitmap {
    return try {
      val exif = ExifInterface(ByteArrayInputStream(imageData))
      val orientation =
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
      rotateImageIfRequired(bitmap, orientation)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to read EXIF orientation from byte array", e)
      bitmap // Return original bitmap if EXIF reading fails
    }
  }

  /**
   * Rotate bitmap based on EXIF orientation value This "bakes in" the orientation so the output
   * image displays correctly
   */
  private fun rotateImageIfRequired(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()

    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> {
        matrix.postRotate(90f)
      }

      ExifInterface.ORIENTATION_ROTATE_180 -> {
        matrix.postRotate(180f)
      }

      ExifInterface.ORIENTATION_ROTATE_270 -> {
        matrix.postRotate(270f)
      }

      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
        matrix.postScale(-1f, 1f)
      }

      ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
        matrix.postScale(1f, -1f)
      }

      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.postScale(-1f, 1f)
      }

      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(270f)
        matrix.postScale(-1f, 1f)
      }

      ExifInterface.ORIENTATION_NORMAL,
      ExifInterface.ORIENTATION_UNDEFINED -> {
        // No rotation needed
        return bitmap
      }
    }

    return try {
      val rotatedBitmap =
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
      // Recycle original bitmap if a new one was created
      if (rotatedBitmap != bitmap) {
        bitmap.recycle()
      }
      rotatedBitmap
    } catch (e: Exception) {
      Log.e(TAG, "Failed to rotate bitmap", e)
      bitmap
    }
  }
}
