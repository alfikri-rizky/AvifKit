package com.alfikri.rizky.avifstudio.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.alfikri.rizky.avifkit.PlatformFile
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual class PlatformActions(
  private val context: Context,
  private val scope: CoroutineScope,
  private val pending: MutableList<PlatformFile>,
  private val launchTreePicker: () -> Unit,
  private val onExportResult: (ExportResult) -> Unit,
) {

  actual fun share(files: List<PlatformFile>, mimeType: String) {
    if (files.isEmpty()) return
    val uris =
      try {
        ArrayList(files.map { it.shareUri(context) })
      } catch (error: Exception) {
        onExportResult(ExportResult.Failed(error.message ?: "Could not prepare these files"))
        return
      }

    val intent =
      if (uris.size == 1) {
          Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.first()) }
        } else {
          Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
          }
        }
        .apply {
          type = mimeType
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          // The chooser is what gets started from a non-Activity context, so it needs the flag.
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    try {
      context.startActivity(
        Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    } catch (_: ActivityNotFoundException) {
      onExportResult(ExportResult.Failed("No app can receive these files"))
    }
  }

  actual fun export(files: List<PlatformFile>) {
    if (files.isEmpty()) {
      onExportResult(ExportResult.Cancelled)
      return
    }
    pending.clear()
    pending.addAll(files)
    launchTreePicker()
  }

  actual val supportsNativeAvifRendering: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  internal fun writePendingInto(treeUri: Uri?) {
    val files = pending.toList()
    pending.clear()
    if (treeUri == null) {
      onExportResult(ExportResult.Cancelled)
      return
    }
    scope.launch {
      val result =
        withContext(Dispatchers.IO) {
          try {
            val directory =
              DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext ExportResult.Failed("That folder is not writable")
            var written = 0
            for (file in files) {
              val bytes = file.readBytes()
              val name = file.name
              // Replace rather than let the provider append " (1)": the user picked this folder
              // to collect a specific batch, and duplicates on re-save are just clutter.
              directory.findFile(name)?.takeIf { it.isFile }?.delete()
              val target =
                directory.createFile(mimeTypeFor(name), name)
                  ?: return@withContext ExportResult.Failed("Could not create $name")
              context.contentResolver.openOutputStream(target.uri)?.use { it.write(bytes) }
                ?: return@withContext ExportResult.Failed("Could not write $name")
              written++
            }
            ExportResult.Saved(written, directory.displayName())
          } catch (error: Exception) {
            ExportResult.Failed(error.message ?: "Could not save these files")
          }
        }
      onExportResult(result)
    }
  }

  private fun DocumentFile.displayName(): String =
    name ?: uri.lastPathSegment ?: "the chosen folder"

  private fun mimeTypeFor(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
      "avif" -> "image/avif"
      "jpg",
      "jpeg" -> "image/jpeg"
      "png" -> "image/png"
      else -> "application/octet-stream"
    }

  /**
   * A URI other apps may read. Files the converter wrote live in our cache directory and need a
   * FileProvider grant; anything already content-backed (a file the user picked) is passed through
   * as-is, since re-wrapping a foreign URI in our own provider is not possible.
   */
  private fun PlatformFile.shareUri(context: Context): Uri =
    when (val android = androidFile) {
      is AndroidFile.UriWrapper -> android.uri
      is AndroidFile.FileWrapper ->
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", android.file)
    }
}

@Composable
actual fun rememberPlatformActions(onExportResult: (ExportResult) -> Unit): PlatformActions {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val pending = remember { mutableListOf<PlatformFile>() }
  // Held in a box so the launcher callback can reach the instance that is created just below it.
  val holder = remember { arrayOfNulls<PlatformActions>(1) }

  val treePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      holder[0]?.writePendingInto(uri)
    }

  return remember(context, scope, treePicker, onExportResult) {
    PlatformActions(
        context = context,
        scope = scope,
        pending = pending,
        launchTreePicker = { treePicker.launch(initialTreeUri()) },
        onExportResult = onExportResult,
      )
      .also { holder[0] = it }
  }
}

/**
 * Opens the picker in Downloads where possible, since that is where most people expect converted
 * files to land. Only a hint — the user can navigate anywhere.
 */
private fun initialTreeUri(): Uri? =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    DocumentsContract.buildDocumentUri("com.android.providers.downloads.documents", "downloads")
  } else {
    null
  }
