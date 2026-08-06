package com.alfikri.rizky.avifstudio.platform

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.alfikri.rizky.avifkit.PlatformFile
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun PlatformFile.resolveMetadata(): FileMetadata {
  // Both of these query the content provider, and both throw SecurityException when the grant on a
  // shared URI has lapsed — which killed the app on an API 24 emulator the moment such a URI
  // arrived. Naming a file is never worth a crash: fall back, and let the conversion itself fail
  // with a message on the row if the bytes really are unreadable.
  val fallbackName = runCatching { name }.getOrDefault(DEFAULT_NAME)
  val fallbackSize = runCatching { size() }.getOrDefault(-1L)
  val uri = (androidFile as? AndroidFile.UriWrapper)?.uri
  val context = AppContext.applicationContext
  if (uri == null || context == null) {
    return FileMetadata(fallbackName, fallbackSize.coerceAtLeast(0L))
  }

  val queried =
    try {
      context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
          if (!cursor.moveToFirst()) return@use null
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
          FileMetadata(
            name =
              if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                cursor.getString(nameIndex)
              } else {
                fallbackName
              },
            sizeBytes =
              if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L,
          )
        }
    } catch (_: Exception) {
      // A provider that refuses the query is not a reason to drop the file: the converter still
      // reads the stream, and the real size is measured from those bytes.
      null
    }

  val name = queried?.name ?: fallbackName
  val size = (queried?.sizeBytes ?: fallbackSize).coerceAtLeast(0L)
  return FileMetadata(
    name = name.withExtension(uri, context).deredact(uri, context),
    sizeBytes = size,
  )
}

/** Used only when the provider will not even tell us what the file is called. */
private const val DEFAULT_NAME = "image"

/**
 * The Android photo picker deliberately withholds the real filename and hands back the MediaStore
 * row id instead — `50.jpg` for a photo actually called `holiday.jpg`. Nothing can recover the
 * original name, but a bare row id is a poor thing to name the user's exported file after, so fall
 * back to the capture date, which the picker does expose.
 */
private fun String.deredact(uri: Uri, context: Context): String {
  val base = substringBeforeLast('.', this)
  if (base.isEmpty() || !base.all { it.isDigit() }) return this
  val extension = substringAfterLast('.', "")
  val suffix = if (extension.isEmpty()) "" else ".$extension"

  val takenAtMillis =
    try {
      context.contentResolver
        .query(
          uri,
          arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_ADDED),
          null,
          null,
          null,
        )
        ?.use { cursor ->
          if (!cursor.moveToFirst()) return@use null
          val takenIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
          val addedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
          when {
            takenIndex >= 0 && !cursor.isNull(takenIndex) -> cursor.getLong(takenIndex)
            // DATE_ADDED is in seconds, DATE_TAKEN in milliseconds.
            addedIndex >= 0 && !cursor.isNull(addedIndex) -> cursor.getLong(addedIndex) * 1000
            else -> null
          }
        }
    } catch (_: Exception) {
      null
    } ?: return "IMG_$base$suffix"

  val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(takenAtMillis))
  return "IMG_$stamp$suffix"
}

/**
 * A name with no extension is almost always a provider handing back a bare row id. The output file
 * is named after this, so give it one from the MIME type rather than shipping `46.avif` to the
 * user's downloads folder.
 */
private fun String.withExtension(uri: Uri, context: Context): String {
  if (substringAfterLast('.', "").isNotEmpty()) return this
  val extension =
    when (runCatching { context.contentResolver.getType(uri) }.getOrNull()?.lowercase()) {
      "image/jpeg" -> "jpg"
      "image/png" -> "png"
      "image/webp" -> "webp"
      "image/avif",
      "image/avif-sequence" -> "avif"
      "image/heic",
      "image/heif" -> "heic"
      "image/gif" -> "gif"
      "image/bmp" -> "bmp"
      else -> null
    } ?: return this
  return "image-$this.$extension"
}
