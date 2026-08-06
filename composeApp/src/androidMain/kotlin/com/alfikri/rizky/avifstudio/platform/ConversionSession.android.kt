package com.alfikri.rizky.avifstudio.platform

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.notif_channel_desc
import com.alfikri.rizky.avifstudio.resources.notif_channel_name
import org.jetbrains.compose.resources.stringResource

actual class ConversionSession(
  private val context: Context,
  private val channelName: String,
  private val channelDescription: String,
) {

  actual fun start(text: SessionText) {
    ConversionForegroundService.start(
      context = context,
      title = text.title,
      body = text.body,
      completed = 0,
      total = 0,
      channelName = channelName,
      channelDescription = channelDescription,
    )
  }

  actual fun update(completed: Int, total: Int, text: SessionText) {
    ConversionForegroundService.start(
      context = context,
      title = text.title,
      body = text.body,
      completed = completed,
      total = total,
      channelName = channelName,
      channelDescription = channelDescription,
    )
  }

  actual fun finish(completion: SessionText?) {
    ConversionForegroundService.stop(context)
    if (completion != null) {
      ConversionForegroundService.notifyDone(
        context = context,
        title = completion.title,
        body = completion.body,
        channelName = channelName,
        channelDescription = channelDescription,
      )
    }
  }
}

@Composable
actual fun rememberConversionSession(): ConversionSession {
  val context = LocalContext.current
  val channelName = stringResource(Res.string.notif_channel_name)
  val channelDescription = stringResource(Res.string.notif_channel_desc)
  return remember(context, channelName, channelDescription) {
    ConversionSession(context, channelName, channelDescription)
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
