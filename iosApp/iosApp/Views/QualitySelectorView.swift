import SwiftUI
import Shared

struct QualitySelectorView: View {
    @Binding var selectedPreset: QualityPreset

    let presets: [QualityPreset] = [.speed, .quality, .storage, .balanced, .custom]

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Quality Preset")
                .font(.headline)

            ForEach(presets, id: \.self) { preset in
                PresetButton(
                    preset: preset,
                    isSelected: selectedPreset == preset,
                    action: {
                        selectedPreset = preset
                    }
                )
            }
        }
        .padding(16)
        .background(Color.secondary.opacity(0.1))
        .cornerRadius(12)
    }
}

struct PresetButton: View {
    let preset: QualityPreset
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(preset.displayName)
                        .font(.body)
                        .fontWeight(isSelected ? .semibold : .regular)
                        .foregroundColor(.primary)

                    Text(preset.description)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Spacer()

                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.accentColor)
                        .font(.system(size: 22))
                }
            }
            .padding(12)
            .background(isSelected ? Color.accentColor.opacity(0.1) : Color.clear)
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isSelected ? Color.accentColor : Color.secondary.opacity(0.3), lineWidth: 1)
            )
        }
    }
}

extension QualityPreset {
    var displayName: String {
        switch self {
        case .speed: return "Speed"
        case .quality: return "Quality"
        case .storage: return "Storage"
        case .balanced: return "Balanced"
        case .custom: return "Custom"
        default: return "Unknown"
        }
    }

    var description: String {
        switch self {
        case .speed: return "Fastest encoding, lower quality"
        case .quality: return "Best quality, slower encoding"
        case .storage: return "Minimum file size"
        case .balanced: return "Good balance of all factors"
        case .custom: return "Custom parameters"
        default: return ""
        }
    }
}
