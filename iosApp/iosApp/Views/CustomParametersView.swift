import SwiftUI
import Shared

struct CustomParametersView: View {
    @Binding var params: CustomParameters
    let currentFileSize: Int64?

    @State private var maxDimEnabled: Bool
    @State private var maxDimValue: Double
    @State private var maxSizeEnabled: Bool
    @State private var maxSizeValue: Double

    init(params: Binding<CustomParameters>, currentFileSize: Int64?) {
        _params = params
        self.currentFileSize = currentFileSize

        _maxDimEnabled = State(initialValue: params.wrappedValue.maxDimension != nil)
        _maxDimValue = State(initialValue: Double(params.wrappedValue.maxDimension ?? 2048))
        _maxSizeEnabled = State(initialValue: params.wrappedValue.maxSize != nil)
        _maxSizeValue = State(initialValue: Double(params.wrappedValue.maxSize ?? currentFileSize ?? 1048576))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Custom Parameters")
                .font(.headline)

            // Quality Slider
            SliderWithLabel(
                label: "Quality",
                value: Binding(
                    get: { Double(params.quality) },
                    set: { params = params.doCopy(quality: Int32($0)) }
                ),
                range: 0...100,
                step: 1,
                valueLabel: "\(params.quality)"
            )

            // Speed Slider
            SliderWithLabel(
                label: "Encoding Speed",
                value: Binding(
                    get: { Double(params.speed) },
                    set: { params = params.doCopy(speed: Int32($0)) }
                ),
                range: 0...10,
                step: 1,
                valueLabel: "\(params.speed)/10"
            )

            // Chroma Subsampling Picker
            VStack(alignment: .leading, spacing: 4) {
                Text("Chroma Subsampling")
                    .font(.body)

                Picker("Chroma Subsampling", selection: Binding(
                    get: { params.subsample },
                    set: { params = params.doCopy(subsample: $0) }
                )) {
                    Text("YUV444").tag(ChromaSubsample.yuv444)
                    Text("YUV422").tag(ChromaSubsample.yuv422)
                    Text("YUV420").tag(ChromaSubsample.yuv420)
                }
                .pickerStyle(SegmentedPickerStyle())
            }

            // Alpha Quality Slider
            SliderWithLabel(
                label: "Alpha Quality",
                value: Binding(
                    get: { Double(params.alphaQuality) },
                    set: { params = params.doCopy(alphaQuality: Int32($0)) }
                ),
                range: 0...100,
                step: 1,
                valueLabel: "\(params.alphaQuality)"
            )

            // Lossless Toggle
            ToggleWithLabel(
                label: "Lossless",
                isOn: Binding(
                    get: { params.lossless },
                    set: { params = params.doCopy(lossless: $0) }
                )
            )

            // Preserve Metadata Toggle
            ToggleWithLabel(
                label: "Preserve Metadata",
                isOn: Binding(
                    get: { params.preserveMetadata },
                    set: { params = params.doCopy(preserveMetadata: $0) }
                )
            )

            // Max Dimension (Optional)
            VStack(alignment: .leading, spacing: 8) {
                ToggleWithLabel(
                    label: "Limit Max Dimension",
                    isOn: $maxDimEnabled
                )
                .onChange(of: maxDimEnabled) { enabled in
                    params = params.doCopy(
                        maxDimension: enabled ? Int32(maxDimValue) : nil
                    )
                }

                if maxDimEnabled {
                    SliderWithLabel(
                        label: "Max Dimension",
                        value: $maxDimValue,
                        range: 512...4096,
                        step: 256,
                        valueLabel: "\(Int(maxDimValue))px"
                    )
                    .onChange(of: maxDimValue) { value in
                        params = params.doCopy(maxDimension: Int32(value))
                    }
                }
            }

            // Max File Size (Optional)
            VStack(alignment: .leading, spacing: 8) {
                ToggleWithLabel(
                    label: "Limit Max File Size",
                    isOn: $maxSizeEnabled
                )
                .onChange(of: maxSizeEnabled) { enabled in
                    if let fileSize = currentFileSize {
                        params = params.doCopy(
                            maxSize: enabled ? Int64(maxSizeValue) : nil
                        )
                    }
                }

                if currentFileSize == nil && maxSizeEnabled {
                    Text("Please select a file first")
                        .font(.caption)
                        .foregroundColor(.red)
                }

                if maxSizeEnabled, let fileSize = currentFileSize {
                    SliderWithLabel(
                        label: "Max File Size",
                        value: $maxSizeValue,
                        range: 1024...Double(fileSize),
                        step: 1024,
                        valueLabel: formatFileSize(Int64(maxSizeValue))
                    )
                    .onChange(of: maxSizeValue) { value in
                        params = params.doCopy(maxSize: Int64(value))
                    }

                    // Warning for very small sizes
                    if maxSizeValue < 50 * 1024 {
                        Text("⚠️ Very small target size. Result may not reach target due to AVIF format overhead and image complexity.")
                            .font(.caption)
                            .foregroundColor(.orange)
                    }

                    Text("Original size: \(formatFileSize(fileSize)) • The converter will try up to 10 attempts to reach the target by adjusting quality and dimensions.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            // Compression Strategy (Only visible when maxSize is set)
            if maxSizeEnabled, currentFileSize != nil {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Compression Strategy")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.accentColor)

                    // Strategy Picker
                    Picker("Compression Strategy", selection: Binding(
                        get: { params.compressionStrategy },
                        set: { params = params.doCopy(compressionStrategy: $0) }
                    )) {
                        Text("SMART (Recommended)").tag(CompressionStrategy.smart)
                        Text("STRICT").tag(CompressionStrategy.strict)
                    }
                    .pickerStyle(SegmentedPickerStyle())

                    // Strategy explanation
                    Text(getCompressionStrategyDescription(params.compressionStrategy))
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(12)
                .background(Color.accentColor.opacity(0.1))
                .cornerRadius(8)
            }
        }
        .padding(16)
        .background(Color.secondary.opacity(0.1))
        .cornerRadius(12)
    }
}

struct SliderWithLabel: View {
    let label: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    let step: Double
    let valueLabel: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(label)
                    .font(.body)
                Spacer()
                Text(valueLabel)
                    .font(.body)
                    .foregroundColor(.accentColor)
            }

            Slider(value: $value, in: range, step: step)
        }
    }
}

struct ToggleWithLabel: View {
    let label: String
    @Binding var isOn: Bool

    var body: some View {
        Toggle(label, isOn: $isOn)
            .font(.body)
    }
}

// Helper extension for CustomParameters copy
extension CustomParameters {
    func doCopy(
        quality: Int32? = nil,
        speed: Int32? = nil,
        subsample: ChromaSubsample? = nil,
        alphaQuality: Int32? = nil,
        lossless: Bool? = nil,
        preserveMetadata: Bool? = nil,
        maxDimension: Int32? = nil,
        maxSize: Int64? = nil,
        compressionStrategy: CompressionStrategy? = nil
    ) -> CustomParameters {
        return CustomParameters(
            quality: quality ?? self.quality,
            speed: speed ?? self.speed,
            subsample: subsample ?? self.subsample,
            alphaQuality: alphaQuality ?? self.alphaQuality,
            lossless: lossless ?? self.lossless,
            preserveMetadata: preserveMetadata ?? self.preserveMetadata,
            maxDimension: maxDimension ?? self.maxDimension,
            maxSize: maxSize ?? self.maxSize,
            compressionStrategy: compressionStrategy ?? self.compressionStrategy
        )
    }
}

// Helper function for compression strategy descriptions
private func getCompressionStrategyDescription(_ strategy: CompressionStrategy) -> String {
    switch strategy {
    case .smart:
        return "🎯 SMART: Finds the highest quality image that meets your target size using binary search. Faster (6-8 attempts) and produces better-looking images."
    case .strict:
        return "🗜️ STRICT: Continues compressing even after meeting target to find the smallest possible size. May take longer (up to 10 attempts)."
    default:
        return "Unknown compression strategy"
    }
}
