@file:OptIn(ExperimentalForeignApi::class)

package com.alfikri.rizky.avifstudio.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.alfikri.rizky.avifkit.PlatformFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
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

actual class PlatformActions(private val onExportResult: (ExportResult) -> Unit) {

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

  /**
   * The Files "export" sheet, which is the iOS equivalent of Android's folder picker: the user
   * chooses a destination once and every file in the batch is copied there.
   */
  actual fun export(files: List<PlatformFile>) {
    if (files.isEmpty()) {
      onExportResult(ExportResult.Cancelled)
      return
    }
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
actual fun rememberPlatformActions(onExportResult: (ExportResult) -> Unit): PlatformActions =
  remember(onExportResult) { PlatformActions(onExportResult) }

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
