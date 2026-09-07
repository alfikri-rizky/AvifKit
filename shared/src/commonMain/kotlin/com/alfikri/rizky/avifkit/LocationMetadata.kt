package com.alfikri.rizky.avifkit

/**
 * Whether a source image carries the coordinates it was taken at.
 *
 * Worth asking separately from "does it have EXIF", because a photo can arrive with its GPS block
 * structurally intact and every value zeroed — which is what Android's system photo picker hands an
 * app that does not hold `ACCESS_MEDIA_LOCATION`. [EncodingOptions.preserveMetadata] copies that
 * block faithfully, so an app promising "GPS travels with the photo" needs a way to notice the
 * promise cannot be kept for this particular file and say so, rather than quietly writing an empty
 * block and leaving the user to discover it later.
 *
 * Reading this costs a walk of the EXIF header — no pixels are decoded.
 */
enum class LocationMetadata {
  /** No GPS block at all: the photo was never tagged, or the tags were properly stripped. */
  NONE,

  /**
   * Coordinates are there and will be carried over when [EncodingOptions.preserveMetadata] is on.
   */
  PRESENT,

  /**
   * The GPS block survived but its values were zeroed before this app ever read the file.
   *
   * On Android this means the image came through the system photo picker, which grants per-item
   * read access *instead of* a media permission and removes locations as part of that trade. A file
   * chosen through the Storage Access Framework — a document picker rather than the photo picker —
   * arrives with its coordinates intact.
   */
  REDACTED;

  companion object {
    /**
     * What [source]'s EXIF says about location. [NONE] for a format AvifKit does not read metadata
     * from (HEIC, GIF, AVIF) — absence of evidence, so callers should not present it as "no GPS".
     */
    fun of(source: ByteArray): LocationMetadata =
      MetadataExtractor.extract(source)?.exif?.let { ExifTiff.gpsState(it) } ?: NONE
  }
}
