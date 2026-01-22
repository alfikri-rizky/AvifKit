import Foundation
import Shared
import SwiftUI
import PhotosUI

@MainActor
class AvifConverterViewModel: ObservableObject {
    private let avifConverter = AvifConverter()

    @Published var uiState: UploadUiStateWrapper = .idle
    @Published var qualityPreset: QualityPreset = .balanced
    @Published var customParams: CustomParameters = CustomParameters(
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

    func onImageSelected(imageData: Data, fileName: String) {
        let fileSize = Int64(imageData.count)
        uiState = .imageSelected(
            imageData: imageData,
            fileName: fileName,
            fileSize: fileSize
        )
    }

    func onQualityPresetChanged(_ preset: QualityPreset) {
        qualityPreset = preset
    }

    func onCustomParamsChanged(_ params: CustomParameters) {
        customParams = params
    }

    func convertToAvif() async {
        guard case let .imageSelected(imageData, fileName, fileSize) = uiState else {
            return
        }

        uiState = .converting(
            imageData: imageData,
            fileName: fileName,
            fileSize: fileSize
        )

        do {
            // Get encoding options
            let encodingOptions: EncodingOptions
            if qualityPreset == .custom {
                encodingOptions = EncodingOptions(
                    quality: customParams.quality,
                    speed: customParams.speed,
                    subsample: customParams.subsample,
                    alphaQuality: customParams.alphaQuality,
                    lossless: customParams.lossless,
                    preserveMetadata: customParams.preserveMetadata,
                    maxDimension: customParams.maxDimension.map { KotlinInt(int: $0) },
                    maxSize: customParams.maxSize.map { KotlinLong(value: $0) },
                    compressionStrategy: customParams.compressionStrategy
                )
            } else {
                encodingOptions = EncodingOptions.companion.fromPriority(priority: qualityPreset.toPriority()!)
            }

            // Get original image info
            guard let uiImage = UIImage(data: imageData) else {
                throw NSError(domain: "AvifKit", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid image input"])
            }

            let originalDimension = "\(Int(uiImage.size.width))x\(Int(uiImage.size.height))"

            // Convert to AVIF using PlatformFile
            let input = ImageInput.companion.from(data: KotlinByteArray(data: imageData))

            // Create output PlatformFile
            let tempDir = FileManager.default.temporaryDirectory
            let outputURL = tempDir.appendingPathComponent("converted_\(Date().timeIntervalSince1970).avif")
            let outputPlatformFile = PlatformFile.companion.fromPath(path: outputURL.path)

            // Use convertToFile with PlatformFile
            let convertedFile = try await avifConverter.convertToFile(
                input: input,
                output: outputPlatformFile,
                priority: qualityPreset.toPriority() ?? Priority.balanced,
                options: encodingOptions
            )

            // Get converted file info from PlatformFile
            let convertedSize = Int64(truncating: try await convertedFile.size())

            // Calculate converted dimensions (considering maxDimension)
            var convertedWidth = Int(uiImage.size.width)
            var convertedHeight = Int(uiImage.size.height)

            if let maxDim = encodingOptions.maxDimension {
                let maxOriginal = max(Int(uiImage.size.width), Int(uiImage.size.height))
                if maxOriginal > Int(maxDim) {
                    let scale = Float(maxDim) / Float(maxOriginal)
                    convertedWidth = Int(Float(uiImage.size.width) * scale)
                    convertedHeight = Int(Float(uiImage.size.height) * scale)
                }
            }

            let convertedDimension = "\(convertedWidth)x\(convertedHeight)"

            // Create result
            let originalData = ImageData(
                quality: 100,
                speed: 0,
                subsample: ChromaSubsample.yuv444,
                metadata: true,
                dimension: originalDimension,
                fileSize: fileSize
            )

            let convertedData = ImageData(
                quality: encodingOptions.quality,
                speed: encodingOptions.speed,
                subsample: encodingOptions.subsample,
                metadata: encodingOptions.preserveMetadata,
                dimension: convertedDimension,
                fileSize: convertedSize
            )

            let result = ConversionResult(
                originalImageUri: "", // Not used on iOS
                convertedImagePath: outputURL.path,
                originalData: originalData,
                convertedData: convertedData
            )

            uiState = .success(result: result, originalImageData: imageData)

        } catch {
            uiState = .error(message: "Conversion failed: \(error.localizedDescription)")
        }
    }

    func resetState() {
        uiState = .idle
        qualityPreset = .balanced
        customParams = CustomParameters(
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

// Wrapper enum for UI state since we can't use sealed classes directly in Swift
enum UploadUiStateWrapper: Equatable {
    case idle
    case imageSelected(imageData: Data, fileName: String, fileSize: Int64)
    case converting(imageData: Data, fileName: String, fileSize: Int64)
    case success(result: ConversionResult, originalImageData: Data)
    case error(message: String)

    static func == (lhs: UploadUiStateWrapper, rhs: UploadUiStateWrapper) -> Bool {
        switch (lhs, rhs) {
        case (.idle, .idle):
            return true
        case (.imageSelected(_, _, _), .imageSelected(_, _, _)):
            return true
        case (.converting(_, _, _), .converting(_, _, _)):
            return true
        case (.success(_, _), .success(_, _)):
            return true
        case (.error(_), .error(_)):
            return true
        default:
            return false
        }
    }
}

// Extensions to help with Kotlin interop
extension KotlinByteArray {
    convenience init(data: Data) {
        let nsData = data as NSData
        let bytes = nsData.bytes.assumingMemoryBound(to: Int8.self)
        self.init(size: Int32(data.count))
        for i in 0..<data.count {
            self.set(index: Int32(i), value: bytes[i])
        }
    }

    func toData() -> Data {
        var data = Data(count: Int(self.size))
        for i in 0..<Int(self.size) {
            data[i] = UInt8(bitPattern: self.get(index: Int32(i)))
        }
        return data
    }
}

extension Int32 {
    var int64Value: Int64? {
        return Int64(self)
    }
}

extension Int64 {
    var int32Value: Int32? {
        return Int32(self)
    }
}
