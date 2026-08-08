package com.alfikri.rizky.avifstudio.platform

import com.alfikri.rizky.avifkit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Files handed to the app from outside it — "Open with" on an `.avif`, or "Share to" a batch of
 * photos from the gallery.
 *
 * Lives outside the ViewModel because the delivery point is platform code (an Android Intent, an
 * iOS URL callback) that runs before, and independently of, any composition.
 */
object IncomingFiles {

  private val _pending = MutableStateFlow<List<PlatformFile>>(emptyList())
  val pending: StateFlow<List<PlatformFile>> = _pending.asStateFlow()

  fun offer(files: List<PlatformFile>) {
    if (files.isEmpty()) return
    _pending.update { it + files }
  }

  /** Called by the UI once it has taken them, so a recomposition cannot add them twice. */
  fun consume(): List<PlatformFile> {
    val taken = _pending.value
    if (taken.isNotEmpty()) _pending.value = emptyList()
    return taken
  }
}
