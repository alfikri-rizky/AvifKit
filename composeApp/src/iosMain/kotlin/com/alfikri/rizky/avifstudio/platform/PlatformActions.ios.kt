@file:OptIn(ExperimentalForeignApi::class)

package com.alfikri.rizky.avifstudio.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.alfikri.rizky.avifkit.PlatformFile
import com.alfikri.rizky.avifstudio.settings.SaveLocation
import io.github.vinceglb.filekit.copyTo
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.name
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.ImageIO.CGImageDestinationCopyTypeIdentifiers
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIModalPresentationPopover
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.darwin.NSObject

actual class PlatformActions(
  private val scope: CoroutineScope,
  private val defaultSaveLocation: () -> SaveLocation?,
  private val onExportResult: (ExportResult) -> Unit,
) {

  actual fun share(files: List<PlatformFile>, mimeType: String) {
    if (files.isEmpty()) return
    val controller =
      UIActivityViewController(activityItems = files.map { it.nsUrl }, applicationActivities = null)

    val presenter = rootViewController() ?: return
    // On iPad an activity sheet with no anchor crashes UIKit outright; anchoring it to the
    // presenting view puts the popover in the middle of the screen instead.
    controller.popoverPresentationController?.sourceView = presenter.view
    controller.modalPresentationStyle = UIModalPresentationPopover
    presenter.presentViewController(controller, animated = true, completion = null)
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
      val result = remembered.useFolder { folder -> writeInto(folder, files) }
      // Gone, moved, or the grant behind the bookmark is no longer honoured. Asking beats
      // reporting a failure against a folder the user cannot see is unreachable, and the setting
      // is deliberately left alone: an unmounted external drive is back tomorrow, and clearing it
      // would quietly cost a choice they never revoked.
      if (result == null) askWhereToPut(files) else onExportResult(result)
    }
  }

  /**
   * Copies the batch into [folder], one file at a time so a failure names the file it happened on.
   *
   * Existing files are overwritten rather than given a numbered suffix: the user pointed at this
   * folder to collect a specific batch, and duplicates on re-save are just clutter. This matches
   * what Android does with `CreateMode.REPLACE`.
   */
  private suspend fun writeInto(folder: PlatformFile, files: List<PlatformFile>): ExportResult {
    var written = 0
    for (file in files) {
      val target = folder / file.name
      if (runCatching { file copyTo target }.isFailure) {
        // Leave nothing half-written: an empty .avif in the user's folder looks like a file that
        // saved, and it is the first thing they would open.
        runCatching { target.delete(mustExist = false) }
        return ExportResult.Failed("Could not write ${file.name}")
      }
      written++
    }
    return ExportResult.Saved(written, folder.name)
  }

  /**
   * The Files "export" sheet: the user chooses a destination once and every file in the batch is
   * copied there. Unlike a bookmarked folder this grants nothing that outlives the sheet, so it
   * stays the fallback rather than the remembered path.
   */
  private fun askWhereToPut(files: List<PlatformFile>) {
    val presenter =
      rootViewController()
        ?: run {
          onExportResult(ExportResult.Failed("No window to present from"))
          return
        }

    val picker =
      UIDocumentPickerViewController(forExportingURLs = files.map { it.nsUrl }, asCopy = true)
    val delegate =
      ExportDelegate(expected = files.size) { result ->
        activeDelegates.removeAll { it === this }
        onExportResult(result)
      }
    // UIKit holds the delegate weakly, so without this it is collected the moment this function
    // returns and the callback never fires.
    activeDelegates.add(delegate)
    picker.delegate = delegate
    presenter.presentViewController(picker, animated = true, completion = null)
  }

  /** AVIF decoding arrived in iOS 16; the app's minimum is lower, so this is a real question. */
  actual val supportsNativeAvifDecoding: Boolean
    get() = isAtLeastIos16()

  /**
   * Asked of ImageIO rather than pinned to an OS version. A current iOS does list `public.avif`
   * here (measured on the iOS 26 simulator), but the read and write sides did not arrive together
   * and an older one need not, so the honest answer is whatever ImageIO reports today.
   */
  actual val supportsNativeAvifEncoding: Boolean
    get() =
      (CFBridgingRelease(CGImageDestinationCopyTypeIdentifiers()) as? List<*>)?.contains(
        AVIF_UTI
      ) == true
}

@Composable
actual fun rememberPlatformActions(
  defaultSaveLocation: SaveLocation?,
  onExportResult: (ExportResult) -> Unit,
): PlatformActions {
  val scope = rememberCoroutineScope()
  // Read through, not captured: keying the instance on the location instead would rebuild it the
  // moment Settings changed the folder.
  val currentSaveLocation = rememberUpdatedState(defaultSaveLocation)
  return remember(scope, onExportResult) {
    PlatformActions(
      scope = scope,
      defaultSaveLocation = { currentSaveLocation.value },
      onExportResult = onExportResult,
    )
  }
}

private val activeDelegates = mutableListOf<NSObject>()

private class ExportDelegate(
  private val expected: Int,
  private val onFinished: ExportDelegate.(ExportResult) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

  override fun documentPicker(
    controller: UIDocumentPickerViewController,
    didPickDocumentsAtURLs: List<*>,
  ) {
    val urls = didPickDocumentsAtURLs.filterIsInstance<NSURL>()
    val folder = urls.firstOrNull()?.URLByDeletingLastPathComponent?.lastPathComponent ?: "Files"
    onFinished(ExportResult.Saved(urls.size.takeIf { it > 0 } ?: expected, folder))
  }

  override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
    onFinished(ExportResult.Cancelled)
  }
}

private fun rootViewController(): UIViewController? {
  val scenes = UIApplication.sharedApplication.connectedScenes
  for (scene in scenes) {
    val windowScene = scene as? UIWindowScene ?: continue
    if (windowScene.activationState != UISceneActivationStateForegroundActive) continue
    val keyWindow =
      windowScene.windows.filterIsInstance<UIWindow>().firstOrNull { it.isKeyWindow() }
    val root =
      keyWindow?.rootViewController
        ?: (windowScene.windows.firstOrNull() as? UIWindow)?.rootViewController
    if (root != null) return root.topMost()
  }
  return UIApplication.sharedApplication.keyWindow?.rootViewController?.topMost()
}

/** Presenting from a controller that already has something on top of it silently does nothing. */
private fun UIViewController.topMost(): UIViewController =
  presentedViewController?.topMost() ?: this

private const val AVIF_UTI = "public.avif"

private fun isAtLeastIos16(): Boolean {
  val version = NSProcessInfo.processInfo.operatingSystemVersion
  return version.useContents { majorVersion >= 16 }
}
