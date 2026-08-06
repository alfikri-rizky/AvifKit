package com.alfikri.rizky.avifstudio.model

/**
 * The image format a job writes out.
 *
 * AVIF is what AvifKit encodes natively. JPEG and PNG exist because the reverse direction is the
 * other half of the real-world problem: an `.avif` downloaded from the web opens nowhere on Android
 * 11 and below, and plenty of upload forms still reject anything that isn't JPEG or PNG.
 *
 * WebP sits between the two: far smaller than JPEG, and unlike AVIF it is read by every browser and
 * OS still in service — which is what makes it the safe answer when AVIF is too new for whatever
 * has to open the file.
 */
enum class OutputFormat(val extension: String, val mimeType: String, val label: String) {
  AVIF("avif", "image/avif", "AVIF"),
  WEBP("webp", "image/webp", "WebP"),
  JPEG("jpg", "image/jpeg", "JPEG"),
  PNG("png", "image/png", "PNG");

  /** True when the encoder ignores the quality setting, so the UI can hide the slider. */
  val isLossless: Boolean
    get() = this == PNG
}
