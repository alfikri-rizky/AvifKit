import SwiftUI
import PhotosUI
import Shared

struct UploadView: View {
    @ObservedObject var viewModel: AvifConverterViewModel
    @State private var showImagePicker = false
    @State private var showCamera = false
    @State private var sourceType: UIImagePickerController.SourceType = .photoLibrary

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Header
                VStack(spacing: 8) {
                    Text("AVIF Converter")
                        .font(.largeTitle)
                        .fontWeight(.medium)

                    Text("Convert your images to AVIF format")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .padding(.vertical, 8)

                // Image Upload Card
                ImageUploadCard(
                    uiState: viewModel.uiState,
                    onGalleryTap: {
                        sourceType = .photoLibrary
                        showImagePicker = true
                    },
                    onCameraTap: {
                        sourceType = .camera
                        showCamera = true
                    }
                )

                // Show quality selector when image is selected
                if case .imageSelected = viewModel.uiState {
                    QualitySelectorView(
                        selectedPreset: $viewModel.qualityPreset
                    )

                    if viewModel.qualityPreset == .custom,
                       case let .imageSelected(_, _, fileSize) = viewModel.uiState {
                        CustomParametersView(
                            params: $viewModel.customParams,
                            currentFileSize: fileSize
                        )
                    }

                    Button(action: {
                        Task {
                            await viewModel.convertToAvif()
                        }
                    }) {
                        Text("Convert to AVIF")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(Color.accentColor)
                            .cornerRadius(12)
                    }
                }

                if case .converting = viewModel.uiState {
                    Button(action: {}) {
                        HStack {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            Text("Converting...")
                                .font(.headline)
                                .foregroundColor(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color.accentColor.opacity(0.7))
                        .cornerRadius(12)
                    }
                    .disabled(true)
                }

                // Error message
                if case let .error(message) = viewModel.uiState {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(message)
                            .font(.body)
                            .foregroundColor(.white)
                            .padding()
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.red.opacity(0.2))
                    .cornerRadius(12)
                }
            }
            .padding(16)
        }
        .sheet(isPresented: $showImagePicker) {
            ImagePicker(sourceType: sourceType) { image in
                handleImageSelected(image)
            }
        }
        .sheet(isPresented: $showCamera) {
            ImagePicker(sourceType: .camera) { image in
                handleImageSelected(image)
            }
        }
    }

    private func handleImageSelected(_ image: UIImage) {
        guard let imageData = image.jpegData(compressionQuality: 1.0) else {
            return
        }

        let fileName = "image_\(Date().timeIntervalSince1970).jpg"
        viewModel.onImageSelected(imageData: imageData, fileName: fileName)
    }
}

struct ImageUploadCard: View {
    let uiState: UploadUiStateWrapper
    let onGalleryTap: () -> Void
    let onCameraTap: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Upload Image")
                .font(.headline)

            HStack(spacing: 12) {
                // Gallery Button
                Button(action: onGalleryTap) {
                    VStack(spacing: 8) {
                        Image(systemName: "photo.on.rectangle")
                            .font(.system(size: 32))
                            .foregroundColor(.accentColor)
                        Text("Gallery")
                            .font(.subheadline)
                            .foregroundColor(.primary)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 96)
                    .background(Color.secondary.opacity(0.1))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.secondary.opacity(0.3), lineWidth: 1)
                    )
                }

                // Camera Button
                Button(action: onCameraTap) {
                    VStack(spacing: 8) {
                        Image(systemName: "camera")
                            .font(.system(size: 32))
                            .foregroundColor(.accentColor)
                        Text("Camera")
                            .font(.subheadline)
                            .foregroundColor(.primary)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 96)
                    .background(Color.secondary.opacity(0.1))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.secondary.opacity(0.3), lineWidth: 1)
                    )
                }
            }

            // Image Preview
            if case let .imageSelected(imageData, fileName, fileSize) = uiState,
               let image = UIImage(data: imageData) {
                ImagePreviewCard(
                    image: image,
                    fileName: fileName,
                    fileSize: fileSize
                )
            } else if case let .converting(imageData, fileName, fileSize) = uiState,
                      let image = UIImage(data: imageData) {
                ImagePreviewCard(
                    image: image,
                    fileName: fileName,
                    fileSize: fileSize
                )
            }
        }
        .padding(12)
        .background(Color.secondary.opacity(0.1))
        .cornerRadius(12)
    }
}

struct ImagePreviewCard: View {
    let image: UIImage
    let fileName: String
    let fileSize: Int64

    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "photo")
                    .font(.system(size: 20))
                    .foregroundColor(.secondary)

                VStack(alignment: .leading, spacing: 4) {
                    Text(fileName)
                        .font(.body)
                        .lineLimit(1)

                    Text(formatFileSize(fileSize))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()
            }

            Image(uiImage: image)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(maxWidth: .infinity)
                .frame(height: 200)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.secondary.opacity(0.3), lineWidth: 1)
                )
        }
        .padding(12)
        .background(Color.secondary.opacity(0.05))
        .cornerRadius(8)
    }
}

// Image Picker wrapper for UIImagePickerController
struct ImagePicker: UIViewControllerRepresentable {
    let sourceType: UIImagePickerController.SourceType
    let onImagePicked: (UIImage) -> Void

    @Environment(\.presentationMode) var presentationMode

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = sourceType
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: ImagePicker

        init(_ parent: ImagePicker) {
            self.parent = parent
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            if let image = info[.originalImage] as? UIImage {
                parent.onImagePicked(image)
            }
            parent.presentationMode.wrappedValue.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.presentationMode.wrappedValue.dismiss()
        }
    }
}

func formatFileSize(_ bytes: Int64) -> String {
    let formatter = ByteCountFormatter()
    formatter.allowedUnits = [.useKB, .useMB]
    formatter.countStyle = .file
    return formatter.string(fromByteCount: bytes)
}
