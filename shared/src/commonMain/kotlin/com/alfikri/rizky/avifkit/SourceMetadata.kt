package com.alfikri.rizky.avifkit

/**
 * The Exif and XMP blobs lifted out of a source image, ready to hand to libavif.
 *
 * Both are raw payloads, copied rather than re-serialised: whatever the camera or editor wrote —
 * maker notes, GPS, ratings, edit history — survives the trip, because nothing here re-encodes it.
 * [ExifTiff.normalize] is the one exception, and it only rewrites tags that would otherwise
 * describe the input instead of the output.
 */
internal data class SourceMetadata(val exif: ByteArray?, val xmp: ByteArray?) {

  val isEmpty: Boolean
    get() = exif == null && xmp == null

  // ByteArray fields make the generated equals/hashCode reference-based, which is wrong for a value
  // type and silently breaks assertEquals in tests.
  override fun equals(other: Any?): Boolean =
    this === other ||
      (other is SourceMetadata && exif.contentEquals(other.exif) && xmp.contentEquals(other.xmp))

  override fun hashCode(): Int = 31 * exif.contentHashCode() + xmp.contentHashCode()
}

/**
 * Pulls Exif and XMP out of the containers AvifKit can decode.
 *
 * Reading the blobs straight from the container, rather than through a platform API, is what keeps
 * Android and iOS honest about producing the same output: one parser, one set of tests, no
 * `ExifInterface`-vs-`ImageIO` differences in what gets carried over. It also sidesteps a real
 * trap: neither platform exposes the raw Exif *payload* that libavif wants, only parsed attributes,
 * and rebuilding a TIFF blob from those would quietly drop every tag the platform doesn't model.
 *
 * HEIF/HEIC is deliberately absent: its metadata lives in ISOBMFF items that need a real box
 * parser, and AvifKit reaches those files through the platform decoder anyway. GIF is absent
 * because it has nowhere to put Exif.
 */
internal object MetadataExtractor {

  /** Exif and XMP found in [data], or null when the format carries neither. */
  fun extract(data: ByteArray): SourceMetadata? =
    when (ImageFormats.detect(data)) {
      ImageFormat.JPEG -> fromJpeg(data)
      ImageFormat.PNG -> fromPng(data)
      ImageFormat.WEBP -> fromWebp(data)
      else -> null
    }?.takeUnless { it.isEmpty }

  private val EXIF_APP1_PREFIX = "Exif\u0000\u0000".encodeToByteArray()
  private val XMP_APP1_PREFIX = "http://ns.adobe.com/xap/1.0/\u0000".encodeToByteArray()
  private val PNG_XMP_KEYWORD = "XML:com.adobe.xmp".encodeToByteArray()

  /**
   * JPEG: Exif rides in an APP1 segment prefixed "Exif\u0000\u0000", XMP in another APP1 prefixed
   * with the Adobe namespace URI. Scanning stops at SOS — everything after it is entropy-coded scan
   * data, where a stray 0xFF would be misread as a marker.
   */
  private fun fromJpeg(data: ByteArray): SourceMetadata? {
    var exif: ByteArray? = null
    var xmp: ByteArray? = null
    var at = 2 // past SOI

    while (at + 4 <= data.size) {
      if (data[at] != 0xFF.toByte()) return SourceMetadata(exif, xmp)
      // Padding: a marker may be preceded by any number of 0xFF fill bytes.
      var marker = data[at + 1].toInt() and 0xFF
      var markerAt = at + 1
      while (marker == 0xFF && markerAt + 1 < data.size) {
        markerAt++
        marker = data[markerAt].toInt() and 0xFF
      }
      // SOS (start of scan) and EOI end the metadata region; standalone markers carry no length.
      if (marker == 0xDA || marker == 0xD9) break
      if (marker == 0x01 || marker in 0xD0..0xD7) {
        at = markerAt + 1
        continue
      }

      val lengthAt = markerAt + 1
      val length = readU16BE(data, lengthAt) ?: break
      if (length < 2) break
      val payloadAt = lengthAt + 2
      val payloadEnd = lengthAt + length
      if (payloadEnd > data.size) break

      if (marker == 0xE1) {
        when {
          exif == null && data.startsWith(EXIF_APP1_PREFIX, payloadAt) ->
            exif = data.copyOfRange(payloadAt + EXIF_APP1_PREFIX.size, payloadEnd)
          xmp == null && data.startsWith(XMP_APP1_PREFIX, payloadAt) ->
            xmp = data.copyOfRange(payloadAt + XMP_APP1_PREFIX.size, payloadEnd)
        }
      }
      at = payloadEnd
    }
    return SourceMetadata(exif, xmp)
  }

  /**
   * PNG: Exif in an `eXIf` chunk (raw TIFF, no "Exif\u0000\u0000" prefix), XMP in an uncompressed
   * `iTXt` chunk keyed "XML:com.adobe.xmp". A compressed iTXt is skipped rather than inflated —
   * there is no deflate in Kotlin common code, and writers overwhelmingly leave XMP uncompressed.
   */
  private fun fromPng(data: ByteArray): SourceMetadata? {
    var exif: ByteArray? = null
    var xmp: ByteArray? = null
    var at = 8 // past the PNG signature

    while (at + 8 <= data.size) {
      val length = readU32BE(data, at) ?: break
      val type = data.copyOfRangeOrNull(at + 4, at + 8) ?: break
      val payloadAt = at + 8
      if (length < 0 || payloadAt + length > data.size) break

      when (type.decodeToString()) {
        "eXIf" -> if (exif == null) exif = data.copyOfRange(payloadAt, payloadAt + length)
        "iTXt" -> if (xmp == null) xmp = pngXmpFromITxt(data, payloadAt, payloadAt + length)
        "IEND" -> return SourceMetadata(exif, xmp)
      }
      at = payloadAt + length + 4 // + CRC
    }
    return SourceMetadata(exif, xmp)
  }

  /** iTXt layout: keyword\0 compressionFlag compressionMethod langTag\0 translated\0 text. */
  private fun pngXmpFromITxt(data: ByteArray, start: Int, end: Int): ByteArray? {
    val keywordEnd = data.indexOfZero(start, end) ?: return null
    if (!data.regionMatches(PNG_XMP_KEYWORD, start, keywordEnd)) return null
    val compressionFlagAt = keywordEnd + 1
    if (compressionFlagAt + 2 > end) return null
    if (data[compressionFlagAt] != 0.toByte()) return null // compressed — see fromPng
    val langEnd = data.indexOfZero(compressionFlagAt + 2, end) ?: return null
    val translatedEnd = data.indexOfZero(langEnd + 1, end) ?: return null
    val textAt = translatedEnd + 1
    return if (textAt <= end) data.copyOfRange(textAt, end) else null
  }

  /**
   * WebP: an extended (VP8X) file stores Exif in an `EXIF` chunk and XMP in an `XMP ` chunk. Chunks
   * are padded to an even size, and the payload starts after the 12-byte RIFF/WEBP header.
   */
  private fun fromWebp(data: ByteArray): SourceMetadata? {
    var exif: ByteArray? = null
    var xmp: ByteArray? = null
    var at = 12 // past "RIFF" + size + "WEBP"

    while (at + 8 <= data.size) {
      val fourcc = data.copyOfRangeOrNull(at, at + 4)?.decodeToString() ?: break
      val length = readU32LE(data, at + 4) ?: break
      val payloadAt = at + 8
      if (length < 0 || payloadAt + length > data.size) break

      when (fourcc) {
        "EXIF" -> if (exif == null) exif = data.copyOfRange(payloadAt, payloadAt + length)
        "XMP " -> if (xmp == null) xmp = data.copyOfRange(payloadAt, payloadAt + length)
      }
      at = payloadAt + length + (length and 1) // chunks are padded to an even length
    }
    return SourceMetadata(exif, xmp)
  }

  private fun readU16BE(data: ByteArray, at: Int): Int? =
    if (at + 2 > data.size) null
    else ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)

  private fun readU32BE(data: ByteArray, at: Int): Int? = readU32(data, at, littleEndian = false)

  private fun readU32LE(data: ByteArray, at: Int): Int? = readU32(data, at, littleEndian = true)

  private fun readU32(data: ByteArray, at: Int, littleEndian: Boolean): Int? {
    if (at + 4 > data.size) return null
    var value = 0L
    for (i in 0 until 4) {
      val byte = (data[at + i].toInt() and 0xFF).toLong()
      value = value or (byte shl (if (littleEndian) i * 8 else (3 - i) * 8))
    }
    // A chunk longer than Int.MAX_VALUE can't be indexed here; treat it as malformed.
    return if (value > Int.MAX_VALUE) null else value.toInt()
  }

  private fun ByteArray.startsWith(prefix: ByteArray, at: Int): Boolean =
    regionMatches(prefix, at, at + prefix.size)

  private fun ByteArray.regionMatches(expected: ByteArray, start: Int, end: Int): Boolean {
    if (end - start != expected.size || end > size || start < 0) return false
    for (i in expected.indices) if (this[start + i] != expected[i]) return false
    return true
  }

  private fun ByteArray.indexOfZero(from: Int, until: Int): Int? {
    for (i in from until minOf(until, size)) if (this[i] == 0.toByte()) return i
    return null
  }

  private fun ByteArray.copyOfRangeOrNull(from: Int, to: Int): ByteArray? =
    if (from < 0 || to > size || from > to) null else copyOfRange(from, to)
}

/**
 * Rewrites the orientation XMP carries alongside Exif.
 *
 * Adobe tools mirror `tiff:Orientation` into XMP, and a reader that prefers XMP over Exif would
 * rotate an already-upright image if only the Exif copy were corrected. Replacement is restricted
 * to single-digit values so the packet keeps its exact byte length — XMP packets are padded to a
 * size writers may have recorded elsewhere, and no rewrite is worth reflowing one.
 */
internal object XmpOrientation {

  private const val ATTRIBUTE = "tiff:Orientation=\""
  private const val ELEMENT_OPEN = "<tiff:Orientation>"
  private const val ELEMENT_CLOSE = "</tiff:Orientation>"

  /** [xmp] with any orientation claim reset to 1 (the "already upright" value). */
  fun normalize(xmp: ByteArray): ByteArray {
    val text = xmp.decodeToString()
    var out = text
    for (value in 2..8) {
      out = out.replace("$ATTRIBUTE$value\"", "${ATTRIBUTE}1\"")
      out = out.replace("$ELEMENT_OPEN$value$ELEMENT_CLOSE", "${ELEMENT_OPEN}1$ELEMENT_CLOSE")
    }
    if (out == text) return xmp
    val encoded = out.encodeToByteArray()
    // Only a same-length rewrite is safe (see the class doc); anything else means the decode round
    // trip changed the bytes, so keep the original.
    return if (encoded.size == xmp.size) encoded else xmp
  }
}

/**
 * The metadata AvifKit embeds in an AVIF, given the source file's bytes.
 *
 * This is the single place the "preserve metadata" contract is implemented, so Android and iOS
 * cannot drift: extract the blobs, then rewrite the parts that describe the *input* — orientation
 * (the encoder receives already-rotated pixels) and pixel dimensions (which
 * [EncodingOptions.maxDimension] may have changed).
 */
internal object EncodedMetadata {

  /**
   * Metadata for a [width]x[height] AVIF encoded from [source], or null when there is none worth
   * embedding. [source] is null when the caller started from an already-decoded bitmap, where the
   * original file — and its metadata — never existed as far as AvifKit is concerned.
   */
  fun forSource(source: ByteArray?, width: Int, height: Int): SourceMetadata? {
    if (source == null) return null
    val found = MetadataExtractor.extract(source) ?: return null
    // A payload libavif can't find a TIFF header in makes avifEncoderWrite fail outright, so an
    // unparseable Exif blob is dropped rather than allowed to break the conversion.
    val exif = found.exif?.let { ExifTiff.normalize(it, width, height) }
    val xmp = found.xmp?.let { XmpOrientation.normalize(it) }
    return SourceMetadata(exif, xmp).takeUnless { it.isEmpty }
  }
}
