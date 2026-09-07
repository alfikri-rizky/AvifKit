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
    exif: ByteArray?,
    xmp: ByteArray?,
  ): ByteArray?

  private external fun nativeDecode(avifData: ByteArray): DecodedImage?

  // Parse-only metadata (no pixel decode): [width, height, hasAlpha(0|1), depth, frameCount,
  // durationMs, repetitionCount], or null on failure. Used by getImageInfo so AVIF inspection
  // works below API 31 (where BitmapFactory can't decode AVIF) and reports alpha accurately.
  private external fun nativeGetAvifInfo(avifData: ByteArray): IntArray?

  private external fun nativeGetVersion(): String

  // Animated AVIF. The encoder is handle-based on purpose: frames are pushed one at a time so
  // only a single frame's RGBA is ever alive on either side of the JNI boundary.
  private external fun nativeAnimEncoderCreate(
    width: Int,
    height: Int,
    quality: Int,
    alphaQuality: Int,
    speed: Int,
    subsample: Int,
    lossless: Boolean,
    hasAlpha: Boolean,
    timescale: Int,
    repetitionCount: Int,
  ): Long

  private external fun nativeAnimEncoderAddFrame(
    handle: Long,
    pixels: ByteArray,
    durationInTimescales: Int,
  ): Boolean

  private external fun nativeAnimEncoderFinish(handle: Long): ByteArray?

  private external fun nativeAnimEncoderDestroy(handle: Long)

  private external fun nativeAnimDecoderCreate(avifData: ByteArray): Long

  private external fun nativeAnimDecoderNextFrame(handle: Long): DecodedImage?

  private external fun nativeAnimDecoderDestroy(handle: Long)

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
    withContext(Dispatchers.Default) {
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
    withContext(Dispatchers.Default) {
      val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

      // Handle maxSize if specified
      val avifData =
        if (encodingOptions.maxSize != null) {
          convertWithAdaptiveCompression(input, encodingOptions)
        } else {
          convertStandard(input, encodingOptions)
        }

      // Save to file (blocking I/O off the CPU pool).
      withContext(Dispatchers.IO) {
        File(outputPath).apply {
          parentFile?.mkdirs()
          writeBytes(avifData)
        }
      }

      outputPath
    }

  actual suspend fun convertToFile(
    input: ImageInput,
    output: PlatformFile,
    priority: Priority,
    options: EncodingOptions?,
  ): PlatformFile =
    withContext(Dispatchers.Default) {
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
    withContext(Dispatchers.Default) {
      val encodingOptions = options ?: EncodingOptions.fromPriority(priority)

      if (encodingOptions.maxSize != null) {
        convertWithAdaptiveCompression(input, encodingOptions)
      } else {
        convertStandard(input, encodingOptions)
      }
    }

  actual suspend fun decodeAvif(input: ImageInput): PlatformBitmap =
    withContext(Dispatchers.Default) {
      val data =
        when (input) {
          is ImageInput.FromBytes -> input.data
          is ImageInput.FromPath -> readFileOnIo(File(input.path))
          is ImageInput.FromFile -> input.file.readBytes()
          is ImageInput.FromBitmap -> throw AvifError.InvalidInput
        }

      decodeAvifToBitmap(data)
    }

  actual suspend fun decodeAvifFrames(
    input: ImageInput,
    maxDimension: Int?,
    maxFrames: Int,
  ): List<AvifFrame> =
    withContext(Dispatchers.Default) {
      require(maxFrames > 0) { "maxFrames must be positive" }
      val data =
        when (input) {
          is ImageInput.FromBytes -> input.data
          is ImageInput.FromPath -> readFileOnIo(File(input.path))
          is ImageInput.FromFile -> input.file.readBytes()
          is ImageInput.FromBitmap -> throw AvifError.InvalidInput
        }

      if (!nativeLibraryLoaded) {
        throw AvifError.DecodingFailed(
          "Native AVIF library not loaded. " +
            "Ensure the 'avif-android-wrapper' native library is included in the AAR."
        )
      }

      val handle = nativeAnimDecoderCreate(data)
      if (handle == 0L) throw AvifError.DecodingFailed("Failed to open AVIF for frame decoding")
      try {
        buildList {
            while (size < maxFrames) {
              val frame = nativeAnimDecoderNextFrame(handle) ?: break
              // Scale immediately: only one full-size frame is alive at a time, so a long animation
              // costs the scaled total plus one frame instead of the full-size total.
              add(
                AvifFrame(scaledBitmap(orientedBitmap(frame), maxDimension), frame.durationMillis)
              )
            }
          }
          .ifEmpty { throw AvifError.DecodingFailed("No frames decoded") }
      } catch (e: OutOfMemoryError) {
        Log.e(TAG, "OutOfMemoryError decoding AVIF frames", e)
        throw AvifError.OutOfMemory
      } finally {
        nativeAnimDecoderDestroy(handle)
      }
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
    withContext(Dispatchers.Default) {
      when (input) {
        is ImageInput.FromBytes -> imageInfoFromBytes(input.data, input.data.size.toLong())
        is ImageInput.FromPath -> {
          val file = File(input.path)
          if (!file.exists()) throw AvifError.FileError("File not found: ${input.path}")
          imageInfoFromBytes(readFileOnIo(file), file.length())
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
          frameCount = info.getOrElse(4) { 1 }.coerceAtLeast(1),
          durationMillis = info.getOrElse(5) { 0 }.toLong(),
          loopCount = AvifFormat.loopCountOf(info.getOrElse(6) { -1 }),
        )
      }
    }

    val format = ImageFormats.detect(data)
    if (format == ImageFormat.GIF) {
      // Structure walk only — no LZW, no pixels — so this stays cheap for a 100 MB GIF.
      GifDecoder.parse(data)?.let { gif ->
        return ImageInfo(
          width = gif.width,
          height = gif.height,
          format = ImageFormat.GIF,
          hasAlpha = gif.hasTransparency,
          fileSize = fileSize,
          frameCount = gif.frameCount,
          durationMillis = gif.durationMillis,
          loopCount = gif.loopCount,
        )
      }
    }

    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
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
    val bytes = readInputBytes(input)

    // Animation wins over the byte budget. Reaching a target by dropping 47 of 48 frames changes
    // what the image IS, and honouring it properly would re-encode the whole sequence once per
    // binary-search probe. Documented on EncodingOptions.encodeAnimation.
    if (bytes != null) {
      animatedGif(bytes, options)?.let { gif ->
        return encodeAnimatedGifToAvif(gif, options)
      }
    }

    // Already-AVIF input: return it untouched only when it fits the target. Otherwise decode it
    // (once) and re-encode — the passthrough would hand back the same oversized bytes forever.
    val source: Bitmap =
      if (bytes != null && AvifFormat.isAvif(bytes)) {
        if (bytes.size <= targetSize) return bytes
        decodeAvifToBitmap(bytes)
      } else if (bytes != null) {
        // Decode + orient the source ONCE; the loop only re-encodes it (M5 — previously each
        // attempt re-read and re-decoded the input).
        decodeBytesToOrientedBitmap(bytes)
      } else {
        decodeSourceToBitmap(input)
      }

    return AdaptiveCompression.compress(
      options = options,
      targetSize = targetSize,
      encode = { opts -> encodeBitmapToAvif(source, opts, bytes) },
      sizeOf = { it.size.toLong() },
    )
  }

  /** The input's raw bytes, or null when the input is already a decoded bitmap. */
  private suspend fun readInputBytes(input: ImageInput): ByteArray? =
    when (input) {
      is ImageInput.FromBytes -> input.data
      is ImageInput.FromPath -> File(input.path).takeIf { it.exists() }?.let { readFileOnIo(it) }
      is ImageInput.FromFile -> input.file.readBytes()
      is ImageInput.FromBitmap -> null
    }

  private suspend fun convertStandard(input: ImageInput, options: EncodingOptions): ByteArray =
    withContext(Dispatchers.Default) {
      when (input) {
        is ImageInput.FromBitmap -> encodeBitmapToAvif(input.bitmap, options)

        is ImageInput.FromBytes -> encodeBytes(input.data, options)

        is ImageInput.FromPath -> {
          val file = File(input.path)
          if (!file.exists()) throw AvifError.FileError("File not found: ${input.path}")
          // Sniff by content, not by ".avif" extension (M4): a mislabeled file must not pass
          // through unconverted, and a real AVIF with the wrong name must not go to BitmapFactory.
          encodeBytes(readFileOnIo(file), options)
        }

        is ImageInput.FromFile -> encodeBytes(input.file.readBytes(), options)
      }
    }

  private fun encodeBytes(data: ByteArray, options: EncodingOptions): ByteArray =
    when {
      AvifFormat.isAvif(data) -> data
      else ->
        animatedGif(data, options)?.let { encodeAnimatedGifToAvif(it, options) }
          ?: encodeBitmapToAvif(decodeBytesToOrientedBitmap(data), options, data)
    }

  /** The parsed GIF when [data] is a multi-frame GIF that should be encoded as an animation. */
  private fun animatedGif(data: ByteArray, options: EncodingOptions): GifDecoder? {
    if (!options.encodeAnimation) return null
    if (ImageFormats.detect(data) != ImageFormat.GIF) return null
    return GifDecoder.parse(data)?.takeIf { it.frameCount > 1 }
  }

  /**
   * Encodes a GIF's frames as an AVIF image sequence.
   *
   * Frames stream: [GifDecoder] composes one frame at a time onto a reused canvas and each is
   * handed straight to the native encoder, so peak memory is one frame's RGBA rather than the whole
   * animation (92 MB for a 48-frame 800x600 GIF).
   */
  private fun encodeAnimatedGifToAvif(gif: GifDecoder, options: EncodingOptions): ByteArray {
    if (!nativeLibraryLoaded) {
      throw AvifError.EncodingFailed(
        "Native AVIF library not loaded. " +
          "Ensure the 'avif-android-wrapper' native library is included in the AAR."
      )
    }
    val stream = GifFrameStream(gif, options.maxDimension)
    val subsampleValue =
      when (options.subsample) {
        ChromaSubsample.YUV444 -> 0
        ChromaSubsample.YUV422 -> 1
        ChromaSubsample.YUV420 -> 2
      }

    val handle =
      nativeAnimEncoderCreate(
        stream.width,
        stream.height,
        options.quality,
        options.alphaQuality,
        options.speed,
        subsampleValue,
        options.lossless,
        stream.hasAlpha,
        GifFrameStream.TIMESCALE,
        stream.repetitionCount,
      )
    if (handle == 0L) throw AvifError.EncodingFailed("Failed to start animated AVIF encoding")

    return try {
      var index = 0
      stream.forEachFrame { rgba, durationMillis ->
        if (!nativeAnimEncoderAddFrame(handle, rgba, durationMillis)) {
          throw AvifError.EncodingFailed("Failed to encode frame $index of ${stream.frameCount}")
        }
        index++
      }
      nativeAnimEncoderFinish(handle)
        ?: throw AvifError.EncodingFailed("Failed to finalize animated AVIF")
    } catch (e: OutOfMemoryError) {
      Log.e(TAG, "OutOfMemoryError during animated AVIF encoding", e)
      throw AvifError.OutOfMemory
    } finally {
      nativeAnimEncoderDestroy(handle)
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
        decodeBytesToOrientedBitmap(readFileOnIo(file))
      }
    }

  /** Read a file's bytes on the IO dispatcher (encode/decode run on Default — L2). */
  private suspend fun readFileOnIo(file: File): ByteArray =
    withContext(Dispatchers.IO) { file.readBytes() }

  private fun decodeBytesToOrientedBitmap(data: ByteArray): Bitmap {
    val bitmap =
      BitmapFactory.decodeByteArray(data, 0, data.size)
        ?: throw AvifError.DecodingFailed("Failed to decode input image")
    return applyExifOrientation(bitmap, data)
  }

  /**
   * [sourceBytes] are the original file's bytes, needed only for [EncodingOptions.preserveMetadata]
   * — null when the caller started from a decoded Bitmap, which has no file (and so no metadata)
   * behind it.
   */
  private fun encodeBitmapToAvif(
    bitmap: Bitmap,
    options: EncodingOptions,
    sourceBytes: ByteArray? = null,
  ): ByteArray {
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

      // Read AFTER the resize: the Exif pixel-dimension tags have to describe the encoded image,
      // not the file it came from.
      val metadata =
        if (options.preserveMetadata) {
          EncodedMetadata.forSource(sourceBytes, resizedBitmap.width, resizedBitmap.height)
        } else null

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
        metadata?.exif,
        metadata?.xmp,
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

  /**
   * Builds a Bitmap from a decoded frame, applying the AVIF irot/imir transform libavif reports but
   * does not itself apply.
   */
  private fun orientedBitmap(decoded: DecodedImage): Bitmap {
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
  }

  /** Downscales [bitmap] to fit [maxDimension], recycling the original when it is replaced. */
  private fun scaledBitmap(bitmap: Bitmap, maxDimension: Int?): Bitmap {
    if (maxDimension == null) return bitmap
    val scaled = resizeBitmap(bitmap, maxDimension)
    if (scaled != bitmap) bitmap.recycle()
    return scaled
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

      return orientedBitmap(decoded)
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
