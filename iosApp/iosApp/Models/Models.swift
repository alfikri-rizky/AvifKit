import Foundation
import Shared

// MARK: - Quality Preset
enum QualityPreset: String, CaseIterable, Identifiable, Hashable {
    case speed = "Speed"
    case balanced = "Balanced"
    case quality = "Quality"
    case storage = "Storage"
    case custom = "Custom"

    var id: String { rawValue }

    func toPriority() -> Priority? {
        switch self {
        case .speed:
            return Priority.speed
        case .balanced:
            return Priority.balanced
        case .quality:
            return Priority.quality
        case .storage:
            return Priority.storage
        case .custom:
            return nil
        }
    }
}

// MARK: - Custom Parameters
struct CustomParameters {
    var quality: Int32
    var speed: Int32
    var subsample: ChromaSubsample
    var alphaQuality: Int32
    var lossless: Bool
    var preserveMetadata: Bool
    var maxDimension: Int32?
    var maxSize: Int64?
    var compressionStrategy: CompressionStrategy

    static var `default`: CustomParameters {
        return CustomParameters(
            quality: 80,
            speed: 5,
            subsample: ChromaSubsample.yuv420,
            alphaQuality: 100,
            lossless: false,
            preserveMetadata: false,
            maxDimension: nil,
            maxSize: nil,
            compressionStrategy: CompressionStrategy.smart
        )
    }
}

// MARK: - Conversion Result
struct ConversionResult {
    let originalImageUri: String
    let convertedImagePath: String
    let originalData: ImageData
    let convertedData: ImageData
}

// MARK: - Image Data
struct ImageData {
    let quality: Int32
    let speed: Int32
    let subsample: ChromaSubsample
    let metadata: Bool
    let dimension: String
    let fileSize: Int64
}
