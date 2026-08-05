package com.alfikri.rizky.avifstudio.model

/**
 * Output file naming.
 *
 * Batch conversion turns naming into a correctness problem rather than a cosmetic one: pick 20
 * photos from three folders and `IMG_0042.jpg` shows up more than once, so the second output must
 * not silently overwrite the first.
 */
object FileNaming {

  /**
   * Characters no mainstream filesystem (or SAF/Files provider) accepts in a name. Spaces are
   * deliberately absent — they are legal everywhere we write, and mangling `My Photo.jpg` into
   * `My_Photo.avif` makes the output look like it came from some other file.
   */
  private val ILLEGAL = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

  /** `photo.JPG` + AVIF → `photo.avif`. Names with no extension just gain one. */
  fun outputName(sourceName: String, format: OutputFormat): String {
    val sanitized = sanitize(baseName(sourceName))
    // Sanitising replaces illegal characters rather than dropping them, so a name made entirely of
    // them survives as a row of underscores. Nobody wants to find `___.avif` in their downloads.
    val base = if (sanitized.any { it.isLetterOrDigit() }) sanitized else "image"
    return "$base.${format.extension}"
  }

  /**
   * Makes [name] unique against [taken], appending ` (2)`, ` (3)`… before the extension the way a
   * desktop file manager does. Comparison is case-insensitive because Android's SAF and iOS's Files
   * app both treat `Photo.avif` and `photo.avif` as the same file.
   */
  fun uniqueName(name: String, taken: Set<String>): String {
    val lowerTaken = taken.mapTo(mutableSetOf()) { it.lowercase() }
    if (name.lowercase() !in lowerTaken) return name

    val base = baseName(name)
    val extension = name.substringAfterLast('.', "")
    val suffix = if (extension.isEmpty()) "" else ".$extension"
    var counter = 2
    while (true) {
      val candidate = "$base ($counter)$suffix"
      if (candidate.lowercase() !in lowerTaken) return candidate
      counter++
    }
  }

  /** Name without its final extension: `a.b.jpg` → `a.b`, `README` → `README`. */
  fun baseName(name: String): String = name.substringBeforeLast('.', name)

  /** Lowercased extension without the dot, or `""` when there is none. */
  fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

  /** Strips path separators and other characters that would break a save target. */
  fun sanitize(name: String): String =
    name
      .map { if (it in ILLEGAL || it.code < 0x20) '_' else it }
      .joinToString("")
      .trim()
      .trimEnd('.')
}
