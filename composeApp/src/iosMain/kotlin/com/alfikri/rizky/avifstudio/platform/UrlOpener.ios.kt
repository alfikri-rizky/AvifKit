package com.alfikri.rizky.avifstudio.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openUrl(url: String) {
  val nsUrl = NSURL.URLWithString(url) ?: return
  val application = UIApplication.sharedApplication
  if (!application.canOpenURL(nsUrl)) return
  application.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
}
