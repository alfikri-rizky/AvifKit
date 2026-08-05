package com.alfikri.rizky.avifstudio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alfikri.rizky.avifkit.PlatformFile
import com.alfikri.rizky.avifstudio.engine.ConversionEngine
import com.alfikri.rizky.avifstudio.engine.toImageBitmap
import io.github.vinceglb.filekit.path

/**
 * Small preview for a list row.
 *
 * Decoding goes through [ConversionEngine], which means it queues behind the same one-at-a-time
 * permit the conversions use. That is deliberate: an AVIF thumbnail is a full decode, and letting a
 * screenful of them run alongside a batch is exactly how a phone runs out of memory. The trade-off
 * is that thumbnails trickle in during a batch rather than appearing at once.
 */
@Composable
fun Thumbnail(file: PlatformFile, size: Dp = 56.dp, modifier: Modifier = Modifier) {
  val key = remember(file) { file.path }
  var image by remember(key) { mutableStateOf(ThumbnailCache[key]) }

  LaunchedEffect(key) {
    if (image != null) return@LaunchedEffect
    image =
      try {
        ConversionEngine().decodeForDisplay(file, THUMBNAIL_PX).toImageBitmap().also {
          ThumbnailCache[key] = it
        }
      } catch (_: Throwable) {
        // A thumbnail is decoration; a file that cannot be decoded reports itself through the
        // row's own error state instead.
        null
      }
  }

  val fade by animateFloatAsState(if (image != null) 1f else 0f, tween(180), label = "thumbnail")

  Box(
    modifier
      .size(size)
      .clip(MaterialTheme.shapes.small)
      .background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    image?.let {
      Image(
        bitmap = it,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().alpha(fade),
      )
    }
  }
}

/** Roughly 2× a 56 dp slot on a 3x screen — sharp enough, and 256 KB of pixels at most. */
private const val THUMBNAIL_PX = 256

/**
 * A tiny bounded cache so scrolling does not re-decode, and so a finished batch can redraw its rows
 * for free. Bounded because the entries are bitmaps: an unbounded map here would be the memory leak
 * that the decode gate exists to prevent.
 */
private object ThumbnailCache {
  private const val MAX_ENTRIES = 48
  private val entries = LinkedHashMap<String, ImageBitmap>()

  operator fun get(key: String): ImageBitmap? = entries[key]

  operator fun set(key: String, value: ImageBitmap) {
    entries.remove(key)
    entries[key] = value
    while (entries.size > MAX_ENTRIES) {
      val oldest = entries.keys.firstOrNull() ?: break
      entries.remove(oldest)
    }
  }
}
