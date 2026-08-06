package com.alfikri.rizky.avifstudio.platform

import androidx.compose.runtime.Composable

/** Already-localised text for a notification. Resolved in composition, shown by platform code. */
data class SessionText(val title: String, val body: String)

/**
 * Keeps a running batch alive while the user is elsewhere, and tells them when it is done.
 *
 * The two platforms can promise very different things here, and the API deliberately does not
 * pretend otherwise:
 *
 * - **Android** starts a `dataSync` foreground service with an ongoing progress notification. The
 *   batch survives the user leaving the app, and the notification is what buys that — a foreground
 *   service without one is not a thing the OS allows.
 * - **iOS** has no equivalent. There is no foreground service and no ongoing progress notification
 *   outside a Live Activity. The best available is `beginBackgroundTask`, which asks the system for
 *   a short grace period to finish what is already in flight; a long batch will still be suspended.
 *   A local notification is delivered when the work completes.
 *
 * So on Android this is "your batch keeps running", and on iOS it is "your batch gets a little
 * longer to finish, and you get told when it does".
 */
expect class ConversionSession {

  /** Called when a batch starts, while the app is still in the foreground. */
  fun start(text: SessionText)

  /** Progress for the ongoing notification. Ignored where there is nothing to update. */
  fun update(completed: Int, total: Int, text: SessionText)

  /**
   * The batch ended. [completion] is `null` when it was cancelled or nothing succeeded, in which
   * case no "finished" notification is posted — a notification for work the user themselves stopped
   * is noise.
   */
  fun finish(completion: SessionText?)
}

@Composable expect fun rememberConversionSession(): ConversionSession

/**
 * Asks for notification permission if the platform needs it and has not been asked.
 *
 * Android 13+ gates notifications behind a runtime permission, and a foreground service without a
 * visible notification is much easier for the system to kill. iOS gates local notifications the
 * same way.
 */
@Composable expect fun rememberNotificationPermissionRequest(): () -> Unit
