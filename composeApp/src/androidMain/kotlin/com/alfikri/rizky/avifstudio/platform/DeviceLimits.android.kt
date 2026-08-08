package com.alfikri.rizky.avifstudio.platform

import android.app.ActivityManager
import android.content.Context

/**
 * Derived from the per-app heap ceiling, which is the number that actually decides whether a decode
 * throws: `memoryClass` is `dalvik.vm.heapgrowthlimit`, 48 MB on the API 24 emulator this was found
 * on and 192 MB on a current one.
 *
 * The ladder budgets roughly two bitmaps of the capped size — the decode plus the scaled copy —
 * against a third of the heap. A 4:3 image at 1280 px is 4.9 MB, at 2560 px 19.7 MB, at 4096 px 50
 * MB.
 */
actual fun deviceImageDimensionCap(): Int? {
  val context = AppContext.applicationContext ?: return null
  val activityManager =
    runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
      .getOrNull() ?: return null
  return when (val heapMb = runCatching { activityManager.memoryClass }.getOrDefault(0)) {
    // Unknown (a unit-test stub, say) is treated as "not a constraint" rather than guessed at.
    0 -> null
    in 256..Int.MAX_VALUE -> null
    in 128..255 -> 4096
    in 96..127 -> 2560
    in 64..95 -> 1920
    else -> 1280
  }
}
