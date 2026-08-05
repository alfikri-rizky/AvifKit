package com.alfikri.rizky.avifstudio.platform

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri

actual fun openUrl(url: String) {
  val context = AppContext.applicationContext ?: return
  val intent =
    Intent(Intent.ACTION_VIEW, url.toUri()).apply {
      // Started from an application context, so it needs its own task.
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  try {
    context.startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    // A device with no browser at all; nothing useful to do but not crash.
  }
}
