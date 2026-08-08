package com.alfikri.rizky.avifstudio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.alfikri.rizky.avifstudio.platform.IncomingFiles
import com.alfikri.rizky.avifstudio.ui.App
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.init

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    // Registers the ActivityResultRegistry FileKit's pickers and savers launch through.
    FileKit.init(this)

    // Only consume the launch intent on a fresh start. On a configuration change onCreate runs
    // again with the same intent, which would re-add the same shared images every rotation.
    if (savedInstanceState == null) consumeIntent(intent)

    setContent { App() }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    consumeIntent(intent)
  }

  /** Turns an "Open with" or "Share to" intent into files the shared UI can pick up. */
  private fun consumeIntent(intent: Intent?) {
    if (intent == null) return
    val uris =
      when (intent.action) {
        Intent.ACTION_VIEW -> listOfNotNull(intent.data)
        Intent.ACTION_SEND ->
          listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
          IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            .orEmpty()
        else -> emptyList()
      }
    if (uris.isEmpty()) return

    // The grant that came with the intent dies with this task; taking a persistable copy is not
    // possible for a plain SEND, so the files are read straight away by the converter instead.
    IncomingFiles.offer(uris.map { PlatformFile(it) })
  }
}
