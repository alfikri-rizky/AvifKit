package com.alfikri.rizky.avifkit

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSClassFromString
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIImage

/**
 * iOS-specific native AVIF handler interface. Implemented by the Swift AvifKitNativeHandler class.
 * Registered at app startup via AvifKitIos.registerHandler(), or auto-discovered lazily the first
 * time a conversion is attempted.
 *
 * This solves the architecture problem where Kotlin/Native cannot directly call Swift classes
 * compiled in a separate SPM target. Instead, the Swift side registers a handler implementation
 * that the Kotlin code calls through.
 */
interface IosAvifNativeHandler {
  fun isAvailable(): Boolean

  fun encodeImageWithOptions(image: UIImage, options: NSDictionary): NSData?

  fun decodeAvif(avifData: NSData): UIImage?

  fun getVersion(): String
}

/**
 * iOS-specific registry for native AVIF handler.
 *
 * The library attempts to auto-discover the Swift handler via Objective-C runtime reflection the
 * first time a conversion is attempted. You do NOT need to call [registerHandler] manually — but
 * you CAN if you prefer explicit setup.
 *
 * Manual registration from Swift (optional):
 * ```swift
 * import Shared
 * import AvifKit
 *
 * // In your app's init:
 * AvifKitSetup.registerNativeHandler()
 * ```
 */
object AvifKitIos {
  private var handler: IosAvifNativeHandler? = null

  fun registerHandler(handler: IosAvifNativeHandler) {
    this.handler = handler
  }

  fun getHandler(): IosAvifNativeHandler? = handler

  fun isNativeAvifAvailable(): Boolean = handler?.isAvailable() == true

  /**
   * Returns the registered handler, or attempts lazy auto-discovery via Objective-C runtime if none
   * has been registered yet.
   *
   * This is reliable because by the time the first conversion is called, all Swift code in the app
   * binary is fully loaded and ObjC-registered.
   *
   * Looks up:
   * - "AvifKit.AvifKitSetup" (module-prefixed, used by Xcode 15+)
   * - "AvifKitSetup" (non-prefixed, used by older Xcode)
   */
  @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
  fun getOrDiscoverHandler(): IosAvifNativeHandler? {
    // Fast path: already registered
    handler?.let {
      return it
    }

    // Slow path: try to auto-discover via ObjC runtime
    // NSClassFromString returns ObjCClass? which is an NSObject meta-class
    val cls = NSClassFromString("AvifKit.AvifKitSetup") ?: NSClassFromString("AvifKitSetup")

    if (cls != null) {
      val sel = NSSelectorFromString("registerNativeHandler")
      @Suppress("UNCHECKED_CAST") val metaObj = cls as platform.darwin.NSObject
      if (metaObj.respondsToSelector(sel)) {
        metaObj.performSelector(sel)
      }
    }

    // Return whatever was registered (may still be null if Swift target not linked)
    return handler
  }
}
