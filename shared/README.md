# AvifKit - Kotlin Multiplatform AVIF Library

[![Maven Central](https://img.shields.io/maven-central/v/io.github.alfikri-rizky/avifkit)](https://central.sonatype.com/artifact/io.github.alfikri-rizky/avifkit)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.20-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A Kotlin Multiplatform library for converting images to AVIF format, supporting both Android and iOS platforms.

## ✨ Features

- 🖼️ **AVIF Encoding** - Convert images to modern AVIF format
- 📱 **Cross-Platform** - Works on Android and iOS
- 🎯 **Compression Strategies**
  - **SMART**: Binary search for highest quality within target size (6-8 attempts)
  - **STRICT**: Exhaustive search for smallest possible size (up to 10 attempts)
- ⚡ **Quality Presets**
  - Speed - Fast encoding (quality 70, speed 10)
  - Balanced - Good balance (quality 80, speed 6)
  - Quality - High quality (quality 95, speed 2)
  - Storage - Maximum compression (quality 65, speed 8)
- 🎨 **Customizable Parameters**
  - Quality (0-100)
  - Encoding speed (0-10)
  - Chroma subsampling (YUV444/422/420)
  - Max dimensions
  - Target file size
  - Alpha channel quality
  - Lossless mode
  - Metadata preservation

## 📦 Installation

### Gradle (Kotlin DSL)

```kotlin
// In your shared module's build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.alfikri-rizky:avifkit:0.1.0")
        }
    }
}
```

### Android

```gradle
dependencies {
    implementation("io.github.alfikri-rizky:avifkit:0.1.0")
}
```

### iOS

#### CocoaPods
```ruby
pod 'AvifKitShared', '~> 0.1.0'
```

#### Swift Package Manager
```swift
dependencies: [
    .package(url: "https://github.com/alfikri-rizky/AvifKit", from: "0.1.0")
]
```

**Note**: iOS requires additional Swift bridge setup. See [iOS Integration Guide](../SWIFT_INTEGRATION_SETUP.md).

## 🚀 Quick Start

### Basic Usage

```kotlin
import com.alfikri.rizky.avifkit.AvifConverter
import com.alfikri.rizky.avifkit.ImageInput
import com.alfikri.rizky.avifkit.Priority

// Create converter instance
val converter = AvifConverter()

// Convert image with preset
val input = ImageInput.from(imageByteArray)
val avifData = converter.encodeAvif(
    input = input,
    priority = Priority.BALANCED
)
```

### Custom Parameters

```kotlin
import com.alfikri.rizky.avifkit.EncodingOptions
import com.alfikri.rizky.avifkit.ChromaSubsample
import com.alfikri.rizky.avifkit.CompressionStrategy

val options = EncodingOptions(
    quality = 80,
    speed = 6,
    subsample = ChromaSubsample.YUV420,
    alphaQuality = 100,
    lossless = false,
    preserveMetadata = true,
    maxDimension = 1920,
    maxSize = 500_000, // Target 500KB
    compressionStrategy = CompressionStrategy.SMART
)

val avifData = converter.encodeAvif(
    input = input,
    options = options
)
```

### Compression Strategies

When using `maxSize`, you can choose between two strategies:

#### SMART (Recommended)
```kotlin
compressionStrategy = CompressionStrategy.SMART
```
- Uses binary search to find highest quality within target size
- Faster (6-8 attempts)
- Better visual quality
- Stops when target is met

#### STRICT
```kotlin
compressionStrategy = CompressionStrategy.STRICT
```
- Continues compressing even after meeting target
- Finds smallest possible file size
- May take longer (up to 10 attempts)
- Best for storage-critical scenarios

### Quality Presets

```kotlin
// Speed-optimized
Priority.SPEED      // Quality 70, Speed 10

// Balanced (default)
Priority.BALANCED   // Quality 80, Speed 6

// Quality-optimized
Priority.QUALITY    // Quality 95, Speed 2

// Storage-optimized
Priority.STORAGE    // Quality 65, Speed 8
```

## 📖 Platform-Specific Examples

### Android

```kotlin
import android.graphics.BitmapFactory

// From Bitmap
val bitmap = BitmapFactory.decodeFile(imagePath)
val input = ImageInput.from(bitmap)
val avifData = converter.encodeAvif(input, Priority.BALANCED)

// From ByteArray
val bytes = File(imagePath).readBytes()
val input = ImageInput.from(bytes)
val avifData = converter.encodeAvif(input, Priority.BALANCED)
```

### iOS

```swift
import Shared
import UIKit

// From UIImage
let image = UIImage(named: "photo")!
let data = image.pngData()!
let input = ImageInput.companion.from(data: KotlinByteArray(data: data))

Task {
    let avifData = try await converter.encodeAvif(
        input: input,
        priority: Priority.balanced,
        options: nil
    )
}
```

**Important**: iOS requires AVIFNativeConverter Swift bridge. See setup guide.

## 🏗️ Architecture

### Cross-Platform Core
- `AvifConverter` - Main conversion API
- `ImageInput` - Unified image input (Bitmap/ByteArray/File)
- `EncodingOptions` - Configurable encoding parameters
- `Priority` - Quality presets
- `CompressionStrategy` - Adaptive compression modes

### Platform-Specific
- **Android**: Uses native libavif via JNI
- **iOS**: Uses Swift bridge to libavif

## 📊 Compression Strategy Comparison

| Feature | SMART | STRICT |
|---------|-------|--------|
| **Algorithm** | Binary search | Exhaustive |
| **Target Behavior** | Stops when met | Continues optimizing |
| **Attempts** | 6-8 | Up to 10 |
| **Speed** | ⚡ Faster | 🐢 Slower |
| **Quality** | 📸 Higher | 🗜️ Maximum compression |
| **Use Case** | General use | Storage-critical |

## 🔧 Requirements

### Android
- **Min SDK**: 24 (Android 7.0)
- **Compile SDK**: 35
- **Kotlin**: 2.2.20+

### iOS
- **iOS**: 14.0+
- **Xcode**: 15.0+
- **Swift**: 5.9+

## 📝 iOS Integration Setup

iOS requires additional setup for the Swift bridge. Follow these steps:

1. Add `AVIFNativeConverter.swift` to your Xcode project
2. Ensure it's added to your app target
3. Clean and rebuild

Detailed instructions: [SWIFT_INTEGRATION_SETUP.md](../SWIFT_INTEGRATION_SETUP.md)

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

```
MIT License

Copyright (c) 2025 Alfikri Rizky

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 🔗 Links

- [GitHub Repository](https://github.com/alfikri-rizky/AvifKit)
- [Issue Tracker](https://github.com/alfikri-rizky/AvifKit/issues)
- [Changelog](../CHANGELOG.md)
- [Publishing Guide](../PUBLISHING_GUIDE.md)

## 💬 Support

- 📧 Email: your.email@example.com
- 🐛 Issues: [GitHub Issues](https://github.com/alfikri-rizky/AvifKit/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/alfikri-rizky/AvifKit/discussions)
