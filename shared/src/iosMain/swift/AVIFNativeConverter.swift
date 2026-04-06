import Foundation
import UIKit

#if canImport(avif)
import avif
#endif

/// Native AVIF converter for iOS
/// Provides encoding and decoding functionality using avif.swift
@objc public class AVIFNativeConverter: NSObject {

    // MARK: - Configuration

    /// Check if avif.swift is available
    @objc public static var isAvifAvailable: Bool {
        #if canImport(avif)
        return true
        #else
        return false
        #endif
    }

    /// Get avif.swift version
    @objc public static var avifVersion: String {
        #if canImport(avif)
        return "avif.swift 2.x"
        #else
        return "avif.swift not available"
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
        #if canImport(avif)
        return encodeWithAvifSwift(image, quality: quality, speed: speed)
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
        #if canImport(avif)
        return decodeWithAvifSwift(avifData)
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

    // MARK: - Private Implementation (avif.swift)

    #if canImport(avif)

    private func encodeWithAvifSwift(
        _ image: UIImage,
        quality: Int,
        speed: Int
    ) -> Data? {
        // Clamp speed to max 6 to stay in AOM_USAGE_GOOD_QUALITY mode.
        // Speed >= 7 triggers AOM_USAGE_REALTIME which uses CBR rate control,
        // incompatible with quantizer-based still image encoding.
        let clampedSpeed = min(speed, 6)

        // Normalize orientation before encoding
        let orientedImage = normalizeOrientation(image)

        // avif.swift quality is 0.0 to 1.0
        let qualityNormalized = Double(quality) / 100.0

        do {
            let avifData = try AVIFEncoder.encode(
                image: orientedImage,
                quality: qualityNormalized,
                speed: clampedSpeed
            )
            return avifData
        } catch {
            print("Failed to encode AVIF: \(error.localizedDescription)")
            return nil
        }
    }

    private func decodeWithAvifSwift(_ avifData: Data) -> UIImage? {
        return AVIFDecoder.decode(avifData)
    }

    #endif

    // MARK: - Placeholder Implementation

    private func encodePlaceholder(_ image: UIImage, quality: Int) -> Data? {
        print("⚠️ Using JPEG fallback - avif.swift not available")
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
        print("⚠️ Using standard image decoding - avif.swift not available")
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
