package com.alfikri.rizky.avifstudio.engine

import com.alfikri.rizky.avifkit.AvifConverter
import com.alfikri.rizky.avifkit.ImageInput
import com.alfikri.rizky.avifkit.PlatformFile
import com.alfikri.rizky.avifstudio.model.ConversionOutput
import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.ImageSniffer
import com.alfikri.rizky.avifstudio.model.OutputFormat
import com.alfikri.rizky.avifstudio.model.SourceImage
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
import io.github.vinceglb.filekit.write
import kotlin.time.TimeSource
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Converts one image at a time.
 *
 * The single-permit [Semaphore] is the whole memory strategy, and it is not optional. A phone
 * happily hands you twenty 12 MP photos at once; decoding even three of those concurrently is ~150
 * MB of bitmaps plus the encoder's own working set, which is an OOM on a mid-range device.
 * Serialising through one permit caps peak usage at a single in-flight image no matter how many
 * callers (batch run, thumbnail warm-up, a retry the user tapped) arrive at the same time. The same
 * gate is shared process-wide for exactly that reason.
 */
class ConversionEngine(
  private val converter: AvifConverter = AvifConverter(),
  private val codec: ImageCodec = ImageCodec(),
) : ConversionRunner {

  /** Where converted files land before the user exports them. */
  private val outputDir: PlatformFile
    get() = FileKit.cacheDir / OUTPUT_DIR_NAME

  /**
   * Converts [source] and writes the result as [outputName] in the app's cache.
   *
   * Returns `null` when [ConversionSettings.skipIfLarger] applies — the conversion ran, the output
   * was no smaller than the original, and the caller should keep the original instead.
   */
  override suspend fun convert(
    source: SourceImage,
    settings: ConversionSettings,
    outputName: String,
  ): ConversionOutput? {
    val startedAt = TimeSource.Monotonic.markNow()
    // Outside the permit on purpose. A file picked from a cloud provider streams at whatever the
    // network gives you; holding the single encode permit through that download would freeze every
    // thumbnail and preview in the app for no memory benefit — the permit exists to cap decoded
    // bitmaps, not to serialise I/O.
    val sourceBytes = readSource(source.file)
    if (sourceBytes.isEmpty()) {
      throw IllegalStateException("The file is empty or could not be read")
    }

    val encoded = encodeGate.withPermit {
      when (settings.outputFormat) {
        OutputFormat.AVIF -> encodeAvif(sourceBytes, settings)
        OutputFormat.WEBP,
        OutputFormat.JPEG,
        OutputFormat.PNG -> encodeViaBitmap(sourceBytes, settings)
      }
    }

    // Measured, not advertised: a content provider can report -1 for a file it happily streams,
    // and every downstream number (savings, "was it smaller?") has to come from what was read.
    val inputBytes = sourceBytes.size.toLong()
    // Header-only read, so this costs nothing next to the encode that just ran. AVIF sources go
    // through AvifKit because BitmapFactory cannot read them below API 31.
    val inputSize =
      runCatching {
          if (ImageSniffer.isAvif(sourceBytes)) {
            converter.getImageInfo(ImageInput.from(sourceBytes)).let {
              PixelSize(it.width, it.height)
            }
          } else {
            codec.readSize(sourceBytes)
          }
        }
        .getOrNull()
    if (ConversionPlanner.shouldKeepOriginal(inputBytes, encoded.bytes.size.toLong(), settings)) {
      return null
    }

    val target = outputDir / outputName
    target.parent()?.createDirectories()
    // A previous run may have written the same name; write() appends to nothing but a stale
    // longer file would otherwise keep its tail.
    if (target.exists()) target.delete(mustExist = false)
    target.write(encoded.bytes)

    return ConversionOutput(
      file = target,
      displayName = outputName,
      sizeBytes = encoded.bytes.size.toLong(),
      inputBytes = inputBytes,
      inputWidth = inputSize?.width,
      inputHeight = inputSize?.height,
      width = encoded.size.width,
      height = encoded.size.height,
      format = settings.outputFormat,
      elapsedMillis = startedAt.elapsedNow().inWholeMilliseconds,
    )
  }

  /** Decodes any supported image (AVIF included) for preview, downscaled to [maxDimension]. */
  suspend fun decodeForDisplay(
    file: PlatformFile,
    maxDimension: Int?,
  ): com.alfikri.rizky.avifkit.PlatformBitmap {
    val bytes = readSource(file)
    return encodeGate.withPermit { decodeAny(bytes, maxDimension) }
  }

  /** Removes everything this engine has written. Called when the user clears a batch. */
  override suspend fun clearOutputs() {
    val dir = outputDir
    if (dir.exists()) dir.delete(mustExist = false)
  }

  private class Encoded(val bytes: ByteArray, val size: PixelSize)

  private suspend fun encodeAvif(sourceBytes: ByteArray, settings: ConversionSettings): Encoded {
    val bytes =
      converter.encodeAvif(
        input = ImageInput.from(sourceBytes),
        options = settings.toEncodingOptions(),
      )
    val info = converter.getImageInfo(ImageInput.from(bytes))
    return Encoded(bytes, PixelSize(info.width, info.height))
  }

  private suspend fun encodeViaBitmap(
    sourceBytes: ByteArray,
    settings: ConversionSettings,
  ): Encoded {
    val decoded = decodeAny(sourceBytes, settings.effectiveMaxDimension)
    val scaled = codec.scale(decoded, settings.effectiveMaxDimension)
    val bytes = codec.encode(scaled, settings.outputFormat, settings.effectiveQuality)
    return Encoded(bytes, codec.sizeOf(scaled))
  }

  /**
   * AVIF input has to go through AvifKit; everything else through the platform decoder. Sniffing
   * the header is cheaper and more reliable than trusting the file extension, which is routinely
   * wrong for files that arrived over chat apps.
   */
  private suspend fun decodeAny(
    bytes: ByteArray,
    maxDimension: Int?,
  ): com.alfikri.rizky.avifkit.PlatformBitmap =
    if (ImageSniffer.isAvif(bytes)) {
      // AvifKit decodes at native size; downscaling afterwards still beats not being able to
      // decode the format at all, which is the situation on Android 11 and below.
      codec.scale(converter.decodeAvif(ImageInput.from(bytes)), maxDimension)
    } else {
      codec.decode(bytes, maxDimension)
    }

  /**
   * iOS hands back security-scoped URLs for anything picked outside the app sandbox; reading one
   * without claiming access first fails. On Android these calls are no-ops.
   */
  private suspend fun readSource(file: PlatformFile): ByteArray {
    val claimed = file.startAccessingSecurityScopedResource()
    return try {
      file.readBytes()
    } finally {
      if (claimed) file.stopAccessingSecurityScopedResource()
    }
  }

  companion object {
    const val OUTPUT_DIR_NAME = "avifstudio-output"

    /**
     * Process-wide, on purpose: the batch runner, the preview loader and any retry all have to
     * queue behind the same permit or the memory ceiling means nothing.
     */
    private val encodeGate = Semaphore(permits = 1)
  }
}
