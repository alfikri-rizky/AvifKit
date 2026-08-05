package com.alfikri.rizky.avifstudio.model

/**
 * The image format a job writes out.
 *
 * AVIF is what AvifKit encodes natively. JPEG and PNG exist because the reverse direction is the
 * other half of the real-world problem: an `.avif` downloaded from the web opens nowhere on Android
 * 11 and below, and plenty of upload forms still reject anything that isn't JPEG or PNG.
 */
enum class OutputFormat(val extension: String, val mimeType: String, val label: String) {
  AVIF("avif", "image/avif", "AVIF"),
  JPEG("jpg", "image/jpeg", "JPEG"),
  PNG("png", "image/png", "PNG");

  /** True when the encoder ignores the quality setting, so the UI can hide the slider. */
  val isLossless: Boolean
    get() = this == PNG
}
