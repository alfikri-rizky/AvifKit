package com.alfikri.rizky.avifstudio.platform

import android.content.Context
import androidx.core.net.toUri
import com.alfikri.rizky.avifstudio.settings.SaveLocation
import com.anggrayudi.storage.StorageFile

/**
 * How a picked folder is written down, and how it is found again on the next launch.
 *
 * The id is SimpleStorage's volume-relative form (`primary:Pictures/AVIF`, `AAAA-BBBB:Backup`)
 * rather than the SAF tree URI, wherever the folder has a real path. Two reasons, both of which
 * bite only after a restart, which is exactly when nobody is watching:
 * - Below Android 10 the picker does not persist a grant for primary storage at all — writes go
 *   through the storage permission instead — so a stored tree URI is a `SecurityException` later.
 * - An SD card can come back under a different volume URI; the storage id and base path do not move
 *   with it.
 *
 * A folder on a cloud provider has no path at all, and keeps its URI.
 */
internal fun StorageFile.asSaveLocation(): SaveLocation =
  SaveLocation(id = path?.toString() ?: uri.toString(), label = absolutePath ?: name)

/**
 * Resolves what [asSaveLocation] wrote down, or `null` when the folder is gone, its volume is
 * unmounted, or the grant behind it was revoked. Touches the content resolver — call it off the
 * main thread.
 */
internal fun SaveLocation.resolve(context: Context): StorageFile? =
  if (id.startsWith(CONTENT_SCHEME)) {
      StorageFile.from(context, id.toUri())?.takeIf { it.canWrite }
    } else {
      StorageFile.fromPath(context, id, requiresWriteAccess = true)
    }
    ?.takeIf { it.isDirectory }

private const val CONTENT_SCHEME = "content://"
