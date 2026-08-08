package com.alfikri.rizky.avifstudio

import androidx.compose.ui.window.ComposeUIViewController
import com.alfikri.rizky.avifkit.PlatformFile
import com.alfikri.rizky.avifstudio.platform.IncomingFiles
import com.alfikri.rizky.avifstudio.ui.App
import platform.Foundation.NSURL
import platform.UIKit.UIViewController

/** The whole iOS UI. `iOSApp.swift` wraps this in a `UIViewControllerRepresentable`. */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }

/**
 * Entry point for files opened from outside the app (Files, Safari downloads, another app's share
 * sheet). Swift calls this from `onOpenURL`.
 */
fun handleIncomingUrl(url: NSURL) {
  IncomingFiles.offer(listOf(PlatformFile(url)))
}
