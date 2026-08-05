package com.alfikri.rizky.avifstudio.engine

import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.FileNaming
import com.alfikri.rizky.avifstudio.model.OutputFormat
import com.alfikri.rizky.avifstudio.model.SourceImage

/**
 * The decisions a batch makes before any pixel is touched — kept pure so they can be tested without
 * a codec, a device, or a filesystem.
 */
object ConversionPlanner {

  /**
   * Output names for a whole batch, deduplicated in list order.
   *
   * Picking `IMG_0042.jpg` from two different folders is routine, and without this the second
   * output silently overwrites the first.
   */
  fun outputNames(sources: List<SourceImage>, format: OutputFormat): List<String> {
    val taken = mutableSetOf<String>()
    return sources.map { source ->
      val unique = FileNaming.uniqueName(FileNaming.outputName(source.displayName, format), taken)
      taken += unique
      unique
    }
  }

  /**
   * Whether to throw away a conversion and keep the original file instead.
   *
   * An app that promises smaller files must not hand back a bigger one without saying so.
   * Re-encoding an already-optimised JPEG, or turning a flat-colour PNG into AVIF, genuinely can
   * grow the file. Format-changing recipes opt out, because there the point is the format, not the
   * size.
   */
  fun shouldKeepOriginal(
    inputBytes: Long,
    outputBytes: Long,
    settings: ConversionSettings,
  ): Boolean = settings.skipIfLarger && inputBytes > 0 && outputBytes >= inputBytes

  /**
   * Estimated per-image peak memory, in bytes, used to warn before a batch of very large photos. A
   * decoded bitmap is 4 bytes per pixel, and the pipeline holds roughly the source bytes plus one
   * decoded copy plus the encoder's output at once.
   */
  fun estimatedPeakBytes(width: Int, height: Int, sourceBytes: Long): Long =
    width.toLong() * height.toLong() * 4L + sourceBytes * 2L
}
