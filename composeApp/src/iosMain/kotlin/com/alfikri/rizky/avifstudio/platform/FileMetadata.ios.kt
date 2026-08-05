package com.alfikri.rizky.avifstudio.platform

import com.alfikri.rizky.avifkit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size

/**
 * iOS hands back real file URLs for everything the picker returns, so FileKit's own name and size
 * are already the file's name and size. Only the negative-size sentinel needs flattening.
 */
actual fun PlatformFile.resolveMetadata(): FileMetadata =
  FileMetadata(name = name, sizeBytes = size().coerceAtLeast(0L))
