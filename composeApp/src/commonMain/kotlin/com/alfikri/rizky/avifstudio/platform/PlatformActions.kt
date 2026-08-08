package com.alfikri.rizky.avifstudio.platform

import androidx.compose.runtime.Composable
import com.alfikri.rizky.avifkit.PlatformFile

sealed interface ExportResult {
  data class Saved(val count: Int, val location: String) : ExportResult

  data object Cancelled : ExportResult

  data class Failed(val message: String) : ExportResult
}

/**
 * Both exits are permission-free by design: sharing goes through a FileProvider grant on Android
 * and the activity sheet on iOS, and saving asks the user to point at a destination rather than
 * claiming broad storage access.
 */
expect class PlatformActions {

  fun share(files: List<PlatformFile>, mimeType: String)

  /**
   * Asks the user where to put [files], then writes them all there.
   *
   * TODO: replace the destination step with a remembered folder selector once SimpleStorage
   *   (https://github.com/anggrayudi/SimpleStorage) ships Kotlin Multiplatform support. Today the
   *   user picks a destination on every export, because each platform's own picker is one-shot:
   *   Android returns a SAF tree URI we do not persist, and iOS's export sheet has no concept of a
   *   remembered folder at all. SimpleStorage already solves the Android half — persisted tree
   *   permissions, storage-access lifecycle, safe file writes — and a KMM version would let this
   *   become "convert straight into the folder you chose last time", which is what a batch tool
   *   should do. The KMM work is still in progress upstream, so this stays per-export for now.
   */
  fun export(files: List<PlatformFile>)

  /**
   * Whether the OS itself can display AVIF — Android 12+ and iOS 16+. False means this app is the
   * only way to look at those files on this device, which is worth telling the user.
   */
  val supportsNativeAvifRendering: Boolean
}

@Composable
expect fun rememberPlatformActions(onExportResult: (ExportResult) -> Unit): PlatformActions
