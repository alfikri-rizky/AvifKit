package com.alfikri.rizky.avifkit

// Import FileKit extension functions
import io.github.vinceglb.filekit.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libavif.*
import platform.CoreGraphics.*
import platform.Foundation.*
import platform.UIKit.*
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class AvifConverter {

  // Codec thread count from the actual CPU count (was hardcoded 4), capped to avoid
  // over-threading small images where coordination outweighs the gain.
  private val codecThreads: Int by lazy {
    NSProcessInfo.processInfo.activeProcessorCount.toInt().coerceIn(1, 8)
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

      // Decode AVIF to UIImage
      decodeAvifToImage(avifData)
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

      // Save to file
      val outputUrl = NSURL.fileURLWithPath(outputPath)
      val directory = outputUrl.URLByDeletingLastPathComponent
      directory?.let { NSFileManager.defaultManager.createDirectoryAtURL(it, true, null, null) }

      val success = avifData.writeToURL(outputUrl, true)

      if (!success) {
        throw AvifError.EncodingFailed("Failed to save file to $outputPath")
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
      output.write(avifData.toByteArray())
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
        convertWithAdaptiveCompression(input, encodingOptions).toByteArray()
      } else {
        convertStandard(input, encodingOptions).toByteArray()
      }
    }

  actual suspend fun decodeAvif(input: ImageInput): PlatformBitmap =
    withContext(Dispatchers.Default) {
      val data =
        when (input) {
          is ImageInput.FromBytes -> input.data.toNSData()
          is ImageInput.FromPath -> {
            val url = NSURL.fileURLWithPath(input.path)
            NSData.dataWithContentsOfURL(url)
              ?: throw AvifError.FileError("File not found: ${input.path}")
          }

          is ImageInput.FromFile -> input.file.readBytes().toNSData()
          is ImageInput.FromBitmap -> throw AvifError.InvalidInput
        }

      decodeAvifToImage(data)
    }

  actual fun isAvifSupported(): Boolean {
    // libavif (+ aom) is statically linked into the framework via cinterop, so
    // the codec is always present — no runtime handler discovery needed.
    return true
  }

  actual fun isAvifFile(input: ImageInput): Boolean {
    return try {
      val data =
        when (input) {
          is ImageInput.FromBytes -> input.data
          is ImageInput.FromPath -> {
            val url = NSURL.fileURLWithPath(input.path)
            val nsData = NSData.dataWithContentsOfURL(url)
            nsData?.toByteArray()?.take(AvifFormat.HEADER_CHECK_SIZE)?.toByteArray() ?: return false
          }

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
        is ImageInput.FromBytes ->
          imageInfoFromNSData(input.data.toNSData(), input.data.size.toLong())

        is ImageInput.FromPath -> {
          val url = NSURL.fileURLWithPath(input.path)
          val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(input.path, null)
          val fileSize = (attributes?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
          val nsData =
            NSData.dataWithContentsOfURL(url)
              ?: throw AvifError.FileError("Failed to read file: ${input.path}")
          imageInfoFromNSData(nsData, fileSize)
        }

        is ImageInput.FromFile ->
          imageInfoFromNSData(input.file.readBytes().toNSData(), input.file.size())

        is ImageInput.FromBitmap -> {
          val image = input.bitmap
          val width = image.size.useContents { this.width }
          val height = image.size.useContents { this.height }

          ImageInfo(
            width = (width * image.scale).toInt(),
            height = (height * image.scale).toInt(),
            format = ImageFormat.UNKNOWN,
            hasAlpha = imageHasAlpha(image),
          )
        }
      }
    }

  private fun imageInfoFromNSData(nsData: NSData, fileSize: Long?): ImageInfo {
    val header = nsData.headerBytes()
    // AVIF: inspect via libavif (parse only). UIImage.imageWithData can't decode AVIF on iOS 15
    // (UIKit AVIF support is iOS 16+), so the old path threw for files the library can decode.
    if (AvifFormat.isAvif(header)) {
      avifInfoFromData(nsData, fileSize)?.let {
        return it
      }
    }
    val image = UIImage.imageWithData(nsData) ?: throw AvifError.InvalidInput
    val width = image.size.useContents { this.width }
    val height = image.size.useContents { this.height }
    return ImageInfo(
      width = (width * image.scale).toInt(),
      height = (height * image.scale).toInt(),
      format = ImageFormats.detect(header),
      hasAlpha = imageHasAlpha(image),
      fileSize = fileSize,
    )
  }

  /** Parse-only AVIF metadata via libavif (width/height/alpha), or null if it isn't parseable. */
  private fun avifInfoFromData(data: NSData, fileSize: Long?): ImageInfo? = memScoped {
    val decoder = avifDecoderCreate() ?: return@memScoped null
    try {
      val bytes = data.bytes?.reinterpret<UByteVar>()
      if (avifDecoderSetIOMemory(decoder, bytes, data.length) != AVIF_RESULT_OK) {
        return@memScoped null
      }
      if (avifDecoderParse(decoder) != AVIF_RESULT_OK) return@memScoped null
      val image = decoder.pointed.image ?: return@memScoped null
      ImageInfo(
        width = image.pointed.width.toInt(),
        height = image.pointed.height.toInt(),
        format = ImageFormat.AVIF,
        // alphaPresent is valid after parse (image->alphaPlane isn't allocated until decode).
        hasAlpha = decoder.pointed.alphaPresent != AVIF_FALSE,
        fileSize = fileSize,
      )
    } finally {
      avifDecoderDestroy(decoder)
    }
  }

  // Private helper methods

  private suspend fun convertWithAdaptiveCompression(
    input: ImageInput,
    options: EncodingOptions,
  ): NSData {
    val targetSize = options.maxSize!!

    // Already-AVIF input: return it untouched only when it fits the target. Otherwise decode it
    // (once) and re-encode — the passthrough would hand back the same oversized bytes forever.
    val avifBytes = readInputAvifBytes(input)
    val source: UIImage =
      if (avifBytes != null) {
        val nsData = avifBytes.toNSData()
        if (avifBytes.size <= targetSize) return nsData
        decodeAvifToImage(nsData)
      } else {
        // Decode the source ONCE; the loop only re-encodes it (M5 — previously each attempt
        // re-read and re-decoded the input).
        decodeSourceToImage(input)
      }

    return AdaptiveCompression.compress(
      options = options,
      targetSize = targetSize,
      encode = { opts -> encodeImageToAvif(source, opts) },
      sizeOf = { it.length.toLong() },
    )
  }

  /** The input's raw bytes when they already contain AVIF data, else null. */
  private suspend fun readInputAvifBytes(input: ImageInput): ByteArray? {
    val bytes =
      when (input) {
        is ImageInput.FromBytes -> input.data
        is ImageInput.FromPath -> {
          val url = NSURL.fileURLWithPath(input.path)
          NSData.dataWithContentsOfURL(url)?.toByteArray() ?: return null
        }
        is ImageInput.FromFile -> input.file.readBytes()
        is ImageInput.FromBitmap -> return null
      }
    return if (AvifFormat.isAvif(bytes)) bytes else null
  }

  private suspend fun convertStandard(input: ImageInput, options: EncodingOptions): NSData =
    withContext(Dispatchers.Default) {
      when (input) {
        is ImageInput.FromBytes -> {
          val nsData = input.data.toNSData()
          if (AvifFormat.isAvif(input.data)) nsData
          else encodeImageToAvif(imageFromData(nsData), options)
        }

        is ImageInput.FromBitmap -> encodeImageToAvif(input.bitmap, options)

        is ImageInput.FromPath -> {
          val url = NSURL.fileURLWithPath(input.path)
          val data = NSData.dataWithContentsOfURL(url) ?: throw AvifError.InvalidInput
          // Sniff by content, not by ".avif" extension (M4).
          if (AvifFormat.isAvif(data.headerBytes())) data
          else encodeImageToAvif(imageFromData(data), options)
        }

        is ImageInput.FromFile -> {
          val byteData = input.file.readBytes()
          val nsData = byteData.toNSData()
          if (AvifFormat.isAvif(byteData)) nsData
          else encodeImageToAvif(imageFromData(nsData), options)
        }
      }
    }

  /** Decode any non-AVIF input to a UIImage. Reads the source exactly once. */
  private suspend fun decodeSourceToImage(input: ImageInput): UIImage =
    when (input) {
      is ImageInput.FromBitmap -> input.bitmap
      is ImageInput.FromBytes -> imageFromData(input.data.toNSData())
      is ImageInput.FromFile -> imageFromData(input.file.readBytes().toNSData())
      is ImageInput.FromPath -> {
        val url = NSURL.fileURLWithPath(input.path)
        val data = NSData.dataWithContentsOfURL(url) ?: throw AvifError.InvalidInput
        imageFromData(data)
      }
    }

  private fun imageFromData(data: NSData): UIImage =
    UIImage.imageWithData(data) ?: throw AvifError.InvalidInput

  /**
   * Encode a UIImage to AVIF by calling libavif directly via cinterop. This is the iOS analog of
   * the Android JNI path (shared-native/.../avif_jni_wrapper.cpp): extract RGBA bytes from the
   * image, convert RGB→YUV, then avifEncoderWrite. No Swift, no handler registration.
   */
  private fun encodeImageToAvif(image: UIImage, options: EncodingOptions): NSData {
    try {
      // Determine alpha from the ORIGINAL image: resizing renders into an RGBA context that always
      // carries an alpha channel, so it can't be the source of truth (M6).
      val hasAlpha = imageHasAlpha(image)
      // Orientation-normalize + optional downscale, then pull RGBA8888 bytes via CoreGraphics.
      val normalized = options.maxDimension?.let { resizeImage(image, it) } ?: image
      val rgba = uiImageToRgba(normalized) ?: throw AvifError.InvalidInput
      return encodeRgbaToAvif(rgba.pixels, rgba.width, rgba.height, options, hasAlpha)
    } catch (e: AvifError) {
      throw e
    } catch (e: OutOfMemoryError) {
      throw AvifError.OutOfMemory
    } catch (e: Exception) {
      NSLog("❌ Unexpected error during encoding: ${e.message}")
      if (e.message?.contains("memory", ignoreCase = true) == true) {
        throw AvifError.OutOfMemory
      }
      throw AvifError.EncodingFailed("Encoding failed: ${e.message}")
    }
  }

  /**
   * Run the RGBA byte buffer through libavif's encoder. Mirrors nativeEncode() in the JNI wrapper.
   */
  private fun encodeRgbaToAvif(
    rgba: ByteArray,
    width: Int,
    height: Int,
    options: EncodingOptions,
    hasAlpha: Boolean,
  ): NSData = memScoped {
    val pixelFormat =
      if (options.lossless) {
        // True lossless requires identity matrix coefficients, which libavif only
        // supports with YUV444 — any chroma subsampling is inherently lossy.
        AVIF_PIXEL_FORMAT_YUV444
      } else
        when (options.subsample) {
          ChromaSubsample.YUV444 -> AVIF_PIXEL_FORMAT_YUV444
          ChromaSubsample.YUV422 -> AVIF_PIXEL_FORMAT_YUV422
          ChromaSubsample.YUV420 -> AVIF_PIXEL_FORMAT_YUV420
        }

    val avifImage =
      avifImageCreate(width.toUInt(), height.toUInt(), 8u, pixelFormat)
        ?: throw AvifError.EncodingFailed("libavif: failed to create image")
    if (options.lossless) {
      // Without identity coefficients the RGB→YUV transform rounds and quality=100
      // alone is NOT lossless. (avifImageCreate defaults yuvRange to full range.)
      avifImage.pointed.matrixCoefficients = AVIF_MATRIX_COEFFICIENTS_IDENTITY.convert()
    }
    val encoder =
      avifEncoderCreate()
        ?: run {
          avifImageDestroy(avifImage)
          throw AvifError.EncodingFailed("libavif: failed to create encoder")
        }

    try {
      // Quality (0-100), alpha quality, and speed map straight onto the encoder, matching the
      // Android wrapper. For lossless, both channels must be AVIF_QUALITY_LOSSLESS (100).
      encoder.pointed.quality = if (options.lossless) AVIF_QUALITY_LOSSLESS else options.quality
      encoder.pointed.qualityAlpha =
        if (options.lossless) AVIF_QUALITY_LOSSLESS else options.alphaQuality
      encoder.pointed.speed = options.speed
      encoder.pointed.maxThreads = codecThreads
      encoder.pointed.codecChoice = AVIF_CODEC_CHOICE_AUTO

      // Opaque sources skip the alpha plane entirely (M6): smaller output, no phantom alpha on
      // decode. avifImageRGBToYUV also honors rgb.ignoreAlpha below, so the two agree.
      val planes = if (hasAlpha) AVIF_PLANES_YUV or AVIF_PLANES_A else AVIF_PLANES_YUV
      val allocRes = avifImageAllocatePlanes(avifImage, planes)
      if (allocRes != AVIF_RESULT_OK) {
        throw AvifError.EncodingFailed("libavif: ${avifResultToString(allocRes)?.toKString()}")
      }

      rgba.usePinned { pinned ->
        val rgb = alloc<avifRGBImage>()
        avifRGBImageSetDefaults(rgb.ptr, avifImage)
        rgb.pixels = pinned.addressOf(0).reinterpret()
        rgb.rowBytes = (width * 4).toUInt()
        rgb.format = AVIF_RGB_FORMAT_RGBA
        rgb.depth = 8u
        // uiImageToRgba draws through CGBitmapContext, which can only produce
        // PREMULTIPLIED alpha. Declaring it so makes libavif unpremultiply during
        // RGB→YUV (AVIF stores straight alpha); otherwise semi-transparent pixels
        // are encoded with darkened RGB values.
        rgb.alphaPremultiplied = AVIF_TRUE
        // Opaque: treat the RGBA buffer as opaque so RGB→YUV neither reads nor emits alpha.
        rgb.ignoreAlpha = if (hasAlpha) AVIF_FALSE else AVIF_TRUE

        val convertRes = avifImageRGBToYUV(avifImage, rgb.ptr)
        if (convertRes != AVIF_RESULT_OK) {
          throw AvifError.EncodingFailed("libavif: ${avifResultToString(convertRes)?.toKString()}")
        }
      }

      val output = alloc<avifRWData>()
      try {
        val encodeRes = avifEncoderWrite(encoder, avifImage, output.ptr)
        if (encodeRes != AVIF_RESULT_OK) {
          throw AvifError.EncodingFailed("libavif: ${avifResultToString(encodeRes)?.toKString()}")
        }
        if (output.size == 0uL || output.data == null) {
          throw AvifError.EncodingFailed("libavif: encoder produced empty output")
        }
        NSData.create(bytes = output.data, length = output.size)
      } finally {
        avifRWDataFree(output.ptr)
      }
    } finally {
      avifImageDestroy(avifImage)
      avifEncoderDestroy(encoder)
    }
  }

  /**
   * Decode AVIF bytes to a UIImage by calling libavif directly. Mirrors nativeDecode() in the JNI
   * wrapper: decode → YUV→RGBA → build a CGImage/UIImage.
   */
  private fun decodeAvifToImage(avifData: NSData): UIImage {
    try {
      return memScoped {
        val decoder =
          avifDecoderCreate() ?: throw AvifError.DecodingFailed("libavif: failed to create decoder")
        val rgb = alloc<avifRGBImage>()
        var rgbAllocated = false
        try {
          decoder.pointed.maxThreads = codecThreads
          decoder.pointed.ignoreXMP = AVIF_TRUE
          decoder.pointed.ignoreExif = AVIF_FALSE

          val bytes = avifData.bytes?.reinterpret<UByteVar>()
          val size = avifData.length
          val ioRes = avifDecoderSetIOMemory(decoder, bytes, size)
          if (ioRes != AVIF_RESULT_OK) {
            throw AvifError.DecodingFailed("libavif: ${avifResultToString(ioRes)?.toKString()}")
          }
          val parseRes = avifDecoderParse(decoder)
          if (parseRes != AVIF_RESULT_OK) {
            throw AvifError.DecodingFailed("libavif: ${avifResultToString(parseRes)?.toKString()}")
          }
          val nextRes = avifDecoderNextImage(decoder)
          if (nextRes != AVIF_RESULT_OK) {
            throw AvifError.DecodingFailed("libavif: ${avifResultToString(nextRes)?.toKString()}")
          }

          val decodedImage =
            decoder.pointed.image
              ?: throw AvifError.DecodingFailed("libavif: decoder produced no image")

          avifRGBImageSetDefaults(rgb.ptr, decodedImage)
          rgb.format = AVIF_RGB_FORMAT_RGBA
          rgb.depth = 8u
          // rgbaToUIImage wraps this buffer in a CGImage declared as PREMULTIPLIED
          // alpha (the only mode CGBitmapContext supports), so ask libavif to
          // premultiply during YUV→RGB. Without this, straight-alpha bytes get
          // displayed as premultiplied — washed-out/fringed transparency.
          rgb.alphaPremultiplied = AVIF_TRUE
          val rgbAllocRes = avifRGBImageAllocatePixels(rgb.ptr)
          if (rgbAllocRes != AVIF_RESULT_OK) {
            throw AvifError.DecodingFailed(
              "libavif: ${avifResultToString(rgbAllocRes)?.toKString()}"
            )
          }
          rgbAllocated = true

          val yuvRes = avifImageYUVToRGB(decodedImage, rgb.ptr)
          if (yuvRes != AVIF_RESULT_OK) {
            throw AvifError.DecodingFailed("libavif: ${avifResultToString(yuvRes)?.toKString()}")
          }

          // AVIF irot/imir orientation: libavif reports the transform properties but does
          // not apply them to the pixels — do it here (rotation first, then mirror).
          val transformFlags = decodedImage.pointed.transformFlags
          val irotAngle =
            if (transformFlags and AVIF_TRANSFORM_IROT.toUInt() != 0u) {
              decodedImage.pointed.irot.angle.toInt() and 3
            } else 0
          val imirAxis =
            if (transformFlags and AVIF_TRANSFORM_IMIR.toUInt() != 0u) {
              decodedImage.pointed.imir.axis.toInt() and 1
            } else -1

          val width = rgb.width.toInt()
          val height = rgb.height.toInt()
          val rowBytes = rgb.rowBytes.toInt()
          val image =
            if (RgbaTransform.isIdentity(irotAngle, imirAxis)) {
              rgbaToUIImage(rgb.pixels!!, width, height, rowBytes)
            } else {
              val srcBytes = ByteArray(rowBytes * height)
              srcBytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), rgb.pixels, (rowBytes * height).toULong())
              }
              val oriented =
                RgbaTransform.apply(srcBytes, width, height, rowBytes, irotAngle, imirAxis)
              oriented.pixels.usePinned { pinned ->
                rgbaToUIImage(
                  pinned.addressOf(0).reinterpret(),
                  oriented.width,
                  oriented.height,
                  oriented.width * 4,
                )
              }
            }
          image ?: throw AvifError.DecodingFailed("libavif: failed to build UIImage from RGBA")
        } finally {
          if (rgbAllocated) avifRGBImageFreePixels(rgb.ptr)
          avifDecoderDestroy(decoder)
        }
      }
    } catch (e: AvifError) {
      throw e
    } catch (e: OutOfMemoryError) {
      throw AvifError.OutOfMemory
    } catch (e: Exception) {
      NSLog("❌ Unexpected error during decoding: ${e.message}")
      if (e.message?.contains("memory", ignoreCase = true) == true) {
        throw AvifError.OutOfMemory
      }
      throw AvifError.DecodingFailed("Decoding failed: ${e.message}")
    }
  }

  /** RGBA8888 pixel buffer extracted from a UIImage (origin top-left, premultiplied alpha). */
  private class RgbaBuffer(val pixels: ByteArray, val width: Int, val height: Int)

  /**
   * Draw a UIImage into an RGBA8888 CGBitmapContext and read back the bytes. Drawing through the
   * context bakes in the image's `imageOrientation`, so this also handles EXIF orientation (the job
   * the Swift `normalizeOrientation` used to do).
   */
  private fun uiImageToRgba(image: UIImage): RgbaBuffer? {
    val cgImage = image.CGImage ?: return null
    val width = CGImageGetWidth(cgImage).toInt()
    val height = CGImageGetHeight(cgImage).toInt()
    if (width <= 0 || height <= 0) return null

    val bytesPerRow = width * 4
    val pixels = ByteArray(bytesPerRow * height)
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    try {
      pixels.usePinned { pinned ->
        val ctx =
          CGBitmapContextCreate(
            data = pinned.addressOf(0),
            width = width.toULong(),
            height = height.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = bytesPerRow.toULong(),
            space = colorSpace,
            bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
          ) ?: return null
        try {
          // Draw via UIImage so imageOrientation is applied (origin flips handled by UIKit).
          UIGraphicsPushContext(ctx)
          memScoped {
            val rect =
              alloc<CGRect>().apply {
                origin.x = 0.0
                origin.y = 0.0
                size.width = width.toDouble()
                size.height = height.toDouble()
              }
            // CoreGraphics origin is bottom-left; flip so the drawn image is top-left like the
            // RGBA buffer libavif expects.
            CGContextTranslateCTM(ctx, 0.0, height.toDouble())
            CGContextScaleCTM(ctx, 1.0, -1.0)
            image.drawInRect(rect.readValue())
          }
          UIGraphicsPopContext()
        } finally {
          // context released by ARC bridge when ctx goes out of scope
        }
      }
    } finally {
      CGColorSpaceRelease(colorSpace)
    }
    return RgbaBuffer(pixels, width, height)
  }

  /** Build a UIImage from an RGBA8888 buffer produced by libavif's decoder. */
  private fun rgbaToUIImage(
    pixels: CPointer<UByteVar>,
    width: Int,
    height: Int,
    rowBytes: Int,
  ): UIImage? {
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    try {
      val ctx =
        CGBitmapContextCreate(
          data = pixels,
          width = width.toULong(),
          height = height.toULong(),
          bitsPerComponent = 8u,
          bytesPerRow = rowBytes.toULong(),
          space = colorSpace,
          bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
        ) ?: return null
      val cgImage = CGBitmapContextCreateImage(ctx) ?: return null
      try {
        return UIImage.imageWithCGImage(cgImage)
      } finally {
        CGImageRelease(cgImage)
      }
    } finally {
      CGColorSpaceRelease(colorSpace)
    }
  }

  /**
   * Downscale a UIImage so its longest side is at most [maxDimension] PIXELS; no-op if already
   * smaller. Uses the CGImage's pixel dimensions (not `image.size`, which is in points and lets
   * `scale>1` images — screenshots, asset catalogs — dodge the resize, unlike Android). Renders
   * with UIGraphicsImageRenderer (UIGraphicsBeginImageContext* is deprecated since iOS 17). (M8)
   */
  private fun resizeImage(image: UIImage, maxDimension: Int): UIImage {
    val cgImage = image.CGImage ?: return image
    val pxWidth = CGImageGetWidth(cgImage).toInt()
    val pxHeight = CGImageGetHeight(cgImage).toInt()

    if (pxWidth <= maxDimension && pxHeight <= maxDimension) {
      return image
    }

    val scale = maxDimension.toDouble() / maxOf(pxWidth, pxHeight)
    // Clamp to >= 1px so an extreme aspect ratio can't collapse the short side to 0.
    val newWidth = (pxWidth * scale).toInt().coerceAtLeast(1)
    val newHeight = (pxHeight * scale).toInt().coerceAtLeast(1)

    return memScoped {
      val newSize =
        alloc<CGSize>().apply {
          this.width = newWidth.toDouble()
          this.height = newHeight.toDouble()
        }
      // scale = 1 so the output is exactly newWidth x newHeight PIXELS (not @2x/@3x points).
      val format = UIGraphicsImageRendererFormat.defaultFormat().apply { setScale(1.0) }
      val renderer = UIGraphicsImageRenderer(size = newSize.readValue(), format = format)
      val rect =
        alloc<CGRect>().apply {
          origin.x = 0.0
          origin.y = 0.0
          size.width = newWidth.toDouble()
          size.height = newHeight.toDouble()
        }
      renderer.imageWithActions { image.drawInRect(rect.readValue()) }
    }
  }

  private fun imageHasAlpha(image: UIImage): Boolean {
    val cgImage = image.CGImage ?: return false
    val alphaInfo = CGImageGetAlphaInfo(cgImage)
    return alphaInfo != CGImageAlphaInfo.kCGImageAlphaNone &&
      alphaInfo != CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst &&
      alphaInfo != CGImageAlphaInfo.kCGImageAlphaNoneSkipLast
  }

  // Extension functions for data conversion
  private fun ByteArray.toNSData(): NSData {
    // addressOf(0) throws on an empty array, escaping the AvifError hierarchy (L1).
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
      NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }
  }

  private fun NSData.toByteArray(): ByteArray {
    if (this.length.toInt() == 0) return ByteArray(0)
    return ByteArray(this.length.toInt()).apply {
      usePinned { pinned ->
        memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
      }
    }
  }

  /**
   * Copy just the leading bytes needed for a format sniff, without materializing the whole file.
   */
  private fun NSData.headerBytes(): ByteArray {
    val n = minOf(this.length.toInt(), AvifFormat.HEADER_CHECK_SIZE)
    if (n <= 0) return ByteArray(0)
    return ByteArray(n).apply {
      usePinned { pinned -> memcpy(pinned.addressOf(0), this@headerBytes.bytes, n.toULong()) }
    }
  }
}
