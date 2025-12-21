package com.alfikri.rizky.avifkit

import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.UIKit.UIImage

/**
 * Stub class for AVIFNativeConverter
 *
 * The actual Swift implementation will be linked by Xcode when building the iOS app.
 * This stub allows the Kotlin framework to compile independently.
 *
 * **Setup Instructions:**
 * 1. Add `iosApp/iosApp/Native/AVIFNativeConverter.swift` to your Xcode project
 * 2. Build the iOS app - Xcode will link the Swift implementation
 * 3. The Swift class will replace this stub at runtime
 *
 * See SWIFT_INTEGRATION_SETUP.md for detailed instructions.
 */
class AVIFNativeConverter {

    companion object {
        /**
         * Check if libavif is available
         * Note: This is a stub - actual implementation is in Swift
         */
        fun isAvifAvailable(): Boolean {
            // Will be replaced by Swift implementation at runtime
            return false
        }

        /**
         * Get libavif version
         * Note: This is a stub - actual implementation is in Swift
         */
        fun avifVersion(): String {
            // Will be replaced by Swift implementation at runtime
            return "Swift implementation not linked"
        }
    }

    /**
     * Encode UIImage to AVIF format
     * Note: This is a stub - actual implementation is in Swift
     */
    fun encodeImage(image: UIImage, quality: Int, speed: Int): NSData? {
        // Will be replaced by Swift implementation at runtime
        return null
    }

    /**
     * Encode with detailed options
     * Note: This is a stub - actual implementation is in Swift
     */
    fun encodeImageWithOptions(image: UIImage, options: NSDictionary): NSData? {
        // Will be replaced by Swift implementation at runtime
        return null
    }

    /**
     * Decode AVIF data to UIImage
     * Note: This is a stub - actual implementation is in Swift
     */
    fun decodeAvif(avifData: NSData): UIImage? {
        // Will be replaced by Swift implementation at runtime
        return null
    }

    /**
     * Check if data is AVIF format
     * Note: This is a stub - actual implementation is in Swift
     */
    fun isAvifFile(data: NSData): Boolean {
        // Will be replaced by Swift implementation at runtime
        return false
    }

    /**
     * Get image information
     * Note: This is a stub - actual implementation is in Swift
     */
    fun getImageInfo(data: NSData): NSDictionary? {
        // Will be replaced by Swift implementation at runtime
        return null
    }
}
