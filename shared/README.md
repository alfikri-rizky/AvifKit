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

### Kotlin Multiplatform (Recommended)

Add to your **shared module's** `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.alfikri-rizky:avifkit:0.1.1")
        }
    }
}
```

✅ **That's it!** The library works on both Android and iOS:
- **Android**: Native libavif automatically included in AAR
- **iOS**: libavif automatically resolved via SPM/CocoaPods

### Android Only

```kotlin
dependencies {
    implementation("io.github.alfikri-rizky:avifkit:0.1.1")
}
```

✅ Native libavif binaries included for all ABIs (arm64-v8a, armeabi-v7a, x86, x86_64)

### iOS Only

#### Swift Package Manager

In Xcode:
1. File → Add Packages...
2. Enter: `https://github.com/alfikri-rizky/AvifKit`
3. Version: `0.1.1` or later

Or in `Package.swift`:
```swift
dependencies: [
    .package(url: "https://github.com/alfikri-rizky/AvifKit.git", from: "0.1.1")
]
```

✅ libavif automatically resolved as dependency

#### CocoaPods

Add to your `Podfile`:
```ruby
pod 'AvifKit', '~> 0.1.1'
```

Then run:
```bash
pod install
```

✅ libavif automatically installed as dependency

## 🚀 Quick Start

### Basic Usage - Convert to File

```kotlin
import com.alfikri.rizky.avifkit.AvifConverter
import com.alfikri.rizky.avifkit.ImageInput
import com.alfikri.rizky.avifkit.Priority
import com.alfikri.rizky.avifkit.PlatformFile

// Create converter instance
val converter = AvifConverter()

// Prepare input and output
val input = ImageInput.from(imageByteArray)
val outputFile = PlatformFile.fromPath("/path/to/output.avif")

// Convert to AVIF file
val resultFile = converter.convertToFile(
    input = input,
    output = outputFile,
    priority = Priority.BALANCED
)

println("Converted AVIF size: ${resultFile.size()} bytes")
```

### Convert to ByteArray (In-Memory)

```kotlin
// Convert to ByteArray without saving to disk
val input = ImageInput.from(imageByteArray)
val avifData = converter.encodeAvif(
    input = input,
    priority = Priority.BALANCED
)

// avifData is now a ByteArray containing AVIF image
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

// Convert with custom options
val resultFile = converter.convertToFile(
    input = input,
    output = outputFile,
    priority = Priority.BALANCED,
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
import com.alfikri.rizky.avifkit.*

// From Bitmap - convert to file
val bitmap = BitmapFactory.decodeFile(imagePath)
val input = ImageInput.from(bitmap)
val outputFile = PlatformFile.fromPath("/sdcard/output.avif")
val result = converter.convertToFile(input, outputFile, Priority.BALANCED)

// From File - convert to ByteArray
val inputFile = PlatformFile.fromPath("/sdcard/input.jpg")
val input = ImageInput.from(inputFile)
val avifData = converter.encodeAvif(input, Priority.BALANCED)

// From Uri (Content Provider)
val uri = Uri.parse("content://...")
val inputFile = PlatformFile.fromUri(context, uri)
val input = ImageInput.from(inputFile)
val outputFile = PlatformFile.fromPath(context.cacheDir.resolve("output.avif").path)
val result = converter.convertToFile(input, outputFile, Priority.BALANCED)
```

### iOS

```swift
import Shared
import UIKit

// From UIImage - convert to file
let image = UIImage(named: "photo")!
let data = image.pngData()!
let input = ImageInput.companion.from(data: KotlinByteArray(data: data))

// Create output file
let tempDir = FileManager.default.temporaryDirectory
let outputURL = tempDir.appendingPathComponent("output.avif")
let outputFile = PlatformFile.companion.fromPath(path: outputURL.path)

Task {
    do {
        let resultFile = try await converter.convertToFile(
            input: input,
            output: outputFile,
            priority: Priority.balanced,
            options: nil
        )

        let fileSize = try await resultFile.size()
        print("AVIF file size: \(fileSize) bytes")
    } catch {
        print("Error: \(error)")
    }
}
```

✅ **No manual setup needed!** When you add the library via SPM or CocoaPods, libavif is automatically included.

## 🏗️ Architecture

### Cross-Platform Core
- `AvifConverter` - Main conversion API with `encodeAvif()` and `convertToFile()`
- `ImageInput` - Unified image input (Bitmap/ByteArray/File/PlatformFile)
- `PlatformFile` - Cross-platform file abstraction for Android & iOS
- `EncodingOptions` - Configurable encoding parameters
- `Priority` - Quality presets (Speed, Balanced, Quality, Storage)
- `CompressionStrategy` - Adaptive compression modes (SMART, STRICT)

### Platform-Specific Implementation
- **Android**: Native libavif via JNI, bundled in AAR
- **iOS**: Native libavif via Swift bridge, resolved by SPM/CocoaPods

### File Handling
- **PlatformFile**: Unified file API across platforms
  - Android: Supports file paths, content:// URIs, SAF documents
  - iOS: Supports file paths, security-scoped resources
  - Both: Async read/write operations with proper resource management

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
- **Native Libraries**: Included automatically in AAR

### iOS
- **iOS**: 13.0+
- **Xcode**: 15.0+
- **Swift**: 5.9+
- **libavif**: Automatically resolved via SPM/CocoaPods

## 📝 No Manual Setup Required!

When you add AvifKit to your project:

✅ **Android**: Native libavif binaries are automatically included in the AAR for all ABIs
✅ **iOS**: libavif is automatically downloaded and linked via SPM or CocoaPods

You don't need to:
- ❌ Download libavif manually
- ❌ Configure build scripts
- ❌ Add Swift bridge files
- ❌ Link frameworks manually

Just add the dependency and start coding! 🎉

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
- [Maven Central](https://central.sonatype.com/artifact/io.github.alfikri-rizky/avifkit)
- [Publishing Guide](../PUBLISH_V0.1.1.md)
- [libavif Integration Guide](../docs/LIBAVIF_INTEGRATION.md)

## 💬 Support

- 📧 Email: rizkyalfikri@gmail.com
- 🐛 Issues: [GitHub Issues](https://github.com/alfikri-rizky/AvifKit/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/alfikri-rizky/AvifKit/discussions)
