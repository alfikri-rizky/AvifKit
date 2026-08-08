package com.alfikri.rizky.avifstudio.platform

import com.alfikri.rizky.avifkit.PlatformFile

data class FileMetadata(val name: String, val sizeBytes: Long)

/**
 * Best-effort name and size for a file the user handed us.
 *
 * FileKit reads both through the platform's own metadata lookup, which is reliable for files but
 * not for every content provider: a `content://media/...` URI arriving from another app's share
 * sheet can come back with no display name at all, leaving the raw row id (`46`) as the "name" and
 * `-1` as the size. Both are user-visible, so each platform gets a chance to do better here — and
 * the size is corrected again from the bytes actually read when the conversion runs, which is the
 * only number the savings maths is allowed to trust.
 */
expect fun PlatformFile.resolveMetadata(): FileMetadata
