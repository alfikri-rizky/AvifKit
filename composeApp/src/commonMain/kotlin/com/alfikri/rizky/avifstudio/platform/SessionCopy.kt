package com.alfikri.rizky.avifstudio.platform

import com.alfikri.rizky.avifstudio.model.formatBytes
import com.alfikri.rizky.avifstudio.resources.Res
import com.alfikri.rizky.avifstudio.resources.notif_cancelled_title
import com.alfikri.rizky.avifstudio.resources.notif_channel_desc
import com.alfikri.rizky.avifstudio.resources.notif_channel_name
import com.alfikri.rizky.avifstudio.resources.notif_done_body
import com.alfikri.rizky.avifstudio.resources.notif_done_body_plain
import com.alfikri.rizky.avifstudio.resources.notif_done_title
import com.alfikri.rizky.avifstudio.resources.notif_running_title
import com.alfikri.rizky.avifstudio.resources.progress_of
import org.jetbrains.compose.resources.getString

/**
 * The localised words a [BatchLifecycle] shows.
 *
 * Split out from the batch runner because Compose Resources cannot resolve a string without a real
 * platform behind it — on the JVM host `getString` throws `Resources.getSystem not mocked`. Left
 * inline, that turned every notification lookup into something that could abort a conversion under
 * test, and made the batch state machine untestable off-device.
 */
interface SessionCopy {

  suspend fun channel(): SessionChannel

  suspend fun running(completed: Int, total: Int): SessionText

  suspend fun finished(succeeded: Int, savedBytes: Long): SessionText

  suspend fun cancelled(completed: Int, total: Int): SessionText
}

class ResourceSessionCopy : SessionCopy {

  override suspend fun channel() =
    SessionChannel(
      name = getString(Res.string.notif_channel_name),
      description = getString(Res.string.notif_channel_desc),
    )

  override suspend fun running(completed: Int, total: Int) =
    SessionText(
      title = getString(Res.string.notif_running_title),
      body = getString(Res.string.progress_of, completed, total),
    )

  override suspend fun finished(succeeded: Int, savedBytes: Long) =
    SessionText(
      title = getString(Res.string.notif_done_title),
      body =
        // "0 B saved" reads as a failure. When nothing was gained, say only what was done.
        if (savedBytes > 0) {
          getString(Res.string.notif_done_body, succeeded, formatBytes(savedBytes))
        } else {
          getString(Res.string.notif_done_body_plain, succeeded)
        },
    )

  override suspend fun cancelled(completed: Int, total: Int) =
    SessionText(
      title = getString(Res.string.notif_cancelled_title),
      body = getString(Res.string.progress_of, completed, total),
    )
}
