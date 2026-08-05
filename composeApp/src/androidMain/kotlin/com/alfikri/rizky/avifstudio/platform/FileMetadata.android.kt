package com.alfikri.rizky.avifstudio.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.alfikri.rizky.avifkit.PlatformFile
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size

actual fun PlatformFile.resolveMetadata(): FileMetadata {
  val fallbackName = name
  val fallbackSize = size()
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
  return FileMetadata(name = name.withExtension(uri, context), sizeBytes = size)
}

/**
 * A name with no extension is almost always a provider handing back a bare row id. The output file
 * is named after this, so give it one from the MIME type rather than shipping `46.avif` to the
 * user's downloads folder.
 */
private fun String.withExtension(uri: Uri, context: Context): String {
  if (substringAfterLast('.', "").isNotEmpty()) return this
  val extension =
    when (context.contentResolver.getType(uri)?.lowercase()) {
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
