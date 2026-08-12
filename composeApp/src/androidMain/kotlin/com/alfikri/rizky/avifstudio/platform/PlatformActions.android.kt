package com.alfikri.rizky.avifstudio.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.alfikri.rizky.avifkit.PlatformFile
import com.alfikri.rizky.avifstudio.model.OutputFormat
import com.alfikri.rizky.avifstudio.settings.SaveLocation
import com.anggrayudi.storage.StorageFile
import com.anggrayudi.storage.copyToFile
import com.anggrayudi.storage.file.CreateMode
import com.anggrayudi.storage.toStorageFile
import com.anggrayudi.storage.transfer.TransferResult
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.name
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual class PlatformActions(
  private val context: Context,
  private val scope: CoroutineScope,
  private val pending: MutableList<PlatformFile>,
  private val defaultSaveLocation: () -> SaveLocation?,
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
    val remembered = defaultSaveLocation()
    if (remembered == null) {
      askWhereToPut(files)
      return
    }
    scope.launch {
      val folder = withContext(Dispatchers.IO) { remembered.resolve(context) }
      if (folder == null) {
        // Deleted, unmounted, or the grant is gone. Asking is better than reporting a failure
        // against a folder the user has no way of knowing is no longer reachable. The setting is
        // deliberately left alone: an SD card that is out today is back tomorrow, and clearing it
        // would quietly cost the user a choice they never revoked.
        askWhereToPut(files)
        return@launch
      }
      onExportResult(withContext(Dispatchers.IO) { writeInto(folder, files) })
    }
  }

  private fun askWhereToPut(files: List<PlatformFile>) {
    pending.clear()
    pending.addAll(files)
    launchTreePicker()
  }

  actual val supportsNativeAvifDecoding: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

  // Bitmap.CompressFormat is JPEG/PNG/WEBP* only, still through compileSdk 36 — there is no
  // still-image AVIF encoder on this platform to query for.
  actual val supportsNativeAvifEncoding: Boolean
    get() = false

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
          // Wrapped straight from the tree URI rather than resolved through
          // StorageFile.from(): that goes via DocumentFileCompat.fromUri(), which special-cases
          // the Downloads provider and answers null for any tree URI whose path is not one of
          // the shapes it knows. The picker opens in Downloads by design (see initialTreeUri),
          // so routing this through it failed the most ordinary save there is.
          val folder =
            DocumentFile.fromTreeUri(context, treeUri)?.toStorageFile(context)
              ?: return@withContext ExportResult.Failed("That folder is not writable")
          writeInto(folder, files)
        }
      onExportResult(result)
    }
  }

  /**
   * Copies the batch into [folder], one file at a time so a failure names the file it happened on.
   *
   * The target document is created first with a MIME type we choose, and only then filled: handed a
   * type it does not recognise, a SAF provider is free to rename `photo.avif` to something whose
   * extension matches what it thinks the file is.
   */
  private suspend fun writeInto(folder: StorageFile, files: List<PlatformFile>): ExportResult =
    try {
      var written = 0
      for (file in files) {
        val name = file.name
        val source =
          file.asStorageFile(context) ?: return ExportResult.Failed("Could not read $name")
        // REPLACE rather than let the provider append " (1)": the user pointed at this folder to
        // collect a specific batch, and duplicates on re-save are just clutter.
        val target =
          folder.createFile(name, mimeTypeFor(name), CreateMode.REPLACE)
            ?: return ExportResult.Failed("Could not create $name")
        when (val result = source.copyToFile(target)) {
          is TransferResult.Success -> written++
          is TransferResult.Skipped -> Unit
          is TransferResult.Failure ->
            return ExportResult.Failed(result.message ?: "Could not write $name")
        }
      }
      ExportResult.Saved(written, folder.absolutePath ?: folder.name)
    } catch (error: Exception) {
      ExportResult.Failed(error.message ?: "Could not save these files")
    }

  /**
   * The app's own output formats are answered from [OutputFormat] rather than the platform map:
   * `image/avif` only reached Android's MIME map in 13, and everything below that would otherwise
   * write AVIF files as `application/octet-stream`. Anything else here is a source kept as-is.
   */
  private fun mimeTypeFor(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase()
    return OutputFormat.entries.firstOrNull { it.extension == extension }?.mimeType
      ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
      ?: "application/octet-stream"
  }

  private fun PlatformFile.asStorageFile(context: Context): StorageFile? =
    when (val android = androidFile) {
      is AndroidFile.UriWrapper -> StorageFile.from(context, android.uri)
      is AndroidFile.FileWrapper -> StorageFile.from(context, android.file)
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
actual fun rememberPlatformActions(
  defaultSaveLocation: SaveLocation?,
  onExportResult: (ExportResult) -> Unit,
): PlatformActions {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val pending = remember { mutableListOf<PlatformFile>() }
  // Read through, not captured: keying the instance on the location instead would rebuild it —
  // and the pending list with it — the moment Settings changed the folder.
  val currentSaveLocation = rememberUpdatedState(defaultSaveLocation)
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
        defaultSaveLocation = { currentSaveLocation.value },
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
