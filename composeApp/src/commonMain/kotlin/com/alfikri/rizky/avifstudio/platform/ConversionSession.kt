package com.alfikri.rizky.avifstudio.platform

/** Already-localised text for a notification. */
data class SessionText(val title: String, val body: String)

/** Localised notification-channel copy. Android only; ignored elsewhere. */
data class SessionChannel(val name: String, val description: String)

/**
 * Keeps a running batch alive while the user is elsewhere, and tells them when it is done.
 *
 * Driven from the batch coroutine, never from composition. Those are different lifetimes on both
 * platforms and tying them together was wrong in two directions: on Android the composition dies
 * when the task is swiped away while the service lives on, leaving an undismissable notification
 * for work that stopped; on iOS recomposition pauses in the background, so the batch finished but
 * `finish()` never ran and the completion notification arrived only once the user reopened the app,
 * where the system drops it.
 *
 * What each platform can promise differs, and this does not pretend otherwise:
 * - **Android** runs a `dataSync` foreground service with an ongoing progress notification. The
 *   batch genuinely survives the user leaving the app.
 * - **iOS** has no equivalent — no foreground service, and ongoing progress needs a Live Activity
 *   with its own extension and entitlement. `beginBackgroundTask` buys a short grace period, so
 *   [update] is deliberately a no-op and the user gets a notification when the work lands.
 */
// The members are repeated here because an expect class does not inherit its supertype's abstract
// members — without them the common metadata compilation rejects the class as unimplemented.
expect class ConversionSession() : BatchLifecycle {
  override fun start(text: SessionText, channel: SessionChannel)

  override fun update(completed: Int, total: Int, text: SessionText)

  override fun finish(completion: SessionText?)
}

/**
 * What the ViewModel needs from a session, so the batch state machine stays testable off-device —
 * constructing the real one in a host test would reach for an Android Context or UIApplication.
 */
interface BatchLifecycle {

  /** Called as a batch starts. Safe to call twice. */
  fun start(text: SessionText, channel: SessionChannel)

  /** Ongoing progress. A no-op where the platform cannot show any. */
  fun update(completed: Int, total: Int, text: SessionText)

  /**
   * The batch ended. [completion] is `null` when nothing worth announcing happened — the user
   * cancelled, or nothing succeeded — in which case the ongoing notification is torn down without
   * posting a replacement.
   */
  fun finish(completion: SessionText?)
}

/**
 * Asks for notification permission if the platform needs it.
 *
 * Requested when the first images are added rather than when Convert is tapped: a short batch can
 * finish while the system dialog is still up, and the completion notification is then posted before
 * authorization exists and silently dropped.
 */
@androidx.compose.runtime.Composable expect fun rememberNotificationPermissionRequest(): () -> Unit
