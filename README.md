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
- ✅ **AVIF Encoding & Decoding** - Full support via native AVIF libraries (avif.swift on iOS, libavif on Android)
- ✅ **Android 15+ Compatible** - 16 KB page size alignment (Google Play requirement)
- ✅ **Adaptive Compression** - Intelligent file size targeting with two strategies
- ✅ **Priority Presets** - Quick configuration for common use cases
- ✅ **Multi-threaded Processing** - Parallel encoding/decoding with auto-tiling across all CPU cores
- ✅ **Format Detection** - Automatic image format identification
- ✅ **Orientation Support** - EXIF orientation handling on Android, UIImage orientation on iOS
- ✅ **Auto-Registration (iOS)** - Native handler registers automatically via `__attribute__((constructor))` — no manual setup needed
- ✅ **Swift Error Handling** - `@Throws` annotations propagate errors as `NSError` to Swift's `do/catch`
- ✅ **Explicit Error Reporting** - Clear `AvifError` exceptions when dependencies are missing (no silent fallbacks)
- ✅ **Memory Safety** - OutOfMemory error handling

#### Advanced Features
- Image resizing with dimension constraints
- Chroma subsampling options (YUV444, YUV422, YUV420)
- Alpha channel quality control
- Metadata preservation (optional)
- Multiple input types (ByteArray, Bitmap/UIImage, file path)

### Architecture

AvifKit uses **native AVIF libraries** on both platforms with explicit error reporting:

- **Android:** libavif via JNI — pre-built native binaries included in the AAR
- **iOS:** [avif.swift](https://github.com/awxkee/avif.swift) — aom encoder + dav1d decoder via SPM
- **Error Handling:** If native dependencies are missing, clear `AvifError` exceptions are thrown (no silent fallbacks)

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

### Installation

AvifKit is published as a Kotlin Multiplatform library with seamless integration for both Android and iOS platforms.

#### Android (Gradle)

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.alfikri-rizky:avifkit:0.2.3")
}
```

**That's it!** The library includes pre-built native binaries for all ABIs (arm64-v8a, armeabi-v7a, x86, x86_64) with full AVIF support via libavif.

#### iOS (Swift Package Manager) - Recommended ⭐

**In Xcode:**
1. File → Add Packages...
2. Enter repository URL: `https://github.com/alfikri-rizky/AvifKit`
3. Select version: `0.2.3` or higher
4. **Important:** After adding the package, **clean build folder** (Cmd+Shift+K) before first build

**Or add to your `Package.swift`:**

```swift
dependencies: [
    .package(url: "https://github.com/alfikri-rizky/AvifKit", from: "0.2.3")
]
```

**Setup Notes:**
- ✅ SPM automatically downloads the pre-built XCFramework from GitHub Releases
- ✅ Resolves avif.swift dependency (pre-built aom encoder + dav1d decoder — no source compilation)
- ✅ Auto-registers native handler — no manual setup code needed
- ✅ Integrates seamlessly with your Xcode project
- ⚠️ **First-time setup:** Dependencies may take a few minutes to download on first resolve
- ⚠️ **Important:** Always do a clean build (Product → Clean Build Folder) after adding the package

**Troubleshooting:**

If you see `AvifError.EncodingFailed: Native AVIF handler not available`:

1. **Ensure sufficient disk space** (at least 1GB free for SPM dependencies)
2. **Clean all caches:**
   ```bash
   # Clear SPM cache
   rm -rf ~/Library/Caches/org.swift.swiftpm
   rm -rf ~/Library/org.swift.swiftpm

   # Clear Xcode derived data
   rm -rf ~/Library/Developer/Xcode/DerivedData
   ```
3. **In Xcode:**
   - File → Packages → Reset Package Caches
   - File → Packages → Update to Latest Package Versions
   - Product → Clean Build Folder (Cmd+Shift+K)
   - Product → Build (Cmd+B)

4. **Verify AVIF is available:**
   ```swift
   import AvifKit

   print("AVIF available:", AVIFNativeConverter.isAvifAvailable)  // Should be true
   ```

**Download from GitHub Releases:** [v0.2.3](https://github.com/alfikri-rizky/AvifKit/releases/tag/v0.2.3)

#### iOS (CocoaPods) - Not Recommended ⚠️

CocoaPods support is technically available but **not recommended** due to validation issues:

```ruby
pod 'AvifKit', '~> 0.2.3'
```

**Important Notes:**
- CocoaPods validation may fail due to transitive dependency configuration issues
- **However, actual usage works fine** when users install via `pod install` since app deployment targets (iOS 13.0+) override pod settings
- Our code and XCFramework are fully compatible

**Recommended alternatives:**
1. **Swift Package Manager** (fully supported, recommended)
2. **Direct XCFramework** from [GitHub Releases](https://github.com/alfikri-rizky/AvifKit/releases/tag/v0.2.3)

---

### Platform Implementation Details

#### Android
- **Native C++ implementation** via JNI (`shared/src/androidMain/cpp/`)
- **Pre-built libavif binaries** included for all ABIs
- **EXIF orientation support** (preserves portrait/landscape orientation)
- **Multi-threaded encoding/decoding** with auto-tiling (uses all CPU cores)
- **Explicit errors** when native library is missing (no silent JPEG fallback)

**Technical Details:**
- NDK with CMake build system
- Conditional compilation support
- Optimized with `-O3` compiler flags
- Symbol stripping for smaller binary size

#### iOS
- **Native Swift implementation** (`AVIFNativeConverter.swift`)
- **Powered by [avif.swift](https://github.com/awxkee/avif.swift)** — high-level `AVIFEncoder`/`AVIFDecoder` API
- **aom encoder + dav1d decoder** — pre-built XCFramework dependencies, no source compilation needed
- **UIImage orientation handling** (properly encodes portrait photos)
- **Auto-registration** via `__attribute__((constructor))` — consumers don't need manual setup
- **`@Throws` annotations** — errors propagate as NSError to Swift's do/catch
- **Explicit errors** when avif.swift is missing (no silent JPEG fallback)

**Technical Details:**
- iOS 13.0+ deployment target
- Swift 5.0+
- avif.swift 2.1.x (aom for encoding, dav1d for fast decoding)
- Pre-built XCFramework dependencies — no C library compilation required
- XCFramework support for multiple architectures

### Implementation Status

| Component | Status | Location | Notes |
|-----------|--------|----------|-------|
| **Core Library** | ✅ Complete | `shared/src/commonMain/` | Cross-platform API |
| **Android Native** | ✅ Complete | `shared/src/androidMain/cpp/` | JNI + libavif |
| **iOS Native** | ✅ Complete | `shared/src/iosMain/swift/` | Swift + libavif |
| **Adaptive Compression** | ✅ Complete | Both platforms | SMART & STRICT strategies |
| **Orientation Support** | ✅ Complete | Both platforms | EXIF (Android), UIImage (iOS) |
| **Fallback Mode** | ❌ Removed | Both platforms | Replaced with explicit `AvifError` exceptions |
| **Distribution** | ✅ Complete | `Package.swift` | SPM support (CocoaPods coming soon) |
| **Build Configuration** | ✅ Complete | `shared/build.gradle.kts` | Ready for publishing |

### Known Limitations

1. **Library Size:**
   - Including libavif increases app size (~2-3MB per architecture on Android, ~1-2MB on iOS)
   - This is standard for any AVIF library and necessary for native performance
   - Fallback mode available if size is critical

2. **No Fallback Mode (v0.2.3+):**
   - Library no longer silently falls back to JPEG
   - Missing dependencies throw clear `AvifError` exceptions
   - Ensures consumers are aware of configuration issues

3. **Platform API Differences:**
   - Android uses `android.graphics.Bitmap`
   - iOS uses `UIImage`
   - Abstracted via `PlatformBitmap` expect/actual pattern

4. **Build Requirements (for library authors only):**
   - Android: Requires NDK and CMake to build from source
   - iOS: Requires Xcode and CocoaPods/SPM
   - End users don't need these - they get pre-built binaries

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

- **Encoding/Decoding:** Throws `AvifError.EncodingFailed` or `AvifError.DecodingFailed` with actionable error messages
- **Swift (iOS):** Errors propagate as `NSError` via `@Throws` — caught by Swift's `do/catch`
- **Android:** Errors thrown as standard Kotlin exceptions

---

### For Library Authors & Contributors

If you want to build the library from source or contribute to development:

#### Prerequisites
- **Android:** NDK, CMake 3.18.1+
- **iOS:** Xcode, CocoaPods or SPM
- **Both:** JDK 11+, Kotlin 1.9+

#### Setup Development Environment

```bash
# 1. Clone the repository
git clone https://github.com/alfikri-rizky/AvifKit.git
cd AvifKit

# 2. Run the preparation script (downloads libavif, builds everything)
./scripts/prepare-for-publish.sh

# 3. Build the project
./gradlew :shared:build
```

#### Publishing

The library uses a comprehensive publishing setup:

**To Maven Central:**
```bash
./gradlew :shared:publishAllPublicationsToSonatypeRepository
```

**To local Maven (for testing):**
```bash
./gradlew :shared:publishToMavenLocal
```

**To CocoaPods:**
```bash
pod trunk push AvifKit.podspec
```

#### Scripts Reference

- `scripts/setup-android-libavif.sh` - Downloads libavif for Android development
- `scripts/setup-ios-avif.sh` - Sets up iOS dependencies (CocoaPods/SPM)
- `scripts/prepare-for-publish.sh` - Prepares everything for release (runs both setup scripts + builds)
- `scripts/verify-integration.sh` - Verifies the integration is working correctly

**Note:** End users of your published library don't need these scripts - they're only for development and publishing.

---

### Changelog

#### v0.2.3
- **iOS:** Auto-registration via `__attribute__((constructor))` — consumers no longer need manual `AvifKitSetup.registerNativeHandler()` calls
- **iOS/Android:** Added `@Throws(Exception::class)` to all public methods — errors now propagate as `NSError` to Swift's `do/catch` instead of crashing
- **iOS/Android:** Removed silent JPEG fallback — missing native dependencies now throw explicit `AvifError.EncodingFailed` / `AvifError.DecodingFailed` with actionable messages
- **iOS:** Single source of truth for `AVIFNativeConverter.swift` — demo app uses symlinks to `shared/src/iosMain/swift/`
- **iOS:** Updated demo app SPM dependency from `libavif-Xcode` to `avif.swift`

#### v0.2.2
- **iOS:** Fixed distribution — removed `#if canImport` conditionals that caused silent JPEG fallback in production

#### v0.2.1
- **iOS:** Replaced `libavif-Xcode` (raw C API) with [`avif.swift`](https://github.com/awxkee/avif.swift) — high-level Swift AVIF encoder/decoder
- **iOS:** Uses pre-built aom encoder + dav1d decoder (no source compilation, faster SPM resolution)
- **iOS:** Massively simplified `AVIFNativeConverter.swift` — removed ~250 lines of manual pixel buffer management
- **iOS:** Simplified orientation handling via `normalizeOrientation()` before encoding
- **CocoaPods:** Updated dependency from `libavif ~> 0.11` to `avif ~> 2.1`
- **SPM:** Updated dependency from `SDWebImage/libavif-Xcode` to `awxkee/avif.swift 2.1.x`

#### v0.2.0
- **iOS Fix:** Fixed libavif not available on production — pinned libavif-Xcode to 0.11.x (1.0.0 had breaking API changes)
- **Android Fix:** Fixed libaom download failure in CI — pre-clone libaom source to avoid unreliable googlesource tarball endpoint
- **Android:** Pinned libavif to stable v1.2.1 tag for reproducible builds
- **Android:** Patched LocalAom.cmake for CMake 3.22 compatibility (NDK default)

#### v0.1.9
- **iOS Performance:** Enabled auto-tiling for parallel multi-core AVIF encoding (significant speedup)
- **iOS Performance:** Optimized pixel extraction — fast path skips UIGraphics double-render for `.up` orientation
- **iOS Performance:** Smart alpha handling — avoids premultiplication overhead for opaque images (JPEG, etc.)
- **iOS:** Upgraded libavif-Xcode SPM dependency from 0.11.1 to 1.0.0
- **iOS Fix:** Fixed `EncodingFailed` crash when using `SPEED` preset (speed ≥ 7 triggered incompatible REALTIME mode in libaom)
- **iOS Fix:** Fixed protocol conformance error in `AvifKitNativeHandler` (Swift method signature mismatch)
- **iOS:** Improved error logging with `avifResultToString` for native encoding failures

#### v0.1.6
- Initial production release with full AVIF encoding/decoding
- Android and iOS native support via libavif
- Adaptive compression strategies (SMART/STRICT)
- Priority presets (SPEED/BALANCED/QUALITY/STORAGE)
- SPM and Maven Central distribution

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
