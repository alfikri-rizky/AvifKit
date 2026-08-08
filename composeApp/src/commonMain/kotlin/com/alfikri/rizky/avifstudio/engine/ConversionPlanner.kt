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

  /** Deduplicated in list order; see [FileNaming] for why that matters. */
  fun outputNames(sources: List<SourceImage>, format: OutputFormat): List<String> {
    val taken = mutableSetOf<String>()
    return sources.map { source ->
      val unique = FileNaming.uniqueName(FileNaming.outputName(source.displayName, format), taken)
      taken += unique
      unique
    }
  }

  /** The [ConversionSettings.skipIfLarger] decision, kept here so it can be tested on its own. */
  fun shouldKeepOriginal(
    inputBytes: Long,
    outputBytes: Long,
    settings: ConversionSettings,
  ): Boolean = settings.skipIfLarger && inputBytes > 0 && outputBytes >= inputBytes
}
