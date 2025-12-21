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

AvifKit is a Kotlin Multiplatform library for AVIF image encoding and decoding on Android and iOS.

### Features

- Convert images to/from AVIF format
- Adaptive compression with target file size
- Two compression strategies: SMART and STRICT
- Priority presets for common scenarios
- Support for Android and iOS platforms

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

### Platform Notes

#### Android
- Requires native library integration (libavif)
- Falls back to JPEG if native library unavailable
- JNI-based implementation

#### iOS
- Requires avif.swift integration (placeholder currently)
- Falls back to JPEG for encoding
- CoreGraphics-based implementation

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…