package com.alfikri.rizky.avifkit

import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.UIKit.UIImage

/**
 * iOS-specific native AVIF handler interface.
 * Implemented by the Swift AvifKitNativeHandler class.
 * Registered at app startup via AvifKitIos.registerHandler().
 *
 * This solves the architecture problem where Kotlin/Native cannot directly
 * call Swift classes compiled in a separate SPM target. Instead, the Swift
 * side registers a handler implementation that the Kotlin code calls through.
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
 * Call [registerHandler] at app startup to enable native AVIF support.
 *
 * Usage from Swift:
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
}
