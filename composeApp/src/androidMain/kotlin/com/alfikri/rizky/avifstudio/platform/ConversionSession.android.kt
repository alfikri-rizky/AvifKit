package com.alfikri.rizky.avifstudio.platform

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class ConversionSession : BatchLifecycle {

  private var channel: SessionChannel? = null
  private var started = false

  override fun start(text: SessionText, channel: SessionChannel) {
    this.channel = channel
    val context = AppContext.applicationContext ?: return
    started = true
    ConversionForegroundService.start(
      context = context,
      title = text.title,
      body = text.body,
      completed = 0,
      total = 0,
      channelName = channel.name,
      channelDescription = channel.description,
    )
  }

  /**
   * Updates the existing notification rather than restarting the service. The previous version
   * called startForegroundService once per converted image — a full ActivityManager round-trip for
   * something NotificationManager can do directly.
   */
  override fun update(completed: Int, total: Int, text: SessionText) {
    if (!started) return
    val context = AppContext.applicationContext ?: return
    val channel = channel ?: return
    ConversionForegroundService.updateProgress(
      context = context,
      title = text.title,
      body = text.body,
      completed = completed,
      total = total,
      channelName = channel.name,
      channelDescription = channel.description,
    )
  }

  override fun finish(completion: SessionText?) {
    val context = AppContext.applicationContext ?: return
    // Nothing to tear down if we never started; stopping anyway spun up a Service on every cold
    // launch just to shut it down again.
    if (!started) return
    started = false
    ConversionForegroundService.stop(context)
    val channel = channel ?: return
    if (completion != null) {
      ConversionForegroundService.notifyDone(
        context = context,
        title = completion.title,
        body = completion.body,
        channelName = channel.name,
        channelDescription = channel.description,
      )
    }
  }

  internal companion object {
    const val TAG = "AvifStudio"

    /** A refused foreground service is the one failure that must not be silent. */
    fun logStartFailure(error: Throwable) {
      Log.w(TAG, "Foreground service refused; the batch will run unprotected", error)
    }
  }
}

@Composable
actual fun rememberNotificationPermissionRequest(): () -> Unit {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return remember { {} }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      // Declined just means no progress notification. The conversion is unaffected, so there is
      // nothing to tell the user and nothing to retry.
    }
  return remember(launcher) { { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) } }
}
