import Foundation
import UIKit

#if canImport(libavif)
import libavif
#endif

/// Native AVIF converter for iOS
/// Provides encoding and decoding functionality using libavif
@objc public class AVIFNativeConverter: NSObject {

    // MARK: - Configuration

    /// Check if libavif is available
    @objc public static var isAvifAvailable: Bool {
        #if canImport(libavif)
        return true
        #else
        return false
        #endif
    }

    /// Get libavif version
    @objc public static var avifVersion: String {
        #if canImport(libavif)
        if let cString = libavif.avifVersion() {
            return String(cString: cString)
        }
        return "Unknown"
        #else
        return "libavif not available"
        #endif
    }

    // MARK: - Encoding

    /// Encode UIImage to AVIF format
    /// - Parameters:
    ///   - image: Source image
    ///   - quality: Quality (0-100)
    ///   - speed: Encoding speed (0-10, 0=slowest/best, 10=fastest)
    /// - Returns: AVIF data or nil on failure
    @objc public func encodeImage(
        _ image: UIImage,
        quality: Int,
        speed: Int
    ) -> Data? {
        #if canImport(libavif)
        return encodeWithLibavif(image, quality: quality, speed: speed)
        #else
        return encodePlaceholder(image, quality: quality)
        #endif
    }

    /// Encode with detailed options
    @objc public func encodeImageWithOptions(
        _ image: UIImage,
        options: NSDictionary
    ) -> Data? {
        let quality = options["quality"] as? Int ?? 75
        let speed = options["speed"] as? Int ?? 6
        let maxDimension = options["maxDimension"] as? Int

        // Resize if needed
        var processedImage = image
        if let maxDim = maxDimension {
            processedImage = resizeImage(image, maxDimension: maxDim)
        }

        return encodeImage(processedImage, quality: quality, speed: speed)
    }

    // MARK: - Decoding

    /// Decode AVIF data to UIImage
    /// - Parameter avifData: AVIF encoded data
    /// - Returns: Decoded image or nil on failure
    @objc public func decodeAvif(_ avifData: Data) -> UIImage? {
        #if canImport(libavif)
        return decodeWithLibavif(avifData)
        #else
        return decodePlaceholder(avifData)
        #endif
    }

    // MARK: - Utilities

    /// Check if data is AVIF format
    @objc public func isAvifFile(_ data: Data) -> Bool {
        guard data.count > 12 else { return false }

        let signature = data.subdata(in: 4..<12)
        let avifSignature = Data([0x66, 0x74, 0x79, 0x70, 0x61, 0x76, 0x69, 0x66])

        return signature == avifSignature
    }

    /// Get image information
    @objc public func getImageInfo(_ data: Data) -> NSDictionary? {
        guard let image = UIImage(data: data) else {
            return nil
        }

        return [
            "width": Int(image.size.width * image.scale),
            "height": Int(image.size.height * image.scale),
            "hasAlpha": image.hasAlpha,
            "scale": image.scale
        ]
    }

    // MARK: - Private Implementation

    #if canImport(libavif)

    private func encodeWithLibavif(
        _ image: UIImage,
        quality: Int,
        speed: Int
    ) -> Data? {
        // Clamp speed to max 6 to stay in AOM_USAGE_GOOD_QUALITY mode.
        // Speed >= 7 triggers AOM_USAGE_REALTIME which uses CBR rate control,
        // incompatible with quantizer-based still image encoding on libaom 2.0.2.
        let clampedSpeed = min(speed, 6)
        // Convert UIImage to RGBA buffer (respects orientation)
        let (pixelData, width, height) = uiImageToRGBA(image)

        guard let pixels = pixelData else {
            print("Failed to convert image to RGBA")
            return nil
        }
        defer { free(pixels) }

        // Create AVIF encoder
        guard let encoder = avifEncoderCreate() else {
            print("Failed to create AVIF encoder")
            return nil
        }
        defer { avifEncoderDestroy(encoder) }

        // Set encoding parameters
        // In libavif 0.11+, use quantizers instead of quality
        // quality 0-100 maps to quantizer 63-0 (inverse relationship)
        let quantizer = Int32(63 - (quality * 63 / 100))
        encoder.pointee.minQuantizer = quantizer
        encoder.pointee.maxQuantizer = quantizer
        encoder.pointee.minQuantizerAlpha = quantizer
        encoder.pointee.maxQuantizerAlpha = quantizer
        encoder.pointee.speed = Int32(clampedSpeed)
        encoder.pointee.maxThreads = 4

        // Create AVIF image
        guard let avifImage = avifImageCreate(
            UInt32(width),
            UInt32(height),
            8,
            AVIF_PIXEL_FORMAT_YUV420
        ) else {
            print("Failed to create AVIF image")
            return nil
        }
        defer { avifImageDestroy(avifImage) }

        // Allocate planes
        let allocResult = avifImageAllocatePlanes(avifImage, UInt32(AVIF_PLANES_ALL.rawValue))
        guard allocResult == AVIF_RESULT_OK else {
            print("Failed to allocate image planes")
            return nil
        }

        // Setup RGB image
        var rgbImage = avifRGBImage()
        avifRGBImageSetDefaults(&rgbImage, avifImage)
        rgbImage.format = AVIF_RGB_FORMAT_RGBA
        rgbImage.depth = 8
        rgbImage.pixels = pixels
        rgbImage.rowBytes = UInt32(width * 4)

        // Convert RGB to YUV
        let convertResult = avifImageRGBToYUV(avifImage, &rgbImage)
        guard convertResult == AVIF_RESULT_OK else {
            print("Failed to convert RGB to YUV")
            return nil
        }

        // Encode
        var output = avifRWData()
        output.data = nil
        output.size = 0

        let encodeResult = avifEncoderWrite(encoder, avifImage, &output)
        defer { avifRWDataFree(&output) }

        guard encodeResult == AVIF_RESULT_OK else {
            let errStr = String(cString: avifResultToString(encodeResult))
            print("Failed to encode AVIF: \(errStr)")
            return nil
        }

        // Convert to Data
        return Data(bytes: output.data, count: output.size)
    }

    private func decodeWithLibavif(_ avifData: Data) -> UIImage? {
        // Create decoder
        guard let decoder = avifDecoderCreate() else {
            print("Failed to create AVIF decoder")
            return nil
        }
        defer { avifDecoderDestroy(decoder) }

        decoder.pointee.maxThreads = 4

        // Set input
        let result = avifData.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) -> avifResult in
            guard let baseAddress = bytes.baseAddress else {
                return AVIF_RESULT_UNKNOWN_ERROR
            }

            return avifDecoderSetIOMemory(
                decoder,
                baseAddress.assumingMemoryBound(to: UInt8.self),
                bytes.count
            )
        }

        guard result == AVIF_RESULT_OK else {
            print("Failed to set decoder input")
            return nil
        }

        // Parse AVIF structure first
        let parseResult = avifDecoderParse(decoder)
        guard parseResult == AVIF_RESULT_OK else {
            print("Failed to parse AVIF structure: \(parseResult)")
            return nil
        }

        // Decode first image
        let decodeResult = avifDecoderNextImage(decoder)
        guard decodeResult == AVIF_RESULT_OK else {
            print("Failed to decode AVIF: \(decodeResult)")
            return nil
        }

        // Convert to RGB
        var rgbImage = avifRGBImage()
        avifRGBImageSetDefaults(&rgbImage, decoder.pointee.image)
        rgbImage.format = AVIF_RGB_FORMAT_RGBA
        rgbImage.depth = 8

        // In libavif 0.11+, avifRGBImageAllocatePixels returns void
        avifRGBImageAllocatePixels(&rgbImage)
        guard rgbImage.pixels != nil else {
            print("Failed to allocate RGB pixels")
            return nil
        }
        defer { avifRGBImageFreePixels(&rgbImage) }

        let convertResult = avifImageYUVToRGB(decoder.pointee.image, &rgbImage)
        guard convertResult == AVIF_RESULT_OK else {
            print("Failed to convert YUV to RGB")
            return nil
        }

        // Create UIImage from RGBA data
        return rgbaToUIImage(
            pixels: rgbImage.pixels,
            width: Int(rgbImage.width),
            height: Int(rgbImage.height),
            rowBytes: Int(rgbImage.rowBytes)
        )
    }

    /// Convert UIImage to RGBA buffer, respecting orientation
    /// This ensures images taken in portrait mode are correctly oriented
    ///
    /// Performance optimizations:
    /// - Fast path: skips UIGraphics double-render when orientation is .up
    /// - Uses noneSkipLast alpha for opaque images (avoids premultiplication overhead)
    private func uiImageToRGBA(_ image: UIImage) -> (pixels: UnsafeMutablePointer<UInt8>?, width: Int, height: Int) {
        let bytesPerPixel = 4
        let bitsPerComponent = 8

        // Determine alpha format based on whether source image has alpha
        // Using noneSkipLast for opaque images avoids per-pixel premultiplication
        let sourceHasAlpha: Bool = {
            guard let cgImage = image.cgImage else { return false }
            let info = cgImage.alphaInfo
            return info != .none && info != .noneSkipFirst && info != .noneSkipLast
        }()
        let alphaInfo: CGImageAlphaInfo = sourceHasAlpha ? .premultipliedLast : .noneSkipLast

        // Fast path: if orientation is already .up, draw CGImage directly
        // This skips the costly UIGraphicsBeginImageContextWithOptions double-render
        if image.imageOrientation == .up, let cgImage = image.cgImage {
            let width = cgImage.width
            let height = cgImage.height
            let bytesPerRow = width * bytesPerPixel

            guard let data = malloc(height * bytesPerRow) else {
                return (nil, width, height)
            }
            let pixels = data.assumingMemoryBound(to: UInt8.self)

            guard let context = CGContext(
                data: pixels,
                width: width,
                height: height,
                bitsPerComponent: bitsPerComponent,
                bytesPerRow: bytesPerRow,
                space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: alphaInfo.rawValue
            ) else {
                free(data)
                return (nil, width, height)
            }

            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
            return (pixels, width, height)
        }

        // Slow path: orientation needs correction, use UIGraphics to normalize
        let width = Int(image.size.width * image.scale)
        let height = Int(image.size.height * image.scale)
        let bytesPerRow = width * bytesPerPixel

        guard let data = malloc(height * bytesPerRow) else {
            return (nil, width, height)
        }

        let pixels = data.assumingMemoryBound(to: UInt8.self)

        guard let context = CGContext(
            data: pixels,
            width: width,
            height: height,
            bitsPerComponent: bitsPerComponent,
            bytesPerRow: bytesPerRow,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: alphaInfo.rawValue
        ) else {
            free(data)
            return (nil, width, height)
        }

        // Draw via UIGraphics to apply orientation transforms
        UIGraphicsBeginImageContextWithOptions(image.size, false, image.scale)
        image.draw(in: CGRect(origin: .zero, size: image.size))
        let orientedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        if let cgImage = orientedImage?.cgImage {
            context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        } else {
            free(data)
            return (nil, width, height)
        }

        return (pixels, width, height)
    }

    private func rgbaToUIImage(
        pixels: UnsafeMutablePointer<UInt8>?,
        width: Int,
        height: Int,
        rowBytes: Int
    ) -> UIImage? {
        guard let pixels = pixels else { return nil }

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue)

        guard let context = CGContext(
            data: pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: rowBytes,
            space: colorSpace,
            bitmapInfo: bitmapInfo.rawValue
        ) else {
            return nil
        }

        guard let cgImage = context.makeImage() else {
            return nil
        }

        return UIImage(cgImage: cgImage)
    }

    #endif

    // MARK: - Placeholder Implementation

    private func encodePlaceholder(_ image: UIImage, quality: Int) -> Data? {
        print("⚠️ Using JPEG fallback - libavif not available")
        // Create properly oriented image before encoding
        let orientedImage = normalizeOrientation(image)
        return orientedImage.jpegData(compressionQuality: CGFloat(quality) / 100.0)
    }

    /// Normalize UIImage orientation by redrawing it
    /// This applies orientation transforms to the pixel data
    private func normalizeOrientation(_ image: UIImage) -> UIImage {
        // If already up orientation, no need to process
        if image.imageOrientation == .up {
            return image
        }

        UIGraphicsBeginImageContextWithOptions(image.size, false, image.scale)
        image.draw(in: CGRect(origin: .zero, size: image.size))
        let normalizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return normalizedImage ?? image
    }

    private func decodePlaceholder(_ data: Data) -> UIImage? {
        print("⚠️ Using standard image decoding - libavif not available")
        return UIImage(data: data)
    }

    // MARK: - Helpers

    private func resizeImage(_ image: UIImage, maxDimension: Int) -> UIImage {
        let size = image.size
        let maxDim = CGFloat(maxDimension)

        guard max(size.width, size.height) > maxDim else {
            return image
        }

        let scale = maxDim / max(size.width, size.height)
        let newSize = CGSize(
            width: size.width * scale,
            height: size.height * scale
        )

        UIGraphicsBeginImageContextWithOptions(newSize, false, image.scale)
        image.draw(in: CGRect(origin: .zero, size: newSize))
        let resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return resizedImage ?? image
    }
}

// MARK: - UIImage Extensions

extension UIImage {
    var hasAlpha: Bool {
        guard let cgImage = self.cgImage else { return false }
        let alphaInfo = cgImage.alphaInfo
        return alphaInfo != .none &&
               alphaInfo != .noneSkipFirst &&
               alphaInfo != .noneSkipLast
    }
}

// MARK: - AvifKit Native Handler Bridge

import Shared

@objc public class AvifKitNativeHandler: NSObject, IosAvifNativeHandler {

    private let converter = AVIFNativeConverter()

    public func isAvailable() -> Bool {
        return AVIFNativeConverter.isAvifAvailable
    }

    public func encodeImageWithOptions(image: UIImage, options: [AnyHashable : Any]) -> Data? {
        return converter.encodeImageWithOptions(image, options: options as NSDictionary) as Data?
    }

    public func decodeAvif(avifData: Data) -> UIImage? {
        return converter.decodeAvif(avifData)
    }

    public func getVersion() -> String {
        return AVIFNativeConverter.avifVersion
    }
}

@objc public class AvifKitSetup: NSObject {

    @objc public static func registerNativeHandler() {
        let handler = AvifKitNativeHandler()
        AvifKitIos.shared.registerHandler(handler: handler)
    }
}
