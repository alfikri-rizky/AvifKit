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
        return avifVersion()
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
        encoder.pointee.quality = Int32(quality)
        encoder.pointee.qualityAlpha = Int32(quality)
        encoder.pointee.speed = Int32(speed)
        encoder.pointee.maxThreads = 4

        // Create AVIF image
        guard let avifImage = avifImageCreate(
            Int32(width),
            Int32(height),
            8,
            AVIF_PIXEL_FORMAT_YUV420
        ) else {
            print("Failed to create AVIF image")
            return nil
        }
        defer { avifImageDestroy(avifImage) }

        // Allocate planes
        let allocResult = avifImageAllocatePlanes(avifImage, AVIF_PLANES_ALL.rawValue)
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
            print("Failed to encode AVIF")
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

        // Decode
        let decodeResult = avifDecoderNextImage(decoder)
        guard decodeResult == AVIF_RESULT_OK else {
            print("Failed to decode AVIF")
            return nil
        }

        // Convert to RGB
        var rgbImage = avifRGBImage()
        avifRGBImageSetDefaults(&rgbImage, decoder.pointee.image)
        rgbImage.format = AVIF_RGB_FORMAT_RGBA
        rgbImage.depth = 8

        let allocResult = avifRGBImageAllocatePixels(&rgbImage)
        guard allocResult == AVIF_RESULT_OK else {
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
    private func uiImageToRGBA(_ image: UIImage) -> (pixels: UnsafeMutablePointer<UInt8>?, width: Int, height: Int) {
        // Use the image's size (which respects orientation) not CGImage size
        let width = Int(image.size.width * image.scale)
        let height = Int(image.size.height * image.scale)
        let bytesPerPixel = 4
        let bytesPerRow = width * bytesPerPixel
        let bitsPerComponent = 8

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
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            free(data)
            return (nil, width, height)
        }

        // IMPORTANT: Draw the UIImage, not the CGImage, to respect orientation
        // UIGraphicsBeginImageContext applies orientation transformations automatically
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
