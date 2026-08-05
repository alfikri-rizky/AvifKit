package com.alfikri.rizky.avifstudio.engine

import com.alfikri.rizky.avifstudio.model.ConversionOutput
import com.alfikri.rizky.avifstudio.model.ConversionSettings
import com.alfikri.rizky.avifstudio.model.SourceImage

/**
 * The slice of [ConversionEngine] the ViewModel actually depends on.
 *
 * It exists so the batch state machine can be tested without a codec, a filesystem or a device —
 * the queue, cancellation and the "a share arrived mid-run" path are where the interesting bugs
 * live, and none of them are about pixels.
 */
interface ConversionRunner {

  /** Converts [source] to [outputName]; `null` means the original was kept as-is. */
  suspend fun convert(
    source: SourceImage,
    settings: ConversionSettings,
    outputName: String,
  ): ConversionOutput?

  /** Discards everything previously written. */
  suspend fun clearOutputs()
}
