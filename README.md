This is a Kotlin Multiplatform project targeting Android, iOS.

* [/composeApp](composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/iosApp](iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](shared/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](shared/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](iosApp) directory in Xcode and run it from there.

---

## AvifKit Library

AvifKit is a production-ready Kotlin Multiplatform library for AVIF image encoding and decoding on Android and iOS.

### Features

#### Core Functionality
- ✅ **AVIF Encoding & Decoding** - Full support via native libavif integration
- ✅ **Adaptive Compression** - Intelligent file size targeting with two strategies
- ✅ **Priority Presets** - Quick configuration for common use cases
- ✅ **Multi-threaded Processing** - Efficient encoding/decoding using up to 4 threads
- ✅ **Format Detection** - Automatic image format identification
- ✅ **Orientation Support** - EXIF orientation handling on Android, UIImage orientation on iOS
- ✅ **Graceful Fallback** - JPEG encoding when native library unavailable

#### Advanced Features
- Image resizing with dimension constraints
- Chroma subsampling options (YUV444, YUV422, YUV420)
- Alpha channel quality control
- Metadata preservation (optional)
- Multiple input types (ByteArray, Bitmap/UIImage, file path)

### Architecture

AvifKit uses a **two-tier architecture**:

1. **Native Mode** (Recommended):
   - Uses libavif for true AVIF encoding/decoding
   - Requires setup (see Platform Setup below)
   - Production-quality AVIF output

2. **Fallback Mode** (Automatic):
   - Uses JPEG encoding when native library unavailable
   - Works out-of-the-box without setup
   - Good for development/testing

### Usage

#### Basic Conversion

```kotlin
val converter = AvifConverter()

// Convert to AVIF with priority preset
val result = converter.convertToFile(
    input = ImageInput.from("/path/to/image.jpg"),
    outputPath = "/path/to/output.avif",
    priority = Priority.BALANCED
)
```

#### Advanced Compression with Target Size

When you need to compress images to meet a specific file size limit, AvifKit offers two compression strategies:

##### SMART Compression (Recommended)

Finds the **highest quality** image that still meets your target file size. This is the default and recommended strategy for most use cases.

```kotlin
val options = EncodingOptions(
    maxSize = 200 * 1024, // 200KB target
    compressionStrategy = CompressionStrategy.SMART  // Default
)

val result = converter.convertToFile(
    input = ImageInput.from("/path/to/image.jpg"),
    outputPath = "/path/to/output.avif",
    priority = Priority.BALANCED,
    options = options
)
```

**How it works:**
- Uses binary search to find optimal quality setting
- Typically completes in 6-8 attempts
- If target is 200KB, it might produce a 198KB image at quality 85
- Faster and produces better-looking results

**Best for:**
- General image compression
- User-facing images where quality matters
- Web optimization with size constraints
- Profile pictures, thumbnails with size limits

##### STRICT Compression (Maximum Compression)

Finds the **smallest possible** image by continuing compression even after meeting the target size.

```kotlin
val options = EncodingOptions(
    maxSize = 200 * 1024, // 200KB target
    compressionStrategy = CompressionStrategy.STRICT
)

val result = converter.convertToFile(
    input = ImageInput.from("/path/to/image.jpg"),
    outputPath = "/path/to/output.avif",
    priority = Priority.BALANCED,
    options = options
)
```

**How it works:**
- Tries multiple compression levels progressively
- Continues even after meeting target to maximize compression
- May take up to 10 attempts
- If target is 200KB, might compress down to 120KB

**Best for:**
- Storage-critical scenarios
- Batch processing where smallest size matters
- Archival systems
- Applications with strict storage quotas

#### Comparison: SMART vs STRICT

| Aspect | SMART | STRICT |
|--------|-------|--------|
| Goal | Best quality within limit | Smallest possible size |
| Speed | Faster (6-8 attempts) | Slower (up to 10 attempts) |
| Result Quality | Higher quality | Lower quality |
| Result Size | Near target size | Well below target |
| Use Case | General use | Storage-critical |

Example with 500KB target:
- **SMART**: Produces 495KB at quality 88
- **STRICT**: Produces 320KB at quality 62

### Priority Presets

```kotlin
Priority.SPEED    // Fast encoding, lower quality
Priority.QUALITY  // Best quality, slower encoding
Priority.STORAGE  // Minimum file size
Priority.BALANCED // Good balance (default)
```

### Encoding Options

```kotlin
EncodingOptions(
    quality = 75,                                    // Base quality (0-100)
    speed = 6,                                       // Encoding speed (0-10)
    subsample = ChromaSubsample.YUV420,             // Chroma subsampling
    alphaQuality = 90,                              // Alpha channel quality
    maxDimension = 2048,                            // Auto-resize if larger
    maxSize = 200 * 1024,                           // Target size in bytes
    compressionStrategy = CompressionStrategy.SMART  // SMART or STRICT
)
```

### Platform Setup

#### Android

**Implementation Status:** ✅ Complete (JNI + C++ with conditional libavif support)

**Quick Start (Fallback Mode):**
```bash
# Build without native AVIF (uses JPEG fallback)
./gradlew :shared:build
```

**Production Setup (Native AVIF):**
```bash
# 1. Download libavif
./scripts/setup-android-libavif.sh

# 2. Uncomment NDK configuration in shared/build.gradle.kts
#    Lines 56-83 (ndk{}, externalNativeBuild{})

# 3. Build with libavif support
./gradlew :shared:build
```

**Implementation Details:**
- Native C++ implementation via JNI (`shared/src/androidMain/cpp/`)
- Conditional compilation: works with OR without libavif
- EXIF orientation support (preserves portrait/landscape orientation)
- Multi-threaded encoding/decoding
- Build configuration in `CMakeLists.txt`

**Dependencies:**
- NDK (for native builds)
- CMake 3.18.1+
- libavif (auto-downloaded by setup script)

#### iOS

**Implementation Status:** ✅ Complete (Swift with conditional libavif support)

**Quick Start (Fallback Mode):**
```bash
# Build without native AVIF (uses JPEG fallback)
# Open iosApp/iosApp.xcodeproj and build
```

**Production Setup (Native AVIF):**
```bash
# 1. Run setup script
./scripts/setup-ios-avif.sh

# 2. Choose integration method:
#    Option 1: CocoaPods (recommended)
#    Option 2: Swift Package Manager

# 3. Open workspace/project in Xcode and build
```

**Implementation Details:**
- Native Swift implementation (`iosApp/Native/AVIFNativeConverter.swift`)
- Conditional compilation: `#if canImport(libavif)`
- UIImage orientation handling (properly encodes portrait photos)
- CoreGraphics-based conversion
- Kotlin stub bridges to Swift implementation

**Dependencies:**
- libavif (via CocoaPods or SPM)
- iOS 13.0+

### Implementation Status

| Component | Status | Location | Notes |
|-----------|--------|----------|-------|
| **Core Library** | ✅ Complete | `shared/src/commonMain/` | Cross-platform API |
| **Android Native** | ✅ Complete | `shared/src/androidMain/cpp/` | JNI + libavif |
| **iOS Native** | ✅ Complete | `iosApp/Native/AVIFNativeConverter.swift` | Swift + libavif |
| **Adaptive Compression** | ✅ Complete | Both platforms | SMART & STRICT strategies |
| **Orientation Support** | ✅ Complete | Both platforms | EXIF (Android), UIImage (iOS) |
| **Fallback Mode** | ✅ Complete | Both platforms | JPEG when libavif unavailable |
| **Setup Scripts** | ✅ Complete | `scripts/` | Android & iOS automation |
| **Build Configuration** | ⚠️ Partial | `shared/build.gradle.kts` | NDK config commented out |

### Known Limitations & Tech Debt

1. **Android NDK Configuration:**
   - NDK config is commented out in `shared/build.gradle.kts` (lines 56-83)
   - Must be manually uncommented for native AVIF support
   - Reason: Allows project to build without NDK installed

2. **iOS libavif Integration:**
   - Swift implementation is complete but library not linked by default
   - Requires manual CocoaPods/SPM setup
   - Falls back to JPEG gracefully

3. **Decoding on iOS (Fallback Mode):**
   - Without libavif: uses standard `UIImage(data:)` decoding
   - May not decode actual AVIF files (placeholder comment in code line 414)
   - With libavif: full AVIF decoding works

4. **Library Size:**
   - Including libavif increases app size (~2-3MB per architecture)
   - Consider using fallback mode if size is critical

5. **Platform API Differences:**
   - Android uses `android.graphics.Bitmap`
   - iOS uses `UIImage`
   - Wrapped in `PlatformBitmap` expect/actual

### Verifying Setup

Check if native AVIF is available:

```kotlin
val converter = AvifConverter()
val isSupported = converter.isAvifSupported()

// Android: check library version
val version = converter.getLibraryVersion() // Shows libavif version or "Placeholder"

// iOS: check in AVIFNativeConverter.swift
AVIFNativeConverter.isAvifAvailable // true if libavif linked
```

### Testing Fallback Behavior

The library automatically uses fallback when native library is unavailable:

- **Encoding:** JPEG with equivalent quality settings
- **Decoding:** Standard image decoder (JPEG, PNG, etc.)
- **Logs:** Check for "Using JPEG fallback" or "libavif not available" messages

---

### Additional Resources

- [Integration Guide](INTEGRATION_GUIDE.md) - Detailed setup instructions
- [iOS Integration](docs/IOS_INTEGRATION_GUIDE.md) - iOS-specific guide
- [Publishing Guide](docs/PUBLISHING_GUIDE.md) - How to publish to Maven
- [Project Summary](docs/PROJECT_SUMMARY.md) - Architecture overview

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…