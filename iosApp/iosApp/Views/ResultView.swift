import SwiftUI
import Shared
import Photos

struct ResultView: View {
    let result: ConversionResult
    let originalImageData: Data
    let targetMaxSize: Int64?
    let onBack: () -> Void

    @State private var selectedTab = 0
    @State private var isZoomed = false
    @State private var isDownloading = false
    @State private var showAlert = false
    @State private var alertMessage = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Header with back button
                HStack(spacing: 12) {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20))
                            .foregroundColor(.accentColor)
                    }

                    Text("Conversion Results")
                        .font(.title2)
                        .fontWeight(.medium)

                    Spacer()
                }

                // Compression Stats Card
                CompressionStatsCard(
                    result: result,
                    targetMaxSize: targetMaxSize
                )

                // Tabs for Before/After
                Picker("View", selection: $selectedTab) {
                    Text("Before").tag(0)
                    Text("After (AVIF)").tag(1)
                }
                .pickerStyle(SegmentedPickerStyle())

                // Image Preview Card
                let currentImageData = selectedTab == 0 ? originalImageData : loadConvertedImageData()
                let currentData = selectedTab == 0 ? result.originalData : result.convertedData
                let imageLabel = selectedTab == 0 ? "Original Image" : "AVIF Image"

                if let imageData = currentImageData,
                   let image = UIImage(data: imageData) {
                    ImagePreviewCardResult(
                        image: image,
                        imageLabel: imageLabel,
                        isZoomed: $isZoomed
                    )
                }

                // Image Details Card
                ImageDetailsCard(imageData: currentData)

                // Download Button
                Button(action: {
                    downloadAVIF()
                }) {
                    HStack {
                        if isDownloading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        } else {
                            Image(systemName: "arrow.down.circle")
                                .font(.system(size: 20))
                        }
                        Text(isDownloading ? "Downloading..." : "Download AVIF")
                            .font(.headline)
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(isDownloading ? Color.accentColor.opacity(0.7) : Color.accentColor)
                    .cornerRadius(12)
                }
                .disabled(isDownloading)

                Text(isDownloading ? "Saving file..." : "Download to your device's Photos library")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(16)
        }
        .navigationBarHidden(true)
        .alert(isPresented: $showAlert) {
            Alert(
                title: Text("Download"),
                message: Text(alertMessage),
                dismissButton: .default(Text("OK"))
            )
        }
    }

    private func loadConvertedImageData() -> Data? {
        guard let data = try? Data(contentsOf: URL(fileURLWithPath: result.convertedImagePath)) else {
            return nil
        }
        return data
    }

    private func downloadAVIF() {
        isDownloading = true

        PHPhotoLibrary.requestAuthorization { status in
            guard status == .authorized else {
                DispatchQueue.main.async {
                    isDownloading = false
                    alertMessage = "Photo library access denied. Please enable it in Settings."
                    showAlert = true
                }
                return
            }

            let fileURL = URL(fileURLWithPath: result.convertedImagePath)

            // Verify file exists
            guard FileManager.default.fileExists(atPath: result.convertedImagePath) else {
                DispatchQueue.main.async {
                    isDownloading = false
                    alertMessage = "Failed to load AVIF file"
                    showAlert = true
                }
                return
            }

            // Save to Photos library using file URL to preserve AVIF format
            PHPhotoLibrary.shared().performChanges({
                let creationRequest = PHAssetCreationRequest.forAsset()
                let options = PHAssetResourceCreationOptions()
                options.uniformTypeIdentifier = "public.avif"
                creationRequest.addResource(with: .photo, fileURL: fileURL, options: options)
            }) { success, error in
                DispatchQueue.main.async {
                    isDownloading = false
                    if success {
                        alertMessage = "AVIF image saved to Photos successfully!"
                        showAlert = true
                    } else {
                        alertMessage = "Download failed: \(error?.localizedDescription ?? "Unknown error")"
                        showAlert = true
                    }
                }
            }
        }
    }
}

struct CompressionStatsCard: View {
    let result: ConversionResult
    let targetMaxSize: Int64?

    var body: some View {
        let originalSize = result.originalData.fileSize
        let convertedSize = result.convertedData.fileSize
        let compressionRatio = originalSize > 0 ?
            Int(((Double(originalSize - convertedSize) / Double(originalSize)) * 100)) : 0
        let savedBytes = originalSize - convertedSize
        let targetAchieved = targetMaxSize.map { convertedSize <= $0 } ?? true

        VStack(spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("File Size Reduced")
                        .font(.body)
                        .foregroundColor(.secondary)

                    Text("\(compressionRatio)% smaller")
                        .font(.title2)
                        .fontWeight(.semibold)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    Text("Saved")
                        .font(.body)
                        .foregroundColor(.secondary)

                    Text(formatFileSize(savedBytes))
                        .font(.title2)
                        .fontWeight(.semibold)
                }
            }

            // Show target max size status if set
            if let targetSize = targetMaxSize {
                Divider()
                    .padding(.vertical, 4)

                HStack {
                    Text("Target Size: \(formatFileSize(targetSize))")
                        .font(.body)
                        .foregroundColor(.secondary)

                    Spacer()

                    HStack(spacing: 4) {
                        Image(systemName: targetAchieved ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .foregroundColor(targetAchieved ? .green : .red)
                        Text(targetAchieved ? "Achieved" : "Not Reached")
                            .font(.body)
                            .foregroundColor(targetAchieved ? .green : .red)
                    }
                }
            }
        }
        .padding(16)
        .background(targetMaxSize != nil && !targetAchieved ?
                    Color.red.opacity(0.1) : Color.blue.opacity(0.1))
        .cornerRadius(12)
    }
}

struct ImagePreviewCardResult: View {
    let image: UIImage
    let imageLabel: String
    @Binding var isZoomed: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(uiImage: image)
                .resizable()
                .aspectRatio(contentMode: isZoomed ? .fill : .fit)
                .frame(maxWidth: .infinity)
                .frame(height: 250)
                .clipped()
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.secondary.opacity(0.3), lineWidth: 1)
                )
                .onTapGesture {
                    withAnimation {
                        isZoomed.toggle()
                    }
                }
                .scaleEffect(isZoomed ? 1.5 : 1.0)

            HStack {
                Text(imageLabel)
                    .font(.body)
                    .foregroundColor(.secondary)

                Spacer()

                Button(action: {
                    withAnimation {
                        isZoomed.toggle()
                    }
                }) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 20))
                        .foregroundColor(.accentColor)
                }
            }
        }
        .padding(12)
        .background(Color.secondary.opacity(0.1))
        .cornerRadius(12)
    }
}

struct ImageDetailsCard: View {
    let imageData: ImageData

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Image Details")
                .font(.headline)

            DetailRow(label: "Quality", value: "\(imageData.quality)")
            Divider()

            DetailRow(label: "Encoding Speed", value: "\(imageData.speed)/10")
            Divider()

            DetailRow(label: "Chroma Subsampling", value: imageData.subsample.name)
            Divider()

            DetailRow(label: "Metadata Preserved", value: imageData.metadata ? "Yes" : "No")
            Divider()

            DetailRow(label: "Dimensions", value: imageData.dimension)
            Divider()

            DetailRow(label: "File Size", value: formatFileSize(imageData.fileSize))
        }
        .padding(16)
        .background(Color.secondary.opacity(0.1))
        .cornerRadius(12)
    }
}

struct DetailRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label)
                .font(.body)
                .foregroundColor(.secondary)

            Spacer()

            Text(value)
                .font(.body)
        }
    }
}
