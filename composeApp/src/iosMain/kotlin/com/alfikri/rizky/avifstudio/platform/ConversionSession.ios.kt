package com.alfikri.rizky.avifstudio.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS has no foreground service, and no ongoing progress notification short of a Live Activity
 * (which needs a separate widget extension and its own entitlement). What it does have is
 * `beginBackgroundTask`: a request for a few minutes of grace to finish work already in flight
 * after the user leaves the app.
 *
 * So [update] is a no-op here, deliberately. Pretending to show progress the platform cannot show
 * would be worse than showing nothing. What the user gets is: the batch keeps going for a while
 * after they switch away, and a notification when it finishes.
 */
actual class ConversionSession : BatchLifecycle {

  private var task: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid

  override fun start(text: SessionText, channel: SessionChannel) {
    if (task != UIBackgroundTaskInvalid) return
    task =
      UIApplication.sharedApplication.beginBackgroundTaskWithName("avif-conversion") {
        // The system is out of patience. Release the assertion or it kills the app outright.
        endTask()
      }
  }

  /** No-op: iOS cannot show ongoing progress without a Live Activity. */
  override fun update(completed: Int, total: Int, text: SessionText) = Unit

  override fun finish(completion: SessionText?) {
    if (completion != null) postNotification(completion)
    endTask()
  }

  private fun endTask() {
    val current = task
    if (current == UIBackgroundTaskInvalid) return
    task = UIBackgroundTaskInvalid
    UIApplication.sharedApplication.endBackgroundTask(current)
  }

  private fun postNotification(text: SessionText) {
    val content =
      UNMutableNotificationContent().apply {
        setTitle(text.title)
        setBody(text.body)
      }
    // No trigger: deliver immediately. iOS suppresses the banner while the app is in the
    // foreground anyway, which is the behaviour we want — the user is already looking at it.
    val request =
      UNNotificationRequest.requestWithIdentifier(
        identifier = NSUUID().UUIDString,
        content = content,
        trigger = null,
      )
    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
  }
}

@Composable
actual fun rememberNotificationPermissionRequest(): () -> Unit = remember {
  {
    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
      UNAuthorizationOptionAlert or UNAuthorizationOptionSound
    ) { _, _ ->
      // Declined only costs the completion notification; the conversion is unaffected.
    }
  }
}
