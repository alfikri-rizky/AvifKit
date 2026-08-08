package com.alfikri.rizky.avifstudio.platform

/** Already-localised text for a notification. */
data class SessionText(val title: String, val body: String)

/** Localised notification-channel copy. Android only; ignored elsewhere. */
data class SessionChannel(val name: String, val description: String)

/**
 * Keeps a running batch alive while the user is elsewhere, and tells them when it is done.
 *
 * Driven from the batch coroutine, never from composition. Those have different lifetimes, and
 * tying them together broke both ways: on Android a swiped-away task left an undismissable
 * notification for work that had stopped, and on iOS `finish()` never ran because recomposition
 * pauses in the background.
 *
 * Android backs this with a `dataSync` foreground service, so the batch survives leaving the app.
 * iOS has no equivalent — [update] is deliberately a no-op there and only the completion
 * notification is promised.
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

  /** Safe to call twice. */
  fun start(text: SessionText, channel: SessionChannel)

  /** A no-op where the platform cannot show ongoing progress. */
  fun update(completed: Int, total: Int, text: SessionText)

  /**
   * The batch ended. [completion] is `null` when nothing worth announcing happened — the user
   * cancelled, or nothing succeeded — in which case the ongoing notification is torn down without
   * posting a replacement.
   */
  fun finish(completion: SessionText?)
}

/**
 * Requested when the first images are added rather than when Convert is tapped: a short batch can
 * finish while the system dialog is still up, and the completion notification is then posted before
 * authorization exists and silently dropped.
 */
@androidx.compose.runtime.Composable expect fun rememberNotificationPermissionRequest(): () -> Unit
