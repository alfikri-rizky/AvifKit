package com.alfikri.rizky.avifstudio.platform

import com.alfikri.rizky.avifkit.PlatformFile
import com.alfikri.rizky.avifstudio.settings.SaveLocation
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.fromBookmarkData
import io.github.vinceglb.filekit.isDirectory
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.startAccessingSecurityScopedResource
import io.github.vinceglb.filekit.stopAccessingSecurityScopedResource
import kotlin.io.encoding.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How a picked folder is written down, and how it is found again on the next launch.
 *
 * The id is a security-scoped bookmark, base64'd — the iOS counterpart to the persistable SAF grant
 * Android takes. A path would not survive: the folder sits outside our sandbox, so reaching it at
 * all needs the grant the bookmark carries, and the container path the app itself lives under is
 * reassigned on every reinstall.
 */
internal suspend fun PlatformFile.asSaveLocation(): SaveLocation =
  SaveLocation(id = Base64.encode(bookmarkData().bytes), label = name)

/**
 * Resolves what [asSaveLocation] wrote down and hands the folder to [block], or returns `null` when
 * the folder is gone, the bookmark no longer resolves, or the system refuses the grant behind it.
 *
 * Android's counterpart simply returns the folder. Here the folder is only reachable between
 * `start`/`stop` of its security scope — a refcounted pair — so the write has to happen inside the
 * bracket rather than after it.
 */
// Default, not IO: Kotlin/Native does not expose an IO dispatcher. Resolving a bookmark can reach a
// cloud provider, so it does not belong on the frame that asked for it either way.
internal suspend fun <T : Any> SaveLocation.useFolder(block: suspend (PlatformFile) -> T): T? =
  withContext(Dispatchers.Default) {
    val folder =
      runCatching { PlatformFile.fromBookmarkData(Base64.decode(id)) }.getOrNull()
        ?: return@withContext null
    if (!folder.startAccessingSecurityScopedResource()) return@withContext null
    try {
      if (folder.isDirectory()) block(folder) else null
    } finally {
      folder.stopAccessingSecurityScopedResource()
    }
  }
